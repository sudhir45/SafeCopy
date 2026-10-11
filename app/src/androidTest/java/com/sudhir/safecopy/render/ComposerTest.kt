package com.sudhir.safecopy.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Rect
import com.sudhir.safecopy.domain.model.WatermarkSize
import com.sudhir.safecopy.domain.model.WatermarkStyle
import com.sudhir.safecopy.domain.watermark.WatermarkLayout
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 15.3: every pixel inside an enabled mask is #000000 when the watermark is off, and the
 * watermark (M3) reaches every part of the image.
 */
@RunWith(AndroidJUnit4::class)
class ComposerTest {
    private fun source(w: Int = 400, h: Int = 300): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 180, 90)) }

    @Test
    fun maskPixelsAreOpaqueBlackIncludingFractionalEdges() {
        val mask = MaskRect("m", Rect(10.4f, 20.6f, 110.2f, 60.5f), FieldType.MANUAL, enabled = true)
        val out = Composer.compose(source(), listOf(mask))
        for (y in 20 until 61) for (x in 10 until 111) {
            assertThat(out.getPixel(x, y)).isEqualTo(Color.BLACK)
        }
    }

    @Test
    fun pixelsOutsideMasksAreUntouched() {
        val mask = MaskRect("m", Rect(10f, 10f, 50f, 50f), FieldType.MANUAL, enabled = true)
        val out = Composer.compose(source(), listOf(mask))
        assertThat(out.getPixel(200, 200)).isEqualTo(Color.rgb(200, 180, 90))
        assertThat(out.getPixel(50, 25)).isEqualTo(Color.rgb(200, 180, 90))
    }

    @Test
    fun disabledMasksAreNotDrawn() {
        val mask = MaskRect("m", Rect(0f, 0f, 100f, 100f), FieldType.QR, enabled = false)
        val out = Composer.compose(source(), listOf(mask))
        assertThat(out.getPixel(50, 50)).isEqualTo(Color.rgb(200, 180, 90))
    }

    @Test
    fun outputIsANewBitmap() {
        val src = source()
        val out = Composer.compose(src, emptyList())
        assertThat(out).isNotSameInstanceAs(src)
        assertThat(out.config).isEqualTo(Bitmap.Config.ARGB_8888)
    }

    @Test
    fun watermarkLeavesNoBarePatch() {
        for (angle in listOf(-45f, -30f, 0f, 30f, 45f)) {
            for (size in WatermarkSize.entries) {
                val white = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                val wm = WatermarkRender("FOR TEST BANK KYC ONLY · 11 OCT 2026", WatermarkStyle(angleDegrees = angle, size = size))
                val out = Composer.compose(white, emptyList(), wm)
                // Rows are 3.2 × font apart (spec 8.1); at 45° that's ~4.5 × font vertically.
                // A cell 1.5 × the row spacing always crosses at least one row of text.
                val rowSpacing = WatermarkLayout.fontSize(1000, size) * WatermarkLayout.ROW_SPACING
                val cell = (rowSpacing * 1.5f).toInt()
                for (cy in 0 until 700 / cell) for (cx in 0 until 1000 / cell) {
                    var inked = false
                    loop@ for (y in cy * cell until (cy + 1) * cell) for (x in cx * cell until (cx + 1) * cell) {
                        if (out.getPixel(x, y) != Color.WHITE) { inked = true; break@loop }
                    }
                    assertWithMessage("bare cell ($cx,$cy) at angle $angle size $size").that(inked).isTrue()
                }
            }
        }
    }

    @Test
    fun watermarkIsDrawnOverMasks() {
        val black = MaskRect("m", Rect(0f, 0f, 1000f, 700f), FieldType.MANUAL, enabled = true)
        val src = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val out = Composer.compose(src, listOf(black), WatermarkRender("FOR TEST KYC ONLY", WatermarkStyle()))
        var nonBlack = 0
        for (y in 0 until 700 step 5) for (x in 0 until 1000 step 5) if (out.getPixel(x, y) != Color.BLACK) nonBlack++
        assertThat(nonBlack).isGreaterThan(0)
    }
}
