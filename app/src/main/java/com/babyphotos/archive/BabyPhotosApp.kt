package com.babyphotos.archive

import android.app.Application
import androidx.work.WorkManager
import com.babyphotos.archive.data.repository.AnalysisRepository
import com.babyphotos.archive.domain.album.AlbumManager
import com.babyphotos.archive.domain.classifier.ClassificationEngine
import com.babyphotos.archive.domain.preprocessor.ImagePreprocessor
import com.babyphotos.archive.domain.preprocessor.VideoFrameExtractor
import com.babyphotos.archive.domain.recognizer.BabyRecognizer
import com.babyphotos.archive.domain.recognizer.BabyRecognizerImpl
import com.babyphotos.archive.domain.scanner.MediaStorePhotoScanner
import com.babyphotos.archive.util.SettingsManager

class BabyPhotosApp : Application() {

    lateinit var repository: AnalysisRepository
        private set

    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        rebuildRepository(
            apiBaseUrl = settingsManager.apiBaseUrl,
            apiKey = settingsManager.apiKey,
            modelName = settingsManager.modelName
        )
        cancelExistingScheduledScan()
    }

    fun updateRecognizer(apiBaseUrl: String, apiKey: String, modelName: String) {
        rebuildRepository(apiBaseUrl, apiKey, modelName)
    }

    private fun rebuildRepository(apiBaseUrl: String, apiKey: String, modelName: String) {
        val scanner = MediaStorePhotoScanner(this)
        val preprocessor = ImagePreprocessor(
            maxSize = settingsManager.maxImageSize,
            jpegQuality = settingsManager.jpegQuality
        )
        val videoFrameExtractor = VideoFrameExtractor(preprocessor)
        val classifier = ClassificationEngine(
            autoAddThreshold = settingsManager.autoAddThreshold,
            confirmThreshold = settingsManager.confirmThreshold
        )
        val albumManager = AlbumManager(this)

        repository = AnalysisRepository(
            context = this,
            scanner = scanner,
            preprocessor = preprocessor,
            videoFrameExtractor = videoFrameExtractor,
            recognizer = createRecognizer(apiBaseUrl, apiKey, modelName),
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

    private fun cancelExistingScheduledScan() {
        WorkManager.getInstance(this).cancelUniqueWork(DAILY_SCAN_WORK_NAME)
    }

    companion object {
        private const val DAILY_SCAN_WORK_NAME = "daily_baby_photo_scan"
    }
}
