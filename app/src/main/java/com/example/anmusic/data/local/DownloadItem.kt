package com.example.anmusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceUrl: String,
    val title: String,
    val author: String = "",
    val thumbnailUrl: String = "",
    val mediaType: String, // "video" or "audio"
    val quality: String, // e.g., "1080p", "720p", "360p", "MP3 (320kbps)"
    val fileExtension: String, // "mp4" or "mp3"
    val directUrl: String = "",
    val localFilePath: String? = null,
    val platform: String = "Web", // "YouTube", "Instagram", "TikTok", etc.
    val status: String = "QUEUED", // "QUEUED", "DOWNLOADING", "COMPLETED", "FAILED"
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeedText: String = "",
    val errorMessage: String? = null,
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
