package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDownloadQueueAdversarialTest {

    @Test
    fun negativeAndReversedSelectionsAreIgnored() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Single(-1))
        queue.enqueue(ChapterSelection.Range(-5, 3))
        queue.enqueue(ChapterSelection.Range(4, 2))
        queue.enqueue(ChapterSelection.Indices(setOf(-3, -1)))

        assertEquals(0, queue.waitingCount())
        assertTrue(queue.waitingIndices().isEmpty())
        assertFalse(queue.isWaiting(-1))
        assertNull(queue.next("book", emptySet()))

        queue.enqueue(ChapterSelection.Single(0))
        assertEquals(0, queue.next("book", emptySet())?.chapterIndex)
    }

    @Test(timeout = 5_000)
    fun nearIntMaxRangeIsRejectedBeforeItCanWrap() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Range(Int.MAX_VALUE - 2, Int.MAX_VALUE))

        assertEquals(0, queue.waitingCount())
        assertNull(queue.next("book", emptySet()))
    }

    @Test(timeout = 5_000)
    fun fullRangeToIntMaxTerminatesAndIsRejected() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Range(0, Int.MAX_VALUE))
        queue.enqueue(ChapterSelection.Range(Int.MAX_VALUE - 1, Int.MAX_VALUE))

        assertEquals(0, queue.waitingCount())
        assertTrue(queue.waitingIndices().isEmpty())
        assertNull(queue.next("book", emptySet()))
    }

    @Test
    fun duplicateAndOverlappingEnqueueBeforeDispatchEmitsEachChapterOnce() {
        val queue = CacheDownloadQueue()

        queue.enqueue(ChapterSelection.Range(0, 8))
        queue.enqueue(ChapterSelection.Range(0, 8))
        queue.enqueue(ChapterSelection.Range(3, 12))
        queue.enqueue(ChapterSelection.Single(5))

        assertEquals(13, queue.waitingCount())
        val drained = drain(queue)
        assertEquals((0..12).toSet(), drained.toSet())
        assertEquals(13, drained.size)
    }

    @Test
    fun prioritizeUnknownChapterDoesNotScheduleIt() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 2))

        queue.prioritize(99)

        assertEquals(listOf(0, 1, 2), queue.waitingIndices())
        assertEquals(3, queue.waitingCount())
        assertEquals(listOf(0, 1, 2), drain(queue))
    }

    @Test
    fun removeEmittedAndRepeatedRemoveDoNotResurrectChapter() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 3))
        assertEquals(0, queue.next("book", emptySet())?.chapterIndex)
        assertEquals(1, queue.next("book", emptySet())?.chapterIndex)

        assertFalse(queue.removeChapter(1))
        assertFalse(queue.removeChapter(1))

        assertEquals(2, queue.waitingCount())
        assertEquals(listOf(2, 3), drain(queue))
        assertNull(queue.next("book", emptySet()))
    }

    @Test
    fun failedChapterCanBeRequeuedAndCancelledChapterIsNotRequeuedAutomatically() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 2))

        val failed = queue.next("book", emptySet())?.chapterIndex
        assertEquals(0, failed)
        queue.enqueue(ChapterSelection.Single(failed!!))
        assertEquals(0, queue.next("book", emptySet())?.chapterIndex)

        val cancelled = 1 // 下一章仍在等待，尚未发出
        assertTrue(queue.removeChapter(cancelled))

        assertEquals(listOf(2), drain(queue))
        assertFalse(queue.isWaiting(cancelled))
    }

    @Test
    fun waitingCountAndWaitingIndicesStayConsistentThroughMixedOperations() {
        val queue = CacheDownloadQueue()
        queue.enqueue(ChapterSelection.Range(0, 6))
        queue.enqueue(ChapterSelection.Indices(setOf(2, 8, 3)))
        assertTrue(queue.removeChapter(4))
        queue.prioritize(8)
        queue.prioritize(8)

        val waiting = queue.waitingIndices()
        assertEquals(waiting.size, queue.waitingCount())
        assertEquals(waiting.toSet().size, waiting.size)
        waiting.forEach { assertTrue(queue.isWaiting(it)) }

        val drained = drain(queue)
        assertEquals(waiting.toSet(), drained.toSet())
        assertEquals(0, queue.waitingCount())
        assertTrue(queue.waitingIndices().isEmpty())
    }

    private fun drain(queue: CacheDownloadQueue): List<Int> {
        val result = mutableListOf<Int>()
        while (true) {
            val next = queue.next("book", emptySet()) ?: break
            result.add(next.chapterIndex)
        }
        return result
    }
}
