package io.legado.app.model.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitCacheBookFifoAdversarialTest {

    @Test
    fun movingUnknownBooksIsNoOpAndDoesNotChangeOrder() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")

        fifo.moveToHead("missing")
        fifo.moveToTail("missing")

        assertEquals(listOf("a", "b"), fifo.snapshot())
        assertEquals(2, fifo.size)
        assertFalse(fifo.contains("missing"))
    }

    @Test
    fun repeatedEnsureAndRemoveKeepOrderAndMembershipStable() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")
        fifo.ensure("a")
        fifo.ensure("b")

        assertEquals(listOf("a", "b"), fifo.snapshot())
        assertTrue(fifo.remove("a"))
        assertFalse(fifo.remove("a"))
        assertTrue(fifo.remove("b"))
        assertFalse(fifo.remove("b"))
        assertTrue(fifo.isEmpty())
        assertNull(fifo.headWhere { true })
    }

    @Test
    fun clearRemovesMembershipAndAllowsReuse() {
        val fifo = ExplicitCacheBookFifo()
        fifo.ensure("a")
        fifo.ensure("b")

        fifo.clear()

        assertEquals(0, fifo.size)
        assertTrue(fifo.isEmpty())
        assertFalse(fifo.contains("a"))
        assertNull(fifo.headWhere { true })

        fifo.ensure("c")
        assertEquals(listOf("c"), fifo.snapshot())
    }
}
