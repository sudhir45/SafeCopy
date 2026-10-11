package com.sudhir.safecopy.domain.detect

import com.sudhir.safecopy.domain.model.Rect

/**
 * Plain copy of an OCR result (ML Kit's Text → lines → elements → symbols), so detectors are
 * pure Kotlin and unit-testable with hand-built input. All rects are bitmap pixels.
 *
 * This holds document text: never log it (spec 2.7).
 */
data class OcrSymbol(val text: String, val rect: Rect)

data class OcrElement(val text: String, val rect: Rect, val symbols: List<OcrSymbol> = emptyList())

data class OcrLine(val text: String, val rect: Rect, val elements: List<OcrElement>)

/** Lines in reading order (ML Kit blocks flattened). */
data class OcrResult(val lines: List<OcrLine>) {
    val alnumCount: Int get() = lines.sumOf { l -> l.text.count { it.isLetterOrDigit() } }
}
