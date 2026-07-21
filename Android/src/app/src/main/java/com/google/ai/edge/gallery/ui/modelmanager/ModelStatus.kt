package com.google.ai.edge.gallery.ui.modelmanager

import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Accelerator

enum class ModelInitializationStatusType {
    NOT_INITIALIZED,
    INITIALIZING,
    INITIALIZED,
    ERROR,
}

data class ModelInitializationStatus(
    val status: ModelInitializationStatusType,
    var error: String = "",
    var initializedBackends: Set<String> = setOf(),
) {
    fun isFirstInitialization(model: Model): Boolean {
        // Need access to ConfigKeys and Accelerator
        val backend =
            model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
        return !initializedBackends.contains(backend)
    }
}
