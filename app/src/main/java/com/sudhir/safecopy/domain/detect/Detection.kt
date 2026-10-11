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
    /** Raw (unpadded) detections. */
    val fields: List<DetectedField>,
    /** Padded, merged masks to put on the image. */
    val masks: List<MaskRect>,
    /** Spec 6.10: Aadhaar but no QR found, so suggest drawing one by hand. */
    val qrHint: Boolean,
) {
    val nothingFound: Boolean get() = fields.isEmpty()
}
