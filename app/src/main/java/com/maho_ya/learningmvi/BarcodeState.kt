package com.maho_ya.learningmvi

import com.google.mlkit.vision.barcode.Barcode
import com.maho_ya.learningmvi.mvi.MviState

data class BarcodeState(
    val url: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
): MviState
