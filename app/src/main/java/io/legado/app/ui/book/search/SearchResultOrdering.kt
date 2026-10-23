package io.legado.app.ui.book.search

import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.MatchMode

/**
 * Display order for accumulated multi-source search results.
 *
 * Priority is equal > kind/tags > contains > other, then origin count descending. The underlying
 * sort is stable, so first-seen order is preserved for ties.
 */
internal fun Collection<SearchBook>.sortedWithSearchPriority(
    keyword: String,
    matchMode: MatchMode,
): List<SearchBook> {
    val equalBooks = arrayListOf<SearchBook>()
    val tagsBooks = arrayListOf<SearchBook>()
    val containsBooks = arrayListOf<SearchBook>()
    val otherBooks = arrayListOf<SearchBook>()
    forEach { book ->
        when {
            book.name.equals(keyword, ignoreCase = true) ||
                book.author.equals(keyword, ignoreCase = true) -> equalBooks.add(book)

            book.kind?.contains(keyword, ignoreCase = true) == true -> tagsBooks.add(book)
            book.name.contains(keyword, ignoreCase = true) ||
                book.author.contains(keyword, ignoreCase = true) -> containsBooks.add(book)

            matchMode == MatchMode.DEFAULT -> otherBooks.add(book)
        }
    }
    return buildList(size) {
        addAll(equalBooks.sortedByDescending { it.origins.size })
        addAll(tagsBooks.sortedByDescending { it.origins.size })
        addAll(containsBooks.sortedByDescending { it.origins.size })
        addAll(otherBooks)
    }
}
