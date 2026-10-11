package com.sudhir.safecopy.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import com.sudhir.safecopy.core.image.ImageMath
import com.sudhir.safecopy.domain.mask.MaskGeometry
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.Rect
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 15.3 / M4 "done when": full detection on every synthetic fixture finds each ground-truth
 * field with IoU ≥ 0.5, and the fields that should be masked by default are on.
 *
 * Fixtures come from tools/fixtures/generate_fixtures.py and are entirely synthetic.
 */
@RunWith(AndroidJUnit4::class)
class FixtureDetectionTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val service = DetectionService()

    private data class Truth(val type: FieldType, val rect: Rect)

    @Test
    fun everyFixtureFindsItsFields() = runBlocking {
        val entries = JSONArray(assets.open("fixtures/ground_truth.json").bufferedReader().readText())
        val failures = mutableListOf<String>()

        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val file = e.getString("file")
            var bitmap = assets.open("fixtures/$file").use { BitmapFactory.decodeStream(it) }!!

            // Same long-edge cap as the app's loader (spec 4.1).
            val fit = ImageMath.fitWithin(bitmap.width, bitmap.height)
            val scale = fit.width.toFloat() / bitmap.width
            if (scale < 1f) bitmap = bitmap.scale(fit.width, fit.height)

            val truths = (0 until e.getJSONArray("fields").length()).map { j ->
                val f = e.getJSONArray("fields").getJSONObject(j)
                val r = f.getJSONArray("rect")
                Truth(
                    FieldType.valueOf(f.getString("type")),
                    Rect(
                        r.getDouble(0).toFloat() * scale, r.getDouble(1).toFloat() * scale,
                        r.getDouble(2).toFloat() * scale, r.getDouble(3).toFloat() * scale,
                    ),
                )
            }

            val oriented = service.detect(bitmap)
            val expectedTurn = (360 - e.getInt("rotatedCw")) % 360
            if (oriented.rotationCw != expectedTurn) {
                failures += "$file: turned ${oriented.rotationCw}°, expected $expectedTurn°"
                continue
            }

            // Map detections back into the fixture's own orientation to compare with truth.
            val back = (360 - oriented.rotationCw) % 360
            val found = oriented.result.fields.map { f ->
                var rect = f.rect
                var h = oriented.bitmap.height
                var w = oriented.bitmap.width
                repeat(back / 90) {
                    rect = MaskGeometry.rotateClockwise(rect, h)
                    val t = w; w = h; h = t
                }
                f.copy(rect = rect)
            }

            for (t in truths) {
                val best = found.filter { it.type == t.type }.maxOfOrNull { it.rect.iou(t.rect) } ?: 0f
                if (best < 0.5f) failures += "$file: ${t.type} best IoU %.2f".format(best)
            }
            for (type in listOf(FieldType.AADHAAR_NUMBER, FieldType.AADHAAR_VID, FieldType.PAN, FieldType.QR)) {
                if (truths.any { it.type == type } && oriented.result.masks.none { it.source == type && it.enabled }) {
                    failures += "$file: $type not masked by default"
                }
            }
            for (type in listOf(FieldType.DOB, FieldType.ADDRESS)) {
                if (oriented.result.masks.any { it.source == type && it.enabled }) failures += "$file: $type on by default"
            }
            if (e.getString("docType") != oriented.result.docType.name) {
                failures += "$file: classified ${oriented.result.docType}, expected ${e.getString("docType")}"
            }
        }

        assertWithMessage(failures.joinToString("\n", prefix = "${failures.size} problems:\n")).that(failures).isEmpty()
    }
}
