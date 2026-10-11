package com.sudhir.safecopy.ml

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.sudhir.safecopy.core.image.ImageLoader
import com.sudhir.safecopy.core.log.SafeLog
import com.sudhir.safecopy.domain.detect.DetectionOptions
import com.sudhir.safecopy.domain.detect.DetectionResult
import com.sudhir.safecopy.domain.detect.FieldDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Detection on an upright bitmap. [bitmap] is the input itself when [rotationCw] is 0. */
data class OrientedDetection(val bitmap: Bitmap, val rotationCw: Int, val result: DetectionResult)

/**
 * Runs OCR + QR and the detectors (spec 6.1), off the main thread and cancellable.
 *
 * Orientation search: if the image as given has no checksum-valid Aadhaar/VID/PAN, the card is
 * probably sideways or upside down (WhatsApp and screenshots carry no EXIF). Each other
 * quarter turn is then scored on a small copy (fast), and the best one, if better, is run at
 * full resolution. The returned bitmap is the turned one, so the editor shows it upright.
 */
class DetectionService(
    private val ocr: OcrEngine = OcrEngine(),
    private val qr: QrEngine = QrEngine(),
) {
    suspend fun detect(bitmap: Bitmap, options: DetectionOptions = DetectionOptions()): OrientedDetection =
        withContext(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            val upright = run(bitmap, options)
            if (upright.hasStrongMatch) {
                SafeLog.d("detection done in ${System.currentTimeMillis() - started} ms")
                return@withContext OrientedDetection(bitmap, 0, upright)
            }

            val probe = downscale(bitmap, PROBE_EDGE)
            val best = listOf(90, 180, 270)
                .map { turns -> turns to run(rotate(probe, turns), options, qrToo = false) }
                .maxBy { it.second.score }
            if (probe !== bitmap) probe.recycle()

            var chosen = OrientedDetection(bitmap, 0, upright)
            if (best.second.score > upright.score) {
                val turned = rotate(bitmap, best.first)
                val full = run(turned, options)
                if (full.score > upright.score) chosen = OrientedDetection(turned, best.first, full)
                else turned.recycle()
            }
            SafeLog.d("detection done in ${System.currentTimeMillis() - started} ms, turned ${chosen.rotationCw}")
            chosen
        }

    private suspend fun run(bitmap: Bitmap, options: DetectionOptions, qrToo: Boolean = true): DetectionResult =
        coroutineScope {
            val text = async { ocr.recognize(bitmap) }
            val codes = if (qrToo) async { qr.findQrBoxes(bitmap) } else null
            FieldDetector.detect(text.await(), codes?.await().orEmpty(), bitmap.width, bitmap.height, options)
        }

    private fun rotate(src: Bitmap, degreesCw: Int): Bitmap {
        var b = src
        repeat(degreesCw / 90) { b = ImageLoader.rotateClockwise(b).also { if (b !== src) b.recycle() } }
        return b
    }

    private fun downscale(src: Bitmap, edge: Int): Bitmap {
        val s = edge.toFloat() / max(src.width, src.height)
        if (s >= 1f) return src
        return src.scale((src.width * s).toInt(), (src.height * s).toInt())
    }

    private companion object {
        /** Long edge for scoring other orientations: enough for ~40 px digits on a card photo. */
        const val PROBE_EDGE = 1600
    }
}
