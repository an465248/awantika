package com.example.anmusic

import android.app.Application
import com.example.anmusic.data.local.AppDatabase
import com.example.anmusic.data.repository.DownloaderRepository

class AnMusicApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { DownloaderRepository(this, database.downloadDao()) }

    override fun onCreate() {
        super.onCreate()
    }
}
