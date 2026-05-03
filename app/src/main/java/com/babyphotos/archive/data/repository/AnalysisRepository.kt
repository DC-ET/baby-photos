package com.babyphotos.archive.data.repository

import android.content.Context
import android.util.Log
import com.babyphotos.archive.data.local.AppDatabase
import com.babyphotos.archive.data.local.ImageAnalysisEntity
import com.babyphotos.archive.domain.album.AlbumManager
import com.babyphotos.archive.domain.model.ClassificationAction
import com.babyphotos.archive.domain.classifier.ClassificationEngine
import com.babyphotos.archive.domain.model.BabyDetectionResult
import com.babyphotos.archive.domain.model.ClassificationDecision
import com.babyphotos.archive.domain.model.MediaType
import com.babyphotos.archive.domain.model.ScanSummary
import com.babyphotos.archive.domain.model.ScannedPhoto
import com.babyphotos.archive.domain.preprocessor.ImagePreprocessor
import com.babyphotos.archive.domain.preprocessor.VideoFrameExtractor
import com.babyphotos.archive.domain.recognizer.BabyRecognizer
import com.babyphotos.archive.domain.scanner.PhotoScanner
import com.babyphotos.archive.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest

class AnalysisRepository(
    private val context: Context,
    private val scanner: PhotoScanner,
    private val preprocessor: ImagePreprocessor,
    private val videoFrameExtractor: VideoFrameExtractor,
    private val recognizer: BabyRecognizer,
    private val classifier: ClassificationEngine,
    private val albumManager: AlbumManager,
    private val settingsManager: SettingsManager
) {
    private val dao by lazy { AppDatabase.getInstance(context).imageAnalysisDao() }
    private val semaphore = Semaphore(4)

    suspend fun runDailyScan(): ScanSummary = coroutineScope {
        // 1. Scan media from scanStartDate or today
        val scanStartDate = settingsManager.scanStartDate
        val mediaItems = if (scanStartDate > 0L) {
            scanner.scanPhotosSince(scanStartDate)
        } else {
            scanner.scanTodayPhotos()
        }
        Log.d(TAG, "Scanned ${mediaItems.size} media items")

        // 2. Filter out already-analyzed media（含已移动到宝宝相册后路径变化的情况）
        val newMediaItems = mediaItems.filter { media ->
            dao.getByPathOrMovedTo(media.path) == null
        }
        Log.d(TAG, "${newMediaItems.size} new media items to analyze")

        if (newMediaItems.isEmpty()) {
            return@coroutineScope ScanSummary(
                totalScanned = mediaItems.size,
                newlyAnalyzed = 0,
                autoAdded = 0,
                needsConfirmation = 0,
                confirmationItems = emptyList()
            )
        }

        // 3. Process each media item with concurrency control
        val decisions = newMediaItems.map { media ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        analyzeMedia(media)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to analyze ${media.path}", e)
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()

        // 4. Auto-add high-confidence results
        val autoAdded = decisions.filter { it.action == ClassificationAction.AUTO_ADD }
        var autoAddedCount = 0
        val moveFailures = mutableListOf<ClassificationDecision>()
        autoAdded.forEach { decision ->
            try {
                val movedPath = albumManager.moveToBabyAlbum(decision.photo).getOrThrow()
                dao.insert(decision.toEntity(movedPath))
                autoAddedCount++
                Log.d(TAG, "Auto-added: ${decision.photo.path} -> $movedPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to move ${decision.photo.path}", e)
                dao.insert(decision.toEntity(null, overrideAction = ClassificationAction.NEEDS_CONFIRM))
                moveFailures += decision
            }
        }

        // 5. Save ignored results
        decisions.filter { it.action == ClassificationAction.IGNORE }.forEach { decision ->
            dao.insert(decision.toEntity(null))
        }

        // 6. Save needs-confirm items (don't move yet)
        val needsConfirm = decisions.filter { it.action == ClassificationAction.NEEDS_CONFIRM }
        needsConfirm.forEach { decision ->
            dao.insert(decision.toEntity(null))
        }
        val confirmationItems = needsConfirm + moveFailures

        ScanSummary(
            totalScanned = mediaItems.size,
            newlyAnalyzed = newMediaItems.size,
            autoAdded = autoAddedCount,
            needsConfirmation = confirmationItems.size,
            confirmationItems = confirmationItems
        )
    }

    private suspend fun analyzeMedia(media: ScannedPhoto): ClassificationDecision? {
        return when (media.mediaType) {
            MediaType.IMAGE -> {
                val preprocessed = preprocessor.preprocess(media)
                recognizer.recognize(preprocessed.base64Data)
                    .getOrNull()
                    ?.let { classifier.classify(media, it) }
            }

            MediaType.VIDEO -> analyzeVideo(media)
        }
    }

    private suspend fun analyzeVideo(video: ScannedPhoto): ClassificationDecision? {
        val frames = videoFrameExtractor.extractFrames(video)
        if (frames.isEmpty()) return null

        var bestBabyFrame: Pair<Int, BabyDetectionResult>? = null
        var bestFallbackFrame: Pair<Int, BabyDetectionResult>? = null

        frames.forEachIndexed { index, frame ->
            val result = recognizer.recognize(frame.base64Data).getOrNull() ?: return@forEachIndexed
            val frameResult = result.copy(reason = "视频第 ${index + 1} 帧：${result.reason}")
            val decision = classifier.classify(video, frameResult)

            if (decision.action == ClassificationAction.AUTO_ADD) {
                return decision
            }

            if (frameResult.containsBaby) {
                val currentBest = bestBabyFrame?.second
                if (currentBest == null || frameResult.confidence > currentBest.confidence) {
                    bestBabyFrame = index to frameResult
                }
            } else {
                val currentBest = bestFallbackFrame?.second
                if (currentBest == null || frameResult.confidence > currentBest.confidence) {
                    bestFallbackFrame = index to frameResult
                }
            }
        }

        val result = bestBabyFrame?.second ?: bestFallbackFrame?.second ?: return null
        return classifier.classify(video, result)
    }

    suspend fun confirmAndMove(decision: ClassificationDecision): Result<String> =
        runCatching {
            val movedPath = albumManager.moveToBabyAlbum(decision.photo).getOrThrow()
            dao.insert(decision.toEntity(movedPath, overrideAction = ClassificationAction.AUTO_ADD))
            movedPath
        }.onFailure { e ->
            Log.e(TAG, "Failed to confirm ${decision.photo.path}", e)
        }

    suspend fun confirmAndMove(entity: ImageAnalysisEntity): Result<String> =
        runCatching {
            val sourcePath = entity.movedTo ?: entity.path
            val movedPath = albumManager.moveToBabyAlbum(
                path = sourcePath,
                mediaStoreId = entity.mediaStoreId,
                mimeType = entity.mimeType,
                mediaType = entity.mediaType.toMediaType()
            ).getOrThrow()
            dao.insert(
                entity.copy(
                    containsBaby = true,
                    action = ClassificationAction.AUTO_ADD.name,
                    movedTo = movedPath,
                    timestamp = System.currentTimeMillis()
                )
            )
            movedPath
        }.onFailure { e ->
            Log.e(TAG, "Failed to confirm ${entity.path}", e)
        }

    suspend fun reject(decision: ClassificationDecision) {
        dao.insert(decision.toEntity(null, overrideAction = ClassificationAction.IGNORE))
    }

    companion object {
        private const val TAG = "AnalysisRepository"
    }
}

private fun ClassificationDecision.toEntity(
    movedTo: String?,
    overrideAction: ClassificationAction? = null
): ImageAnalysisEntity {
    val action = overrideAction ?: this.action
    return ImageAnalysisEntity(
        id = hashPath(photo.path),
        path = photo.path,
        mediaStoreId = photo.id,
        mediaType = photo.mediaType.name,
        mimeType = photo.mimeType,
        containsBaby = detectionResult.containsBaby,
        confidence = detectionResult.confidence,
        reason = detectionResult.reason,
        action = action.name,
        timestamp = System.currentTimeMillis(),
        movedTo = movedTo
    )
}

private fun hashPath(path: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(path.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}

private fun String.toMediaType(): MediaType =
    runCatching { MediaType.valueOf(this) }.getOrDefault(MediaType.IMAGE)
