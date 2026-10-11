package com.sudhir.safecopy.domain.model

import com.sudhir.safecopy.domain.watermark.TemplateEngine
import java.time.LocalDate
import java.util.Locale

/** Spec 8.1 step 1: font size = image width / divisor. */
enum class WatermarkSize(val widthDivisor: Float) { S(28f), M(22f), L(16f) }

/** Spec 5.2: dark / light / red. Concrete colours live in the renderer. */
enum class WatermarkColour { DARK, LIGHT, RED }

/** Spec 8.4 built-in templates. */
enum class WatermarkTemplate(val pattern: String) {
    KYC("FOR {recipient} KYC ONLY · {date}"),
    SPECIFIC_PURPOSE("ONLY FOR {purpose} · {recipient} · {date}"),
    RENTAL("FOR RENTAL AGREEMENT WITH {recipient} · {date}"),
    HOTEL("HOTEL CHECK-IN AT {recipient} · {date}"),
    JOB("EMPLOYMENT VERIFICATION · {recipient} · {date}"),
    NOT_VALID_ELSEWHERE("SUBMITTED TO {recipient} · NOT VALID FOR ANY OTHER PURPOSE · {date}"),
}

/** How the tiled text looks (spec 8.1). Defaults follow spec 11. */
data class WatermarkStyle(
    val opacity: Float = DEFAULT_OPACITY,
    val angleDegrees: Float = DEFAULT_ANGLE,
    val size: WatermarkSize = WatermarkSize.M,
    val colour: WatermarkColour = WatermarkColour.DARK,
) {
    companion object {
        const val DEFAULT_OPACITY = 0.30f
        const val MIN_OPACITY = 0.15f
        const val MAX_OPACITY = 0.60f
        const val DEFAULT_ANGLE = -30f
        const val MIN_ANGLE = -45f
        const val MAX_ANGLE = 45f
    }
}

/** Everything the user chose on the Watermark screen. */
data class WatermarkConfig(
    val template: WatermarkTemplate = WatermarkTemplate.KYC,
    val recipient: String = "",
    val purpose: String = "",
    val date: LocalDate,
    val uppercase: Boolean = true,
    val style: WatermarkStyle = WatermarkStyle(),
) {
    /** Spec 5.2: the recipient is required. */
    val isComplete: Boolean get() = recipient.isNotBlank()

    fun text(locale: Locale = Locale.getDefault()): String = TemplateEngine.render(
        pattern = template.pattern,
        recipient = recipient,
        purpose = purpose,
        date = TemplateEngine.formatDate(date, locale),
        uppercase = uppercase,
    )
}
