package io.legado.app.feature.reader.core.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W6-A 对抗性回归：章节源码解析、标题分段和仅正文折叠面对畸形标签、代理对、分页标记
 * 变体与占位标记碰撞时，位置空间必须自洽，正文不得丢失或重排。
 */
class ReaderChapterSourceAdversarialTest {

    private fun assertPositionsMatchSemanticContent(source: ReaderChapterSource) {
        assertEquals(
            "characterCount 必须等于 semanticContent.length",
            source.characterCount,
            source.semanticContent.length,
        )
        source.blocks.forEach { block ->
            assertTrue(
                "chapterPosition 越界：$block",
                block.chapterPosition in 0..source.characterCount,
            )
            when (block) {
                is ReaderChapterSourceBlock.Paragraph -> block.items.forEach { item ->
                    val expected = when (item) {
                        is ReaderChapterInlineSource.Text -> item.value
                        is ReaderChapterInlineSource.Image -> "\uFFFC"
                        is ReaderChapterInlineSource.BlankLine -> ""
                    }
                    val available = source.characterCount - item.chapterPosition
                    assertTrue("段落项位置越界：$item", expected.length <= available)
                    assertEquals(
                        "段落项位置与 semanticContent 不一致：$item",
                        expected,
                        source.semanticContent.substring(
                            item.chapterPosition,
                            item.chapterPosition + expected.length,
                        ),
                    )
                }

                is ReaderChapterSourceBlock.Html -> assertTrue(
                    "HTML 语义区间越界：$block",
                    block.chapterPosition + block.semanticLength <= source.characterCount,
                )

                is ReaderChapterSourceBlock.Text -> Unit
                is ReaderChapterSourceBlock.Image -> Unit
                is ReaderChapterSourceBlock.PageBreak -> Unit
            }
        }
    }

    @Test
    fun malformedImageAndUseHtmlTagsKeepSurroundingTextAsLiteralContent() {
        val paragraphs = listOf(
            "前<img src=\"a.jpg 后",
            "前<img src='b.jpg'>后",
            "前<usehtml><b>正文 后",
            "前</usehtml>后",
            "前<img src=后",
        )

        paragraphs.forEach { paragraph ->
            val source = ReaderChapterSourceParser.parse(
                chapterIndex = 0,
                title = "",
                paragraphs = listOf(paragraph),
                includeTitle = false,
                adaptSpecialStyle = true,
            )
            assertPositionsMatchSemanticContent(source)
            assertEquals(
                "畸形标签不得吞掉正文：$paragraph",
                "$paragraph\n",
                source.semanticContent,
            )
        }
    }

    @Test
    fun emptyAndDuplicateImageSourcesKeepOnePositionPerImageTag() {
        val empty = ReaderChapterSourceParser.parse(0, "", listOf("<img src=\"\">"), false, true)
        assertPositionsMatchSemanticContent(empty)
        assertEquals("\uFFFC\n", empty.semanticContent)
        val emptyItem = (empty.blocks.single() as ReaderChapterSourceBlock.Paragraph).items.single()
        assertEquals("", (emptyItem as ReaderChapterInlineSource.Image).source)

        val duplicate = ReaderChapterSourceParser.parse(
            0, "", listOf("<img src=\"a\" src=\"b\">"), false, true,
        )
        assertPositionsMatchSemanticContent(duplicate)
        assertEquals("\uFFFC\n", duplicate.semanticContent)
        val duplicateItem = (duplicate.blocks.single() as ReaderChapterSourceBlock.Paragraph).items.single()
        assertEquals("b", (duplicateItem as ReaderChapterInlineSource.Image).source)
    }

    @Test
    fun surrogatePairsEmojiAndCombiningMarksUseUtf16PositionsConsistently() {
        val first = "😀A👨‍👩‍👧‍👦e\u0301"
        val source = ReaderChapterSourceParser.parse(
            0, "", listOf(first, "后段"), false, true,
        )

        assertPositionsMatchSemanticContent(source)
        val paragraphs = source.blocks.filterIsInstance<ReaderChapterSourceBlock.Paragraph>()
        assertEquals(first, (paragraphs[0].items.single() as ReaderChapterInlineSource.Text).value)
        assertEquals(first.length + 1, paragraphs[1].chapterPosition)
        assertEquals("$first\n后段\n", source.semanticContent)
        assertEquals(source.characterCount, source.semanticContent.length)
    }

    @Test
    fun pageBreakVariantsOnlyRecognizeTheTrimmedLowercaseMarker() {
        val pageBreak = ReaderChapterSourceParser.parse(0, "", listOf("  [newpage]  "), false, true)
        assertTrue(pageBreak.blocks.single() is ReaderChapterSourceBlock.PageBreak)
        assertEquals("", pageBreak.semanticContent)

        val carriageReturn = ReaderChapterSourceParser.parse(0, "", listOf("[newpage]\r"), false, true)
        assertTrue(carriageReturn.blocks.single() is ReaderChapterSourceBlock.PageBreak)

        listOf("[NEWPAGE]", "[newpage]extra", "[[newpage]]", "[new page]").forEach { literal ->
            val source = ReaderChapterSourceParser.parse(0, "", listOf(literal), false, true)
            assertPositionsMatchSemanticContent(source)
            assertEquals(
                "$literal 应保持为可见正文",
                "$literal\n",
                source.semanticContent,
            )
        }
    }

    @Test
    fun unclosedUseHtmlKeepsFollowingBlocksAndPositionsConsistent() {
        val source = ReaderChapterSourceParser.parse(
            0,
            "",
            listOf("<usehtml><b>未闭合", "后段<img src=\"a\">"),
            false,
            true,
        )

        assertPositionsMatchSemanticContent(source)
        val paragraphs = source.blocks.filterIsInstance<ReaderChapterSourceBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("<usehtml><b>未闭合", (paragraphs[0].items.single() as ReaderChapterInlineSource.Text).value)
        assertEquals(16, paragraphs[1].chapterPosition)
        assertTrue(paragraphs[1].items.any { it is ReaderChapterInlineSource.Image })
    }

    @Test
    fun titleSegmentationSurvivesEmptyDelimiterInvalidRegexAndSurrogateDistance() {
        val title = "第一章😀：初遇"
        listOf(
            ReaderTitleSegmentation(type = 0),
            ReaderTitleSegmentation(type = 1, distance = 0),
            ReaderTitleSegmentation(type = 1, distance = -3),
            ReaderTitleSegmentation(type = 1, distance = 10_000),
            ReaderTitleSegmentation(type = 2, delimiter = ""),
            ReaderTitleSegmentation(type = 2, delimiter = " , "),
            ReaderTitleSegmentation(type = 3, delimiter = "["),
            ReaderTitleSegmentation(type = 3, delimiter = "(?<"),
        ).forEach { segmentation ->
            val blocks = segmentation.blocks(title)
            assertTrue("空标题配置不应产生空结果：$segmentation", blocks.isNotEmpty())
            assertEquals(
                "标题分段不得丢字：$segmentation",
                title,
                blocks.joinToString("") { it.value },
            )
        }

        val emojiOnly = "😀😀"
        val split = ReaderTitleSegmentation(type = 1, distance = 1).blocks(emojiOnly)
        assertEquals("代理对按 UTF-16 距离切分时也必须无损", emojiOnly, split.joinToString("") { it.value })

        assertTrue(ReaderTitleSegmentation(type = 1, distance = 1).blocks(" \n ").isEmpty())
        assertTrue(ReaderTitleSegmentation(type = 3, delimiter = "[").blocks("").isEmpty())
    }

    @Test
    fun bodyOnlyScanAndReassembleRoundTripMalformedSpecialBlocks() {
        val text = "第一行\n<usehtml>未闭合\n<img src=\"a\">\n[newpage]  \n<img src='single'>\n末尾"
        val spans = ReaderBodyOnlyFilter.scan(text)
        val folded = ReaderBodyOnlyFilter.toBodyOnly(text, spans)

        assertFalse("未闭合 usehtml 不应整段消失", folded.contains("<img src=\"a\">"))
        assertEquals(text, ReaderBodyOnlyFilter.reassemble(folded, spans))
    }

    @Test
    fun bodyTextContainingPlaceholderLiteralDoesNotStealTheRealFragmentSlot() {
        val image = "<img src=\"real.jpg\">"
        val text = "〔图片1〕$image 尾部"

        val spans = ReaderBodyOnlyFilter.scan(text)
        val folded = ReaderBodyOnlyFilter.toBodyOnly(text, spans)

        assertEquals("折叠文本必须与真实片段位置一一对应", text, ReaderBodyOnlyFilter.reassemble(folded, spans))
        assertTrue("真实图片不能被用户手写的占位标记顶掉", folded.contains("〔图片"))
        assertFalse("折叠结果里不应出现两个同号标记", folded.split("〔图片1〕").size - 1 >= 2)
    }

    @Test
    fun bodyOnlyOffsetIsMonotonicAndInsideFoldedTextForEveryRawCursorPosition() {
        val text = "ab<img src=\"x\">cd\n[newpage]\n尾"
        val spans = ReaderBodyOnlyFilter.scan(text)
        val folded = ReaderBodyOnlyFilter.toBodyOnly(text, spans)

        var previous = -1
        for (rawOffset in 0..text.length) {
            val offset = ReaderBodyOnlyFilter.toBodyOnlyOffset(rawOffset, spans)
            assertTrue("rawOffset=$rawOffset 映射越界：$offset", offset in 0..folded.length)
            assertTrue("rawOffset=$rawOffset 映射不单调：$previous -> $offset", offset >= previous)
            previous = offset
        }
    }
}
