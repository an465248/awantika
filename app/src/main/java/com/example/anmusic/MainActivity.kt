package com.example.anmusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.anmusic.ui.navigation.MainAppContainer
import com.example.anmusic.ui.theme.AnMusicTheme
import com.example.anmusic.ui.viewmodel.DownloaderViewModel
import com.example.anmusic.ui.viewmodel.DownloaderViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: DownloaderViewModel by viewModels {
        DownloaderViewModelFactory((application as AnMusicApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            AnMusicTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val extractedUrl = extractUrlFromText(sharedText)
                viewModel.onUrlChanged(extractedUrl)
            }
        }
    }

    private fun extractUrlFromText(text: String): String {
        val words = text.split("\\s+".toRegex())
        for (word in words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                return word
            }
        }
        return text.trim()
    }
}
