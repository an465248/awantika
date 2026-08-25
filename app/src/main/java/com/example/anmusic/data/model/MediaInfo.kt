package com.example.anmusic.data.model

data class QualityOption(
    val id: String,
    val label: String,
    val resolutionOrBitrate: String,
    val ext: String,
    val isAudio: Boolean
)

data class MediaMetadata(
    val title: String,
    val author: String = "",
    val durationSeconds: Int = 0,
    val thumbnailUrl: String = "",
    val platform: PlatformType = PlatformType.OTHER,
    val availableQualities: List<QualityOption> = emptyList(),
    val directUrl: String? = null
)

enum class PlatformType(val displayName: String, val iconColorHex: Long) {
    YOUTUBE("YouTube", 0xFFFF0000),
    INSTAGRAM("Instagram", 0xFFE1306C),
    TIKTOK("TikTok", 0xFF00F2FE),
    TWITTER("Twitter / X", 0xFF1DA1F2),
    FACEBOOK("Facebook", 0xFF1877F2),
    SOUNDCLOUD("SoundCloud", 0xFFFF5500),
    VIMEO("Vimeo", 0xFF1AB7EA),
    REDDIT("Reddit", 0xFFFF4500),
    PINTEREST("Pinterest", 0xFFE60023),
    TWITCH("Twitch", 0xFF9146FF),
    OTHER("Web Stream", 0xFF6366F1);

    companion object {
        fun detect(url: String): PlatformType {
            val lower = url.lowercase()
            return when {
                lower.contains("youtube.com") || lower.contains("youtu.be") -> YOUTUBE
                lower.contains("instagram.com") -> INSTAGRAM
                lower.contains("tiktok.com") -> TIKTOK
                lower.contains("twitter.com") || lower.contains("x.com") -> TWITTER
                lower.contains("facebook.com") || lower.contains("fb.watch") -> FACEBOOK
                lower.contains("soundcloud.com") -> SOUNDCLOUD
                lower.contains("vimeo.com") -> VIMEO
                lower.contains("reddit.com") -> REDDIT
                lower.contains("pinterest.com") -> PINTEREST
                lower.contains("twitch.tv") -> TWITCH
                else -> OTHER
            }
        }
    }
}
