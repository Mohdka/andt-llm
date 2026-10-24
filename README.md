# AndtLLM (Local LLM on Android)

A minimal harness to run GGUF models locally on Android via `llama.cpp`.

## Prerequisites
- Android Studio (Koala/Iguana or newer)
- NDK, CMake (install via SDK Manager)
- Android device with >= 6GB RAM recommended for 3B; 4GB for 1B Q2; 2GB for 360M Q2
- A GGUF model file

## Quick start
1. Clone/add `llama.cpp` as a submodule:
   ```bash
   git submodule add https://github.com/ggml-org/llama.cpp.git native/llama.cpp
   git submodule update --init --recursive
   ```
2. Download a small GGUF model (e.g. from HuggingFace) and put it on device:
   - Recommended starter: `SmolLM2-360M-Instruct-GGUF` Q2_K_M (~160MB)
   - Put in `Android/data/com.andt.llm/files/models/` or use the in-app downloader.
3. Open `andt-llm/` in Android Studio, sync Gradle, build and run.
4. Select the model in the app and start chatting.

## Notes
- First generation is slow (context warmup). Subsequent tokens faster.
- Use smaller quantizations (IQ2/ GGUF Q2) for lower RAM/CPU.
- Long generations can heat the device; consider a foreground service + wake lock.
- This scaffold uses CPU by default; you can enable Vulkan/NNAPI in llama.cpp build flags if supported.

## Files to implement next (scaffolded)
- JNI bridge (`app/src/main/cpp/llama_jni.cpp`)
- CMake (`app/src/main/cpp/CMakeLists.txt`)
- Kotlin bindings + UI + model manager
- Foreground service for background generation
