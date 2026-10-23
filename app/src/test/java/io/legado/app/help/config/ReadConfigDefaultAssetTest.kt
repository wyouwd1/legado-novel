package io.legado.app.help.config

import android.app.Application
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadConfigDefaultAssetTest {

    @Test
    fun `default read config asset keeps body spacing`() {
        val json = RuntimeEnvironment.getApplication().assets
            .open("defaultData/readConfig.json")
            .bufferedReader()
            .use { it.readText() }
        val configs = Gson().fromJson(json, Array<ReadBookConfig.Config>::class.java)
        val config = configs.first { it.name == "微信读书" }

        assertEquals(0.1f, config.letterSpacing, 0f)
        assertEquals(12, config.lineSpacingExtra)
        assertEquals(6, config.paragraphSpacing)
        assertEquals("　　", config.paragraphIndent)
    }
}
