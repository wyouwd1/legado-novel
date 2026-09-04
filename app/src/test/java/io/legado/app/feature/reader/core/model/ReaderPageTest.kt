package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageTest {
    @Test fun hitTestingUsesImmutableElementBounds() {
        val text = ReaderElement.Text(
            bounds = ReaderRect(10f, 20f, 30f, 40f),
            baselinePx = 35f,
            value = "阅",
            style = ReaderTextStyle(colorArgb = 0xff000000.toInt(), fontSizePx = 20f),
            selected = false,
            emphasized = false,
            chapterPosition = 12,
        )
        val page = ReaderPage(
            ReaderPageId(1, 2), "chapter", "text", 100, 200, 20f, 180f, listOf(text), 1,
        )
        assertEquals(text, page.elementAt(20f, 30f))
        assertNull(page.elementAt(5f, 30f))
    }

    @Test fun htmlLinkUsesAccentColorAndUnderlineWithLegacyPriority() {
        val link = text(link = "https://example")

        assertEquals(0xff22aa44.toInt(), link.resolvedColorArgb(0xff22aa44.toInt()))
        assertTrue(link.drawsLinkUnderline)
    }

    @Test fun searchResultUsesAccentButOrdinaryTextKeepsItsConfiguredColor() {
        val ordinary = text()
        val result = text(searchResult = true)

        assertEquals(0xff123456.toInt(), ordinary.resolvedColorArgb(0xff22aa44.toInt()))
        assertFalse(ordinary.drawsLinkUnderline)
        assertEquals(0xff22aa44.toInt(), result.resolvedColorArgb(0xff22aa44.toInt()))
        assertFalse(result.drawsLinkUnderline)
    }

    private fun text(
        link: String? = null,
        searchResult: Boolean = false,
    ) = ReaderElement.Text(
        bounds = ReaderRect(0f, 0f, 10f, 20f),
        baselinePx = 15f,
        value = "字",
        style = ReaderTextStyle(colorArgb = 0xff123456.toInt(), fontSizePx = 20f),
        selected = false,
        emphasized = false,
        searchResult = searchResult,
        link = link,
        chapterPosition = 0,
    )
}
