package com.sudhir.safecopy.core.files

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sudhir.safecopy.core.log.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * All temporary files live under cacheDir (spec 2.4, 9):
 * - `source/`: originals, deleted after use and on every app start
 * - `camera/`: system-camera captures, deleted right after loading
 * - `export/`: outputs handed to other apps, deleted after an hour
 */
class TempFiles(private val context: Context) {
    private val authority = "${context.packageName}.fileprovider"

    val sourceDir get() = File(context.cacheDir, "source")
    val cameraDir get() = File(context.cacheDir, "camera")
    val exportDir get() = File(context.cacheDir, "export")

    /** Creates an empty capture file and returns its FileProvider URI for ACTION_IMAGE_CAPTURE. */
    suspend fun newCameraCapture(): Uri = withContext(Dispatchers.IO) {
        cameraDir.mkdirs()
        val file = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        FileProvider.getUriForFile(context, authority, file)
    }

    /** Deletes the file behind one of our own camera URIs. Ignores any other URI. */
    suspend fun deleteCameraCapture(uri: Uri) = withContext(Dispatchers.IO) {
        if (uri.authority != authority) return@withContext
        val name = uri.lastPathSegment ?: return@withContext
        File(cameraDir, File(name).name).delete()
    }

    /** Discard / cancel: drop every original we might still hold on disk. */
    suspend fun clearOriginals() = withContext(Dispatchers.IO) {
        sourceDir.deleteChildren { true }
        cameraDir.deleteChildren { true }
    }

    /** "Clear all data": everything in the cache, exports included. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        context.cacheDir.deleteChildren { true }
    }

    /**
     * Stale-file sweep on app start (spec 2.4, 9).
     *
     * Camera captures younger than [CAMERA_GRACE_MS] survive one sweep: on low-memory phones
     * Android often kills the app while the camera is open, and the capture comes back to a
     * freshly started process that still has to load it.
     */
    suspend fun sweepOnStart() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        sourceDir.deleteChildren { true }
        cameraDir.deleteChildren { now - it.lastModified() > CAMERA_GRACE_MS }
        exportDir.deleteChildren { now - it.lastModified() > EXPORT_TTL_MS }
    }

    private fun File.deleteChildren(predicate: (File) -> Boolean) {
        val children = listFiles() ?: return
        var failed = 0
        for (child in children) {
            if (predicate(child) && !child.deleteRecursively()) failed++
        }
        if (failed > 0) SafeLog.w("temp sweep: $failed files not deleted in $name")
    }

    companion object {
        val CAMERA_GRACE_MS = TimeUnit.MINUTES.toMillis(10)
        val EXPORT_TTL_MS = TimeUnit.HOURS.toMillis(1)
    }
}
