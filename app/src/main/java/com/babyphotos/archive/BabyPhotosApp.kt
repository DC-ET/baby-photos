package com.babyphotos.archive

import android.app.Application
import androidx.work.WorkManager
import com.babyphotos.archive.data.repository.AnalysisRepository
import com.babyphotos.archive.domain.album.AlbumManager
import com.babyphotos.archive.domain.album.BabyAlbumReader
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

    lateinit var babyAlbumReader: BabyAlbumReader
        private set

    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        babyAlbumReader = BabyAlbumReader(this)
        rebuildRepository(
            apiBaseUrl = settingsManager.apiBaseUrl,
            apiKey = settingsManager.apiKey,
            modelName = settingsManager.modelName,
            systemPrompt = settingsManager.systemPrompt,
            userPrompt = settingsManager.userPrompt
        )
        cancelExistingScheduledScan()
    }

    fun updateRecognizer(apiBaseUrl: String, apiKey: String, modelName: String, systemPrompt: String, userPrompt: String) {
        rebuildRepository(apiBaseUrl, apiKey, modelName, systemPrompt, userPrompt)
    }

    private fun rebuildRepository(apiBaseUrl: String, apiKey: String, modelName: String, systemPrompt: String, userPrompt: String) {
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
            recognizer = createRecognizer(apiBaseUrl, apiKey, modelName, systemPrompt, userPrompt),
            classifier = classifier,
            albumManager = albumManager,
            settingsManager = settingsManager
        )
    }

    private fun createRecognizer(apiBaseUrl: String, apiKey: String, modelName: String, systemPrompt: String, userPrompt: String): BabyRecognizer {
        return BabyRecognizerImpl(
            apiBaseUrl = apiBaseUrl,
            apiKey = apiKey,
            modelName = modelName,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt
        )
    }

    private fun cancelExistingScheduledScan() {
        WorkManager.getInstance(this).cancelUniqueWork(DAILY_SCAN_WORK_NAME)
    }

    companion object {
        private const val DAILY_SCAN_WORK_NAME = "daily_baby_photo_scan"
    }
}
