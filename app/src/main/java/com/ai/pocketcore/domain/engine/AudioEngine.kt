package com.ai.pocketcore.domain.engine

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Audio transcription engine interface for automatic speech recognition (Whisper).
 * Extends [InferenceEngine] for multi-modal speech-to-text processing.
 */
interface AudioEngine : InferenceEngine {
    /**
     * Transcribes an input WAV audio file into a stream of text chunks.
     *
     * @param audioWavFile Input audio file (standard 16kHz mono WAV).
     * @return Flow emitting transcribed text segments.
     */
    suspend fun transcribe(audioWavFile: File): Flow<String>
}
