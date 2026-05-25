package com.lomovtsev.home.bot.rutracker

import okhttp3.*
import org.jsoup.Jsoup
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

class RutrackerClient(
    private val username: String,
    proxyUrl: String? = null,
    val cookie: String = ""
) {
    companion object {
        private const val HOST = "rutracker.org"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:152.0) Gecko/20100101 " +
                "Firefox/152.0"
    }
    
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
    private var validCreds: Boolean = false

    fun search(query: String, limit: Int = 10): List<RutrackerSearchResult> {
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
    
    fun getTorrent(torrentLink: String): String {
        // TODO: download torrent file via link

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegments("forum/$torrentLink")
            .build()
        return ""
    }

    @Synchronized
    private fun ensureLoggedIn() {
        if (validCreds) {
            return
        }
        validCreds = checkAccess()
        if (!validCreds) {
            println("Problem with accessing rutracker pages $cookie")
        }
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
        .header("Cookie", cookie)
        .header("User-Agent", USER_AGENT)
        .build()

    private fun parseSearchHtml(html: String, limit: Int): List<RutrackerSearchResult> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("tr.tCenter.hl-tr")
        return rows.asSequence()
            .mapNotNull { row ->
                val titleA = row.selectFirst("a.tLink") ?: return@mapNotNull null
                val href = titleA.attr("href")
                val topicId = href.substringAfter("?t=", "").takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val title = titleA.text().trim()
                val size = row.selectFirst("td.tor-size a")?.text() ?: "unknown"
                val seeds = row.selectFirst("b.seedmed")?.text()?.toIntOrNull() ?: 0
                val magnet = row.selectFirst("a.magnet-link")?.attr("href")
                val leeches = row.selectFirst("td.leechmed")?.text()?.toIntOrNull() ?: 0
                val addedDate = row.selectFirst("td.row4.nowrap>p")?.text() ?: "unknown"
                val torrentFile = row.selectFirst("a.tr-dl.small")?.attr("href")
                RutrackerSearchResult(topicId, title, size, seeds, leeches, addedDate, magnet, torrentFile)
            }
            .sortedByDescending { it.seeds }
            .take(limit)
            .toList()
    }
}
