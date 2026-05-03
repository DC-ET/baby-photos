package com.babyphotos.archive

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.babyphotos.archive.data.repository.AnalysisRepository
import com.babyphotos.archive.domain.album.AlbumManager
import com.babyphotos.archive.domain.classifier.ClassificationEngine
import com.babyphotos.archive.domain.preprocessor.ImagePreprocessor
import com.babyphotos.archive.domain.recognizer.BabyRecognizer
import com.babyphotos.archive.domain.recognizer.BabyRecognizerImpl
import com.babyphotos.archive.domain.scanner.MediaStorePhotoScanner
import com.babyphotos.archive.util.SettingsManager
import com.babyphotos.archive.worker.DailyScanWorker
import java.util.concurrent.TimeUnit

class BabyPhotosApp : Application() {

    lateinit var repository: AnalysisRepository
        private set

    private lateinit var _recognizer: BabyRecognizer
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        val scanner = MediaStorePhotoScanner(this)
        val preprocessor = ImagePreprocessor()
        val classifier = ClassificationEngine()
        val albumManager = AlbumManager(this)

        // Default recognizer with placeholder config (user must configure in Settings)
        _recognizer = createRecognizer("", "", "gpt-4o-mini")
        repository = AnalysisRepository(
            context = this,
            scanner = scanner,
            preprocessor = preprocessor,
            recognizer = _recognizer,
            classifier = classifier,
            albumManager = albumManager,
            settingsManager = settingsManager
        )

        scheduleDailyScan()
    }

    fun updateRecognizer(apiBaseUrl: String, apiKey: String, modelName: String) {
        val scanner = MediaStorePhotoScanner(this)
        val preprocessor = ImagePreprocessor()
        val classifier = ClassificationEngine()
        val albumManager = AlbumManager(this)

        _recognizer = createRecognizer(apiBaseUrl, apiKey, modelName)
        repository = AnalysisRepository(
            context = this,
            scanner = scanner,
            preprocessor = preprocessor,
            recognizer = _recognizer,
            classifier = classifier,
            albumManager = albumManager,
            settingsManager = settingsManager
        )
    }

    private fun createRecognizer(apiBaseUrl: String, apiKey: String, modelName: String): BabyRecognizer {
        return BabyRecognizerImpl(
            apiBaseUrl = apiBaseUrl,
            apiKey = apiKey,
            modelName = modelName
        )
    }

    private fun scheduleDailyScan() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val scanRequest = PeriodicWorkRequestBuilder<DailyScanWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_baby_photo_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            scanRequest
        )
    }
}
