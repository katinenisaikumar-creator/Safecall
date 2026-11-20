package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.telephony.ForwardingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class CloudVoiceConfig(
    val backendBaseUrl: String = "",
    val twilioNumber: String = "",
    val forwardingMode: ForwardingMode = ForwardingMode.CONDITIONAL_NO_ANSWER,
    val useCloudForwarding: Boolean = false,
    val fcmToken: String = ""
) {
    val isForwardingReady: Boolean
        get() = useCloudForwarding && twilioNumber.trim().isNotEmpty()
}

class SafeCallPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _collegeModeFlow = MutableStateFlow(isCollegeModeOn)
    val collegeModeFlow: StateFlow<Boolean> = _collegeModeFlow.asStateFlow()

    private val _studentStatusFlow = MutableStateFlow(studentStatus)
    val studentStatusFlow: StateFlow<String> = _studentStatusFlow.asStateFlow()

    private val _modesFlow = MutableStateFlow(getAllModes())
    val modesFlow: StateFlow<List<SafeCallMode>> = _modesFlow.asStateFlow()

    private val _selectedModeIdFlow = MutableStateFlow(selectedModeId)
    val selectedModeIdFlow: StateFlow<String> = _selectedModeIdFlow.asStateFlow()

    private val _activeVoiceLanguageFlow = MutableStateFlow(getActiveVoiceLanguage())
    val activeVoiceLanguageFlow: StateFlow<VoiceLanguageOption> = _activeVoiceLanguageFlow.asStateFlow()

    private val _cloudConfigFlow = MutableStateFlow(getCloudConfig())
    val cloudConfigFlow: StateFlow<CloudVoiceConfig> = _cloudConfigFlow.asStateFlow()

    private val _messagesFlow = MutableStateFlow(getMessages())
    val messagesFlow: StateFlow<List<ParentMessage>> = _messagesFlow.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_COLLEGE_MODE -> _collegeModeFlow.value = isCollegeModeOn
            KEY_STUDENT_STATUS -> _studentStatusFlow.value = studentStatus
            KEY_CUSTOM_MODES_JSON -> _modesFlow.value = getAllModes()
            KEY_SELECTED_MODE_ID -> _selectedModeIdFlow.value = selectedModeId
            KEY_SELECTED_LANGUAGE_ID,
            KEY_CUSTOM_GREETING,
            KEY_CUSTOM_CLOSING,
            KEY_CUSTOM_LOCALE_TAG -> _activeVoiceLanguageFlow.value = getActiveVoiceLanguage()
            KEY_BACKEND_URL,
            KEY_TWILIO_NUMBER,
            KEY_FORWARDING_MODE,
            KEY_USE_CLOUD_FORWARDING,
            KEY_FCM_TOKEN -> _cloudConfigFlow.value = getCloudConfig()
            KEY_MESSAGES_JSON -> _messagesFlow.value = getMessages()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    var isCollegeModeOn: Boolean
        get() = prefs.getBoolean(KEY_COLLEGE_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_COLLEGE_MODE, value).apply()
            _collegeModeFlow.value = value
        }

    var selectedModeId: String
        get() = prefs.getString(KEY_SELECTED_MODE_ID, DefaultSafeCallModes.list.first().id)
            ?: DefaultSafeCallModes.list.first().id
        private set(value) {
            prefs.edit().putString(KEY_SELECTED_MODE_ID, value).apply()
            _selectedModeIdFlow.value = value
        }

    var studentStatus: String
        get() = prefs.getString(KEY_STUDENT_STATUS, DEFAULT_STATUS) ?: DEFAULT_STATUS
        set(value) {
            prefs.edit().putString(KEY_STUDENT_STATUS, value).apply()
            _studentStatusFlow.value = value
        }

    var selectedLanguageId: String
        get() = prefs.getString(KEY_SELECTED_LANGUAGE_ID, VoiceLanguages.ID_TELUGU)
            ?: VoiceLanguages.ID_TELUGU
        private set(value) {
            prefs.edit().putString(KEY_SELECTED_LANGUAGE_ID, value).apply()
            _activeVoiceLanguageFlow.value = getActiveVoiceLanguage()
        }

    fun selectLanguage(languageId: String) {
        selectedLanguageId = languageId
    }

    fun saveCustomVoiceMessage(
        greetingTemplate: String,
        closingMessage: String,
        localeTag: String
    ) {
        val cleanGreeting = greetingTemplate.trim().ifEmpty {
            VoiceLanguages.builtInList.first().greetingTemplate
        }
        val cleanClosing = closingMessage.trim().ifEmpty {
            VoiceLanguages.builtInList.first().closingMessage
        }
        val cleanLocale = localeTag.trim().ifEmpty { "te-IN" }

        prefs.edit()
            .putString(KEY_SELECTED_LANGUAGE_ID, VoiceLanguages.ID_CUSTOM)
            .putString(KEY_CUSTOM_GREETING, cleanGreeting)
            .putString(KEY_CUSTOM_CLOSING, cleanClosing)
            .putString(KEY_CUSTOM_LOCALE_TAG, cleanLocale)
            .apply()
        _activeVoiceLanguageFlow.value = getActiveVoiceLanguage()
    }

    fun getActiveVoiceLanguage(): VoiceLanguageOption {
        val langId = selectedLanguageId
        if (langId == VoiceLanguages.ID_CUSTOM) {
            val defaultTelugu = VoiceLanguages.builtInList.first()
            val customGreeting = prefs.getString(KEY_CUSTOM_GREETING, null)
                ?.takeIf { it.isNotBlank() }
                ?: defaultTelugu.greetingTemplate
            val customClosing = prefs.getString(KEY_CUSTOM_CLOSING, null)
                ?.takeIf { it.isNotBlank() }
                ?: defaultTelugu.closingMessage
            val customLocale = prefs.getString(KEY_CUSTOM_LOCALE_TAG, null)
                ?.takeIf { it.isNotBlank() }
                ?: "te-IN"
            return VoiceLanguageOption(
                id = VoiceLanguages.ID_CUSTOM,
                displayName = "Custom Language",
                shortLabel = "Custom",
                localeTag = customLocale,
                greetingTemplate = customGreeting,
                closingMessage = customClosing
            )
        }
        return VoiceLanguages.findBuiltIn(langId) ?: VoiceLanguages.builtInList.first()
    }

    fun getAllModes(): List<SafeCallMode> {
        return DefaultSafeCallModes.list + getCustomModes()
    }

    fun getActiveMode(): SafeCallMode {
        val currentId = selectedModeId
        return getAllModes().firstOrNull { it.id == currentId }
            ?: DefaultSafeCallModes.list.first()
    }

    fun selectMode(modeId: String) {
        val mode = getAllModes().firstOrNull { it.id == modeId } ?: return
        selectedModeId = mode.id
        studentStatus = mode.defaultStatus
    }

    fun addCustomMode(
        name: String,
        defaultStatus: String,
        iconKey: String = SafeCallMode.ICON_CUSTOM
    ): SafeCallMode {
        val cleanName = name.trim().ifEmpty { "Custom Mode" }
        val cleanStatus = defaultStatus.trim().ifEmpty { cleanName }
        val newMode = SafeCallMode(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            defaultStatus = cleanStatus,
            iconKey = iconKey,
            isBuiltIn = false
        )
        val updatedCustom = getCustomModes() + newMode
        saveCustomModes(updatedCustom)
        selectedModeId = newMode.id
        studentStatus = newMode.defaultStatus
        return newMode
    }

    fun deleteCustomMode(modeId: String) {
        val updatedCustom = getCustomModes().filterNot { it.id == modeId }
        saveCustomModes(updatedCustom)
        if (selectedModeId == modeId) {
            val fallback = DefaultSafeCallModes.list.first()
            selectedModeId = fallback.id
            studentStatus = fallback.defaultStatus
        }
    }

    private fun getCustomModes(): List<SafeCallMode> {
        val rawJson = prefs.getString(KEY_CUSTOM_MODES_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        SafeCallMode(
                            id = obj.optString("id"),
                            name = obj.optString("name", "Custom Mode"),
                            defaultStatus = obj.optString("defaultStatus", DEFAULT_STATUS),
                            iconKey = obj.optString("iconKey", SafeCallMode.ICON_CUSTOM),
                            isBuiltIn = false
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveCustomModes(modes: List<SafeCallMode>) {
        val array = JSONArray()
        modes.forEach { mode ->
            array.put(
                JSONObject().apply {
                    put("id", mode.id)
                    put("name", mode.name)
                    put("defaultStatus", mode.defaultStatus)
                    put("iconKey", mode.iconKey)
                }
            )
        }
        prefs.edit().putString(KEY_CUSTOM_MODES_JSON, array.toString()).apply()
        _modesFlow.value = getAllModes()
    }

    fun getEffectiveStatus(): String {
        val current = studentStatus.trim()
        return current.ifEmpty { getActiveMode().defaultStatus }
    }

    fun getCloudConfig(): CloudVoiceConfig {
        val modeName = prefs.getString(KEY_FORWARDING_MODE, ForwardingMode.CONDITIONAL_NO_ANSWER.name)
        val mode = runCatching { ForwardingMode.valueOf(modeName ?: "") }
            .getOrDefault(ForwardingMode.CONDITIONAL_NO_ANSWER)
        return CloudVoiceConfig(
            backendBaseUrl = prefs.getString(KEY_BACKEND_URL, "").orEmpty(),
            twilioNumber = prefs.getString(KEY_TWILIO_NUMBER, "").orEmpty(),
            forwardingMode = mode,
            useCloudForwarding = prefs.getBoolean(KEY_USE_CLOUD_FORWARDING, false),
            fcmToken = prefs.getString(KEY_FCM_TOKEN, "").orEmpty()
        )
    }

    fun saveCloudConfig(
        backendBaseUrl: String,
        twilioNumber: String,
        forwardingMode: ForwardingMode,
        useCloudForwarding: Boolean
    ) {
        prefs.edit()
            .putString(KEY_BACKEND_URL, backendBaseUrl.trim())
            .putString(KEY_TWILIO_NUMBER, twilioNumber.trim())
            .putString(KEY_FORWARDING_MODE, forwardingMode.name)
            .putBoolean(KEY_USE_CLOUD_FORWARDING, useCloudForwarding)
            .apply()
        _cloudConfigFlow.value = getCloudConfig()
    }

    fun updateFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        _cloudConfigFlow.value = getCloudConfig()
    }

    fun getMessages(): List<ParentMessage> {
        val rawJson = prefs.getString(KEY_MESSAGES_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ParentMessage(
                            id = obj.optString("id"),
                            callerDisplay = obj.optString("callerDisplay", "Unknown Caller"),
                            callerNumber = obj.optString("callerNumber", "Private Number"),
                            messageText = obj.optString("messageText", ""),
                            studentStatusAtCall = obj.optString("studentStatusAtCall", DEFAULT_STATUS),
                            timestampMillis = obj.optLong("timestampMillis", 0L),
                            recordingUrl = obj.optString("recordingUrl").takeIf { it.isNotBlank() },
                            durationSeconds = obj.optInt("durationSeconds", 0),
                            isSimulated = obj.optBoolean("isSimulated", false)
                        )
                    )
                }
            }.sortedByDescending { it.timestampMillis }
        }.getOrElse { emptyList() }
    }

    fun addMessage(message: ParentMessage) {
        val updated = listOf(message) + getMessages().filterNot { it.id == message.id }
        saveMessages(updated)
    }

    fun deleteMessage(messageId: String) {
        val updated = getMessages().filterNot { it.id == messageId }
        saveMessages(updated)
    }

    fun clearAllMessages() {
        prefs.edit().remove(KEY_MESSAGES_JSON).apply()
        _messagesFlow.value = emptyList()
    }

    private fun saveMessages(messages: List<ParentMessage>) {
        val array = JSONArray()
        messages.forEach { msg ->
            array.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("callerDisplay", msg.callerDisplay)
                    put("callerNumber", msg.callerNumber)
                    put("messageText", msg.messageText)
                    put("studentStatusAtCall", msg.studentStatusAtCall)
                    put("timestampMillis", msg.timestampMillis)
                    put("recordingUrl", msg.recordingUrl.orEmpty())
                    put("durationSeconds", msg.durationSeconds)
                    put("isSimulated", msg.isSimulated)
                }
            )
        }
        prefs.edit().putString(KEY_MESSAGES_JSON, array.toString()).apply()
        _messagesFlow.value = messages
    }

    fun unregisterListener() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        const val PREFS_NAME = "safecall_prefs"
        const val KEY_COLLEGE_MODE = "college_mode_on"
        const val KEY_STUDENT_STATUS = "student_status"
        const val KEY_SELECTED_MODE_ID = "selected_mode_id"
        const val KEY_CUSTOM_MODES_JSON = "custom_modes_json"
        const val KEY_SELECTED_LANGUAGE_ID = "selected_language_id"
        const val KEY_CUSTOM_GREETING = "custom_greeting_template"
        const val KEY_CUSTOM_CLOSING = "custom_closing_message"
        const val KEY_CUSTOM_LOCALE_TAG = "custom_locale_tag"
        const val KEY_BACKEND_URL = "cloud_backend_url"
        const val KEY_TWILIO_NUMBER = "cloud_twilio_number"
        const val KEY_FORWARDING_MODE = "cloud_forwarding_mode"
        const val KEY_USE_CLOUD_FORWARDING = "cloud_use_forwarding"
        const val KEY_FCM_TOKEN = "cloud_fcm_token"
        const val KEY_MESSAGES_JSON = "parent_messages_json"
        const val DEFAULT_STATUS = "Lab Session lo unna"
    }
}
