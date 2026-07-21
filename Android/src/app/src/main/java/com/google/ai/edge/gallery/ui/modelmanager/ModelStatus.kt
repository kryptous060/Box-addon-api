package com.google.ai.edge.gallery.ui.modelmanager

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
            model.getStringConfigValue(key = com.google.ai.edge.gallery.data.ConfigKeys.ACCELERATOR, defaultValue = com.google.ai.edge.gallery.data.Accelerator.GPU.label)
        return !initializedBackends.contains(backend)
    }
}
