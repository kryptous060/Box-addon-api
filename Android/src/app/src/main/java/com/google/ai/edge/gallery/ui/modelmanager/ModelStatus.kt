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
)
