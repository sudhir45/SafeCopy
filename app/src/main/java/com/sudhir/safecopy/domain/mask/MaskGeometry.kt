package com.sudhir.safecopy.domain.mask

import com.sudhir.safecopy.domain.model.Point
import com.sudhir.safecopy.domain.model.Rect

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Editing operations on mask rectangles (spec 7.2, 7.3). All coordinates are bitmap pixels. */
object MaskGeometry {
    /** Spec 7.2: smaller manual boxes are treated as accidental and dropped. */
    const val MIN_SIZE = 24f

    /** Box from a drag gesture, clamped to the image; null if it's smaller than [MIN_SIZE]. */
    fun fromDrag(start: Point, end: Point, imageWidth: Int, imageHeight: Int): Rect? {
        val r = clampTo(Rect.fromCorners(start, end), imageWidth, imageHeight)
        return if (r.width < MIN_SIZE || r.height < MIN_SIZE) null else r
    }

    /** Clips a rect to the image bounds. */
    fun clampTo(r: Rect, imageWidth: Int, imageHeight: Int) = Rect(
        r.left.coerceIn(0f, imageWidth.toFloat()),
        r.top.coerceIn(0f, imageHeight.toFloat()),
        r.right.coerceIn(0f, imageWidth.toFloat()),
        r.bottom.coerceIn(0f, imageHeight.toFloat()),
    )

    /** Moves a rect by (dx, dy) without changing its size, stopping at the image edges. */
    fun move(r: Rect, dx: Float, dy: Float, imageWidth: Int, imageHeight: Int): Rect {
        val x = dx.coerceIn(-r.left, imageWidth - r.right)
        val y = dy.coerceIn(-r.top, imageHeight - r.bottom)
        return r.translate(x, y)
    }

    /**
     * Drags one corner to [to] while the opposite corner stays put. The corner can't cross
     * the opposite edges (the box keeps at least [MIN_SIZE]) or leave the image.
     */
    fun resize(r: Rect, corner: Corner, to: Point, imageWidth: Int, imageHeight: Int): Rect {
        val w = imageWidth.toFloat()
        val h = imageHeight.toFloat()
        return when (corner) {
            Corner.TOP_LEFT -> Rect(
                to.x.coerceIn(0f, r.right - MIN_SIZE), to.y.coerceIn(0f, r.bottom - MIN_SIZE), r.right, r.bottom,
            )
            Corner.TOP_RIGHT -> Rect(
                r.left, to.y.coerceIn(0f, r.bottom - MIN_SIZE), to.x.coerceIn(r.left + MIN_SIZE, w), r.bottom,
            )
            Corner.BOTTOM_LEFT -> Rect(
                to.x.coerceIn(0f, r.right - MIN_SIZE), r.top, r.right, to.y.coerceIn(r.top + MIN_SIZE, h),
            )
            Corner.BOTTOM_RIGHT -> Rect(
                r.left, r.top, to.x.coerceIn(r.left + MIN_SIZE, w), to.y.coerceIn(r.top + MIN_SIZE, h),
            )
        }
    }

    /**
     * Where [r] ends up after the image (of the given pre-rotation size) turns 90° clockwise.
     * A point (x, y) maps to (imageHeight - y, x).
     */
    fun rotateClockwise(r: Rect, imageHeight: Int) = Rect(
        left = imageHeight - r.bottom,
        top = r.left,
        right = imageHeight - r.top,
        bottom = r.right,
    )
}
