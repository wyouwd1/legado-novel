package io.legado.app.feature.reader.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CastMarkersTextTest {

    @Test
    fun `injectParagraph inserts labels at successive quote anchors`() {
        val tracker = CastMarkers.CastQuoteTracker()
        val first = CastMarkers.injectParagraph("“第一句”", tracker, mapOf(0 to "<<甲（池）>>"))
        val second = CastMarkers.injectParagraph("“第二句”", tracker, mapOf(1 to "<<乙>>"))

        assertEquals("“<<甲（池）>>第一句”", first)
        assertEquals("“<<乙>>第二句”", second)
        assertEquals(1, tracker.lastCastOrdinal)
    }

    @Test
    fun `injectParagraph advances tracker even without labels`() {
        val tracker = CastMarkers.CastQuoteTracker()
        val text = CastMarkers.injectParagraph("“没有分配”", tracker, emptyMap())

        assertEquals("“没有分配”", text)
        assertEquals(0, tracker.lastCastOrdinal)
    }

    @Test
    fun `strip removes markers without touching body text`() {
        assertEquals("“你好”", CastMarkers.strip("“<<甲（池）>>你好”"))
        assertEquals("“你好”", CastMarkers.strip("“<<甲>>你好”"))
    }

    @Test
    fun `stripKeepingOffsets maps spoken index back to stored text`() {
        val spoken = CastMarkers.stripKeepingOffsets("a<<甲>>b", base = 10)

        assertEquals("ab", spoken.text)
        assertEquals(10, spoken.storedOf(0))
        assertEquals(16, spoken.storedOf(1))
        assertEquals(17, spoken.storedOf(99))
    }

    @Test
    fun `tracker reports nested closed span with maxDepth`() {
        val tracker = CastMarkers.CastQuoteTracker(maxDepth = 2)
        "“外‘内’外”".forEach { tracker.feed(it) }

        val span = tracker.lastClosedSpan
        assertTrue(span != null)
        assertEquals(0, span!!.ordinal)
        assertEquals(0, span.start)
        assertEquals(7, span.endExclusive)
    }
}
