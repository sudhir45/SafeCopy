package com.sudhir.safecopy.ui.editor

import com.sudhir.safecopy.domain.model.Point
import kotlin.math.min

/**
 * Maps between bitmap pixels and editor view pixels (spec 6.1: "a single matrix derived from
 * the fit/zoom/pan transform").
 *
 * The image is first fitted and centred in the view, then zoomed by [zoom] around the view
 * centre and shifted by ([panX], [panY]).
 */
data class ViewTransform(
    val viewWidth: Float,
    val viewHeight: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    private val fit = min(viewWidth / imageWidth, viewHeight / imageHeight)
    private val fitLeft = (viewWidth - imageWidth * fit) / 2
    private val fitTop = (viewHeight - imageHeight * fit) / 2
    private val cx = viewWidth / 2
    private val cy = viewHeight / 2

    /** View pixels per bitmap pixel at the current zoom. */
    val pixelScale: Float get() = fit * zoom

    fun toView(p: Point): Point {
        val fx = fitLeft + p.x * fit
        val fy = fitTop + p.y * fit
        return Point(cx + (fx - cx) * zoom + panX, cy + (fy - cy) * zoom + panY)
    }

    fun toBitmap(p: Point): Point {
        val fx = cx + (p.x - cx - panX) / zoom
        val fy = cy + (p.y - cy - panY) / zoom
        return Point((fx - fitLeft) / fit, (fy - fitTop) / fit)
    }

    /**
     * Applies a pinch: scales by [factor] (result clamped to [minZoom]..[maxZoom]) keeping the
     * point under [centroid] fixed, then pans by [pan]. The result is clamped so the image
     * can't be dragged out of view.
     */
    fun pinch(centroid: Point, factor: Float, pan: Point, minZoom: Float = 1f, maxZoom: Float = 5f): ViewTransform {
        val newZoom = (zoom * factor).coerceIn(minZoom, maxZoom)
        val k = newZoom / zoom
        val gx = centroid.x - cx
        val gy = centroid.y - cy
        return copy(
            zoom = newZoom,
            panX = gx - (gx - panX) * k + pan.x,
            panY = gy - (gy - panY) * k + pan.y,
        ).clamped()
    }

    fun clamped(): ViewTransform {
        val maxX = viewWidth * (zoom - 1) / 2
        val maxY = viewHeight * (zoom - 1) / 2
        return copy(panX = panX.coerceIn(-maxX, maxX), panY = panY.coerceIn(-maxY, maxY))
    }
}
