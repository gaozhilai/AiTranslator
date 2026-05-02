package com.gzl.aitranslator

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import kotlinx.coroutines.sync.Mutex

enum class SendResult { Success, Busy, NotInitialized }

object TranslationManager {
    private var engine: Engine? = null
    private val mutex = Mutex()

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var isBusy = false
        private set

    suspend fun initialize(modelPath: String) {
        if (isInitialized) return
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val engineConfig = EngineConfig(modelPath = modelPath)
        engine = Engine(engineConfig)
        engine!!.initialize()
        isInitialized = true
    }

    suspend fun sendMessageStream(message: String, onToken: suspend (String) -> Unit): SendResult {
        if (!isInitialized) return SendResult.NotInitialized
        if (!mutex.tryLock()) return SendResult.Busy
        try {
            isBusy = true
            val conv = engine!!.createConversation()
            try {
                conv.sendMessageAsync(message).collect { msg ->
                    val text = msg.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    onToken(text)
                }
            } finally {
                conv.close()
            }
            return SendResult.Success
        } finally {
            isBusy = false
            mutex.unlock()
        }
    }

    fun close() {
        engine?.close()
        engine = null
        isInitialized = false
    }
}
