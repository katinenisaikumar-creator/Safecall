package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SafeCallPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _collegeModeFlow = MutableStateFlow(isCollegeModeOn)
    val collegeModeFlow: StateFlow<Boolean> = _collegeModeFlow.asStateFlow()

    private val _studentStatusFlow = MutableStateFlow(studentStatus)
    val studentStatusFlow: StateFlow<String> = _studentStatusFlow.asStateFlow()

    private val _messagesFlow = MutableStateFlow(getMessages())
    val messagesFlow: StateFlow<List<ParentMessage>> = _messagesFlow.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_COLLEGE_MODE -> _collegeModeFlow.value = isCollegeModeOn
            KEY_STUDENT_STATUS -> _studentStatusFlow.value = studentStatus
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

    var studentStatus: String
        get() = prefs.getString(KEY_STUDENT_STATUS, DEFAULT_STATUS) ?: DEFAULT_STATUS
        set(value) {
            prefs.edit().putString(KEY_STUDENT_STATUS, value).apply()
            _studentStatusFlow.value = value
        }

    fun getEffectiveStatus(): String {
        val current = studentStatus.trim()
        return current.ifEmpty { DEFAULT_STATUS }
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
        const val KEY_MESSAGES_JSON = "parent_messages_json"
        const val DEFAULT_STATUS = "Lab Session lo unna"
    }
}
