package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W6-A 对抗性回归：断行器面对非法宽度、零宽视口与超长输入时的终止性、单调性和边界。
 *
 * 这些输入在真实设备上来自字体量测的异常返回（NaN/Inf）和极端布局（1px 视口、padding
 * 吃掉全部宽度），不允许把 NaN 传播到 [io.legado.app.feature.reader.core.model.ReaderPage]。
 */
class ChineseLineBreakerAdversarialTest {

    private fun breaker(
        clusters: List<String>,
        widths: List<Float>,
        widthPx: Int,
        letterSpacingPx: Float = 0f,
    ) = ChineseLineBreaker(
        clusters = clusters,
        widthsPx = widths,
        indentCharacters = 0,
        widthPx = widthPx,
        ideographWidthPx = 10f,
        letterSpacingPx = letterSpacingPx,
    )

    private fun assertLineRangesAreMonotonicAndBounded(
        clusters: List<String>,
        breaker: ChineseLineBreaker,
    ) {
        val starts = breaker.lineStarts
        val clusterStarts = breaker.lineClusterStarts
        val widths = breaker.lineWidthsPx
        assertEquals("每个视觉行必须有起点和簇起点", breaker.lineCount + 1, starts.size)
        assertEquals("每个视觉行必须有起点和簇起点", breaker.lineCount + 1, clusterStarts.size)
        assertEquals(0, starts.first())
        assertEquals("行起点必须覆盖全部字符", clusters.sumOf(String::length), starts.last())
        assertEquals(0, clusterStarts.first())
        assertEquals("簇起点必须覆盖全部簇", clusters.size, clusterStarts.last())
        for (index in 0 until breaker.lineCount) {
            val from = starts[index]
            val until = starts[index + 1]
            assertTrue("行 $index 出现反向区间 $from..$until", from <= until)
            val clusterFrom = clusterStarts[index]
            val clusterUntil = clusterStarts[index + 1]
            assertTrue("行 $index 出现反向簇区间 $clusterFrom..$clusterUntil", clusterFrom <= clusterUntil)
            assertTrue("行 $index 起点越界", from in 0..clusters.sumOf(String::length))
            assertTrue("行 $index 终点越界", until in 0..clusters.sumOf(String::length))
        }
        starts.toList().zipWithNext().forEach { (left, right) ->
            assertTrue("lineStarts 非单调：$left > $right", left <= right)
        }
        clusterStarts.toList().zipWithNext().forEach { (left, right) ->
            assertTrue("lineClusterStarts 非单调：$left > $right", left <= right)
        }
        widths.forEach { width ->
            assertTrue("行宽必须是有限值，实际 $width", width.isFinite())
            assertTrue("行宽不得为负，实际 $width", width >= 0f)
        }
    }

    @Test
    fun emptyTextProducesNoLinesAndZeroLengthInputKeepOneStart() {
        val empty = breaker(emptyList(), emptyList(), widthPx = 0)
        assertEquals(0, empty.lineCount)
        assertEquals(listOf(0), empty.lineStarts.toList())
        assertEquals(listOf(0), empty.lineClusterStarts.toList())
        assertTrue(empty.lineWidthsPx.isEmpty())
    }

    @Test
    fun singleCharacterAtZeroWidthStillProducesOneCharacterLine() {
        val clusters = listOf("字")
        val result = breaker(clusters, listOf(10f), widthPx = 0)

        assertLineRangesAreMonotonicAndBounded(clusters, result)
        assertEquals(1, result.lineStarts.last())
        assertEquals(10f, result.lineWidthsPx.sum(), 0f)
    }

    @Test
    fun zeroAndNegativeViewportWidthsProduceMonotonicRangesWithoutReversedLines() {
        val clusters = "正文内容".map(Char::toString)
        val widths = List(clusters.size) { 10f }
        listOf(0, -1, -100).forEach { viewportWidth ->
            val result = breaker(clusters, widths, widthPx = viewportWidth)
            assertLineRangesAreMonotonicAndBounded(clusters, result)
            assertTrue("零/负宽度不得吞掉字符", result.lineStarts.last() == clusters.size)
            assertTrue(
                "零/负宽度最多只能比簇数多一行，实际 ${result.lineCount}",
                result.lineCount <= clusters.size + 1,
            )
        }
    }

    @Test
    fun onePixelViewportKeepsEveryClusterAndTerminates() {
        val clusters = "超长文本".repeat(200).map(Char::toString)
        val widths = List(clusters.size) { 10f }

        val result = breaker(clusters, widths, widthPx = 1)

        assertLineRangesAreMonotonicAndBounded(clusters, result)
        assertEquals("每个字符都必须出现在某个行区间里", clusters.size, result.lineStarts.last())
        assertTrue("1px 视口不应产生超过每簇一行的结果", result.lineCount <= clusters.size + 1)
    }

    @Test
    fun nonFiniteAndNegativeClusterWidthsNeverLeakNaNOrNegativeLineWidths() {
        val clusters = "正文内容".map(Char::toString)
        val cases = listOf(
            listOf(10f, Float.NaN, 10f, 10f),
            listOf(10f, Float.POSITIVE_INFINITY, 10f, 10f),
            listOf(10f, Float.NEGATIVE_INFINITY, 10f, 10f),
            listOf(10f, -5f, 10f, 10f),
            listOf(Float.NaN, Float.NaN, Float.NaN, Float.NaN),
            listOf(Float.POSITIVE_INFINITY),
        )
        cases.forEach { widths ->
            val caseClusters = if (widths.size == clusters.size) clusters
            else List(widths.size) { clusters[it] }
            val result = breaker(caseClusters, widths, widthPx = 25)
            assertLineRangesAreMonotonicAndBounded(caseClusters, result)
            assertEquals(
                "非法宽度不得吞字符：$widths",
                caseClusters.sumOf(String::length),
                result.lineStarts.last(),
            )
        }
    }

    @Test
    fun longUnbrokenLatinWordStillTerminatesAndCoversEveryCharacter() {
        val clusters = List(5_000) { "a" }
        val widths = List(clusters.size) { 10f }

        val result = breaker(clusters, widths, widthPx = 40)

        assertLineRangesAreMonotonicAndBounded(clusters, result)
        assertEquals(5_000, result.lineStarts.last())
        assertTrue("超长单词必须产生有限行数", result.lineCount in 1..5_000)
    }

    @Test
    fun zeroWidthClusterDoesNotCreateReversedLineRange() {
        val clusters = listOf("\u200B", "正", "\u200B", "文")
        val widths = listOf(0f, 10f, 0f, 10f)

        val result = breaker(clusters, widths, widthPx = 10)

        assertLineRangesAreMonotonicAndBounded(clusters, result)
        assertEquals(4, result.lineStarts.last())
    }
}
