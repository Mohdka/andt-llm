package com.andet.llm

import android.content.Context
import java.io.File

class ModelManager(private val context: Context) {
    fun getModelsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listModels(): List<File> {
        val dir = getModelsDir()
        return dir.listFiles { file ->
            file.isFile && (file.extension.equals("gguf", true))
        }?.sortedBy { it.name } ?: emptyList()
    }

    fun getDefaultModel(): File? = listModels().firstOrNull()
}
