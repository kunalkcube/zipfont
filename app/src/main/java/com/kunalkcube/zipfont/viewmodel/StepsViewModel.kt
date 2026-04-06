package com.kunalkcube.zipfont.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExploitStepsViewModel : ViewModel() {

    private val _completedStepIds = MutableStateFlow<Set<Int>>(emptySet())
    val completedStepIds: StateFlow<Set<Int>> = _completedStepIds.asStateFlow()

    fun markCompleted(stepId: Int) {
        _completedStepIds.value = _completedStepIds.value + stepId
    }

    fun toggleCompleted(stepId: Int) {
        _completedStepIds.value = if (_completedStepIds.value.contains(stepId)) {
            _completedStepIds.value - stepId
        } else {
            _completedStepIds.value + stepId
        }
    }

    fun resetAll() {
        _completedStepIds.value = emptySet()
    }
}
