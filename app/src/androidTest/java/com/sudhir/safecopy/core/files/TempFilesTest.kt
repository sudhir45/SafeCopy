package com.sudhir.safecopy.core.files

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Spec 15.3: cleanup, share intent and permission checks. */
@RunWith(AndroidJUnit4::class)
class TempFilesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val temp = TempFiles(context)
    private val now = System.currentTimeMillis()

    private fun file(dir: File, name: String, ageMs: Long): File =
        File(dir.apply { mkdirs() }, name).apply {
            writeText("x")
            setLastModified(now - ageMs)
        }

    @Before
    fun clean() {
        listOf(temp.sourceDir, temp.cameraDir, temp.exportDir).forEach { it.deleteRecursively() }
    }

    @Test
    fun sweepOnStartAppliesPerFolderRules() = runBlocking {
        val source = file(temp.sourceDir, "a.jpg", ageMs = 0)
        val freshCapture = file(temp.cameraDir, "new.jpg", ageMs = TimeUnit.MINUTES.toMillis(2))
        val oldCapture = file(temp.cameraDir, "old.jpg", ageMs = TimeUnit.MINUTES.toMillis(30))
        val freshExport = file(temp.exportDir, "new.jpg", ageMs = TimeUnit.MINUTES.toMillis(10))
        val oldExport = file(temp.exportDir, "old.jpg", ageMs = TimeUnit.HOURS.toMillis(2))

        temp.sweepOnStart()

        assertThat(source.exists()).isFalse()
        assertThat(freshCapture.exists()).isTrue()
        assertThat(oldCapture.exists()).isFalse()
        assertThat(freshExport.exists()).isTrue()
        assertThat(oldExport.exists()).isFalse()
    }

    @Test
    fun clearOriginalsEmptiesSourceAndCamera() = runBlocking {
        file(temp.sourceDir, "a.jpg", 0)
        file(temp.cameraDir, "b.jpg", 0)
        val export = file(temp.exportDir, "c.jpg", 0)

        temp.clearOriginals()

        assertThat(temp.sourceDir.listFiles().orEmpty()).isEmpty()
        assertThat(temp.cameraDir.listFiles().orEmpty()).isEmpty()
        assertThat(export.exists()).isTrue()
    }

    @Test
    fun cameraCaptureUriIsDeletable() = runBlocking {
        val uri = temp.newCameraCapture()
        assertThat(temp.cameraDir.listFiles().orEmpty()).hasLength(1)
        temp.deleteCameraCapture(uri)
        assertThat(temp.cameraDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun shareChooserGrantsReadOnStream() = runBlocking {
        val uri = temp.newCameraCapture()
        val chooser = ShareIntents.chooser(uri, "Share")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("image/jpeg")
        @Suppress("DEPRECATION")
        assertThat(send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)).isEqualTo(uri)
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(send.clipData!!.getItemAt(0).uri).isEqualTo(uri)
        temp.deleteCameraCapture(uri)
    }

    @Test
    fun installedAppRequestsNoNetworkPermission() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        assertThat(requested).doesNotContain("android.permission.INTERNET")
        assertThat(requested).doesNotContain("android.permission.ACCESS_NETWORK_STATE")
        assertThat(requested).doesNotContain("android.permission.CAMERA")
    }
}
