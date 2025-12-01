package com.lomovtsev.home.bot.magnet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MagnetLinkTest {
    
    @Test
    fun testGettingLargeTorrent() {
        val rawLink = "magnet:?xt=urn:btih:56CE67EA9542281819287A31A1EB6DE1C0A5545F" +
                "&tr=http%3A%2F%2Fbt.t-ru.org%2Fann%3Fmagnet" +
                "&dn=%D0%9C%D1%83%D0%BD%D0%B4%D0%BE%D0%BB%D1%8C%D1%84%20%2F%20" +
                "Moondolf%20%2F%20StarCraft%20II%20%2F%20sc2tv%20(%D0%A1%D0%B5" +
                "%D0%B7%D0%BE%D0%BD%D1%8B%201-5)"

        val magnetLink = parseMagnet(rawLink)
        
        val torrent = magnetLink.getTorrentFile()
        
        assertEquals(184950882954L, torrent.size)
    }
    
    @Test
    fun testTinyTorrent() {
        val magnetLink = parseMagnet("magnet:?xt=urn:btih:CFF2C9413AF843EF518F9CBAD32165B0096A6AA5" +
                "&tr=http%3A%2F%2Fbt4.t-ru.org%2Fann%3Fmagnet" +
                "&dn=%D0%A0%D0%BE%D0%B3%D0%BE%D0%B2%20%D0%95.%20%D0%92.%20-%20PostgreSQL%2017%20%D0%B8%D0" +
                "%B7%D0%BD%D1%83%D1%82%D1%80%D0%B8%20%5B2025%2C%20PDF%2C%20RUS%5D")
        
        val torrent = magnetLink.getTorrentFile()

        assertEquals("Рогов Е. - PostgreSQL 17 изнутри - 2025.pdf", torrent.name)
        assertEquals(11990240L, torrent.size)
    }

    @Test
    fun testMovieTorrent() {
        val link = "magnet:?xt=urn:btih:22823F73FA7800632EFD20A11C727FB33933BD47" +
                "&tr=http%3A%2F%2Fbt3.t-ru.org%2Fann%3Fmagnet" +
                "&dn=%5Bamd64%5D%20Astra%20Linux%20Special%20Edition%201.7.8"
        val magnetLink = parseMagnet(link)

        val torrentFile = magnetLink.getTorrentFile()
        
        assertEquals(59784684802, torrentFile.size)
        assertEquals("Astra Linux 1.7.8", torrentFile.name)
    }

}
