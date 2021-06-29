package com.maho_ya.learningmvi

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.Barcode
import com.maho_ya.learningmvi.mvi.MviModel
import dagger.assisted.Assisted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val barcodeProcessor: BarcodeProcessor
) : ViewModel(), MviModel<BarcodeState, BarcodeIntent> {

    // 容量無制限のバッファ
    override val intents: Channel<BarcodeIntent> = Channel(Channel.UNLIMITED)

    private val _state = MutableLiveData(BarcodeState()) //.apply { value = UserState() }
    override val state: LiveData<BarcodeState> = _state

    init {
        handleIntent()
    }

    private fun handleIntent() {
        viewModelScope.launch {
            // consumeAsFlow: フローに変換して消費する. collect: flowを収集し、ラムダで指定したアクションを実行
            intents.consumeAsFlow().collect { barcodeIntent ->
                // Viewから渡されたIntentで指定された処理を実行する
                when (barcodeIntent) {
                    is BarcodeIntent.Idle -> return@collect
                    is BarcodeIntent.ScanBarcode -> scanBarcode(barcodeIntent.barcode)
                }
            }
        }
    }

    private fun scanBarcode(barcode: Barcode) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // updateState内で渡された値（state.value）をラムダで実行する

                // ローディングを有効
                updateState { it.copy(isLoading = true) }
                // ローディングを無効。データ取得を実行する。実行後にstateが更新される
                updateState { it.copy(isLoading = false, url = barcodeProcessor.getUrl(barcode)) }
            } catch (e: Exception) {
                updateState { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private suspend fun updateState(handler: suspend (userState: BarcodeState) -> BarcodeState) {
        _state.postValue(handler(state.value!!))
    }
}
