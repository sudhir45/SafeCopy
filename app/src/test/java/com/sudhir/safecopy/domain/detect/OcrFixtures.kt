package com.sudhir.safecopy.domain.detect

import com.sudhir.safecopy.domain.model.Rect
import kotlin.random.Random

/**
 * Hand-built OCR input for detector tests. Every character is [CHAR_W] px wide and lines are
 * [LINE_H] px tall, so expected boxes are easy to compute. All numbers are synthetic.
 */
object OcrFixtures {
    const val CHAR_W = 10f
    const val LINE_H = 20f

    /** One line at row [row]; elements are the space-separated words, laid out left to right. */
    fun line(text: String, row: Int = 0, x0: Float = 0f): OcrLine {
        val top = row * LINE_H * 2
        var x = x0
        val elements = text.split(" ").filter { it.isNotEmpty() }.map { word ->
            val el = OcrElement(word, Rect(x, top, x + word.length * CHAR_W, top + LINE_H))
            x += (word.length + 1) * CHAR_W
            el
        }
        val rect = elements.map { it.rect }.reduce { a, b -> a.union(b) }
        return OcrLine(elements.joinToString(" ") { it.text }, rect, elements)
    }

    fun lines(vararg texts: String): List<NormalizedLine> =
        texts.mapIndexed { i, t -> NormalizedLine(line(t, i)) }

    fun result(vararg texts: String) = OcrResult(texts.mapIndexed { i, t -> line(t, i) })

    /** A fake but checksum-valid 12-digit number, grouped as "XXXX XXXX XXXX". */
    fun fakeAadhaar(seed: Int = 1): String {
        val rnd = Random(seed)
        val body = (rnd.nextInt(2, 10)).toString() + (1..10).joinToString("") { rnd.nextInt(10).toString() }
        return group(body + Verhoeff.checkDigit(body))
    }

    /** Same, but with the check digit deliberately wrong. */
    fun fakeInvalidAadhaar(seed: Int = 1): String {
        val valid = fakeAadhaar(seed).replace(" ", "")
        val bad = valid.dropLast(1) + ((valid.last() - '0' + 1) % 10)
        return group(bad)
    }

    fun group(digits: String) = digits.chunked(4).joinToString(" ")
}
