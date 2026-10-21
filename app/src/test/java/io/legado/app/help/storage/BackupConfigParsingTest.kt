package io.legado.app.help.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConfigParsingTest {

    @Test
    fun `missing or blank config is an empty map`() {
        assertTrue(parseIgnoreConfigMap(null).isEmpty())
        assertTrue(parseIgnoreConfigMap("").isEmpty())
        assertTrue(parseIgnoreConfigMap("   ").isEmpty())
    }

    @Test
    fun `empty json object is an empty map`() {
        assertTrue(parseIgnoreConfigMap("{}").isEmpty())
    }

    @Test
    fun `valid booleans are preserved`() {
        val result = parseIgnoreConfigMap(
            """{"readConfig":true,"themeMode":false,"localBook":true}"""
        )
        assertEquals(
            mapOf("readConfig" to true, "themeMode" to false, "localBook" to true),
            result,
        )
    }

    @Test
    fun `one bad value does not discard the whole config`() {
        val result = parseIgnoreConfigMap(
            """{"readConfig":true,"badType":123,"anotherBad":{},"localBook":true}"""
        )
        assertEquals(mapOf("readConfig" to true, "localBook" to true), result)
    }

    @Test
    fun `malformed json and wrong root type are ignored`() {
        assertTrue(parseIgnoreConfigMap("{broken").isEmpty())
        assertTrue(parseIgnoreConfigMap("[]").isEmpty())
        assertTrue(parseIgnoreConfigMap("null").isEmpty())
    }

    @Test
    fun `unknown legacy domain keys do not fail the parse`() {
        val result = parseIgnoreConfigMap(
            """{"rss":true,"httpTTS":false,"homepage":true,"readConfig":true}"""
        )
        assertEquals(
            mapOf("rss" to true, "httpTTS" to false, "homepage" to true, "readConfig" to true),
            result,
        )
    }
}
