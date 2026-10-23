package io.legado.app.ui.book.changesource

import io.legado.app.data.entities.SearchBook

/**
 * 换源结果的字数过滤：只保留章节字数达到 [minWordCount] 的结果。
 *
 * [minWordCount] <= 0 表示不过滤；字数未知（-1）在启用过滤时一律不通过，
 * 避免把「没取到字数」误当成「字数足够」。
 */
internal fun SearchBook.meetsMinWordCount(minWordCount: Int): Boolean =
    minWordCount <= 0 || chapterWordCount >= minWordCount
