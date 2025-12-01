package com.lomovtsev.home.bot.magnet

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

class BDecoderTest {
    @Test
    fun decodeTest() {
        val url = BDecoderTest::class.java.classLoader.getResource("torrents/ready.torrent")!!
        val file = File(url.toURI())

        val torrentBytes = file.readBytes()

        val decode = BDecoder(torrentBytes).decode() as Map<*, *>
        val info = decode["info"] as Map<*, *>
        
        Assertions.assertTrue(info.containsKey("name"))
    }
}
