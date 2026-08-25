package com.example.anmusic.data.extractors

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class YouTubeExtractedData(
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val streamUrl: String?,
    val formatType: String = "mp4"
)

class YouTubeExtractor(private val client: OkHttpClient) {

    private val invidiousInstances = listOf(
        "https://inv.tux.pizza",
        "https://invidious.nerdvpn.de",
        "https://invidious.projectsegfau.lt",
        "https://vid.puffyan.us",
        "https://yt.artemislena.eu",
        "https://invidious.drgns.space",
        "https://iv.ggtyler.dev",
        "https://invidious.flokinet.to"
    )

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://api.piped.privacy.com.de",
        "https://pipedapi.tokhmi.xyz"
    )

    fun extractVideoId(url: String): String? {
        val cleanUrl = url.trim()
        return when {
            cleanUrl.contains("youtu.be/") -> {
                cleanUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            }
            cleanUrl.contains("youtube.com/shorts/") -> {
                cleanUrl.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("/")
            }
            cleanUrl.contains("youtube.com/live/") -> {
                cleanUrl.substringAfter("youtube.com/live/").substringBefore("?").substringBefore("/")
            }
            cleanUrl.contains("youtube.com/embed/") -> {
                cleanUrl.substringAfter("youtube.com/embed/").substringBefore("?").substringBefore("/")
            }
            cleanUrl.contains("v=") -> {
                cleanUrl.substringAfter("v=").substringBefore("&").substringBefore("#")
            }
            cleanUrl.matches(Regex("^[a-zA-Z0-9_-]{11}$")) -> cleanUrl
            else -> null
        }
    }

    fun fetchMetadata(url: String, videoId: String): YouTubeExtractedData {
        var title = "YouTube Video ($videoId)"
        var author = "YouTube Creator"
        val thumbnail = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val req = Request.Builder()
                .url(oembedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    title = json.optString("title", title)
                    author = json.optString("author_name", author)
                }
            }
        } catch (_: Exception) {}

        return YouTubeExtractedData(
            title = title,
            author = author,
            thumbnailUrl = thumbnail,
            durationSeconds = 0,
            streamUrl = null
        )
    }

    fun resolveStream(videoId: String, isAudio: Boolean, quality: String): String? {
        // 1. Try Invidious Instances
        for (base in invidiousInstances) {
            try {
                val apiUrl = "$base/api/v1/videos/$videoId"
                val req = Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        if (isAudio) {
                            // Find best audio stream in adaptiveFormats
                            val adaptive = json.optJSONArray("adaptiveFormats")
                            if (adaptive != null && adaptive.length() > 0) {
                                var bestAudioUrl: String? = null
                                var maxBitrate = 0
                                for (i in 0 until adaptive.length()) {
                                    val f = adaptive.getJSONObject(i)
                                    val type = f.optString("type")
                                    if (type.contains("audio")) {
                                        val bitrate = f.optInt("bitrate", 0)
                                        val streamUrl = f.optString("url")
                                        if (streamUrl.isNotBlank() && bitrate >= maxBitrate) {
                                            maxBitrate = bitrate
                                            bestAudioUrl = streamUrl
                                        }
                                    }
                                }
                                if (!bestAudioUrl.isNullOrBlank()) return bestAudioUrl
                            }
                        } else {
                            // Look in formatStreams (combined video + audio e.g. 720p/360p)
                            val formats = json.optJSONArray("formatStreams")
                            if (formats != null && formats.length() > 0) {
                                var targetUrl: String? = null
                                for (i in 0 until formats.length()) {
                                    val f = formats.getJSONObject(i)
                                    val qual = f.optString("qualityLabel")
                                    val streamUrl = f.optString("url")
                                    if (streamUrl.isNotBlank()) {
                                        if (quality.contains("720") && qual.contains("720")) return streamUrl
                                        if (quality.contains("360") && qual.contains("360")) return streamUrl
                                        if (targetUrl == null) targetUrl = streamUrl
                                    }
                                }
                                if (!targetUrl.isNullOrBlank()) return targetUrl
                            }

                            // Fallback to adaptiveFormats video
                            val adaptive = json.optJSONArray("adaptiveFormats")
                            if (adaptive != null && adaptive.length() > 0) {
                                for (i in 0 until adaptive.length()) {
                                    val f = adaptive.getJSONObject(i)
                                    val type = f.optString("type")
                                    val streamUrl = f.optString("url")
                                    if (type.contains("video") && streamUrl.isNotBlank()) {
                                        return streamUrl
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Try Piped API Instances
        for (base in pipedInstances) {
            try {
                val apiUrl = "$base/streams/$videoId"
                val req = Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()

                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        if (isAudio) {
                            val audioStreams = json.optJSONArray("audioStreams")
                            if (audioStreams != null && audioStreams.length() > 0) {
                                val audioUrl = audioStreams.getJSONObject(0).optString("url")
                                if (audioUrl.isNotBlank()) return audioUrl
                            }
                        } else {
                            val videoStreams = json.optJSONArray("videoStreams")
                            if (videoStreams != null && videoStreams.length() > 0) {
                                for (i in 0 until videoStreams.length()) {
                                    val v = videoStreams.getJSONObject(i)
                                    val qual = v.optString("quality")
                                    val videoUrl = v.optString("url")
                                    if (videoUrl.isNotBlank()) {
                                        if (quality.contains("1080") && qual.contains("1080")) return videoUrl
                                        if (quality.contains("720") && qual.contains("720")) return videoUrl
                                        if (quality.contains("360") && qual.contains("360")) return videoUrl
                                    }
                                }
                                val firstUrl = videoStreams.getJSONObject(0).optString("url")
                                if (firstUrl.isNotBlank()) return firstUrl
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
