package com.sudhir.safecopy

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            SafeCopyTheme {
                SafeCopyNavGraph()
            }
        }
    }
}
