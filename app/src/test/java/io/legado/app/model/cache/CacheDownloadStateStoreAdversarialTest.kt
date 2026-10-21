package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDownloadStateStoreAdversarialTest {

    @Test
    fun duplicateSuccessCallbacksCountAChapterOnlyOnce() {
        val store = CacheDownloadStateStore()
        store.updateBookQueue("a", waitingCount = 0, runningIndices = setOf(1))

        store.markSuccess("a", 1)
        store.markSuccess("a", 1)
        store.markSuccess("a", 1)

        val bookState = store.state.books.getValue("a")
        assertEquals(1, bookState.successCount)
        assertEquals(1, store.state.totalSuccess)
        assertTrue(bookState.runningIndices.isEmpty())
        assertTrue(bookState.failedIndices.isEmpty())
    }

    @Test
    fun duplicateFailureCallbacksAreIdempotentAndLeaveNoRunningIndices() {
        val store = CacheDownloadStateStore()
        store.updateBookQueue("a", waitingCount = 0, runningIndices = setOf(1, 2))

        store.markFailed("a", 1)
        store.markFailed("a", 1)
        store.markFailed("a", 2)

        val bookState = store.state.books.getValue("a")
        assertEquals(setOf(1, 2), bookState.failedIndices)
        assertEquals(2, store.state.totalFailure)
        assertTrue(bookState.runningIndices.isEmpty())
        assertEquals(0, bookState.successCount)
    }

    @Test
    fun interleavedSuccessAndFailureCallbacksStayConsistent() {
        val store = CacheDownloadStateStore()
        store.updateBookQueue("a", waitingCount = 0, runningIndices = setOf(1, 2))

        store.markFailed("a", 1)
        store.markSuccess("a", 2)
        store.markSuccess("a", 2)
        store.markFailed("a", 2)
        store.markFailed("a", 1)

        val bookState = store.state.books.getValue("a")
        assertEquals(setOf(1, 2), bookState.failedIndices)
        assertEquals(1, bookState.successCount)
        assertEquals(1, store.state.totalSuccess)
        assertTrue(bookState.runningIndices.isEmpty())
        assertTrue(bookState.pausedIndices.isEmpty())
    }

    @Test
    fun lateCallbacksAfterRemoveBookDoNotResurrectState() {
        val store = CacheDownloadStateStore()
        store.updateBookQueue("a", waitingCount = 0, runningIndices = setOf(1))
        store.removeBook("a")

        store.markSuccess("a", 1)
        store.markFailed("a", 2)
        store.updateChapterProgress(
            "a",
            3,
            CacheChapterProgress(CacheChapterProgressPhase.CONTENT, completed = 0, total = 1),
        )
        store.clearFailure("a", 2)

        assertEquals(emptyMap<String, CacheBookDownloadState>(), store.state.books)
        assertEquals(0, store.state.totalSuccess)
        assertEquals(0, store.state.totalFailure)
        assertFalse(store.state.isRunning)
    }

    @Test
    fun clearWipesEverythingAndRecalculatesTotals() {
        val store = CacheDownloadStateStore()
        store.updateBookQueue("a", waitingCount = 2, runningIndices = setOf(1))
        store.markFailed("a", 1)
        store.markSuccess("a", 2)
        store.markBookFailed("b", "source unavailable")

        store.clear()

        assertEquals(CacheDownloadState(), store.state)
        assertFalse(store.state.isRunning)
    }

    @Test
    fun clearRuntimeStateIsIdempotentAndKeepsVisibleFailures() {
        val store = CacheDownloadStateStore()
        store.updateBookQueue("failed", waitingCount = 3, runningIndices = setOf(1))
        store.markFailed("failed", 1)
        store.updateBookQueue("running", waitingCount = 2, runningIndices = setOf(2))
        store.markBookFailed("bookFailure", "source unavailable")

        store.clearRuntimeState()
        store.clearRuntimeState()

        assertEquals(setOf("failed", "bookFailure"), store.state.books.keys)
        val failed = store.state.books.getValue("failed")
        assertEquals(setOf(1), failed.failedIndices)
        assertEquals(0, failed.waitingCount)
        assertTrue(failed.runningIndices.isEmpty())
        assertEquals(0, failed.successCount)
        assertEquals("source unavailable", store.state.books.getValue("bookFailure").failureMessage)
        assertEquals(2, store.state.totalFailure)
        assertEquals(0, store.state.totalWaiting)
        assertEquals(0, store.state.totalRunning)
        assertEquals(0, store.state.totalSuccess)
        assertFalse(store.state.isRunning)
    }

    @Test
    fun partialPauseStillReportsRunningWhenAnotherChapterIsWaiting() {
        val store = CacheDownloadStateStore()

        store.updateBookQueue(
            "a",
            waitingCount = 1,
            runningIndices = emptySet(),
            pausedIndices = setOf(9),
        )

        assertEquals(1, store.state.totalWaiting)
        assertEquals(1, store.state.totalPaused)
        assertTrue("a runnable waiting chapter must keep the queue running", store.state.isRunning)
    }

    @Test
    fun negativeWaitingCountIsClampedToZero() {
        val store = CacheDownloadStateStore()

        store.updateBookQueue("a", waitingCount = -5, runningIndices = emptySet())

        assertEquals(0, store.state.totalWaiting)
        assertEquals(0, store.state.books.getValue("a").waitingCount)
        assertFalse(store.state.isRunning)
    }

    @Test
    fun failureMessageLifecycleFollowsQueueRestart() {
        val store = CacheDownloadStateStore()
        store.markBookFailed("a", "source unavailable")

        store.updateBookQueue("a", waitingCount = 0, runningIndices = emptySet())
        assertEquals("source unavailable", store.state.books.getValue("a").failureMessage)
        assertEquals(1, store.state.totalFailure)

        store.updateBookQueue("a", waitingCount = 0, runningIndices = setOf(1))
        assertNull(store.state.books.getValue("a").failureMessage)
        assertEquals(0, store.state.totalFailure)

        store.markBookFailed("a", "again")
        store.updateBookQueue("a", waitingCount = 0, runningIndices = setOf(2))
        assertNull(store.state.books.getValue("a").failureMessage)
    }

    @Test
    fun failedChapterCanBeRequeuedAndClearedAfterSuccess() {
        val queue = CacheDownloadQueue()
        val store = CacheDownloadStateStore()
        queue.enqueue(ChapterSelection.Range(0, 2))
        store.updateBookQueue("a", queue.waitingCount(), runningIndices = emptySet())

        val chapter = queue.next("a", emptySet())!!.chapterIndex
        store.updateBookQueue("a", queue.waitingCount(), runningIndices = setOf(chapter))
        store.markFailed("a", chapter)
        assertEquals(setOf(chapter), store.state.books.getValue("a").failedIndices)
        assertTrue(store.state.books.getValue("a").runningIndices.isEmpty())

        // 与 CacheBookModel.onPostError 的重试路径一致：失败章重新入队后再次调度。
        queue.enqueue(ChapterSelection.Single(chapter))
        assertEquals(chapter, queue.next("a", emptySet())?.chapterIndex)
        store.updateBookQueue("a", queue.waitingCount(), runningIndices = setOf(chapter))
        store.clearFailure("a", chapter)
        assertTrue(store.state.books.getValue("a").failedIndices.isEmpty())
        store.markSuccess("a", chapter)
        assertEquals(1, store.state.books.getValue("a").successCount)
        assertEquals(0, store.state.totalFailure)
    }
}
