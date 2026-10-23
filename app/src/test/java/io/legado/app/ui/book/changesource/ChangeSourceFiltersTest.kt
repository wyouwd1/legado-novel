package io.legado.app.ui.book.changesource

import io.legado.app.data.entities.SearchBook
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeSourceFiltersTest {

    private fun bookWithWordCount(wordCount: Int) = SearchBook(
        bookUrl = "https://example.com/book",
        origin = "https://example.com",
        chapterWordCount = wordCount,
    )

    @Test
    fun `未设置最少字数时全部通过`() {
        assertTrue(bookWithWordCount(0).meetsMinWordCount(0))
        assertTrue(bookWithWordCount(100).meetsMinWordCount(0))
        assertTrue(bookWithWordCount(-1).meetsMinWordCount(0))
    }

    @Test
    fun `字数达到或超过阈值时通过`() {
        assertTrue(bookWithWordCount(2000).meetsMinWordCount(2000))
        assertTrue(bookWithWordCount(2001).meetsMinWordCount(2000))
    }

    @Test
    fun `字数低于阈值时被过滤`() {
        assertFalse(bookWithWordCount(1999).meetsMinWordCount(2000))
        assertFalse(bookWithWordCount(0).meetsMinWordCount(1))
    }

    @Test
    fun `字数未知时启用过滤后被过滤`() {
        assertFalse(bookWithWordCount(-1).meetsMinWordCount(1))
    }
}
