package com.lomovtsev.home.bot

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class QBitClient() {

    private val username: String = System.getenv("QBIT_USERNAME") ?: "admin"
    private val password: String = System.getenv("QBIT_PASSWORD") ?: "password"
    private val qBitUrl: String = System.getenv("QBIT_URL") ?: "http://localhost:9090"
    private var cookies: String? = null
    
    private val client = OkHttpClient()
    
    init {
        auth()
    }

    fun auth(): String {

        // 1) логин
        val loginReq = Request.Builder()
            .url("$qBitUrl/api/v2/auth/login")
            .header("Content-type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:144.0) Gecko/20100101 Firefox/144.0"
            )
            .post(
                FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .build()
            )
            .build()

        val loginResp = client.newCall(loginReq).execute()
        val cookies = loginResp.headers("set-cookie").joinToString("; ")
        if (cookies.isEmpty()) {
            throw IllegalStateException("Auth Request Failed")
        }
        this.cookies = cookies
        return cookies

    }

    fun addTorrent(magnet: String): Boolean {
        val cookies = auth()

        // 2) добавить торрент
        val addReq = Request.Builder()
            .url("$qBitUrl/api/v2/torrents/add")
            .post(FormBody.Builder().add("urls", magnet).build())
            .header("Cookie", cookies)
            .build()

        val addResp = client.newCall(addReq).execute()
        val result = addResp.isSuccessful
        if (!result) {
            auth()
        }
        return result
    }
}
