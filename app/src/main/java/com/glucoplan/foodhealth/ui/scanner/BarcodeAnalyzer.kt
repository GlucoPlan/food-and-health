package com.glucoplan.foodhealth.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.glucoplan.foodhealth.data.product.Barcodes
import com.glucoplan.foodhealth.data.product.ScanConfirmer
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable

/**
 * Кадры камеры → ML Kit → подтверждённый товарный штрихкод.
 * QR и DataMatrix («Честный знак») не распознаются намеренно: на упаковке
 * они рядом со штрихкодом и сбивали бы сканер.
 */
class BarcodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer, Closeable {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build()
    )
    private val confirmer = ScanConfirmer()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val media = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { b ->
                    b.rawValue?.let(Barcodes::normalize)?.takeIf(Barcodes::isRetail)
                }?.let(confirmer::offer)?.let(onCode)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    override fun close() = scanner.close()
}
