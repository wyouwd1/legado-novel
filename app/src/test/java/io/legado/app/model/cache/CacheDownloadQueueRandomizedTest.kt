package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 固定种子的随机对拍：队列的等待集合/计数/出队必须与朴素模型一致，
 * 不允许丢章、重复发同一章或返回从未入队的章节。
 */
class CacheDownloadQueueRandomizedTest {

    @Test
    fun thousandRandomOperationsMatchNaiveModel() {
        val random = Random(RANDOM_SEED)
        val queue = CacheDownloadQueue()
        val model = NaiveQueue()

        repeat(1_000) { step ->
            when (random.nextInt(10)) {
                0, 1, 2 -> {
                    val selection = randomSelection(random)
                    queue.enqueue(selection)
                    model.enqueue(selection)
                }

                3, 4, 5 -> {
                    val running = randomRunning(random)
                    val expected = model.waiting.filterNot { it in running }.toSet()
                    val actual = queue.next(BOOK_URL, running)
                    if (expected.isEmpty()) {
                        assertNull("step=$step unexpectedly dispatched ${actual?.chapterIndex}", actual)
                    } else {
                        assertNotNull("step=$step expected one of $expected", actual)
                        val index = actual!!.chapterIndex
                        assertTrue("step=$step dispatched $index not in $expected", index in expected)
                        model.markEmitted(index)
                    }
                }

                6 -> {
                    val index = random.nextInt(0, 41)
                    assertEquals("step=$step remove $index", model.remove(index), queue.removeChapter(index))
                }

                7 -> {
                    val index = random.nextInt(0, 41)
                    model.prioritize(index)
                    queue.prioritize(index)
                }

                else -> Unit
            }
            assertQueueMatchesModel(queue, model, step)
        }

        val remaining = model.waiting.toSet()
        val drained = mutableListOf<Int>()
        while (true) {
            val candidate = queue.next(BOOK_URL, emptySet()) ?: break
            assertTrue(
                "step=final dispatched ${candidate.chapterIndex} which was not waiting",
                candidate.chapterIndex in remaining,
            )
            assertTrue("step=final dispatched duplicate ${candidate.chapterIndex}", candidate.chapterIndex !in drained)
            drained.add(candidate.chapterIndex)
        }
        assertEquals("step=final lost waiting chapters", remaining, drained.toSet())
        assertEquals(0, queue.waitingCount())
        assertNull(queue.next(BOOK_URL, emptySet()))
    }

    private fun assertQueueMatchesModel(queue: CacheDownloadQueue, model: NaiveQueue, step: Int) {
        assertEquals("step=$step waitingCount", model.waiting.size, queue.waitingCount())
        assertEquals("step=$step waitingIndices", model.waiting, queue.waitingIndices().toSet())
        model.waiting.forEach { index ->
            assertTrue("step=$step isWaiting($index)", queue.isWaiting(index))
        }
        assertEquals(
            "step=$step hasLaunchableChapter",
            model.waiting.isNotEmpty(),
            queue.hasLaunchableChapter(emptySet()),
        )
    }

    private fun randomSelection(random: Random): ChapterSelection {
        return when (random.nextInt(3)) {
            0 -> {
                val start = random.nextInt(0, 36)
                ChapterSelection.Range(start, start + random.nextInt(0, 5))
            }

            1 -> ChapterSelection.Single(random.nextInt(0, 41))
            else -> ChapterSelection.Indices(
                List(random.nextInt(1, 6)) { random.nextInt(0, 41) }.toSet(),
            )
        }
    }

    private fun randomRunning(random: Random): Set<Int> {
        return List(random.nextInt(0, 4)) { random.nextInt(0, 41) }.toSet()
    }

    private class NaiveQueue {
        val waiting = linkedSetOf<Int>()
        private val emitted = hashSetOf<Int>()

        fun enqueue(selection: ChapterSelection) {
            when (selection) {
                is ChapterSelection.Range -> {
                    for (index in selection.start..selection.end) {
                        emitted.remove(index)
                        waiting.add(index)
                    }
                }

                is ChapterSelection.Indices -> selection.values.forEach(::enqueueSingle)
                is ChapterSelection.Single -> enqueueSingle(selection.index)
            }
        }

        fun remove(index: Int): Boolean = waiting.remove(index)

        fun prioritize(index: Int) {
            if (waiting.remove(index)) {
                val rest = waiting.toList()
                waiting.clear()
                waiting.add(index)
                waiting.addAll(rest)
            }
        }

        fun markEmitted(index: Int) {
            check(waiting.remove(index)) { "dispatched $index which was not waiting" }
            emitted.add(index)
        }

        private fun enqueueSingle(index: Int) {
            emitted.remove(index)
            waiting.add(index)
        }
    }

    private companion object {
        const val RANDOM_SEED = 20261021L
        const val BOOK_URL = "https://example.com/book/random"
    }
}
