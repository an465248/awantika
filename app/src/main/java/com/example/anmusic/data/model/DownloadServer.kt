package com.example.anmusic.data.model

data class DownloadServer(
    val id: String,
    val name: String,
    val endpoint: String,
    val region: String,
    val description: String,
    val isAuto: Boolean = false,
    val isCustom: Boolean = false,
    val pingMs: Long = 0L,
    val isOnline: Boolean = true,
    val loadPercentage: Int = 20
)
