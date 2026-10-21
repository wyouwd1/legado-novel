package io.legado.app.feature.reader.core.source

import io.legado.app.constant.AppPattern
import io.legado.app.feature.reader.core.source.ReaderBodyOnlyFilter.reassemble
import io.legado.app.feature.reader.core.source.ReaderBodyOnlyFilter.toBodyOnly

/**
 * 「仅显示正文」折叠器：把阅读器**不以文字呈现**的片段换成占位标记，供正文编辑弹层使用。
 *
 * 判据与 [ReaderChapterSourceParser] 完全一致——渲染器只把这三种片段当结构处理，其余源码一律
 * 按字面文本显示，所以折叠它们不会隐藏用户真正读到的字：
 *
 * - 段内 `<img ...>`（[AppPattern.imgPattern]）：渲染成图片。源码可能是几 KB 的
 *   `data:image/...;base64`，塞在编辑框里既不可读，光标一划就容易误删。
 * - `<usehtml>...</usehtml>` 整段：渲染成交互 HTML 块，源码同样不可读。
 * - `[newpage]` 整段：分页标记。
 *
 * 折叠只发生在**显示层**：原始片段按 [HiddenSpan.raw] 记住，[reassemble] 再按标记出现顺序
 * 原样插回。因此
 *
 * - 编辑正文不会丢图片/HTML/分页标记，也不会改写标签内部字符；
 * - 未被折叠的字符与换行位置就地保留（占位标记原地替换原片段），排版不被重排；
 * - 用户若删掉或改坏某个标记，[reassemble] 仍按顺序把原始片段插回——最坏情况是位置漂移，
 *   不会丢内容。
 */
object ReaderBodyOnlyFilter {

    private const val USE_HTML_START = "<usehtml>"
    private const val USE_HTML_END = "</usehtml>"
    private const val PAGE_BREAK = "[newpage]"

    /** 标记编号碰撞时的搜索上限，防止恶意正文把扫描变成无限循环。 */
    private const val MARKER_DISAMBIGUATION_LIMIT = 10_000

    /** 折叠片段的类型，[label] 直接出现在占位标记里（形如 `〔图片1〕`）。 */
    enum class Kind(val label: String) {
        Image("图片"),
        Html("富文本"),
        PageBreak("分页"),
    }

    /**
     * 一段被折叠的原文。[index] 正常从 1 开始，标记里带序号，让 [reassemble] 能逐个定位。
     * 若正文本身恰好包含同号字面量，[scan] 会整体后移编号以避开碰撞。
     */
    data class HiddenSpan(
        val index: Int,
        val start: Int,
        val end: Int,
        val raw: String,
        val kind: Kind,
    ) {
        val marker: String get() = "〔${kind.label}$index〕"
    }

    /**
     * 按出现顺序扫描全部非正文片段（[toBodyOnly] / [reassemble] 共用同一份结果）。
     *
     * [adaptSpecialStyle] 必须与渲染器同源（`ReadSettings.adaptSpecialStyle`）：该开关关闭时
     * `[newpage]`、`<usehtml>` 会按字面文字显示，此时只折叠 `<img>`，否则会藏掉用户看得见的字。
     */
    fun scan(text: String, adaptSpecialStyle: Boolean = true): List<HiddenSpan> {
        val spans = mutableListOf<HiddenSpan>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trim()
            when {
                adaptSpecialStyle && trimmed == PAGE_BREAK ->
                    spans += span(spans.size, lineStart, lineEnd, line, Kind.PageBreak)

                adaptSpecialStyle && trimmed.startsWith(USE_HTML_START) && trimmed.endsWith(
                    USE_HTML_END
                ) ->
                    spans += span(spans.size, lineStart, lineEnd, line, Kind.Html)

                else -> AppPattern.imgPattern.findAll(line).forEach { match ->
                    spans += span(
                        spans.size,
                        lineStart + match.range.first,
                        lineStart + match.range.last + 1,
                        match.value,
                        Kind.Image,
                    )
                }
            }
            if (lineEnd >= text.length) break
            lineStart = lineEnd + 1
        }
        return spans.disambiguatedAgainst(text)
    }

    /**
     * 用户正文本身可能包含 `〔图片1〕` 这类字符串。若直接用它做折叠标记，
     * [toBodyOnly] 会生成两个同号标记，[reassemble] 会先命中用户手写的那一个，
     * 把真实图片插到错误位置。这里只在检测到碰撞时整体后移编号，保持既有
     * “正常正文用 1 开始”的输出不变。
     */
    private fun List<HiddenSpan>.disambiguatedAgainst(text: String): List<HiddenSpan> {
        if (isEmpty() || !text.contains('〔')) return this
        if (none { text.contains(it.marker) }) return this
        var indexOffset = 1
        while (indexOffset <= MARKER_DISAMBIGUATION_LIMIT) {
            val candidate = mapIndexed { localIndex, span ->
                span.copy(index = indexOffset + localIndex)
            }
            if (candidate.none { text.contains(it.marker) }) return candidate
            indexOffset += size
        }
        // 病态文本包含数以万计的候选标记时保持旧输出，至少不改变内容归属的已知行为。
        return this
    }

    /** 把 [spans] 换成占位标记；[spans] 必须由同一份 [text] 扫描得到。 */
    fun toBodyOnly(text: String, spans: List<HiddenSpan> = scan(text)): String {
        if (spans.isEmpty()) return text
        val sb = StringBuilder(text.length)
        var cursor = 0
        for (span in spans) {
            if (span.start < cursor || span.end > text.length) continue
            sb.append(text, cursor, span.start)
            sb.append(span.marker)
            cursor = span.end
        }
        if (cursor < text.length) sb.append(text, cursor, text.length)
        return sb.toString()
    }

    /** 按标记出现顺序把原始片段插回 [bodyOnlyText]；标记缺失时顺序插回，不丢内容。 */
    fun reassemble(bodyOnlyText: String, spans: List<HiddenSpan>): String {
        if (spans.isEmpty()) return bodyOnlyText
        val sb = StringBuilder(bodyOnlyText.length + spans.sumOf { it.raw.length })
        var cursor = 0
        for (span in spans) {
            val found = bodyOnlyText.indexOf(span.marker, cursor)
            val at = if (found >= 0) found else bodyOnlyText.length
            sb.append(bodyOnlyText, cursor, at)
            sb.append(span.raw)
            cursor = if (found >= 0) at + span.marker.length else bodyOnlyText.length
        }
        if (cursor < bodyOnlyText.length) sb.append(bodyOnlyText, cursor, bodyOnlyText.length)
        return sb.toString()
    }

    /** 原文光标位置 → 折叠后文本的光标位置（折叠掉的字符换成标记长度）。 */
    fun toBodyOnlyOffset(rawOffset: Int, spans: List<HiddenSpan>): Int {
        var offset = rawOffset
        for (span in spans) {
            if (span.start >= rawOffset) break
            val hidden = (minOf(span.end, rawOffset) - span.start).coerceAtLeast(0)
            offset += span.marker.length - hidden
        }
        return offset.coerceAtLeast(0)
    }

    private fun span(index: Int, start: Int, end: Int, raw: String, kind: Kind) =
        HiddenSpan(index + 1, start, end, raw, kind)
}
