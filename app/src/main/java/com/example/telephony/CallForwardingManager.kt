package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

enum class ForwardingMode(val codePrefix: String, val disableCode: String, val label: String) {
    CONDITIONAL_NO_ANSWER("**61*", "##61#", "Unanswered (**61*)"),
    UNCONDITIONAL_ALL("**21*", "##21#", "All Calls (**21*)")
}

object CallForwardingManager {

    private const val TAG = "CallForwardingManager"

    /**
     * Dials the carrier MMI/USSD code to activate call forwarding to [twilioNumber].
     * Example: tel:**61*+14155551234%23  or  tel:**21*+14155551234%23
     */
    fun activateForwarding(
        context: Context,
        twilioNumber: String,
        mode: ForwardingMode = ForwardingMode.CONDITIONAL_NO_ANSWER
    ): Result<String> {
        val sanitizedNumber = sanitizePhoneNumber(twilioNumber)
        if (sanitizedNumber.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your Twilio number first"))
        }

        val ussdCode = "${mode.codePrefix}$sanitizedNumber#"
        return launchUssdDial(context, ussdCode)
    }

    /**
     * Dials the carrier MMI/USSD code (##61# or ##21#) to cancel call forwarding.
     */
    fun deactivateForwarding(
        context: Context,
        mode: ForwardingMode = ForwardingMode.CONDITIONAL_NO_ANSWER
    ): Result<String> {
        return launchUssdDial(context, mode.disableCode)
    }

    /**
     * Cancels all conditional + unconditional call forwards across Indian carriers (##002#).
     */
    fun deactivateAllForwarding(context: Context): Result<String> {
        return launchUssdDial(context, "##002#")
    }

    @SuppressLint("MissingPermission")
    private fun launchUssdDial(context: Context, rawUssdCode: String): Result<String> {
        val encodedUriString = "tel:" + rawUssdCode.replace("#", Uri.encode("#"))
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse(encodedUriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            context.startActivity(intent)
            Log.d(TAG, "Launched $action for USSD: $rawUssdCode")
            rawUssdCode
        }
    }

    private fun sanitizePhoneNumber(input: String): String {
        return input.trim().filter { it.isDigit() || it == '+' }
    }
}
