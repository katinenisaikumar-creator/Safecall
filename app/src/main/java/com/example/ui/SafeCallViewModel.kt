package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.AutoAnswerService
import com.example.data.ParentMessage
import com.example.data.SafeCallMode
import com.example.data.SafeCallPreferences
import com.example.data.VoiceLanguageOption
import com.example.service.AutoAnswerUiState
import com.example.service.CallPipelineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    val isCollegeModeOn: StateFlow<Boolean> = prefs.collegeModeFlow
    val studentStatus: StateFlow<String> = prefs.studentStatusFlow
    val modes: StateFlow<List<SafeCallMode>> = prefs.modesFlow
    val selectedModeId: StateFlow<String> = prefs.selectedModeIdFlow
    val activeVoiceLanguage: StateFlow<VoiceLanguageOption> = prefs.activeVoiceLanguageFlow
    val messages: StateFlow<List<ParentMessage>> = prefs.messagesFlow
    val pipelineState: StateFlow<AutoAnswerUiState> = CallPipelineState.state

    private val _systemHealth = MutableStateFlow(computeSystemHealth())
    val systemHealth: StateFlow<SystemHealthState> = _systemHealth.asStateFlow()

    init {
        // Ensure background monitor stays synced with persisted preference
        if (prefs.isCollegeModeOn) {
            startMonitorService()
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
        val newValue = !prefs.isCollegeModeOn
        prefs.isCollegeModeOn = newValue
        if (newValue) {
            startMonitorService()
        } else {
            stopMonitorService()
        }
    }

    fun setCollegeMode(enabled: Boolean) {
        if (prefs.isCollegeModeOn == enabled) return
        prefs.isCollegeModeOn = enabled
        if (enabled) {
            startMonitorService()
        } else {
            stopMonitorService()
        }
    }

    fun selectMode(modeId: String) {
        prefs.selectMode(modeId)
        if (prefs.isCollegeModeOn) {
            startMonitorService()
        }
    }

    fun addCustomMode(name: String, defaultStatus: String, iconKey: String) {
        prefs.addCustomMode(name = name, defaultStatus = defaultStatus, iconKey = iconKey)
        if (prefs.isCollegeModeOn) {
            startMonitorService()
        }
    }

    fun deleteCustomMode(modeId: String) {
        prefs.deleteCustomMode(modeId)
        if (prefs.isCollegeModeOn) {
            startMonitorService()
        }
    }

    fun updateStudentStatus(newStatus: String) {
        prefs.studentStatus = newStatus
        if (prefs.isCollegeModeOn) {
            startMonitorService()
        }
    }

    fun selectLanguage(languageId: String) {
        prefs.selectLanguage(languageId)
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
        prefs.deleteMessage(messageId)
    }

    fun clearAllMessages() {
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
                label = "Call Handling (CALL_PHONE)",
                description = "Required by Android telephony call controls",
                isGranted = isGranted(Manifest.permission.CALL_PHONE)
            ),
            PermissionItemState(
                permission = Manifest.permission.RECORD_AUDIO,
                label = "Microphone (RECORD_AUDIO)",
                description = "Transcribes parent's voice message for 6 seconds",
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
                    description = "Lets SafeCall lift the call after 3s and hang up",
                    isGranted = isGranted(Manifest.permission.ANSWER_PHONE_CALLS)
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                PermissionItemState(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    label = "Notifications (POST_NOTIFICATIONS)",
                    description = "Alerts you when a new parent message is saved",
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
        prefs.unregisterListener()
        super.onCleared()
    }
}
