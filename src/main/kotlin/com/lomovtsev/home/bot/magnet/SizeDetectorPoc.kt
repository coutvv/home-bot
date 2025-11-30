package com.lomovtsev.home.bot.magnet
import java.io.File

// --- ПРИМЕР ИСПОЛЬЗОВАНИЯ ---
fun findContentSize(torrentFile: File): Long {

    if (!torrentFile.exists()) {
        error("Файл не найден! Сначала запусти скачивание метаданных.")
    }

    val data = torrentFile.readBytes()

    try {
        println(String(data, UTF8))
        // 1. Парсим байты в структуру Map/List
        val decoded = BDecoder(data).decode() as Map<*, *>
        var result = 0L
        if (decoded.contains("files")) {
            val filesMap = decoded["files"] as? List<*> ?: error("no files info")
            for (f in filesMap ) {
                val fAttrs = f as Map<*, *>
                result += fAttrs["length"] as? Long ?: 0
                // here is also we have key = "path" with the name of the downloaded file path
            }
        } else if (decoded.contains("length")) {
            println("Solo Length")
            result = decoded["length"] as? Long ?: error("no length info")
        }
        println("Fucking SIZE of torrent IS: ${formatSize(result)}")
        return result
    } catch (e: Exception) {
        error("Ошибка парсинга: ${e.message}")
    }
}

fun formatSize(v: Long): String {
    if (v < 1024) return "$v B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(v)) / 10
    return String.format("%.1f %sB", v.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
}

class EOFException : Exception("Unexpected end of B-Encoded data")
