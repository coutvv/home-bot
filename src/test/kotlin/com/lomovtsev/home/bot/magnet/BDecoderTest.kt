package com.lomovtsev.home.bot.magnet

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

class BDecoderTest {
    @Test
    fun decodeTestLargeFile() {
        val url = BDecoderTest::class.java.classLoader.getResource("torrents/legendary.torrent")!!
        val file = File(url.toURI())

        val torrentBytes = file.readBytes()

        val decode = BDecoder(torrentBytes).decode() as Map<*, *>
        val info = decode["info"] as Map<*, *>
        
        val name = String(info["name"] as ByteArray, UTF8)
        Assertions.assertEquals("Мундольф (Сезоны 1-5)", name)
    }
}
