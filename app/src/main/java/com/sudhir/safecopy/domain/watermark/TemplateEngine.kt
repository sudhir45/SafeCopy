package com.sudhir.safecopy.domain.watermark

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fills watermark templates (spec 8.2).
 *
 * A template is a list of segments joined by " · ". A segment that uses a token whose value is
 * empty is dropped whole, so "ONLY FOR {purpose} · {recipient}" with no purpose becomes just
 * the recipient, never "ONLY FOR · ...". The result is at most [MAX_LENGTH] characters: the
 * longer of recipient and purpose is shortened with an ellipsis, then the whole string.
 */
object TemplateEngine {
    const val MAX_LENGTH = 80
    const val SEPARATOR = " · "
    private const val ELLIPSIS = "…"
    private val TOKEN = Regex("\\{(recipient|purpose|date|ref)\\}")

    fun formatDate(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern("dd MMM yyyy", locale).format(date)

    fun render(
        pattern: String,
        recipient: String,
        purpose: String,
        date: String,
        ref: String = "",
        uppercase: Boolean = true,
    ): String {
        var r = clean(recipient)
        var p = clean(purpose)
        val d = clean(date)
        val f = clean(ref)

        var text = fill(pattern, r, p, d, f, uppercase)
        // Shorten whichever free-text field is longer, so a long purpose never eats the
        // recipient (the part that makes a leaked copy traceable).
        // The longer one is cut down towards the shorter one first; once they're level, both
        // give up half each.
        repeat(8) {
            val overflow = text.length - MAX_LENGTH
            if (overflow <= 0) return text
            val diff = r.length - p.length
            when {
                diff > 0 -> r = shorten(r, minOf(overflow, diff))
                diff < 0 -> p = shorten(p, minOf(overflow, -diff))
                else -> {
                    r = shorten(r, (overflow + 1) / 2)
                    p = shorten(p, (overflow + 1) / 2)
                }
            }
            text = fill(pattern, r, p, d, f, uppercase)
        }
        if (text.length > MAX_LENGTH) text = text.take(MAX_LENGTH - 1).trimEnd() + ELLIPSIS
        return text
    }

    private fun fill(pattern: String, r: String, p: String, d: String, f: String, uppercase: Boolean): String {
        val values = mapOf("recipient" to r, "purpose" to p, "date" to d, "ref" to f)
        val text = pattern.split(SEPARATOR)
            .filter { segment -> TOKEN.findAll(segment).all { values.getValue(it.groupValues[1]).isNotEmpty() } }
            .map { segment -> TOKEN.replace(segment) { values.getValue(it.groupValues[1]) }.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(SEPARATOR)
        // Locale.ROOT so e.g. a Turkish device doesn't turn "i" into "İ".
        return if (uppercase) text.uppercase(Locale.ROOT) else text
    }

    /** Collapses whitespace runs (incl. newlines) to single spaces. */
    private fun clean(s: String) = s.trim().replace(Regex("\\s+"), " ")

    /** Removes [overflow] characters (plus room for the ellipsis); never shorter than 1 char + "…". */
    private fun shorten(s: String, overflow: Int): String {
        if (s.isEmpty()) return s
        val keep = (s.length - overflow - ELLIPSIS.length).coerceAtLeast(1)
        return if (keep >= s.length) s else s.take(keep).trimEnd() + ELLIPSIS
    }
}
