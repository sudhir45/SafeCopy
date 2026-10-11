package com.sudhir.safecopy.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.content.FileProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.sudhir.safecopy.MainActivity
import com.sudhir.safecopy.appContainer
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Spec 15.4 Compose UI tests: the main flow from a fresh install, manual mask editing, and a
 * TalkBack-readiness check that every tappable element has a label. Images are synthetic.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainFlowTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = target.appContainer

    @get:Rule
    val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule<MainActivity>()

    @Before
    fun freshInstallState() = runBlocking {
        // Same as "Clear all data", but before the activity reads settings.
        container.settings.clear()
        container.editSession.clear()
    }

    /** Puts a file where the camera flow would, and hands its URI to the share-sheet entry. */
    private fun shareIn(write: (File) -> Unit) {
        val dir = File(target.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "uitest_${System.nanoTime()}.jpg").also(write)
        val uri: Uri = FileProvider.getUriForFile(target, "${target.packageName}.fileprovider", file)
        container.incomingShare.value = uri
    }

    private fun fixture(name: String): (File) -> Unit = { f ->
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/$name").use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
    }

    private val blankImage: (File) -> Unit = { f ->
        val bmp = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(230, 230, 220)) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun skipOnboarding() {
        compose.waitUntilAtLeastOneExists(hasText("Skip"), 10_000)
        compose.onNodeWithText("Skip").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Choose photo"), 10_000)
    }

    private fun waitForEditorSheet() {
        // Detection can take several seconds on an emulator; the sheet's Done appears after it.
        compose.waitUntilAtLeastOneExists(hasText("Done"), 60_000)
    }

    @Test
    fun onboardingThenSharedAadhaarToPreview() {
        compose.waitUntilAtLeastOneExists(hasText("Make safe copies of your ID"), 10_000)
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Get started").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Choose photo"), 10_000)

        shareIn(fixture("aadhaar_front_base.jpg"))
        waitForEditorSheet()
        compose.onNodeWithText("Aadhaar card").assertExists()
        compose.onNodeWithText("Aadhaar number (first 8 digits)").assertExists()
        compose.onNodeWithText("QR code").assertExists()
        compose.onNodeWithText("Done").performClick()

        compose.onNodeWithText("Next").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Recipient"), 10_000)
        compose.onNodeWithText("Next").assertIsNotEnabled() // recipient is required (spec 5.2)
        compose.onNode(hasSetTextAction() and hasText("Recipient", substring = true)).performTextInput("Test Bank")
        compose.onNodeWithText("Next").assertIsEnabled().performClick()

        compose.waitUntilAtLeastOneExists(hasText("Share"), 30_000)
        compose.waitUntil(30_000) {
            runCatching { compose.onNodeWithText("Share").assertIsEnabled() }.isSuccess
        }
        // The masks handed to export include the enabled Aadhaar number and QR.
        val masks = container.editSession.masks
        assertThat(masks.filter { it.enabled }.map { it.source.name })
            .containsAtLeast("AADHAAR_NUMBER", "QR")
    }

    @Test
    fun manualMaskDrawDeleteUndoRedo() {
        skipOnboarding()
        shareIn(blankImage)
        waitForEditorSheet()
        compose.onNodeWithText("Couldn't find details automatically", substring = true).assertExists()
        compose.onNodeWithText("Done").performClick()

        val canvas = compose.onNodeWithContentDescription("Document being edited")
        fun covered(n: Int) = SemanticsMatcher("covers $n") {
            it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("$n area") == true
        }

        compose.onNodeWithContentDescription("Add mask").performClick()
        canvas.performTouchInput { swipe(center.copy(x = width * 0.3f, y = height * 0.35f), center.copy(x = width * 0.7f, y = height * 0.6f), 400) }
        compose.waitUntilAtLeastOneExists(covered(1), 5_000)

        compose.onNodeWithContentDescription("Delete mask").assertIsEnabled().performClick()
        compose.waitUntilAtLeastOneExists(covered(0), 5_000)

        compose.onNodeWithContentDescription("Undo").performClick()
        compose.waitUntilAtLeastOneExists(covered(1), 5_000)
        compose.onNodeWithContentDescription("Redo").performClick()
        compose.waitUntilAtLeastOneExists(covered(0), 5_000)
    }

    @Test
    fun everyTappableElementHasALabel() {
        skipOnboarding()
        val unlabeled = compose.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes().filter { node ->
            val c = node.config
            c.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
        }
        assertWithMessage("unlabelled clickable nodes on Home: $unlabeled").that(unlabeled).isEmpty()
        // Spot-check the editor's icon buttons too.
        shareIn(blankImage)
        waitForEditorSheet()
        compose.onNodeWithText("Done").performClick()
        for (label in listOf("Back", "Detected fields", "Add mask", "Rotate", "Undo", "Redo", "Delete mask")) {
            compose.onNode(hasContentDescription(label)).assertExists()
        }
    }
}
