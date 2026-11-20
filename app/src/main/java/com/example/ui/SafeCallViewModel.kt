package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoAnswerService
import com.example.audio.AudioPlaybackState
import com.example.audio.VoiceRecordingPlayer
import com.example.data.BackendSyncRepository
import com.example.data.CloudVoiceConfig
import com.example.data.ParentMessage
import com.example.data.SafeCallMode
import com.example.data.SafeCallPreferences
import com.example.data.VoiceLanguageOption
import com.example.service.AutoAnswerUiState
import com.example.service.CallPipelineState
import com.example.telephony.CallForwardingManager
import com.example.telephony.ForwardingMode
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionItemState(
    val permission: String,
    val label: String,
    val description: String,
    val isGranted: Boolean
)

data class SystemHealthState(
    val permissions: List<PermissionItemState> = emptyList(),
    val isBatteryUnrestricted: Boolean = false
) {
    val allPermissionsGranted: Boolean
        get() = permissions.all { it.isGranted }

    val missingPermissionsCount: Int
        get() = permissions.count { !it.isGranted }

    val isFullyReady: Boolean
        get() = allPermissionsGranted && isBatteryUnrestricted
}

class SafeCallViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = SafeCallPreferences(appContext)
    private val backendRepo = BackendSyncRepository(prefs)
    private val audioPlayer = VoiceRecordingPlayer(viewModelScope)

    val isCollegeModeOn: StateFlow<Boolean> = prefs.collegeModeFlow
    val studentStatus: StateFlow<String> = prefs.studentStatusFlow
    val modes: StateFlow<List<SafeCallMode>> = prefs.modesFlow
    val selectedModeId: StateFlow<String> = prefs.selectedModeIdFlow
    val activeVoiceLanguage: StateFlow<VoiceLanguageOption> = prefs.activeVoiceLanguageFlow
    val cloudConfig: StateFlow<CloudVoiceConfig> = prefs.cloudConfigFlow
    val messages: StateFlow<List<ParentMessage>> = prefs.messagesFlow
    val pipelineState: StateFlow<AutoAnswerUiState> = CallPipelineState.state
    val playbackState: StateFlow<AudioPlaybackState> = audioPlayer.state

    private val _systemHealth = MutableStateFlow(computeSystemHealth())
    val systemHealth: StateFlow<SystemHealthState> = _systemHealth.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow<String?>(null)
    val cloudSyncStatus: StateFlow<String?> = _cloudSyncStatus.asStateFlow()

    private var statusSyncDebounceJob: Job? = null

    init {
        if (prefs.isCollegeModeOn && !prefs.getCloudConfig().isForwardingReady) {
            startMonitorService()
        }
        fetchFcmTokenSafely()
        if (prefs.getCloudConfig().backendBaseUrl.isNotBlank()) {
            syncStatusIfConfigured()
            refreshCloudInbox(silent = true)
        }
    }

    private fun fetchFcmTokenSafely() {
        runCatching {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        prefs.updateFcmToken(token)
                        syncStatusIfConfigured()
                    }
                }
            }
        }
    }

    fun refreshSystemHealth() {
        _systemHealth.value = computeSystemHealth()
    }

    fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            list.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    fun toggleCollegeMode() {
        setCollegeMode(!prefs.isCollegeModeOn)
    }

    fun setCollegeMode(enabled: Boolean) {
        if (prefs.isCollegeModeOn == enabled) return
        prefs.isCollegeModeOn = enabled
        val config = prefs.getCloudConfig()

        if (config.isForwardingReady) {
            stopMonitorService()
            val forwardResult = if (enabled) {
                CallForwardingManager.activateForwarding(
                    context = appContext,
                    twilioNumber = config.twilioNumber,
                    mode = config.forwardingMode
                )
            } else {
                CallForwardingManager.deactivateForwarding(
                    context = appContext,
                    mode = config.forwardingMode
                )
            }
            forwardResult.onSuccess { code ->
                _cloudSyncStatus.value = if (enabled) {
                    "Dialing $code to forward calls to Twilio..."
                } else {
                    "Dialing $code to disable call forwarding..."
                }
            }.onFailure { err ->
                _cloudSyncStatus.value = err.message ?: "Unable to launch USSD forwarding code"
            }
        } else {
            if (enabled) {
                startMonitorService()
            } else {
                stopMonitorService()
            }
        }

        syncStatusIfConfigured()
    }

    fun selectMode(modeId: String) {
        prefs.selectMode(modeId)
        if (prefs.isCollegeModeOn && !prefs.getCloudConfig().isForwardingReady) {
            startMonitorService()
        }
        syncStatusIfConfigured()
    }

    fun addCustomMode(name: String, defaultStatus: String, iconKey: String) {
        prefs.addCustomMode(name = name, defaultStatus = defaultStatus, iconKey = iconKey)
        if (prefs.isCollegeModeOn && !prefs.getCloudConfig().isForwardingReady) {
            startMonitorService()
        }
        syncStatusIfConfigured()
    }

    fun deleteCustomMode(modeId: String) {
        prefs.deleteCustomMode(modeId)
        if (prefs.isCollegeModeOn && !prefs.getCloudConfig().isForwardingReady) {
            startMonitorService()
        }
        syncStatusIfConfigured()
    }

    fun updateStudentStatus(newStatus: String) {
        prefs.studentStatus = newStatus
        if (prefs.isCollegeModeOn && !prefs.getCloudConfig().isForwardingReady) {
            startMonitorService()
        }
        statusSyncDebounceJob?.cancel()
        statusSyncDebounceJob = viewModelScope.launch {
            delay(500L)
            syncStatusIfConfigured()
        }
    }

    fun selectLanguage(languageId: String) {
        prefs.selectLanguage(languageId)
        syncStatusIfConfigured()
    }

    fun saveCustomVoiceMessage(
        greetingTemplate: String,
        closingMessage: String,
        localeTag: String
    ) {
        prefs.saveCustomVoiceMessage(
            greetingTemplate = greetingTemplate,
            closingMessage = closingMessage,
            localeTag = localeTag
        )
        syncStatusIfConfigured()
    }

    fun saveCloudConfig(
        backendBaseUrl: String,
        twilioNumber: String,
        forwardingMode: ForwardingMode,
        useCloudForwarding: Boolean
    ) {
        prefs.saveCloudConfig(
            backendBaseUrl = backendBaseUrl,
            twilioNumber = twilioNumber,
            forwardingMode = forwardingMode,
            useCloudForwarding = useCloudForwarding
        )
        if (useCloudForwarding) {
            stopMonitorService()
        } else if (prefs.isCollegeModeOn) {
            startMonitorService()
        }
        fetchFcmTokenSafely()
        if (backendBaseUrl.trim().isNotEmpty()) {
            syncCloudStatusNow()
        } else {
            _cloudSyncStatus.value = "Cloud config saved"
        }
    }

    fun activateForwardingNow() {
        val config = prefs.getCloudConfig()
        CallForwardingManager.activateForwarding(
            context = appContext,
            twilioNumber = config.twilioNumber,
            mode = config.forwardingMode
        ).onSuccess { code ->
            _cloudSyncStatus.value = "Dialed $code for ${config.forwardingMode.label}"
        }.onFailure { err ->
            _cloudSyncStatus.value = err.message ?: "Enter your Twilio number first"
        }
    }

    fun deactivateAllForwardingNow() {
        CallForwardingManager.deactivateAllForwarding(appContext)
            .onSuccess { code ->
                _cloudSyncStatus.value = "Dialed $code to clear all carrier forwarding"
            }
            .onFailure { err ->
                _cloudSyncStatus.value = err.message ?: "Unable to dial ##002#"
            }
    }

    fun syncCloudStatusNow() {
        viewModelScope.launch {
            _cloudSyncStatus.value = "Syncing status & language to cloud..."
            backendRepo.syncStatusToBackend()
                .onSuccess {
                    _cloudSyncStatus.value = "Cloud voice synced (${prefs.getEffectiveStatus()})"
                }
                .onFailure { err ->
                    _cloudSyncStatus.value = "Sync failed: ${err.message ?: "Check backend URL"}"
                }
        }
    }

    fun refreshCloudInbox(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _cloudSyncStatus.value = "Checking cloud inbox..."
            }
            backendRepo.syncStatusToBackend()
            backendRepo.fetchMessagesFromBackend()
                .onSuccess { count ->
                    if (!silent) {
                        _cloudSyncStatus.value = "Inbox synced ($count cloud messages)"
                    }
                }
                .onFailure { err ->
                    if (!silent) {
                        _cloudSyncStatus.value = "Inbox refresh failed: ${err.message ?: "Offline"}"
                    }
                }
        }
    }

    fun dismissCloudStatusBanner() {
        _cloudSyncStatus.value = null
    }

    fun toggleAudioPlayback(messageId: String, recordingUrl: String) {
        audioPlayer.togglePlayPause(messageId, recordingUrl)
    }

    fun stopAudioPlayback() {
        audioPlayer.stop()
    }

    private fun syncStatusIfConfigured() {
        if (prefs.getCloudConfig().backendBaseUrl.isBlank()) return
        viewModelScope.launch {
            backendRepo.syncStatusToBackend()
        }
    }

    fun startFakeCallTest(callerName: String, callerNumber: String) {
        val intent = Intent(appContext, AutoAnswerService::class.java).apply {
            action = AutoAnswerService.ACTION_SIMULATE_FAKE_CALL
            putExtra(AutoAnswerService.EXTRA_CALLER_NAME, callerName.ifBlank { "Amma (Test Call)" })
            putExtra(AutoAnswerService.EXTRA_CALLER_NUMBER, callerNumber.ifBlank { "+91 98765 43210" })
        }
        startServiceCompat(intent)
    }

    fun cancelActiveCallFlow() {
        val intent = Intent(appContext, AutoAnswerService::class.java).apply {
            action = AutoAnswerService.ACTION_CANCEL_ACTIVE_FLOW
        }
        runCatching { appContext.startService(intent) }
        CallPipelineState.reset()
    }

    fun deleteMessage(messageId: String) {
        if (audioPlayer.state.value.activeMessageId == messageId) {
            audioPlayer.stop()
        }
        prefs.deleteMessage(messageId)
    }

    fun clearAllMessages() {
        audioPlayer.stop()
        prefs.clearAllMessages()
    }

    private fun startMonitorService() {
        val intent = Intent(appContext, AutoAnswerService::class.java).apply {
            action = AutoAnswerService.ACTION_START_MONITORING
        }
        startServiceCompat(intent)
    }

    private fun stopMonitorService() {
        val intent = Intent(appContext, AutoAnswerService::class.java).apply {
            action = AutoAnswerService.ACTION_STOP_MONITORING
        }
        runCatching { appContext.startService(intent) }
    }

    private fun startServiceCompat(intent: Intent) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    private fun computeSystemHealth(): SystemHealthState {
        val items = mutableListOf(
            PermissionItemState(
                permission = Manifest.permission.READ_PHONE_STATE,
                label = "Phone State (READ_PHONE_STATE)",
                description = "Detects when a parent call starts ringing",
                isGranted = isGranted(Manifest.permission.READ_PHONE_STATE)
            ),
            PermissionItemState(
                permission = Manifest.permission.CALL_PHONE,
                label = "Call Forwarding & Dialing (CALL_PHONE)",
                description = "Dials carrier USSD forwarding codes (**61* / ##61#)",
                isGranted = isGranted(Manifest.permission.CALL_PHONE)
            ),
            PermissionItemState(
                permission = Manifest.permission.RECORD_AUDIO,
                label = "Microphone (RECORD_AUDIO)",
                description = "Used by offline Test Fake Call simulation",
                isGranted = isGranted(Manifest.permission.RECORD_AUDIO)
            ),
            PermissionItemState(
                permission = Manifest.permission.READ_CALL_LOG,
                label = "Caller Number (READ_CALL_LOG)",
                description = "Shows which parent number called you",
                isGranted = isGranted(Manifest.permission.READ_CALL_LOG)
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            items.add(
                1,
                PermissionItemState(
                    permission = Manifest.permission.ANSWER_PHONE_CALLS,
                    label = "Auto Answer & Cut (ANSWER_PHONE_CALLS)",
                    description = "Required only for local fallback mode",
                    isGranted = isGranted(Manifest.permission.ANSWER_PHONE_CALLS)
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                PermissionItemState(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    label = "Notifications (POST_NOTIFICATIONS)",
                    description = "Alerts you when a parent voice recording arrives via FCM",
                    isGranted = isGranted(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true

        return SystemHealthState(
            permissions = items,
            isBatteryUnrestricted = batteryUnrestricted
        )
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        statusSyncDebounceJob?.cancel()
        audioPlayer.stop()
        prefs.unregisterListener()
        super.onCleared()
    }
}
