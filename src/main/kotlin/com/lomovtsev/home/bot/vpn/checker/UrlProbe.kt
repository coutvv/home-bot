package com.lomovtsev.home.bot.vpn.checker

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Зонд для проверки доступности URL
 */
class UrlProbe(
    private val bot: Bot,
    private val url: String,
    private val chatId: Long,
    private val enable: AtomicBoolean = AtomicBoolean(),
) {
    private val client = OkHttpClient()

    fun startSiteChecker() {
        if (enable.get()) {
            println("Probe already started!")
            return
        }
        enable.set(true)
        startChecking()
    }

    private fun startChecking() {
        val request = Request.Builder().url(url).get().build()

        CoroutineScope(Dispatchers.IO).launch {
            println("Start probing url: $url")
            while (enable.get()) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.code() == 200) {
                        // skip
                    } else {
                        println("Site $url is unavailable")
                        enable.set(false)
                        bot.sendMessage(ChatId.fromId(chatId), text = "Probe is failed my lord!")
                    }
                    delay(30_000)
                } catch (_: Exception) {
                    println("Site $url is unavailable")
                    enable.set(false)
                    bot.sendMessage(ChatId.fromId(chatId), text = "Probe is failed my lord!")
                }
            }
        }
    }
}
