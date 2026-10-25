package com.ai.pocketcore.data.downloader

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground Service that handles long-running GGUF model downloads, keeping the device awake
 * and providing interactive notifications (Pause, Resume, Cancel).
 */
class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    private var stateObserverJob: Job? = null

    private lateinit var notificationHelper: DownloadNotificationHelper
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentUrl: String = ""
    private var currentFileName: String = ""
    private var currentDestDir: String = ""
    private var currentAuthToken: String? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PocketCore:DownloadForegroundServiceWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL).orEmpty()
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
                val destDir = intent.getStringExtra(EXTRA_DESTINATION_DIR).orEmpty()
                val authToken = intent.getStringExtra(EXTRA_AUTH_TOKEN)

                if (url.isNotBlank() && fileName.isNotBlank()) {
                    currentUrl = url
                    currentFileName = fileName
                    currentDestDir = destDir
                    currentAuthToken = authToken
                    startModelDownload(url, fileName, destDir, authToken)
                }
            }
            ACTION_PAUSE -> {
                pauseDownload()
            }
            ACTION_RESUME -> {
                resumeDownload()
            }
            ACTION_CANCEL -> {
                cancelDownload()
            }
        }

        return START_NOT_STICKY
    }

    private fun startModelDownload(
        url: String,
        fileName: String,
        destinationDirPath: String,
        authToken: String?
    ) {
        acquireWakeLock()

        val destDir = if (destinationDirPath.isNotBlank()) {
            File(destinationDirPath)
        } else {
            File(filesDir, "models")
        }

        // Display initial notification and enter foreground
        val initialNotification = notificationHelper.buildProgressNotification(
            fileName = fileName,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = -1L,
            speedMbS = 0.0,
            etaSeconds = -1L,
            isPaused = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DownloadNotificationHelper.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloadNotificationHelper.NOTIFICATION_ID, initialNotification)
        }

        // Observe downloader state updates
        val downloader = sharedDownloader
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            downloader.state.collectLatest { state ->
                DownloadManager.updateSharedState(state)
                when (state) {
                    is DownloadState.Downloading -> {
                        val notification = notificationHelper.buildProgressNotification(
                            fileName = fileName,
                            progress = state.progress,
                            downloadedBytes = state.downloadedBytes,
                            totalBytes = state.totalBytes,
                            speedMbS = state.speedMbS,
                            etaSeconds = state.etaSeconds,
                            isPaused = false
                        )
                        notificationHelper.updateNotification(
                            DownloadNotificationHelper.NOTIFICATION_ID,
                            notification
                        )
                    }
                    is DownloadState.Paused -> {
                        val notification = notificationHelper.buildProgressNotification(
                            fileName = fileName,
                            progress = state.progress,
                            downloadedBytes = state.downloadedBytes,
                            totalBytes = state.totalBytes,
                            speedMbS = 0.0,
                            etaSeconds = -1L,
                            isPaused = true
                        )
                        notificationHelper.updateNotification(
                            DownloadNotificationHelper.NOTIFICATION_ID,
                            notification
                        )
                        releaseWakeLock()
                    }
                    is DownloadState.Completed -> {
                        releaseWakeLock()
                        val notification = notificationHelper.buildCompletedNotification(
                            fileName = fileName,
                            totalBytes = state.totalBytes
                        )
                        notificationHelper.updateNotification(
                            DownloadNotificationHelper.NOTIFICATION_ID,
                            notification
                        )
                        stopForegroundCompat(false)
                        stopSelf()
                    }
                    is DownloadState.Failed -> {
                        releaseWakeLock()
                        val notification = notificationHelper.buildFailedNotification(
                            fileName = fileName,
                            errorMessage = state.error
                        )
                        notificationHelper.updateNotification(
                            DownloadNotificationHelper.NOTIFICATION_ID,
                            notification
                        )
                        stopForegroundCompat(false)
                        stopSelf()
                    }
                    is DownloadState.Idle -> {
                        releaseWakeLock()
                        stopForegroundCompat(true)
                        stopSelf()
                    }
                }
            }
        }

        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            downloader.download(
                downloadUrl = url,
                fileName = fileName,
                destinationDir = destDir,
                authToken = authToken
            )
        }
    }

    private fun pauseDownload() {
        sharedDownloader.pause()
        releaseWakeLock()
    }

    private fun resumeDownload() {
        if (currentUrl.isNotBlank() && currentFileName.isNotBlank()) {
            startModelDownload(currentUrl, currentFileName, currentDestDir, currentAuthToken)
        }
    }

    private fun cancelDownload() {
        val destDir = if (currentDestDir.isNotBlank()) File(currentDestDir) else File(filesDir, "models")
        val partFile = File(destDir, "$currentFileName.part")
        sharedDownloader.cancel(partFile)
        downloadJob?.cancel()
        stateObserverJob?.cancel()
        releaseWakeLock()
        notificationHelper.cancelNotification(DownloadNotificationHelper.NOTIFICATION_ID)
        stopForegroundCompat(true)
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes safe timeout
            }
        } catch (_: SecurityException) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: SecurityException) {}
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            stopForeground(flags)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        downloadJob?.cancel()
        stateObserverJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.ai.pocketcore.downloader.action.START"
        const val ACTION_PAUSE = "com.ai.pocketcore.downloader.action.PAUSE"
        const val ACTION_RESUME = "com.ai.pocketcore.downloader.action.RESUME"
        const val ACTION_CANCEL = "com.ai.pocketcore.downloader.action.CANCEL"

        const val EXTRA_URL = "com.ai.pocketcore.downloader.extra.URL"
        const val EXTRA_FILE_NAME = "com.ai.pocketcore.downloader.extra.FILE_NAME"
        const val EXTRA_DESTINATION_DIR = "com.ai.pocketcore.downloader.extra.DESTINATION_DIR"
        const val EXTRA_AUTH_TOKEN = "com.ai.pocketcore.downloader.extra.AUTH_TOKEN"

        /**
         * Singleton downloader instance shared between service and manager.
         */
        val sharedDownloader = ResumableDownloader()

        fun start(
            context: Context,
            url: String,
            fileName: String,
            destinationDir: String? = null,
            authToken: String? = null
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_DESTINATION_DIR, destinationDir)
                putExtra(EXTRA_AUTH_TOKEN, authToken)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
