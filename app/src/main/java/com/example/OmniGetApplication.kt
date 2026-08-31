package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.DownloadRepository
import com.example.engine.DownloadManager
import com.example.service.NotificationHelper

class OmniGetApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: DownloadRepository
        private set

    lateinit var downloadManager: DownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        NotificationHelper.createNotificationChannels(this)
        database = AppDatabase.getDatabase(this)
        repository = DownloadRepository(database.downloadDao(), this)
        downloadManager = DownloadManager(this, repository)
    }

    companion object {
        lateinit var instance: OmniGetApplication
            private set
    }
}
