package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.ParentMessage
import com.example.data.SafeCallPreferences
import com.example.service.AutoAnswerUiState
import com.example.service.CallPipelineState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class AutoAnswerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: SafeCallPreferences

    private var activeCallJob: Job? = null
    private var currentCallerNumber: String? = null
    private var textToSpeech: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        prefs = SafeCallPreferences(applicationContext)
        createNotificationChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_MONITORING -> {
                promoteToForeground(buildMonitorNotification())
            }

            ACTION_STOP_MONITORING -> {
                activeCallJob?.cancel()
                isHandlingRealCall = false
                CallPipelineState.reset()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_CALL_ENDED_EXTERNALLY -> {
                if (isHandlingRealCall) {
                    Log.d(TAG, "Caller hung up before auto-answer completed")
                    activeCallJob?.cancel()
                    isHandlingRealCall = false
                    CallPipelineState.reset()
                    refreshMonitorOrStop()
                }
            }

            ACTION_CANCEL_ACTIVE_FLOW -> {
                activeCallJob?.cancel()
                isHandlingRealCall = false
                CallPipelineState.reset()
                refreshMonitorOrStop()
            }

            ACTION_HANDLE_INCOMING_CALL -> {
                val incomingNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER)
                if (!incomingNumber.isNullOrBlank()) {
                    currentCallerNumber = incomingNumber
                }

                // Deduplicate multiple PHONE_STATE_RINGING broadcasts for the same call
                if (activeCallJob?.isActive == true) {
                    return START_STICKY
                }

                if (!prefs.isCollegeModeOn) {
                    refreshMonitorOrStop()
                    return START_NOT_STICKY
                }

                promoteToForeground(buildMonitorNotification())
                isHandlingRealCall = true
                activeCallJob = serviceScope.launch {
                    try {
                        runAutoAnswerSequence(
                            callerName = null,
                            callerNumber = currentCallerNumber ?: "Incoming Caller",
                            isSimulated = false
                        )
                    } finally {
                        isHandlingRealCall = false
                        currentCallerNumber = null
                        refreshMonitorOrStop()
                    }
                }
            }

            ACTION_SIMULATE_FAKE_CALL -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Amma (Test Call)"
                val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "+91 98765 43210"

                activeCallJob?.cancel()
                promoteToForeground(buildMonitorNotification(isTesting = true))
                activeCallJob = serviceScope.launch {
                    try {
                        runAutoAnswerSequence(
                            callerName = callerName,
                            callerNumber = callerNumber,
                            isSimulated = true
                        )
                    } finally {
                        refreshMonitorOrStop()
                    }
                }
            }

            else -> {
                if (prefs.isCollegeModeOn) {
                    promoteToForeground(buildMonitorNotification())
                } else {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        return if (prefs.isCollegeModeOn) START_STICKY else START_NOT_STICKY
    }

    private suspend fun runAutoAnswerSequence(
        callerName: String?,
        callerNumber: String,
        isSimulated: Boolean
    ) {
        val displayCaller = callerName?.takeIf { it.isNotBlank() }
            ?: currentCallerNumber?.takeIf { it.isNotBlank() }
            ?: callerNumber

        // Step 1: Wait 3 seconds while ringing before auto-answering
        for (secondsLeft in RING_DELAY_SECONDS downTo 1) {
            if (!isSimulated && !prefs.isCollegeModeOn) {
                CallPipelineState.reset()
                return
            }
            val latestDisplay = callerName ?: currentCallerNumber ?: displayCaller
            CallPipelineState.update(
                AutoAnswerUiState.RingingCountdown(
                    callerDisplay = latestDisplay,
                    secondsRemaining = secondsLeft,
                    isSimulated = isSimulated
                )
            )
            delay(1000L)
        }

        val finalCallerDisplay = callerName ?: currentCallerNumber ?: displayCaller
        val resolvedNumber = currentCallerNumber ?: callerNumber

        // Step 2: Lift the call using TelecomManager (only for real incoming calls)
        if (!isSimulated) {
            val answered = acceptIncomingCallViaTelecom()
            if (!answered) {
                Log.w(TAG, "Could not accept call via TelecomManager (missing permission or API < 26)")
            }
            // Brief settle delay so audio route opens after answering
            delay(700L)
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val previousAudioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        val previousSpeaker = audioManager?.isSpeakerphoneOn ?: false

        try {
            if (!isSimulated && audioManager != null) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
            }

            val studentStatus = prefs.getEffectiveStatus()
            val greetingPrompt =
                "Hello, he is currently in $studentStatus. He is busy. " +
                    "Please tell your message after beep, I will note and send to him."

            // Step 3: Speak greeting via TextToSpeech
            CallPipelineState.update(
                AutoAnswerUiState.SpeakingGreeting(
                    callerDisplay = finalCallerDisplay,
                    spokenText = greetingPrompt,
                    isSimulated = isSimulated
                )
            )
            speakWithTts(greetingPrompt)

            // Step 4: Play beep cue before listening
            playBeepCue()

            // Step 5: Listen for 6 seconds using SpeechRecognizer
            val capturedTranscript = listenForSixSeconds(
                callerDisplay = finalCallerDisplay,
                isSimulated = isSimulated
            )

            val savedText = capturedTranscript.ifBlank {
                "No voice message detected (caller remained silent or speech service unavailable)."
            }

            // Step 6: Save parent message to SharedPreferences
            val newMessage = ParentMessage(
                callerDisplay = finalCallerDisplay,
                callerNumber = resolvedNumber,
                messageText = savedText,
                studentStatusAtCall = studentStatus,
                timestampMillis = System.currentTimeMillis(),
                isSimulated = isSimulated
            )
            prefs.addMessage(newMessage)
            postNewMessageNotification(newMessage)

            // Step 7: Speak closing confirmation
            val closingPrompt = "Thank you, message noted. He will call you back when free."
            CallPipelineState.update(
                AutoAnswerUiState.SpeakingClosing(
                    callerDisplay = finalCallerDisplay,
                    capturedMessage = savedText,
                    isSimulated = isSimulated
                )
            )
            speakWithTts(closingPrompt)

            // Step 8: Cut the call using TelecomManager (only for real incoming calls)
            if (!isSimulated) {
                endCallViaTelecom()
            }

            CallPipelineState.update(
                AutoAnswerUiState.Completed(
                    callerDisplay = finalCallerDisplay,
                    capturedMessage = savedText,
                    isSimulated = isSimulated
                )
            )
            delay(3500L)
            CallPipelineState.reset()
        } finally {
            if (!isSimulated && audioManager != null) {
                runCatching {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = previousSpeaker
                    audioManager.mode = previousAudioMode
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun acceptIncomingCallViaTelecom(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            telecomManager?.acceptRingingCall()
            true
        }.getOrElse { e ->
            Log.e(TAG, "acceptRingingCall failed: ${e.message}", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun endCallViaTelecom(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            telecomManager?.endCall() ?: false
        }.getOrElse { e ->
            Log.e(TAG, "endCall failed: ${e.message}", e)
            false
        }
    }

    private suspend fun speakWithTts(text: String) {
        val tts = ensureTtsInitialized()
        if (tts == null) {
            Log.w(TAG, "TTS unavailable, falling back to brief pause")
            delay(2200L)
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        withTimeoutOrNull(14_000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit

                    override fun onDone(id: String?) {
                        if (id == utteranceId && cont.isActive) {
                            cont.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId && cont.isActive) {
                            cont.resume(Unit)
                        }
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId && cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                })

                val params = Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                }
                val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (result != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resume(Unit)
                }
            }
        }
    }

    private suspend fun ensureTtsInitialized(): TextToSpeech? {
        textToSpeech?.let { return it }

        return withTimeoutOrNull(4_000L) {
            suspendCancellableCoroutine { cont ->
                var instance: TextToSpeech? = null
                instance = TextToSpeech(applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val indianEnglish = Locale("en", "IN")
                        val langResult = instance?.setLanguage(indianEnglish)
                        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                            langResult == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            instance?.setLanguage(Locale.US)
                        }
                        instance?.setSpeechRate(0.95f)
                        instance?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        textToSpeech = instance
                        if (cont.isActive) cont.resume(instance)
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                cont.invokeOnCancellation {
                    runCatching { instance?.stop() }
                }
            }
        }
    }

    private suspend fun playBeepCue() {
        var toneGen: ToneGenerator? = null
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 95)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            delay(380L)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator failed: ${e.message}")
        } finally {
            runCatching { toneGen?.release() }
        }
    }

    private suspend fun listenForSixSeconds(
        callerDisplay: String,
        isSimulated: Boolean
    ): String {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            for (sec in LISTEN_DURATION_SECONDS downTo 1) {
                CallPipelineState.update(
                    AutoAnswerUiState.ListeningForMessage(
                        callerDisplay = callerDisplay,
                        secondsRemaining = sec,
                        partialTranscript = "Microphone permission required for voice transcription.",
                        isSimulated = isSimulated
                    )
                )
                delay(1000L)
            }
            return ""
        }

        val recognitionAvailable = withContext(Dispatchers.Main) {
            SpeechRecognizer.isRecognitionAvailable(applicationContext)
        }

        if (!recognitionAvailable) {
            for (sec in LISTEN_DURATION_SECONDS downTo 1) {
                CallPipelineState.update(
                    AutoAnswerUiState.ListeningForMessage(
                        callerDisplay = callerDisplay,
                        secondsRemaining = sec,
                        partialTranscript = "Speech recognition service unavailable on device.",
                        isSimulated = isSimulated
                    )
                )
                delay(1000L)
            }
            return ""
        }

        var latestTranscript = ""
        val finalResultDeferred = CompletableDeferred<String>()
        var speechRecognizer: SpeechRecognizer? = null

        val countdownJob = serviceScope.launch {
            for (sec in LISTEN_DURATION_SECONDS downTo 1) {
                CallPipelineState.update(
                    AutoAnswerUiState.ListeningForMessage(
                        callerDisplay = callerDisplay,
                        secondsRemaining = sec,
                        partialTranscript = latestTranscript,
                        isSimulated = isSimulated
                    )
                )
                delay(1000L)
            }
        }

        try {
            withContext(Dispatchers.Main) {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotBlank()) {
                            latestTranscript = partial
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val finalText = matches?.firstOrNull()?.trim().orEmpty()
                        val resolved = finalText.ifBlank { latestTranscript }
                        latestTranscript = resolved
                        if (!finalResultDeferred.isCompleted) {
                            finalResultDeferred.complete(resolved)
                        }
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "SpeechRecognizer onError code=$error")
                        if (!finalResultDeferred.isCompleted) {
                            finalResultDeferred.complete(latestTranscript)
                        }
                    }
                })

                val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        LISTEN_DURATION_SECONDS * 1000L
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        LISTEN_DURATION_SECONDS * 1000L
                    )
                }

                recognizer.startListening(recognizerIntent)
            }

            // Wait up to 6 seconds for full window or early result
            val timedResult = withTimeoutOrNull(LISTEN_DURATION_SECONDS * 1000L) {
                finalResultDeferred.await()
            }

            if (timedResult == null) {
                withContext(Dispatchers.Main) {
                    runCatching { speechRecognizer?.stopListening() }
                }
                withTimeoutOrNull(1_000L) {
                    finalResultDeferred.await()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer exception: ${e.message}", e)
        } finally {
            countdownJob.cancel()
            withContext(Dispatchers.Main) {
                runCatching {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                }
            }
        }

        return latestTranscript.trim()
    }

    private fun refreshMonitorOrStop() {
        if (prefs.isCollegeModeOn) {
            promoteToForeground(buildMonitorNotification())
        } else {
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(MONITOR_NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildMonitorNotification(isTesting: Boolean = false): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = prefs.getEffectiveStatus()
        val title = if (isTesting) {
            "SafeCall • Running Fake Call Test"
        } else {
            "SafeCall • College Mode Active"
        }
        val body = "Status: $statusText — Auto-answering calls in 3s"

        return NotificationCompat.Builder(this, CHANNEL_MONITOR_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun postNewMessageNotification(message: ParentMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefix = if (message.isSimulated) "[Test] " else ""
        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${prefix}Message from ${message.callerDisplay}")
            .setContentText(message.messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.messageText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(message.id.hashCode(), notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR_ID,
            getString(R.string.notif_channel_monitor_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_monitor_desc)
            setShowBadge(false)
        }

        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES_ID,
            getString(R.string.notif_channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_channel_messages_desc)
        }

        nm.createNotificationChannel(monitorChannel)
        nm.createNotificationChannel(messageChannel)
    }

    override fun onDestroy() {
        activeCallJob?.cancel()
        isHandlingRealCall = false
        runCatching {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        textToSpeech = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SafeCallService"

        const val ACTION_START_MONITORING = "com.example.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.action.STOP_MONITORING"
        const val ACTION_HANDLE_INCOMING_CALL = "com.example.action.HANDLE_INCOMING_CALL"
        const val ACTION_SIMULATE_FAKE_CALL = "com.example.action.SIMULATE_FAKE_CALL"
        const val ACTION_CALL_ENDED_EXTERNALLY = "com.example.action.CALL_ENDED_EXTERNALLY"
        const val ACTION_CANCEL_ACTIVE_FLOW = "com.example.action.CANCEL_ACTIVE_FLOW"

        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_NAME = "extra_caller_name"

        private const val CHANNEL_MONITOR_ID = "safecall_monitor_channel"
        private const val CHANNEL_MESSAGES_ID = "safecall_messages_channel"
        private const val MONITOR_NOTIFICATION_ID = 1001

        private const val RING_DELAY_SECONDS = 3
        private const val LISTEN_DURATION_SECONDS = 6

        @Volatile
        var isHandlingRealCall: Boolean = false
            private set
    }
}
