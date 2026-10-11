package com.sudhir.safecopy.ui.export

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudhir.safecopy.AppContainer
import com.sudhir.safecopy.core.log.SafeLog
import com.sudhir.safecopy.render.Composer
import com.sudhir.safecopy.render.ExportedFile
import com.sudhir.safecopy.render.WatermarkRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreviewUiState(
    val rendering: Boolean = true,
    /** The composed output: exactly the pixels that were encoded and will be shared. */
    val output: Bitmap? = null,
    val export: ExportedFile? = null,
    val failed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** One-shot: the copy was shared or saved; show the Done screen. */
    val done: Boolean = false,
)

class PreviewViewModel(private val container: AppContainer) : ViewModel() {
    private val session = container.editSession

    val sessionExpired: Boolean = session.image.value == null

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    init {
        if (!sessionExpired) render()
    }

    /** Spec 9: compose onto a new bitmap, then encode JPEG into cache/export. */
    private fun render() {
        val source = session.image.value ?: return
        val masks = session.masks
        val watermark = session.watermark?.let { WatermarkRender(it.text(), it.style) }
        viewModelScope.launch {
            try {
                val output = withContext(Dispatchers.Default) { Composer.compose(source, masks, watermark) }
                val export = container.exporter.writeJpeg(output)
                _state.update { it.copy(rendering = false, output = output, export = export) }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                SafeLog.e("export failed", e)
                _state.update { it.copy(rendering = false, failed = true) }
            }
        }
    }

    /** True once the export was handed to another app; its file must outlive this screen. */
    private var exportHandedOff = false

    /** The share chooser was opened. The export file stays in cache so the target app can read it. */
    fun onShared() {
        exportHandedOff = true
        finish()
    }

    fun onSaveDestination(uri: Uri?) {
        val export = _state.value.export ?: return
        if (uri == null) return // user backed out of the system "save as" screen
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ok = container.exporter.copyTo(export, uri)
            if (ok) {
                container.exporter.delete(export) // saved copy is the user's now; no one else needs ours
                finish()
            } else {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun saveFailedShown() = _state.update { it.copy(saveFailed = false) }

    fun doneHandled() = _state.update { it.copy(done = false) }

    override fun onCleared() {
        // Back to the editor without sharing: this render is stale, drop its file now
        // instead of waiting for the one-hour sweep.
        val export = _state.value.export
        if (!exportHandedOff && export != null) container.appScope.launch { container.exporter.delete(export) }
    }

    /** Spec 9: after share/save the original is dropped from memory and temp storage. */
    private fun finish() {
        // Remember who this copy went to, for the recipient suggestions (spec 10: last 10).
        session.watermark?.recipient?.let { r -> container.appScope.launch { container.settings.addRecentRecipient(r) } }
        session.clear()
        container.appScope.launch { container.tempFiles.clearOriginals() }
        _state.update { it.copy(saving = false, done = true) }
    }
}
