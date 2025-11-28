package com.lomovtsev.home.bot.magnet

import java.net.URLDecoder
import com.turn.ttorrent.client.Client
import com.turn.ttorrent.client.SharedTorrent
import java.net.InetAddress

data class MagnetLink(
    val origin: String,
    val xt: String?,
    val dn: String?,
    val tr: List<String>
) {
    fun getTorrentSize(): Long {
        return 0L
    }
    
    fun getTorrentFile(): TorrentFile {
        return TorrentFile("empty")
    }
}

data class TorrentFile(
    val name: String,
) {
    
}

fun parseMagnet(uri: String): MagnetLink {
    val params = uri.removePrefix("magnet:?")
        .split("&")
        .map {
            val (k, v) = it.split("=")
            k to URLDecoder.decode(v, "UTF-8")
        }
        .groupBy({ it.first }, { it.second })

    return MagnetLink(
        origin = uri,
        xt = params["xt"]?.firstOrNull(),
        dn = params["dn"]?.firstOrNull(),
        tr = params["tr"] ?: emptyList()
    )
}
