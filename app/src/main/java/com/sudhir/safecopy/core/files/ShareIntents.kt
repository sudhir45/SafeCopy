package com.sudhir.safecopy.core.files

import android.content.ClipData
import android.content.ComponentName
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

    /** [self] is left out of the targets: sharing a safe copy back into SafeCopy makes no sense. */
    fun chooser(uri: Uri, title: CharSequence, self: ComponentName? = null): Intent =
        Intent.createChooser(image(uri), title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (self != null) putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(self))
        }
}
