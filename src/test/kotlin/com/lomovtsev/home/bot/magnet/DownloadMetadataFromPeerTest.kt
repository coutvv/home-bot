package com.lomovtsev.home.bot.magnet

import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

class DownloadMetadataFromPeerTest {
    
    // shawshank
    @Test
    fun downloadTorrentFile() {
        val link = "magnet:?xt=urn:btih:A91720FA92C6564A2CF8906F05FA3065B1AB392C&tr=http%3A%2F%2Fbt2.t-ru.org%2Fann%3Fmagnet&dn=%5BVIDEO4PSP%5D%20%D0%9F%D0%BE%D0%B1%D0%B5%D0%B3%20%D0%B8%D0%B7%20%D0%A8%D0%BE%D1%83%D1%88%D0%B5%D0%BD%D0%BA%D0%B0%20%2F%20The%20Shawshank%20Redemption%20%5B1994%2C%20%D0%B4%D1%80%D0%B0%D0%BC%D0%B0%2C%20DVDRip%5D"

        val content = pocGetTorrentFile(link)
        
        Files.write(Path.of("./my.torrent"), content)
    }

}
