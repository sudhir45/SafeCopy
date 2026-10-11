package com.sudhir.safecopy.domain.detect

import com.sudhir.safecopy.domain.model.Confidence
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Rect

enum class DocType { AADHAAR, PAN, UNKNOWN }

enum class AadhaarMaskMode { FIRST_8, ALL_12 }

enum class PanMaskMode { FIRST_6, ALL }

/** User-configurable detection choices (spec 11); Settings (M5) will supply them. */
data class DetectionOptions(
    val aadhaarMaskMode: AadhaarMaskMode = AadhaarMaskMode.FIRST_8,
    val panMaskMode: PanMaskMode = PanMaskMode.FIRST_6,
    val maskQrByDefault: Boolean = true,
)

/** Spec 6.12. [rect] is the tight (unpadded) box in bitmap pixels. */
data class DetectedField(
    val id: String,
    val type: FieldType,
    val rect: Rect,
    val confidence: Confidence,
    val defaultEnabled: Boolean,
)

/** What the editor gets back from detection. */
data class DetectionResult(
    val docType: DocType,
    /** Raw detections (used by tests and for scoring). */
    val fields: List<DetectedField>,
    /** Padded, merged masks to put on the image. */
    val masks: List<MaskRect>,
    /** Spec 6.10: Aadhaar but no QR found, so suggest drawing one by hand. */
    val qrHint: Boolean,
    /** Characters the OCR read, a rough measure of how readable this orientation is. */
    val textAmount: Int,
) {
    val nothingFound: Boolean get() = fields.isEmpty()

    /** A trustworthy ID number was found: this orientation is right. */
    val hasStrongMatch: Boolean
        get() = fields.any { it.confidence == Confidence.HIGH && it.type in STRONG_TYPES }

    /**
     * Used to pick the best of several orientations: checksum-valid ID numbers dominate, then
     * other confident text fields, then a recognised document type, then the amount of text.
     * QR codes don't count: they're found at any rotation, so they say nothing about it.
     */
    val score: Int
        get() = fields.count { it.confidence == Confidence.HIGH && it.type in STRONG_TYPES } * 1000 +
            fields.count { it.confidence == Confidence.HIGH && it.type !in STRONG_TYPES && it.type != FieldType.QR } * 100 +
            (if (docType != DocType.UNKNOWN) 500 else 0) +
            textAmount.coerceAtMost(99)

    private companion object {
        val STRONG_TYPES = setOf(FieldType.AADHAAR_NUMBER, FieldType.AADHAAR_VID, FieldType.PAN)
    }
}
