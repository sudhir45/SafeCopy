package com.sudhir.safecopy.domain.watermark

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.sudhir.safecopy.domain.model.Point
import com.sudhir.safecopy.domain.model.WatermarkSize
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class WatermarkLayoutTest {

    /**
     * Spec 15.2: every sampled image point lies within one row spacing of a text baseline,
     * and horizontally inside one repeat (text + gap) of a text instance on that row.
     */
    private fun assertCovers(layout: WatermarkLayout) {
        val step = layout.textWidth + layout.gap
        val rows = layout.positions.groupBy { it.y }
        val grid = 24
        for (i in 0..grid) for (j in 0..grid) {
            val p = Point(layout.imageWidth * i / grid.toFloat(), layout.imageHeight * j / grid.toFloat())
            val r = layout.toRotatedFrame(p)
            val covered = rows.any { (y, row) ->
                abs(r.y - y) <= layout.rowSpacing && row.any { r.x >= it.x && r.x <= it.x + step }
            }
            assertWithMessage("uncovered point $p in $layout".take(200)).that(covered).isTrue()
        }
    }

    @Test
    fun coversRandomImagesAnglesAndSizes() {
        val rnd = Random(42)
        repeat(150) {
            val w = 300 + rnd.nextInt(3700)
            val h = 300 + rnd.nextInt(3700)
            val size = WatermarkSize.entries[rnd.nextInt(3)]
            val font = WatermarkLayout.fontSize(w, size)
            val chars = 10 + rnd.nextInt(71) // 10..80 chars
            val layout = WatermarkLayout.compute(
                imageWidth = w,
                imageHeight = h,
                angleDegrees = -45f + rnd.nextFloat() * 90f,
                fontSize = font,
                textWidth = font * 0.6f * chars,
            )
            assertCovers(layout)
        }
    }

    @Test
    fun coversExtremeAspectRatiosAndAngles() {
        for ((w, h) in listOf(4000 to 300, 300 to 4000, 3000 to 3000)) {
            for (angle in listOf(-45f, -30f, 0f, 30f, 45f)) {
                val font = WatermarkLayout.fontSize(w, WatermarkSize.L)
                assertCovers(WatermarkLayout.compute(w, h, angle, font, font * 0.6f * 80))
            }
        }
    }

    @Test
    fun alternateRowsAreStaggeredByHalfARepeat() {
        val font = 100f
        val tw = 1000f
        val layout = WatermarkLayout.compute(2000, 1000, -30f, font, tw)
        val rows = layout.positions.groupBy { it.y }.toSortedMap().values.toList()
        val step = tw + layout.gap
        val offset = (rows[1].first().x - rows[0].first().x)
        assertThat(offset).isWithin(0.01f).of(step / 2)
    }

    @Test
    fun specConstants() {
        assertThat(WatermarkLayout.fontSize(2200, WatermarkSize.M)).isEqualTo(100f)
        assertThat(WatermarkLayout.fontSize(2800, WatermarkSize.S)).isEqualTo(100f)
        assertThat(WatermarkLayout.fontSize(1600, WatermarkSize.L)).isEqualTo(100f)
        val layout = WatermarkLayout.compute(1000, 1000, 0f, 50f, 300f)
        assertThat(layout.rowSpacing).isEqualTo(160f)
        assertThat(layout.gap).isEqualTo(100f)
        assertThat(layout.strokeWidth).isWithin(0.001f).of(4f)
    }

    @Test
    fun rotatedFrameRoundTripAtCentreIsFixed() {
        val layout = WatermarkLayout.compute(1000, 600, -30f, 50f, 300f)
        val c = layout.toRotatedFrame(Point(500f, 300f))
        assertThat(c.x).isWithin(0.01f).of(500f)
        assertThat(c.y).isWithin(0.01f).of(300f)
    }
}
