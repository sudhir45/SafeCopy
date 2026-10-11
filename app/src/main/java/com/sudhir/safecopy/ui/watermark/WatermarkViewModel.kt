package com.sudhir.safecopy.ui.watermark

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudhir.safecopy.AppContainer
import com.sudhir.safecopy.core.log.SafeLog
import com.sudhir.safecopy.data.settings.RecentRecipients
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Rect
import com.sudhir.safecopy.domain.model.WatermarkColour
import com.sudhir.safecopy.domain.model.WatermarkConfig
import com.sudhir.safecopy.domain.model.WatermarkSize
import com.sudhir.safecopy.domain.model.WatermarkStyle
import com.sudhir.safecopy.domain.model.WatermarkTemplate
import com.sudhir.safecopy.render.Composer
import com.sudhir.safecopy.render.WatermarkRender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.max

class WatermarkViewModel(private val container: AppContainer) : ViewModel() {
    private val session = container.editSession

    val sessionExpired: Boolean = session.image.value == null

    private val _config = MutableStateFlow(session.watermark ?: WatermarkConfig(date = LocalDate.now()))
    val config: StateFlow<WatermarkConfig> = _config.asStateFlow()

    /** Live preview rendered with the real Composer on a downscaled copy (spec 5.2). */
    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    val suggestions: StateFlow<List<String>> =
        combine(container.settings.recentRecipients, _config) { recents, c -> RecentRecipients.suggest(recents, c.recipient) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val source = session.image.value
        if (source != null) startPreview(source, session.masks)
    }

    /**
     * The watermark is laid out relative to image width (font = W / 22 etc.), so a scaled-down
     * render looks the same as the full one, just smaller. Re-rendered at most every 120 ms.
     */
    private fun startPreview(source: Bitmap, masks: List<MaskRect>) {
        viewModelScope.launch {
            val scale = PREVIEW_EDGE.toFloat() / max(source.width, source.height)
            val (small, smallMasks) = withContext(Dispatchers.Default) {
                if (scale >= 1f) {
                    source to masks
                } else {
                    val w = (source.width * scale).toInt().coerceAtLeast(1)
                    val h = (source.height * scale).toInt().coerceAtLeast(1)
                    source.scale(w, h) to masks.map { it.copy(rect = it.rect.scaled(scale)) }
                }
            }
            _config.collectLatest { c ->
                delay(PREVIEW_DEBOUNCE_MS)
                try {
                    val render = c.takeIf { it.isComplete }?.let { WatermarkRender(it.text(), it.style) }
                    _preview.value = withContext(Dispatchers.Default) { Composer.compose(small, smallMasks, render) }
                } catch (e: OutOfMemoryError) {
                    SafeLog.w("preview out of memory", e)
                }
            }
        }
    }

    fun setTemplate(t: WatermarkTemplate) = _config.value.let { _config.value = it.copy(template = t) }
    fun setRecipient(r: String) = _config.value.let { _config.value = it.copy(recipient = r.take(MAX_FIELD)) }
    fun setPurpose(p: String) = _config.value.let { _config.value = it.copy(purpose = p.take(MAX_FIELD)) }
    fun setDate(d: LocalDate) = _config.value.let { _config.value = it.copy(date = d) }
    fun setUppercase(u: Boolean) = _config.value.let { _config.value = it.copy(uppercase = u) }
    fun setOpacity(o: Float) = updateStyle { it.copy(opacity = o) }
    fun setAngle(a: Float) = updateStyle { it.copy(angleDegrees = a) }
    fun setSize(s: WatermarkSize) = updateStyle { it.copy(size = s) }
    fun setColour(c: WatermarkColour) = updateStyle { it.copy(colour = c) }

    private fun updateStyle(f: (WatermarkStyle) -> WatermarkStyle) {
        _config.value = _config.value.let { it.copy(style = f(it.style)) }
    }

    /** Hands the config to the export step. Only called when [WatermarkConfig.isComplete]. */
    fun next() {
        session.watermark = _config.value
    }

    private fun Rect.scaled(s: Float) = Rect(left * s, top * s, right * s, bottom * s)

    private companion object {
        const val PREVIEW_EDGE = 1200
        const val PREVIEW_DEBOUNCE_MS = 120L

        /** Input cap; the rendered text is cut to 80 characters anyway (spec 8.2). */
        const val MAX_FIELD = 120
    }
}
