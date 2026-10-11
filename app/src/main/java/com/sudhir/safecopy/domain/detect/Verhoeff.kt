package com.sudhir.safecopy.domain.detect

/** Verhoeff checksum, used for the last digit of an Aadhaar number (spec 6.5). */
object Verhoeff {
    private val D = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 9, 8, 7, 6, 0, 4, 3, 2, 1),
        intArrayOf(6, 5, 9, 8, 7, 1, 0, 4, 3, 2),
        intArrayOf(7, 6, 5, 9, 8, 2, 1, 0, 4, 3),
        intArrayOf(8, 7, 6, 5, 9, 3, 2, 1, 0, 4),
        intArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0),
    )
    private val P = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8),
    )
    private val INV = intArrayOf(0, 4, 3, 2, 1, 5, 6, 7, 8, 9)

    /** True if [num] (digits only) ends in a correct Verhoeff check digit. */
    fun isValid(num: String): Boolean {
        if (num.isEmpty() || !num.all { it in '0'..'9' }) return false
        var c = 0
        num.reversed().forEachIndexed { i, ch -> c = D[c][P[i % 8][ch - '0']] }
        return c == 0
    }

    /** Check digit to append to [numWithoutCheck] (digits only). */
    fun checkDigit(numWithoutCheck: String): Int {
        require(numWithoutCheck.all { it in '0'..'9' })
        var c = 0
        numWithoutCheck.reversed().forEachIndexed { i, ch -> c = D[c][P[(i + 1) % 8][ch - '0']] }
        return INV[c]
    }
}
