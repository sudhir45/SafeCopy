package com.sudhir.safecopy.render

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class ExportNamingTest {
    @Test
    fun jpegNameMatchesSpecPattern() {
        assertThat(ExportNaming.jpegName(LocalDateTime.of(2026, 10, 11, 9, 5, 7)))
            .isEqualTo("SafeCopy_20261011_090507.jpg")
    }

    @Test
    fun formatSize() {
        assertThat(ExportNaming.formatSize(900)).isEqualTo("900 B")
        assertThat(ExportNaming.formatSize(812 * 1024L)).isEqualTo("812 KB")
        assertThat(ExportNaming.formatSize(1_468_006)).isEqualTo("1.4 MB")
    }
}
