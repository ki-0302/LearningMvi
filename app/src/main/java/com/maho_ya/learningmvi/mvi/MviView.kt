package com.maho_ya.learningmvi.mvi

// View -> User -> Intent
interface MviView<S: MviState> {
    fun render(state: S)
}
