package io.legado.app.ui.widget.components.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 通用 HTML 渲染组件：把不可信 HTML 解析为段落 + 行内样式/链接/图片/按钮，供 Compose 展示。
 * 书籍详情简介等纯文本 UI 复用同一实现。
 *
 * @param interactive 为 true 时启用 legado 交互约定（<button>名称@onclick:脚本</button>、
 *                   图片 src 后缀 {...,"click":"脚本"} 的点击、图片长按），并把回调暴露给调用方。
 */
@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LegadoTheme.typography.bodyMedium.merge(
        color = LegadoTheme.colorScheme.onSurface
    ),
    interactive: Boolean = false,
    onButtonClick: (name: String, click: String) -> Unit = { _, _ -> },
    onImageClick: (click: String) -> Unit = {},
    onImageLongClick: (source: String) -> Unit = {},
    imageModel: (String) -> Any? = { it },
) {
    val document = remember(html) { HtmlParser.parse(html) }
    val linkColor = LegadoTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = constraints.maxWidth
        Column {
            document.paragraphs.forEachIndexed { paragraphIndex, paragraph ->
                HtmlParagraphContent(
                    paragraph = paragraph,
                    paragraphIndex = paragraphIndex,
                    maxWidthPx = availableWidthPx,
                    baseStyle = style,
                    linkColor = linkColor,
                    interactive = interactive,
                    onButtonClick = onButtonClick,
                    onImageClick = onImageClick,
                    onImageLongClick = onImageLongClick,
                    imageModel = imageModel,
                )
            }
        }
    }
}

@Composable
private fun HtmlParagraphContent(
    paragraph: HtmlParagraph,
    paragraphIndex: Int,
    maxWidthPx: Int,
    baseStyle: TextStyle,
    linkColor: Color,
    interactive: Boolean,
    onButtonClick: (name: String, click: String) -> Unit,
    onImageClick: (click: String) -> Unit,
    onImageLongClick: (source: String) -> Unit,
    imageModel: (String) -> Any?,
) {
    if (paragraph.content.size == 1 && paragraph.content[0] is HtmlInline.HorizontalRule) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = LegadoTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
        return
    }
    Column {
        var runStart = 0
        paragraph.content.forEachIndexed { index, inline ->
            if (inline is HtmlInline.Button) {
                if (index > runStart) {
                    HtmlTextRun(
                        inlines = paragraph.content.subList(runStart, index),
                        paragraphIndex = paragraphIndex,
                        maxWidthPx = maxWidthPx,
                        baseStyle = baseStyle,
                        linkColor = linkColor,
                        interactive = interactive,
                        onImageClick = onImageClick,
                        onImageLongClick = onImageLongClick,
                        imageModel = imageModel,
                    )
                }
                HtmlButtonChip(
                    button = inline,
                    baseStyle = baseStyle,
                    interactive = interactive,
                    onButtonClick = onButtonClick,
                )
                runStart = index + 1
            }
        }
        if (runStart < paragraph.content.size) {
            HtmlTextRun(
                inlines = paragraph.content.subList(runStart, paragraph.content.size),
                paragraphIndex = paragraphIndex,
                maxWidthPx = maxWidthPx,
                baseStyle = baseStyle,
                linkColor = linkColor,
                interactive = interactive,
                onImageClick = onImageClick,
                onImageLongClick = onImageLongClick,
                imageModel = imageModel,
            )
        }
    }
}

/**
 * 渲染一段不含按钮的行内内容（文本/图片/换行），按钮由调用方拆出。
 */
@Composable
private fun HtmlTextRun(
    inlines: List<HtmlInline>,
    paragraphIndex: Int,
    maxWidthPx: Int,
    baseStyle: TextStyle,
    linkColor: Color,
    interactive: Boolean,
    onImageClick: (click: String) -> Unit,
    onImageLongClick: (source: String) -> Unit,
    imageModel: (String) -> Any?,
) {
    val density = LocalDensity.current
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    var imageIndex = 0

    inlines.forEach { inline ->
        if (inline !is HtmlInline.Image) return@forEach
        val id = "html-image-$paragraphIndex-${imageIndex++}"
        // The trailing JSON is a Legado image option, not part of the URL.
        // Passing the raw value to Coil makes otherwise valid book-source images fail to load.
        val painter = rememberAsyncImagePainter(model = imageModel(inline.loadSource))
        val painterState by painter.state.collectAsState()
        val image = (painterState as? AsyncImagePainter.State.Success)?.result?.image
        val drawableWidth = image?.width?.coerceAtLeast(1) ?: 1
        val drawableHeight = image?.height?.coerceAtLeast(1) ?: 1
        val scale = if (maxWidthPx in 1..<drawableWidth) {
            maxWidthPx.toFloat() / drawableWidth
        } else {
            1f
        }
        val width = with(density) { (drawableWidth * scale).toDp().toSp() }
        val height = with(density) { (drawableHeight * scale).toDp().toSp() }
        val imageModifier = if (interactive) {
            Modifier.combinedClickable(
                onClick = { if (inline.click != null) onImageClick(inline.click) },
                onLongClick = { onImageLongClick(inline.source) },
            )
        } else {
            Modifier
        }
        inlineContent[id] = InlineTextContent(
            placeholder = Placeholder(
                width = width.value.coerceAtLeast(1f).sp,
                height = height.value.coerceAtLeast(1f).sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
            )
        ) {
            Image(
                painter = painter,
                contentDescription = inline.description,
                modifier = Modifier
                    .fillMaxSize()
                    .then(imageModifier),
                contentScale = ContentScale.Fit,
            )
        }
    }

    val annotatedText = buildAnnotatedString {
        imageIndex = 0
        inlines.forEach { inline ->
            when (inline) {
                is HtmlInline.Text -> appendStyledText(inline, linkColor)
                is HtmlInline.Image -> {
                    val id = "html-image-$paragraphIndex-${imageIndex++}"
                    if (interactive && inline.click != null) {
                        appendInlineContent(id, inline.description ?: "image")
                    } else if (inline.link != null) {
                        withLink(LinkAnnotation.Url(inline.link)) {
                            appendInlineContent(id, inline.description ?: "image")
                        }
                    } else {
                        appendInlineContent(id, inline.description ?: "image")
                    }
                }

                HtmlInline.LineBreak -> append('\n')
                HtmlInline.HorizontalRule -> append('\n')
                is HtmlInline.Button -> Unit
            }
        }
    }

    BasicText(
        text = annotatedText,
        inlineContent = inlineContent,
        style = baseStyle,
    )
}

/**
 * 独立渲染的书源交互按钮 chip，不依赖行内占位测量，保证可见。
 */
@Composable
private fun HtmlButtonChip(
    button: HtmlInline.Button,
    baseStyle: TextStyle,
    interactive: Boolean,
    onButtonClick: (name: String, click: String) -> Unit,
) {
    val baseSize = if (baseStyle.fontSize.value.isNaN()) 14.sp else baseStyle.fontSize
    val scaledSize = baseSize * 0.9f
    val buttonFontSize = if (!scaledSize.value.isNaN() && scaledSize.value < 11f) {
        11.sp
    } else {
        scaledSize
    }
    val buttonStyle = baseStyle.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = buttonFontSize,
    )
    val clickModifier = if (interactive) {
        Modifier.clickable { onButtonClick(button.name, button.click) }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LegadoTheme.colorScheme.surfaceContainer)
            .then(clickModifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = button.name,
            style = buttonStyle,
            color = LegadoTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private fun AnnotatedString.Builder.appendStyledText(
    text: HtmlInline.Text,
    linkColor: Color,
) {
    val spanStyle = text.style.toSpanStyle(
        fallbackColor = if (text.link != null) linkColor else null,
        underlineLink = text.link != null,
    )
    if (text.link != null) {
        withLink(LinkAnnotation.Url(text.link)) {
            withStyle(spanStyle) { append(text.value) }
        }
    } else {
        withStyle(spanStyle) { append(text.value) }
    }
}

private fun HtmlTextStyle.toSpanStyle(
    fallbackColor: Color?,
    underlineLink: Boolean,
): SpanStyle {
    val decorations = buildList {
        if (underline || underlineLink) add(TextDecoration.Underline)
        if (strikeThrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        color = parseHtmlColor(color) ?: fallbackColor ?: Color.Unspecified,
        background = parseHtmlColor(backgroundColor) ?: Color.Unspecified,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontSize = if (relativeFontSize == 1f) androidx.compose.ui.unit.TextUnit.Unspecified else relativeFontSize.em,
        textDecoration = decorations.takeIf { it.isNotEmpty() }?.let(TextDecoration::combine),
        baselineShift = when (baseline) {
            HtmlBaseline.Normal -> null
            HtmlBaseline.Subscript -> BaselineShift.Subscript
            HtmlBaseline.Superscript -> BaselineShift.Superscript
        },
    )
}

private fun parseHtmlColor(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim().lowercase()
    return runCatching {
        when {
            normalized.startsWith("rgb(") -> {
                val channels = normalized.substringAfter('(').substringBefore(')').split(',')
                    .map { it.trim().toInt().coerceIn(0, 255) }
                if (channels.size != 3) return null
                Color(channels[0], channels[1], channels[2])
            }

            normalized.startsWith("rgba(") -> {
                val channels =
                    normalized.substringAfter('(').substringBefore(')').split(',').map(String::trim)
                if (channels.size != 4) return null
                Color(
                    red = channels[0].toInt().coerceIn(0, 255),
                    green = channels[1].toInt().coerceIn(0, 255),
                    blue = channels[2].toInt().coerceIn(0, 255),
                    alpha = (channels[3].toFloat().coerceIn(0f, 1f) * 255).toInt(),
                )
            }

            else -> Color(normalized.toColorInt())
        }
    }.getOrNull()
}

internal data class HtmlDocument(
    val paragraphs: List<HtmlParagraph>,
)

internal data class HtmlParagraph(
    val content: List<HtmlInline>,
)

internal sealed interface HtmlInline {
    data class Text(
        val value: String,
        val style: HtmlTextStyle,
        val link: String? = null,
    ) : HtmlInline

    data class Image(
        val source: String,
        val loadSource: String,
        val description: String?,
        val link: String? = null,
        val click: String? = null,
    ) : HtmlInline

    /** `<button>名称@onclick:脚本</button>`，legado 书源交互约定。 */
    data class Button(
        val name: String,
        val click: String,
    ) : HtmlInline

    data object LineBreak : HtmlInline

    data object HorizontalRule : HtmlInline
}

internal data class HtmlTextStyle(
    val color: String? = null,
    val backgroundColor: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val relativeFontSize: Float = 1f,
    val baseline: HtmlBaseline = HtmlBaseline.Normal,
)

internal enum class HtmlBaseline { Normal, Subscript, Superscript }

internal object HtmlParser {

    private val blockTags = setOf(
        "address", "article", "aside", "blockquote", "div", "dl", "fieldset", "figcaption",
        "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
        "hr", "main", "nav", "ol", "p", "pre", "section", "table", "ul",
    )

    private const val BUTTON_SPLIT = "@onclick:"

    // 与上游 AnalyzeUrl.paramPattern 一致：图片 src 后的 JSON 参数，例如 ",{"width":"80%","click":"showCmt(...)"}"
    private val imageParamPattern = Regex("\\s*,\\s*(?=\\{)")

    private fun parseImageOptions(source: String): Pair<String, String?> {
        val urlMatcher = imageParamPattern.find(source) ?: return source to null
        val urlOptionStr = source.substring(urlMatcher.range.last + 1)
        val click = GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()?.get("click")
        return source.substring(0, urlMatcher.range.first) to click
    }

    private fun collectButtonText(element: Element): String {
        val sb = StringBuilder()
        collectText(element, sb)
        return sb.toString()
    }

    private fun collectText(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> sb.append(normalizeWhitespace(node.wholeText))
            is Element -> node.childNodes().forEach { collectText(it, sb) }
            else -> Unit
        }
    }

    fun parse(html: String): HtmlDocument {
        val paragraphs = mutableListOf<HtmlParagraph>()
        parseContainer(
            nodes = Jsoup.parseBodyFragment(html).body().childNodes(),
            inheritedStyle = HtmlTextStyle(),
            inheritedLink = null,
            paragraphs = paragraphs,
        )
        return HtmlDocument(paragraphs)
    }

    private fun parseContainer(
        nodes: List<Node>,
        inheritedStyle: HtmlTextStyle,
        inheritedLink: String?,
        paragraphs: MutableList<HtmlParagraph>,
    ) {
        var current = mutableListOf<HtmlInline>()

        fun flush() {
            trimBoundaryWhitespace(current)
            if (current.isNotEmpty()) paragraphs += HtmlParagraph(current)
            current = mutableListOf()
        }

        nodes.forEach { node ->
            if (node is Element && node.normalName() in blockTags) {
                flush()
                parseBlock(node, inheritedStyle, inheritedLink, paragraphs)
            } else {
                parseInline(node, inheritedStyle, inheritedLink, current)
            }
        }
        flush()
    }

    private fun parseBlock(
        element: Element,
        inheritedStyle: HtmlTextStyle,
        inheritedLink: String?,
        paragraphs: MutableList<HtmlParagraph>,
    ) {
        val style = mergeStyle(inheritedStyle, element)
        when (element.normalName()) {
            "div", "section", "article", "main", "header", "footer", "nav", "aside", "form",
            "fieldset", "figure", "figcaption", "address", "blockquote" -> parseContainer(
                element.childNodes(), style, inheritedLink, paragraphs
            )

            "ul", "ol" -> parseList(element, style, inheritedLink, paragraphs)
            "table" -> element.select("tr").forEach { row ->
                val content = mutableListOf<HtmlInline>()
                row.select(":scope > th, :scope > td").forEachIndexed { index, cell ->
                    if (index > 0) appendText(content, "  ", style, inheritedLink)
                    cell.childNodes().forEach { parseInline(it, style, inheritedLink, content) }
                }
                addParagraph(content, paragraphs)
            }

            "hr" -> paragraphs += HtmlParagraph(listOf(HtmlInline.HorizontalRule))

            else -> {
                val content = mutableListOf<HtmlInline>()
                element.childNodes().forEach { parseInline(it, style, inheritedLink, content) }
                addParagraph(content, paragraphs)
            }
        }
    }

    private fun parseList(
        list: Element,
        style: HtmlTextStyle,
        link: String?,
        paragraphs: MutableList<HtmlParagraph>,
    ) {
        val ordered = list.normalName() == "ol"
        val start = list.attr("start").toIntOrNull() ?: 1
        list.children().filter { it.normalName() == "li" }.forEachIndexed { index, item ->
            val content = mutableListOf<HtmlInline>()
            appendText(content, if (ordered) "${start + index}. " else "• ", style, link)
            item.childNodes().forEach { parseInline(it, style, link, content) }
            addParagraph(content, paragraphs)
        }
    }

    private fun parseInline(
        node: Node,
        inheritedStyle: HtmlTextStyle,
        inheritedLink: String?,
        output: MutableList<HtmlInline>,
    ) {
        when (node) {
            is TextNode -> appendText(
                output,
                normalizeWhitespace(node.wholeText),
                inheritedStyle,
                inheritedLink
            )

            is Element -> {
                val style = mergeStyle(inheritedStyle, node)
                val link = if (node.normalName() == "a") node.attr("href")
                    .ifBlank { inheritedLink } else inheritedLink
                when (node.normalName()) {
                    "br" -> output += HtmlInline.LineBreak
                    "hr" -> {
                        output += HtmlInline.LineBreak
                        output += HtmlInline.HorizontalRule
                        output += HtmlInline.LineBreak
                    }

                    "img" -> node.attr("src").takeIf(String::isNotBlank)?.let { source ->
                        val (loadSource, click) = parseImageOptions(source)
                        output += HtmlInline.Image(
                            source,
                            loadSource,
                            node.attr("alt").ifBlank { null },
                            link,
                            click,
                        )
                    }

                    "button" -> {
                        val buttonText = collectButtonText(node)
                        val parts = buttonText.split(BUTTON_SPLIT, limit = 2)
                        if (parts.size == 2) {
                            output += HtmlInline.Button(parts[0].trim(), parts[1].trim())
                        } else {
                            appendText(output, buttonText, style, inheritedLink)
                        }
                    }

                    in blockTags -> {
                        if (output.isNotEmpty() && output.last() !is HtmlInline.LineBreak) {
                            output += HtmlInline.LineBreak
                        }
                        node.childNodes().forEach { parseInline(it, style, link, output) }
                        if (output.isNotEmpty() && output.last() !is HtmlInline.LineBreak) {
                            output += HtmlInline.LineBreak
                        }
                    }

                    else -> node.childNodes().forEach { parseInline(it, style, link, output) }
                }
            }
        }
    }

    private fun appendText(
        output: MutableList<HtmlInline>,
        value: String,
        style: HtmlTextStyle,
        link: String?,
    ) {
        if (value.isEmpty()) return
        val previous = output.lastOrNull() as? HtmlInline.Text
        if (previous != null && previous.style == style && previous.link == link) {
            output[output.lastIndex] = previous.copy(value = previous.value + value)
        } else {
            output += HtmlInline.Text(value, style, link)
        }
    }

    private fun addParagraph(
        content: MutableList<HtmlInline>,
        paragraphs: MutableList<HtmlParagraph>,
    ) {
        trimBoundaryWhitespace(content)
        if (content.isNotEmpty()) paragraphs += HtmlParagraph(content)
    }

    private fun trimBoundaryWhitespace(content: MutableList<HtmlInline>) {
        val first = content.firstOrNull() as? HtmlInline.Text
        if (first != null) {
            val trimmed = first.value.trimStart()
            if (trimmed.isEmpty()) content.removeAt(0) else content[0] = first.copy(value = trimmed)
        }
        val last = content.lastOrNull() as? HtmlInline.Text
        if (last != null) {
            val trimmed = last.value.trimEnd()
            if (trimmed.isEmpty()) content.removeAt(content.lastIndex)
            else content[content.lastIndex] = last.copy(value = trimmed)
        }
        while (content.firstOrNull() is HtmlInline.LineBreak) content.removeAt(0)
        while (content.lastOrNull() is HtmlInline.LineBreak) content.removeAt(content.lastIndex)
    }

    private fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ")

    private fun mergeStyle(parent: HtmlTextStyle, element: Element): HtmlTextStyle {
        var style = when (element.normalName()) {
            "b", "strong" -> parent.copy(bold = true)
            "i", "em", "cite", "dfn" -> parent.copy(italic = true)
            "u", "ins" -> parent.copy(underline = true)
            "s", "strike", "del" -> parent.copy(strikeThrough = true)
            "sub" -> parent.copy(
                relativeFontSize = parent.relativeFontSize * 0.8f,
                baseline = HtmlBaseline.Subscript
            )

            "sup" -> parent.copy(
                relativeFontSize = parent.relativeFontSize * 0.8f,
                baseline = HtmlBaseline.Superscript
            )

            "small" -> parent.copy(relativeFontSize = parent.relativeFontSize * 0.8f)
            "big" -> parent.copy(relativeFontSize = parent.relativeFontSize * 1.2f)
            "h1" -> parent.copy(bold = true, relativeFontSize = parent.relativeFontSize * 2f)
            "h2" -> parent.copy(bold = true, relativeFontSize = parent.relativeFontSize * 1.5f)
            "h3" -> parent.copy(bold = true, relativeFontSize = parent.relativeFontSize * 1.17f)
            "h4", "h5", "h6" -> parent.copy(bold = true)
            "mark" -> parent.copy(backgroundColor = "#ffff00")
            else -> parent
        }
        if (element.normalName() == "font") {
            element.attr("color").takeIf(String::isNotBlank)?.let { style = style.copy(color = it) }
            element.attr("size").toIntOrNull()?.let { size ->
                val scale = when (size.coerceIn(1, 7)) {
                    1 -> .63f; 2 -> .82f; 3 -> 1f; 4 -> 1.13f; 5 -> 1.5f; 6 -> 2f; else -> 3f
                }
                style = style.copy(relativeFontSize = scale)
            }
        }
        element.attr("style").split(';').forEach { declaration ->
            val (name, rawValue) = declaration.split(':', limit = 2).takeIf { it.size == 2 }
                ?: return@forEach
            val value = rawValue.trim().removeSuffix("!important").trim()
            style = when (name.trim().lowercase()) {
                "color" -> style.copy(color = value)
                "background", "background-color" -> style.copy(backgroundColor = value)
                "font-weight" -> style.copy(
                    bold = value.equals("bold", true) || value.toIntOrNull()
                        ?.let { it >= 600 } == true)

                "font-style" -> style.copy(
                    italic = value.equals(
                        "italic",
                        true
                    ) || value.equals("oblique", true)
                )

                "text-decoration", "text-decoration-line" -> style.copy(
                    underline = "underline" in value.lowercase(),
                    strikeThrough = "line-through" in value.lowercase(),
                )

                "font-size" -> parseRelativeFontSize(value)?.let { style.copy(relativeFontSize = it) }
                    ?: style

                "vertical-align" -> style.copy(
                    baseline = when (value.lowercase()) {
                        "sub" -> HtmlBaseline.Subscript
                        "super" -> HtmlBaseline.Superscript
                        else -> HtmlBaseline.Normal
                    }
                )

                else -> style
            }
        }
        return style
    }

    private fun parseRelativeFontSize(value: String): Float? = when {
        value.endsWith("em", true) -> value.dropLast(2).trim().toFloatOrNull()
        value.endsWith("%") -> value.dropLast(1).trim().toFloatOrNull()?.div(100f)
        value.endsWith("px", true) -> value.dropLast(2).trim().toFloatOrNull()?.div(16f)
        value.equals("smaller", true) -> 0.8f
        value.equals("larger", true) -> 1.2f
        else -> null
    }?.coerceIn(0.5f, 4f)
}
