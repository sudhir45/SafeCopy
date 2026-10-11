package com.sudhir.safecopy.ui.editor

import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.domain.model.Point
import org.junit.Test
import kotlin.random.Random

class ViewTransformTest {
    private fun assertNear(a: Point, b: Point) {
        assertThat(a.x).isWithin(0.01f).of(b.x)
        assertThat(a.y).isWithin(0.01f).of(b.y)
    }

    @Test
    fun fitCentresWideImage() {
        // 2000x1000 into 1000x1000: scale 0.5, letterboxed 250 px top and bottom.
        val t = ViewTransform(1000f, 1000f, 2000, 1000)
        assertNear(t.toView(Point(0f, 0f)), Point(0f, 250f))
        assertNear(t.toView(Point(2000f, 1000f)), Point(1000f, 750f))
    }

    @Test
    fun roundTripsAtAnyZoomAndPan() {
        val rnd = Random(7)
        repeat(200) {
            val t = ViewTransform(
                viewWidth = 300f + rnd.nextFloat() * 1000,
                viewHeight = 300f + rnd.nextFloat() * 1000,
                imageWidth = 200 + rnd.nextInt(3000),
                imageHeight = 200 + rnd.nextInt(3000),
                zoom = 1f + rnd.nextFloat() * 4,
                panX = rnd.nextFloat() * 400 - 200,
                panY = rnd.nextFloat() * 400 - 200,
            )
            val p = Point(rnd.nextFloat() * t.imageWidth, rnd.nextFloat() * t.imageHeight)
            assertNear(t.toBitmap(t.toView(p)), p)
        }
    }

    @Test
    fun pinchKeepsCentroidFixed() {
        val t = ViewTransform(1000f, 800f, 3000, 2000)
        val centroid = Point(300f, 200f)
        val before = t.toBitmap(centroid)
        val after = t.pinch(centroid, factor = 2f, pan = Point(0f, 0f))
        assertThat(after.zoom).isEqualTo(2f)
        assertNear(after.toBitmap(centroid), before)
    }

    @Test
    fun zoomAndPanAreClamped() {
        val t = ViewTransform(1000f, 800f, 3000, 2000)
        val out = t.pinch(Point(500f, 400f), factor = 100f, pan = Point(99999f, -99999f))
        assertThat(out.zoom).isEqualTo(5f)
        assertThat(out.panX).isEqualTo(2000f)
        assertThat(out.panY).isEqualTo(-1600f)
        assertThat(t.pinch(Point(0f, 0f), factor = 0.1f, pan = Point(50f, 50f)).zoom).isEqualTo(1f)
    }
}
