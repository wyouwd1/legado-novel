package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadStateStore
import io.legado.app.model.cache.ChapterSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * 公开 API 的取消语义：removeDownload 只移除一次，不会自动把章节重新入队；
 * 再次下载必须来自新的显式请求。
 */
class CacheBookModelFailureTest {

    @Test
    fun removeWaitingDownloadDoesNotRequeueAndExplicitRequestCanReAddIt() {
        val model = newModel()
        model.addRequest(CacheDownloadRequest(BOOK_URL, ChapterSelection.Range(0, 5)))

        assertTrue(model.removeDownload(3))
        assertFalse(model.isWaiting(3))
        assertEquals(5, model.diagnostics().waitingChapterCount)
        assertFalse(model.removeDownload(3))
        assertEquals(5, model.diagnostics().waitingChapterCount)

        model.addRequest(CacheDownloadRequest(BOOK_URL, ChapterSelection.Single(3)))
        assertTrue(model.isWaiting(3))
        assertEquals(6, model.diagnostics().waitingChapterCount)
    }

    @Test(timeout = 5_000)
    fun hugeRangeIsRejectedBeforeExpandingTheRange() {
        val model = newModel()

        model.addRequest(CacheDownloadRequest(BOOK_URL, ChapterSelection.Range(0, Int.MAX_VALUE)))
        model.addRequest(CacheDownloadRequest(BOOK_URL, ChapterSelection.Range(-1, 10)))

        assertEquals(0, model.diagnostics().waitingChapterCount)
        assertFalse(model.isWaiting(0))
        assertFalse(model.hasLaunchableChapters())
    }

    private fun newModel(): CacheBookModel {
        val host = FakeHost()
        val model = CacheBookModel(
            bookSource = BookSource(bookSourceUrl = "https://example.com", bookSourceName = "t"),
            book = Book(bookUrl = BOOK_URL, name = "book", origin = "https://example.com"),
            host = host,
        )
        host.cacheBookMap[BOOK_URL] = model
        return model
    }

    private class FakeHost : CacheBookModel.Host {
        override val stateStore = CacheDownloadStateStore()
        override val cacheBookMap = ConcurrentHashMap<String, CacheBookModel>()
        override fun incrementSuccessCount(): Int = 0
        override fun onTaskQueuesChanged(bookUrl: String) = Unit
        override fun onTaskRemoved(bookUrl: String, clearState: Boolean) = Unit
        override fun onExplicitBookQueued(bookUrl: String) = Unit
        override fun emitDownloadingIndices(bookUrl: String, indices: Set<Int>) = Unit
        override fun emitDownloadError(bookUrl: String, indices: Set<Int>) = Unit
        override fun emitChapterCached(chapter: BookChapter) = Unit
        override fun errorIndices(bookUrl: String): Set<Int> = emptySet()
    }

    private companion object {
        const val BOOK_URL = "https://example.com/book/failure"
    }
}
