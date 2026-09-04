package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.model.CacheableBook
import io.legado.app.domain.repository.BookDomainRepository
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection

class BatchCacheDownloadUseCase(
    private val bookRepository: BookDomainRepository,
    private val bookCacheDownloadGateway: BookCacheDownloadGateway
) {

    suspend fun execute(
        bookUrls: Set<String>,
        downloadAllChapters: Boolean
    ): Int {
        if (bookUrls.isEmpty()) return 0
        val requests = bookRepository.getCacheableBooks(bookUrls).mapNotNull { book ->
            createRequestIfNeeded(book, downloadAllChapters)
        }
        bookCacheDownloadGateway.start(requests)
        return requests.size
    }

    private fun createRequestIfNeeded(
        book: CacheableBook,
        downloadAllChapters: Boolean
    ): CacheDownloadRequest? {
        if (book.isLocal) return null
        val startIndex = if (downloadAllChapters) 0 else book.durChapterIndex
        val endIndex = book.lastChapterIndex
        if (endIndex < startIndex) return null
        return CacheDownloadRequest(
            bookUrl = book.bookUrl,
            selection = ChapterSelection.Range(startIndex, endIndex),
            source = CacheDownloadSource.Batch,
        )
    }
}
