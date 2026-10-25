package com.ai.pocketcore

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigurationTest {
    @Test
    fun verifyTargetArchitectureConstants() {
        val expectedAbi = "arm64-v8a"
        val expectedMinSdk = 28
        val expectedTargetSdk = 35
        assertEquals("arm64-v8a", expectedAbi)
        assertEquals(28, expectedMinSdk)
        assertEquals(35, expectedTargetSdk)
        assertEquals("arm64-v8a", BuildConstants.TARGET_ABI)
        assertEquals(28, BuildConstants.MIN_SDK)
        assertEquals(35, BuildConstants.TARGET_SDK)
    }
}
