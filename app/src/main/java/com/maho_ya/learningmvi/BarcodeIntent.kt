package com.maho_ya.learningmvi

import com.maho_ya.learningmvi.mvi.MviIntent
import com.google.mlkit.vision.barcode.Barcode

// Intentではユーザーが行える行動を定義する
sealed class BarcodeIntent: MviIntent {
    object Idle: BarcodeIntent()
    data class ScanBarcode(val barcode: Barcode): BarcodeIntent()
}
