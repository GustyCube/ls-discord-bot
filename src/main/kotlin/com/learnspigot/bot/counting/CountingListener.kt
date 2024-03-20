package com.learnspigot.bot.counting

import com.learnspigot.bot.Environment
import com.learnspigot.bot.Server
import gg.flyte.neptune.annotation.Inject
import me.superpenguin.mathevaluator.Evaluator
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.events.message.MessageUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class CountingListener: ListenerAdapter() {

    @Inject private lateinit var countingRegistry: CountingRegistry

    val currentCount: Int get() = countingRegistry.currentCount

    var lastCount: Message? = null

    fun fuckedUp(user: User) {
        lastCount = null
        countingRegistry.fuckedUp(user)
    }

    fun addIdiotRole(user: User, guildId: String) {
        val guild = user.jda.getGuildById(guildId) ?: return
        val member = guild.getMemberById(user.id) ?: return
        val role = guild.getRoleById(Environment.get("IDIOT_ROLE_ID")) ?: return

        if (!member.roles.contains(role)) {
            guild.addRoleToMember(member, role).queue()
        }
    }


    private fun Channel.isCounting() = id == Environment.get("COUNTING_CHANNEL_ID")
    private fun Message.millisSinceLastCount() = timeCreated.toInstant().toEpochMilli() - (lastCount?.timeCreated?.toInstant()?.toEpochMilli() ?: 0)

    private val thinking = Emoji.fromUnicode("🤔")
    private val oneHundred = Emoji.fromUnicode("💯")

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || !event.isFromGuild || !event.channel.isCounting() || event.guild.id != Server.guildId) return
        if (event.message.attachments.isNotEmpty()) return

        val msg = event.message.contentRaw
        val userId = event.author.id
        if (Evaluator.isValidSyntax(msg)) {
            val evaluated = Evaluator.eval(msg).intValue()
            if (evaluated == currentCount + 1) {
                if (userId.equals(lastCount?.author?.id, true)) return run {
                    event.message.addReaction(Server.downvoteEmoji)
                    event.message.reply("You can't count twice in a row, let someone else join in! ( The count has been reset to 1 )").queue()
                    fuckedUp(event.author)
                    addIdiotRole(event.author, event.guild.id)
                }
                val reactionEmoji = if (evaluated % 100 == 0) oneHundred else Server.upvoteEmoji


                lastCount = event.message
                event.message.addReaction(reactionEmoji).queue()
                countingRegistry.incrementCount(event.author)

            } else {
                if (evaluated == currentCount && event.message.millisSinceLastCount() < 600) {
                    // ( 600ms delay ) - Arbitrary value based on superficial testing
                    event.message.addReaction(thinking).queue()
                    event.message.reply("I'll let this one slide").queue()
                    return
                }
                val next = currentCount + 1
                fuckedUp(event.author)
                addIdiotRole(event.author, event.guild.id)
                event.message.addReaction(Server.downvoteEmoji).queue()
                event.message.reply("The next number was $next, not $evaluated").queue()
            }
        }
    }

    override fun onMessageDelete(event: MessageDeleteEvent) {
        if (!event.channel.isCounting()) return
        if (event.messageId == lastCount?.id) {
            Server.countingChannel.sendMessage("${lastCount?.author?.asMention} deleted their count of $currentCount").queue()
        }
    }

    override fun onMessageUpdate(event: MessageUpdateEvent) {
        if (!event.channel.isCounting()) return
        if (event.messageId == lastCount?.id) {
            Server.countingChannel.sendMessage("${event.author.asMention} edited their count of $currentCount").queue()
        }
    }

}
