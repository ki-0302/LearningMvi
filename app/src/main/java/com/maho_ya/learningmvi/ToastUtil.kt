package com.maho_ya.learningmvi

import android.content.Context
import android.widget.Toast

class ToastUtil {

    companion object {
        fun show(context: Context, text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
            Toast.makeText(
                context,
                text,
                duration
            ).show()
        }
    }
}
