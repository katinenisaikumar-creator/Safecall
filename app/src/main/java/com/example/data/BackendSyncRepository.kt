package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BackendSyncRepository(
    private val prefs: SafeCallPreferences
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun syncStatusToBackend(): Result<Unit> = withContext(Dispatchers.IO) {
        val config = prefs.getCloudConfig()
        val baseUrl = config.backendBaseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Backend URL not configured"))
        }

        val voiceLang = prefs.getActiveVoiceLanguage()
        val payload = JSONObject().apply {
            put("studentId", "default_student")
            put("collegeModeOn", prefs.isCollegeModeOn)
            put("status", prefs.getEffectiveStatus())
            put("languageId", voiceLang.id)
            put("localeTag", voiceLang.localeTag)
            put("customGreeting", voiceLang.greetingTemplate)
            put("customClosing", voiceLang.closingMessage)
            put("fcmToken", config.fcmToken)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/status")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }
            }
            Log.d(TAG, "Status synced to backend ($baseUrl)")
            Unit
        }
    }

    suspend fun fetchMessagesFromBackend(): Result<Int> = withContext(Dispatchers.IO) {
        val config = prefs.getCloudConfig()
        val baseUrl = config.backendBaseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Backend URL not configured"))
        }

        val request = Request.Builder()
            .url("$baseUrl/api/messages")
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val root = JSONObject(bodyStr)
                val array = root.optJSONArray("messages") ?: return@use 0

                var importedCount = 0
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isBlank()) continue
                    val msg = ParentMessage(
                        id = id,
                        callerDisplay = obj.optString("callerDisplay", "Parent"),
                        callerNumber = obj.optString("callerNumber", ""),
                        messageText = obj.optString("transcript", "Voice recording received"),
                        studentStatusAtCall = obj.optString("studentStatusAtCall", prefs.getEffectiveStatus()),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        recordingUrl = obj.optString("recordingUrl").takeIf { it.isNotBlank() },
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        isSimulated = false
                    )
                    prefs.addMessage(msg)
                    importedCount++
                }
                importedCount
            }
        }
    }

    companion object {
        private const val TAG = "BackendSyncRepo"
    }
}
