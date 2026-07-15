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
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class ChatRequest(val message: String)

@Serializable
data class GenerateImageRequest(val prompt: String)

@Serializable
data class LoadModelRequest(val modelName: String)

@AndroidEntryPoint
class BoxApiServer : Service() {

    @Inject lateinit var modelManagerViewModel: ModelManagerViewModel

    private lateinit var server: EmbeddedServer<CIOEngine, CIOApplicationEngine.Configuration>
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
                    // Implementation: delegate to modelManagerViewModel or chat engine
                    call.respondText("Received chat message: ${request.message}")
                }
                post("/generate-image") {
                    val request = call.receive<GenerateImageRequest>()
                    // Implementation: trigger image generation
                    call.respondText("Image generated for prompt: ${request.prompt}")
                }
                post("/load-llm") {
                    val request = call.receive<LoadModelRequest>()
                    // Implementation: load LLM
                    call.respondText("Loading LLM model: ${request.modelName}")
                }
                post("/load-image-model") {
                    val request = call.receive<LoadModelRequest>()
                    // Implementation: load image generation model
                    call.respondText("Loading image model: ${request.modelName}")
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
