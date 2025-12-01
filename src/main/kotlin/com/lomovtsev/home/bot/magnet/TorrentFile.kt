package com.lomovtsev.home.bot.magnet

data class TorrentFile(
    val name: String,
    val size: Long, // bytes
) {
    
}

fun parseTorrentFile(bytes: ByteArray): TorrentFile {

    val decodedFile =  BDecoder(bytes).decode() as Map<*, *>
    
    val info = decodedFile["info"] as Map<*, *>
    
    println("Torrent base info: ${info.keys}")
    if (info.containsKey("length")) { // single file torrent
        val nameBytes = info["name"] as? ByteArray ?: "No Torrent Name".toByteArray()
        val size = info["length"] as? Long ?: 0L
        return TorrentFile(String(nameBytes, UTF8), size)
    } else if (info.containsKey("files")) {
        val files = info["files"] as List<*>
        var resultSize = 0L
        for (file in files) {
            file as Map<*, *>
            resultSize += file["length"] as Long
        }
        val name = info["name"] as? ByteArray ?: "Big Torrent / Not found name".toByteArray()
        return TorrentFile(String(name, UTF8), resultSize)
    }
    
//    return TorrentFile("", )
    error("meh")
}
