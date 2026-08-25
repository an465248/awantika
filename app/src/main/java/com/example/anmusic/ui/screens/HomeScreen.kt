package com.example.anmusic.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.anmusic.R
import com.example.anmusic.data.local.DownloadItem
import com.example.anmusic.ui.components.DownloadProgressBar
import com.example.anmusic.ui.components.MediaItemCard
import com.example.anmusic.ui.components.MediaPreviewCard
import com.example.anmusic.ui.components.ServerSelectorBar
import com.example.anmusic.ui.components.ServerSelectorSheet
import com.example.anmusic.ui.theme.AccentCyan
import com.example.anmusic.ui.theme.AccentPurple
import com.example.anmusic.ui.theme.BgDark
import com.example.anmusic.ui.theme.BorderDark
import com.example.anmusic.ui.theme.CardDark
import com.example.anmusic.ui.theme.ErrorRed
import com.example.anmusic.ui.theme.PrimaryIndigo
import com.example.anmusic.ui.theme.TextMuted
import com.example.anmusic.ui.theme.TextPrimary
import com.example.anmusic.ui.theme.TextSecondary
import com.example.anmusic.ui.viewmodel.DownloaderViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: DownloaderViewModel,
    onPlayMedia: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val selectedMediaType by viewModel.selectedMediaType.collectAsStateWithLifecycle()
    val selectedQuality by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val metadata by viewModel.extractedMetadata.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadSpeed by viewModel.downloadSpeed.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val activeDownloadItem by viewModel.activeDownloadItem.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val isTestingServers by viewModel.isTestingServers.collectAsStateWithLifecycle()
    var showServerSelector by remember { mutableStateOf(false) }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pastedText = clip.getItemAt(0).text?.toString() ?: ""
            if (pastedText.isNotBlank()) {
                viewModel.onUrlChanged(pastedText.trim())
                Toast.makeText(context, "URL pasted from clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Hero Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hero_banner),
                        contentDescription = "Banner",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF0F0F1A).copy(alpha = 0.4f),
                                        Color(0xFF0F0F1A).copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Download ",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "4K Videos",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryIndigo
                                )
                            )
                            Text(
                                text = " & ",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "HD Audio",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AccentCyan
                                )
                            )
                        }
                        Text(
                            text = "YouTube, Instagram, TikTok, Twitter/X, SoundCloud & 1000+ Sites",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // URL Input Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Paste Media URL",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { viewModel.onUrlChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("url_input"),
                        placeholder = {
                            Text(
                                text = "Paste video/music link here...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Link",
                                tint = AccentCyan
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (urlInput.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onUrlChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = TextMuted
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { pasteFromClipboard() },
                                    modifier = Modifier.testTag("paste_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = AccentCyan
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = BorderDark,
                            focusedContainerColor = BgDark,
                            unfocusedContainerColor = BgDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sample link chips for quick testing
                    Text(
                        text = "Quick Demo Links:",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SampleChip("YouTube Video") {
                            viewModel.loadSampleUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                        }
                        SampleChip("YouTube Shorts") {
                            viewModel.loadSampleUrl("https://www.youtube.com/shorts/3i_b71I44tY")
                        }
                        SampleChip("Facebook Video") {
                            viewModel.loadSampleUrl("https://www.facebook.com/watch/?v=10153231379946729")
                        }
                        SampleChip("TikTok Clip") {
                            viewModel.loadSampleUrl("https://www.tiktok.com/@tiktok/video/7106594312292453678")
                        }
                        SampleChip("Direct MP4 HD") {
                            viewModel.loadSampleUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Download Type Selection (Video vs Audio)
                    Text(
                        text = "Download Type",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Video Card Option
                        TypeSelectionCard(
                            title = "Video (MP4)",
                            subtitle = "Full HD / 4K Video",
                            icon = Icons.Default.PlayCircle,
                            isSelected = selectedMediaType == "video",
                            accentColor = PrimaryIndigo,
                            onClick = { viewModel.setMediaType("video") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_type_video")
                        )

                        // Audio Card Option
                        TypeSelectionCard(
                            title = "Audio (MP3)",
                            subtitle = "HD 320k Music",
                            icon = Icons.Default.MusicNote,
                            isSelected = selectedMediaType == "audio",
                            accentColor = AccentCyan,
                            onClick = { viewModel.setMediaType("audio") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_type_audio")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quality Selection Badges
                    Text(
                        text = "Select Quality",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedMediaType == "video") {
                            QualityBadge(
                                label = "1080p Full HD",
                                isSelected = selectedQuality == "1080",
                                color = PrimaryIndigo,
                                onClick = { viewModel.setQuality("1080") }
                            )
                            QualityBadge(
                                label = "720p HD",
                                isSelected = selectedQuality == "720",
                                color = PrimaryIndigo,
                                onClick = { viewModel.setQuality("720") }
                            )
                            QualityBadge(
                                label = "360p SD",
                                isSelected = selectedQuality == "360",
                                color = PrimaryIndigo,
                                onClick = { viewModel.setQuality("360") }
                            )
                            QualityBadge(
                                label = "Best Available",
                                isSelected = selectedQuality == "best",
                                color = PrimaryIndigo,
                                onClick = { viewModel.setQuality("best") }
                            )
                        } else {
                            QualityBadge(
                                label = "320 kbps (High Quality)",
                                isSelected = selectedQuality == "audio_320",
                                color = AccentCyan,
                                onClick = { viewModel.setQuality("audio_320") }
                            )
                            QualityBadge(
                                label = "192 kbps (Standard)",
                                isSelected = selectedQuality == "audio_192",
                                color = AccentCyan,
                                onClick = { viewModel.setQuality("audio_192") }
                            )
                            QualityBadge(
                                label = "128 kbps (Compact)",
                                isSelected = selectedQuality == "audio_128",
                                color = AccentCyan,
                                onClick = { viewModel.setQuality("audio_128") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Download Server Switcher Bar
                    ServerSelectorBar(
                        selectedServer = selectedServer,
                        onClick = { showServerSelector = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Download Action Button
                    Button(
                        onClick = { viewModel.startDownload() },
                        enabled = !isDownloading && !isAnalyzing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("download_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryIndigo,
                            disabledContainerColor = PrimaryIndigo.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isDownloading || isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isAnalyzing) "Analyzing URL..." else "Downloading...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedMediaType == "audio") "Download Audio (MP3)" else "Download Video (MP4)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Error message banner
        if (errorMessage != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ErrorRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = ErrorRed
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Dismiss",
                                tint = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Live Download Progress Bar
        if (isDownloading || downloadProgress > 0) {
            item {
                DownloadProgressBar(
                    progress = downloadProgress,
                    speedText = downloadSpeed,
                    statusText = statusMessage
                )
            }
        }

        // Media Preview Card
        if (metadata != null) {
            item {
                MediaPreviewCard(
                    metadata = metadata!!,
                    selectedType = selectedMediaType
                )
            }
        }

        // Active Download Result Card
        if (activeDownloadItem != null && !isDownloading) {
            item {
                Column {
                    Text(
                        text = "Recently Downloaded",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MediaItemCard(
                        item = activeDownloadItem!!,
                        onPlayClick = { onPlayMedia(it) },
                        onDeleteClick = { viewModel.deleteItem(it) },
                        onRetryClick = { viewModel.retryDownload(it) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showServerSelector) {
        ServerSelectorSheet(
            servers = servers,
            selectedServer = selectedServer,
            isTesting = isTestingServers,
            onSelectServer = { viewModel.selectServer(it) },
            onRefreshLatency = { viewModel.refreshServersLatency() },
            onDismiss = { showServerSelector = false }
        )
    }
}

@Composable
fun SampleChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .background(Color(0xFF141426), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = AccentCyan,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TypeSelectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else BorderDark,
                shape = RoundedCornerShape(14.dp)
            )
            .background(
                if (isSelected) accentColor.copy(alpha = 0.12f) else BgDark,
                RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (isSelected) accentColor else Color(0xFF24243E),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color.White else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) TextPrimary else TextSecondary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun QualityBadge(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (isSelected) color else BorderDark,
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                if (isSelected) color else color.copy(alpha = 0.12f),
                RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else color
        )
    }
}
