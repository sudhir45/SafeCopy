package com.sudhir.safecopy.domain.detect

import com.sudhir.safecopy.domain.model.Confidence
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.Rect

/** A raw match before ids and defaults are assigned. */
data class Match(val type: FieldType, val rect: Rect, val confidence: Confidence)

/** Spec 6.4: 12-digit Aadhaar numbers, validated with Verhoeff. */
object AadhaarDetector {
    /** Never starts with 0/1; not part of a longer digit run (that would be a VID or similar). */
    private val PATTERN = Regex("(?<!\\d\\s?)[2-9]\\d{3}\\s?\\d{4}\\s?\\d{4}(?!\\s?\\d)")
    private val KEYWORD = Regex("aadhaar|आधार", RegexOption.IGNORE_CASE)

    data class Candidate(val line: NormalizedLine, val digitIndices: List<Int>, val valid: Boolean)

    fun candidates(lines: List<NormalizedLine>): List<Candidate> = lines.flatMap { line ->
        PATTERN.findAll(line.digits).map { m ->
            val idx = m.range.filter { line.digits[it].isDigit() }
            Candidate(line, idx, Verhoeff.isValid(idx.map { line.digits[it] }.joinToString("")))
        }.toList()
    }

    fun detect(lines: List<NormalizedLine>, docType: DocType, mode: AadhaarMaskMode): List<Match> {
        val all = candidates(lines)
        val anyValid = all.any { it.valid }
        return all.mapNotNull { c ->
            val confidence = when {
                c.valid -> Confidence.HIGH
                // Checksum failed: maybe an OCR misread. Suggest it only with Aadhaar context.
                KEYWORD.containsMatchIn(c.line.text) || (docType == DocType.AADHAAR && !anyValid) -> Confidence.LOW
                else -> return@mapNotNull null
            }
            val last = if (mode == AadhaarMaskMode.ALL_12) 11 else 7
            val rect = c.line.rectOf(c.digitIndices.first(), c.digitIndices[last] + 1) ?: return@mapNotNull null
            Match(FieldType.AADHAAR_NUMBER, rect, confidence)
        }
    }
}

/** Spec 6.6: 16-digit Virtual ID, only with "VID" on the same or previous line. */
object VidDetector {
    private val PATTERN = Regex("(?<!\\d\\s?)\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}(?!\\s?\\d)")
    private val KEYWORD = Regex("(?<![A-Za-z])VID(?![A-Za-z])", RegexOption.IGNORE_CASE)

    fun detect(lines: List<NormalizedLine>): List<Match> = lines.flatMapIndexed { i, line ->
        val labelled = KEYWORD.containsMatchIn(line.text) || (i > 0 && KEYWORD.containsMatchIn(lines[i - 1].text))
        if (!labelled) return@flatMapIndexed emptyList()
        PATTERN.findAll(line.digits).mapNotNull { m ->
            line.rectOf(m.range.first, m.range.last + 1)?.let { Match(FieldType.AADHAAR_VID, it, Confidence.HIGH) }
        }.toList()
    }
}

/** Spec 6.7: PAN = 5 letters, 4 digits, 1 letter; 4th letter is the holder type. */
object PanDetector {
    private val STRICT = Regex("[A-Z]{3}[ABCFGHLJPT][A-Z]\\d{4}[A-Z]")
    private val LENIENT = Regex("[A-Z0-9]{5}\\d{4}[A-Z0-9]")
    private val TOKEN = Regex("(?<![A-Za-z0-9])[A-Za-z0-9|]{10}(?![A-Za-z0-9])")

    /** Fixes OCR look-alikes by position: letters at 1-5 and 10, digits at 6-9. */
    fun repair(token: String): String = String(
        CharArray(10) { i -> if (i in 5..8) TextNormalizer.toDigit(token[i]) else TextNormalizer.toLetter(token[i]) },
    ).uppercase()

    fun hasStrictPan(lines: List<NormalizedLine>): Boolean = lines.any { line ->
        TOKEN.findAll(line.text).any { STRICT.matches(it.value.uppercase()) }
    }

    fun detect(lines: List<NormalizedLine>, docType: DocType, mode: PanMaskMode): List<Match> = lines.flatMap { line ->
        TOKEN.findAll(line.text).mapNotNull { m ->
            val raw = m.value.uppercase()
            val fixed = repair(m.value)
            val confidence = when {
                STRICT.matches(raw) -> Confidence.HIGH
                STRICT.matches(fixed) -> if (docType == DocType.PAN) Confidence.HIGH else Confidence.LOW
                docType == DocType.PAN && LENIENT.matches(TextNormalizer.toDigits(raw).let { raw.take(5) + it.substring(5, 9) + raw.last() }) ->
                    Confidence.LOW
                else -> return@mapNotNull null
            }
            val end = if (mode == PanMaskMode.ALL) 10 else 6
            line.rectOf(m.range.first, m.range.first + end)?.let { Match(FieldType.PAN, it, confidence) }
        }.toList()
    }
}

/** Spec 6.8: dates of birth, only next to a DOB label (or the first date on a PAN card). */
object DobDetector {
    private val DATE = Regex("(?<!\\d)\\d{2}[/\\-.]\\d{2}[/\\-.]\\d{4}(?!\\d)")
    private val YEAR_OF_BIRTH = Regex("(?:Year of Birth|YOB)\\s*:?\\s*(\\d{4})(?!\\d)", RegexOption.IGNORE_CASE)
    private val LABEL = Regex("DOB|Date of Birth|Birth|जन्म", RegexOption.IGNORE_CASE)

    fun detect(lines: List<NormalizedLine>, docType: DocType): List<Match> {
        val out = ArrayList<Match>()
        var firstDateOnPan = docType == DocType.PAN
        lines.forEachIndexed { i, line ->
            val labelled = LABEL.containsMatchIn(line.text) || (i > 0 && LABEL.containsMatchIn(lines[i - 1].text))
            for (m in DATE.findAll(line.digits)) {
                if (labelled || firstDateOnPan) {
                    line.rectOf(m.range.first, m.range.last + 1)?.let { out += Match(FieldType.DOB, it, Confidence.HIGH) }
                }
                firstDateOnPan = false
            }
            for (m in YEAR_OF_BIRTH.findAll(line.digits)) {
                val g = m.groups[1]!!.range
                line.rectOf(g.first, g.last + 1)?.let { out += Match(FieldType.DOB, it, Confidence.HIGH) }
            }
        }
        return out
    }
}

/** Spec 6.9: the address block, from its label down to the line with the PIN code. */
object AddressDetector {
    // No word boundary (\b): Java treats Devanagari as non-word characters, so "पता" would never match.
    private val START = Regex("^\\s*(Address|पता|[SDWC]\\s?/\\s?O)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val PIN = Regex("(?<!\\d)[1-9]\\d{5}(?!\\d)")
    private const val MAX_LINES = 7
    private const val LINES_WITHOUT_PIN = 4

    fun detect(lines: List<NormalizedLine>): List<Match> {
        val out = ArrayList<Match>()
        var i = 0
        while (i < lines.size) {
            if (!START.containsMatchIn(lines[i].text)) {
                i++
                continue
            }
            val end = (i until minOf(lines.size, i + MAX_LINES)).firstOrNull { PIN.containsMatchIn(lines[it].digits) }
            val (last, confidence) = if (end != null) end to Confidence.HIGH
            else minOf(lines.size - 1, i + LINES_WITHOUT_PIN - 1) to Confidence.LOW
            val rect = (i..last).map { lines[it].line.rect }.reduce { a, b -> a.union(b) }
            out += Match(FieldType.ADDRESS, rect, confidence)
            i = last + 1
        }
        return out
    }
}
