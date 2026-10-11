package com.sudhir.safecopy.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudhir.safecopy.AppContainer
import com.sudhir.safecopy.core.image.ImageLoader
import com.sudhir.safecopy.core.log.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val image: Bitmap? = null,
    val rotating: Boolean = false,
    /** True once the user changed anything; Back then asks before discarding (spec 5.2). */
    val hasEdits: Boolean = false,
)

class EditorViewModel(private val container: AppContainer) : ViewModel() {
    private val session = container.editSession

    /** Captured once: if there is no image now, the process was killed and the session is gone. */
    val sessionExpired: Boolean = session.image.value == null

    private val local = MutableStateFlow(EditorUiState())

    val state: StateFlow<EditorUiState> = combine(session.image, local) { image, l -> l.copy(image = image) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditorUiState(image = session.image.value))

    private var quarterTurns = 0

    fun rotate() {
        val src = session.image.value ?: return
        if (local.value.rotating) return
        local.update { it.copy(rotating = true) }
        viewModelScope.launch {
            try {
                val rotated = withContext(Dispatchers.Default) { ImageLoader.rotateClockwise(src) }
                session.replace(rotated)
                quarterTurns = (quarterTurns + 1) % 4
                local.update { it.copy(hasEdits = quarterTurns != 0) }
            } catch (e: OutOfMemoryError) {
                SafeLog.w("rotate out of memory", e)
            } finally {
                local.update { it.copy(rotating = false) }
            }
        }
    }

    /** Drops the in-memory original and any temp files. Runs in app scope so it finishes. */
    fun discard() {
        session.clear()
        container.appScope.launch { container.tempFiles.clearOriginals() }
    }
}
