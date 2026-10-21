package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheDownloadAdmissionQueueAdversarialTest {

    @Test
    fun nonPositiveMaxActiveBooksStillAdmitsQueuedRequests() {
        for (maxActiveBooks in listOf(0, -1, Int.MIN_VALUE)) {
            val queue = CacheDownloadAdmissionQueue(maxActiveBooks)
            val request = request("a")

            assertFalse("max=$maxActiveBooks", queue.shouldQueue(request, emptySet()))
            queue.add(request)

            assertEquals("max=$maxActiveBooks", request, queue.pollReady(emptySet()))
            assertTrue("max=$maxActiveBooks", queue.isEmpty())
        }
    }

    @Test
    fun drainAllClearsQueueAndRepeatedRemoveIsSafe() {
        val queue = CacheDownloadAdmissionQueue(maxActiveBooks = 1)
        val a = request("a")
        val b = request("b")
        queue.add(a)
        queue.add(b)

        assertEquals(listOf(a, b), queue.drainAll())
        assertEquals(0, queue.size)
        assertTrue(queue.isEmpty())
        assertNull(queue.pollReady(emptySet()))
        assertFalse(queue.removeBook("a"))
        assertEquals(emptyList<CacheDownloadRequest>(), queue.drainAll())
    }

    @Test
    fun duplicateAddForActiveBookDoesNotGetStuckBehindLimit() {
        val queue = CacheDownloadAdmissionQueue(maxActiveBooks = 1)
        val active = request("active")
        queue.add(request("waiting"))
        queue.add(active)
        queue.add(active)

        assertEquals(active, queue.pollReady(setOf("active")))
        assertEquals(active, queue.pollReady(setOf("active")))
        assertNull(queue.pollReady(setOf("active")))
    }

    private fun request(bookUrl: String): CacheDownloadRequest {
        return CacheDownloadRequest(
            bookUrl = bookUrl,
            selection = ChapterSelection.Single(0),
            source = CacheDownloadSource.Batch,
        )
    }
}
