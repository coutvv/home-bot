package com.lomovtsev.home.bot.magnet

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class MagnetLink(
    val origin: String,
    val xt: String?,
    val dn: String?,
    val tr: List<String>
) {
    
    val prefix = "d4:info".toByteArray(StandardCharsets.ISO_8859_1)
    val suffix = "e".toByteArray(StandardCharsets.ISO_8859_1)
    
    fun getTorrentFile(): TorrentFile {
        val torrentFileBytes = pocGetTorrentFile(origin)
        
        val result = listOf(prefix, torrentFileBytes, suffix).reduce { acc, next -> acc + next }

        val fileName = "${getHashHex()}.torrent"
        File(fileName).writeBytes(result)
        writeOrigin(torrentFileBytes)
        return parseTorrentFile(result)
    }
    
    private fun writeOrigin(bytes: ByteArray) {

        val fileName = "${getHashHex()}-origin.torrent"
        File(fileName).writeBytes(bytes)
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
