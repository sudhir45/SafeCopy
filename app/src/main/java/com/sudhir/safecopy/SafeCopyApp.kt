package com.sudhir.safecopy

import android.app.Application
import android.content.Context

/** Manual DI container (spec 12). Grows as milestones add repositories and engines. */
class AppContainer(@Suppress("unused") private val context: Context)

class SafeCopyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
