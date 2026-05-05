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
import com.babyphotos.archive.domain.model.ScanPhase
import com.babyphotos.archive.domain.model.ScanProgress
import com.babyphotos.archive.domain.model.ScanSummary
import com.babyphotos.archive.domain.model.ScannedPhoto
import com.babyphotos.archive.domain.preprocessor.ImagePreprocessor
import com.babyphotos.archive.domain.preprocessor.VideoFrameExtractor
import com.babyphotos.archive.domain.recognizer.BabyRecognizer
import com.babyphotos.archive.domain.scanner.computeEffectiveMediaScanLowerBound
import com.babyphotos.archive.domain.scanner.PhotoScanner
import com.babyphotos.archive.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** [removeFromBabyAlbum] 的结果：已移回磁盘，或归档文件已丢失仅更新了数据库。 */
sealed class RemoveFromBabyAlbumOutcome {
    data class MovedBack(val path: String) : RemoveFromBabyAlbumOutcome()
    data object ClearedStaleRecord : RemoveFromBabyAlbumOutcome()
}

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
    suspend fun runDailyScan(
        onProgress: ((ScanProgress) -> Unit)? = null
    ): ScanSummary = coroutineScope {
        val semaphore = Semaphore(settingsManager.concurrencyLimit)
        // 1. Scan media from scanStartDate or today（已配置起始日时支持按上次水位增量查询）
        onProgress?.invoke(ScanProgress(ScanPhase.SCANNING_MEDIA, 0, 0))
        val scanStartDate = settingsManager.scanStartDate
        val useConfiguredStart = scanStartDate > 0L
        val effectiveLower = if (useConfiguredStart) {
            computeEffectiveMediaScanLowerBound(
                configuredStartEpochSec = scanStartDate,
                snapshotAtLastScan = settingsManager.scanStartDateSnapshotAtLastScan,
                lastDateAddedWatermark = settingsManager.lastScanMediaDateAddedWatermark
            )
        } else {
            0L
        }
        val mediaItems = if (useConfiguredStart) {
            scanner.scanPhotosSince(effectiveLower)
        } else {
            scanner.scanTodayPhotos()
        }
        Log.d(
            TAG,
            "Scanned ${mediaItems.size} media items (useConfiguredStart=$useConfiguredStart effectiveLower=$effectiveLower)"
        )

        // 2. Filter out already-analyzed media（含已移动到宝宝相册后路径变化的情况）
        val newMediaItems = mediaItems.filter { media ->
            dao.getByPathOrMovedTo(media.path) == null
        }
        Log.d(TAG, "${newMediaItems.size} new media items to analyze")

        if (newMediaItems.isEmpty()) {
            commitScanCursorAfterSuccessfulScan(useConfiguredStart, scanStartDate, mediaItems)
            return@coroutineScope ScanSummary(
                totalScanned = mediaItems.size,
                newlyAnalyzed = 0,
                autoAdded = 0,
                needsConfirmation = 0,
                confirmationItems = emptyList()
            )
        }

        // 3. Process each media item with concurrency control
        onProgress?.invoke(ScanProgress(ScanPhase.ANALYZING, 0, newMediaItems.size))
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val decisions = newMediaItems.map { media ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    try {
                        val result = analyzeMedia(media)
                        val done = completedCount.incrementAndGet()
                        onProgress?.invoke(ScanProgress(ScanPhase.ANALYZING, done, newMediaItems.size))
                        result
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to analyze ${media.path}", e)
                        completedCount.incrementAndGet()
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()

        // 4. Auto-add high-confidence results
        val autoAdded = decisions.filter { it.action == ClassificationAction.AUTO_ADD }
        onProgress?.invoke(ScanProgress(ScanPhase.CLASSIFYING, 0, decisions.size))
        var autoAddedCount = 0
        val moveFailures = mutableListOf<ClassificationDecision>()
        var classifyProgress = 0
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
            classifyProgress++
            onProgress?.invoke(ScanProgress(ScanPhase.CLASSIFYING, classifyProgress, decisions.size))
        }

        // 5. Save ignored results
        decisions.filter { it.action == ClassificationAction.IGNORE }.forEach { decision ->
            dao.insert(decision.toEntity(null))
            classifyProgress++
            onProgress?.invoke(ScanProgress(ScanPhase.CLASSIFYING, classifyProgress, decisions.size))
        }

        // 6. Save needs-confirm items (don't move yet)
        val needsConfirm = decisions.filter { it.action == ClassificationAction.NEEDS_CONFIRM }
        needsConfirm.forEach { decision ->
            dao.insert(decision.toEntity(null))
            classifyProgress++
            onProgress?.invoke(ScanProgress(ScanPhase.CLASSIFYING, classifyProgress, decisions.size))
        }
        val confirmationItems = needsConfirm + moveFailures

        commitScanCursorAfterSuccessfulScan(useConfiguredStart, scanStartDate, mediaItems)
        ScanSummary(
            totalScanned = mediaItems.size,
            newlyAnalyzed = newMediaItems.size,
            autoAdded = autoAddedCount,
            needsConfirmation = confirmationItems.size,
            confirmationItems = confirmationItems
        )
    }

    /**
     * 在整次 [runDailyScan] 成功结束后更新增量游标；失败或未执行到此则不应调用。
     */
    private fun commitScanCursorAfterSuccessfulScan(
        useConfiguredStart: Boolean,
        configuredScanStartDate: Long,
        mediaItems: List<ScannedPhoto>
    ) {
        if (!useConfiguredStart) {
            settingsManager.scanStartDateSnapshotAtLastScan = 0L
            settingsManager.lastScanMediaDateAddedWatermark = 0L
            return
        }
        settingsManager.scanStartDateSnapshotAtLastScan = configuredScanStartDate
        val maxAdded = mediaItems.maxOfOrNull { it.dateAdded } ?: return
        val prev = settingsManager.lastScanMediaDateAddedWatermark
        settingsManager.lastScanMediaDateAddedWatermark = maxOf(prev, maxAdded)
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

    /**
     * 从宝宝相册移回原始目录（或同目录下自动加后缀），并标记为误识别已忽略。
     * 若归档路径上文件已不存在，不视为错误，仅更新数据库（清除归档状态并标记忽略）。
     */
    suspend fun removeFromBabyAlbum(entity: ImageAnalysisEntity): Result<RemoveFromBabyAlbumOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val archived = entity.movedTo ?: error("该记录未归档到宝宝相册")
                if (!File(archived).exists()) {
                    clearBabyAlbumRecordWithoutMove(entity)
                    return@runCatching RemoveFromBabyAlbumOutcome.ClearedStaleRecord
                }

                val moveResult = albumManager.moveBackFromBabyAlbum(
                    archivedPath = archived,
                    originalPath = entity.path,
                    mediaStoreId = entity.mediaStoreId,
                    mimeType = entity.mimeType,
                    mediaType = entity.mediaType.toMediaType()
                )
                if (moveResult.isFailure) {
                    if (!File(archived).exists()) {
                        clearBabyAlbumRecordWithoutMove(entity)
                        return@runCatching RemoveFromBabyAlbumOutcome.ClearedStaleRecord
                    }
                    moveResult.getOrThrow()
                }
                val restoredPath = moveResult.getOrThrow()
                dao.insert(
                    entity.copy(
                        path = restoredPath,
                        movedTo = null,
                        containsBaby = false,
                        action = ClassificationAction.IGNORE.name,
                        timestamp = System.currentTimeMillis()
                    )
                )
                RemoveFromBabyAlbumOutcome.MovedBack(restoredPath)
            }.onFailure { e ->
                Log.e(TAG, "Failed to remove from baby album ${entity.path}", e)
            }
        }

    private suspend fun clearBabyAlbumRecordWithoutMove(entity: ImageAnalysisEntity) {
        dao.insert(
            entity.copy(
                path = entity.path,
                movedTo = null,
                containsBaby = false,
                action = ClassificationAction.IGNORE.name,
                timestamp = System.currentTimeMillis()
            )
        )
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
