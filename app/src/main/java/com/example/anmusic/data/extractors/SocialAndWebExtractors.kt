package com.example.anmusic.data.extractors

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

data class GenericExtractedData(
    val title: String,
    val author: String = "",
    val thumbnailUrl: String = "",
    val videoUrl: String? = null,
    val audioUrl: String? = null
)

class TikTokExtractor(private val client: OkHttpClient) {

    fun extract(url: String): GenericExtractedData {
        var title = "TikTok Video"
        var author = "TikTok Creator"
        var thumb = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=600&q=80"
        var videoUrl: String? = null
        var audioUrl: String? = null

        // 1. Try TikWM API
        try {
            val apiUrl = "https://www.tikwm.com/api/?url=$url"
            val req = Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    if (json.optInt("code", -1) == 0 && json.has("data")) {
                        val data = json.getJSONObject("data")
                        title = data.optString("title", title)
                        thumb = data.optString("cover", thumb)
                        val authorObj = data.optJSONObject("author")
                        if (authorObj != null) {
                            author = authorObj.optString("nickname", author)
                        }
                        videoUrl = data.optString("play", data.optString("wmplay"))
                        audioUrl = data.optString("music")
                        if (videoUrl?.startsWith("//") == true) videoUrl = "https:$videoUrl"
                        if (audioUrl?.startsWith("//") == true) audioUrl = "https:$audioUrl"
                    }
                }
            }
        } catch (_: Exception) {}

        return GenericExtractedData(
            title = title,
            author = author,
            thumbnailUrl = thumb,
            videoUrl = videoUrl,
            audioUrl = audioUrl
        )
    }
}

class TwitterExtractor(private val client: OkHttpClient) {

    fun extract(url: String): GenericExtractedData {
        var title = "Twitter / X Post"
        var author = "Twitter User"
        var thumb = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=600&q=80"
        var videoUrl: String? = null

        try {
            val statusId = if (url.contains("/status/")) {
                url.substringAfter("/status/").substringBefore("?").substringBefore("/")
            } else ""

            if (statusId.isNotBlank()) {
                val apiUrl = "https://api.fxtwitter.com/status/$statusId"
                val req = Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()

                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val tweet = json.optJSONObject("tweet")
                        if (tweet != null) {
                            title = tweet.optString("text", title).take(60)
                            val authorObj = tweet.optJSONObject("author")
                            if (authorObj != null) {
                                author = authorObj.optString("name", author)
                            }
                            val media = tweet.optJSONObject("media")
                            val videos = media?.optJSONArray("videos")
                            if (videos != null && videos.length() > 0) {
                                val v = videos.getJSONObject(0)
                                videoUrl = v.optString("url")
                                thumb = v.optString("thumbnail_url", thumb)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return GenericExtractedData(
            title = title,
            author = author,
            thumbnailUrl = thumb,
            videoUrl = videoUrl,
            audioUrl = null
        )
    }
}

class UniversalWebExtractor(private val client: OkHttpClient) {

    fun extract(url: String): GenericExtractedData {
        var title = "Media Download"
        var author = "Web Media"
        var thumb = "https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?w=600&q=80"
        var videoUrl: String? = null
        var audioUrl: String? = null

        // Direct media links
        if (url.endsWith(".mp4", ignoreCase = true) || url.endsWith(".webm", ignoreCase = true) ||
            url.contains(".googlevideo.com") || url.contains(".fbcdn.net") || url.contains(".tiktokcdn.com")
        ) {
            val filename = url.substringAfterLast("/").substringBefore("?").ifEmpty { "Video Stream" }
            return GenericExtractedData(
                title = filename,
                author = "Direct Stream",
                thumbnailUrl = thumb,
                videoUrl = url,
                audioUrl = null
            )
        }

        if (url.endsWith(".mp3", ignoreCase = true) || url.endsWith(".m4a", ignoreCase = true) ||
            url.endsWith(".wav", ignoreCase = true) || url.endsWith(".aac", ignoreCase = true) ||
            url.endsWith(".ogg", ignoreCase = true)
        ) {
            val filename = url.substringAfterLast("/").substringBefore("?").ifEmpty { "Audio Track" }
            return GenericExtractedData(
                title = filename,
                author = "Direct Audio",
                thumbnailUrl = thumb,
                videoUrl = null,
                audioUrl = url
            )
        }

        // Webpage scraping for OpenGraph & HTML5 media
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val html = res.body?.string() ?: ""

                // Extract og:title
                val ogTitle = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogTitle.find()) {
                    title = cleanText(ogTitle.group(1) ?: title)
                } else {
                    val htmlTitle = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (htmlTitle.find()) {
                        title = cleanText(htmlTitle.group(1) ?: title)
                    }
                }

                // Extract og:image
                val ogImage = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogImage.find()) {
                    thumb = cleanText(ogImage.group(1) ?: thumb)
                }

                // Extract og:video
                val ogVideo = Pattern.compile("<meta\\s+property=\"og:video(?::(?:secure_)?url)?\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogVideo.find()) {
                    videoUrl = cleanUrl(ogVideo.group(1) ?: "")
                }

                // Extract twitter:player:stream
                if (videoUrl.isNullOrBlank()) {
                    val twVideo = Pattern.compile("<meta\\s+(?:name|property)=\"twitter:player:stream\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (twVideo.find()) {
                        videoUrl = cleanUrl(twVideo.group(1) ?: "")
                    }
                }

                // Extract HTML5 <video src="..."> or <source src="...">
                if (videoUrl.isNullOrBlank()) {
                    val html5Video = Pattern.compile("<video[^>]*src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (html5Video.find()) {
                        videoUrl = cleanUrl(html5Video.group(1) ?: "")
                    }
                }

                if (videoUrl.isNullOrBlank()) {
                    val sourceVideo = Pattern.compile("<source[^>]*src=\"([^\"]+)\"[^>]*type=\"video/[^\"]*\"", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (sourceVideo.find()) {
                        videoUrl = cleanUrl(sourceVideo.group(1) ?: "")
                    }
                }

                // Extract og:audio / HTML5 audio
                val ogAudio = Pattern.compile("<meta\\s+property=\"og:audio(?::secure_url)?\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogAudio.find()) {
                    audioUrl = cleanUrl(ogAudio.group(1) ?: "")
                } else {
                    val html5Audio = Pattern.compile("<audio[^>]*src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (html5Audio.find()) {
                        audioUrl = cleanUrl(html5Audio.group(1) ?: "")
                    }
                }
            }
        } catch (_: Exception) {}

        return GenericExtractedData(
            title = title,
            author = author,
            thumbnailUrl = thumb,
            videoUrl = videoUrl,
            audioUrl = audioUrl
        )
    }

    private fun cleanText(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.replace("&amp;", "&").trim()
        try {
            clean = URLDecoder.decode(clean, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {}
        return clean
    }
}
