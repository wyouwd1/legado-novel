package io.legado.app.domain.usecase

import io.legado.app.domain.model.BookShelfState
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveBookShelfStateUseCaseTest {

    private val shelf = setOf(
        BookShelfKey("same", "author", "https://one.example"),
        BookShelfKey("other", "author", "https://two.example"),
        BookShelfKey("null-url", "author", null),
    )
    private val lookup = ResolveBookShelfStateUseCase().prepare(shelf)

    @Test
    fun `exact url wins over same name and author`() {
        assertEquals(
            BookShelfState.IN_SHELF,
            lookup.resolve("same", "author", "https://one.example"),
        )
    }

    @Test
    fun `same name and author with another url is a conflict hint`() {
        assertEquals(
            BookShelfState.SAME_NAME_AUTHOR,
            lookup.resolve("same", "author", "https://another.example"),
        )
    }

    @Test
    fun `null url matches exactly`() {
        assertEquals(
            BookShelfState.IN_SHELF,
            lookup.resolve("null-url", "author", null),
        )
    }

    @Test
    fun `unknown book is not in shelf`() {
        assertEquals(
            BookShelfState.NOT_IN_SHELF,
            lookup.resolve("missing", "author", "https://one.example"),
        )
    }
}
