package com.sudhir.safecopy.domain.detect

import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.domain.detect.OcrFixtures.fakeAadhaar
import com.sudhir.safecopy.domain.mask.MaskMerger
import com.sudhir.safecopy.domain.model.Confidence
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.Rect
import org.junit.Test

class FieldDetectorTest {
    private val front = OcrFixtures.result(
        "Government of India",
        "DOB: 13/11/1990",
        fakeAadhaar(3),
        "VID : 9186 0913 9099 6030",
        "My Aadhaar, My Identity",
    )
    private val qr = Rect(500f, 0f, 600f, 100f)

    @Test
    fun syntheticAadhaarFront() {
        val r = FieldDetector.detect(front, listOf(qr), 1000, 1000)
        assertThat(r.docType).isEqualTo(DocType.AADHAAR)
        assertThat(r.fields.map { it.type }).containsExactly(
            FieldType.AADHAAR_NUMBER, FieldType.AADHAAR_VID, FieldType.DOB, FieldType.QR,
        )
        val enabled = r.masks.associate { it.source to it.enabled }
        assertThat(enabled).containsExactly(
            FieldType.AADHAAR_NUMBER, true,
            FieldType.AADHAAR_VID, true,
            FieldType.QR, true,
            FieldType.DOB, false,
        )
        assertThat(r.qrHint).isFalse()
        assertThat(r.hasStrongMatch).isTrue()
    }

    @Test
    fun aadhaarWithoutQrAsksForManualBox() {
        assertThat(FieldDetector.detect(front, emptyList(), 1000, 1000).qrHint).isTrue()
    }

    @Test
    fun qrDefaultFollowsOption() {
        val r = FieldDetector.detect(front, listOf(qr), 1000, 1000, DetectionOptions(maskQrByDefault = false))
        assertThat(r.masks.single { it.source == FieldType.QR }.enabled).isFalse()
    }

    @Test
    fun nothingFound() {
        val r = FieldDetector.detect(OcrFixtures.result("hello world"), emptyList(), 100, 100)
        assertThat(r.nothingFound).isTrue()
        assertThat(r.docType).isEqualTo(DocType.UNKNOWN)
        assertThat(r.hasStrongMatch).isFalse()
    }

    @Test
    fun qrAloneDoesNotWinOrientationScore() {
        val sidewaysWithQr = FieldDetector.detect(OcrFixtures.result("x y z"), listOf(qr, qr.translate(200f, 0f)), 1000, 1000)
        val uprightNoQr = FieldDetector.detect(front, emptyList(), 1000, 1000)
        assertThat(uprightNoQr.score).isGreaterThan(sidewaysWithQr.score)
    }

    @Test
    fun scorePrefersStrongMatchesOverText() {
        val strong = FieldDetector.detect(front, listOf(qr), 1000, 1000)
        val gibberish = FieldDetector.detect(OcrFixtures.result("x".repeat(400)), emptyList(), 1000, 1000)
        assertThat(strong.score).isGreaterThan(gibberish.score)
    }
}

class MaskMergerTest {
    private fun field(type: FieldType, r: Rect, id: String = type.name, c: Confidence = Confidence.HIGH, on: Boolean = true) =
        DetectedField(id, type, r, c, on)

    @Test
    fun textPaddingIsFourPercentWideAndFifteenPercentTall() {
        val m = MaskMerger.merge(listOf(field(FieldType.PAN, Rect(100f, 100f, 300f, 140f))), 1000, 1000).single()
        assertThat(m.rect).isEqualTo(Rect(92f, 94f, 308f, 146f))
    }

    @Test
    fun qrPaddingIsEightPercentOfWidthAllRound() {
        val m = MaskMerger.merge(listOf(field(FieldType.QR, Rect(100f, 100f, 200f, 200f))), 1000, 1000).single()
        assertThat(m.rect).isEqualTo(Rect(92f, 92f, 208f, 208f))
    }

    @Test
    fun clampedToImage() {
        val m = MaskMerger.merge(listOf(field(FieldType.QR, Rect(0f, 0f, 100f, 100f))), 150, 150).single()
        assertThat(m.rect).isEqualTo(Rect(0f, 0f, 108f, 108f))
    }

    @Test
    fun overlappingSameTypeMergeOthersDont() {
        val masks = MaskMerger.merge(
            listOf(
                field(FieldType.ADDRESS, Rect(0f, 0f, 100f, 20f), "a"),
                field(FieldType.ADDRESS, Rect(0f, 22f, 100f, 42f), "b", Confidence.LOW),
                field(FieldType.DOB, Rect(0f, 0f, 100f, 20f), "c"),
                field(FieldType.ADDRESS, Rect(0f, 500f, 100f, 520f), "d"),
            ),
            1000, 1000,
        )
        assertThat(masks.count { it.source == FieldType.ADDRESS }).isEqualTo(2)
        val merged = masks.first { it.source == FieldType.ADDRESS && it.rect.top < 100f }
        assertThat(merged.rect.bottom).isGreaterThan(42f)
        assertThat(merged.confidence).isEqualTo(Confidence.HIGH)
        assertThat(masks.count { it.source == FieldType.DOB }).isEqualTo(1)
    }
}

class RectIouTest {
    @Test
    fun iou() {
        val a = Rect(0f, 0f, 10f, 10f)
        assertThat(a.iou(a)).isEqualTo(1f)
        assertThat(a.iou(Rect(20f, 20f, 30f, 30f))).isEqualTo(0f)
        assertThat(a.iou(Rect(5f, 0f, 15f, 10f))).isWithin(1e-6f).of(50f / 150f)
    }
}
