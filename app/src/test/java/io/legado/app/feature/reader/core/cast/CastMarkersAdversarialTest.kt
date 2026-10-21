package io.legado.app.feature.reader.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W6-A 对抗性回归：角色标记的剥离偏移必须对任意引擎下标保持单调、有界；引号跟踪器
 * 面对未配对/同符号引号时只推进状态，不吞文本。
 */
class CastMarkersAdversarialTest {

    @Test
    fun stripKeepingOffsetsIsMonotonicAndBoundedForEverySpokenIndex() {
        val texts = listOf(
            "",
            "正文",
            "<<甲>>正文",
            "正文<<甲>>",
            "<<甲>><<乙>>正文",
            "正<<甲>>文<<乙>>尾",
            "<<甲>>“对白”<<乙>>",
            "<<甲>>".repeat(50),
        )
        val bases = listOf(0, 1, 7, 100)
        texts.forEach { text ->
            bases.forEach { base ->
                val spoken = CastMarkers.stripKeepingOffsets(text, base)
                var previous = Int.MIN_VALUE
                for (index in -2..spoken.text.length + 2) {
                    val stored = spoken.storedOf(index)
                    assertTrue(
                        "storedOf($index) 越界：text=[$text] base=$base stored=$stored",
                        stored in base..(base + text.length),
                    )
                    assertTrue(
                        "storedOf 非单调：text=[$text] base=$base $previous -> $stored",
                        stored >= previous,
                    )
                    previous = stored
                }
                assertEquals(
                    "末尾必须映射到正文末端",
                    base + text.length,
                    spoken.storedOf(spoken.text.length),
                )
            }
        }
    }

    @Test
    fun extremeSpokenIndicesClampWithoutOverflow() {
        val text = "<<甲>>正文"
        val spoken = CastMarkers.stripKeepingOffsets(text, base = 10)

        assertEquals(10, spoken.storedOf(-1))
        assertEquals(10, spoken.storedOf(Int.MIN_VALUE))
        assertEquals(10 + text.length, spoken.storedOf(Int.MAX_VALUE))
        assertEquals(10 + text.length, spoken.storedOf(spoken.text.length + 100))
    }

    @Test
    fun markersAtFirstAndLastPositionsMapToStoredBoundaries() {
        val text = "<<甲>>正文<<乙>>"
        val base = 20
        val spoken = CastMarkers.stripKeepingOffsets(text, base)

        assertEquals("正文", spoken.text)
        assertEquals(base + "<<甲>>".length, spoken.storedOf(0))
        assertEquals(base + text.length, spoken.storedOf(spoken.text.length))
        assertEquals(base + "<<甲>>".length + 1, spoken.storedOf(1))
        assertEquals(base + text.length, spoken.storedOf(99))
        assertEquals(base, spoken.storedOf(-1))
    }

    @Test
    fun sameSymbolAndUnpairedQuotesCloseInFeedOrderWithoutThrowing() {
        val tracker = CastMarkers.CastQuoteTracker()
        val closed = mutableListOf<CastMarkers.ClosedSpan>()
        "\"甲\"\"乙\"".forEach { character ->
            tracker.feed(character)
            tracker.lastClosedSpan?.let { closed += it }
        }

        assertEquals(
            "同符号引号必须按开/闭交替处理，未配对引号不得伪造闭合",
            listOf(CastMarkers.ClosedSpan(0, 0, 3), CastMarkers.ClosedSpan(1, 3, 6)),
            closed,
        )
    }

    @Test
    fun nestedQuotesAtMaxDepthStillReportTheOuterSpan() {
        val tracker = CastMarkers.CastQuoteTracker(maxDepth = 2)
        "“外「内」外”".forEach(tracker::feed)

        assertEquals(CastMarkers.ClosedSpan(0, 0, 7), tracker.lastClosedSpan)
        assertEquals(7, tracker.fedCharacters)
    }

    @Test
    fun injectParagraphNeverLosesVisibleTextAndKeepsTrackerInSync() {
        val tracker = CastMarkers.CastQuoteTracker()
        val first = "“甲”与“乙"
        val injected = CastMarkers.injectParagraph(
            first,
            tracker,
            mapOf(0 to "<<甲（池）>>", 1 to "<<乙>>"),
        )

        assertEquals("“<<甲（池）>>甲”与“<<乙>>乙", injected)
        assertEquals("“甲”与“乙", CastMarkers.strip(injected))
        assertNull("段落不以闭引号结束时不产生闭合区间", tracker.lastClosedSpan)
        assertEquals(1, tracker.lastCastOrdinal)
    }

    @Test
    fun blankAndMarkerOnlyInputsRemainIdempotent() {
        assertEquals("", CastMarkers.strip(""))
        assertEquals("", CastMarkers.strip("<<甲>><<乙>>"))
        val spoken = CastMarkers.stripKeepingOffsets("<<甲>>", base = 5)
        assertEquals("", spoken.text)
        assertEquals(5, spoken.storedOf(-1))
        assertEquals(10, spoken.storedOf(0))
        assertFalse(CastMarkers.stripKeepingOffsets("正文", 0).text.contains("<<"))
    }

    @Test
    fun passThroughMappingStaysInsideItsOwnSegment() {
        val spoken = CastMarkers.Spoken.passThrough("正文", base = 8)
        assertEquals(8, spoken.storedOf(-1))
        assertEquals(10, spoken.storedOf(2))
        assertEquals(10, spoken.storedOf(99))
    }
}
