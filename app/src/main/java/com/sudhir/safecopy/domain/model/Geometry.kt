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

    companion object {
        /** Rectangle spanned by two arbitrary corners. */
        fun fromCorners(a: Point, b: Point) = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }
}
