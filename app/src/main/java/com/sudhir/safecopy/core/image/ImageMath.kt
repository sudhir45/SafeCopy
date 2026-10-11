package com.sudhir.safecopy.core.image

import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure sizing/orientation math for image loading. No Android types, so it runs in JVM tests. */
object ImageMath {
    /** Spec 4.1 / 5.3: long edge is capped at this many pixels on load. */
    const val MAX_EDGE = 3000

    /** Spec 14.2: incoming images over this size are rejected. */
    const val MAX_BYTES = 25L * 1024 * 1024

    data class Size(val width: Int, val height: Int)

    /**
     * How to draw a decoded bitmap upright: rotate clockwise by [rotationDegrees], then
     * (if [flipHorizontal]) mirror horizontally.
     */
    data class Orientation(val rotationDegrees: Int, val flipHorizontal: Boolean) {
        val swapsDimensions: Boolean get() = rotationDegrees % 180 != 0
    }

    /** Maps an EXIF orientation tag (1..8) to a transform. Unknown values mean "as is". */
    fun orientationFor(exifOrientation: Int): Orientation = when (exifOrientation) {
        2 -> Orientation(0, flipHorizontal = true) // FLIP_HORIZONTAL
        3 -> Orientation(180, flipHorizontal = false) // ROTATE_180
        4 -> Orientation(180, flipHorizontal = true) // FLIP_VERTICAL
        5 -> Orientation(90, flipHorizontal = true) // TRANSPOSE
        6 -> Orientation(90, flipHorizontal = false) // ROTATE_90
        7 -> Orientation(270, flipHorizontal = true) // TRANSVERSE
        8 -> Orientation(270, flipHorizontal = false) // ROTATE_270
        else -> Orientation(0, flipHorizontal = false) // NORMAL / UNDEFINED
    }

    /** Spec 5.3 / M5 "huge image": never decode more than this many pixels (~64 MB as ARGB). */
    const val MAX_DECODE_PIXELS = 16_000_000L

    /**
     * Power-of-two `inSampleSize`: the largest that keeps the decoded long edge at or above
     * [maxEdge] (so the exact downscale afterwards never upscales), raised further if the
     * decode would still exceed [maxPixels]. A 6000×4000 photo decodes at 3000×2000, not at
     * full size first.
     */
    fun inSampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE, maxPixels: Long = MAX_DECODE_PIXELS): Int {
        val longEdge = max(width, height)
        var sample = 1
        while (longEdge / (sample * 2) >= maxEdge) sample *= 2
        while ((width.toLong() / sample) * (height.toLong() / sample) > maxPixels) sample *= 2
        return sample
    }

    /** Size after scaling so the long edge is at most [maxEdge], keeping aspect ratio. */
    fun fitWithin(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Size {
        val longEdge = max(width, height)
        if (longEdge <= maxEdge) return Size(width, height)
        val scale = maxEdge.toDouble() / longEdge
        return Size(
            max(1, (width * scale).roundToInt()),
            max(1, (height * scale).roundToInt()),
        )
    }

    /**
     * Reads the whole stream, or returns null as soon as it exceeds [maxBytes].
     * The caller owns (and closes) the stream.
     */
    fun readCapped(input: InputStream, maxBytes: Long = MAX_BYTES): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
