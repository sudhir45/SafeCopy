package com.sudhir.safecopy.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudhir.safecopy.AppContainer
import com.sudhir.safecopy.core.image.ImageLoader
import com.sudhir.safecopy.core.log.SafeLog
import com.sudhir.safecopy.domain.mask.MaskGeometry
import com.sudhir.safecopy.domain.mask.UndoStack
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Point
import com.sudhir.safecopy.domain.model.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class EditorUiState(
    val image: Bitmap? = null,
    val masks: List<MaskRect> = emptyList(),
    val selectedId: String? = null,
    /** "Add mask" is on: a drag on empty space draws a new box. */
    val drawMode: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val rotating: Boolean = false,
    /** True once the user changed anything; Back then asks before discarding (spec 5.2). */
    val hasEdits: Boolean = false,
)

class EditorViewModel(private val container: AppContainer) : ViewModel() {
    private val session = container.editSession

    /** Captured once: if there is no image now, the process was killed and the session is gone. */
    val sessionExpired: Boolean = session.image.value == null

    private val history = UndoStack<List<MaskRect>>(session.masks)
    private var quarterTurns = 0
    private val local = MutableStateFlow(EditorUiState().withHistory())

    val state: StateFlow<EditorUiState> = combine(session.image, local) { image, l -> l.copy(image = image) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, local.value.copy(image = session.image.value))

    private fun EditorUiState.withHistory() = copy(
        masks = history.current,
        canUndo = history.canUndo,
        canRedo = history.canRedo,
        selectedId = selectedId?.takeIf { id -> history.current.any { it.id == id } },
        hasEdits = history.current.isNotEmpty() || quarterTurns != 0,
    )

    private fun commit(masks: List<MaskRect>) {
        history.push(masks)
        local.update { it.withHistory() }
    }

    fun toggleDrawMode() = local.update { it.copy(drawMode = !it.drawMode, selectedId = null) }

    /** Tap: select the top-most mask under the point, or clear the selection. */
    fun select(at: Point) {
        val hit = history.current.lastOrNull { it.rect.contains(at) }
        local.update { it.copy(selectedId = hit?.id) }
    }

    fun addMask(rect: Rect) {
        val mask = MaskRect(UUID.randomUUID().toString(), rect, FieldType.MANUAL, enabled = true)
        commit(history.current + mask)
        // One box per "Add mask" tap; the new box is selected so it can be adjusted right away.
        local.update { it.copy(selectedId = mask.id, drawMode = false) }
    }

    fun updateMask(id: String, rect: Rect) {
        commit(history.current.map { if (it.id == id) it.copy(rect = rect) else it })
        local.update { it.copy(selectedId = id) }
    }

    fun deleteSelected() {
        val id = local.value.selectedId ?: return
        commit(history.current.filterNot { it.id == id })
    }

    fun undo() {
        if (history.undo()) local.update { it.withHistory() }
    }

    fun redo() {
        if (history.redo()) local.update { it.withHistory() }
    }

    fun rotate() {
        val src = session.image.value ?: return
        if (local.value.rotating) return
        local.update { it.copy(rotating = true) }
        viewModelScope.launch {
            try {
                val rotated = withContext(Dispatchers.Default) { ImageLoader.rotateClockwise(src) }
                // Masks turn with the image, in every undo/redo snapshot too (spec 6.1).
                history.mapAll { masks -> masks.map { it.copy(rect = MaskGeometry.rotateClockwise(it.rect, src.height)) } }
                session.replace(rotated)
                quarterTurns = (quarterTurns + 1) % 4
                local.update { it.withHistory() }
            } catch (e: OutOfMemoryError) {
                SafeLog.w("rotate out of memory", e)
            } finally {
                local.update { it.copy(rotating = false) }
            }
        }
    }

    /** Hands the current masks to the export step. */
    fun next() {
        session.masks = history.current
        local.update { it.copy(selectedId = null, drawMode = false) }
    }

    /** Drops the in-memory original and any temp files. Runs in app scope so it finishes. */
    fun discard() {
        session.clear()
        container.appScope.launch { container.tempFiles.clearOriginals() }
    }
}
