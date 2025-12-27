package com.lomovtsev.home.bot

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.lomovtsev.home.bot.common.getBeautifulSize
import com.lomovtsev.home.bot.magnet.parseMagnet
import com.lomovtsev.home.bot.vpn.checker.UrlProbe
import java.io.File

val masterIds = setOf(127769371L, 321992164L)
const val masterChatId = 127769371L

fun main() {

    val qbitClient = QBitClient()
    lateinit var urlProbe: UrlProbe
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
                if (text.startsWith("/probevpn") && message.chat.id == masterChatId) {
                    urlProbe.startSiteChecker()
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Start probing VPN")
                    return@message
                }
                if (text.startsWith("/available")) {
                    val path = File("/")
                    val usableSpace = getBeautifulSize(path.usableSpace)
                    bot.sendMessage(
                        chatId = ChatId.fromId(message.chat.id),
                        text = "Свободного места в корне: $usableSpace"
                    )
                    return@message
                }
                if (!text.startsWith("magnet:?")) {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Привет! Дай magnet-ссылку")
                    return@message
                }
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Ща попробую добавить...")
                val responseMessage = tryAddTorrent(qbitClient, text)
                bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = responseMessage)
            }
        }
    }
    val url = System.getenv("PROBE_URL") ?: "https://lomovtsev.com"
    urlProbe = UrlProbe(bot, url, masterChatId)
    urlProbe.startSiteChecker()
    
    println("Bot created and started!")
    bot.startPolling()
}

fun tryAddTorrent(qbitClient: QBitClient, text: String): String {

    try {
        val magnetLink = parseMagnet(text)
        val torrentFile = magnetLink.getTorrentFile()
        if (torrentFile.size > getFreeSpace() - oneGigabyte) {
            return "Не могу слишком большой файл! Места нет"
        }
        val ok = qbitClient.addTorrent(text) 
        if (!ok) {
            return "Ошибка, братишка! Что-то с QBittorrent'ом"
        }
        return "Добавил файлик: ${torrentFile.name} \n" +
                "Размер: ${getBeautifulSize(torrentFile.size)}"
    } catch (e: Exception) {
        return "Не смог распарсить, но добавил в загрузочки. Ошибка:\n${e.message}"
    }
}

fun getFreeSpace(): Long {
    val file = File("/")
    return file.freeSpace
}

const val oneGigabyte = 1024*1024*1024
