package com.sudhir.safecopy.domain.detect

import com.sudhir.safecopy.domain.model.Rect

/**
 * One OCR line prepared for pattern matching (spec 6.2).
 *
 * - [text]: the line's elements joined by single spaces.
 * - [digits]: the same, but in elements that look numeric, OCR look-alikes become digits
 *   ("2O12" → "2012"). Same length as [text], so an index means the same character in both.
 * - [rectOf] maps a character range back to a box in the bitmap. Symbol boxes are used when
 *   OCR provides them; otherwise the element box is split evenly per character.
 */
class NormalizedLine(val line: OcrLine) {
    val text: String
    val digits: String

    /** Start index of each element in [text]. */
    val elementStarts: IntArray
    private val charRects: List<Rect?>

    init {
        val t = StringBuilder()
        val d = StringBuilder()
        val rects = ArrayList<Rect?>()
        val starts = IntArray(line.elements.size)
        line.elements.forEachIndexed { i, el ->
            if (i > 0) {
                t.append(' ')
                d.append(' ')
                rects.add(null)
            }
            starts[i] = t.length
            t.append(el.text)
            d.append(if (TextNormalizer.looksNumeric(el.text)) TextNormalizer.toDigits(el.text) else el.text)
            rects.addAll(TextNormalizer.charRects(el))
        }
        text = t.toString()
        digits = d.toString()
        elementStarts = starts
        charRects = rects
    }

    /** Box covering characters [start, endExclusive), or null if they're all spaces. */
    fun rectOf(start: Int, endExclusive: Int): Rect? =
        charRects.subList(start.coerceAtLeast(0), endExclusive.coerceAtMost(charRects.size))
            .filterNotNull()
            .reduceOrNull { a, b -> a.union(b) }
}

object TextNormalizer {
    /** Spec 6.2 confusion table, applied only in a digit context. */
    private val TO_DIGIT = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', '|' to '1', 'i' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'B' to '8',
        'G' to '6',
    )

    /** Reverse direction, for the letter positions of a PAN. */
    private val TO_LETTER = mapOf('0' to 'O', '1' to 'I', '2' to 'Z', '5' to 'S', '8' to 'B', '6' to 'G')

    fun toDigit(c: Char): Char = TO_DIGIT[c] ?: c
    fun toLetter(c: Char): Char = TO_LETTER[c] ?: c

    fun toDigits(s: String): String = String(CharArray(s.length) { toDigit(s[it]) })

    /**
     * An element is in a "digit context" if every letter in it is an OCR look-alike of a digit
     * and at least half of its letters-and-digits are real digits. "2O12" and "13/1l/1990"
     * qualify; "DOB", "SOLO" and "B12" don't.
     */
    fun looksNumeric(s: String): Boolean {
        var digits = 0
        var alnum = 0
        for (c in s) {
            when {
                c in '0'..'9' -> { digits++; alnum++ }
                c in TO_DIGIT -> alnum++
                c.isLetter() -> return false
            }
        }
        return alnum > 0 && digits * 2 >= alnum
    }

    fun charRects(el: OcrElement): List<Rect> {
        val n = el.text.length
        if (n == 0) return emptyList()
        if (el.symbols.size == n) return el.symbols.map { it.rect }
        val r = el.rect
        return List(n) { i ->
            Rect(r.left + r.width * i / n, r.top, r.left + r.width * (i + 1) / n, r.bottom)
        }
    }

    fun normalize(result: OcrResult): List<NormalizedLine> = result.lines.map { NormalizedLine(it) }
}
