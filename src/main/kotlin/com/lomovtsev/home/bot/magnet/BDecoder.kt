package com.lomovtsev.home.bot.magnet

import java.nio.charset.Charset

val UTF8: Charset = Charset.forName("UTF-8")

// --- B-DECODER (ПАРСЕР) ---
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
            
            val key = String(keyBytes, UTF8)
            val value = decode()
            map[key] = value
        }
        ptr++ // skip 'e'
        return map
    }
}
