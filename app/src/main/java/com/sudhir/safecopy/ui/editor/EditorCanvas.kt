package com.sudhir.safecopy.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sudhir.safecopy.domain.mask.Corner
import com.sudhir.safecopy.domain.mask.MaskGeometry
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Point
import com.sudhir.safecopy.domain.model.Rect
import kotlin.math.roundToInt

private const val DOUBLE_TAP_ZOOM = 2.5f
private const val DOUBLE_TAP_MS = 300L

private sealed interface Drag {
    data object Pan : Drag
    data object Pinch : Drag
    data object Draw : Drag
    data class Move(val mask: MaskRect) : Drag
    data class Resize(val mask: MaskRect, val corner: Corner) : Drag
}

/**
 * Image plus mask overlay with all editor gestures (spec 7.3):
 * - one finger on a selected mask's corner handle: resize
 * - one finger on a mask: move it (and select it)
 * - one finger elsewhere: draw a new box in draw mode, otherwise pan the zoomed image
 * - two fingers: pinch zoom and pan (cancels any one-finger edit in progress)
 * - tap: select the mask under the finger, or clear the selection; double-tap: zoom in/out
 *
 * Edits are previewed locally while the finger moves and reported once, on release, so each
 * gesture is one undo step.
 */
@Composable
fun EditorCanvas(
    bitmap: Bitmap,
    masks: List<MaskRect>,
    selectedId: String?,
    drawMode: Boolean,
    contentDescription: String,
    onTap: (Point) -> Unit,
    onCreate: (Rect) -> Unit,
    onChange: (id: String, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomPan by remember(bitmap) { mutableStateOf(Triple(1f, 0f, 0f)) }
    /** Rect being drawn or edited right now (id null = new box); not yet committed. */
    var preview by remember { mutableStateOf<Pair<String?, Rect>?>(null) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    val handleRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    val handleDrawRadius = with(LocalDensity.current) { 7.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }

    val currentMasks by rememberUpdatedState(masks)
    val currentSelected by rememberUpdatedState(selectedId)
    val currentDrawMode by rememberUpdatedState(drawMode)

    fun transform() = ViewTransform(
        viewSize.width.toFloat(), viewSize.height.toFloat(), bitmap.width, bitmap.height,
        zoom = zoomPan.first, panX = zoomPan.second, panY = zoomPan.third,
    )

    fun setTransform(t: ViewTransform) {
        zoomPan = Triple(t.zoom, t.panX, t.panY)
    }

    val selectedColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier
            .onSizeChanged { viewSize = it }
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(bitmap) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val t0 = transform()
                    val start = t0.toBitmap(down.position.toPoint())
                    val selected = currentMasks.firstOrNull { it.id == currentSelected }

                    var drag: Drag = when {
                        selected != null && hitCorner(selected.rect, t0, down.position, handleRadiusPx) != null ->
                            Drag.Resize(selected, hitCorner(selected.rect, t0, down.position, handleRadiusPx)!!)
                        else -> {
                            // Prefer the selected mask, then the top-most one under the finger.
                            val hit = selected?.takeIf { it.rect.contains(start) }
                                ?: currentMasks.lastOrNull { it.rect.contains(start) }
                            when {
                                hit != null -> Drag.Move(hit)
                                currentDrawMode -> Drag.Draw
                                else -> Drag.Pan
                            }
                        }
                    }
                    var moved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size > 1 && drag != Drag.Pinch) {
                            drag = Drag.Pinch
                            preview = null
                        }
                        val t = transform()
                        when (val d = drag) {
                            Drag.Pinch -> {
                                setTransform(
                                    t.pinch(
                                        centroid = event.calculateCentroid().toPoint(),
                                        factor = event.calculateZoom(),
                                        pan = event.calculatePan().toPoint(),
                                    ),
                                )
                                moved = true
                            }
                            Drag.Pan -> {
                                val pan = event.calculatePan()
                                if ((pressed[0].position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (moved && t.zoom > 1f) setTransform(t.copy(panX = t.panX + pan.x, panY = t.panY + pan.y).clamped())
                            }
                            else -> {
                                val pos = pressed[0].position
                                if (!moved && (pos - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (moved) {
                                    val p = t.toBitmap(pos.toPoint())
                                    preview = when (d) {
                                        Drag.Draw -> null to Rect.fromCorners(start, p)
                                        is Drag.Move -> d.mask.id to MaskGeometry.move(
                                            d.mask.rect, p.x - start.x, p.y - start.y, bitmap.width, bitmap.height,
                                        )
                                        is Drag.Resize -> d.mask.id to MaskGeometry.resize(
                                            d.mask.rect, d.corner, p, bitmap.width, bitmap.height,
                                        )
                                        else -> null
                                    }
                                }
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }

                    val result = preview
                    preview = null
                    when {
                        !moved -> {
                            val now = System.currentTimeMillis()
                            if (now - lastTapAt < DOUBLE_TAP_MS) {
                                lastTapAt = 0L
                                val t = transform()
                                setTransform(
                                    if (t.zoom > 1f) t.copy(zoom = 1f, panX = 0f, panY = 0f)
                                    else t.pinch(down.position.toPoint(), DOUBLE_TAP_ZOOM, Point(0f, 0f)),
                                )
                            } else {
                                lastTapAt = now
                                onTap(start)
                            }
                        }
                        result != null && drag == Drag.Draw -> {
                            MaskGeometry.fromDrag(
                                Point(result.second.left, result.second.top),
                                Point(result.second.right, result.second.bottom),
                                bitmap.width, bitmap.height,
                            )?.let(onCreate)
                        }
                        result?.first != null -> onChange(result.first!!, result.second)
                    }
                }
            },
    ) {
        if (viewSize == IntSize.Zero) return@Canvas
        val t = transform()
        val topLeft = t.toView(Point(0f, 0f))
        val bottomRight = t.toView(Point(bitmap.width.toFloat(), bitmap.height.toFloat()))
        drawImage(
            image,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize((bottomRight.x - topLeft.x).roundToInt(), (bottomRight.y - topLeft.y).roundToInt()),
            filterQuality = FilterQuality.Medium,
        )

        val editing = preview
        for (mask in masks) {
            if (!mask.enabled) continue
            val rect = if (editing?.first == mask.id) editing.second else mask.rect
            drawMask(t, rect, selected = mask.id == selectedId, selectedColor, strokePx, handleDrawRadius)
        }
        if (editing != null && editing.first == null) {
            drawMask(t, editing.second, selected = true, selectedColor, strokePx, handleRadius = 0f)
        }
    }
}

/** While editing, masks are see-through so the user can tell what's underneath (spec 5.2). */
private fun DrawScope.drawMask(
    t: ViewTransform,
    rect: Rect,
    selected: Boolean,
    selectedColor: Color,
    strokePx: Float,
    handleRadius: Float,
) {
    val a = t.toView(Point(rect.left, rect.top))
    val b = t.toView(Point(rect.right, rect.bottom))
    val size = Size(b.x - a.x, b.y - a.y)
    val offset = Offset(a.x, a.y)
    drawRect(Color.Black.copy(alpha = 0.6f), offset, size)
    drawRect(if (selected) selectedColor else Color.White, offset, size, style = Stroke(strokePx))
    if (selected && handleRadius > 0f) {
        for (c in listOf(Offset(a.x, a.y), Offset(b.x, a.y), Offset(a.x, b.y), Offset(b.x, b.y))) {
            drawCircle(Color.White, handleRadius, c)
            drawCircle(selectedColor, handleRadius, c, style = Stroke(strokePx))
        }
    }
}

private fun hitCorner(rect: Rect, t: ViewTransform, at: Offset, radius: Float): Corner? {
    val corners = mapOf(
        Corner.TOP_LEFT to Point(rect.left, rect.top),
        Corner.TOP_RIGHT to Point(rect.right, rect.top),
        Corner.BOTTOM_LEFT to Point(rect.left, rect.bottom),
        Corner.BOTTOM_RIGHT to Point(rect.right, rect.bottom),
    )
    return corners.entries
        .map { (corner, p) -> corner to (t.toView(p).toOffset() - at).getDistance() }
        .filter { it.second <= radius }
        .minByOrNull { it.second }
        ?.first
}

private fun Offset.toPoint() = Point(x, y)
private fun Point.toOffset() = Offset(x, y)
