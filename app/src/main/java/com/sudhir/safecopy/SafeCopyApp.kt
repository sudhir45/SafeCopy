package com.sudhir.safecopy

import android.app.Application
import android.content.Context
import android.net.Uri
import com.sudhir.safecopy.core.files.TempFiles
import com.sudhir.safecopy.core.image.EditSession
import com.sudhir.safecopy.core.image.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Manual DI container (spec 12). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val tempFiles = TempFiles(context)
    val imageLoader = ImageLoader(context.contentResolver)
    val editSession = EditSession()

    /** Image URI received through the share sheet, waiting to be imported by Home. */
    val incomingShare = MutableStateFlow<Uri?>(null)
}

class SafeCopyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.tempFiles.sweepOnStart() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SafeCopyApp).container
