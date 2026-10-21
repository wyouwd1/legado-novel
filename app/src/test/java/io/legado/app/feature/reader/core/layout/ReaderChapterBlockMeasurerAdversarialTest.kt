package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderHtmlImageSpanExtent
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import io.legado.app.feature.reader.core.source.ReaderTitleSegmentation
import io.legado.app.feature.reader.core.style.ReaderCharacterStyle
import io.legado.app.feature.reader.core.style.ReaderStyleRange
import io.legado.app.feature.reader.core.style.ReaderStyleTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W6-A 对抗性回归：章节测量对空源、空白段、超长段、非法图片尺寸、空 HTML 解析结果和
 * 越界样式区间必须不崩、不丢字。
 */
class ReaderChapterBlockMeasurerAdversarialTest {
    private val bodyStyle = ReaderTextStyle(colorArgb = 0xff111111.toInt(), fontSizePx = 10f)
    private val measureStyle = ReaderChapterMeasureStyle(
        bodyStyle = bodyStyle,
        titleStyle = bodyStyle.copy(fontSizePx = 20f),
        bodyIndentCharacters = 2,
        bodyAlignment = ReaderTextAlignment.JUSTIFY,
        titleAlignment = ReaderTextAlignment.CENTER,
    )
    private val shaper = ReaderTextShaper { text ->
        GlyphClusters(text.map(Char::toString), List(text.length) { 10f })
    }

    private fun measurer(
        imageDimensions: suspend (String) -> ReaderImageDimensions? = { null },
        htmlResolver: (String, Int) -> List<ReaderHtmlParagraph>? = { _, _ -> null },
    ) = ReaderChapterBlockMeasurer(
        bodyShaper = shaper,
        titleShaper = shaper,
        imageDimensionsResolver = imageDimensions,
        htmlSourceResolver = { html, position -> htmlResolver(html, position) },
    )

    private fun successBlocks(result: ReaderChapterMeasureResult): List<ReaderMeasuredBlock> =
        (result as ReaderChapterMeasureResult.Success).blocks

    private fun textOf(blocks: List<ReaderMeasuredBlock>): String = buildString {
        blocks.forEach { block ->
            when (block) {
                is ReaderMeasuredBlock.Paragraph -> append(block.value.text)
                is ReaderMeasuredBlock.InlineParagraph -> block.items.forEach { item ->
                    append(
                        when (item) {
                            is ReaderMeasuredInlineItem.Text -> item.value
                            is ReaderMeasuredInlineItem.Image -> "\uFFFC"
                        },
                    )
                }

                is ReaderMeasuredBlock.Image -> append('\uFFFC')
                is ReaderMeasuredBlock.BlankLine -> append('\n')
                is ReaderMeasuredBlock.Rule -> append("—")
                ReaderMeasuredBlock.PageBreak -> Unit
            }
        }
    }

    @Test
    fun emptySourceAndBlankParagraphsMeasureWithoutSyntheticText() = runBlocking {
        val empty = measurer().measure(
            ReaderChapterSource(0, "", emptyList(), 0),
            measureStyle,
        )
        assertTrue(empty is ReaderChapterMeasureResult.Success)
        assertTrue(successBlocks(empty).isEmpty())

        val blank = measurer().measure(
            ReaderChapterSource(
                0, "",
                listOf(
                    ReaderChapterSourceBlock.Text("", 0),
                    ReaderChapterSourceBlock.Text("   ", 1),
                    ReaderChapterSourceBlock.Text("", 4),
                ),
                4,
            ),
            measureStyle,
        )
        val blocks = successBlocks(blank)
        assertEquals("空白段不得生成伪文字", "   ", textOf(blocks))
    }

    @Test
    fun emptyHtmlResolverResultFallsBackToSemanticTextInsteadOfDroppingBody() = runBlocking {
        val source = ReaderChapterSource(
            chapterIndex = 1,
            title = "",
            blocks = listOf(ReaderChapterSourceBlock.Html("<b>正文</b>", 0, semanticLength = 3)),
            characterCount = 3,
            semanticContent = "正文\n",
        )

        val result = measurer(htmlResolver = { _, _ -> emptyList() }).measure(source, measureStyle)

        val items = successBlocks(result).filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
            .flatMap { it.items }
            .filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals("空 HTML 解析结果不得吞掉正文", "正文", items.joinToString("") { it.value })
        assertEquals(listOf(0, 1), items.map { it.chapterPosition })
    }

    @Test
    fun htmlResolverReturningOnlyBlankItemsFallsBackToSemanticText() = runBlocking {
        val source = ReaderChapterSource(
            chapterIndex = 1,
            title = "",
            blocks = listOf(ReaderChapterSourceBlock.Html("<b>正文</b>", 0, semanticLength = 2)),
            characterCount = 3,
            semanticContent = "正文\n",
        )

        val result = measurer(htmlResolver = { _, _ ->
            listOf(ReaderHtmlParagraph(listOf(ReaderChapterInlineSource.Text("", 0))))
        }).measure(source, measureStyle)

        val items = successBlocks(result).filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
            .flatMap { it.items }
            .filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals("正文", items.joinToString("") { it.value })
    }

    @Test
    fun htmlResolverReturningVeryLongTextKeepsEveryCharacter() = runBlocking {
        val htmlText = "富文本".repeat(2_000)
        val source = ReaderChapterSource(
            1, "",
            listOf(ReaderChapterSourceBlock.Html("<p>long</p>", 0, semanticLength = htmlText.length)),
            htmlText.length + 1,
            "$htmlText\n",
        )

        val result = measurer(htmlResolver = { _, _ ->
            listOf(ReaderHtmlParagraph(listOf(ReaderChapterInlineSource.Text(htmlText, 0))))
        }).measure(source, measureStyle)

        val glyphs = successBlocks(result).filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
            .flatMap { it.items }
            .filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals(htmlText, glyphs.joinToString("") { it.value })
    }

    @Test
    fun veryLongParagraphKeepsEveryCharacter() = runBlocking {
        val text = "正文".repeat(2_500)
        val source = ReaderChapterSource(
            0, "",
            listOf(ReaderChapterSourceBlock.Text(text, 0)),
            text.length,
            "$text\n",
        )

        val blocks = successBlocks(measurer().measure(source, measureStyle))

        assertEquals(text, textOf(blocks))
        val glyphs = blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
            .flatMap { it.items }
            .filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals(text, glyphs.joinToString("") { it.value })
    }

    @Test
    fun illegalImageDimensionsDoNotDropSurroundingText() = runBlocking {
        val source = ReaderChapterSource(
            0, "",
            listOf(
                ReaderChapterSourceBlock.Paragraph(
                    listOf(
                        ReaderChapterInlineSource.Text("前", 0),
                        ReaderChapterInlineSource.Image("bad", 1),
                        ReaderChapterInlineSource.Text("后", 2),
                    ),
                    0,
                ),
            ),
            3,
            "前\uFFFC后\n",
        )
        val invalidSizes = listOf(
            ReaderImageDimensions(Float.NaN, Float.NaN),
            ReaderImageDimensions(Float.POSITIVE_INFINITY, 10f),
            ReaderImageDimensions(0f, 0f),
            ReaderImageDimensions(-5f, -5f),
        )

        invalidSizes.forEach { size ->
            val result = measurer(imageDimensions = { size }).measure(source, measureStyle)
            val blocks = successBlocks(result)
            assertEquals("图片尺寸 $size 不得丢正文", "前￼后", textOf(blocks))
            val inline = blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>().single()
            val image = inline.items.filterIsInstance<ReaderMeasuredInlineItem.Image>().single()
            assertTrue("行内图宽必须有限：$size", image.widthPx.isFinite())
            assertTrue("行内图高必须有限：$size", image.heightPx.isFinite())
        }
    }

    @Test
    fun outOfRangeStyleRangesDoNotThrowOrDropGlyphs() = runBlocking {
        val source = ReaderChapterSource(
            0, "",
            listOf(ReaderChapterSourceBlock.Text("甲乙丙丁", 0)),
            4,
            "甲乙丙丁\n",
        )
        val styleRanges = listOf(
            ReaderStyleRange(Int.MIN_VALUE, Int.MAX_VALUE, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 1)),
            ReaderStyleRange(-100, -50, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 2)),
            ReaderStyleRange(2, 2, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 3)),
            ReaderStyleRange(3, Int.MAX_VALUE, ReaderStyleTarget.BODY, ReaderCharacterStyle(italic = true)),
            ReaderStyleRange(Int.MAX_VALUE - 1, Int.MAX_VALUE, ReaderStyleTarget.TITLE, ReaderCharacterStyle(colorArgb = 4)),
        )

        val blocks = successBlocks(
            measurer().measure(source, measureStyle.copy(styleRanges = styleRanges)),
        )

        val glyphs = blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
            .flatMap { it.items }
            .filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals("甲乙丙丁", glyphs.joinToString("") { it.value })
        assertEquals(listOf(0, 1, 2, 3), glyphs.map { it.chapterPosition })
    }

    @Test
    fun htmlSpanExtentWithInvalidGeometryDoesNotDropText() = runBlocking {
        val source = ReaderChapterSource(
            0, "",
            listOf(
                ReaderChapterSourceBlock.Paragraph(
                    listOf(
                        ReaderChapterInlineSource.Text("甲", 0),
                        ReaderChapterInlineSource.Image(
                            "bad",
                            1,
                            htmlSpanExtent = ReaderHtmlImageSpanExtent(Float.NaN, Float.NaN),
                        ),
                        ReaderChapterInlineSource.Text("乙", 2),
                    ),
                    0,
                ),
            ),
            3,
            "甲\uFFFC乙\n",
        )

        val blocks = successBlocks(
            measurer(imageDimensions = { ReaderImageDimensions(10f, 10f) }).measure(source, measureStyle),
        )

        assertEquals("甲￼乙", textOf(blocks))
        val image = blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>().single()
            .items.filterIsInstance<ReaderMeasuredInlineItem.Image>().single()
        assertTrue("非法 HTML span 宽必须回落为有限值", image.widthPx.isFinite())
        assertTrue("非法 HTML span 高必须回落为有限值", image.heightPx.isFinite())
    }

    @Test
    fun titleVisibilityRebuildKeepsBodyBlocksAndPositions() = runBlocking {
        val source = ReaderChapterSource(
            0, "章名", listOf(ReaderChapterSourceBlock.Text("正文", 0)), 2, "正文\n",
        )
        val shown = source.withTitleVisibility(true, ReaderTitleSegmentation(type = 1, distance = 1))
        val hidden = shown.withTitleVisibility(false)

        assertEquals(source.blocks, hidden.blocks)
        assertEquals(source.semanticContent, shown.semanticContent)
        assertEquals(source.characterCount, shown.characterCount)
    }
}
