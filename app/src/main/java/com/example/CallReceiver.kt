package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.SafeCallPreferences

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = SafeCallPreferences(context)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (prefs.isCollegeModeOn && !prefs.getCloudConfig().isForwardingReady) {
                    Log.d(TAG, "Restoring SafeCall monitoring after boot/update")
                    val serviceIntent = Intent(context, AutoAnswerService::class.java).apply {
                        this.action = AutoAnswerService.ACTION_START_MONITORING
                    }
                    startForegroundCompat(context, serviceIntent)
                }
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                if (!prefs.isCollegeModeOn) {
                    // College Mode is OFF: let calls behave completely normally
                    return
                }
                if (prefs.getCloudConfig().isForwardingReady) {
                    // Cloud Call Forwarding (**61* / **21*) is active: let Twilio answer instead of local TelecomManager
                    Log.d(TAG, "Cloud forwarding is active; skipping local AutoAnswerService")
                    return
                }

                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        Log.d(TAG, "Incoming ringing call detected in College Mode")
                        val serviceIntent = Intent(context, AutoAnswerService::class.java).apply {
                            this.action = AutoAnswerService.ACTION_HANDLE_INCOMING_CALL
                            putExtra(AutoAnswerService.EXTRA_CALLER_NUMBER, incomingNumber)
                        }
                        startForegroundCompat(context, serviceIntent)
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        if (AutoAnswerService.isHandlingRealCall) {
                            val serviceIntent = Intent(context, AutoAnswerService::class.java).apply {
                                this.action = AutoAnswerService.ACTION_CALL_ENDED_EXTERNALLY
                            }
                            context.startService(serviceIntent)
                        }
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        // Call was answered (either by SafeCall or manually by user)
                    }
                }
            }
        }
    }

    private fun startForegroundCompat(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start AutoAnswerService from receiver: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SafeCallReceiver"
    }
}
