package com.sudhir.safecopy.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sudhir.safecopy.domain.detect.OcrElement
import com.sudhir.safecopy.domain.detect.OcrLine
import com.sudhir.safecopy.domain.detect.OcrResult
import com.sudhir.safecopy.domain.detect.OcrSymbol
import com.sudhir.safecopy.domain.model.Rect

/**
 * Thin wrapper over ML Kit text recognition (bundled Latin model, spec 6.1, 13).
 * Runs on the bitmap exactly as shown (rotation 0), so boxes come back in bitmap pixels.
 */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(
                    text = line.text,
                    rect = box.toRect(),
                    elements = line.elements.mapNotNull { el ->
                        val elBox = el.boundingBox ?: return@mapNotNull null
                        OcrElement(
                            text = el.text,
                            rect = elBox.toRect(),
                            symbols = el.symbols.mapNotNull { s -> s.boundingBox?.let { OcrSymbol(s.text, it.toRect()) } },
                        )
                    },
                )
            }
        }
        return OcrResult(lines)
    }
}

internal fun android.graphics.Rect.toRect() = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
