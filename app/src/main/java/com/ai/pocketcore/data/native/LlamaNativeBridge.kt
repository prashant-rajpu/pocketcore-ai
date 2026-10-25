package com.ai.pocketcore.data.native

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * JNI wrapper bridge communicating with the native C++ inference engine (`libpocketcore.so`).
 */
class LlamaNativeBridge {

    companion object {
        init {
            try {
                System.loadLibrary("pocketcore")
            } catch (e: UnsatisfiedLinkError) {
                // Expected when running on host JVM unit tests without the native ARM64 library
            }
        }
    }

    external fun nativeInitModel(
        modelPath: String,
        nGpuLayers: Int,
        contextSize: Int,
        threads: Int
    ): Long

    external fun nativeStartCompletion(
        handle: Long,
        prompt: String,
        temperature: Float,
        topP: Float,
        callback: TokenCallback
    )

    external fun nativeStopCompletion(handle: Long)

    external fun nativeReleaseModel(handle: Long)

    /**
     * Initializes a GGUF model via the native engine.
     * @return opaque pointer handle to the native engine instance.
     */
    fun initModel(
        modelPath: String,
        nGpuLayers: Int = 33,
        contextSize: Int = 2048,
        threads: Int = 4
    ): Long {
        return nativeInitModel(modelPath, nGpuLayers, contextSize, threads)
    }

    /**
     * Starts synchronous completion loop in native C++.
     */
    fun startCompletion(
        handle: Long,
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        callback: TokenCallback
    ) {
        nativeStartCompletion(handle, prompt, temperature, topP, callback)
    }

    /**
     * Signals the ongoing native completion loop to stop immediately.
     */
    fun stopCompletion(handle: Long) {
        nativeStopCompletion(handle)
    }

    /**
     * Releases native model resources and destroys the engine instance.
     */
    fun releaseModel(handle: Long) {
        nativeReleaseModel(handle)
    }

    /**
     * Asynchronously streams generated tokens via a Kotlin [Flow].
     * Executes the native loop on [Dispatchers.IO] and safely coordinates cancellation.
     */
    fun streamTokens(
        handle: Long,
        prompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): Flow<String> = callbackFlow {
        val callback = TokenCallback { token ->
            trySendBlocking(token)
        }

        val job = launch(Dispatchers.IO) {
            try {
                nativeStartCompletion(handle, prompt, temperature, topP, callback)
                channel.close()
            } catch (e: Throwable) {
                channel.close(e)
            }
        }

        awaitClose {
            nativeStopCompletion(handle)
            job.cancel()
        }
    }
}
