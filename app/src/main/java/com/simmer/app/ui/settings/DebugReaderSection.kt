package com.simmer.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.simmer.app.reader.GemmaReader
import com.simmer.app.ui.components.PrimaryButton
import com.simmer.app.ui.components.SecondaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// THROWAWAY manual-test UI for issue #16. Never merge.

data class DebugReaderUi(val output: String = "", val error: String? = null, val running: Boolean = false, val info: String = "")

@HiltViewModel
class DebugReaderViewModel @Inject constructor(private val reader: GemmaReader) : ViewModel() {
    val ui = MutableStateFlow(DebugReaderUi(info = "Backend: ${reader.lastBackend}"))
    private var job: Job? = null

    fun generate() {
        if (ui.value.running) return
        val started = System.currentTimeMillis()
        ui.value = DebugReaderUi(running = true, info = "Loading/generating...")
        job = viewModelScope.launch {
            var tokens = 0
            try {
                reader.generate("You are a helpful assistant.", "Name three vegetables, one per line.", 128)
                    .collect {
                        tokens++
                        ui.value = ui.value.copy(output = ui.value.output + it)
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                ui.value = ui.value.copy(error = "Cancelled")
                throw e
            } catch (e: Throwable) {
                ui.value = ui.value.copy(error = "${e::class.java.simpleName}: ${e.message}")
            } finally {
                val secs = (System.currentTimeMillis() - started) / 1000.0
                ui.value = ui.value.copy(running = false, info = "Backend: ${reader.lastBackend}; $tokens chunks in ${"%.1f".format(secs)}s")
            }
        }
    }

    fun cancel() { job?.cancel() }

    fun unload() {
        reader.unload()
        ui.value = ui.value.copy(info = "Unload requested (closes now, or after the running generation ends). Last backend: ${reader.lastBackend}")
    }

    fun refreshBackend() { ui.value = ui.value.copy(info = "Backend: ${reader.lastBackend}") }
}

@Composable
fun DebugReaderSection(viewModel: DebugReaderViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("DEBUG: Reader test (throwaway)")
        PrimaryButton(text = "Test generate", onClick = viewModel::generate, enabled = !state.running)
        SecondaryButton(text = "Cancel", onClick = viewModel::cancel, enabled = state.running)
        SecondaryButton(text = "Unload engine", onClick = viewModel::unload)
        SecondaryButton(text = "Show backend", onClick = viewModel::refreshBackend)
        Text(state.info)
        Text("Output: ${state.output}")
        state.error?.let { Text("ERROR: $it") }
    }
}
