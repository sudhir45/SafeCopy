package com.sudhir.safecopy.core.files

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** Spec 9: ACTION_SEND with a FileProvider URI, a read grant, and ClipData so the grant applies. */
object ShareIntents {
    fun image(uri: Uri, mimeType: String = "image/jpeg"): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun chooser(uri: Uri, title: CharSequence): Intent =
        Intent.createChooser(image(uri), title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
