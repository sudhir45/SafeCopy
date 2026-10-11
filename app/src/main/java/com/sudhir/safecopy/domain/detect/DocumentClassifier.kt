package com.sudhir.safecopy.domain.detect

/** Keyword scoring (spec 6.3): Aadhaar vs PAN vs unknown. */
object DocumentClassifier {
    private val AADHAAR_KEYWORDS = mapOf(
        "aadhaar" to 2, "आधार" to 2, "unique identification authority" to 2, "uidai" to 2,
        "my aadhaar my identity" to 2, "government of india" to 1, "enrolment no" to 1, "vid" to 1,
    )
    private val PAN_KEYWORDS = mapOf(
        "income tax department" to 2, "permanent account number" to 2, "govt of india" to 1, "pan" to 1,
    )
    private const val STRONG_SIGNAL = 3
    private const val THRESHOLD = 2

    /**
     * [validAadhaar]/[validPan]: whether a checksum-valid Aadhaar number or a strictly-formatted
     * PAN was found, which count as strong evidence (spec 6.3).
     */
    fun classify(lines: List<NormalizedLine>, validAadhaar: Boolean, validPan: Boolean): DocType {
        val text = " " + lines.joinToString(" ") { it.text }
            .lowercase()
            .replace(Regex("[.,:]"), " ")
            .replace(Regex("\\s+"), " ") + " "
        val aadhaar = score(text, AADHAAR_KEYWORDS) + if (validAadhaar) STRONG_SIGNAL else 0
        val pan = score(text, PAN_KEYWORDS) + if (validPan) STRONG_SIGNAL else 0
        return when {
            aadhaar >= THRESHOLD && aadhaar > pan -> DocType.AADHAAR
            pan >= THRESHOLD && pan > aadhaar -> DocType.PAN
            else -> DocType.UNKNOWN
        }
    }

    /** Whole-word matches only, so "pan" doesn't fire inside "company". */
    private fun score(text: String, keywords: Map<String, Int>): Int =
        keywords.entries.sumOf { (k, w) -> if (Regex("(?<![\\p{L}\\p{N}])${Regex.escape(k)}(?![\\p{L}\\p{N}])").containsMatchIn(text)) w else 0 }
}
