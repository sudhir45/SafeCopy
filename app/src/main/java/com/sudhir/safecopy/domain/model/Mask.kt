package com.sudhir.safecopy.domain.model

/** Spec 6.12. MANUAL is a user-drawn box; the rest come from detection (M4). */
enum class FieldType { AADHAAR_NUMBER, AADHAAR_VID, PAN, DOB, ADDRESS, QR, PHOTO, MANUAL }

/** Spec 6.12. LOW means "suggested, please check" (e.g. a number that fails the checksum). */
enum class Confidence { HIGH, LOW }

/**
 * Spec 7.1. [rect] is in bitmap pixel coordinates. [confidence] is an addition to the spec
 * model so the editor can highlight low-confidence detections.
 */
data class MaskRect(
    val id: String,
    val rect: Rect,
    val source: FieldType,
    val enabled: Boolean,
    val confidence: Confidence = Confidence.HIGH,
)
