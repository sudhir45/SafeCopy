package com.sudhir.safecopy.domain.watermark

import com.sudhir.safecopy.domain.model.Point
import com.sudhir.safecopy.domain.model.WatermarkSize
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where to draw each copy of the watermark text (spec 8.1), as pure math.
 *
 * Positions are text baselines (left end) in the *rotated* frame: the renderer rotates the
 * canvas by [angleDegrees] around the image centre, then draws the text at each position.
 * Rows cover the full diagonal so no corner is left bare at any angle, and odd rows are
 * shifted by half a repeat (brick pattern, harder to clone-stamp out).
 */
data class WatermarkLayout(
    val imageWidth: Int,
    val imageHeight: Int,
    val angleDegrees: Float,
    val fontSize: Float,
    val textWidth: Float,
    val positions: List<Point>,
) {
    val rowSpacing: Float get() = fontSize * ROW_SPACING
    val gap: Float get() = fontSize * GAP
    val strokeWidth: Float get() = fontSize * STROKE

    /** Maps an image point into the rotated frame (inverse of the canvas rotation). */
    fun toRotatedFrame(p: Point): Point {
        val cx = imageWidth / 2f
        val cy = imageHeight / 2f
        val a = Math.toRadians(-angleDegrees.toDouble())
        val dx = p.x - cx
        val dy = p.y - cy
        return Point(
            (cx + dx * cos(a) - dy * sin(a)).toFloat(),
            (cy + dx * sin(a) + dy * cos(a)).toFloat(),
        )
    }

    companion object {
        const val ROW_SPACING = 3.2f
        const val GAP = 2f
        const val STROKE = 0.08f

        fun fontSize(imageWidth: Int, size: WatermarkSize): Float = imageWidth / size.widthDivisor

        /** [textWidth] is the measured width of the text at [fontSize] (Paint.measureText). */
        fun compute(
            imageWidth: Int,
            imageHeight: Int,
            angleDegrees: Float,
            fontSize: Float,
            textWidth: Float,
        ): WatermarkLayout {
            require(fontSize > 0f && textWidth > 0f) { "empty watermark" }
            val cx = imageWidth / 2f
            val cy = imageHeight / 2f
            val d = sqrt(imageWidth.toFloat() * imageWidth + imageHeight.toFloat() * imageHeight)
            val rowSpacing = fontSize * ROW_SPACING
            val step = textWidth + fontSize * GAP

            val positions = ArrayList<Point>()
            var row = 0
            var y = cy - d
            while (y <= cy + d + rowSpacing) {
                val shift = if (row % 2 == 1) step / 2 else 0f
                var x = cx - d - step + shift
                while (x <= cx + d) {
                    positions += Point(x, y)
                    x += step
                }
                y += rowSpacing
                row++
            }
            return WatermarkLayout(imageWidth, imageHeight, angleDegrees, fontSize, textWidth, positions)
        }
    }
}
