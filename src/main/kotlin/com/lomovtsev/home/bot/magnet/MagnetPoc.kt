package com.lomovtsev.home.bot.magnet

import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.and
import kotlin.random.Random

// --- КОНФИГУРАЦИЯ ---
const val TIMEOUT_MS = 5_000

fun pocGetTorrentFile(magnetLink: String) {
    println(">>> Start Processing...")

    // 1. Парсинг ссылки
    val infoHashHex = magnetLink.substringAfter("xt=urn:btih:").substringBefore("&")
    val infoHashBytes = hexToBytes(infoHashHex)
    val encodedTrackerUrl = magnetLink.substringAfter("tr=").substringBefore("&")
    val trackerUrl = URLDecoder.decode(encodedTrackerUrl, StandardCharsets.UTF_8.name())
    val trackerUri = URI(trackerUrl)
    
    

    println("Target Hash: $infoHashHex")
    val port = if (trackerUri.port == -1) {
        80
    } else {
        trackerUri.port
    }
    println("Tracker: ${trackerUri.host}:${port}")

    // 2. Получаем пиров с UDP трекера
    val peers = try {
        getPeersFromHttpTracker(trackerUri.host, infoHashBytes)
    } catch (e: Exception) {
        println("Tracker failed: ${e.message}")
        return
    }

    if (peers.isEmpty()) {
        println("No peers found.")
        return
    }
    println("Found ${peers.size} peers. Trying to fetch metadata...")

    // 3. Пробуем скачать Metadata (BEP 9/10)
    for (peer in peers) {
        try {
            println("Connecting to $peer...")
            val metadata = downloadMetadataFromPeer(peer, infoHashBytes)
            if (metadata != null) {
                val fileName = "$infoHashHex.torrent"
                File(fileName).writeBytes(metadata)
                println("\n>>> SUCCESS! Saved to: $fileName")
                println(">>> File size: ${metadata.size} bytes")
                return
            }
        } catch (e: Exception) {
            println("Failed with $peer: ${e.message}")
        }
    }
    println("Could not download metadata from any peer.")
}

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

// --- ЧАСТЬ 1: UDP TRACKER PROTOCOL ---

fun getPeersFromUdpTracker(host: String, port: Int, infoHash: ByteArray): List<InetSocketAddress> {
    val socket = DatagramSocket()
    socket.soTimeout = TIMEOUT_MS
    val address = InetAddress.getByName(host)

    // A. Connection Request
    val transactionId = Random.nextInt()
    val connReq = ByteBuffer.allocate(16)
    connReq.putLong(0x41727101980) // Protocol ID
    connReq.putInt(0) // Action: Connect
    connReq.putInt(transactionId)

    sendUdp(socket, connReq.array(), address, port)
    val connResp = receiveUdp(socket, 16)

    connResp.getInt() // action
    if (connResp.getInt() != transactionId) throw IOException("Wrong transaction ID")
    val connectionId = connResp.getLong()

    // B. Announce Request
    val announceReq = ByteBuffer.allocate(98)
    announceReq.putLong(connectionId)
    announceReq.putInt(1) // Action: Announce
    announceReq.putInt(transactionId)
    announceReq.put(infoHash)
    announceReq.put(ByteArray(20) { 0.toByte() }) // Peer ID (fake)
    announceReq.putLong(0) // Downloaded
    announceReq.putLong(0) // Left
    announceReq.putLong(0) // Uploaded
    announceReq.putInt(2) // Event: None
    announceReq.putInt(0) // IP
    announceReq.putInt(0) // Key
    announceReq.putInt(-1) // Num want
    announceReq.putShort(port.toShort())

    sendUdp(socket, announceReq.array(), address, port)
    val announceResp = receiveUdp(socket, 1024)

    announceResp.getInt() // Action
    announceResp.getInt() // Transaction ID
    announceResp.getInt() // Interval
    announceResp.getInt() // Leechers
    announceResp.getInt() // Seeders

    val peers = mutableListOf<InetSocketAddress>()
    while (announceResp.remaining() >= 6) {
        val ipBytes = ByteArray(4)
        announceResp.get(ipBytes)
        val portPeer = announceResp.short.toInt() and 0xFFFF
        peers.add(InetSocketAddress(InetAddress.getByAddress(ipBytes), portPeer))
    }
    return peers
}

fun sendUdp(socket: DatagramSocket, data: ByteArray, address: InetAddress, port: Int) {
    val packet = DatagramPacket(data, data.size, address, port)
    socket.send(packet)
}

fun receiveUdp(socket: DatagramSocket, size: Int): ByteBuffer {
    val buffer = ByteArray(size)
    val packet = DatagramPacket(buffer, buffer.size)
    socket.receive(packet)
    return ByteBuffer.wrap(packet.data, 0, packet.length)
}

// --- ЧАСТЬ 2: TCP PEER WIRE PROTOCOL (BEP 9 & 10) ---

fun downloadMetadataFromPeer(peer: InetSocketAddress, infoHash: ByteArray): ByteArray? {
    val socket = Socket()
    socket.connect(peer, TIMEOUT_MS)
    val input = DataInputStream(socket.getInputStream())
    val output = socket.getOutputStream()

    // 1. Handshake
    // 8 байт reserved. 20-й бит (0x10 в 5-м байте справа) означает Extension Protocol
    val reserved = ByteArray(8)
    reserved[5] = 0x10.toByte()

    val handshake = ByteBuffer.allocate(68)
    handshake.put(19.toByte())
    handshake.put("BitTorrent protocol".toByteArray())
    handshake.put(reserved)
    handshake.put(infoHash)
    handshake.put("-KT1000-000000000000".toByteArray()) // Fake Client ID

    output.write(handshake.array())

    // Читаем ответный Handshake
    val responseHandshake = ByteArray(68)
    input.readFully(responseHandshake)

    // Проверка поддержки расширений (Extension bit)
    if ((responseHandshake[25].toInt() and 0x10) == 0) {
        socket.close()
        throw IOException("Peer doesn't support extensions")
    }

    // 2. Отправляем Extended Handshake
    // Это словарь BEncoded. Говорим, что мы умеем metadata (ID 1)
    val extHandshakeMsg = "d1:md11:ut_metadatai1eee"
    val extBody = extHandshakeMsg.toByteArray()

    val extPacket = ByteBuffer.allocate(4 + 1 + 1 + extBody.size)
    extPacket.putInt(1 + 1 + extBody.size) // Length
    extPacket.put(20.toByte()) // ID для Extension Protocol = 20
    extPacket.put(0.toByte()) // ID для Handshake payload = 0
    extPacket.put(extBody)

    output.write(extPacket.array())

    // 3. Читаем сообщения, пока не получим ответный Extended Handshake
    var metadataId = -1

    // Простой цикл чтения сообщений
    while (socket.isConnected) {
        val len = input.readInt()
        if (len == 0) continue // Keep-alive

        val msgId = input.readByte().toInt()

        if (msgId == 20) { // Это Extension сообщение
            val extMsgId = input.readByte().toInt()
            val payload = ByteArray(len - 2)
            input.readFully(payload)

            if (extMsgId == 0) { // Это Handshake ответ
                val text = String(payload)
                // Грязный хак: ищем "ut_metadata" и число после него. 
                // В BEncode это выглядит как 11:ut_metadatai<ID>e
                val key = "ut_metadatai"
                val idx = text.indexOf(key)
                if (idx != -1) {
                    val endIdx = text.indexOf("e", idx)
                    metadataId = text.substring(idx + key.length, endIdx).toInt()

                    // Узнали ID, запрашиваем метаданные (кусок 0)
                    // Сообщение: d8:msg_typei0e5:piecei0ee -> {"msg_type": 0, "piece": 0}
                    val req = "d8:msg_typei0e5:piecei0ee".toByteArray()
                    val reqPacket = ByteBuffer.allocate(4 + 1 + 1 + req.size)
                    reqPacket.putInt(1 + 1 + req.size)
                    reqPacket.put(20.toByte())
                    reqPacket.put(metadataId.toByte())
                    reqPacket.put(req)
                    output.write(reqPacket.array())
                }
            } else if (extMsgId == 1) { // Это DATA (ответ на запрос метаданных)
                // Формат: BEncoded Dictionary + Raw Info Bytes
                // Нужно найти конец словаря "ee" и всё что после — это файл
                val str = String(payload, StandardCharsets.ISO_8859_1)
                val splitMark = "ee"
                val splitIdx = str.indexOf(splitMark)

                if (splitIdx != -1) {
                    // Возвращаем чистые байты метаданных
                    val rawDataStart = splitIdx + 2
                    return@downloadMetadataFromPeer payload.copyOfRange(rawDataStart, payload.size)
                }
            }
        } else {
            // Пропускаем обычные сообщения (BitField, Have и т.д.)
            if (len > 1) {
                input.skipBytes(len - 1)
            }
        }
    }
    socket.close()
    return null
}

// Утилита: Hex String -> ByteArray
fun hexToBytes(hex: String): ByteArray {
    val result = ByteArray(hex.length / 2)
    for (i in result.indices) {
        val index = i * 2
        val j = Integer.parseInt(hex.substring(index, index + 2), 16)
        result[i] = j.toByte()
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
