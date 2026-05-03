package com.babyphotos.archive.domain.preprocessor

import android.media.MediaMetadataRetriever
import com.babyphotos.archive.domain.model.PreprocessedImage
import com.babyphotos.archive.domain.model.ScannedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoFrameExtractor(
    private val imagePreprocessor: ImagePreprocessor,
    private val frameCount: Int = 3
) {
    suspend fun extractFrames(video: ScannedPhoto): List<PreprocessedImage> =
        withContext(Dispatchers.Default) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.path)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L
                val frameTimesUs = calculateFrameTimesUs(durationMs)

                frameTimesUs.mapNotNull { timeUs ->
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { bitmap ->
                            imagePreprocessor.preprocessBitmap("${video.path}#frame=$timeUs", bitmap)
                        }
                }
            } finally {
                retriever.release()
            }
        }

    private fun calculateFrameTimesUs(durationMs: Long): List<Long> {
        if (durationMs <= 0L) return listOf(0L)

        return (1..frameCount).map { index ->
            durationMs * 1_000L * index / (frameCount + 1)
        }
    }
}
