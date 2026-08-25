package com.example.anmusic.data.extractors

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

data class FacebookExtractedData(
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val hdStreamUrl: String?,
    val sdStreamUrl: String?
)

class FacebookExtractor(private val client: OkHttpClient) {

    fun resolveRedirects(initialUrl: String): String {
        return try {
            val req = Request.Builder()
                .url(initialUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()
            val res = client.newCall(req).execute()
            val finalUrl = res.request.url.toString()
            res.close()
            finalUrl
        } catch (_: Exception) {
            initialUrl
        }
    }

    fun fetchMetadata(url: String): FacebookExtractedData {
        val resolvedUrl = resolveRedirects(url)
        var title = "Facebook Video"
        var author = "Facebook User"
        var thumbnail = "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=600&q=80"
        var hdUrl: String? = null
        var sdUrl: String? = null

        try {
            val req = Request.Builder()
                .url(resolvedUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val html = res.body?.string() ?: ""

                // Extract OpenGraph tags
                val ogTitleMatch = Pattern.compile("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogTitleMatch.find()) {
                    title = cleanHtml(ogTitleMatch.group(1) ?: title)
                }

                val ogImageMatch = Pattern.compile("<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html)
                if (ogImageMatch.find()) {
                    thumbnail = cleanHtml(ogImageMatch.group(1) ?: thumbnail)
                }

                // Extract HD stream
                val hdPatterns = listOf(
                    "browser_native_hd_url[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']",
                    "playable_url_quality_hd[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']",
                    "hd_src[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']",
                    "hd_src_no_ratelimit[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']"
                )
                for (p in hdPatterns) {
                    val m = Pattern.compile(p).matcher(html)
                    if (m.find()) {
                        hdUrl = unescapeJsonUrl(m.group(1) ?: "")
                        if (!hdUrl.isNullOrBlank()) break
                    }
                }

                // Extract SD stream
                val sdPatterns = listOf(
                    "browser_native_sd_url[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']",
                    "playable_url[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']",
                    "sd_src[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']",
                    "sd_src_no_ratelimit[\"']?\\s*:\\s*[\"'](https:[^\"']+)[\"']",
                    "<meta\\s+property=\"og:video:secure_url\"\\s+content=\"([^\"]+)\"",
                    "<meta\\s+property=\"og:video\"\\s+content=\"([^\"]+)\""
                )
                for (p in sdPatterns) {
                    val m = Pattern.compile(p).matcher(html)
                    if (m.find()) {
                        sdUrl = unescapeJsonUrl(m.group(1) ?: "")
                        if (!sdUrl.isNullOrBlank()) break
                    }
                }
            }
        } catch (_: Exception) {}

        return FacebookExtractedData(
            title = title,
            author = author,
            thumbnailUrl = thumbnail,
            hdStreamUrl = hdUrl,
            sdStreamUrl = sdUrl
        )
    }

    fun extractDirectStream(url: String, preferHd: Boolean = true): String? {
        val meta = fetchMetadata(url)
        if (preferHd && !meta.hdStreamUrl.isNullOrBlank()) return meta.hdStreamUrl
        if (!meta.sdStreamUrl.isNullOrBlank()) return meta.sdStreamUrl
        if (!meta.hdStreamUrl.isNullOrBlank()) return meta.hdStreamUrl

        // Try FDownloader / SnapSave Public API
        try {
            val resolvedUrl = resolveRedirects(url)
            val formBody = FormBody.Builder()
                .add("q", resolvedUrl)
                .add("vt", "facebook")
                .build()

            val req = Request.Builder()
                .url("https://fdownloader.net/api/ajaxSearch")
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Origin", "https://fdownloader.net")
                .addHeader("Referer", "https://fdownloader.net/")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    if (json.optString("status") == "ok") {
                        val dataHtml = json.optString("data")
                        val linkMatch = Pattern.compile("href=\"(https:[^\"]+)\"\\s+class=\"[^\"]*download[^\"]*\"", Pattern.CASE_INSENSITIVE).matcher(dataHtml)
                        if (linkMatch.find()) {
                            val stream = linkMatch.group(1)
                            if (!stream.isNullOrBlank()) return stream
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun unescapeJsonUrl(raw: String): String {
        var clean = raw.replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u0025", "%")
            .replace("&amp;", "&")
        try {
            clean = URLDecoder.decode(clean, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {}
        return clean
    }

    private fun cleanHtml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
