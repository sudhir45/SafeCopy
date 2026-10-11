package com.sudhir.safecopy.domain.detect

import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.domain.detect.OcrFixtures.CHAR_W
import com.sudhir.safecopy.domain.detect.OcrFixtures.fakeAadhaar
import com.sudhir.safecopy.domain.detect.OcrFixtures.fakeInvalidAadhaar
import com.sudhir.safecopy.domain.detect.OcrFixtures.lines
import com.sudhir.safecopy.domain.model.Confidence
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.Rect
import org.junit.Test

class AadhaarDetectorTest {
    private val num = fakeAadhaar(1)

    private fun detect(vararg text: String, doc: DocType = DocType.UNKNOWN, mode: AadhaarMaskMode = AadhaarMaskMode.FIRST_8) =
        AadhaarDetector.detect(lines(*text), doc, mode)

    @Test
    fun threeElementNumberMasksFirstEightDigits() {
        val m = detect(num).single()
        assertThat(m.confidence).isEqualTo(Confidence.HIGH)
        // "XXXX XXXX" = chars 0..8 → 9 chars wide; the last group is left visible.
        assertThat(m.rect).isEqualTo(Rect(0f, 0f, 9 * CHAR_W, 20f))
    }

    @Test
    fun allTwelveMode() {
        val m = detect(num, mode = AadhaarMaskMode.ALL_12).single()
        assertThat(m.rect.right).isEqualTo(14 * CHAR_W)
    }

    @Test
    fun singleElementNumber() {
        val m = detect("No: " + num.replace(" ", "")).single()
        // Element starts after "No: " (4 chars); first 8 digits are 8 chars wide.
        assertThat(m.rect).isEqualTo(Rect(4 * CHAR_W, 0f, 12 * CHAR_W, 20f))
    }

    @Test
    fun ocrLookAlikesAreRepaired() {
        val noisy = num.replace('0', 'O').replace('1', 'l')
        assertThat(detect(noisy)).hasSize(1)
    }

    @Test
    fun invalidChecksumWithoutContextIsIgnored() {
        assertThat(detect(fakeInvalidAadhaar(1))).isEmpty()
    }

    @Test
    fun invalidChecksumNextToKeywordIsLowConfidence() {
        assertThat(detect("Aadhaar " + fakeInvalidAadhaar(1)).single().confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun invalidChecksumOnAadhaarCardWithNoValidNumberIsLow() {
        assertThat(detect(fakeInvalidAadhaar(1), doc = DocType.AADHAAR).single().confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun invalidIgnoredWhenAValidOneExists() {
        val found = detect(num, fakeInvalidAadhaar(2), doc = DocType.AADHAAR)
        assertThat(found).hasSize(1)
        assertThat(found.single().confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun everyOccurrenceIsFound() {
        assertThat(detect(num, "Your Aadhaar No. : $num", num)).hasSize(3)
    }

    @Test
    fun numbersStartingWithZeroOrOneAreNotAadhaar() {
        val body = "1" + "2345678901"
        assertThat(detect(OcrFixtures.group(body + Verhoeff.checkDigit(body)))).isEmpty()
    }

    @Test
    fun sixteenDigitVidIsNotMistakenForAadhaar() {
        assertThat(detect("VID : 9186 0913 9099 6030")).isEmpty()
    }
}

class VidDetectorTest {
    @Test
    fun vidOnSameLine() {
        val m = VidDetector.detect(lines("VID : 9186 0913 9099 6030")).single()
        assertThat(m.type).isEqualTo(FieldType.AADHAAR_VID)
        assertThat(m.rect).isEqualTo(Rect(6 * CHAR_W, 0f, 25 * CHAR_W, 20f))
    }

    @Test
    fun vidLabelOnPreviousLine() {
        assertThat(VidDetector.detect(lines("VID", "9186 0913 9099 6030"))).hasSize(1)
    }

    @Test
    fun sixteenDigitsWithoutLabelAreIgnored() {
        assertThat(VidDetector.detect(lines("Card 9186 0913 9099 6030"))).isEmpty()
        assertThat(VidDetector.detect(lines("VIDEO 9186 0913 9099 6030"))).isEmpty()
    }
}

class PanDetectorTest {
    private fun detect(vararg text: String, doc: DocType = DocType.UNKNOWN, mode: PanMaskMode = PanMaskMode.FIRST_6) =
        PanDetector.detect(lines(*text), doc, mode)

    @Test
    fun strictPanMasksFirstSix() {
        val m = detect("ABCPK1234F").single()
        assertThat(m.confidence).isEqualTo(Confidence.HIGH)
        assertThat(m.rect).isEqualTo(Rect(0f, 0f, 6 * CHAR_W, 20f))
    }

    @Test
    fun allMode() {
        assertThat(detect("ABCPK1234F", mode = PanMaskMode.ALL).single().rect.right).isEqualTo(10 * CHAR_W)
    }

    @Test
    fun invalidHolderTypeIsNotStrict() {
        // 4th letter must be one of ABCFGHLJPT; "X" isn't.
        assertThat(detect("ABCXK1234F")).isEmpty()
    }

    @Test
    fun ocrRepairIsHighOnPanCardAndLowElsewhere() {
        assertThat(detect("ABCPK12O4F", doc = DocType.PAN).single().confidence).isEqualTo(Confidence.HIGH)
        assertThat(detect("ABCPK12O4F").single().confidence).isEqualTo(Confidence.LOW)
        assertThat(detect("A8CPK1234F", doc = DocType.PAN).single().confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun lenientOnlyOnPanCards() {
        assertThat(detect("AB1XK1234F", doc = DocType.PAN).single().confidence).isEqualTo(Confidence.LOW)
        assertThat(detect("AB1XK1234F")).isEmpty()
    }

    @Test
    fun ordinaryWordsAreIgnored() {
        assertThat(detect("GOVERNMENT DEPARTMENT", "SIGNATURES", doc = DocType.PAN)).isEmpty()
    }

    @Test
    fun lowercaseAndTrailingPunctuation() {
        assertThat(detect("PAN: abcpk1234f.")).hasSize(1)
    }

    @Test
    fun strictPanHelper() {
        assertThat(PanDetector.hasStrictPan(lines("x", "ABCPK1234F"))).isTrue()
        assertThat(PanDetector.hasStrictPan(lines("ABCPK12O4F"))).isFalse()
    }
}

class DobDetectorTest {
    @Test
    fun labelledDateOnSameLine() {
        val m = DobDetector.detect(lines("DOB: 13/11/1990"), DocType.AADHAAR).single()
        assertThat(m.rect).isEqualTo(Rect(5 * CHAR_W, 0f, 15 * CHAR_W, 20f))
    }

    @Test
    fun labelOnPreviousLine() {
        assertThat(DobDetector.detect(lines("Date of Birth", "13-11-1990"), DocType.UNKNOWN)).hasSize(1)
    }

    @Test
    fun unlabelledDateIsIgnoredOffPan() {
        assertThat(DobDetector.detect(lines("Issued 13/11/2020"), DocType.AADHAAR)).isEmpty()
    }

    @Test
    fun firstDateOnPanCountsEvenWithoutLabel() {
        val found = DobDetector.detect(lines("RAVI KUMAR", "13/11/1990", "01/01/2020"), DocType.PAN)
        assertThat(found).hasSize(1)
        assertThat(found.single().rect.top).isEqualTo(40f)
    }

    @Test
    fun yearOfBirth() {
        val m = DobDetector.detect(lines("Year of Birth : 1990"), DocType.AADHAAR).single()
        assertThat(m.rect).isEqualTo(Rect(16 * CHAR_W, 0f, 20 * CHAR_W, 20f))
    }

    @Test
    fun ocrNoiseInDate() {
        assertThat(DobDetector.detect(lines("DOB: l3/1l/199O"), DocType.AADHAAR)).hasSize(1)
    }
}

class AddressDetectorTest {
    @Test
    fun blockFromLabelToPin() {
        val l = lines("Name", "Address: S/O Mohan Lal,", "House 12, Station Road,", "Pune, Maharashtra - 411001", "Other")
        val m = AddressDetector.detect(l).single()
        assertThat(m.confidence).isEqualTo(Confidence.HIGH)
        assertThat(m.rect.top).isEqualTo(l[1].line.rect.top)
        assertThat(m.rect.bottom).isEqualTo(l[3].line.rect.bottom)
    }

    @Test
    fun relationPrefixStartsABlock() {
        assertThat(AddressDetector.detect(lines("C/O Ravi", "Indore 452001"))).hasSize(1)
        assertThat(AddressDetector.detect(lines("पता: गली 4", "Indore 452001"))).hasSize(1)
    }

    @Test
    fun noPinGivesShortLowConfidenceBlock() {
        val l = lines("Address:", "a", "b", "c", "d", "e", "f", "g")
        val m = AddressDetector.detect(l).single()
        assertThat(m.confidence).isEqualTo(Confidence.LOW)
        assertThat(m.rect.bottom).isEqualTo(l[3].line.rect.bottom)
    }

    @Test
    fun noLabelNoAddress() {
        assertThat(AddressDetector.detect(lines("Pune 411001"))).isEmpty()
        assertThat(AddressDetector.detect(lines("Addressee list"))).isEmpty()
    }
}

class DocumentClassifierTest {
    @Test
    fun aadhaarByKeywords() {
        assertThat(DocumentClassifier.classify(lines("Government of India", "My Aadhaar, My Identity"), false, false))
            .isEqualTo(DocType.AADHAAR)
    }

    @Test
    fun aadhaarByValidNumber() {
        assertThat(DocumentClassifier.classify(lines("x"), validAadhaar = true, validPan = false)).isEqualTo(DocType.AADHAAR)
    }

    @Test
    fun panByKeywords() {
        assertThat(DocumentClassifier.classify(lines("INCOME TAX DEPARTMENT", "GOVT. OF INDIA"), false, false))
            .isEqualTo(DocType.PAN)
    }

    @Test
    fun unknownWhenNothingMatches() {
        assertThat(DocumentClassifier.classify(lines("Company ID card", "Expanse"), false, false)).isEqualTo(DocType.UNKNOWN)
    }
}
