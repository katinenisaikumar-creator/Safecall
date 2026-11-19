package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AutoAnswerUiState {
    data object Idle : AutoAnswerUiState

    data class RingingCountdown(
        val callerDisplay: String,
        val secondsRemaining: Int,
        val isSimulated: Boolean
    ) : AutoAnswerUiState

    data class SpeakingGreeting(
        val callerDisplay: String,
        val spokenText: String,
        val isSimulated: Boolean
    ) : AutoAnswerUiState

    data class ListeningForMessage(
        val callerDisplay: String,
        val secondsRemaining: Int,
        val partialTranscript: String,
        val isSimulated: Boolean
    ) : AutoAnswerUiState

    data class SpeakingClosing(
        val callerDisplay: String,
        val capturedMessage: String,
        val isSimulated: Boolean
    ) : AutoAnswerUiState

    data class Completed(
        val callerDisplay: String,
        val capturedMessage: String,
        val isSimulated: Boolean
    ) : AutoAnswerUiState
}

object CallPipelineState {
    private val _state = MutableStateFlow<AutoAnswerUiState>(AutoAnswerUiState.Idle)
    val state: StateFlow<AutoAnswerUiState> = _state.asStateFlow()

    fun update(newState: AutoAnswerUiState) {
        _state.value = newState
    }

    fun reset() {
        _state.value = AutoAnswerUiState.Idle
    }
}
