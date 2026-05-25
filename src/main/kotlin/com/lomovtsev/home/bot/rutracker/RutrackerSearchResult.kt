package com.lomovtsev.home.bot.rutracker

data class RutrackerSearchResult(
    val topicId: String,
    val title: String,
    val size: String,
    val seeds: Int,
    val leeches: Int,
    val addedDate: String,
    val magnetLink: String?,
    val torrentLink: String?
)
