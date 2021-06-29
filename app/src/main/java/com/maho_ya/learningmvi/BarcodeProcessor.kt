package com.maho_ya.learningmvi

import com.google.mlkit.vision.barcode.Barcode

class BarcodeProcessor {

    fun getUrl(barcode: Barcode) : String? {
        return barcode.url?.url?.toString()
    }

}
