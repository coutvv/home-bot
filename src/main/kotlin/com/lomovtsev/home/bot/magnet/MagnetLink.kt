package com.lomovtsev.home.bot.magnet

import java.net.URLDecoder

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
        val torrentFileBytes = pocGetTorrentFile(origin)
        
        error("TODO")
    }
    
    fun getHashHexBytes(): ByteArray {
        val hashHex = getHashHex()
        val result = ByteArray(hashHex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val j = Integer.parseInt(hashHex.substring(index, index + 2), 16)
            result[i] = j.toByte()
        }
        return result
    }
    
    fun getHashHex(): String = xt!!.split("btih:").last()
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
