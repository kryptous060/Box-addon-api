package com.google.ai.edge.gallery.addon

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import android.util.Log

// Bridge class for Python to call back into Kotlin
class KotlinBridge(private val service: AddonApiService) {
    fun runChat(message: String): String {
        // Run synchronously but within the serialized NPU mutex
        return runBlocking {
            service.runNpuTask {
                Log.i("AddonApiService", "Running LLM inference for: $message")
                // TODO: Actual native JNI call here
                "Simulated response to: $message"
            }
        }
    }
}

class AddonApiService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val npuMutex = Mutex()
    private val TAG = "AddonApiService"

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Python and FastAPI")

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        serviceScope.launch {
            val py = Python.getInstance()
            val module = py.getModule("server")
            val bridge = KotlinBridge(this@AddonApiService)
            
            Log.i(TAG, "Starting FastAPI server")
            module.callAttr("start_server", bridge)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Stopping service")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Serialized NPU request handling
    suspend fun <T> runNpuTask(task: suspend () -> T): T {
        npuMutex.lock()
        try {
            Log.i(TAG, "NPU access acquired, running task...")
            return task()
        } finally {
            Log.i(TAG, "NPU access released.")
            npuMutex.unlock()
        }
    }
}

// Helper to bridge suspend functions to synchronous Python calls
fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}
