package io.legado.app.ui.book.search

import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.MatchMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultOrderingTest {

    @Test
    fun `priority order is equal then tags then contains then other`() {
        val equalOne = book("equal-one", "foo", origins = 1)
        val equalThree = book("equal-three", "foo", origins = 3)
        val tags = book("tags", "bar", kind = "foo", origins = 2)
        val contains = book("contains", "foobar", origins = 5)
        val other = book("other", "bar", origins = 9)

        val sorted = listOf(other, contains, tags, equalOne, equalThree)
            .sortedWithSearchPriority("foo", MatchMode.DEFAULT)

        assertEquals(
            listOf(equalThree, equalOne, tags, contains, other),
            sorted,
        )
    }

    @Test
    fun `exact mode does not add the default-only other bucket`() {
        val equal = book("equal", "foo", origins = 1)
        val other = book("other", "bar", origins = 3)

        val sorted = listOf(other, equal)
            .sortedWithSearchPriority("foo", MatchMode.EXACT)

        assertEquals(listOf(equal), sorted)
    }

    @Test
    fun `equal origin counts keep stable first-seen order`() {
        val first = book("first", "foobar", origins = 1)
        val second = book("second", "foobar", origins = 1)

        val sorted = listOf(first, second)
            .sortedWithSearchPriority("foo", MatchMode.DEFAULT)

        assertEquals(listOf(first, second), sorted)
    }

    private fun book(
        name: String,
        author: String,
        kind: String? = null,
        origins: Int,
    ) = SearchBook(
        bookUrl = "https://book/$name",
        origin = "https://source/0",
        name = name,
        author = author,
        kind = kind,
    ).apply {
        repeat(origins - 1) { addOrigin("https://source/${it + 1}") }
    }
}
