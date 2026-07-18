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

package com.google.ai.edge.gallery.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerService
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModelBase
import com.google.ai.edge.gallery.stablediffusion.StableDiffusion
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class ChatRequest(val message: String, val instanceId: String)

@Serializable
data class GenerateImageRequest(val prompt: String, val instanceId: String)

@Serializable
data class LoadModelRequest(val modelName: String, val taskId: String, val instanceId: String)

@AndroidEntryPoint
class BoxApiServer : Service() {

    @Inject lateinit var modelManagerService: ModelManagerService
    
    // Tracks which instance is currently the orchestrator
    private var orchestratorInstanceId: String = "comm"

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        server = embeddedServer(CIO, port = 8080) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("/v1beta/models/{model}:streamGenerateContent") {
                    val request = call.receive<ChatRequest>()
                    
                    // Route to primary model regardless of requested modelName
                    val modelIdToUse = "primary"
                    val model = modelManagerService.getActiveModel(modelIdToUse)
                    
                    if (model?.instance is LlmChatViewModelBase) {
                        val chatViewModel = model.instance as LlmChatViewModelBase
                        val response = chatViewModel.generateResponseAsync(model, request.message)
                        
                        // Return response formatted in Google's standard JSON structure
                        val googleResponse = mapOf(
                            "candidates" to listOf(
                                mapOf("content" to mapOf("parts" to listOf(mapOf("text" to response))))
                            )
                        )
                        call.respond(HttpStatusCode.OK, googleResponse)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "No active primary model found")
                    }
                }
                post("/set-orchestration-mode") {
                    val request = call.receive<Map<String, String>>()
                    orchestratorInstanceId = request["orchestratorInstanceId"] ?: "comm"
                    call.respond(HttpStatusCode.OK, "Orchestration mode set to $orchestratorInstanceId")
                }
                post("/chat") {
                    val request = call.receive<ChatRequest>()
                    val orchestratorId = orchestratorInstanceId
                    val specialistId = if (orchestratorId == "comm") "coder" else "comm"
                    
                    val modelIdToUse = if (request.instanceId == "primary") orchestratorId else request.instanceId
                    val model = modelManagerService.getActiveModel(modelIdToUse)
                    
                    if (model?.instance is LlmChatViewModelBase) {
                        val chatViewModel = model.instance as LlmChatViewModelBase
                        val response = chatViewModel.generateResponseAsync(model, request.message)
                        
                        // Orchestration Logic: Detect delegation signal
                        if (modelIdToUse == orchestratorId && response.contains("[DELEGATE_TO_SPECIALIST]")) {
                            val rephrasedPrompt = response.substringAfter("[DELEGATE_TO_SPECIALIST]").trim()
                            val specialistModel = modelManagerService.getActiveModel(specialistId)
                            
                            if (specialistModel?.instance is LlmChatViewModelBase) {
                                val specialistViewModel = specialistModel.instance as LlmChatViewModelBase
                                val specialistResponse = specialistViewModel.generateResponseAsync(specialistModel, rephrasedPrompt)
                                call.respond(HttpStatusCode.OK, "Specialist output: $specialistResponse")
                            } else {
                                call.respond(HttpStatusCode.BadRequest, "Specialist model ($specialistId) not loaded")
                            }
                        } else {
                            call.respond(HttpStatusCode.OK, response)
                        }
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Model instance not found or not an LLM")
                    }
                }
                post("/generate-image") {
                    val request = call.receive<GenerateImageRequest>()
                    val model = modelManagerService.getActiveModel(request.instanceId)
                    if (model?.instance is StableDiffusion) {
                        val sd = model.instance as StableDiffusion
                        val params = StableDiffusion.GenerationParams(
                            prompt = request.prompt,
                            steps = 20,
                            cfgScale = 7.5f,
                        )
                        // Use suspend function to await generation
                        val image = sd.generateImageAsync(params)
                        call.respond(HttpStatusCode.OK, "Image generation complete for ${request.instanceId}")
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Model instance not found or not an Image Gen model")
                    }
                }
                post("/load-llm") {
                    val request = call.receive<LoadModelRequest>()
                    val model = modelManagerService.getModelByName(request.modelName)
                    val task = modelManagerService.getTaskById(request.taskId)

                    if (model != null && task != null) {
                        // Execute NPU load in background
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                modelManagerService.initializeModel(request.instanceId, task, model, serviceScope)
                            } catch (e: Exception) {
                                println("NPU Load Failed: ${e.message}")
                            }
                        }
                        // Instantly close connection
                        call.respond(HttpStatusCode.Accepted, "{\"status\": \"initializing_in_background\"}")
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Model or Task not found"))
                    }
                }
                post("/load-image-model") {
                    val request = call.receive<LoadModelRequest>()
                    val model = modelManagerService.getModelByName(request.modelName)
                    val task = modelManagerService.getTaskById(request.taskId)
                    if (model != null && task != null) {
                        modelManagerService.initializeModel(request.instanceId, task, model, serviceScope)
                        call.respond(HttpStatusCode.OK, mapOf("status" to "loading", "model" to request.modelName))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Model or Task not found"))
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            server.start(wait = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server.stop(1000, 2000)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
