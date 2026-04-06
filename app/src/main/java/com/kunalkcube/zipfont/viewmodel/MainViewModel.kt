package com.kunalkcube.zipfont.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kunalkcube.zipfont.processor.ApkProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _generatedApk = MutableStateFlow<File?>(null)
    val generatedApk: StateFlow<File?> = _generatedApk.asStateFlow()

    fun selectFont(uri: Uri) {
        viewModelScope.launch {
            _generatedApk.value = null
            _state.value = UiState.Processing(ApkProcessor.ProcessState.ExtractingSkeleton)

            val result = ApkProcessor.processFont(
                context = getApplication(),
                fontUri = uri,
                onStateChange = { processState ->
                    _state.value = when (processState) {
                        is ApkProcessor.ProcessState.Error -> UiState.Error(processState.message)
                        ApkProcessor.ProcessState.Ready -> UiState.Processing(processState)
                        else -> UiState.Processing(processState)
                    }
                }
            )

            if (result != null) {
                _generatedApk.value = result
                _state.value = UiState.Ready
            } else if (_state.value !is UiState.Error) {
                _state.value = UiState.Error("Failed to generate APK")
            }
        }
    }

    fun reset() {
        _state.value = UiState.Idle
        _generatedApk.value = null
    }

    sealed class UiState {
        object Idle : UiState()
        data class Processing(val processState: ApkProcessor.ProcessState) : UiState()
        object Ready : UiState()
        data class Error(val message: String) : UiState()
    }
}
