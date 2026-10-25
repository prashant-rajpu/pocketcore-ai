# PocketCore AI: Build, Compilation & Run Guide

This document provides a comprehensive, step-by-step guide to setting up your development environment, compiling the native C++20 Vulkan inference engine, building the Android APK, and deploying/running PocketCore AI on physical **Samsung Galaxy S25** hardware (or any compatible ARM64-v8a Android 15+ device).

---

## 1. System Requirements & Prerequisites

### 1.1. Host Development Environment
- **Operating System:**
  - Linux (Ubuntu 22.04 LTS / 24.04 LTS, Debian 12+, Fedora 39+, Arch Linux) on `x86_64`
  - macOS (macOS 14 Sonoma or macOS 15 Sequoia) on Apple Silicon (M1/M2/M3/M4) or Intel `x86_64`
  - Windows 11 (64-bit) with native command line or WSL2 (Ubuntu 22.04+)
  > **Note on NDK Host Toolchains:** The prebuilt Android NDK clang compiler binaries provided by Google are compiled for `linux-x86_64`, `darwin-x86_64` / `darwin-aarch64`, and `windows-x86_64`. When building on Linux, ensure you build on an `x86_64` host or use an appropriate toolchain container.

- **Java Development Kit (JDK):**
  - **OpenJDK 21** or **Oracle JDK 21** (JDK 21 LTS is enforced in `app/build.gradle.kts` via `JavaVersion.VERSION_21`).
  - Verify your active JDK:
    ```bash
    java -version
    # Expected output: openjdk version "21.x.x" ...
    ```

- **Android Studio:**
  - Recommended: **Android Studio Ladybug (2024.2+)**, **Koala Feature Drop (2024.1+)**, or newer.
  - Bundled with Android Gradle Plugin 8.5.2 support and Compose tooling.

- **Android SDK Components:**
  - **Android SDK Platform 35** (Android 15)
  - **Android SDK Build-Tools 35.0.0**
  - **Android SDK Platform-Tools 35.0.0+** (`adb`, `fastboot`)
  - **CMake 3.22.1** (Installed via Android Studio SDK Manager -> SDK Tools -> CMake)
  - **Android NDK r26b** (`26.1.10909125`) or **NDK r27+**

### 1.2. Physical Target Hardware
- **Primary Target:** **Samsung Galaxy S25 / S25+ / S25 Ultra**
  - **SoC:** Qualcomm Snapdragon 8 Elite (Oryon CPU architecture)
  - **GPU:** Qualcomm Adreno 830 (Vulkan 1.3 compute support)
  - **Memory:** 12 GB unified LPDDR5X RAM
  - **Storage:** UFS 4.0 internal storage
  - **Operating System:** Android 15 with One UI 7.0+
- **Minimum Specifications:** Any Android device with `minSdk = 28` (Android 9.0+), `arm64-v8a` ABI, and Vulkan 1.1+ support.

---

## 2. Project Setup & Local Configuration

### 2.1. Clone Repository
```bash
git clone https://github.com/ai-pocketcore/pocketcore.git
cd pocketcore
```

### 2.2. Configure SDK & NDK Paths
Create a `local.properties` file in the project root directory specifying the location of your Android SDK and NDK:

```properties
## local.properties
sdk.dir=/Users/username/Library/Android/sdk
# or on Linux: sdk.dir=/home/username/Android/Sdk
# or on Windows: sdk.dir=C\:\\Users\\username\\AppData\\Local\\Android\\Sdk

ndk.dir=/Users/username/Library/Android/sdk/ndk/26.1.10909125
```

Alternatively, you can export environment variables in your shell profile:
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/26.1.10909125
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

### 2.3. Native C++ & Vulkan Build Flags
PocketCore AI configures CMake via `app/build.gradle.kts`:

```kotlin
externalNativeBuild {
    cmake {
        cppFlags("-std=c++20", "-O3", "-march=armv8.2-a+fp16+dotprod")
        arguments(
            "-DGGML_VULKAN=ON",
            "-DANDROID_STL=c++_shared"
        )
    }
}
```

These flags ensure:
1. **C++20 Compliance:** Enables modern synchronization primitives (`std::atomic`, `std::latch`), memory buffers, and coroutine-friendly structures.
2. **ARM NEON SIMD Vectorization:** `-march=armv8.2-a+fp16+dotprod` enables 8-bit integer dot products (`SDOT`/`UDOT`) and half-precision floating-point acceleration on the Oryon CPU cores.
3. **Vulkan GPU Acceleration:** `-DGGML_VULKAN=ON` links the native C++ runtime against the Android system `libvulkan.so`, allowing compute shader dispatch directly on the Adreno 830 GPU.

---

## 3. Building & Testing from Command Line

### 3.1. Run Full Unit & Companion Smoke Test Suite
PocketCore AI includes 115 automated JVM tests that execute without requiring a connected physical device:
```bash
./gradlew testDebugUnitTest
```
Expected output:
```text
BUILD SUCCESSFUL in 15s
28 actionable tasks: 3 executed, 25 up-to-date
```
To run only the end-to-end companion smoke verification:
```bash
./gradlew testDebugUnitTest --tests com.ai.pocketcore.PocketCoreSmokeTest
```

### 3.2. Compile Android Instrumentation Tests
To compile the Android on-device instrumentation test suite:
```bash
./gradlew compileDebugAndroidTestKotlin
```

### 3.3. Compile Debug APK
To compile the debug APK with embedded native ARM64 libraries:
```bash
./gradlew assembleDebug
```
Upon completion, the compiled APK will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### 3.4. Compile Release APK
To compile an optimized, minified release APK:
```bash
./gradlew assembleRelease
```
Output:
```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 4. Deploying to Samsung Galaxy S25

### 4.1. Enable USB Debugging on Galaxy S25
1. Open **Settings** on your Galaxy S25.
2. Scroll to the bottom and tap **About phone**.
3. Tap **Software information**.
4. Locate **Build number** and tap it **7 consecutive times** until the prompt displays: *"Developer mode has been enabled"*.
5. Return to the main **Settings** menu and select **Developer options** at the bottom.
6. Toggle **USB debugging** to **ON**.
7. *(Optional)* If using Wireless ADB, toggle **Wireless debugging** to **ON**.

### 4.2. Connect Device via ADB
Connect your Galaxy S25 to your development machine using a high-speed USB-C 3.2 Gen 2 cable.

1. On your workstation terminal, run:
   ```bash
   adb devices
   ```
2. Your phone will display an authorization dialog: *"Allow USB debugging?"*. Check *"Always allow from this computer"* and tap **Allow**.
3. Verify the device is authorized:
   ```bash
   adb devices
   # Output:
   # List of devices attached
   # RFCX10XXXXX    device
   ```

### 4.3. Install & Launch PocketCore AI
Install the debug APK directly to the Galaxy S25:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the application:
```bash
adb shell am start -n com.ai.pocketcore/.MainActivity
```

---

## 5. Running On-Device Instrumentation Tests

With your Galaxy S25 connected via ADB, run the instrumentation smoke test on physical hardware:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ai.pocketcore.PocketCoreSmokeTest
```
This runs the full on-device pipeline against the real Android OS and Room SQLite:
- Creates an in-memory Room SQLite database.
- Registers and validates a 3B GGUF model entity.
- Executes `LoadModelUseCase` verifying physical RAM headroom against the device's 12 GB LPDDR5X memory.
- Streams generated tokens.
- Calculates and validates real-time telemetry (TTFT & tokens/sec).
- Persists user and assistant turns to Room and verifies database round-trip retrieval.

---

## 6. Model Acquisition & First Run

Once the app is running on your Galaxy S25, there are two methods to load GGUF weights:

### Method A: In-App Download from Hugging Face Hub
1. In PocketCore AI, navigate to the **Hub** tab.
2. Select any curated model card (e.g., `Llama-3.2-3B-Instruct` or `Qwen-2.5-3B-Instruct`).
3. Tap **Download**. The pull-up bottom sheet displays available quantizations (`Q4_K_M`, `Q5_K_M`, `Q8_0`).
4. Select `Q4_K_M` (recommended balance between size, speed, and perplexity).
5. The download starts via the background foreground service with live progress in the Android notification drawer.
6. Downloads are chunked and resumable; if the connection drops or is paused, it resumes at the exact byte index.
7. *(For gated models like Meta Llama-3)*: Go to Settings -> Hugging Face Access Token -> Enter your `hf_...` User Access Token.

### Method B: Sideloading GGUF Models Directly via USB / ADB
You can push existing GGUF models directly from your computer to the phone's storage:

1. Download your preferred GGUF model on your computer (e.g. `Llama-3.2-3B-Instruct-Q4_K_M.gguf`).
2. Push the file to the Galaxy S25's `Download` folder via ADB:
   ```bash
   adb push ~/Downloads/Llama-3.2-3B-Instruct-Q4_K_M.gguf /sdcard/Download/
   ```
3. Open PocketCore AI and tap the **Vault** tab.
4. Tap **Import GGUF from Device**.
5. Use the Android Storage Access Framework (SAF) file picker to select the GGUF file from your `Download` folder.
6. The app validates the GGUF header magic bytes (`0x46554747`), copies the weights into app-scoped storage, and registers the model in Room.

---

## 7. Validating Vulkan Acceleration

To verify that the Qualcomm Adreno 830 GPU is actively executing Vulkan compute shaders:

1. Monitor logcat during generation:
   ```bash
   adb logcat -s pocketcore:V LlamaNativeBridge:V
   ```
2. Look for native initialization confirmation:
   ```text
   V/pocketcore: [NativeEngine] Initializing model: /data/user/0/com.ai.pocketcore/files/models/Llama-3.2-3B-Instruct-Q4_K_M.gguf
   V/pocketcore: [NativeEngine] Vulkan compute device: Adreno (TM) 830
   V/pocketcore: [NativeEngine] Offloading 33 layers to Vulkan compute buffers
   V/pocketcore: [NativeEngine] Generation loop started with threads=4, context=2048
   ```
3. In the **Studio** tab, examine the top-right **Telemetry Capsule**:
   - Indicator should display `GPU: Vulkan`.
   - Speed on `Llama-3.2-3B-Instruct (Q4_K_M)` should register between **52 – 61 tok/s**.
   - Time to First Token (TTFT) should read between **80 – 120 ms**.

---

## 8. Troubleshooting & Diagnostics

### 8.1. CMake / NDK Host Compiler Failure
- **Symptom:** `error when building with cmake: The C compiler .../bin/clang is not able to compile a simple test program`.
- **Cause:** Attempting to invoke x86_64 prebuilt NDK binaries on a non-x86 host (e.g., inside an ARM64 Linux container).
- **Solution:** Compile the APK on an `x86_64` workstation (Linux, Windows) or an Apple Silicon Mac (`darwin-aarch64` NDK is natively supported).

### 8.2. Out-of-Memory (OOM) Rejection on Large Models
- **Symptom:** The Vault screen displays a warning pill: *"Insufficient Memory: Model requires 11.2 GB but only 9.8 GB available"*, and loading fails with `InsufficientMemoryException`.
- **Cause:** PocketCore AI enforces an 85% headroom safety guard (`modelSizeBytes <= availableRamBytes * 0.85`) to protect system stability.
- **Solution:** Select a smaller quantization (e.g. `Q4_K_M` instead of `Q8_0` or `FP16`) or use a 3B/7B model rather than a 14B/70B model.

### 8.3. Gated Model Download Rejection (HTTP 401 / 403)
- **Symptom:** Hugging Face download fails immediately with *"HTTP 401 Unauthorized"*.
- **Solution:** Some model repositories (e.g., Meta Llama-3, Gemma-2) require accepting the license agreement on huggingface.co. Ensure you have accepted the terms and entered your Bearer User Access Token in PocketCore AI Settings.

### 8.4. GGUF Magic Header Error
- **Symptom:** Sideloaded or downloaded file fails validation with `CorruptedModelException`.
- **Cause:** File was truncated during transfer or is not a valid GGUF file.
- **Solution:** PocketCore AI checks the first 4 bytes for `0x46554747` (`GGUF`). Ensure the file is not an HTML redirect page and that the download completed fully.
