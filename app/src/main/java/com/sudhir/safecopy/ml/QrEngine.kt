package com.sudhir.safecopy.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sudhir.safecopy.domain.model.Rect

/**
 * Finds QR codes (bundled ML Kit barcode model, spec 6.10).
 *
 * Only the bounding box is read. The decoded content (rawValue/displayValue) holds full
 * demographic data on an Aadhaar QR and is never touched, stored or logged.
 */
class QrEngine {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
    )

    suspend fun findQrBoxes(bitmap: Bitmap): List<Rect> =
        scanner.process(InputImage.fromBitmap(bitmap, 0)).await().mapNotNull { it.boundingBox?.toRect() }
}
