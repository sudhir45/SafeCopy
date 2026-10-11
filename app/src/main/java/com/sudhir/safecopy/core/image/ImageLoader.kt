package com.sudhir.safecopy.core.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.sudhir.safecopy.core.log.SafeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

sealed interface LoadResult {
    data class Success(val bitmap: Bitmap) : LoadResult
    data class Failure(val reason: Reason) : LoadResult

    enum class Reason { TOO_LARGE, NOT_AN_IMAGE, UNREADABLE }
}

/**
 * Loads an untrusted image URI into an upright, software ARGB_8888 bitmap with the long edge
 * at most [ImageMath.MAX_EDGE] (spec 4.1, 5.3, 14.2).
 *
 * The bytes are read into memory once (capped at 25 MB), so nothing is written to disk and the
 * URI is opened only once. BitmapFactory + ExifInterface is used on every API level instead of
 * ImageDecoder on 28+, so orientation handling is a single code path.
 */
class ImageLoader(private val resolver: ContentResolver) {

    suspend fun load(uri: Uri): LoadResult = withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            // Never follow file paths from other apps (spec 14.2).
            return@withContext LoadResult.Failure(LoadResult.Reason.UNREADABLE)
        }
        val type = runCatching { resolver.getType(uri) }.getOrNull()
        if (type != null && !type.startsWith("image/")) {
            return@withContext LoadResult.Failure(LoadResult.Reason.NOT_AN_IMAGE)
        }

        val stream = try {
            resolver.openInputStream(uri)
        } catch (e: Exception) {
            SafeLog.w("open failed", e)
            null
        } ?: return@withContext LoadResult.Failure(LoadResult.Reason.UNREADABLE)

        val bytes = try {
            stream.use { ImageMath.readCapped(it) }
        } catch (e: Exception) {
            SafeLog.w("read failed", e)
            return@withContext LoadResult.Failure(LoadResult.Reason.UNREADABLE)
        } ?: return@withContext LoadResult.Failure(LoadResult.Reason.TOO_LARGE)

        withContext(Dispatchers.Default) { decode(bytes) }
    }

    private fun decode(bytes: ByteArray): LoadResult {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return LoadResult.Failure(LoadResult.Reason.NOT_AN_IMAGE)
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageMath.inSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return LoadResult.Failure(LoadResult.Reason.NOT_AN_IMAGE)

            val exif = runCatching {
                ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            LoadResult.Success(uprightAndCapped(decoded, ImageMath.orientationFor(exif)))
        } catch (e: OutOfMemoryError) {
            SafeLog.w("decode out of memory", e)
            LoadResult.Failure(LoadResult.Reason.TOO_LARGE)
        } catch (e: Exception) {
            SafeLog.w("decode failed", e)
            LoadResult.Failure(LoadResult.Reason.UNREADABLE)
        }
    }

    /** One matrix pass: exact downscale, then EXIF rotation, then mirror. */
    private fun uprightAndCapped(src: Bitmap, orientation: ImageMath.Orientation): Bitmap {
        val target = ImageMath.fitWithin(src.width, src.height)
        val matrix = Matrix().apply {
            postScale(target.width.toFloat() / src.width, target.height.toFloat() / src.height)
            postRotate(orientation.rotationDegrees.toFloat())
            if (orientation.flipHorizontal) postScale(-1f, 1f)
        }
        if (matrix.isIdentity) return src
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out !== src) src.recycle()
        return out
    }

    companion object {
        /** Rotates by a quarter turn clockwise (editor's Rotate button). */
        fun rotateClockwise(src: Bitmap): Bitmap {
            val matrix = Matrix().apply { postRotate(90f) }
            return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        }
    }
}
