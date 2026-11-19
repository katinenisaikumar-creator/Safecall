package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ParentMessage
import com.example.data.SafeCallPreferences
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
            timestampMillis = 1000L
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

        prefs.deleteMessage("m2")
        assertEquals(1, SafeCallPreferences(context).getMessages().size)

        prefs.clearAllMessages()
        assertEquals(0, SafeCallPreferences(context).getMessages().size)
    }
}
