package com.sudhir.safecopy.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.WatermarkColour
import com.sudhir.safecopy.domain.model.WatermarkStyle
import com.sudhir.safecopy.domain.watermark.WatermarkLayout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Final watermark text plus how to draw it. */
data class WatermarkRender(val text: String, val style: WatermarkStyle)

/**
 * Draws the shareable copy onto a brand-new bitmap (spec 2.5, 7.2, 8.1, 9):
 * source, then enabled masks as solid opaque fills, then the tiled watermark, so the
 * watermark runs across the masks.
 *
 * Nothing from the source file survives except its pixels: there is no metadata on a Bitmap,
 * and masked pixels are overwritten, not blurred.
 *
 * The same function renders the Watermark screen's live preview (on a downscaled copy) and
 * the final export, so what the user sees is what gets shared.
 */
object Composer {
    fun compose(
        source: Bitmap,
        masks: List<MaskRect>,
        watermark: WatermarkRender? = null,
        maskColor: Int = Color.BLACK,
    ): Bitmap {
        val out = createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)
        drawMasks(canvas, masks, maskColor)
        if (watermark != null && watermark.text.isNotBlank()) drawWatermark(canvas, out.width, out.height, watermark)
        return out
    }

    private fun drawMasks(canvas: Canvas, masks: List<MaskRect>, maskColor: Int) {
        val paint = Paint().apply {
            color = maskColor
            alpha = 255
            style = Paint.Style.FILL
            isAntiAlias = false // hard edges: no partially transparent border pixels
        }
        for (mask in masks) {
            if (!mask.enabled) continue
            val r = mask.rect
            // Round outwards so a fractional edge never leaves a half-covered pixel.
            canvas.drawRect(RectF(floor(r.left), floor(r.top), ceil(r.right), ceil(r.bottom)), paint)
        }
    }

    /** Spec 8.1: rotated, brick-tiled text, each copy stroked in a contrasting colour then filled. */
    private fun drawWatermark(canvas: Canvas, width: Int, height: Int, wm: WatermarkRender) {
        val wmStyle = wm.style
        val fontSize = WatermarkLayout.fontSize(width, wmStyle.size)
        val (fillColor, strokeColor) = colours(wmStyle.colour)
        val opacity = wmStyle.opacity.coerceIn(WatermarkStyle.MIN_OPACITY, WatermarkStyle.MAX_OPACITY)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = fontSize
            color = fillColor
            alpha = (opacity * 255).roundToInt()
        }
        val stroke = Paint(fill).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = fontSize * WatermarkLayout.STROKE
            color = strokeColor
            alpha = (opacity * 0.8f * 255).roundToInt()
        }

        val layout = WatermarkLayout.compute(
            imageWidth = width,
            imageHeight = height,
            angleDegrees = wmStyle.angleDegrees,
            fontSize = fontSize,
            textWidth = fill.measureText(wm.text),
        )
        canvas.withRotation(wmStyle.angleDegrees, width / 2f, height / 2f) {
            for (p in layout.positions) {
                drawText(wm.text, p.x, p.y, stroke)
                drawText(wm.text, p.x, p.y, fill)
            }
        }
    }

    /** (fill, stroke) per colour option. DARK is the spec default: #202020 on a white outline. */
    private fun colours(colour: WatermarkColour): Pair<Int, Int> = when (colour) {
        WatermarkColour.DARK -> Color.rgb(0x20, 0x20, 0x20) to Color.WHITE
        WatermarkColour.LIGHT -> Color.WHITE to Color.rgb(0x20, 0x20, 0x20)
        WatermarkColour.RED -> Color.rgb(0xC6, 0x28, 0x28) to Color.WHITE
    }
}
