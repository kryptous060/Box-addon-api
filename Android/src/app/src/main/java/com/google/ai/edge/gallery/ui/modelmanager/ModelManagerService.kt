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

private const val TAG = "ModelManagerService"

@Singleton
class ModelManagerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customTasks: Set<@JvmSuppressWildcards CustomTask>
) {

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

    fun getCustomTaskByTaskId(id: String): CustomTask? {
        return customTasks.toList().find { it.task.id == id }
    }

    fun initializeModel(
        task: Task,
        model: Model,
        coroutineScope: CoroutineScope,
        onDone: () -> Unit = {},
    ) {
        coroutineScope.launch(Dispatchers.Default) {
            // Start initialization.
            Log.d(TAG, "Initializing model '${model.name}'...")
            model.initializing = true
            
            val onDoneFn: (error: String) -> Unit = { error ->
                model.initializing = false
                if (model.instance != null) {
                    Log.d(TAG, "Model '${model.name}' initialized successfully")
                    onDone()
                } else if (error.isNotEmpty()) {
                    Log.d(TAG, "Model '${model.name}' failed to initialize: $error")
                }
            }

            // Call the model initialization function.
            getCustomTaskByTaskId(id = task.id)
                ?.initializeModelFn(
                    context = context,
                    coroutineScope = coroutineScope,
                    model = model,
                    onDone = onDoneFn,
                )
        }
    }
}
