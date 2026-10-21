package io.legado.app.model.cache

data class CacheDownloadRequest(
    val bookUrl: String,
    val selection: ChapterSelection,
    val source: CacheDownloadSource = CacheDownloadSource.Manual,
)

sealed interface ChapterSelection {
    data class Range(val start: Int, val end: Int) : ChapterSelection
    data class Indices(val values: Set<Int>) : ChapterSelection
    data class Single(val index: Int) : ChapterSelection
}

/**
 * 章节下标是目录中的 0-based 下标。超过该值的 Range 属于明显的调用方错误：
 * 队列继续处理会在游标/计数时越过 Int.MAX_VALUE 回绕；模型层先展开 `(start..end).toSet()`
 * 则可能在入队前直接 OOM。队列和模型共用这一个上限。
 */
internal const val MAX_CACHE_CHAPTER_INDEX = 1_000_000

internal fun isSupportedCacheIndex(index: Int): Boolean = index in 0..MAX_CACHE_CHAPTER_INDEX

/** 过滤非法章节选择；全部非法时返回 null，调用方应直接忽略该请求。 */
internal fun ChapterSelection.sanitizedForCache(): ChapterSelection? = when (this) {
    is ChapterSelection.Range ->
        if (start in 0..MAX_CACHE_CHAPTER_INDEX && end in start..MAX_CACHE_CHAPTER_INDEX) this else null

    is ChapterSelection.Indices -> {
        val valid = values.filterTo(linkedSetOf(), ::isSupportedCacheIndex)
        if (valid.isEmpty()) null else ChapterSelection.Indices(valid)
    }

    is ChapterSelection.Single -> if (isSupportedCacheIndex(index)) this else null
}

enum class CacheDownloadSource {
    Manual,
    Batch,
    ReadPreload,
}

data class CacheDownloadCandidate(
    val bookUrl: String,
    val chapterIndex: Int,
)

data class CacheDownloadQueueSnapshot(
    val waitingCount: Int,
)

data class CacheDownloadState(
    val isRunning: Boolean = false,
    val totalWaiting: Int = 0,
    val totalRunning: Int = 0,
    val totalPaused: Int = 0,
    val totalSuccess: Int = 0,
    val totalFailure: Int = 0,
    val books: Map<String, CacheBookDownloadState> = emptyMap(),
)

data class CacheBookDownloadState(
    val bookUrl: String,
    val waitingCount: Int = 0,
    val runningIndices: Set<Int> = emptySet(),
    val pausedIndices: Set<Int> = emptySet(),
    val failedIndices: Set<Int> = emptySet(),
    val successCount: Int = 0,
    val failureMessage: String? = null,
    val chapterProgress: Map<Int, CacheChapterProgress> = emptyMap(),
    /** 本次运行内已成功过的章节；重复完成回调不得重复累加 successCount。 */
    val succeededIndices: Set<Int> = emptySet(),
)

enum class CacheChapterProgressPhase {
    CONTENT,
    IMAGES,
}

data class CacheChapterProgress(
    val phase: CacheChapterProgressPhase,
    val completed: Int,
    val total: Int,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total
}
