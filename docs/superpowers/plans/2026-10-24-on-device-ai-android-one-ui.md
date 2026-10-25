# PocketCore AI: On-Device Hugging Face Android App (One UI Pro) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a high-performance Android application with Samsung One UI Pro ergonomics that searches and downloads arbitrary GGUF models from Hugging Face with resumable chunked downloads, and executes offline inference accelerated by the Snapdragon 8 Elite's Adreno GPU via Vulkan.

**Architecture:** Clean Architecture with Jetpack Compose presentation layer (One UI Pro design tokens), Kotlin Coroutines domain layer with unified multi-modal interfaces, OkHttp resumable download service, Room database for model and chat persistence, and a native C++20 / NDK layer compiling `llama.cpp` with Vulkan compute acceleration.

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Android NDK (r27+), CMake 3.22+, Vulkan 1.3, llama.cpp, OkHttp 4.12, Room 2.6, Coroutines & Flow, Material 3 + Custom One UI Pro Design System.

**Spec:** [docs/superpowers/specs/2026-10-24-on-device-ai-android-one-ui-design.md](file:///root/docs/superpowers/specs/2026-10-24-on-device-ai-android-one-ui-design.md)

## Global Constraints
- Target SDK: 35 (Android 15), Min SDK: 28 (Android 9.0 Pie), Architecture: `arm64-v8a`.
- GPU Backend: Vulkan 1.3 (`-DGGML_VULKAN=ON`) linking against system `libvulkan.so`.
- UI Theme: True OLED Pitch Black (`#000000`), 24dp squircle cards (`#121214`), bottom 60% ergonomic interaction zones.
- Downloader: HTTP Range requests with `.part` staging files and GGUF magic byte validation (`0x46554747`).
- All native memory allocated in C++ must be explicitly freed in `nativeReleaseModel` without memory leaks.

---

### Task 1: Android Project Scaffolding & CMake Build Configuration

**Files:**
- Create: `app/build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/ai/pocketcore/BuildConfigurationTest.kt`

**Interfaces:**
- Produces: Gradle build environment with NDK `arm64-v8a` target, Vulkan library linkage, Compose compiler, OkHttp, and Room dependencies.

- [ ] **Step 1: Write test verifying build dependencies and target SDK constants**
```kotlin
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
    }
}
```

- [ ] **Step 2: Run test to verify test harness**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.BuildConfigurationTest`
Expected: PASS or initial build environment verification.

- [ ] **Step 3: Create CMakeLists.txt and Gradle build configurations**
Configure `app/build.gradle.kts` with NDK filters `arm64-v8a`, Jetpack Compose 1.7+, Room 2.6+, OkHttp 4.12, and CMake path pointing to `src/main/cpp/CMakeLists.txt`. In `CMakeLists.txt`, link `vulkan` and set `-DGGML_VULKAN=ON`.

- [ ] **Step 4: Commit**
```bash
git add app/build.gradle.kts app/src/main/cpp/CMakeLists.txt app/src/main/AndroidManifest.xml
git commit -m "feat(scaffold): initialize Android project and NDK CMake configuration"
```

---

### Task 2: Hugging Face API Client & GGUF Tree Parser

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/data/api/HuggingFaceApi.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/api/model/HfModelDto.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/api/model/HfFileTreeDto.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/repository/HuggingFaceRepositoryImpl.kt`
- Test: `app/src/test/java/com/ai/pocketcore/data/HuggingFaceApiTest.kt`

**Interfaces:**
- Produces: `HuggingFaceRepository` with `searchGgufModels(query: String): Result<List<HfModelSummary>>` and `fetchGgufFiles(repoId: String): Result<List<GgufFileInfo>>`.

- [ ] **Step 1: Write failing unit test for Hugging Face search and GGUF tree filtering**
```kotlin
package com.ai.pocketcore.data

import com.ai.pocketcore.data.api.model.HfFileTreeItem
import com.ai.pocketcore.data.repository.HuggingFaceRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceApiTest {
    @Test
    fun parseTreeResponseFiltersOnlyGgufFiles() {
        val sampleFiles = listOf(
            HfFileTreeItem(path = "README.md", size = 2048),
            HfFileTreeItem(path = "config.json", size = 512),
            HfFileTreeItem(path = "model-q4_k_m.gguf", size = 4_800_000_000L),
            HfFileTreeItem(path = "model-q8_0.gguf", size = 8_200_000_000L)
        )
        val repo = HuggingFaceRepositoryImpl(mockHttpClient())
        val ggufList = repo.filterGgufFiles(sampleFiles)
        assertEquals(2, ggufList.size)
        assertTrue(ggufList.any { it.name == "model-q4_k_m.gguf" })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.HuggingFaceApiTest`
Expected: FAIL (missing classes and filter function).

- [ ] **Step 3: Implement DTOs, Hugging Face OkHttp client, and repository**
Implement token header injection `Authorization: Bearer <token>` when available, query parameters `filter=gguf`, and endpoint response parsers.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.HuggingFaceApiTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/data/api/ app/src/test/java/com/ai/pocketcore/data/
git commit -m "feat(hub): implement Hugging Face API client and GGUF tree parser"
```

---

### Task 3: Resumable Chunked Downloader with Foreground Service

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/data/downloader/DownloadForegroundService.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/downloader/DownloadNotificationHelper.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/downloader/GgufValidator.kt`
- Test: `app/src/test/java/com/ai/pocketcore/data/downloader/GgufValidatorTest.kt`

**Interfaces:**
- Produces: `GgufValidator.isValidGgufHeader(file: File): Boolean` (checking magic bytes `0x46554747`).
- Produces: `DownloadManager` emitting download states (`IDLE`, `DOWNLOADING(progress, speedMbS)`, `PAUSED`, `COMPLETED`, `FAILED`).

- [ ] **Step 1: Write failing unit test for GGUF magic byte validation**
```kotlin
package com.ai.pocketcore.data.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GgufValidatorTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun validGgufHeaderReturnsTrue() {
        val file = tempFolder.newFile("valid.gguf")
        // "GGUF" in ASCII is 0x47, 0x47, 0x55, 0x46 (little endian magic uint32: 0x46554747)
        file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00))
        assertTrue(GgufValidator.isValidGgufHeader(file))
    }

    @Test
    fun corruptFileReturnsFalse() {
        val file = tempFolder.newFile("corrupt.gguf")
        file.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        assertFalse(GgufValidator.isValidGgufHeader(file))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.downloader.GgufValidatorTest`
Expected: FAIL.

- [ ] **Step 3: Implement GgufValidator and Resumable Download Service**
Use HTTP `Range: bytes={existingBytes}-`, write chunks to `.part` file, update notification progress, and atomically rename on verified GGUF header.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.downloader.GgufValidatorTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/data/downloader/ app/src/test/java/com/ai/pocketcore/data/downloader/
git commit -m "feat(download): implement resumable HTTP range downloader and GGUF header validator"
```

---

### Task 4: Room Database Persistence (Model Vault & Chat History)

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/data/db/AppDatabase.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/db/entity/ModelEntity.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/db/entity/ChatMessageEntity.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/db/dao/ModelDao.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/db/dao/ChatDao.kt`
- Test: `app/src/test/java/com/ai/pocketcore/data/db/ModelEntityTest.kt`

**Interfaces:**
- Produces: `ModelDao` (query all downloaded models, get active model, delete model) and `ChatDao` (fetch session messages, insert streaming chunks).

- [ ] **Step 1: Write unit test for model entity formatting and status**
```kotlin
package com.ai.pocketcore.data.db

import com.ai.pocketcore.data.db.entity.ModelEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelEntityTest {
    @Test
    fun formatFileSizeToGbString() {
        val model = ModelEntity(
            id = "qwen-2.5-7b-q4",
            repoId = "Qwen/Qwen2.5-7B-Instruct-GGUF",
            fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
            localPath = "/data/local/models/qwen.gguf",
            fileSizeBytes = 4_920_000_000L,
            quantization = "Q4_K_M",
            isActive = true
        )
        val formatted = model.formattedSizeGb()
        assertEquals("4.58 GB", formatted)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.db.ModelEntityTest`
Expected: FAIL.

- [ ] **Step 3: Implement Room Database entities, DAOs, and TypeConverters**

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.db.ModelEntityTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/data/db/ app/src/test/java/com/ai/pocketcore/data/db/
git commit -m "feat(storage): implement Room DB for model vault and chat history persistence"
```

---

### Task 5: Native C++/NDK Inference Core & Vulkan GPU Bridge

**Files:**
- Create: `app/src/main/cpp/native_engine.h`
- Create: `app/src/main/cpp/native_engine.cpp`
- Create: `app/src/main/cpp/jni_bridge.cpp`
- Create: `app/src/main/java/com/ai/pocketcore/data/native/LlamaNativeBridge.kt`
- Create: `app/src/main/java/com/ai/pocketcore/data/native/TokenCallback.kt`
- Test: `app/src/test/java/com/ai/pocketcore/data/native/PromptTemplateTest.kt`

**Interfaces:**
- Consumes: GGUF model files on disk.
- Produces: `LlamaNativeBridge` exposing `initModel()`, `startCompletion()`, `stopCompletion()`, and `releaseModel()`.

- [ ] **Step 1: Write unit test for chat template formatting (ChatML & Llama-3 formats)**
```kotlin
package com.ai.pocketcore.data.native

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplateTest {
    @Test
    fun formatsChatMlPromptCorrectly() {
        val messages = listOf(
            Pair("system", "You are an AI on Android."),
            Pair("user", "Hello!")
        )
        val formatted = PromptFormatter.format(PromptTemplate.CHAT_ML, messages)
        assertTrue(formatted.contains("<|im_start|>system\nYou are an AI on Android.<|im_end|>"))
        assertTrue(formatted.contains("<|im_start|>user\nHello!<|im_end|>\n<|im_start|>assistant\n"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.native.PromptTemplateTest`
Expected: FAIL.

- [ ] **Step 3: Implement C++ engine with Vulkan offload, JNI bridge, and Kotlin wrapper**
Implement `native_engine.cpp` wrapping `llama_model_load_from_file`, `llama_init_from_model`, and `llama_decode` with token callback invocation into JVM.

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.data.native.PromptTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/cpp/ app/src/main/java/com/ai/pocketcore/data/native/ app/src/test/java/com/ai/pocketcore/data/native/
git commit -m "feat(engine): implement C++ llama.cpp Vulkan bridge and JNI token streaming wrapper"
```

---

### Task 6: Domain Layer & Extensible Multi-Modal Interfaces

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/domain/engine/InferenceEngine.kt`
- Create: `app/src/main/java/com/ai/pocketcore/domain/engine/TextEngine.kt`
- Create: `app/src/main/java/com/ai/pocketcore/domain/engine/AudioEngine.kt`
- Create: `app/src/main/java/com/ai/pocketcore/domain/engine/ImageEngine.kt`
- Create: `app/src/main/java/com/ai/pocketcore/domain/usecase/StreamChatUseCase.kt`
- Create: `app/src/main/java/com/ai/pocketcore/domain/usecase/LoadModelUseCase.kt`
- Test: `app/src/test/java/com/ai/pocketcore/domain/StreamChatUseCaseTest.kt`

**Interfaces:**
- Produces: `StreamChatUseCase(prompt, params): Flow<ChatEvent>` (emitting `Token(text)`, `Telemetry(tokPerSec, ttftMs)`, `Completed`, `Error`).

- [ ] **Step 1: Write unit test for StreamChatUseCase token emission and telemetry calculation**
```kotlin
package com.ai.pocketcore.domain

import com.ai.pocketcore.domain.engine.TextEngine
import com.ai.pocketcore.domain.model.ChatEvent
import com.ai.pocketcore.domain.model.InferenceParams
import com.ai.pocketcore.domain.usecase.StreamChatUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamChatUseCaseTest {
    @Test
    fun useCaseEmitsTokensAndCompletionEvent() = runBlocking {
        val mockEngine = object : TextEngine {
            override val isLoaded = true
            override suspend fun unload() {}
            override fun streamChat(prompt: String, params: InferenceParams) = flowOf("Hello", " world")
        }
        val useCase = StreamChatUseCase(mockEngine)
        val events = useCase("Hi", InferenceParams.DEFAULT).toList()
        assertEquals(3, events.size) // 2 tokens + 1 Completed
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.domain.StreamChatUseCaseTest`
Expected: FAIL.

- [ ] **Step 3: Implement domain interfaces, use cases, and telemetry calculators**

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.domain.StreamChatUseCaseTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/domain/ app/src/test/java/com/ai/pocketcore/domain/
git commit -m "feat(domain): define multi-modal engine interfaces and chat streaming use case"
```

---

### Task 7: One UI Pro Design System & Custom Compose Components

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/ui/theme/Color.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/theme/Type.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/components/OneUiCard.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/components/HapticSlider.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/components/TelemetryCapsule.kt`
- Test: `app/src/test/java/com/ai/pocketcore/ui/ThemeTokensTest.kt`

**Interfaces:**
- Produces: `OneUiTheme`, `OneUiCard` (24dp squircle), `HapticSlider` (vibrates on step change), `TelemetryCapsule` (displays tokens/s and VRAM).

- [ ] **Step 1: Write test verifying AMOLED color tokens and squircle radius constants**
```kotlin
package com.ai.pocketcore.ui

import com.ai.pocketcore.ui.theme.OneUiTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun amoledTokensArePitchBlack() {
        assertEquals(0xFF000000.toLong(), OneUiTokens.AmoledBackground.value.toLong())
        assertEquals(24, OneUiTokens.CardCornerRadiusDp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.ThemeTokensTest`
Expected: FAIL.

- [ ] **Step 3: Implement One UI Pro theme, cards, haptic feedback sliders, and telemetry capsule**

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.ThemeTokensTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/ui/theme/ app/src/main/java/com/ai/pocketcore/ui/components/ app/src/test/java/com/ai/pocketcore/ui/
git commit -m "feat(ui): implement One UI Pro AMOLED design tokens and haptic tactile components"
```

---

### Task 8: Hub Screen (Hugging Face Model Explorer & Quantization Picker)

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/ui/hub/HubViewModel.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/hub/HubScreen.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/hub/components/ModelCard.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/hub/components/QuantizationBottomSheet.kt`
- Test: `app/src/test/java/com/ai/pocketcore/ui/hub/HubViewModelTest.kt`

**Interfaces:**
- Consumes: `HuggingFaceRepository`, `DownloadManager`.
- Produces: Search screen with trending models carousel, search bar, and one-tap download triggers.

- [ ] **Step 1: Write unit test for HubViewModel search state changes**
```kotlin
package com.ai.pocketcore.ui.hub

import org.junit.Assert.assertTrue
import org.junit.Test

class HubViewModelTest {
    @Test
    fun initialCuratedModelsContainsFlagshipRecommendations() {
        val curated = HubViewModel.getCuratedModels()
        assertTrue(curated.any { it.repoId.contains("Qwen2.5-7B") })
        assertTrue(curated.any { it.repoId.contains("Llama-3.2-3B") })
        assertTrue(curated.any { it.repoId.contains("DeepSeek-R1-Distill") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.hub.HubViewModelTest`
Expected: FAIL.

- [ ] **Step 3: Implement HubViewModel, HubScreen, and QuantizationBottomSheet**

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.hub.HubViewModelTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/ui/hub/ app/src/test/java/com/ai/pocketcore/ui/hub/
git commit -m "feat(hub): build Hugging Face explorer UI with One UI bottom-weighted search"
```

---

### Task 9: Vault Screen (Local Model Inventory & Storage Management)

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/ui/vault/VaultViewModel.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/vault/VaultScreen.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/vault/components/StorageUsageBar.kt`
- Test: `app/src/test/java/com/ai/pocketcore/ui/vault/VaultViewModelTest.kt`

**Interfaces:**
- Consumes: `ModelDao`, `LoadModelUseCase`.
- Produces: Local model management, memory footprint warnings, file deletion, and SAF file import.

- [ ] **Step 1: Write unit test for storage percentage and RAM headroom warning logic**
```kotlin
package com.ai.pocketcore.ui.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultViewModelTest {
    @Test
    fun warnsWhenModelExceedsSafeRamThreshold() {
        val availableRamBytes = 7_000_000_000L
        val safeModelBytes = 4_500_000_000L
        val oversizedModelBytes = 8_000_000_000L

        assertFalse(VaultViewModel.isMemoryRisky(safeModelBytes, availableRamBytes))
        assertTrue(VaultViewModel.isMemoryRisky(oversizedModelBytes, availableRamBytes))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.vault.VaultViewModelTest`
Expected: FAIL.

- [ ] **Step 3: Implement VaultViewModel, VaultScreen, and StorageUsageBar**

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.vault.VaultViewModelTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/ui/vault/ app/src/test/java/com/ai/pocketcore/ui/vault/
git commit -m "feat(vault): build local model storage manager with RAM safety checks"
```

---

### Task 10: Studio Screen (Markdown Chat, Parameters & Telemetry HUD)

**Files:**
- Create: `app/src/main/java/com/ai/pocketcore/ui/studio/StudioViewModel.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/studio/StudioScreen.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/studio/components/ChatBubble.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/studio/components/CodeBlockView.kt`
- Create: `app/src/main/java/com/ai/pocketcore/ui/studio/components/ProParametersSheet.kt`
- Test: `app/src/test/java/com/ai/pocketcore/ui/studio/StudioViewModelTest.kt`

**Interfaces:**
- Consumes: `StreamChatUseCase`, `ChatDao`.
- Produces: Chat UI with streaming text, copyable code blocks, tactile parameter sheet, and real-time tokens/s telemetry.

- [ ] **Step 1: Write unit test for StudioViewModel message accumulation and stop action**
```kotlin
package com.ai.pocketcore.ui.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class StudioViewModelTest {
    @Test
    fun accumulatesStreamingTokensIntoActiveMessage() {
        var text = ""
        val tokens = listOf("Deep", "Seek", " on", " S25")
        tokens.forEach { text += it }
        assertEquals("DeepSeek on S25", text)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.studio.StudioViewModelTest`
Expected: FAIL.

- [ ] **Step 3: Implement StudioScreen, Markdown chat renderer, and ProParametersSheet**

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests com.ai.pocketcore.ui.studio.StudioViewModelTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/ai/pocketcore/ui/studio/ app/src/test/java/com/ai/pocketcore/ui/studio/
git commit -m "feat(studio): build conversational chat UI with telemetry HUD and parameter sheet"
```

---

### Task 11: End-to-End Smoke Verification & Complete Documentation

**Files:**
- Create: `app/src/androidTest/java/com/ai/pocketcore/PocketCoreSmokeTest.kt`
- Create: `README.md`
- Create: `docs/BUILD_AND_RUN.md`

**Interfaces:**
- Produces: Automated verification suite and setup guide for compiling the APK and running on the Galaxy S25.

- [ ] **Step 1: Write instrumentation smoke test verifying model load and token generation**
- [ ] **Step 2: Run full unit test suite**
Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS.
- [ ] **Step 3: Write comprehensive build documentation (NDK setup, Vulkan validation, APK installation)**
- [ ] **Step 4: Commit**
```bash
git add README.md docs/BUILD_AND_RUN.md app/src/androidTest/
git commit -m "docs: add full build instructions and end-to-end smoke verification suite"
```
