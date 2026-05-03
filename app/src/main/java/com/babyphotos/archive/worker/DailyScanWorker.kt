package com.babyphotos.archive.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babyphotos.archive.BabyPhotosApp
import com.babyphotos.archive.R
import com.babyphotos.archive.domain.album.AlbumManager
import com.babyphotos.archive.domain.classifier.ClassificationEngine
import com.babyphotos.archive.domain.preprocessor.ImagePreprocessor
import com.babyphotos.archive.domain.recognizer.BabyRecognizerImpl
import com.babyphotos.archive.domain.scanner.MediaStorePhotoScanner
import com.babyphotos.archive.data.repository.AnalysisRepository

class DailyScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily scan")

        return try {
            val app = applicationContext as BabyPhotosApp
            val repository = app.repository

            val summary = repository.runDailyScan()
            Log.d(TAG, "Scan complete: $summary")

            if (summary.needsConfirmation > 0) {
                showNotification(summary.needsConfirmation)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun showNotification(needsConfirmation: Int) {
        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "扫描结果",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("发现 $needsConfirmation 张宝宝照片")
            .setContentText("是否加入\"宝宝相册\"？")
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "DailyScanWorker"
        private const val CHANNEL_ID = "baby_photos_scan"
        private const val NOTIFICATION_ID = 1001
    }
}
