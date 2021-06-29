package com.maho_ya.learningmvi.mvi

import androidx.lifecycle.LiveData
import kotlinx.coroutines.channels.Channel

interface MviModel<S : MviState, I : MviIntent> {
    val intents: Channel<I>
    val state: LiveData<S>
}
