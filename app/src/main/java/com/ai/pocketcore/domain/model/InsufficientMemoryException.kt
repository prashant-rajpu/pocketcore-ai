package com.ai.pocketcore.domain.model

import java.util.Locale

/**
 * Thrown when attempting to load a model that violates the safe RAM headroom threshold.
 * Prevents system Out-Of-Memory (OOM) killer terminations on Android devices.
 *
 * @property requiredBytes Model footprint or file size in bytes.
 * @property availableBytes Available system RAM in bytes.
 */
class InsufficientMemoryException(
    val requiredBytes: Long,
    val availableBytes: Long,
    message: String = String.format(
        Locale.US,
        "Insufficient RAM headroom: model size %.2f GB exceeds 85%% safe threshold of available RAM %.2f GB",
        requiredBytes.toDouble() / (1024.0 * 1024.0 * 1024.0),
        availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    )
) : RuntimeException(message)
