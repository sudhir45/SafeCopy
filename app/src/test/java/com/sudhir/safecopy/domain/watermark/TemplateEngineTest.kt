package com.sudhir.safecopy.domain.watermark

import com.google.common.truth.Truth.assertThat
import com.sudhir.safecopy.domain.model.WatermarkConfig
import com.sudhir.safecopy.domain.model.WatermarkTemplate
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TemplateEngineTest {
    private val date = "11 Oct 2026"

    private fun render(t: WatermarkTemplate, recipient: String = "Acme Bank", purpose: String = "", uppercase: Boolean = true) =
        TemplateEngine.render(t.pattern, recipient, purpose, date, uppercase = uppercase)

    @Test
    fun everyBuiltInTemplateRenders() {
        assertThat(render(WatermarkTemplate.KYC)).isEqualTo("FOR ACME BANK KYC ONLY · 11 OCT 2026")
        assertThat(render(WatermarkTemplate.SPECIFIC_PURPOSE, purpose = "loan application"))
            .isEqualTo("ONLY FOR LOAN APPLICATION · ACME BANK · 11 OCT 2026")
        assertThat(render(WatermarkTemplate.RENTAL, recipient = "Mr Rao"))
            .isEqualTo("FOR RENTAL AGREEMENT WITH MR RAO · 11 OCT 2026")
        assertThat(render(WatermarkTemplate.HOTEL, recipient = "Hotel Sea View"))
            .isEqualTo("HOTEL CHECK-IN AT HOTEL SEA VIEW · 11 OCT 2026")
        assertThat(render(WatermarkTemplate.JOB, recipient = "Example Ltd"))
            .isEqualTo("EMPLOYMENT VERIFICATION · EXAMPLE LTD · 11 OCT 2026")
        assertThat(render(WatermarkTemplate.NOT_VALID_ELSEWHERE))
            .isEqualTo("SUBMITTED TO ACME BANK · NOT VALID FOR ANY OTHER PURPOSE · 11 OCT 2026")
    }

    @Test
    fun segmentWithEmptyTokenIsDroppedWithoutStraySeparator() {
        assertThat(render(WatermarkTemplate.SPECIFIC_PURPOSE, purpose = "  "))
            .isEqualTo("ACME BANK · 11 OCT 2026")
        assertThat(TemplateEngine.render("For {recipient} · {purpose} · {date} · {ref}", "X", "", "", uppercase = false))
            .isEqualTo("For X")
    }

    @Test
    fun uppercaseCanBeTurnedOff() {
        assertThat(render(WatermarkTemplate.KYC, recipient = "Acme Bank", uppercase = false))
            .isEqualTo("FOR Acme Bank KYC ONLY · 11 Oct 2026")
    }

    @Test
    fun whitespaceInInputsIsCollapsed() {
        assertThat(render(WatermarkTemplate.KYC, recipient = "  Acme \n  Bank ")).isEqualTo("FOR ACME BANK KYC ONLY · 11 OCT 2026")
    }

    @Test
    fun longRecipientIsTruncatedWithEllipsisToMaxLength() {
        val text = render(WatermarkTemplate.KYC, recipient = "A".repeat(200))
        assertThat(text.length).isEqualTo(TemplateEngine.MAX_LENGTH)
        assertThat(text).startsWith("FOR AAAA")
        assertThat(text).contains("…")
        assertThat(text).endsWith("KYC ONLY · 11 OCT 2026")
    }

    @Test
    fun longPurposeIsTruncatedBeforeShortRecipient() {
        val text = render(WatermarkTemplate.SPECIFIC_PURPOSE, recipient = "Bank", purpose = "P".repeat(200))
        assertThat(text.length).isAtMost(TemplateEngine.MAX_LENGTH)
        assertThat(text).endsWith("· BANK · 11 OCT 2026")
    }

    @Test
    fun bothLongAreBalanced() {
        val text = render(WatermarkTemplate.SPECIFIC_PURPOSE, recipient = "R".repeat(100), purpose = "P".repeat(100))
        assertThat(text.length).isAtMost(TemplateEngine.MAX_LENGTH)
        assertThat(text).contains("RRRRRRRRRRRR")
        assertThat(text).contains("PPPPPPPPPPPP")
    }

    @Test
    fun neverExceedsMaxLength() {
        for (template in WatermarkTemplate.entries) {
            val text = render(template, recipient = "R".repeat(300), purpose = "P".repeat(300))
            assertThat(text.length).isAtMost(TemplateEngine.MAX_LENGTH)
        }
    }

    @Test
    fun turkishLocaleDoesNotDotCapitalI() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertThat(render(WatermarkTemplate.KYC, recipient = "india")).contains("INDIA")
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun dateFormatIsDdMmmYyyy() {
        assertThat(TemplateEngine.formatDate(LocalDate.of(2026, 1, 5), Locale.ENGLISH)).isEqualTo("05 Jan 2026")
    }

    @Test
    fun configRequiresRecipient() {
        val c = WatermarkConfig(date = LocalDate.of(2026, 10, 11))
        assertThat(c.isComplete).isFalse()
        assertThat(c.copy(recipient = "  ").isComplete).isFalse()
        assertThat(c.copy(recipient = "X").isComplete).isTrue()
        assertThat(c.copy(recipient = "x").text(Locale.ENGLISH)).isEqualTo("FOR X KYC ONLY · 11 OCT 2026")
    }
}
