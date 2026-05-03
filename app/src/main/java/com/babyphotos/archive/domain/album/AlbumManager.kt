package com.babyphotos.archive.domain.album

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.babyphotos.archive.domain.model.ScannedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AlbumManager(private val context: Context) {

    companion object {
        const val BABY_ALBUM_DIR = "Pictures/BabyAlbum"
    }

    suspend fun moveToBabyAlbum(photo: ScannedPhoto): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(photo.path)
                check(sourceFile.exists()) { "Source file not found: ${photo.path}" }

                val albumDir = File(Environment.getExternalStorageDirectory(), BABY_ALBUM_DIR)
                if (!albumDir.exists()) albumDir.mkdirs()

                val destFile = File(albumDir, sourceFile.name)

                // Handle duplicate filenames
                val finalDest = if (destFile.exists()) {
                    val name = sourceFile.nameWithoutExtension
                    val ext = sourceFile.extension
                    var counter = 1
                    var candidate: File
                    do {
                        candidate = File(albumDir, "${name}_${counter}.${ext}")
                        counter++
                    } while (candidate.exists())
                    candidate
                } else {
                    destFile
                }

                val moved = sourceFile.renameTo(finalDest)

                // Fallback: copy then delete (cross-mount scenario)
                if (!moved) {
                    sourceFile.copyTo(finalDest)
                    sourceFile.delete()
                }

                // Sync with MediaStore
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalDest.absolutePath, sourceFile.absolutePath),
                    arrayOf(photo.mimeType),
                    null
                )

                finalDest.absolutePath
            }
        }

    fun getBabyAlbumPath(): String =
        File(Environment.getExternalStorageDirectory(), BABY_ALBUM_DIR).absolutePath
}
