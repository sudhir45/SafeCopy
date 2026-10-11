package com.sudhir.safecopy.domain.detect

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

/** Spec 6.5 required tests. */
class VerhoeffTest {
    @Test
    fun knownValues() {
        assertThat(Verhoeff.isValid("2363")).isTrue()
        assertThat(Verhoeff.isValid("2364")).isFalse()
        assertThat(Verhoeff.checkDigit("236")).isEqualTo(3)
    }

    @Test
    fun rejectsNonDigits() {
        assertThat(Verhoeff.isValid("")).isFalse()
        assertThat(Verhoeff.isValid("23a3")).isFalse()
    }

    @Test
    fun roundTripOnThousandRandomNumbers() {
        val rnd = Random(2026)
        repeat(1000) {
            val body = (1..11).joinToString("") { rnd.nextInt(10).toString() }
            assertThat(Verhoeff.isValid(body + Verhoeff.checkDigit(body))).isTrue()
        }
    }

    @Test
    fun everySingleDigitSubstitutionIsCaught() {
        val rnd = Random(9)
        repeat(50) {
            val body = (1..11).joinToString("") { rnd.nextInt(10).toString() }
            val valid = body + Verhoeff.checkDigit(body)
            for (pos in valid.indices) for (d in '0'..'9') {
                if (d == valid[pos]) continue
                val changed = valid.substring(0, pos) + d + valid.substring(pos + 1)
                assertThat(Verhoeff.isValid(changed)).isFalse()
            }
        }
    }
}
