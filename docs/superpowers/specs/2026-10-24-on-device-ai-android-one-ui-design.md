# PocketCore AI: On-Device Hugging Face Multi-Modal Android Application (One UI Pro)
**Date:** 2026-10-24  
**Target Hardware:** Samsung Galaxy S25 (12GB Physical LPDDR5X RAM, Snapdragon 8 Elite, Adreno 830 GPU)  
**OS Target:** Android 15+ (API 35, Min SDK 28)  
**Primary Stack:** Kotlin, Jetpack Compose (One UI Pro Design System), C++20 / Android NDK, CMake, Vulkan Compute  

---

## 1. Executive Summary & Goals
PocketCore AI is a high-performance, 100% offline-first Android application designed to download, manage, and run arbitrary open-source AI models from Hugging Face directly on-device. Tailored specifically for modern flagship mobile silicon (Snapdragon 8 Elite), the app delivers hardware-accelerated local inference (Vulkan GPU offload) while providing an ergonomic, Samsung One UI Pro design experience built with Jetpack Compose.

### Key Objectives
1. **Hugging Face Hub Integration:** Search, inspect, and stream-download GGUF quantized models directly from Hugging Face with chunked resumable downloads and optional access token authentication for gated models.
2. **Native Vulkan Acceleration:** Compile `llama.cpp` using the Android NDK with ARM NEON SIMD and Vulkan 1.3 compute backend, offloading model weights to the Adreno GPU to achieve 40–60+ tokens/sec on 3B models and 15–25 tokens/sec on 7B/8B models.
3. **One UI Pro Experience:** Ergonomic bottom-weighted layout tailored for large-screen one-handed reachability, tactile haptic parameter dials, true AMOLED dark theme (`#000000`), and a real-time hardware telemetry HUD.
4. **Multi-Modal Extensibility:** Unified modular architecture engineered to seamlessly integrate speech-to-text (`whisper.cpp`) and text-to-image generation (`stable-diffusion.cpp`) in subsequent phases.

---

## 2. System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Presentation Layer (Jetpack Compose)                 │
│              One UI Pro Theme · AMOLED · Haptic Feedback Controls       │
│                                                                        │
│   ┌─────────────────────┐  ┌────────────────────┐  ┌────────────────┐ │
│   │   Hub (HF Search)   │  │ Vault (Model Store)│  │ Studio (Chat)  │ │
│   └──────────┬──────────┘  └─────────┬──────────┘  └────────┬───────┘ │
└──────────────┼───────────────────────┼──────────────────────┼──────────┘
               │                       │                      │
┌──────────────▼───────────────────────▼──────────────────────▼──────────┐
│                             Domain Layer                               │
│  UseCases: SearchModelsUseCase, DownloadModelUseCase, LoadModelUseCase,│
│            StreamCompletionUseCase, MonitorTelemetryUseCase            │
│  Interfaces: TextEngine, AudioEngine, ImageEngine, ModelRepository     │
└──────────────┬──────────────────────────────────────────────┬──────────┘
               │                                              │
┌──────────────▼──────────────────────────┐  ┌────────────────▼──────────┐
│              Data Layer                 │  │       Native Engine       │
│ - HuggingFaceApiService (Ktor/OkHttp)   │  │ - libpocketcore.so (NDK)  │
│ - ResumableDownloadService (Foreground) │  │ - JNI Bridge              │
│ - Room Database (Models & Chat History) │  │ - llama.cpp + GGML Core   │
│ - DataStore (Preferences & Tokens)      │  │ - Vulkan Compute Backend  │
│ - Local Storage (Scoped Filesystem)     │  │ - Adreno GPU Offload      │
└─────────────────────────────────────────┘  └───────────────────────────┘
```

---

## 3. Detailed Component Specifications

### 3.1. Native C++ & Hardware Acceleration Layer (`app/src/main/cpp`)
- **Engine Core:** Vendored or submodule `llama.cpp` compiled with C++20 via CMake.
- **CMake Configuration Flags:**
  - `CMAKE_ANDROID_ARCH_ABI=arm64-v8a`
  - `GGML_VULKAN=ON` (links against NDK `libvulkan.so`)
  - `GGML_OPENMP=ON` (multi-threaded CPU operations)
  - `CMAKE_BUILD_TYPE=Release`
  - Optimization flags: `-O3 -march=armv8.2-a+fp16+dotprod`
- **JNI Interface (`jni_bridge.cpp`):**
  - `nativeInitModel(modelPath: String, nGpuLayers: Int, contextSize: Int, threads: Int): Long`: Loads GGUF model into memory and returns an opaque 64-bit pointer to the C++ runtime context.
  - `nativeStartCompletion(contextPtr: Long, prompt: String, temperature: Float, topP: Float, tokenCallback: JniTokenCallback)`: Executes generation loop on background native thread, invoking `tokenCallback.onToken(token)` per generated token.
  - `nativeStopCompletion(contextPtr: Long)`: Sets atomic boolean flag in C++ loop to stop generation immediately without blocking.
  - `nativeReleaseModel(contextPtr: Long)`: Safely frees KV cache, model weights, and Vulkan compute buffers.
- **OOM Shield & Pre-allocation:** Prior to invocation, the Kotlin layer verifies available RAM via `ActivityManager.MemoryInfo`. If predicted memory exceeds `availMem * 0.85`, it rejects loading with `InsufficientMemoryException`.

### 3.2. Hugging Face Hub & Download Engine
- **REST Client:** Uses OkHttp/Ktor client.
- **Hub Endpoints:**
  - Search: `GET https://huggingface.co/api/models?search={query}&filter=gguf&sort=downloads&direction=-1&limit=30`
  - Tree & Quantization Inspector: `GET https://huggingface.co/api/models/{repo_id}/tree/main`
- **Authentication:** In Settings, users can store a Hugging Face User Access Token (Fine-grained or Read) stored encrypted via Android `EncryptedSharedPreferences`. Sent as `Authorization: Bearer <token>`.
- **Resumable Downloader (`DownloadForegroundService`):**
  - Uses `Range: bytes={existing_bytes}-` headers.
  - Writes to temporary `.gguf.part` files.
  - Upon completion, validates GGUF magic bytes (`0x46554747` / "GGUF") before renaming to `.gguf`.
  - Android notification with `setProgress(max, current, false)` and action buttons for Pause / Cancel / Resume.

### 3.3. One UI Pro Presentation Layer
- **Layout Paradigm (Samsung Ergonomic Split):**
  - **Upper 35-40%:** Display and visualization zone (Conversation thread, model overview cards, hardware HUD).
  - **Lower 60-65%:** Interaction and control zone (Prompts, input bars, parameter dials, tabs).
- **Design Tokens:**
  - Background: Pitch AMOLED Black (`#000000`).
  - Card Surfaces: Deep Charcoal (`#121214`) with 24dp rounded corners.
  - Accent Colors: Electric Cobalt (`#0D6EFD`) and Cyber Cyan (`#00F2FE`).
  - Monospace Telemetry: Roboto Mono / JetBrains Mono for token speed and RAM counters.
- **Key Screens:**
  1. **Hub Screen:** Curated carousel (Qwen 2.5, Llama 3.2, DeepSeek-R1-Distill) + search bar + filter chips + expandable quantization picker sheet.
  2. **Vault Screen:** Installed model cards, storage usage bar chart, "Active" indicator pill, unload/load buttons, and custom model import via Android SAF.
  3. **Studio Screen:**
     - Chat bubble stream with rich Markdown, LaTeX, and syntax-highlighted code blocks with copy action.
     - Telemetry Capsule at top right: `⚡ XX tok/s | ⏱ TTFT: XX ms | 💾 X.X GB | GPU: Vulkan`.
     - Bottom interaction sheet with tactile dials for Temperature, Top-P, Context Length, and GPU Offload with Android haptic tick feedback (`CLOCK_TICK`).

---

## 4. Multi-Modal Roadmap (Sub-Projects 2 & 3)
The domain layer defines extensible engine abstractions:
```kotlin
interface InferenceEngine {
    val isLoaded: Boolean
    suspend fun unload()
}

interface TextEngine : InferenceEngine {
    fun streamChat(prompt: String, params: InferenceParams): Flow<String>
}

// Sub-Project 2 Integration Point
interface AudioEngine : InferenceEngine {
    suspend fun transcribe(audioWavFile: File): Flow<String>
}

// Sub-Project 3 Integration Point
interface ImageEngine : InferenceEngine {
    suspend fun generateImage(prompt: String, steps: Int, cfg: Float): Bitmap
}
```
All three engines share GGML memory layouts and Vulkan backend contexts, allowing seamless unloading and swapping without fragmentation.

---

## 5. Error Handling & Edge Cases
1. **Network Drops:** Download worker transitions to `PAUSED_RECOVERABLE`. When connectivity returns, worker resumes at the exact byte index.
2. **Corrupted Models:** If GGUF magic bytes or metadata table are invalid, `nativeInitModel` throws `CorruptedModelException` and provides a one-tap "Repair / Redownload" action.
3. **Thermal Throttling / Device Sleep:** Inference service acquires a partial `WakeLock` to keep CPU/GPU running while streaming tokens in the background. If thermal state reaches `THERMAL_STATUS_SEVERE`, the engine scales down thread count by 2.
4. **App Killed / Backgrounded:** Download state is persisted in Room DB; on app restart, active downloads are displayed with "Resume" options.

---

## 6. Testing Strategy
- **Unit Tests (`app/src/test`):**
  - Hugging Face API parsing (tree, models, sizes).
  - Download chunk calculation, byte range math, and resume state machine.
  - Inference parameter clamping and prompt template formatting (ChatML, Llama-3, Alpaca).
- **Integration Tests (`app/src/androidTest`):**
  - Room DB migration, model entity creation, chat thread persistence.
  - JNI Smoke Test: Load a bundled 45MB test GGUF model (`Qwen2.5-0.5B-Q2_K.gguf`), run 16-token generation, verify token callbacks, and clean release without memory leak.
