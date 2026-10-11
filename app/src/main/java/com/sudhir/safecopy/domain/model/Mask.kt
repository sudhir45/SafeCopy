package com.sudhir.safecopy.domain.model

/** Spec 6.12. MANUAL is a user-drawn box; the rest come from detection (M4). */
enum class FieldType { AADHAAR_NUMBER, AADHAAR_VID, PAN, DOB, ADDRESS, QR, PHOTO, MANUAL }

/** Spec 7.1. [rect] is in bitmap pixel coordinates. */
data class MaskRect(
    val id: String,
    val rect: Rect,
    val source: FieldType,
    val enabled: Boolean,
)
