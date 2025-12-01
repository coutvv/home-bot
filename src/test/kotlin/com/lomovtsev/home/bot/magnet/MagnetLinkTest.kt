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

        val magnetLink = parseMagnet(rawLink)
        
        val torrent = magnetLink.getTorrentFile()
        
        assertEquals(184950841994L, torrent.size)
    }
    
    @Test
    fun testTinyTorrent() {

        val rawLink = "magnet:?xt=urn:btih:34AE673CC4D786086261AF0A88BCF2C3C6E33E20&tr=http%3A%2F%2Fbt4.t-ru" +
                ".org%2Fann%3Fmagnet&dn=Magnet%202.13%20%5BMAC%5D"
        pocGetTorrentFile(rawLink)
    }
    
    @Test
    fun testBookTorrent() {
        val link = "magnet:?xt=urn:btih:9C1A13569ED04A73F7B52E11175487FDC853369B&tr=http%3A%2F%2Fbt.t-ru.org%2Fann%3Fmagnet&dn=%5BAI%5D%20%D0%9C%D0%B8%D1%82%D1%87%D0%B5%D0%BB%D0%BB%20%D0%9C%D0%B5%D0%BB%D0%B0%D0%BD%D0%B8%20-%20%D0%98%D0%B4%D0%B8%D0%BE%D1%82%20%D0%B8%D0%BB%D0%B8%20%D0%B3%D0%B5%D0%BD%D0%B8%D0%B9%3F%20%D0%9A%D0%B0%D0%BA%20%D1%80%D0%B0%D0%B1%D0%BE%D1%82%D0%B0%D0%B5%D1%82%20%D0%B8%20%D0%BD%D0%B0%20%D1%87%D1%82%D0%BE%20%D1%81%D0%BF%D0%BE%D1%81%D0%BE%D0%B1%D0%B5%D0%BD%20%D0%B8%D1%81%D0%BA%D1%83%D1%81%D1%81%D1%82%D0%B2%D0%B5%D0%BD%D0%BD%D1%8B%D0%B9%20%D0%B8%D0%BD%D1%82%D0%B5%D0%BB%D0%BB%D0%B5%D0%BA%D1%82%20(%D0%AD%D0%BB%D0%B5%D0%BC%D0%B5%D0%BD%D1%82%D1%8B%202.0)%20%5B2022%2C%20EPUB%2FFB2%2FMOBI%2FRTF%2C%20RUS%5D"
        val magnetLink = parseMagnet(link)
        
        val torrent = magnetLink.getTorrentFile()
        
        assertEquals(19668271, torrent.size)
        assertEquals("Митчелл Мелани - Идиот или гений. [...] - (Элементы 2.0) - 2022", torrent.name)
    }

    @Test
    fun testMovieTorrent() {
        val link = "magnet:?xt=urn:btih:E9DBCF5CDA56175CB78344C0400E1EEACBBB8F94&tr=http%3A%2F%2Fbt4.t-ru.org%2Fann%3Fmagnet&dn=%D0%9C%D0%B0%D1%82%D1%80%D0%B8%D1%86%D0%B0%3A%20%D0%A0%D0%B5%D0%B2%D0%BE%D0%BB%D1%8E%D1%86%D0%B8%D1%8F%20%2F%20The%20Matrix%3A%20Revolutions%20(%D0%AD%D0%BD%D0%B4%D0%B8%20%D0%92%D0%B0%D1%87%D0%BE%D0%B2%D1%81%D0%BA%D0%B8%2C%20%D0%9B%D0%B0%D1%80%D1%80%D0%B8%20%D0%92%D0%B0%D1%87%D0%BE%D0%B2%D1%81%D0%BA%D0%B8%20%2F%20Andy%20Wachowski%2C%20Larry%20Wachowski)%20%5B2003%2C%20%D0%A1%D0%A8%D0%90%2C%20%D1%84%D0%B0%D0%BD%D1%82%D0%B0%D1%81%D1%82%D0%B8%D0%BA%D0%B0%2C%20%D0%B1%D0%BE%D0%B5%D0%B2%D0%B8%D0%BA%2C%20WEB-DLR"

        val magnetLink = parseMagnet(link)
        
        magnetLink.getTorrentFile()
    }

}
