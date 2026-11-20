package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ParentMessage
import com.example.data.SafeCallPreferences
import com.example.data.VoiceLanguages
import com.example.telephony.ForwardingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SafeCall", appName)
    }

    @Test
    fun `college mode and student status persist in SharedPreferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SafeCallPreferences(context)

        prefs.isCollegeModeOn = true
        prefs.studentStatus = "Chemistry Lab lo unna"

        val reloaded = SafeCallPreferences(context)
        assertTrue(reloaded.isCollegeModeOn)
        assertEquals("Chemistry Lab lo unna", reloaded.studentStatus)

        reloaded.isCollegeModeOn = false
        assertFalse(SafeCallPreferences(context).isCollegeModeOn)
    }

    @Test
    fun `parent messages save delete and clear cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SafeCallPreferences(context)
        prefs.clearAllMessages()

        val msg1 = ParentMessage(
            id = "m1",
            callerDisplay = "Amma",
            callerNumber = "+91 98765 43210",
            messageText = "Call me after lab finishes",
            studentStatusAtCall = "Lab Session lo unna",
            timestampMillis = 1000L,
            recordingUrl = "https://example.ngrok-free.app/api/recordings/RE123",
            durationSeconds = 9
        )
        val msg2 = ParentMessage(
            id = "m2",
            callerDisplay = "Nanna",
            callerNumber = "+91 98480 12345",
            messageText = "Reached college safely?",
            studentStatusAtCall = "Class lo unna",
            timestampMillis = 2000L
        )

        prefs.addMessage(msg1)
        prefs.addMessage(msg2)

        val loaded = SafeCallPreferences(context).getMessages()
        assertEquals(2, loaded.size)
        assertEquals("Nanna", loaded.first().callerDisplay)
        assertEquals("https://example.ngrok-free.app/api/recordings/RE123", loaded.last().recordingUrl)
        assertEquals(9, loaded.last().durationSeconds)

        prefs.deleteMessage("m2")
        assertEquals(1, SafeCallPreferences(context).getMessages().size)

        prefs.clearAllMessages()
        assertEquals(0, SafeCallPreferences(context).getMessages().size)
    }

    @Test
    fun `built-in and custom modes persist and switch student status`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SafeCallPreferences(context)

        // Built-in mode switch updates status
        prefs.selectMode("exam")
        assertEquals("exam", prefs.selectedModeId)
        assertEquals("Exam Hall lo unna", prefs.studentStatus)

        // Add custom mode
        val custom = prefs.addCustomMode(
            name = "Tuition Mode",
            defaultStatus = "Maths Tuition lo unna",
            iconKey = "study"
        )
        val reloaded = SafeCallPreferences(context)
        assertEquals(custom.id, reloaded.selectedModeId)
        assertEquals("Maths Tuition lo unna", reloaded.studentStatus)
        assertTrue(reloaded.getAllModes().any { it.name == "Tuition Mode" })

        // Delete custom mode falls back cleanly
        reloaded.deleteCustomMode(custom.id)
        assertEquals("college", SafeCallPreferences(context).selectedModeId)
    }

    @Test
    fun `voice language defaults to Telugu and supports switching or custom message`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SafeCallPreferences(context)

        // Default is Telugu
        assertEquals(VoiceLanguages.ID_TELUGU, prefs.getActiveVoiceLanguage().id)
        assertTrue(prefs.getActiveVoiceLanguage().formatGreeting("Lab").contains("నమస్కారం"))

        // Switch to Hindi
        prefs.selectLanguage(VoiceLanguages.ID_HINDI)
        assertEquals("hi-IN", SafeCallPreferences(context).getActiveVoiceLanguage().localeTag)

        // Save custom message in any language
        prefs.saveCustomVoiceMessage(
            greetingTemplate = "Namaskaram, {status} lo unnanu, beep tarvata cheppandi.",
            closingMessage = "Thank you amma.",
            localeTag = "te-IN"
        )
        val customLang = SafeCallPreferences(context).getActiveVoiceLanguage()
        assertEquals(VoiceLanguages.ID_CUSTOM, customLang.id)
        assertEquals(
            "Namaskaram, Exam Hall lo unnanu, beep tarvata cheppandi.",
            customLang.formatGreeting("Exam Hall")
        )
    }

    @Test
    fun `cloud voice forwarding config and fcm token persist cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SafeCallPreferences(context)

        prefs.saveCloudConfig(
            backendBaseUrl = "https://safecall-demo.ngrok-free.app",
            twilioNumber = "+14155550199",
            forwardingMode = ForwardingMode.UNCONDITIONAL_ALL,
            useCloudForwarding = true
        )
        prefs.updateFcmToken("fcm_test_token_abc")

        val reloaded = SafeCallPreferences(context).getCloudConfig()
        assertEquals("https://safecall-demo.ngrok-free.app", reloaded.backendBaseUrl)
        assertEquals("+14155550199", reloaded.twilioNumber)
        assertEquals(ForwardingMode.UNCONDITIONAL_ALL, reloaded.forwardingMode)
        assertTrue(reloaded.useCloudForwarding)
        assertTrue(reloaded.isForwardingReady)
        assertEquals("fcm_test_token_abc", reloaded.fcmToken)
    }
}
