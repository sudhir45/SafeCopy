package com.sudhir.safecopy.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.sudhir.safecopy.domain.model.MaskRect

/**
 * Draws the shareable copy onto a brand-new bitmap (spec 2.5, 7.2, 9):
 * source, then enabled masks as solid opaque fills. The watermark is drawn after the masks
 * (M3), so it runs across them.
 *
 * Nothing from the source file survives except its pixels: there is no metadata on a Bitmap,
 * and masked pixels are overwritten, not blurred.
 */
object Composer {
    fun compose(source: Bitmap, masks: List<MaskRect>, maskColor: Int = Color.BLACK): Bitmap {
        val out = createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)

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
            canvas.drawRect(
                RectF(
                    kotlin.math.floor(r.left),
                    kotlin.math.floor(r.top),
                    kotlin.math.ceil(r.right),
                    kotlin.math.ceil(r.bottom),
                ),
                paint,
            )
        }
        return out
    }
}
