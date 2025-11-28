package com.lomovtsev.home.bot.magnet

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class MagnetLinkTest {
    
    @Test
    fun testGettingTorrentSize() {
        val rawLink = "magnet:?xt=urn:btih:56CE67EA9542281819287A31A1EB6DE1C0A5545F" +
                "&tr=http%3A%2F%2Fbt.t-ru.org%2Fann%3Fmagnet" +
                "&dn=%D0%9C%D1%83%D0%BD%D0%B4%D0%BE%D0%BB%D1%8C%D1%84%20%2F%20" +
                "Moondolf%20%2F%20StarCraft%20II%20%2F%20sc2tv%20(%D0%A1%D0%B5" +
                "%D0%B7%D0%BE%D0%BD%D1%8B%201-5)"

        val decodedRawLink = URLDecoder.decode(rawLink, StandardCharsets.UTF_8.name())
        println(decodedRawLink)
        val magnetLink = parseMagnet(rawLink)
        
        val torrentSize = magnetLink.getTorrentSize()
        
        assertEquals(172250000000L, torrentSize)
    }
    
    @Test
    fun testPoc() {
        val rawLink = "magnet:?xt=urn:btih:56CE67EA9542281819287A31A1EB6DE1C0A5545F" +
                "&tr=http%3A%2F%2Fbt.t-ru.org%2Fann%3Fmagnet" +
                "&dn=%D0%9C%D1%83%D0%BD%D0%B4%D0%BE%D0%BB%D1%8C%D1%84%20%2F%20" +
                "Moondolf%20%2F%20StarCraft%20II%20%2F%20sc2tv%20(%D0%A1%D0%B5" +
                "%D0%B7%D0%BE%D0%BD%D1%8B%201-5)"
        pocGetTorrentFile(rawLink)
    }

}
