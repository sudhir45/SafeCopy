package com.sudhir.safecopy.core.log

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeLogTest {
    @Test
    fun redactsAsciiDigits() {
        assertThat(SafeLog.redact("number 2363 4567 8901")).isEqualTo("number #### #### ####")
    }

    @Test
    fun redactsDevanagariDigits() {
        assertThat(SafeLog.redact("२०२६")).isEqualTo("####")
    }

    @Test
    fun keepsLettersAndPunctuation() {
        assertThat(SafeLog.redact("Editor opened: OK!")).isEqualTo("Editor opened: OK!")
    }

    @Test
    fun panLikeStringKeepsOnlyLetters() {
        assertThat(SafeLog.redact("ABCPE1234F")).isEqualTo("ABCPE####F")
    }
}
