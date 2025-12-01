package com.lomovtsev.home.bot.magnet

data class TorrentFile(
    val name: String,
    val size: Long, // in bytes
) {
    fun getBeautifulSize(): String {
        if (size < 1024) return "$size B"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(size)) / 10
        return String.format("%.1f %sB", size.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
    }
}

private const val defaultName = "No Name Torrent"

fun parseTorrentFile(bytes: ByteArray): TorrentFile {

    val decodedFile =  BDecoder(bytes).decode() as Map<*, *>
    
    val info = decodedFile["info"] as Map<*, *>
    
    if (info.containsKey("length")) { // single file torrent
        val nameBytes = info["name"] as? ByteArray ?: defaultName.toByteArray()
        val size = info["length"] as? Long ?: 0L
        return TorrentFile(String(nameBytes, UTF8), size)
    } else if (info.containsKey("files")) {
        val files = info["files"] as List<*>
        var resultSize = 0L
        for (file in files) {
            file as Map<*, *>
            resultSize += file["length"] as Long
        }
        val name = info["name"] as? ByteArray ?: defaultName.toByteArray()
        return TorrentFile(String(name, UTF8), resultSize)
    }
    
    error("Can't parse torrent file")
}
