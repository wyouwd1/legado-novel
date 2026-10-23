package io.legado.app.domain.usecase

import android.app.Application
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.gateway.SourceSearch
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.MatchMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Deterministic scheduler-level coverage for multi-source search.
 *
 * Virtual time keeps the 60 s dead-source tail measurable without a wall-clock benchmark; the
 * production default uses `Dispatchers.IO`, while this test injects the test dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SearchBooksUseCaseSchedulingTest {

    @Test
    fun `baseline scheduler reproduces the sixty second tail`() = runTest {
        val samples = List(5) {
            runWorkload(
                sourceCount = 60,
                concurrency = 16,
                slowTailCount = 2,
                slowDelayMs = 59_000L,
                deadlineMs = 120_000L,
                sourceTimeoutMs = 60_000L,
            )
        }
        val p50 = samples.map { it.elapsedMs }.sorted()[2]
        val p95 = samples.map { it.elapsedMs }.sorted()[4]

        println("SEARCH_BENCH baselineP50=$p50 baselineP95=$p95 samples=${samples.map { it.elapsedMs }}")
        assertEquals(59_600L, p50)
        assertEquals(59_600L, p95)
        assertEquals(60, samples.first().resultCount)
        assertEquals(100L, samples.first().firstResultMs)
        assertTrue(samples.first().finished)
    }

    @Test
    fun `deadline returns partial results and frees the slow tail`() = runTest {
        val samples = List(5) {
            runWorkload(
                sourceCount = 60,
                concurrency = DEFAULT_SEARCH_CONCURRENCY,
                slowTailCount = 2,
                slowDelayMs = 59_000L,
                deadlineMs = SEARCH_DEADLINE_MS,
            )
        }
        val result = samples.first()
        val p50 = samples.map { it.elapsedMs }.sorted()[2]
        val p95 = samples.map { it.elapsedMs }.sorted()[4]

        println("SEARCH_BENCH deadlineP50=$p50 deadlineP95=$p95 samples=${samples.map { it.elapsedMs }}")
        assertEquals(20_000L, p50)
        assertEquals(20_000L, p95)
        assertTrue(result.finished)
        assertTrue(result.partial)
        assertEquals(false, result.isEmpty)
        assertEquals(false, result.hasMore)
        assertEquals(DEFAULT_SEARCH_CONCURRENCY, result.maxConcurrent)
        assertEquals(2, result.pendingSources)
        assertEquals(58, result.resultCount)
        assertEquals(1, result.sourceListLoads)
        assertEquals(0, result.sourceRowLookups)
    }

    @Test
    fun `deadline with zero completed results is partial not empty`() = runTest {
        val result = runWorkload(
            sourceCount = 1,
            concurrency = 1,
            slowTailCount = 1,
            slowDelayMs = 59_000L,
            deadlineMs = 1_000L,
        )

        assertTrue(result.partial)
        assertEquals(false, result.isEmpty)
        assertEquals(1, result.pendingSources)
        assertEquals(0, result.resultCount)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `duplicate editions merge into one result with all origins`() = runTest {
        val result = runWorkload(
            sourceCount = 3,
            concurrency = 3,
            duplicateNameAuthor = true,
        )

        assertEquals(1, result.resultCount)
        assertEquals(3, result.maxOrigins)
        assertEquals(false, result.partial)
    }

    @Test
    fun `duplicate book url merges origins without duplicating the result`() = runTest {
        val result = runWorkload(
            sourceCount = 3,
            concurrency = 3,
            duplicateBookUrl = true,
        )

        assertEquals(1, result.resultCount)
        assertEquals(3, result.maxOrigins)
    }

    @Test
    fun `book url alias stays merged when a later source changes its title`() = runTest {
        val sources = listOf(
            BookSource(
                bookSourceUrl = "https://source-0.example",
                bookSourceName = "source-0",
                searchUrl = "https://source-0.example/search?key={{key}}",
            ),
            BookSource(
                bookSourceUrl = "https://source-1.example",
                bookSourceName = "source-1",
                searchUrl = "https://source-1.example/search?key={{key}}",
            ),
            BookSource(
                bookSourceUrl = "https://source-2.example",
                bookSourceName = "source-2",
                searchUrl = "https://source-2.example/search?key={{key}}",
            ),
        )
        val gateway = FakeGateway(sources)
        val sourceSearch = SourceSearch { source, _, _, _ ->
            when (source.bookSourceUrl) {
                "https://source-0.example" -> listOf(
                    SearchBook(
                        bookUrl = "https://book-a.example",
                        origin = source.bookSourceUrl,
                        name = "shared-title",
                        author = "shared-author",
                    )
                )
                "https://source-1.example" -> listOf(
                    SearchBook(
                        bookUrl = "https://book-b.example",
                        origin = source.bookSourceUrl,
                        name = "shared-title",
                        author = "shared-author",
                    )
                )
                else -> listOf(
                    SearchBook(
                        bookUrl = "https://book-b.example",
                        origin = source.bookSourceUrl,
                        name = "changed-title",
                        author = "changed-author",
                    )
                )
            }
        }
        val useCase = SearchBooksUseCase(
            gateway = gateway,
            sourceSearch = sourceSearch,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        var resultCount = 0
        var maxOrigins = 0

        useCase.execute(
            BookSearchRequest(
                keyword = "keyword",
                page = 1,
                scope = BookSearchScope(""),
                matchMode = MatchMode.DEFAULT,
                concurrency = 3,
            ),
            BookSearchControl(),
        ).collect { event ->
            when (event) {
                is SearchRunEvent.Progress -> {
                    resultCount = event.resultCount
                    event.upsertBooks.forEach { maxOrigins = maxOf(maxOrigins, it.origins.size) }
                }
                else -> Unit
            }
        }

        assertEquals(1, resultCount)
        assertEquals(3, maxOrigins)
    }

    @Test
    fun `result cap retains one thousand books and stops pagination`() = runTest {
        val result = runWorkload(
            sourceCount = 1001,
            concurrency = DEFAULT_SEARCH_CONCURRENCY,
            deadlineMs = 120_000L,
        )

        assertEquals(1000, result.resultCount)
        assertEquals(false, result.hasMore)
        assertEquals(false, result.isEmpty)
    }

    @Test
    fun `hard concurrency cap clamps an oversized request`() = runTest {
        val result = runWorkload(
            sourceCount = 100,
            concurrency = 256,
            deadlineMs = 120_000L,
        )

        assertTrue(result.maxConcurrent <= MAX_SEARCH_CONCURRENCY)
        assertEquals(100, result.resultCount)
    }

    @Test
    fun `page two is forwarded to every source`() = runTest {
        val result = runWorkload(
            sourceCount = 3,
            concurrency = 3,
            page = 2,
        )

        assertEquals(setOf(2), result.requestPages)
        assertEquals(3, result.resultCount)
    }

    @Test
    fun `paused search defers work until resumed and still completes`() = runTest {
        val sources = (0 until 2).map { index ->
            BookSource(
                bookSourceUrl = "https://source-$index.example",
                bookSourceName = "source-$index",
                searchUrl = "https://source-$index.example/search?key={{key}}&page={{page}}",
            )
        }
        val gateway = FakeGateway(sources)
        val sourceSearch = SourceSearch { source, _, _, _ ->
            delay(100L)
            listOf(
                SearchBook(
                    bookUrl = "https://book-${source.bookSourceName}.example",
                    origin = source.bookSourceUrl,
                    originName = source.bookSourceName,
                    name = source.bookSourceName,
                    author = "author",
                )
            )
        }
        val useCase = SearchBooksUseCase(
            gateway = gateway,
            sourceSearch = sourceSearch,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val control = BookSearchControl().apply { pause() }
        val events = mutableListOf<SearchRunEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase.execute(
                BookSearchRequest(
                    keyword = "keyword",
                    page = 1,
                    scope = BookSearchScope(""),
                    matchMode = MatchMode.DEFAULT,
                ),
                control,
            ).collect { events += it }
        }

        // Longer than the 20 s wall-clock deadline: paused time must not consume the budget.
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(0, events.count { it is SearchRunEvent.Progress })

        control.resume()
        advanceUntilIdle()

        val finished = events.filterIsInstance<SearchRunEvent.Finished>().single()
        assertEquals(false, finished.isPartial)
        assertEquals(2, events.filterIsInstance<SearchRunEvent.Progress>().last().resultCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `per-source timeout fails one source without blocking the rest`() = runTest {
        val result = runWorkload(
            sourceCount = 2,
            concurrency = 2,
            slowTailCount = 1,
            slowDelayMs = 50_000L,
            deadlineMs = 120_000L,
            sourceTimeoutMs = 1_000L,
        )

        assertEquals(1_000L, result.elapsedMs)
        assertEquals(1, result.resultCount)
        assertEquals(false, result.partial)
        assertEquals(0, result.pendingSources)
        assertTrue(result.finished)
    }

    @Test
    fun `external cancellation emits no finished event and releases searches`() = runTest {
        val sources = listOf(
            BookSource(
                bookSourceUrl = "https://source-cancel.example",
                bookSourceName = "cancel",
                searchUrl = "https://source-cancel.example/search?key={{key}}&page={{page}}",
            )
        )
        val gateway = FakeGateway(sources)
        val started = CompletableDeferred<Unit>()
        var active = 0
        val sourceSearch = SourceSearch { _, _, _, _ ->
            active++
            started.complete(Unit)
            try {
                delay(120_000L)
            } finally {
                active--
            }
            emptyList()
        }
        val useCase = SearchBooksUseCase(
            gateway = gateway,
            sourceSearch = sourceSearch,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val events = mutableListOf<SearchRunEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase.execute(
                BookSearchRequest(
                    keyword = "keyword",
                    page = 1,
                    scope = BookSearchScope(""),
                    matchMode = MatchMode.DEFAULT,
                ),
                BookSearchControl(),
            ).collect { events += it }
        }
        started.await()
        job.cancelAndJoin()

        assertEquals(0, active)
        assertTrue(events.none { it is SearchRunEvent.Finished })
    }

    @Test
    fun `dedicated concurrency improves healthy-source completion`() = runTest {
        val at16 = runWorkload(sourceCount = 120, concurrency = 16)
        val at32 = runWorkload(sourceCount = 120, concurrency = DEFAULT_SEARCH_CONCURRENCY)

        println("SEARCH_BENCH healthy16=$at16 healthy32=$at32")
        assertTrue(
            "32-way search should beat 16-way by at least 20%: " +
                "16=$at16, 32=$at32",
            at32.elapsedMs < at16.elapsedMs * 0.8,
        )
        assertEquals(120, at32.resultCount)
        assertEquals(at16.firstResultMs, at32.firstResultMs)
        assertTrue(at32.maxConcurrent <= MAX_SEARCH_CONCURRENCY)
        assertTrue(at32.finished)
        assertEquals(false, at32.partial)
        assertEquals(false, at32.isEmpty)
        assertTrue(at32.hasMore)
        assertEquals(1, at32.sourceListLoads)
    }

    private suspend fun TestScope.runWorkload(
        sourceCount: Int,
        concurrency: Int,
        slowTailCount: Int = 0,
        slowDelayMs: Long = 59_000L,
        deadlineMs: Long = SEARCH_DEADLINE_MS,
        sourceTimeoutMs: Long = 25_000L,
        duplicateNameAuthor: Boolean = false,
        duplicateBookUrl: Boolean = false,
        page: Int = 1,
    ): BenchResult {
        val sources = (0 until sourceCount).map { index ->
            BookSource(
                bookSourceUrl = "https://source-$index.example",
                bookSourceName = "source-$index",
                searchUrl = "https://source-$index.example/search?key={{key}}&page={{page}}",
            )
        }
        val gateway = FakeGateway(sources)
        var active = 0
        var maxActive = 0
        val requestPages = mutableSetOf<Int>()
        val sourceSearch = SourceSearch { source, _, requestedPage, _ ->
            requestPages += requestedPage
            active++
            maxActive = maxOf(maxActive, active)
            try {
                val index = source.bookSourceUrl
                    .removePrefix("https://source-")
                    .removeSuffix(".example")
                    .toInt()
                val isSlow = index >= sourceCount - slowTailCount
                delay(if (isSlow) slowDelayMs else 100L + (index % 5) * 50L)
                listOf(
                    SearchBook(
                        bookUrl = if (duplicateBookUrl) {
                            "https://book-shared.example"
                        } else {
                            "https://book-$index.example"
                        },
                        origin = source.bookSourceUrl,
                        originName = source.bookSourceName,
                        name = if (duplicateNameAuthor) "shared-book" else "book-$index",
                        author = if (duplicateNameAuthor) "shared-author" else "author-$index",
                    )
                )
            } finally {
                active--
            }
        }

        val useCase = SearchBooksUseCase(
            gateway = gateway,
            sourceSearch = sourceSearch,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            sourceTimeoutMs = sourceTimeoutMs,
        )
        val startedAt = testScheduler.currentTime
        var firstResultAt: Long? = null
        var resultCount = 0
        var maxOrigins = 0
        var partial = false
        var pendingSources = 0
        var finished = false
        var hasMore = false
        var isEmpty = false
        useCase.execute(
            BookSearchRequest(
                keyword = "keyword",
                page = page,
                scope = BookSearchScope(""),
                matchMode = MatchMode.DEFAULT,
                concurrency = concurrency,
                deadlineMs = deadlineMs,
            ),
            BookSearchControl(),
        ).collect { event ->
            when (event) {
                is SearchRunEvent.Progress -> {
                    if (event.upsertBooks.isNotEmpty() && firstResultAt == null) {
                        firstResultAt = testScheduler.currentTime - startedAt
                    }
                    resultCount = event.resultCount
                    event.upsertBooks.forEach { maxOrigins = maxOf(maxOrigins, it.origins.size) }
                }

                is SearchRunEvent.Finished -> {
                    partial = event.isPartial
                    pendingSources = event.pendingSources
                    hasMore = event.hasMore
                    isEmpty = event.isEmpty
                    finished = true
                }

                SearchRunEvent.Started -> Unit
            }
        }
        return BenchResult(
            elapsedMs = testScheduler.currentTime - startedAt,
            firstResultMs = firstResultAt ?: -1,
            resultCount = resultCount,
            maxOrigins = maxOrigins,
            maxConcurrent = maxActive,
            partial = partial,
            pendingSources = pendingSources,
            finished = finished,
            hasMore = hasMore,
            isEmpty = isEmpty,
            sourceListLoads = gateway.sourceListLoads,
            sourceRowLookups = gateway.sourceRowLookups,
            requestPages = requestPages,
        )
    }

    private data class BenchResult(
        val elapsedMs: Long,
        val firstResultMs: Long,
        val resultCount: Int,
        val maxOrigins: Int,
        val maxConcurrent: Int,
        val partial: Boolean,
        val pendingSources: Int,
        val finished: Boolean,
        val hasMore: Boolean,
        val isEmpty: Boolean,
        val sourceListLoads: Int,
        val sourceRowLookups: Int,
        val requestPages: Set<Int>,
    )

    private class FakeGateway(
        private val sources: List<BookSource>,
    ) : BookSearchGateway {
        var sourceListLoads = 0
        var sourceRowLookups = 0

        override suspend fun getSearchSources(
            scope: BookSearchScope,
            types: Set<Int>?,
        ): List<BookSource> {
            sourceListLoads++
            return sources
        }

        override suspend fun getBookSource(sourceUrl: String): BookSource? {
            sourceRowLookups++
            return sources.firstOrNull { it.bookSourceUrl == sourceUrl }
        }

        override suspend fun saveSearchBooks(books: List<SearchBook>) = Unit
    }
}
