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
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ModelManagerService"

@Singleton
class ModelManagerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customTasks: Set<@JvmSuppressWildcards CustomTask>
) {

    // Store active models by a unique instanceId
    private val activeModels = ConcurrentHashMap<String, Model>()

    fun getTaskById(id: String): Task? {
        return customTasks.map { it.task }.find { it.id == id }
    }

    fun getModelByName(name: String): Model? {
        for (customTask in customTasks) {
            for (model in customTask.task.models) {
                if (model.name == name) {
                    return model
                }
            }
        }
        return null
    }

    fun getActiveModel(instanceId: String): Model? = activeModels[instanceId]

    fun getAllModels(): List<Model> {
        return customTasks.flatMap { it.task.models }
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
        onDone: () -> Unit = {},
    ) {
        Log.d(TAG, "DEBUG: initializeModel called for instance '$instanceId'")
        coroutineScope.launch(Dispatchers.IO) {
            Log.d(TAG, "DEBUG: initializeModel coroutine started for '${model.name}'")
            model.initializing = true
            
            val onDoneFn: (error: String) -> Unit = { error ->
                Log.d(TAG, "DEBUG: onDoneFn called for '${model.name}', error: '$error'")
                model.initializing = false
                if (model.instance != null) {
                    activeModels[instanceId] = model
                    Log.d(TAG, "Model '${model.name}' initialized successfully for '$instanceId'")
                    onDone()
                } else if (error.isNotEmpty()) {
                    Log.e(TAG, "Model '${model.name}' failed to initialize: $error")
                }
            }

            Log.d(TAG, "DEBUG: Calling getCustomTaskByTaskId for '${task.id}'")
            val customTask = getCustomTaskByTaskId(id = task.id)
            Log.d(TAG, "DEBUG: CustomTask found: ${customTask != null}")
            
            customTask
                ?.initializeModelFn(
                    context = context,
                    coroutineScope = coroutineScope,
                    model = model,
                    onDone = onDoneFn,
                )
            Log.d(TAG, "DEBUG: initializeModelFn called for '${model.name}'")
        }
    }

    fun getCustomTaskByTaskId(id: String): CustomTask? {
        return customTasks.toList().find { it.task.id == id }
    }
}
