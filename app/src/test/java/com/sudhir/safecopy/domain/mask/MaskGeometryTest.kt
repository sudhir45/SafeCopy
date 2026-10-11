package com.sudhir.safecopy.domain.mask

import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.domain.model.Point
import com.sudhir.safecopy.domain.model.Rect
import org.junit.Test

class MaskGeometryTest {
    private val w = 1000
    private val h = 600

    @Test
    fun dragInAnyDirectionGivesNormalisedRect() {
        val r = MaskGeometry.fromDrag(Point(300f, 200f), Point(100f, 50f), w, h)
        assertThat(r).isEqualTo(Rect(100f, 50f, 300f, 200f))
    }

    @Test
    fun dragIsClampedToImage() {
        val r = MaskGeometry.fromDrag(Point(-50f, -20f), Point(2000f, 900f), w, h)
        assertThat(r).isEqualTo(Rect(0f, 0f, 1000f, 600f))
    }

    @Test
    fun tinyDragIsDropped() {
        assertThat(MaskGeometry.fromDrag(Point(10f, 10f), Point(30f, 200f), w, h)).isNull()
        assertThat(MaskGeometry.fromDrag(Point(10f, 10f), Point(200f, 33f), w, h)).isNull()
        assertThat(MaskGeometry.fromDrag(Point(10f, 10f), Point(34f, 34f), w, h)).isNotNull()
    }

    @Test
    fun moveKeepsSizeAndStopsAtEdges() {
        val r = Rect(100f, 100f, 200f, 150f)
        assertThat(MaskGeometry.move(r, 50f, 20f, w, h)).isEqualTo(Rect(150f, 120f, 250f, 170f))
        assertThat(MaskGeometry.move(r, -500f, -500f, w, h)).isEqualTo(Rect(0f, 0f, 100f, 50f))
        assertThat(MaskGeometry.move(r, 5000f, 5000f, w, h)).isEqualTo(Rect(900f, 550f, 1000f, 600f))
    }

    @Test
    fun resizeMovesOnlyTheDraggedCorner() {
        val r = Rect(100f, 100f, 200f, 200f)
        assertThat(MaskGeometry.resize(r, Corner.BOTTOM_RIGHT, Point(300f, 250f), w, h))
            .isEqualTo(Rect(100f, 100f, 300f, 250f))
        assertThat(MaskGeometry.resize(r, Corner.TOP_LEFT, Point(50f, 60f), w, h))
            .isEqualTo(Rect(50f, 60f, 200f, 200f))
        assertThat(MaskGeometry.resize(r, Corner.TOP_RIGHT, Point(260f, 80f), w, h))
            .isEqualTo(Rect(100f, 80f, 260f, 200f))
        assertThat(MaskGeometry.resize(r, Corner.BOTTOM_LEFT, Point(90f, 210f), w, h))
            .isEqualTo(Rect(90f, 100f, 200f, 210f))
    }

    @Test
    fun resizeCannotCrossOppositeEdgeOrLeaveImage() {
        val r = Rect(100f, 100f, 200f, 200f)
        val crossed = MaskGeometry.resize(r, Corner.BOTTOM_RIGHT, Point(0f, 0f), w, h)
        assertThat(crossed).isEqualTo(Rect(100f, 100f, 124f, 124f))
        val outside = MaskGeometry.resize(r, Corner.TOP_LEFT, Point(-100f, -100f), w, h)
        assertThat(outside).isEqualTo(Rect(0f, 0f, 200f, 200f))
    }

    @Test
    fun rotateClockwiseMapsRectWithImage() {
        // Image 1000x600 becomes 600x1000. Top-left box ends up top-right.
        val r = Rect(0f, 0f, 100f, 50f)
        assertThat(MaskGeometry.rotateClockwise(r, imageHeight = 600)).isEqualTo(Rect(550f, 0f, 600f, 100f))
    }

    @Test
    fun fourRotationsAreIdentity() {
        val r = Rect(120f, 40f, 380f, 90f)
        var cur = r
        var iw = w
        var ih = h
        repeat(4) {
            cur = MaskGeometry.rotateClockwise(cur, ih)
            iw = ih.also { ih = iw }
        }
        assertThat(cur).isEqualTo(r)
    }
}
