package com.sudhir.safecopy.core.image

import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.core.image.ImageMath.Orientation
import com.sudhir.safecopy.core.image.ImageMath.Size
import org.junit.Test
import java.io.ByteArrayInputStream

class ImageMathTest {
    @Test
    fun exifOrientationMapping() {
        assertThat(ImageMath.orientationFor(1)).isEqualTo(Orientation(0, false))
        assertThat(ImageMath.orientationFor(2)).isEqualTo(Orientation(0, true))
        assertThat(ImageMath.orientationFor(3)).isEqualTo(Orientation(180, false))
        assertThat(ImageMath.orientationFor(4)).isEqualTo(Orientation(180, true))
        assertThat(ImageMath.orientationFor(5)).isEqualTo(Orientation(90, true))
        assertThat(ImageMath.orientationFor(6)).isEqualTo(Orientation(90, false))
        assertThat(ImageMath.orientationFor(7)).isEqualTo(Orientation(270, true))
        assertThat(ImageMath.orientationFor(8)).isEqualTo(Orientation(270, false))
    }

    @Test
    fun unknownOrientationIsIdentity() {
        assertThat(ImageMath.orientationFor(0)).isEqualTo(Orientation(0, false))
        assertThat(ImageMath.orientationFor(42)).isEqualTo(Orientation(0, false))
    }

    @Test
    fun quarterTurnsSwapDimensions() {
        assertThat(ImageMath.orientationFor(6).swapsDimensions).isTrue()
        assertThat(ImageMath.orientationFor(8).swapsDimensions).isTrue()
        assertThat(ImageMath.orientationFor(3).swapsDimensions).isFalse()
    }

    @Test
    fun sampleSizeNeverDropsBelowMaxEdge() {
        assertThat(ImageMath.inSampleSize(2000, 1500)).isEqualTo(1)
        assertThat(ImageMath.inSampleSize(3000, 2000)).isEqualTo(1)
        assertThat(ImageMath.inSampleSize(6000, 4000)).isEqualTo(2)
        assertThat(ImageMath.inSampleSize(4000, 3000)).isEqualTo(1) // 12 MP: under the pixel cap
        assertThat(ImageMath.inSampleSize(4000, 12000)).isEqualTo(4)
        for (edge in listOf(3001, 4500, 8000, 16000, 40000)) {
            val s = ImageMath.inSampleSize(edge, 100)
            assertThat(edge / s).isAtLeast(ImageMath.MAX_EDGE)
        }
    }

    @Test
    fun pixelCapRaisesSampleForBigButNarrowEdgePhotos() {
        // 5999×4000 = 24 MP: the edge rule alone would decode it at full size (96 MB).
        assertThat(ImageMath.inSampleSize(5999, 4000)).isEqualTo(2)
        // Decompression-bomb shape: 20000×20000 must not decode at 10000×10000.
        val s = ImageMath.inSampleSize(20000, 20000)
        assertThat((20000L / s) * (20000L / s)).isAtMost(ImageMath.MAX_DECODE_PIXELS)
    }

    @Test
    fun fitWithinCapsLongEdgeAndKeepsAspect() {
        assertThat(ImageMath.fitWithin(2000, 1000)).isEqualTo(Size(2000, 1000))
        assertThat(ImageMath.fitWithin(4000, 3000)).isEqualTo(Size(3000, 2250))
        assertThat(ImageMath.fitWithin(3000, 6000)).isEqualTo(Size(1500, 3000))
    }

    @Test
    fun fitWithinNeverReturnsZero() {
        assertThat(ImageMath.fitWithin(100000, 1)).isEqualTo(Size(3000, 1))
    }

    @Test
    fun readCappedReturnsBytesUnderLimit() {
        val data = ByteArray(1000) { it.toByte() }
        assertThat(ImageMath.readCapped(ByteArrayInputStream(data), maxBytes = 1000)).isEqualTo(data)
    }

    @Test
    fun readCappedRejectsOverLimit() {
        assertThat(ImageMath.readCapped(ByteArrayInputStream(ByteArray(1001)), maxBytes = 1000)).isNull()
    }
}
