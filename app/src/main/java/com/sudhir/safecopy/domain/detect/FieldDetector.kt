package com.sudhir.safecopy.domain.detect

import com.sudhir.safecopy.domain.mask.MaskMerger
import com.sudhir.safecopy.domain.model.Confidence
import com.sudhir.safecopy.domain.model.FieldType
import com.sudhir.safecopy.domain.model.Rect

/**
 * Runs every detector over one OCR result plus QR boxes (spec 6.1 pipeline, pure part).
 * The ML Kit calls and the orientation search live in ml/DetectionService.
 */
object FieldDetector {
    /** Spec 6.8/6.9: DOB and address are offered but off by default. */
    private val OFF_BY_DEFAULT = setOf(FieldType.DOB, FieldType.ADDRESS, FieldType.PHOTO)

    fun detect(
        ocr: OcrResult,
        qrBoxes: List<Rect>,
        imageWidth: Int,
        imageHeight: Int,
        options: DetectionOptions = DetectionOptions(),
    ): DetectionResult {
        val lines = TextNormalizer.normalize(ocr)
        val validAadhaar = AadhaarDetector.candidates(lines).any { it.valid }
        val docType = DocumentClassifier.classify(lines, validAadhaar, PanDetector.hasStrictPan(lines))

        val matches = buildList {
            addAll(AadhaarDetector.detect(lines, docType, options.aadhaarMaskMode))
            addAll(VidDetector.detect(lines))
            addAll(PanDetector.detect(lines, docType, options.panMaskMode))
            addAll(DobDetector.detect(lines, docType))
            addAll(AddressDetector.detect(lines))
            // Spec 6.10: only the QR's box is used. Its content is never decoded or stored.
            qrBoxes.forEach { add(Match(FieldType.QR, it, Confidence.HIGH)) }
        }

        val fields = matches.mapIndexed { i, m ->
            DetectedField(
                id = "${m.type.name.lowercase()}-$i",
                type = m.type,
                rect = m.rect,
                confidence = m.confidence,
                defaultEnabled = when (m.type) {
                    in OFF_BY_DEFAULT -> false
                    FieldType.QR -> options.maskQrByDefault
                    else -> true
                },
            )
        }
        return DetectionResult(
            docType = docType,
            fields = fields,
            masks = MaskMerger.merge(fields, imageWidth, imageHeight),
            qrHint = docType == DocType.AADHAAR && qrBoxes.isEmpty(),
            textAmount = ocr.alnumCount,
        )
    }
}
