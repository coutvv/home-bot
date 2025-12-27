package com.lomovtsev.home.bot.common


fun getBeautifulSize(sizeInBytes: Long): String {
    if (sizeInBytes < 1024) {
        return "$sizeInBytes B"
    }
    val z = (63 - java.lang.Long.numberOfLeadingZeros(sizeInBytes)) / 10
    return String.format("%.1f %sB", sizeInBytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
}
