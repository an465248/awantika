package com.example.anmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anmusic.data.repository.PlatformInfo
import com.example.anmusic.ui.theme.AccentCyan
import com.example.anmusic.ui.theme.BgDark
import com.example.anmusic.ui.theme.BorderDark
import com.example.anmusic.ui.theme.CardDark
import com.example.anmusic.ui.theme.TextMuted
import com.example.anmusic.ui.theme.TextPrimary
import com.example.anmusic.ui.theme.TextSecondary
import com.example.anmusic.ui.viewmodel.DownloaderViewModel

@Composable
fun SitesScreen(
    viewModel: DownloaderViewModel,
    modifier: Modifier = Modifier
) {
    val platforms = listOf(
        PlatformInfo("YouTube", "4K, 1080p, 720p, Shorts, Live Streams, Audio MP3 (320kbps)", 0xFFFF0000),
        PlatformInfo("Instagram", "Reels, Video Posts, Stories, Carousels, Audio tracks", 0xFFE1306C),
        PlatformInfo("TikTok", "Watermark-free HD Videos, Sound/Audio extractions, Slideshows", 0xFF00F2FE),
        PlatformInfo("Twitter / X", "HQ Clips, Thread media, Native MP4 video conversions", 0xFF1DA1F2),
        PlatformInfo("Facebook", "Watch videos, Public stories, Reels in 1080p Full HD", 0xFF1877F2),
        PlatformInfo("SoundCloud", "Direct high-fidelity 320k / 192k MP3 audio download", 0xFFFF5500),
        PlatformInfo("Vimeo", "4K Ultra HD & 1080p raw video streams without player locks", 0xFF1AB7EA),
        PlatformInfo("Reddit", "Merged audio & video MP4 downloads from all subreddits", 0xFFFF4500),
        PlatformInfo("Pinterest", "HD pin video & gif downloads", 0xFFE60023),
        PlatformInfo("Twitch", "Game clips and streamer VOD highlights in MP4", 0xFF9146FF),
        PlatformInfo("1000+ Other Sites", "DailyMotion, Bilibili, VK, Rumble, Bandcamp, and direct media links", 0xFF6366F1)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "Supported Sites",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Powered by universal media extraction engine",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(AccentCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "How to Download",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "Simply copy any video or song link from your browser or favorite app and paste it into AnMusic Downloader.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        items(platforms) { platform ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(platform.colorHex).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(platform.colorHex).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(platform.colorHex),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = platform.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = platform.description,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
