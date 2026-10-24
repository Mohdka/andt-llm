package com.andet.llm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelManager = ModelManager(this)
        setContent {
            MaterialTheme {
                ChatScreen(modelManager)
            }
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path
        val cut = name?.lastIndexOf('/')
        if (cut != -1 && cut != null) {
            name = name?.substring(cut + 1)
        }
    }
    return name
}

data class Message(val role: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modelManager: ModelManager) {
    val context = LocalContext.current
    val models = remember { mutableStateOf(modelManager.listModels()) }
    var selectedModel by remember { mutableStateOf(models.value.firstOrNull()) }
    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Place GGUF files in ${modelManager.getModelsDir().absolutePath}") }
    var showHfDialog by remember { mutableStateOf(false) }
    var hfUrl by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val fileName = getFileName(context, it) ?: "model.gguf"
                val destFile = File(modelManager.getModelsDir(), fileName)
                inputStream?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                models.value = modelManager.listModels()
                status = "✓ Imported ${destFile.name}"
            } catch (e: Exception) {
                status = "Error: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AndtLLM", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Model selector card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (models.value.isEmpty()) {
                        Text(
                            "No GGUF models found",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Import a model below",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = selectedModel?.name ?: "Select model",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                models.value.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model.name) },
                                        onClick = {
                                            selectedModel = model
                                            expanded = false
                                            status = if (LlmNative.loadModel(model.absolutePath)) {
                                                "✓ Model loaded"
                                            } else {
                                                "⚠ Failed to load (stub/native not built). Add llama.cpp submodule."
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import File")
                        }
                        OutlinedButton(
                            onClick = { showHfDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("From HF")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showHfDialog) {
                AlertDialog(
                    onDismissRequest = { showHfDialog = false },
                    title = { Text("Download from HuggingFace") },
                    text = {
                        Column {
                            Text(
                                "Paste the direct GGUF download URL",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = hfUrl,
                                onValueChange = { hfUrl = it },
                                label = { Text("URL") },
                                placeholder = { Text("https://huggingface.co/.../resolve/main/model.gguf") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (hfUrl.isNotBlank()) {
                                    try {
                                        val request = DownloadManager.Request(Uri.parse(hfUrl))
                                        val fileName = hfUrl.substringAfterLast("/").substringBefore("?")
                                        request.setDestinationInExternalFilesDir(context, "models", fileName)
                                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        request.setTitle("Downloading $fileName")
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        dm.enqueue(request)
                                        status = "Downloading $fileName..."
                                        showHfDialog = false
                                        hfUrl = ""
                                    } catch (e: Exception) {
                                        status = "Error: ${e.message}"
                                    }
                                }
                            }
                        ) {
                            Text("Download")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showHfDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Messages
            if (messages.isEmpty() && !generating) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Start a conversation",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ask anything, get answers from your local model",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                modifier = Modifier.widthIn(max = 280.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        msg.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    if (generating) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Thinking...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input area
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message...") },
                        enabled = !generating && selectedModel != null,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        maxLines = 4
                    )
                    FilledIconButton(
                    onClick = {
                        if (prompt.isBlank() || selectedModel == null) return@FilledIconButton
                        val userPrompt = prompt
                        val assistantIndex = messages.size + 1
                        messages = messages + Message("user", userPrompt) + Message("assistant", "")
                        prompt = ""
                        generating = true
                        status = "Generating..."
                        LlmNative.generate(userPrompt, 512, object : LlmNative.GenerationCallback {
                            override fun onToken(token: String) {
                                messages = messages.toMutableList().apply {
                                    if (assistantIndex >= 0 && assistantIndex < this.size) {
                                        val current = this[assistantIndex]
                                        this[assistantIndex] = current.copy(content = current.content + token)
                                    }
                                }
                            }
                            override fun onComplete() {
                                generating = false
                                status = "Done"
                            }
                            override fun onError(message: String) {
                                generating = false
                                status = "Error: $message"
                                messages = messages.toMutableList().apply {
                                    if (assistantIndex >= 0 && assistantIndex < this.size) {
                                        val current = this[assistantIndex]
                                        if (current.content.isEmpty()) {
                                            this[assistantIndex] = current.copy(content = "Error: $message")
                                        }
                                    }
                                }
                            }
                        })
                    },
                    enabled = !generating && selectedModel != null && prompt.isNotBlank()
                ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path
        val cut = name?.lastIndexOf('/')
        if (cut != -1 && cut != null) {
            name = name?.substring(cut + 1)
        }
    }
    return name
}
