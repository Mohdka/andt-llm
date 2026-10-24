#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <android/log.h>

#include "llama.h"
#include "common.h"

#define TAG "AndtLLM-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static struct llama_model *g_model = nullptr;
static struct llama_context *g_ctx = nullptr;
static std::thread g_thread;
static std::atomic<bool> g_stop{false};
static JavaVM *g_vm = nullptr;
static jobject g_callback = nullptr;

static void stream_token(const std::string &token) {
    if (!g_vm || !g_callback) return;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return;
        }
    }
    jclass cls = env->GetObjectClass(g_callback);
    jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    if (mid) {
        jstring jtoken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(g_callback, mid, jtoken);
        env->DeleteLocalRef(jtoken);
    }
    env->DeleteLocalRef(cls);
    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

static void call_complete() {
    if (!g_vm || !g_callback) return;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return;
        }
    }
    jclass cls = env->GetObjectClass(g_callback);
    jmethodID mid = env->GetMethodID(cls, "onComplete", "()V");
    if (mid) env->CallVoidMethod(g_callback, mid);
    env->DeleteLocalRef(cls);
    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

static void call_error(const std::string &msg) {
    if (!g_vm || !g_callback) return;
    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            return;
        }
    }
    jclass cls = env->GetObjectClass(g_callback);
    jmethodID mid = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
    if (mid) {
        jstring jmsg = env->NewStringUTF(msg.c_str());
        env->CallVoidMethod(g_callback, mid, jmsg);
        env->DeleteLocalRef(jmsg);
    }
    env->DeleteLocalRef(cls);
    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    llama_backend_init();
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    llama_backend_free();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_andet_llm_LlmNative_loadModel(JNIEnv *env, jobject thiz, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU by default; can increase if supported

    g_model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_andet_llm_LlmNative_generate(JNIEnv *env, jobject thiz, jstring prompt, jint maxTokens, jobject callback) {
    if (!g_model || !g_ctx) {
        call_error("Model not loaded");
        return;
    }
    if (g_thread.joinable()) {
        g_stop = true;
        g_thread.join();
    }

    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
    }
    g_callback = env->NewGlobalRef(callback);
    g_stop = false;

    const char *p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str = p;
    env->ReleaseStringUTFChars(prompt, p);

    g_thread = std::thread([prompt_str, maxTokens]() {
        const llama_vocab *vocab = llama_model_get_vocab(g_model);
        std::vector<llama_token> tokens_list = ::llama_tokenize(vocab, prompt_str.c_str(), prompt_str.size(), true, true);

        if (llama_decode(g_ctx, llama_batch_get_one(tokens_list.data(), tokens_list.size()))) {
            call_error("Failed to decode prompt");
            return;
        }

        auto sparams = llama_sampler_chain_default_params();
        llama_sampler *smpl = llama_sampler_chain_init(sparams);
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

        for (int i = 0; i < maxTokens && !g_stop; ++i) {
            llama_token new_token_id = llama_sampler_sample(smpl, g_ctx, -1);
            if (llama_vocab_is_eog(vocab, new_token_id)) break;

            char buf[256];
            int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
            if (n < 0) break;
            std::string piece(buf, n);
            stream_token(piece);

            tokens_list.clear();
            tokens_list.push_back(new_token_id);
            if (llama_decode(g_ctx, llama_batch_get_one(tokens_list.data(), tokens_list.size()))) break;
        }

        llama_sampler_free(smpl);
        call_complete();
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_andet_llm_LlmNative_stop(JNIEnv *env, jobject thiz) {
    g_stop = true;
    if (g_thread.joinable()) {
        g_thread.join();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_andet_llm_LlmNative_unloadModel(JNIEnv *env, jobject thiz) {
    Java_com_andet_llm_LlmNative_stop(env, thiz);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
    }
}
