package com.sudhir.safecopy.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudhir.safecopy.AppContainer
import com.sudhir.safecopy.core.image.ImageLoader
import com.sudhir.safecopy.core.log.SafeLog
import com.sudhir.safecopy.domain.detect.AadhaarMaskMode
import com.sudhir.safecopy.domain.detect.DetectionResult
import com.sudhir.safecopy.domain.detect.DocType
import com.sudhir.safecopy.domain.detect.PanMaskMode
import com.sudhir.safecopy.domain.mask.MaskGeometry
import com.sudhir.safecopy.domain.mask.UndoStack
import com.sudhir.safecopy.domain.model.Confidence
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Point
import com.sudhir.safecopy.domain.model.Rect
import kotlinx.coroutines.CancellationException
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

/** One row of the Fields sheet: all detected masks of one type. */
data class FieldSummary(val type: FieldType, val count: Int, val enabled: Boolean, val needsCheck: Boolean)

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
    /** Spec 5.2: "Finding sensitive details…" while detection runs; editing waits for it. */
    val detecting: Boolean = false,
    val docType: DocType = DocType.UNKNOWN,
    val fields: List<FieldSummary> = emptyList(),
    val qrHint: Boolean = false,
    val showFieldsSheet: Boolean = false,
    /** Mask modes from Settings, so the sheet can say "first 8 digits" or "all 12 digits". */
    val aadhaarAll: Boolean = false,
    val panAll: Boolean = false,
)

class EditorViewModel(private val container: AppContainer) : ViewModel() {
    private val session = container.editSession

    /** Captured once: if there is no image now, the process was killed and the session is gone. */
    val sessionExpired: Boolean = session.image.value == null

    private var history = UndoStack<List<MaskRect>>(session.masks)
    private var quarterTurns = 0
    private val local = MutableStateFlow(EditorUiState().withHistory())

    val state: StateFlow<EditorUiState> = combine(session.image, local) { image, l -> l.copy(image = image) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, local.value.copy(image = session.image.value))

    init {
        val done = session.detection
        if (done != null) applyDetectionInfo(done) else session.image.value?.let { detect(it) }
    }

    /** Spec 5.2: detect on entry, then show the summary sheet. Runs once per image. */
    private fun detect(image: Bitmap) {
        local.update { it.copy(detecting = true) }
        viewModelScope.launch {
            val result = try {
                val options = container.settings.current().detectionOptions
                local.update { it.copy(aadhaarAll = options.aadhaarMaskMode == AadhaarMaskMode.ALL_12, panAll = options.panMaskMode == PanMaskMode.ALL) }
                val oriented = container.detection.detect(image, options)
                if (oriented.rotationCw != 0) session.replace(oriented.bitmap)
                oriented.result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Model or decode failure: carry on with manual masking only (spec M5 error states).
                SafeLog.e("detection failed", e)
                null
            } catch (e: OutOfMemoryError) {
                SafeLog.e("detection out of memory", e)
                null
            }
            val r = result ?: DetectionResult(DocType.UNKNOWN, emptyList(), emptyList(), qrHint = false, textAmount = 0)
            session.detection = r
            // Detected masks are the starting point, not an undo step: Undo can't wipe them out.
            history = UndoStack(r.masks + history.current)
            applyDetectionInfo(r)
            local.update { it.withHistory().copy(detecting = false, showFieldsSheet = true) }
        }
    }

    private fun applyDetectionInfo(r: DetectionResult) {
        local.update { it.copy(docType = r.docType, qrHint = r.qrHint).withHistory() }
    }

    private fun EditorUiState.withHistory(): EditorUiState {
        val masks = history.current
        return copy(
            masks = masks,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            selectedId = selectedId?.takeIf { id -> masks.any { it.id == id && it.enabled } },
            hasEdits = history.canUndo || quarterTurns != 0,
            fields = masks.filter { it.source != FieldType.MANUAL }
                .groupBy { it.source }
                .toSortedMap(compareBy { FIELD_ORDER.indexOf(it) })
                .map { (type, group) ->
                    FieldSummary(type, group.size, group.any { it.enabled }, group.any { it.confidence == Confidence.LOW })
                },
        )
    }

    private fun commit(masks: List<MaskRect>) {
        history.push(masks)
        local.update { it.withHistory() }
    }

    fun openFields() = local.update { it.copy(showFieldsSheet = true, drawMode = false) }
    fun closeFields() = local.update { it.copy(showFieldsSheet = false) }

    /** Fields sheet switch: turns every detected mask of [type] on or off (one undo step). */
    fun setFieldEnabled(type: FieldType, enabled: Boolean) {
        commit(history.current.map { if (it.source == type) it.copy(enabled = enabled) else it })
    }

    fun toggleDrawMode() = local.update { it.copy(drawMode = !it.drawMode, selectedId = null) }

    /** Tap: select the top-most visible mask under the point, or clear the selection. */
    fun select(at: Point) {
        val hit = history.current.lastOrNull { it.enabled && it.rect.contains(at) }
        local.update { it.copy(selectedId = hit?.id) }
    }

    fun addMask(rect: Rect) {
        val mask = MaskRect(UUID.randomUUID().toString(), rect, FieldType.MANUAL, enabled = true)
        commit(history.current + mask)
        // One box per "Add mask" tap; the new box is selected so it can be adjusted right away.
        local.update { it.copy(selectedId = mask.id, drawMode = false) }
    }

    fun updateMask(id: String, rect: Rect) {
        // A box the user moved or resized has been checked, so it's no longer "low confidence".
        commit(history.current.map { if (it.id == id) it.copy(rect = rect, confidence = Confidence.HIGH) else it })
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
        if (local.value.rotating || local.value.detecting) return
        local.update { it.copy(rotating = true) }
        viewModelScope.launch {
            try {
                val rotated = withContext(Dispatchers.Default) { ImageLoader.rotateClockwise(src) }
                // Masks (detected and manual) turn with the image, in every undo/redo snapshot
                // too. Detection isn't re-run: it already tried all four orientations.
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

    private companion object {
        /** Sheet order, matching spec 5.2. */
        val FIELD_ORDER = listOf(
            FieldType.AADHAAR_NUMBER, FieldType.AADHAAR_VID, FieldType.QR, FieldType.PAN,
            FieldType.DOB, FieldType.ADDRESS, FieldType.PHOTO,
        )
    }
}
