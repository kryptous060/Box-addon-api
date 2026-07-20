package com.google.ai.edge.gallery.service

import android.util.Log
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestModelManager @Inject constructor(
    private val modelManagerService: ModelManagerService
) {
    fun runDiagnostic() {
        Log.d("TestModelManager", "DEBUG: Starting diagnostic")
        try {
            val models = modelManagerService.getAllModels()
            Log.d("TestModelManager", "DEBUG: Models retrieved: ${models.size}")
            
            // Accessing private method via reflection or just logging if possible. 
            // Since I cannot easily change access, I will just call getModelByName for a test name or 
            // if I cannot access imported models, I will add a log indicating this.
            Log.d("TestModelManager", "DEBUG: Note: Imported models not explicitly listed in diagnostic.")
            
            val tasks = modelManagerService.getAllTasks()
            Log.d("TestModelManager", "DEBUG: Tasks retrieved: ${tasks.size}")
        } catch (e: Exception) {
            Log.e("TestModelManager", "DEBUG: Diagnostic failed", e)
        }
    }
}
