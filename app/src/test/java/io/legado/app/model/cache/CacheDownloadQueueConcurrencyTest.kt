package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 多线程同时 enqueue/next/remove/waitingCount/prioritize。
 * 只做“有界终止 + 最终自洽”断言，不依赖 Thread.sleep 同步。
 */
class CacheDownloadQueueConcurrencyTest {

    @Test(timeout = 60_000)
    fun concurrentOperationsDoNotCorruptQueueOrLoseChapters() {
        val queue = CacheDownloadQueue()
        val threads = 6
        val chaptersPerThread = 120
        val iterations = 2_500
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val emitted = ConcurrentHashMap.newKeySet<Int>()
        val removed = ConcurrentHashMap.newKeySet<Int>()
        val executor = Executors.newFixedThreadPool(threads)

        repeat(threads) { threadIndex ->
            executor.execute {
                val base = threadIndex * chaptersPerThread
                val running = setOf(base + 3, base + 7)
                val random = Random(7_000L + threadIndex)
                try {
                    ready.countDown()
                    start.await()
                    ownedIndices(base, chaptersPerThread).chunked(4).forEach { chunk ->
                        queue.enqueue(ChapterSelection.Range(chunk.first(), chunk.last()))
                    }
                    repeat(iterations) {
                        when (random.nextInt(6)) {
                            0, 1 -> queue.next(BOOK_URL, running)?.let { candidate ->
                                assertFalse(
                                    "dispatched running chapter ${candidate.chapterIndex}",
                                    candidate.chapterIndex in running,
                                )
                                emitted.add(candidate.chapterIndex)
                            }

                            2 -> {
                                val index = base + random.nextInt(chaptersPerThread)
                                if (queue.removeChapter(index)) {
                                    removed.add(index)
                                }
                            }

                            3 -> queue.prioritize(base + random.nextInt(chaptersPerThread))
                            4 -> assertTrue(queue.waitingCount() >= 0)
                            else -> queue.waitingIndices()
                        }
                    }
                } catch (throwable: Throwable) {
                    errors.add(throwable)
                }
            }
        }

        assertTrue("workers did not start", ready.await(10, TimeUnit.SECONDS))
        start.countDown()
        executor.shutdown()
        assertTrue("workers did not finish", executor.awaitTermination(30, TimeUnit.SECONDS))
        assertTrue("concurrent failures: $errors", errors.isEmpty())

        val waiting = queue.waitingIndices().toSet()
        assertEquals("waitingCount must equal waitingIndices.size", waiting.size, queue.waitingCount())
        waiting.forEach { index -> assertTrue("isWaiting($index) must be true", queue.isWaiting(index)) }
        assertEquals(waiting.isNotEmpty(), queue.hasLaunchableChapter(emptySet()))

        val expectedRemaining = (0 until threads * chaptersPerThread)
            .filterNot { it in emitted || it in removed }
            .toSet()
        assertEquals("lost chapters", expectedRemaining, waiting)

        val drained = mutableListOf<Int>()
        while (true) {
            val candidate = queue.next(BOOK_URL, emptySet()) ?: break
            drained.add(candidate.chapterIndex)
        }
        assertEquals("duplicate dispatch during final drain", drained.size, drained.toSet().size)
        assertEquals("final drain differs from waiting set", waiting, drained.toSet())
        assertEquals(0, queue.waitingCount())
        assertNull(queue.next(BOOK_URL, emptySet()))
    }

    private fun ownedIndices(base: Int, count: Int): List<Int> = (base until base + count).toList()

    private companion object {
        const val BOOK_URL = "https://example.com/book/concurrent"
    }
}
