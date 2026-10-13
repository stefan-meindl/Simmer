package com.simmer.app.reader

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.emitAll

/** The real LiteRT-LM engine. Not unit-tested on the JVM (native); see docs/manual-testing.md. */
class LiteRtEngineFactory(private val context: Context) : LlmEngineFactory {
    override fun create(modelPath: String, backend: InferenceBackend): LlmEngine {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = when (backend) {
                InferenceBackend.Gpu -> Backend.GPU()
                InferenceBackend.Cpu -> Backend.CPU()
            },
            // A writable dir speeds up the second load.
            cacheDir = context.cacheDir.path,
        )
        val engine = Engine(config)
        try {
            engine.initialize()
        } catch (e: Throwable) {
            engine.close()
            throw e
        }
        return LiteRtEngine(engine)
    }
}

private class LiteRtEngine(private val engine: Engine) : LlmEngine {
    override fun generate(system: String, user: String, maxTokens: Int, temperature: Double): Flow<String> = flow {
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature),
            maxOutputToken = maxTokens,
        )
        val conversation = engine.createConversation(config)
        emitAll(
            conversation.sendMessageAsync(user)
                .map { it.toString() }
                .onCompletion { conversation.close() },
        )
    }.flowOn(Dispatchers.Default)

    override fun close() = engine.close()
}

/** Severe or worse (THERMAL_STATUS_SEVERE and up) on API 29+; older phones report no thermal status. */
class AndroidThermalMonitor(private val context: Context) : ThermalMonitor {
    override fun isSevere(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }
}

/** SEAM: replace with an ML Kit GenAI availability check when GeminiNanoReader is built. */
class NoNanoAvailability : NanoAvailabilityCheck {
    override suspend fun check(): NanoAvailability = NanoAvailability.Unavailable
}
