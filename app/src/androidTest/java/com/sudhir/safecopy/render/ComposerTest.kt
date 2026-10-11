package com.sudhir.safecopy.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Rect
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 15.3: every pixel inside an enabled mask is #000000 (no watermark yet, M2). */
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
}
