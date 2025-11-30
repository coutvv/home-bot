package com.lomovtsev.home.bot.magnet

import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

class TorrentFileTest {
    
    @Test
    fun testParseFile() {
        val filename = "E9DBCF5CDA56175CB78344C0400E1EEACBBB8F94.torrent.info"
        val file = File(filename)

        val torrentBytes = Files.readAllBytes(file.toPath())
    }

}
