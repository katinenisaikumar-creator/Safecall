package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.BackendSyncRepository
import com.example.data.ParentMessage
import com.example.data.SafeCallPreferences
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeCallMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val prefs = SafeCallPreferences(applicationContext)
        prefs.updateFcmToken(token)
        serviceScope.launch {
            BackendSyncRepository(prefs).syncStatusToBackend()
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isEmpty()) return

        val id = data["id"] ?: data["callSid"] ?: "fcm_${System.currentTimeMillis()}"
        val callerDisplay = data["callerDisplay"]?.takeIf { it.isNotBlank() } ?: "Parent"
        val callerNumber = data["callerNumber"].orEmpty()
        val transcript = data["transcript"]?.takeIf { it.isNotBlank() }
            ?: remoteMessage.notification?.body
            ?: "Recorded voice message received"
        val recordingUrl = data["recordingUrl"]?.takeIf { it.isNotBlank() }
        val durationSeconds = data["durationSeconds"]?.toIntOrNull() ?: 0
        val studentStatus = data["studentStatusAtCall"]?.takeIf { it.isNotBlank() }
            ?: SafeCallPreferences(applicationContext).getEffectiveStatus()
        val timestampMillis = data["timestampMillis"]?.toLongOrNull() ?: System.currentTimeMillis()

        val message = ParentMessage(
            id = id,
            callerDisplay = callerDisplay,
            callerNumber = callerNumber,
            messageText = transcript,
            studentStatusAtCall = studentStatus,
            timestampMillis = timestampMillis,
            recordingUrl = recordingUrl,
            durationSeconds = durationSeconds,
            isSimulated = false
        )

        val prefs = SafeCallPreferences(applicationContext)
        prefs.addMessage(message)
        showMessageNotification(message)
    }

    private fun showMessageNotification(message: ParentMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                getString(R.string.notif_channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
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

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Voice message from ${message.callerDisplay}")
            .setContentText(message.messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.messageText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(message.id.hashCode(), notification)
    }

    companion object {
        private const val TAG = "SafeCallFcmService"
        private const val CHANNEL_MESSAGES_ID = "safecall_messages_channel"
    }
}
