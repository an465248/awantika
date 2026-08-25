package com.example.anmusic.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.anmusic.data.local.DownloadItem
import com.example.anmusic.data.model.DownloadServer
import com.example.anmusic.data.model.MediaMetadata
import com.example.anmusic.data.model.PlatformType
import com.example.anmusic.data.repository.DownloaderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloaderViewModel(
    private val repository: DownloaderRepository
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _selectedMediaType = MutableStateFlow("video") // "video" or "audio"
    val selectedMediaType: StateFlow<String> = _selectedMediaType.asStateFlow()

    private val _selectedQuality = MutableStateFlow("720")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    private val _extractedMetadata = MutableStateFlow<MediaMetadata?>(null)
    val extractedMetadata: StateFlow<MediaMetadata?> = _extractedMetadata.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready to download")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeDownloadItem = MutableStateFlow<DownloadItem?>(null)
    val activeDownloadItem: StateFlow<DownloadItem?> = _activeDownloadItem.asStateFlow()

    private val _customApiUrl = MutableStateFlow(repository.customApiEndpoint)
    val customApiUrl: StateFlow<String> = _customApiUrl.asStateFlow()

    // Multi-Server Download Infrastructure
    private val _servers = MutableStateFlow<List<DownloadServer>>(repository.getAvailableServers())
    val servers: StateFlow<List<DownloadServer>> = _servers.asStateFlow()

    private val _selectedServer = MutableStateFlow(
        repository.getAvailableServers().firstOrNull { it.isAuto }
            ?: repository.getAvailableServers().first()
    )
    val selectedServer: StateFlow<DownloadServer> = _selectedServer.asStateFlow()

    private val _isTestingServers = MutableStateFlow(false)
    val isTestingServers: StateFlow<Boolean> = _isTestingServers.asStateFlow()

    val allDownloads: StateFlow<List<DownloadItem>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Run initial background server ping to determine fastest node
        refreshServersLatency()
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
        _errorMessage.value = null
        if (newUrl.isNotBlank() && (newUrl.startsWith("http://") || newUrl.startsWith("https://"))) {
            analyzeUrl(newUrl)
        } else {
            _extractedMetadata.value = null
        }
    }

    fun setMediaType(type: String) {
        _selectedMediaType.value = type
        if (type == "audio") {
            _selectedQuality.value = "audio_320"
        } else {
            _selectedQuality.value = "720"
        }
    }

    fun setQuality(quality: String) {
        _selectedQuality.value = quality
    }

    fun selectServer(server: DownloadServer) {
        _selectedServer.value = server
        _statusMessage.value = "Switched to ${server.name}"
    }

    fun refreshServersLatency() {
        viewModelScope.launch {
            _isTestingServers.value = true
            val updated = repository.testAllServers()
            _servers.value = updated
            _selectedServer.value = updated.find { it.id == _selectedServer.value.id } ?: updated.first()
            _isTestingServers.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setCustomApi(url: String) {
        if (url.isNotBlank()) {
            _customApiUrl.value = url
            repository.customApiEndpoint = url
            _servers.value = repository.getAvailableServers()
        }
    }

    fun analyzeUrl(url: String = _urlInput.value) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            _statusMessage.value = "Analyzing media link..."

            val result = repository.analyzeUrl(url)
            result.onSuccess { meta ->
                _extractedMetadata.value = meta
                _statusMessage.value = "Media loaded: ${meta.platform.displayName}"
            }.onFailure { err ->
                _errorMessage.value = "Could not parse URL: ${err.localizedMessage}"
                _statusMessage.value = "Analysis failed"
            }
            _isAnalyzing.value = false
        }
    }

    fun startDownload() {
        val url = _urlInput.value.trim()
        if (url.isBlank()) {
            _errorMessage.value = "Please enter or paste a valid URL first."
            return
        }

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0
            _downloadSpeed.value = "Connecting..."
            _errorMessage.value = null

            val currentServer = _selectedServer.value
            _statusMessage.value = "Connecting to ${currentServer.name}..."

            val currentMeta = _extractedMetadata.value
            val platform = currentMeta?.platform?.displayName ?: PlatformType.detect(url).displayName
            val title = currentMeta?.title ?: "AnMusic_${System.currentTimeMillis()}"
            val thumb = currentMeta?.thumbnailUrl ?: ""
            val type = _selectedMediaType.value
            val quality = _selectedQuality.value

            // 1. Resolve Direct Stream URL with active server and automatic failover
            val resolveResult = repository.resolveDirectStreamUrl(url, type, quality, currentServer.id)

            resolveResult.onSuccess { directStreamUrl ->
                _statusMessage.value = "High-speed download in progress..."
                val dlResult = repository.startDownload(
                    sourceUrl = url,
                    title = title,
                    thumbnailUrl = thumb,
                    mediaType = type,
                    quality = quality,
                    directUrl = directStreamUrl,
                    platform = platform,
                    onProgressUpdate = { progress, speed ->
                        _downloadProgress.value = progress
                        _downloadSpeed.value = speed
                        _statusMessage.value = "Downloading: $progress% ($speed)"
                    }
                )

                dlResult.onSuccess { downloadedItem ->
                    _activeDownloadItem.value = downloadedItem
                    _statusMessage.value = "Download completed successfully!"
                    _downloadProgress.value = 100
                }.onFailure { err ->
                    _errorMessage.value = "Download error: ${err.localizedMessage}"
                    _statusMessage.value = "Download failed"
                }
            }.onFailure { err ->
                // Try fallback to direct url if provided by metadata
                val directMetaUrl = currentMeta?.directUrl
                if (!directMetaUrl.isNullOrBlank()) {
                    _statusMessage.value = "Downloading via direct server fallback..."
                    val dlResult = repository.startDownload(
                        sourceUrl = url,
                        title = title,
                        thumbnailUrl = thumb,
                        mediaType = type,
                        quality = quality,
                        directUrl = directMetaUrl,
                        platform = platform,
                        onProgressUpdate = { progress, speed ->
                            _downloadProgress.value = progress
                            _downloadSpeed.value = speed
                        }
                    )
                    dlResult.onSuccess { downloadedItem ->
                        _activeDownloadItem.value = downloadedItem
                        _statusMessage.value = "Download completed!"
                    }.onFailure { dlErr ->
                        _errorMessage.value = "Download error: ${dlErr.localizedMessage}"
                        _statusMessage.value = "Download failed"
                    }
                } else {
                    _errorMessage.value = "Server error: ${err.localizedMessage}"
                    _statusMessage.value = "Download resolution failed"
                }
            }

            _isDownloading.value = false
        }
    }

    fun retryDownload(item: DownloadItem) {
        viewModelScope.launch {
            _urlInput.value = item.sourceUrl
            _selectedMediaType.value = item.mediaType
            _selectedQuality.value = item.quality
            analyzeUrl(item.sourceUrl)
            startDownload()
        }
    }

    fun deleteItem(item: DownloadItem) {
        viewModelScope.launch {
            repository.deleteDownload(item)
            if (_activeDownloadItem.value?.id == item.id) {
                _activeDownloadItem.value = null
            }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompletedDownloads()
        }
    }

    fun loadSampleUrl(url: String) {
        _urlInput.value = url
        analyzeUrl(url)
    }
}

class DownloaderViewModelFactory(private val repository: DownloaderRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DownloaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DownloaderViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
