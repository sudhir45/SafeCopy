package com.sudhir.safecopy.core.image

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The image being edited, held in memory only (spec 2.4, 5.3).
 *
 * App-scoped so it survives navigation and configuration changes. If the process dies, it is
 * gone, and the editor tells the user to pick the image again; originals are never persisted
 * to survive process death.
 */
class EditSession {
    private val _image = MutableStateFlow<Bitmap?>(null)
    val image: StateFlow<Bitmap?> = _image.asStateFlow()

    fun start(bitmap: Bitmap) {
        _image.value = bitmap
    }

    fun replace(bitmap: Bitmap) {
        _image.value = bitmap
    }

    fun clear() {
        // Not recycled here: Compose may still be drawing it. Dropping the reference is enough.
        _image.value = null
    }
}
