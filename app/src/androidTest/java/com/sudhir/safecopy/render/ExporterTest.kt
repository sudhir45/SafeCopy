package com.sudhir.safecopy.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.sudhir.safecopy.core.files.TempFiles
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 9, 15.3: the exported JPEG carries no EXIF metadata. */
@RunWith(AndroidJUnit4::class)
class ExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val exporter = Exporter(context, TempFiles(context))

    @Test
    fun exportedJpegHasNoMetadata() = runBlocking {
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val export = exporter.writeJpeg(bmp)
        try {
            val exif = ExifInterface(export.file)
            for (tag in listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_SOFTWARE,
            )) {
                assertWithMessage(tag).that(exif.getAttribute(tag)).isNull()
            }
            assertThat(exif.latLong).isNull()
            // ExifInterface reports orientation "0" (undefined) even with no EXIF at all, so
            // check the file itself: no APP1 segment (EXIF or XMP) may exist. JFIF (APP0) and
            // the sRGB colour profile (APP2) carry no personal data.
            assertThat(jpegSegments(export.file.readBytes())).doesNotContain(0xE1)
        } finally {
            exporter.delete(export)
        }
    }

    @Test
    fun exportLandsInCacheExportWithSpecName() = runBlocking {
        val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val export = exporter.writeJpeg(bmp)
        try {
            assertThat(export.file.parentFile).isEqualTo(TempFiles(context).exportDir)
            assertThat(export.file.name).matches("SafeCopy_\\d{8}_\\d{6}\\.jpg")
            assertThat(export.uri.scheme).isEqualTo("content")
            assertThat(export.sizeBytes).isGreaterThan(0L)
        } finally {
            exporter.delete(export)
        }
    }

    /** Marker bytes of every segment before the image data (SOS). */
    private fun jpegSegments(b: ByteArray): List<Int> {
        val markers = mutableListOf<Int>()
        var i = 2 // after SOI
        while (i + 3 < b.size && b[i] == 0xFF.toByte()) {
            val marker = b[i + 1].toInt() and 0xFF
            markers += marker
            if (marker == 0xDA) break
            val length = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            i += 2 + length
        }
        return markers
    }
}
