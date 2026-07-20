/*
 * Copyright 2025 Box Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.modelmanager

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.RuntimeType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import com.google.ai.edge.gallery.proto.ImportedModel
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.Config
import com.google.ai.edge.gallery.data.ConfigKey
import com.google.ai.edge.gallery.data.ValueType
import com.google.ai.edge.gallery.data.NumberSliderConfig

// Top-level status classes to avoid ViewModel dependency
enum class ModelInitializationStatusType {
    INITIALIZING, INITIALIZED, ERROR
}

data class ModelInitializationStatus(
    val status: ModelInitializationStatusType,
    val error: String = "",
)

private const val TAG = "ModelManagerService"
private const val IMPORTS_DIR = "imports"

@Singleton
class ModelManagerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customTasks: Set<@JvmSuppressWildcards CustomTask>,
    private val dataStoreRepository: DataStoreRepository
) {

    // Dedicated dispatcher for heavy model initialization to prevent Ktor server hang
    private val modelLoaderDispatcher = newFixedThreadPoolContext(1, "ModelLoaderPool")

    // Store active models by a unique instanceId
    private val activeModels = ConcurrentHashMap<String, Model>()
    
    // Track initialization status of models
    private val modelInitializationStatus = ConcurrentHashMap<String, ModelInitializationStatus>()

    // Required helper, missing previously
    private fun createModelFromImportedModelInfo(info: ImportedModel): Model {
        val accelerators: MutableList<Accelerator> =
            info.llmConfig.compatibleAcceleratorsList
                .mapNotNull { acceleratorLabel ->
                    when (acceleratorLabel.trim()) {
                        Accelerator.GPU.label -> Accelerator.GPU
                        Accelerator.CPU.label -> Accelerator.CPU
                        Accelerator.NPU.label -> Accelerator.NPU
                        else -> null 
                    }
                }
                .toMutableList()
        val model =
            Model(
                name = info.fileName,
                url = "",
                sizeInBytes = info.fileSize,
                downloadFileName = "$IMPORTS_DIR/${info.fileName}",
                imported = true,
                runtimeType = RuntimeType.LITERT_LM,
                accelerators = accelerators,
                llmMaxToken = info.llmConfig.defaultMaxTokens,
                maxContextSize = info.llmConfig.maxContextSize,
            )
        model.preProcess()
        return model
    }

    fun getInitializationStatus(modelName: String): ModelInitializationStatus? {
        return modelInitializationStatus[modelName]
    }

    fun updateInitializationStatus(model: Model, status: ModelInitializationStatus) {
        modelInitializationStatus[model.name] = status
    }

    fun getTaskById(id: String): Task? {
        return customTasks.map { it.task }.find { it.id == id }
    }

    // Refresh and return imported models
    private fun getImportedModels(): List<Model> {
        return dataStoreRepository.readImportedModels().map { info ->
            createModelFromImportedModelInfo(info)
        }
    }

    fun getModelByName(name: String): Model? {
        // Check custom tasks
        for (customTask in customTasks) {
            for (model in customTask.task.models) {
                if (model.name == name) {
                    return model
                }
            }
        }
        // Check imported
        return getImportedModels().find { it.name == name }
    }

    fun getActiveModel(instanceId: String): Model? = activeModels[instanceId]

    fun getAllModels(): List<Model> {
        // Ensure imported models are included and refreshed
        val allModels = customTasks.flatMap { it.task.models }.toMutableList()
        allModels.addAll(getImportedModels())
        return allModels.distinctBy { it.name }
    }

    fun getAllTasks(): List<CustomTask> {
        return customTasks.toList()
    }

    fun setOrchestrationMode(mode: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                java.net.URL("http://localhost:8080/set-orchestration-mode")
                    .openConnection().apply {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        outputStream.use { it.write("{\"orchestratorInstanceId\":\"$mode\"}".toByteArray()) }
                        inputStream.bufferedReader().use { it.readText() }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set orchestration mode", e)
            }
        }
    }

    fun initializeModel(
        instanceId: String,
        task: Task,
        model: Model,
        coroutineScope: CoroutineScope,
        onDone: (error: String) -> Unit = {},
    ) {
        Log.d(TAG, "DEBUG: initializeModel called for instance '$instanceId', model '${model.name}', task '${task.id}'")
        
        // Check if already initializing or initialized to prevent redundant work
        val currentStatus = getInitializationStatus(model.name)?.status
        if (currentStatus == ModelInitializationStatusType.INITIALIZING) {
            Log.d(TAG, "Model '${model.name}' is already initializing. Skipping.")
            return
        }

        // Offload to dedicated dispatcher
        coroutineScope.launch(modelLoaderDispatcher) {
            Log.d(TAG, "DEBUG: initializeModel coroutine started for '${model.name}'")
            model.initializing = true
            updateInitializationStatus(model, ModelInitializationStatus(status = ModelInitializationStatusType.INITIALIZING))
            
            val onDoneFn: (error: String) -> Unit = { error ->
                Log.d(TAG, "DEBUG: onDoneFn called for '${model.name}', error: '$error'")
                model.initializing = false
                if (model.instance != null) {
                    activeModels[instanceId] = model
                    updateInitializationStatus(model, ModelInitializationStatus(status = ModelInitializationStatusType.INITIALIZED))
                    Log.d(TAG, "Model '${model.name}' initialized successfully for '$instanceId'")
                    onDone("")
                } else {
                    val errorMessage = error.ifEmpty { "Unknown initialization error" }
                    updateInitializationStatus(model, ModelInitializationStatus(status = ModelInitializationStatusType.ERROR, error = errorMessage))
                    Log.e(TAG, "Model '${model.name}' failed to initialize: $errorMessage")
                    onDone(errorMessage)
                }
            }

            val customTask = getCustomTaskByTaskId(id = task.id)
            
            if (customTask != null) {
                customTask.initializeModelFn(
                    context = context,
                    coroutineScope = coroutineScope,
                    model = model,
                    onDone = { error -> onDoneFn(error) },
                )
            } else {
                onDoneFn("CustomTask not found for taskId '${task.id}'")
            }
        }
    }

    fun getCustomTaskByTaskId(id: String): CustomTask? {
        return customTasks.toList().find { it.task.id == id }
    }
}
