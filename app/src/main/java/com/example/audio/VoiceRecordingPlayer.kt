package com.example.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlaybackState(
    val activeMessageId: String? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val errorMessage: String? = null
)

class VoiceRecordingPlayer(
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    fun togglePlayPause(messageId: String, recordingUrl: String) {
        val current = _state.value
        if (current.activeMessageId == messageId && mediaPlayer != null) {
            if (current.isPlaying) {
                runCatching { mediaPlayer?.pause() }
                progressJob?.cancel()
                _state.value = current.copy(isPlaying = false)
            } else if (!current.isPreparing) {
                runCatching { mediaPlayer?.start() }
                _state.value = current.copy(isPlaying = true, errorMessage = null)
                startProgressLoop()
            }
            return
        }

        stop()

        _state.value = AudioPlaybackState(
            activeMessageId = messageId,
            isPreparing = true,
            isPlaying = false
        )

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnPreparedListener { mp ->
                mp.start()
                _state.value = _state.value.copy(
                    isPreparing = false,
                    isPlaying = true,
                    durationMs = mp.duration.coerceAtLeast(0),
                    currentPositionMs = 0,
                    errorMessage = null
                )
                startProgressLoop()
            }
            setOnCompletionListener {
                progressJob?.cancel()
                _state.value = AudioPlaybackState()
                releasePlayerOnly()
            }
            setOnErrorListener { _, what, extra ->
                progressJob?.cancel()
                _state.value = AudioPlaybackState(
                    activeMessageId = messageId,
                    errorMessage = "Audio playback failed (code $what/$extra)"
                )
                releasePlayerOnly()
                true
            }
        }

        mediaPlayer = player
        runCatching {
            player.setDataSource(recordingUrl)
            player.prepareAsync()
        }.onFailure { e ->
            _state.value = AudioPlaybackState(
                activeMessageId = messageId,
                errorMessage = e.message ?: "Unable to stream recording"
            )
            releasePlayerOnly()
        }
    }

    fun stop() {
        progressJob?.cancel()
        releasePlayerOnly()
        _state.value = AudioPlaybackState()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val mp = mediaPlayer ?: break
                val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                val dur = runCatching { mp.duration }.getOrDefault(_state.value.durationMs)
                _state.value = _state.value.copy(
                    currentPositionMs = pos.coerceAtLeast(0),
                    durationMs = dur.coerceAtLeast(0)
                )
                delay(250L)
            }
        }
    }

    private fun releasePlayerOnly() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }
}
