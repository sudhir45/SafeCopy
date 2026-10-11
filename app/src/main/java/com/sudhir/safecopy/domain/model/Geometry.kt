package com.sudhir.safecopy.domain.model

import kotlin.math.max
import kotlin.math.min

/** Pure-Kotlin geometry used by domain code (spec 12): converted to/from RectF at the edges. */
data class Point(val x: Float, val y: Float) {
    operator fun plus(o: Point) = Point(x + o.x, y + o.y)
    operator fun minus(o: Point) = Point(x - o.x, y - o.y)
}

/** Axis-aligned rectangle in bitmap pixel coordinates. Always kept normalised (left ≤ right, top ≤ bottom). */
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(p: Point): Boolean = p.x in left..right && p.y in top..bottom

    fun translate(dx: Float, dy: Float) = Rect(left + dx, top + dy, right + dx, bottom + dy)

    fun intersects(o: Rect): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    fun union(o: Rect) = Rect(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    val area: Float get() = width.coerceAtLeast(0f) * height.coerceAtLeast(0f)

    /** Intersection over union, 0..1. */
    fun iou(o: Rect): Float {
        val inter = Rect(max(left, o.left), max(top, o.top), min(right, o.right), min(bottom, o.bottom))
        if (inter.width <= 0f || inter.height <= 0f) return 0f
        val i = inter.area
        return i / (area + o.area - i)
    }

    /** Grows the rect by [dx] on the left and right and [dy] on the top and bottom. */
    fun inflate(dx: Float, dy: Float) = Rect(left - dx, top - dy, right + dx, bottom + dy)

    companion object {
        /** Rectangle spanned by two arbitrary corners. */
        fun fromCorners(a: Point, b: Point) = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }
}
