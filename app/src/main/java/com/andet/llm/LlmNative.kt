package com.andet.llm

object LlmNative {
    init {
        System.loadLibrary("andtllm")
    }

    external fun loadModel(modelPath: String): Boolean
    external fun generate(prompt: String, maxTokens: Int, callback: GenerationCallback)
    external fun stop()
    external fun unloadModel()

    interface GenerationCallback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(message: String)
    }
}
