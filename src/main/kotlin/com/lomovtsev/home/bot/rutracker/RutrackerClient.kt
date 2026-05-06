package com.lomovtsev.home.bot.rutracker

import okhttp3.*
import org.jsoup.Jsoup
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

data class RutrackerResult(
    val topicId: String,
    val title: String,
    val size: String,
    val seeds: Int,
    val magnetLink: String?,
)

class RutrackerClient(
    private val username: String,
    private val password: String,
    proxyUrl: String? = null,
    val cookie: String = ""
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .also { builder ->
            if (!proxyUrl.isNullOrEmpty()) {
                val uri = URI(proxyUrl)
                builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port)))
            }
        }
        .build()

    @Volatile
    private var loggedIn: Boolean = false

    fun search(query: String, limit: Int = 5): List<RutrackerResult> {
        ensureLoggedIn()
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegments("forum/tracker.php")
            .addQueryParameter("nm", query)
            .build()
        val html = fetchHtml(buildGet(url), "search")
        return parseSearchHtml(html, limit)
    }

    fun getMagnet(topicId: String): String {
        ensureLoggedIn()
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegments("forum/viewtopic.php")
            .addQueryParameter("t", topicId)
            .build()
        val html = fetchHtml(buildGet(url), "viewtopic")
        val doc = Jsoup.parse(html)
        return doc.selectFirst("a.magnet-link")?.attr("href")
            ?: error("magnet link not found for topic $topicId")
    }

    @Synchronized
    private fun ensureLoggedIn() {
        if (loggedIn) return
        loggedIn = checkAccess()
    }

    private fun checkAccess(): Boolean {
        val request = Request.Builder()
            .url("https://$HOST/forum/index.php")
            .addHeader("Cookie", cookie)
            .header("User-Agent", USER_AGENT)
            .build()
        
        val resp = httpClient.newCall(request).execute()
        
        return resp.isSuccessful && resp.body()?.string()?.contains(username) ?: false
    }

    // TODO: fix
    private fun login() {
        val body = FormBody.Builder(Charset.forName("windows-1251"))
            .add("login_username", username)
            .add("login_password", password)
            .add("login", "Вход")
            .build()
        val request = Request.Builder()
            .url("https://$HOST/forum/login.php")
            .addHeader("Cookie", cookie)
            .header("User-Agent", USER_AGENT)
            .post(body)
            .build()
        val resp = httpClient.newCall(request).execute()
        try {
            require(resp.isSuccessful) { "login failed $resp" }
        } finally {
            resp.close()
        }
        loggedIn = true
    }

    private fun fetchHtml(request: Request, label: String): String {
        val resp = httpClient.newCall(request).execute()
        try {
            require(resp.isSuccessful) { "$label failed $resp" }
            return resp.peekBody(Long.MAX_VALUE).string()
        } finally {
            resp.close()
        }
    }

    private fun buildGet(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .build()

    private fun parseSearchHtml(html: String, limit: Int): List<RutrackerResult> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr.tCenter.hl-tr")
        return rows.asSequence()
            .mapNotNull { row ->
                val titleA = row.selectFirst("a.tLink") ?: return@mapNotNull null
                val href = titleA.attr("href")
                val topicId = href.substringAfter("?t=", "").takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val title = titleA.text().trim()
                val sizeBytes = row.selectFirst("td.tor-size a")?.text() ?: "unknown"
                val seeds = row.selectFirst("b.seedmed")?.text()?.toIntOrNull() ?: 0
                val magnet = row.selectFirst("a.magnet-link")?.attr("href")
                RutrackerResult(topicId, title, sizeBytes, seeds, magnet)
            }
            .sortedByDescending { it.seeds }
            .take(limit)
            .toList()
    }

    companion object {
        private const val HOST = "rutracker.org"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:152.0) Gecko/20100101 " +
                "Firefox/152.0"
    }
}

private class SimpleCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    @Synchronized
    fun hasAnyCookies(): Boolean = store.isNotEmpty()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store.addAll(cookies)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return store.filter { it.matches(url) }
    }
}
