package com.ai.pocketcore.data.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Helper to build and manage Android notifications for [DownloadForegroundService].
 */
class DownloadNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of GGUF model downloads."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds ongoing foreground progress notification with Pause/Resume and Cancel actions.
     */
    fun buildProgressNotification(
        fileName: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speedMbS: Double,
        etaSeconds: Long,
        isPaused: Boolean
    ): Notification {
        val progressPercent = (progress * 100).toInt().coerceIn(0, 100)
        val title = if (isPaused) "Paused: $fileName" else "Downloading $fileName"

        val content = if (isPaused) {
            "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)} ($progressPercent%)"
        } else {
            val speedText = formatSpeed(speedMbS)
            val etaText = formatEta(etaSeconds)
            "$speedText • ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)} • ETA: $etaText"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)

        if (totalBytes > 0L) {
            builder.setProgress(100, progressPercent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (isPaused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                createActionPendingIntent(context, DownloadForegroundService.ACTION_RESUME, REQUEST_CODE_RESUME)
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                createActionPendingIntent(context, DownloadForegroundService.ACTION_PAUSE, REQUEST_CODE_PAUSE)
            )
        }

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            createActionPendingIntent(context, DownloadForegroundService.ACTION_CANCEL, REQUEST_CODE_CANCEL)
        )

        return builder.build()
    }

    /**
     * Builds completion notification when file is downloaded and verified.
     */
    fun buildCompletedNotification(fileName: String, totalBytes: Long): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("$fileName (${formatBytes(totalBytes)}) is verified and ready")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
    }

    /**
     * Builds error notification if download or verification failed.
     */
    fun buildFailedNotification(fileName: String, errorMessage: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$fileName: $errorMessage")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
    }

    fun updateNotification(notificationId: Int, notification: Notification) {
        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    companion object {
        const val CHANNEL_ID = "pocketcore_model_downloads"
        const val CHANNEL_NAME = "Model Downloads"
        const val NOTIFICATION_ID = 2001

        const val REQUEST_CODE_PAUSE = 101
        const val REQUEST_CODE_RESUME = 102
        const val REQUEST_CODE_CANCEL = 103

        private fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups >= units.size) digitGroups = units.size - 1
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return String.format(Locale.US, "%.2f %s", value, units[digitGroups])
        }

        fun formatSpeed(speedMbS: Double): String {
            return String.format(Locale.US, "%.1f MB/s", speedMbS.coerceAtLeast(0.0))
        }

        fun formatEta(etaSeconds: Long): String {
            if (etaSeconds < 0L) return "--"
            return when {
                etaSeconds < 60L -> "${etaSeconds}s"
                etaSeconds < 3600L -> "${etaSeconds / 60L}m ${etaSeconds % 60L}s"
                else -> "${etaSeconds / 3600L}h ${(etaSeconds % 3600L) / 60L}m"
            }
        }
    }
}
