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
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_SOFTWARE,
            )) {
                assertWithMessage(tag).that(exif.getAttribute(tag)).isNull()
            }
            assertThat(exif.latLong).isNull()
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
}
