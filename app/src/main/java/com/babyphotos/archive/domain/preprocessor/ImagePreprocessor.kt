package com.babyphotos.archive.domain.preprocessor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.babyphotos.archive.domain.model.PreprocessedImage
import com.babyphotos.archive.domain.model.ScannedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ImagePreprocessor(
    private val maxSize: Int = 1024,
    private val jpegQuality: Int = 70
) {
    suspend fun preprocess(photo: ScannedPhoto): PreprocessedImage =
        withContext(Dispatchers.Default) {
            val originalPath = photo.path

            // Step 1: Decode with inJustDecodeBounds to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(originalPath, options)

            val width = options.outWidth
            val height = options.outHeight

            // Step 2: Calculate inSampleSize for initial downscale
            options.inSampleSize = calculateInSampleSize(width, height, maxSize)
            options.inJustDecodeBounds = false

            // Step 3: Decode with sample size
            val sampledBitmap = BitmapFactory.decodeFile(originalPath, options) ?: run {
                // Fallback: return minimal placeholder if decode fails
                return@withContext PreprocessedImage(
                    originalPath = originalPath,
                    base64Data = "",
                    compressedSize = 0
                )
            }

            // Step 4: Fine-scale to exact target size
            val scaledBitmap = scaleBitmap(sampledBitmap, maxSize)
            if (sampledBitmap !== scaledBitmap) {
                sampledBitmap.recycle()
            }

            // Step 5: Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
            scaledBitmap.recycle()

            val byteArray = outputStream.toByteArray()

            // Step 6: Base64 encode with data URI prefix
            val base64Str = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            val dataUri = "data:image/jpeg;base64,$base64Str"

            PreprocessedImage(
                originalPath = originalPath,
                base64Data = dataUri,
                compressedSize = byteArray.size
            )
        }

    private fun calculateInSampleSize(
        width: Int, height: Int, targetSize: Int
    ): Int {
        var inSampleSize = 1
        if (width > targetSize || height > targetSize) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= targetSize &&
                (halfHeight / inSampleSize) >= targetSize
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
