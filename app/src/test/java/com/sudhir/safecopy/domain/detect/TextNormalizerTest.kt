package com.sudhir.safecopy.domain.detect

import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.domain.detect.OcrFixtures.line
import com.sudhir.safecopy.domain.model.Rect
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun digitContextRules() {
        assertThat(TextNormalizer.looksNumeric("2O12")).isTrue()
        assertThat(TextNormalizer.looksNumeric("13/1l/1990")).isTrue()
        assertThat(TextNormalizer.looksNumeric("4567")).isTrue()
        assertThat(TextNormalizer.looksNumeric("DOB")).isFalse()
        assertThat(TextNormalizer.looksNumeric("SOLO")).isFalse()
        assertThat(TextNormalizer.looksNumeric("B12O")).isTrue()
        assertThat(TextNormalizer.looksNumeric("BOS1")).isFalse() // only 1 of 4 is a real digit
        assertThat(TextNormalizer.looksNumeric("India")).isFalse()
        assertThat(TextNormalizer.looksNumeric("--")).isFalse()
    }

    @Test
    fun confusionTableFromSpec() {
        assertThat(TextNormalizer.toDigits("OoDQ Il|i Zz Ss B G")).isEqualTo("0000 1111 22 55 8 6")
    }

    @Test
    fun onlyNumericElementsAreNormalisedAndLengthIsKept() {
        val n = NormalizedLine(line("DOB: 2O/1l/199O"))
        assertThat(n.text).isEqualTo("DOB: 2O/1l/199O")
        assertThat(n.digits).isEqualTo("DOB: 20/11/1990")
        assertThat(n.digits.length).isEqualTo(n.text.length)
    }

    @Test
    fun rectOfInterpolatesWithinElementsAndSkipsSpaces() {
        val n = NormalizedLine(line("ab 1234"))
        // "1234" starts at x = 30 (3 chars incl. space), 10 px per char.
        assertThat(n.rectOf(3, 5)).isEqualTo(Rect(30f, 0f, 50f, 20f))
        assertThat(n.rectOf(2, 3)).isNull() // just the space
        assertThat(n.rectOf(0, 7)).isEqualTo(Rect(0f, 0f, 70f, 20f))
        assertThat(n.elementStarts.toList()).containsExactly(0, 3).inOrder()
    }

    @Test
    fun symbolBoxesArePreferredWhenComplete() {
        val symbols = listOf(
            OcrSymbol("4", Rect(100f, 5f, 108f, 25f)),
            OcrSymbol("2", Rect(111f, 4f, 120f, 26f)),
        )
        val el = OcrElement("42", Rect(100f, 4f, 120f, 26f), symbols)
        val n = NormalizedLine(OcrLine("42", el.rect, listOf(el)))
        assertThat(n.rectOf(1, 2)).isEqualTo(Rect(111f, 4f, 120f, 26f))
    }
}
