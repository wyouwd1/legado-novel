package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook

/**
 * One book source's search operation.
 *
 * Production delegates to the existing WebBook pipeline; tests inject a deterministic fake so
 * scheduling and tail-latency behavior can be benchmarked without network or JS execution.
 */
fun interface SourceSearch {
    suspend fun search(
        source: BookSource,
        keyword: String,
        page: Int,
        filter: ((name: String, author: String, kind: String?) -> Boolean)?,
    ): List<SearchBook>
}
