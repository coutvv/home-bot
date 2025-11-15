package com.lomovtsev

import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    
    val bot = bot {
        token = System.getenv("TELEGRAM_TOKEN")

        dispatch {
            message {
                val text = message.text ?: return@message
                if (!text.startsWith("magnet:?")) {
                    bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Дай magnet ссылку")
                    return@message
                }

                val ok = addTorrent(text)
                bot.sendMessage(
                    chatId = ChatId.fromId(message.chat.id),
                    text = if (ok) "Добавил" else "Ошибка"
                )
            }
        }
    }
    bot.startPolling()
}

val qbitUrl = "http://localhost:9090"

fun addTorrent(magnet: String): Boolean {
    val client = OkHttpClient()

    // 1) логин
    val loginReq = Request.Builder()
        .url("$qbitUrl/api/v2/auth/login")
        .post(FormBody.Builder()
            .add("username", "admin")
            .add("password", "password")
            .build())
        .build()

    val loginResp = client.newCall(loginReq).execute()
    val cookies = loginResp.headers("set-cookie").joinToString("; ")

    // 2) добавить торрент
    val addReq = Request.Builder()
        .url("$qbitUrl/api/v2/torrents/add")
        .post(FormBody.Builder().add("urls", magnet).build())
        .header("Cookie", cookies)
        .build()

    val addResp = client.newCall(addReq).execute()
    return addResp.isSuccessful
}
