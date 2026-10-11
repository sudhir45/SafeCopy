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

/** [angle]: the line's rotation in degrees as reported by OCR (0 upright, 90, 180, -90 ...). */
data class OcrLine(val text: String, val rect: Rect, val elements: List<OcrElement>, val angle: Float = 0f)

/** Lines in reading order (ML Kit blocks flattened). */
data class OcrResult(val lines: List<OcrLine>) {
    /**
     * Clockwise quarter turn (0, 90, 180 or 270) that makes the text upright. Each line votes
     * for its nearest quarter turn, weighted by its length; a turn is only suggested when at
     * least 60 % of the text agrees, so a few odd lines or a tilted photo never trigger it.
     */
    fun uprightTurn(): Int {
        val votes = IntArray(4)
        for (l in lines) {
            val q = Math.floorMod(Math.round(l.angle / 90f), 4)
            votes[q] += l.text.count { it.isLetterOrDigit() }
        }
        val total = votes.sum()
        val best = votes.indices.maxBy { votes[it] }
        if (total == 0 || best == 0 || votes[best] * 10 < total * 6) return 0
        return (4 - best) % 4 * 90
    }
}
