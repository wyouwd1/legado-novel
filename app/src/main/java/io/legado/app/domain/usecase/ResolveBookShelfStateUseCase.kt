package io.legado.app.domain.usecase

import io.legado.app.domain.model.BookShelfState

data class BookShelfKey(
    val name: String,
    val author: String,
    val url: String?
)

internal data class BookShelfNameAuthor(val name: String, val author: String)

/**
 * Hash-based shelf resolver for callers that evaluate many search results against one shelf.
 * Building it is O(shelf); each lookup is O(1).
 */
class BookShelfLookup internal constructor(
    private val exact: Set<BookShelfKey>,
    private val nameAuthors: Set<BookShelfNameAuthor>,
) {
    fun resolve(name: String, author: String, url: String?): BookShelfState {
        if (BookShelfKey(name, author, url) in exact) return BookShelfState.IN_SHELF
        if (BookShelfNameAuthor(name, author) in nameAuthors) return BookShelfState.SAME_NAME_AUTHOR
        return BookShelfState.NOT_IN_SHELF
    }
}

class ResolveBookShelfStateUseCase {

    fun prepare(shelf: Set<BookShelfKey>): BookShelfLookup = BookShelfLookup(
        exact = shelf.toHashSet(),
        nameAuthors = shelf.mapTo(HashSet()) { BookShelfNameAuthor(it.name, it.author) },
    )

    fun execute(
        name: String,
        author: String,
        url: String?,
        shelf: Set<BookShelfKey>
    ): BookShelfState {
        val exactMatch = shelf.any {
            it.name == name && it.author == author && it.url == url
        }
        if (exactMatch) return BookShelfState.IN_SHELF

        val sameNameAuthor = shelf.any {
            it.name == name && it.author == author && it.url != url
        }
        if (sameNameAuthor) return BookShelfState.SAME_NAME_AUTHOR

        return BookShelfState.NOT_IN_SHELF
    }
}
