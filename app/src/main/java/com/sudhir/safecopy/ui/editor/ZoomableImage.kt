package com.sudhir.safecopy.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Fit-to-screen image with pinch zoom, pan and double-tap zoom (spec 5.2).
 * Zoom resets whenever the bitmap changes (e.g. after a rotation).
 */
@Composable
fun ZoomableImage(bitmap: Bitmap, contentDescription: String, modifier: Modifier = Modifier) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        // Allow panning only as far as the zoomed content extends past the viewport.
        val maxX = size.width * (s - 1) / 2
        val maxY = size.height * (s - 1) / 2
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(bitmap) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > MIN_SCALE) {
                        scale = MIN_SCALE
                        offset = Offset.Zero
                    } else {
                        scale = DOUBLE_TAP_SCALE
                        // Zoom towards the tapped point.
                        val center = Offset(size.width / 2f, size.height / 2f)
                        offset = clamp((center - tap) * (DOUBLE_TAP_SCALE - 1), DOUBLE_TAP_SCALE)
                    }
                })
            }
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    offset = clamp(offset + pan, newScale)
                    scale = newScale
                }
            },
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
