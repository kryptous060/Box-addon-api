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
data class ChatRequest(val message: String, val modelName: String)

@Serializable
data class GenerateImageRequest(val prompt: String, val modelName: String)

@Serializable
data class LoadModelRequest(val modelName: String, val taskId: String)

@AndroidEntryPoint
class BoxApiServer : Service() {

    @Inject lateinit var modelManagerService: ModelManagerService

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        server = embeddedServer(CIO, port = 8080) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("/chat") {
                    val request = call.receive<ChatRequest>()
                    val model = modelManagerService.getModelByName(request.modelName)
                    if (model?.instance is LlmChatViewModelBase) {
                        val chatViewModel = model.instance as LlmChatViewModelBase
                        // Simplified chat execution
                        chatViewModel.generateResponse(model, request.message, onError = {})
                        call.respond(HttpStatusCode.OK, "Chat message sent to ${request.modelName}")
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Model not loaded or not an LLM")
                    }
                }
                post("/generate-image") {
                    val request = call.receive<GenerateImageRequest>()
                    val model = modelManagerService.getModelByName(request.modelName)
                    if (model?.instance is StableDiffusion) {
                        val sd = model.instance as StableDiffusion
                        // Simplified image generation execution
                        val params = StableDiffusion.GenerationParams(
                            prompt = request.prompt,
                            steps = 20,
                            cfgScale = 7.5f,
                        )
                        serviceScope.launch { sd.generateImage(params).collect {} }
                        call.respond(HttpStatusCode.OK, "Image generation triggered for ${request.modelName}")
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Model not loaded or not an Image Gen model")
                    }
                }
                post("/load-llm") {
                    val request = call.receive<LoadModelRequest>()
                    val model = modelManagerService.getModelByName(request.modelName)
                    val task = modelManagerService.getTaskById(request.taskId)
                    if (model != null && task != null) {
                        modelManagerService.initializeModel(task, model, serviceScope)
                        call.respond(HttpStatusCode.OK, "Loading LLM model: ${request.modelName}")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Model or Task not found")
                    }
                }
                post("/load-image-model") {
                    val request = call.receive<LoadModelRequest>()
                    val model = modelManagerService.getModelByName(request.modelName)
                    val task = modelManagerService.getTaskById(request.taskId)
                    if (model != null && task != null) {
                        modelManagerService.initializeModel(task, model, serviceScope)
                        call.respond(HttpStatusCode.OK, "Loading image model: ${request.modelName}")
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Model or Task not found")
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
