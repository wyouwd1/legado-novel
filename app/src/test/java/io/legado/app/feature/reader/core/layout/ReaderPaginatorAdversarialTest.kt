package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W6-A 对抗性回归：分页会话面对空块、零尺寸、非法图片尺寸和连续分页符时必须终止、
 * 不丢文本，也不把 NaN/Infinity 写进页面几何。
 */
class ReaderPaginatorAdversarialTest {
    private val style = ReaderTextStyle(colorArgb = 0xff111111.toInt(), fontSizePx = 10f)
    private val config = ReaderPaginationConfig(
        chapterIndex = 7,
        chapterTitle = "对抗",
        viewportWidthPx = 40,
        viewportHeightPx = 45,
        paddingLeftPx = 0f,
        paddingTopPx = 0f,
        paddingRightPx = 0f,
        paddingBottomPx = 5f,
        lineHeightPx = 20f,
        baselineOffsetPx = 15f,
    )

    private fun paragraph(text: String, position: Int = 0, widthPx: Float = 10f) =
        ReaderMeasuredParagraph(
            text = text,
            clusters = text.map(Char::toString),
            clusterWidthsPx = List(text.length) { widthPx },
            style = style,
            chapterPosition = position,
        )

    private fun assertFiniteGeometry(pages: List<io.legado.app.feature.reader.core.model.ReaderPage>) {
        pages.forEach { page ->
            assertTrue("contentTopPx 非有限：${page.contentTopPx}", page.contentTopPx.isFinite())
            assertTrue("contentBottomPx 非有限：${page.contentBottomPx}", page.contentBottomPx.isFinite())
            assertTrue("scrollExtentPx 非有限：${page.scrollExtentPx}", page.scrollExtentPx.isFinite())
            page.elements.forEach { element ->
                val bounds = element.bounds
                assertTrue("元素左边非有限：$element", bounds.left.isFinite())
                assertTrue("元素上边非有限：$element", bounds.top.isFinite())
                assertTrue("元素右边非有限：$element", bounds.right.isFinite())
                assertTrue("元素下边非有限：$element", bounds.bottom.isFinite())
            }
        }
    }

    private fun textOf(pages: List<io.legado.app.feature.reader.core.model.ReaderPage>) =
        pages.joinToString("") { it.text }

    @Test
    fun emptyBlocksAndEmptyParagraphsProduceNoSyntheticPages() {
        assertTrue(ReaderPaginator.paginateBlocks(emptyList(), config).isEmpty())
        val emptyParagraph = ReaderMeasuredBlock.Paragraph(
            ReaderMeasuredParagraph("", emptyList(), emptyList(), style, 0),
        )
        assertTrue(ReaderPaginator.paginateBlocks(listOf(emptyParagraph), config).isEmpty())
    }

    @Test
    fun zeroContentWidthAndTwoColumnsWithOnePixelViewportTerminateWithStableText() {
        val blocks = listOf(
            ReaderMeasuredBlock.Paragraph(paragraph("正文内容")),
            ReaderMeasuredBlock.PageBreak,
            ReaderMeasuredBlock.PageBreak,
            ReaderMeasuredBlock.Paragraph(paragraph("第二段", position = 4)),
        )

        val pages = ReaderPaginator.paginateBlocks(
            blocks,
            config.copy(viewportWidthPx = 1, columnCount = 2),
        )

        assertEquals("正文内容\n第二段", textOf(pages))
        assertFiniteGeometry(pages)
        assertTrue("零宽视口不得无限出页，实际 ${pages.size}", pages.size <= 32)
    }

    @Test
    fun zeroAndNegativeLineHeightsTerminateAndKeepEveryCharacter() {
        listOf(0f, -20f, -0.5f).forEach { lineHeight ->
            val pages = ReaderPaginator.paginateBlocks(
                listOf(
                    ReaderMeasuredBlock.Paragraph(paragraph("甲乙丙丁")),
                    ReaderMeasuredBlock.Paragraph(paragraph("戊己", position = 4)),
                ),
                config.copy(lineHeightPx = lineHeight),
            )
            assertEquals("lineHeight=$lineHeight", "甲乙丙丁\n戊己", textOf(pages))
            assertFiniteGeometry(pages)
            assertTrue("lineHeight=$lineHeight 出页数无界", pages.size <= 8)
        }
    }

    @Test
    fun nonFiniteImageIntrinsicSizesNeverLeakNaNGeometryIntoFollowingText() {
        val dimensions = listOf(
            Float.NaN to Float.NaN,
            Float.POSITIVE_INFINITY to 10f,
            10f to Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY to Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY to -5f,
        )

        dimensions.forEach { (width, height) ->
            val pages = ReaderPaginator.paginateBlocks(
                listOf(
                    ReaderMeasuredBlock.Image("bad", width, height, 0),
                    ReaderMeasuredBlock.Paragraph(paragraph("正文", position = 1)),
                ),
                config,
            )
            assertEquals("图片尺寸 ($width,$height) 不得吞正文", "￼正文", textOf(pages))
            assertFiniteGeometry(pages)
            assertTrue("图片尺寸 ($width,$height) 出页数无界", pages.size <= 4)
        }
    }

    @Test
    fun nonFiniteParagraphClusterWidthsKeepFiniteBoundsAndText() {
        val widths = listOf(Float.NaN, Float.POSITIVE_INFINITY, -10f, 10f)
        val paragraph = ReaderMeasuredParagraph(
            text = "正文内容",
            clusters = "正文内容".map(Char::toString),
            clusterWidthsPx = widths,
            style = style,
            chapterPosition = 0,
        )

        val pages = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.Paragraph(paragraph)),
            config,
        )

        assertEquals("正文内容", textOf(pages))
        assertFiniteGeometry(pages)
    }

    @Test
    fun nonFiniteInlineImageKeepsFollowingGlyphsFinite() {
        val inline = ReaderMeasuredBlock.InlineParagraph(
            items = listOf(
                ReaderMeasuredInlineItem.Text("甲", 10f, style, 0),
                ReaderMeasuredInlineItem.Image("bad", Float.NaN, Float.NaN, 1),
                ReaderMeasuredInlineItem.Text("乙", 10f, style, 2),
            ),
            indentCharacters = 0,
            alignment = ReaderTextAlignment.START,
            lineHeightPx = 20f,
            baselineOffsetPx = 15f,
            baseTextSizePx = 10f,
        )

        val pages = ReaderPaginator.paginateBlocks(listOf(inline), config)

        assertEquals("甲￼乙", textOf(pages))
        assertFiniteGeometry(pages)
    }

    @Test
    fun consecutivePageBreaksDoNotCreateEmptyOrUnboundedPages() {
        val pages = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.PageBreak,
                ReaderMeasuredBlock.PageBreak,
                ReaderMeasuredBlock.Paragraph(paragraph("正文")),
                ReaderMeasuredBlock.PageBreak,
                ReaderMeasuredBlock.PageBreak,
                ReaderMeasuredBlock.Paragraph(paragraph("续", position = 2)),
            ),
            config,
        )

        assertEquals("正文\n续", textOf(pages))
        assertFiniteGeometry(pages)
        assertTrue("连续分页符出现在空页上不应产生空页", pages.none { it.text.isEmpty() })
        assertFalse(pages.size > 4)
    }

    @Test
    fun pageHighLessThanLineHeightStillProducesFinitePagesAndAllText() {
        val pages = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.Paragraph(paragraph("一二三四五六")),
                ReaderMeasuredBlock.Paragraph(paragraph("七八", position = 6)),
            ),
            config.copy(viewportHeightPx = 5, paddingBottomPx = 0f),
        )

        assertEquals("一二三四五六\n七八", textOf(pages))
        assertFiniteGeometry(pages)
        assertTrue("每行一页时也必须终止", pages.size <= 8)
        assertTrue(pages.all { it.elements.isNotEmpty() })
    }

    @Test
    fun negativeOrZeroImageIntrinsicSizesKeepPlaceholderGeometryStable() {
        listOf(0f to 0f, -5f to -5f, 0f to 20f, 20f to 0f).forEach { (width, height) ->
            val pages = ReaderPaginator.paginateBlocks(
                listOf(
                    ReaderMeasuredBlock.Image("bad", width, height, 0),
                    ReaderMeasuredBlock.Paragraph(paragraph("后文", position = 1)),
                ),
                config,
            )
            assertEquals("图片尺寸 ($width,$height)", "￼后文", textOf(pages))
            assertFiniteGeometry(pages)
            val image = pages.flatMap { it.elements }.filterIsInstance<ReaderElement.Image>().single()
            assertTrue("图片宽度必须非负", image.bounds.width >= 0f)
            assertTrue("图片高度必须非负", image.bounds.height >= 0f)
        }
    }
}
