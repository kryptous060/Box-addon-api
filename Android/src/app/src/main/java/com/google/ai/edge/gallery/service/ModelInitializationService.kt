package com.google.ai.edge.gallery.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

@AndroidEntryPoint
class ModelInitializationService : Service() {

    @Inject lateinit var modelManagerService: ModelManagerService
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelName = intent?.getStringExtra("modelName")
        val taskId = intent?.getStringExtra("taskId")
        val instanceId = intent?.getStringExtra("instanceId")

        if (modelName != null && taskId != null && instanceId != null) {
            serviceScope.launch {
                val model = modelManagerService.getModelByName(modelName)
                val task = modelManagerService.getTaskById(taskId)
                
                if (model != null && task != null) {
                    Log.d("ModelInitService", "Starting background init for $modelName")
                    modelManagerService.initializeModel(instanceId, task, model, serviceScope)
                }
            }
        }
        
        // Stop the service once the init command is dispatched to background
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
