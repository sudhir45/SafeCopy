package com.sudhir.safecopy.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.sudhir.safecopy.core.files.TempFiles
import com.sudhir.safecopy.core.log.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

data class ExportedFile(val file: File, val uri: Uri, val sizeBytes: Long)

/**
 * Encodes the composed bitmap (spec 9). `Bitmap.compress` writes a bare JPEG with no EXIF,
 * and nothing is copied from the source file; ExifInterface.saveAttributes is never used.
 */
class Exporter(private val context: Context, private val tempFiles: TempFiles) {

    suspend fun writeJpeg(bitmap: Bitmap, quality: Int = JPEG_QUALITY): ExportedFile = withContext(Dispatchers.IO) {
        val dir = tempFiles.exportDir.apply { mkdirs() }
        val file = File(dir, ExportNaming.jpegName(LocalDateTime.now()))
        file.outputStream().buffered().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encode failed" }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        ExportedFile(file, uri, file.length())
    }

    /** Copies an export to a location the user picked with ACTION_CREATE_DOCUMENT. */
    suspend fun copyTo(export: ExportedFile, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // "wt" truncates, in case the user picked an existing file to overwrite.
            val out = context.contentResolver.openOutputStream(destination, "wt") ?: return@withContext false
            out.use { o -> export.file.inputStream().use { it.copyTo(o) } }
            true
        } catch (e: Exception) {
            SafeLog.w("save to device failed", e)
            false
        }
    }

    suspend fun delete(export: ExportedFile) = withContext(Dispatchers.IO) { export.file.delete() }

    companion object {
        const val JPEG_QUALITY = 90
    }
}
