package com.sudhir.safecopy

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.sudhir.safecopy.navigation.SafeCopyNavGraph
import com.sudhir.safecopy.ui.theme.SafeCopyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Spec 2.8 / 14.2: block screenshots and recents thumbnails. Debug builds skip it so
        // screenshots work during development (spec 17.7); release always sets FORCE_SECURE.
        if (!BuildConfig.DEBUG || BuildConfig.FORCE_SECURE) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        // Only on a fresh start: after a configuration change the intent was already handled.
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            SafeCopyTheme {
                SafeCopyNavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /** Spec 14.2: accept only ACTION_SEND with an image MIME type and a content:// stream. */
    private fun handleShareIntent(intent: Intent?) {
        val uri = sharedImageUri(intent) ?: return
        appContainer.incomingShare.value = uri
    }

    private fun sharedImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        return uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
    }
}
