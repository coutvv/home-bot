package com.lomovtsev.home.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.lomovtsev.home.bot.common.getBeautifulSize
import com.lomovtsev.home.bot.magnet.parseMagnet
import com.lomovtsev.home.bot.magnet.parseTorrentFile
import com.lomovtsev.home.bot.rutracker.RutrackerClient
import com.lomovtsev.home.bot.vpn.checker.UrlProbe
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

val masterIds = setOf(127769371L, 321992164L)
const val masterChatId = 127769371L

fun main() {

    val qbitClient = QBitClient()
    val rutrackerClient = buildRutrackerClient()
    lateinit var urlProbe: UrlProbe
    val bot = bot {
        token = System.getenv("TELEGRAM_TOKEN")
        val telegramProxy = System.getenv("TELEGRAM_PROXY_URL")
        if (telegramProxy != null && telegramProxy.isNotEmpty()) {
            val uri = URI(telegramProxy)
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port))
            println("Telegram Bot will use http PROXY")
        }

        dispatch {
            callbackQuery {
                val chatId = callbackQuery.message!!.chat.id
                val chat = ChatId.fromId(chatId)
                when (callbackQuery.data) {
                    "AVAILABLE" -> {
                        val path = File("/")
                        val usableSpace = getBeautifulSize(path.usableSpace)
                        bot.sendMessage(
                            chatId = chat,
                            text = "Свободного места в корне: $usableSpace"
                        )
                    }

                    "PROBING" -> {
                        urlProbe.startSiteChecker()
                        bot.sendMessage(chatId = chat, text = "Start probing VPN")
                    }

                    else -> {
                        val data = callbackQuery.data
                        if (data.startsWith("RT:")) {
                            val datas = data.removePrefix("RT:").split("|||")
                            val topicId = datas[0]
                            val torrentLink = datas[1]
                            handleRutrackerPick(bot, chat, rutrackerClient, qbitClient, topicId, torrentLink)
                        }
                    }
                }
            }
            message {
                val chatId = message.chat.id
                val chatIdentity = ChatId.fromId(chatId)
                if (!masterIds.contains(message.from!!.id)) {
                    bot.sendMessage(
                        chatId = chatIdentity,
                        text = "You are not my master!"
                    )
                    return@message
                }
                val text = message.text ?: return@message
                if (text.startsWith("/probevpn") && message.chat.id == masterChatId) {
                    urlProbe.startSiteChecker()
                    bot.sendMessage(chatId = chatIdentity, text = "Start probing VPN")
                    return@message
                }
                if (text.startsWith("/search")) {
                    val query = text.removePrefix("/search").trim()
                    if (query.isEmpty()) {
                        bot.sendMessage(chatIdentity, "Использование: /search <запрос>")
                        return@message
                    }
                    handleRutrackerSearch(bot, chatIdentity, rutrackerClient, query)
                    return@message
                }
                if (text.startsWith("/available")) {
                    val path = File("/")
                    val usableSpace = getBeautifulSize(path.usableSpace)
                    bot.sendMessage(
                        chatId = chatIdentity,
                        text = "Свободного места в корне: $usableSpace"
                    )
                    return@message
                }
                if (!text.startsWith("magnet:?")) {
                    val keyboard = InlineKeyboardMarkup.create(
                        listOf(
                            InlineKeyboardButton.CallbackData(
                                text = "Доступное место",
                                callbackData = "AVAILABLE"
                            ),
                            InlineKeyboardButton.CallbackData(
                                text = "Зачекать сайтец",
                                callbackData = "PROBING",
                            )
                        )
                    )
                    bot.sendMessage(
                        chatId = chatIdentity,
                        text = "Привет! Дай magnet-ссылку, /search <запрос> или нажми кнопку",
                        replyMarkup = keyboard
                    )
                    return@message
                }
                bot.sendMessage(chatId = chatIdentity, text = "Ща попробую добавить...")
                val responseMessage = tryAddTorrent(qbitClient, text)
                bot.sendMessage(chatId = chatIdentity, text = responseMessage)
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
        val ok = qbitClient.addTorrentByMagnet(text)
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

const val oneGigabyte = 1024 * 1024 * 1024

private fun buildRutrackerClient(): RutrackerClient? {
    val user = System.getenv("RUTRACKER_USERNAME")
    if (user.isNullOrEmpty()) {
        println("RUTRACKER_USERNAME/RUTRACKER_PASSWORD not set — search disabled")
        return null
    }
    val proxy = System.getenv("RUTRACKER_PROXY_URL")
    val cookie = System.getenv("RUTRACKER_COOKIE")
    return RutrackerClient(user, proxy, cookie)
}

private fun handleRutrackerSearch(
    bot: Bot,
    chat: ChatId,
    client: RutrackerClient?,
    query: String,
) {
    if (client == null) {
        bot.sendMessage(chat, "Поиск отключён: задай RUTRACKER_USERNAME/RUTRACKER_PASSWORD")
        return
    }
    bot.sendMessage(chat, "Ищу на rutracker: $query")
    val results = try {
        client.search(query)
    } catch (e: Exception) {
        bot.sendMessage(chat, "Ошибочка произошла: ${e.message}")
        return
    }
    if (results.isEmpty()) {
        bot.sendMessage(chat, "Ничего не найдено")
        return
    }
    val text = buildString {
        results.forEachIndexed { i, r ->
            append(
                """
                ${i + 1}. (${r.seeds}, ${r.leeches}) | ${r.addedDate} | <b>${r.size}</b>
                ${r.title}
                
                
                """.trimIndent()
            )
        }
    }
    val buttons = results.mapIndexed { i, r ->
        InlineKeyboardButton.CallbackData(
            text = "${i + 1} (${r.seeds}, ${r.leeches})",
            callbackData = "RT:${r.topicId}|||${r.torrentLink}"
        )
    }.chunked(5)
    val keyboard = InlineKeyboardMarkup.create(buttons)
    bot.sendMessage(chatId = chat, text = text, replyMarkup = keyboard, parseMode = ParseMode.HTML)
}

private fun handleRutrackerPick(
    bot: Bot,
    chat: ChatId,
    client: RutrackerClient?,
    qbit: QBitClient,
    topicId: String,
    torrentLink: String,
) {
    if (client == null) {
        bot.sendMessage(chat, "Поиск отключён")
        return
    }
    val link = if (torrentLink.isEmpty() || torrentLink == "null") "dl.php?t=$topicId" else torrentLink
    bot.sendMessage(chat, "Качаю .torrent с rutracker...")
    val bytes = try {
        client.getTorrent(link)
    } catch (e: Exception) {
        bot.sendMessage(chat, "Не удалось скачать .torrent: ${e.message}")
        return
    }
    val parsed = try {
        parseTorrentFile(bytes)
    } catch (e: Exception) {
        bot.sendMessage(chat, "Не смог распарсить .torrent: ${e.message}")
        return
    }
    if (parsed.size > getFreeSpace() - oneGigabyte) {
        bot.sendMessage(chat, "Не могу слишком большой файл! Места нет")
        return
    }
    val ok = qbit.addTorrentFile(bytes, "$topicId.torrent")
    val response = if (ok) {
        "Добавил файлик: ${parsed.name} \n" +
                "Размер: ${getBeautifulSize(parsed.size)}"
    } else {
        "Ошибка, братишка! Что-то с QBittorrent'ом"
    }
    bot.sendMessage(chat, response)
}
