package com.sudhir.safecopy.ml

import android.graphics.Bitmap
import com.sudhir.safecopy.core.image.ImageLoader
import com.sudhir.safecopy.core.log.SafeLog
import com.sudhir.safecopy.domain.detect.DetectionOptions
import com.sudhir.safecopy.domain.detect.DetectionResult
import com.sudhir.safecopy.domain.detect.FieldDetector
import com.sudhir.safecopy.domain.detect.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Detection on an upright bitmap. [bitmap] is the input itself when [rotationCw] is 0. */
data class OrientedDetection(val bitmap: Bitmap, val rotationCw: Int, val result: DetectionResult)

/**
 * Runs OCR + QR and the detectors (spec 6.1), off the main thread and cancellable.
 *
 * Orientation: ML Kit reads sideways and upside-down text and reports each line's angle.
 * When most of the text is at 90°, 180° or 270° (WhatsApp images and screenshots carry no
 * EXIF), the bitmap is turned upright and detection runs again, so the editor shows the
 * card the right way up and the boxes match it.
 */
class DetectionService(
    private val ocr: OcrEngine = OcrEngine(),
    private val qr: QrEngine = QrEngine(),
) {
    suspend fun detect(bitmap: Bitmap, options: DetectionOptions = DetectionOptions()): OrientedDetection =
        withContext(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            val (text, first) = run(bitmap, options)
            val turn = text.uprightTurn()
            val result = if (turn == 0) {
                OrientedDetection(bitmap, 0, first)
            } else {
                val upright = rotate(bitmap, turn)
                OrientedDetection(upright, turn, run(upright, options).second)
            }
            SafeLog.d("detection done in ${System.currentTimeMillis() - started} ms, turned $turn")
            result
        }

    private suspend fun run(bitmap: Bitmap, options: DetectionOptions): Pair<OcrResult, DetectionResult> =
        coroutineScope {
            val text = async { ocr.recognize(bitmap) }
            val codes = async { qr.findQrBoxes(bitmap) }
            val t = text.await()
            t to FieldDetector.detect(t, codes.await(), bitmap.width, bitmap.height, options)
        }

    private fun rotate(src: Bitmap, degreesCw: Int): Bitmap {
        var b = src
        repeat(degreesCw / 90) { b = ImageLoader.rotateClockwise(b).also { if (b !== src) b.recycle() } }
        return b
    }
}
