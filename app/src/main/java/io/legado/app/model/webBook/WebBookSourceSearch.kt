package io.legado.app.model.webBook

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.SourceSearch

/** Production [SourceSearch] adapter; behavior is identical to the previous inline call. */
object WebBookSourceSearch : SourceSearch {
    override suspend fun search(
        source: BookSource,
        keyword: String,
        page: Int,
        filter: ((name: String, author: String, kind: String?) -> Boolean)?,
    ): List<SearchBook> = WebBook.searchBookAwait(
        bookSource = source,
        key = keyword,
        page = page,
        filter = filter,
    )
}
