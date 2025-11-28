package com.lomovtsev.home.bot.magnet
import java.io.File
import java.nio.charset.Charset

// --- ПРИМЕР ИСПОЛЬЗОВАНИЯ ---
fun runSearching(torrentFile: File) {

    if (!torrentFile.exists()) {
        println("Файл не найден! Сначала запусти скачивание метаданных.")
        return
    }

    val data = torrentFile.readBytes()

    try {
        // 1. Парсим байты в структуру Map/List
        val decoded = BDecoder(data).decode() as Map<*, *>
        var result = 0L
        if (decoded.contains("files")) {
            val filesMap = decoded["files"] as? List<Map<String, Any>> ?: error("no files info")
            for (f in filesMap) {
                result += f["length"] as? Long ?: 0
                // here is also we have key = "path" with the name of the downloaded file path
            }
        } else if (decoded.contains("length")) {
            result = decoded["length"] as? Long ?: error("no length info")
        }
        println("Fucking SIZE of torrent IS: ${formatSize(result)}")
        return
    } catch (e: Exception) {
        println("Ошибка парсинга: ${e.message}")
        e.printStackTrace()
    }
}

fun formatSize(v: Long): String {
    if (v < 1024) return "$v B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(v)) / 10
    return String.format("%.1f %sB", v.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
}

// --- B-DECODER (ПАРСЕР) ---
// Простой рекурсивный парсер без внешних либ
class BDecoder(private val data: ByteArray) {
    private var ptr = 0

    fun decode(): Any {
        if (ptr >= data.size) throw EOFException()

        return when (data[ptr].toInt().toChar()) {
            'd' -> decodeDict()
            'l' -> decodeList()
            'i' -> decodeInt()
            in '0'..'9' -> decodeString()
            else -> throw IllegalArgumentException("Unknown type at index $ptr: ${data[ptr].toInt().toChar()}")
        }
    }

    private fun decodeInt(): Long {
        ptr++ // skip 'i'
        val start = ptr
        while (ptr < data.size && data[ptr].toInt().toChar() != 'e') {
            ptr++
        }
        val numStr = String(data, start, ptr - start)
        ptr++ // skip 'e'
        return numStr.toLong()
    }

    private fun decodeString(): ByteArray {
        val start = ptr
        while (ptr < data.size && data[ptr].toInt().toChar() != ':') {
            ptr++
        }
        val lenStr = String(data, start, ptr - start)
        val len = lenStr.toInt()
        ptr++ // skip ':'

        val bytes = data.copyOfRange(ptr, ptr + len)
        ptr += len
        return bytes
    }

    private fun decodeList(): List<Any> {
        ptr++ // skip 'l'
        val list = mutableListOf<Any>()
        while (ptr < data.size && data[ptr].toInt().toChar() != 'e') {
            list.add(decode())
        }
        ptr++ // skip 'e'
        return list
    }

    private fun decodeDict(): Map<String, Any> {
        ptr++ // skip 'd'
        val map = mutableMapOf<String, Any>()
        while (ptr < data.size && data[ptr].toInt().toChar() != 'e') {
            // Ключи в словарях всегда строки
            val keyBytes = decodeString()
            val key = String(keyBytes, Charset.forName("UTF-8"))
            val value = decode()
            map[key] = value
        }
        ptr++ // skip 'e'
        return map
    }
}

class EOFException : Exception("Unexpected end of B-Encoded data")
