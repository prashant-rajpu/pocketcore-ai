package com.ai.pocketcore

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.ai.pocketcore.data.api.HuggingFaceApi
import com.ai.pocketcore.data.db.AppDatabase
import com.ai.pocketcore.data.downloader.DownloadManager
import com.ai.pocketcore.data.native.LlamaTextEngine
import com.ai.pocketcore.data.repository.HuggingFaceRepository
import com.ai.pocketcore.data.repository.HuggingFaceRepositoryImpl
import com.ai.pocketcore.domain.usecase.LoadModelUseCase
import com.ai.pocketcore.domain.usecase.StreamChatUseCase
import okhttp3.OkHttpClient
import java.io.File

/**
 * Main application class maintaining singleton services and dependencies.
 */
class PocketCoreApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    val hfApi: HuggingFaceApi by lazy { HuggingFaceApi(client = okHttpClient) }

    val hfRepository: HuggingFaceRepository by lazy { HuggingFaceRepositoryImpl(api = hfApi) }

    val downloadManager: DownloadManager by lazy { DownloadManager.create(this) }

    val textEngine: LlamaTextEngine by lazy { LlamaTextEngine() }

    val loadModelUseCase: LoadModelUseCase by lazy {
        LoadModelUseCase(
            modelDao = database.modelDao(),
            engine = textEngine
        )
    }

    val streamChatUseCase: StreamChatUseCase by lazy {
        StreamChatUseCase(textEngine = textEngine)
    }

    val modelsDirectory: File by lazy {
        File(filesDir, "models").apply { if (!exists()) mkdirs() }
    }

    /**
     * Queries device physical and available RAM in bytes via [ActivityManager.MemoryInfo].
     */
    fun getMemoryInfo(): Pair<Long, Long> {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val available = if (memInfo.availMem > 0L) memInfo.availMem else 10_000_000_000L
        val total = if (memInfo.totalMem > 0L) memInfo.totalMem else 12_000_000_000L
        return Pair(available, total)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: PocketCoreApplication
            private set
    }
}
