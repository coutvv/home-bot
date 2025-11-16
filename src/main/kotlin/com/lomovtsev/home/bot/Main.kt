package com.lomovtsev.home.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId

val masterIds = setOf(127769371L, 321992164L)

fun main() {

    val qbitClient = QBitClient()

    val bot = bot {
        token = System.getenv("TELEGRAM_TOKEN")
        dispatch {
            message {
                if (!masterIds.contains(message.from!!.id)) {
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "You are not my master!"
                    )
                    return@message
                }
                val text = message.text ?: return@message
                if (!text.startsWith("magnet:?")) {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Привет! Дай magnet-ссылку")
                    return@message
                }

                val ok = qbitClient.addTorrent(text)
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = if (ok) "Добавил" else "Ошибка"
                )
            }
        }
    }
    println("Bot created and started!")
    bot.startPolling()
}
