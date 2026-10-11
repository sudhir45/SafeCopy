package com.sudhir.safecopy.core.log

import android.util.Log
import com.sudhir.safecopy.BuildConfig

/**
 * The only logger the app may use (spec 2.7, rule 20.3).
 *
 * Logs only in debug builds, and redacts every digit so a message can never carry an
 * Aadhaar/PAN/VID number by accident. Never pass OCR text, QR content, file paths of user
 * images or bitmaps here, even redacted.
 */
object SafeLog {
    private const val TAG = "SafeCopy"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, redact(message))
    }

    fun w(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, redact(message) + error?.let { " (${it.javaClass.simpleName})" }.orEmpty())
    }

    fun e(message: String, error: Throwable? = null) {
        // Only the exception type is logged: messages and stack traces can contain user data.
        if (BuildConfig.DEBUG) Log.e(TAG, redact(message) + error?.let { " (${it.javaClass.simpleName})" }.orEmpty())
    }

    /** Replaces every Unicode digit (incl. Devanagari) with `#`. */
    fun redact(message: String): String = buildString(message.length) {
        message.forEach { append(if (it.isDigit()) '#' else it) }
    }
}
