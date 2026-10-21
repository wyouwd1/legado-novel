package io.legado.app.ui.main

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MainIntentTest {

    @Test
    fun `regular reader keeps existing activity and navigation semantics`() {
        val context: Application = RuntimeEnvironment.getApplication()

        val intent = MainIntent.createReadBookIntent(context)

        val mediaControlFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(0, intent.flags and mediaControlFlags)
        assertFalse(MainIntent.shouldOpenRouteWithHomeParent(intent))
    }
}
