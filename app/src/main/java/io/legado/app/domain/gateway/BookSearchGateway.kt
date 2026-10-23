package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookSearchScope

interface BookSearchGateway {
    /** Full source rows for global search, already filtered by enabled + non-blank searchUrl. */
    suspend fun getSearchSources(
        scope: BookSearchScope,
        types: Set<Int>? = null,
    ): List<BookSource>

    suspend fun getBookSource(sourceUrl: String): BookSource?
    suspend fun saveSearchBooks(books: List<SearchBook>)
}
