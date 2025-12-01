package com.lomovtsev.home.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.lomovtsev.home.bot.magnet.parseMagnet

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

                val responseMessage = tryAddTorrent(qbitClient, text)
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = responseMessage)
            }
        }
    }
    println("Bot created and started!")
    bot.startPolling()
}

fun tryAddTorrent(qbitClient: QBitClient, text: String): String {

    val ok = qbitClient.addTorrent(text) // TODO: check the size of free space
    if (!ok) {
        return "Ошибка, братишка!"
    }
    try {
        val magnetLink = parseMagnet(text)
        val torrentFile = magnetLink.getTorrentFile()
        return "Добавил файлик: ${torrentFile.name} \n" +
                "Размер: ${torrentFile.getBeautifulSize()}"
    } catch (e: Exception) {
        return "Не смог распарсить, но добавил в загрузочки. Ошибка:\n${e.message}"
    }
}
