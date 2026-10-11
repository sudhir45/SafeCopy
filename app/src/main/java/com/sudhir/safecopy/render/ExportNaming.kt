package com.sudhir.safecopy.render

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Spec 9: `SafeCopy_<yyyyMMdd_HHmmss>.jpg`. */
object ExportNaming {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)

    fun jpegName(at: LocalDateTime): String = "SafeCopy_${stamp.format(at)}.jpg"

    /** Human-readable size, e.g. "812 KB" or "1.4 MB". */
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
