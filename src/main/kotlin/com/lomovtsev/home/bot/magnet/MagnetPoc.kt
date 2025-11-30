package com.lomovtsev.home.bot.magnet

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

const val TIMEOUT_MS = 5_000

fun pocGetTorrentFile(magnetLink: String) {
    println(">>> Start Processing HTTP Tracker...")

    // 1. Парсинг ссылки
    val infoHashHex = magnetLink.substringAfter("xt=urn:btih:").substringBefore("&")
    val infoHashBytes = hexToBytes(infoHashHex)

    // Вытаскиваем URL трекера
    val trackerUrlRaw = magnetLink.substringAfter("tr=").substringBefore("&")
    // В ссылке может быть URL-encoded символы, декодируем (напр. ://)
    val trackerUrl = URLDecoder.decode(trackerUrlRaw, StandardCharsets.UTF_8.name())

    println("Target Hash: $infoHashHex")
    println("Tracker URL: $trackerUrl")

    // 2. Получаем пиров (HTTP)
    val peers = try {
        if (trackerUrl.startsWith("http")) {
            getPeersFromHttpTracker(trackerUrl, infoHashBytes)
        } else {
            println("Это не HTTP трекер!")
            return
        }
    } catch (e: Exception) {
        println("Tracker failed: $e")
        e.printStackTrace()
        return
    }

    if (peers.isEmpty()) {
        println("No peers found on tracker.")
        return
    }
    println("Found ${peers.size} peers. Trying to fetch metadata...")

    // 3. Пробуем скачать Metadata (P2P часть осталась той же)
    for (peer in peers) {
        try {
            println("Connecting to $peer...")
            val metadata = downloadMetadataFromPeer(peer, infoHashBytes)
            if (metadata != null) {
                val fileName = "$infoHashHex.torrent.info"
                File(fileName).writeBytes(metadata)
                println("\n>>> SUCCESS! Saved to: ${File(fileName).absolutePath}")
                println(">>> File size: ${metadata.size} bytes")
                runSearching(File(fileName))
                return
            } 
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed with $peer: ${e.message}")
        }
    }
    println("Could not download metadata from any peer.")
}


// --- ЧАСТЬ 1: HTTP TRACKER CLIENT ---

fun getPeersFromHttpTracker(announceUrl: String, infoHash: ByteArray): List<InetSocketAddress> {
    // 1. Формируем URL с параметрами
    // Важно: info_hash нужно "экранировать" (%XX), но стандартный URLEncoder кодирует строку, 
    // а нам нужны сырые байты. Делаем руками.
    val encodedHash = StringBuilder()
    for (b in infoHash) {
        encodedHash.append(String.format("%%%02x", b))
    }

    val peerId = "-KT1000-123456789012" // Fake Peer ID
    val port = 6881

    // compact=1 заставляет трекер вернуть пиров в бинарном виде (6 байт на пира), а не списком словарей
    val separator = if (announceUrl.contains("?")) "&" else "?"
    val finalUrl = "$announceUrl${separator}info_hash=$encodedHash&peer_id=$peerId&port=$port&uploaded=0&downloaded=0&left=0&compact=1&event=started"

    println("Requesting: $finalUrl")

    // 2. Делаем GET запрос
    val url = URL(finalUrl)
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = TIMEOUT_MS
    conn.readTimeout = TIMEOUT_MS
    conn.setRequestProperty("User-Agent", "Transmission/2.94") // Некоторые трекеры блочат без UA

    val responseCode = conn.responseCode
    if (responseCode != 200) {
        throw IOException("Tracker returned HTTP $responseCode")
    }

    // 3. Читаем ответ (B-Encoded)
    val stream = DataInputStream(conn.inputStream)
    val responseBytes = stream.readBytes()

    // Для отладки (если не работает) можно раскомментировать:
    // println("Tracker response (str): ${String(responseBytes, StandardCharsets.ISO_8859_1)}")

    // 4. Парсим "peers" из B-Encoded словаря
    // Формат: ...5:peers<LENGTH>:<BINARY DATA>...
    // Ищем байты "5:peers"
    val pattern = "5:peers".toByteArray(StandardCharsets.US_ASCII)
    val idx = findByteArray(responseBytes, pattern)

    if (idx == -1) {
        // Иногда ключ просто "peers" без длины ключа перед ним (редко, но бывает)
        // Но чаще всего, если нет peers, значит вернулась ошибка "failure reason"
        throw IOException("Field 'peers' not found in tracker response")
    }

    // После "5:peers" идет длина строки (ascii цифры) и двоеточие
    // Пример: 5:peers12:......
    var ptr = idx + pattern.size
    var lengthStr = ""
    while (ptr < responseBytes.size && responseBytes[ptr].toChar() != ':') {
        lengthStr += responseBytes[ptr].toChar()
        ptr++
    }
    ptr++ // Пропускаем ':'

    if (lengthStr.isEmpty()) throw IOException("Invalid peers format")
    val dataLength = lengthStr.toInt()

    // Читаем сами данные пиров
    val peersData = responseBytes.copyOfRange(ptr, ptr + dataLength)

    // 5. Превращаем бинарные данные в IP:Port (6 байт на пира)
    val result = mutableListOf<InetSocketAddress>()
    var i = 0
    while (i + 6 <= peersData.size) {
        val ipBytes = peersData.copyOfRange(i, i + 4)
        val portBytes = peersData.copyOfRange(i + 4, i + 6)

        // Port в Big Endian
        val portVal = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

        try {
            val ip = InetAddress.getByAddress(ipBytes)
            result.add(InetSocketAddress(ip, portVal))
        } catch (e: Exception) {
            // Игнорируем кривые IP
        }
        i += 6
    }

    return result
}

// Утилита для поиска подмассива (indexOf для byte[])
fun findByteArray(source: ByteArray, match: ByteArray): Int {
    if (match.isEmpty()) return -1
    for (i in 0..source.size - match.size) {
        var found = true
        for (j in match.indices) {
            if (source[i + j] != match[j]) {
                found = false
                break
            }
        }
        if (found) return i
    }
    return -1
}

// --- ЧАСТЬ 2: TCP PEER WIRE (Без изменений, скопировано для автономности файла) ---

fun downloadMetadataFromPeer(peer: InetSocketAddress, infoHash: ByteArray): ByteArray? {
    val socket = Socket()
    try {
        socket.connect(peer, 3000) // Быстрый коннект
    } catch (e: Exception) {
        println("Peer is DEAD: ${peer.hostName}")
        return null // Пир мертв
    }

    val input = DataInputStream(socket.getInputStream())
    val output = socket.getOutputStream()

    // 1. Handshake
    val reserved = ByteArray(8)
    reserved[5] = 0x10.toByte() // Extension bit

    val handshake = ByteBuffer.allocate(68)
    handshake.put(19.toByte())
    handshake.put("BitTorrent protocol".toByteArray())
    handshake.put(reserved)
    handshake.put(infoHash)
    handshake.put("-KT1000-000000000000".toByteArray())

    output.write(handshake.array())
    output.flush()

    val responseHandshake = ByteArray(68)
    try {
        input.readFully(responseHandshake)
    } catch (e: EOFException) { 
        println("handshake can't read")
        return null 
    }

    if ((responseHandshake[25].toInt() and 0x10) == 0) {
        socket.close()
        throw IOException("Peer doesn't support extensions")
    }

    // 2. Ext Handshake
    val extHandshakeMsg = "d1:md11:ut_metadatai1eee"
    val extBody = extHandshakeMsg.toByteArray()

    val extPacket = ByteBuffer.allocate(4 + 1 + 1 + extBody.size)
    extPacket.putInt(1 + 1 + extBody.size)
    extPacket.put(20.toByte())
    extPacket.put(0.toByte())
    extPacket.put(extBody)

    output.write(extPacket.array())
    output.flush()

    // 3. Loop
    var metadataId = -1
    val buffer = ByteArray(65535) // Буфер чтения

    val startTime = System.currentTimeMillis()

    // Упрощенный цикл чтения (читаем кусками и анализируем)
    while (socket.isConnected && (System.currentTimeMillis() - startTime < 10_000)) {
        if (input.available() < 4) {
            Thread.sleep(100)
            continue
        }

        val len = input.readInt()
        if (len <= 0) {
            continue
        }

        val msgId = input.readByte().toInt()

        if (msgId == 20) { // Extension
            val extMsgId = input.readByte().toInt()
            val payloadLen = len - 2
            val payload = ByteArray(payloadLen)
            input.readFully(payload)

            if (extMsgId == 0) { // Handshake response
                val text = String(payload, StandardCharsets.ISO_8859_1)
                val key = "ut_metadatai"
                val idx = text.indexOf(key)
                if (idx != -1) {
                    // Парсим ID (может быть 1, 2, 3...)
                    val startIdx = idx + key.length
                    val endIdx = text.indexOf("e", startIdx)
                    if (startIdx > endIdx) {
                        throw IllegalStateException("Fucking stupid start/end indexes $startIdx - $endIdx")
                    }
                    val idStr = text.substring(startIdx, endIdx)
                    metadataId = idStr.toInt()

                    // Request Metadata piece 0
                    val req = "d8:msg_typei0e5:piecei0ee".toByteArray()
                    val reqPacket = ByteBuffer.allocate(4 + 1 + 1 + req.size)
                    reqPacket.putInt(1 + 1 + req.size)
                    reqPacket.put(20.toByte())
                    reqPacket.put(metadataId.toByte())
                    reqPacket.put(req)
                    output.write(reqPacket.array())
                    output.flush()
                }
            } else if (extMsgId == 1) { // Payload data
                val str = String(payload, StandardCharsets.ISO_8859_1)
                val splitMark = "ee"
                val splitIdx = str.indexOf(splitMark)

                if (splitIdx != -1) {
                    socket.close()
                    val rawDataStart = splitIdx + 2
                    println("sort of not null result")
                    return payload.copyOfRange(rawDataStart, payload.size)
                }
            }
        } else {
            // Skip other messages
            if (len > 1) {
                // skipBytes не всегда пропускает всё, лучше читать
                val skip = len - 1
                var skippedTotal = 0L
                while (skippedTotal < skip) {
                    skippedTotal += input.skip(skip - skippedTotal)
                }
            }
        }
    }
    socket.close()
    println("Fully cycled - no result")
    return null
}

fun hexToBytes(hex: String): ByteArray {
    val result = ByteArray(hex.length / 2)
    for (i in result.indices) {
        val index = i * 2
        val j = Integer.parseInt(hex.substring(index, index + 2), 16)
        result[i] = j.toByte()
    }
    return result
}
