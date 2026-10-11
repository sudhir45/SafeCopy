package com.sudhir.safecopy.domain.mask

import com.sudhir.safecopy.domain.detect.DetectedField
import com.sudhir.safecopy.domain.model.Confidence
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.MaskRect
import com.sudhir.safecopy.domain.model.Rect

/** Turns tight detection boxes into masks (spec 7.2): padding, clamping, merging. */
object MaskMerger {
    /** OCR boxes are tight and anti-aliased glyph edges leak, so text gets padded. */
    const val TEXT_PAD_X = 0.04f
    const val TEXT_PAD_Y = 0.15f

    /** Spec 6.10: QR padded by 8 % of its width on all sides. */
    const val QR_PAD = 0.08f

    /** Boxes of the same field closer than this (bitmap px) merge into one. */
    private const val ADJACENT_PX = 2f

    fun pad(type: FieldType, r: Rect): Rect = when (type) {
        FieldType.QR, FieldType.PHOTO -> r.inflate(r.width * QR_PAD, r.width * QR_PAD)
        else -> r.inflate(r.width * TEXT_PAD_X, r.height * TEXT_PAD_Y)
    }

    fun merge(fields: List<DetectedField>, imageWidth: Int, imageHeight: Int): List<MaskRect> {
        val out = ArrayList<MaskRect>()
        for ((type, group) in fields.groupBy { it.type }) {
            val pending = group.map {
                MaskRect(it.id, MaskGeometry.clampTo(pad(type, it.rect), imageWidth, imageHeight), type, it.defaultEnabled, it.confidence)
            }.toMutableList()
            // Repeatedly merge any two overlapping or touching boxes until none are left.
            var merged = true
            while (merged) {
                merged = false
                loop@ for (a in pending.indices) for (b in a + 1 until pending.size) {
                    if (pending[a].rect.inflate(ADJACENT_PX, ADJACENT_PX).intersects(pending[b].rect)) {
                        val x = pending[a]
                        val y = pending[b]
                        pending[a] = x.copy(
                            rect = x.rect.union(y.rect),
                            enabled = x.enabled || y.enabled,
                            confidence = if (x.confidence == Confidence.LOW && y.confidence == Confidence.LOW) Confidence.LOW else Confidence.HIGH,
                        )
                        pending.removeAt(b)
                        merged = true
                        break@loop
                    }
                }
            }
            out += pending
        }
        return out
    }
}
