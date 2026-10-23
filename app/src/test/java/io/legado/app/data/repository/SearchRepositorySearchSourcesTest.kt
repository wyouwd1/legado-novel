package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.domain.model.BookSearchScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SearchRepositorySearchSourcesTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SearchRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = SearchRepositoryImpl(db)
        insertSources()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `all scope returns only enabled searchable sources in custom order`() = runBlocking {
        val result = repository.getSearchSources(BookSearchScope(""), types = null)

        assertEquals(listOf("s1", "s3", "s5"), result.map { it.bookSourceUrl })
    }

    @Test
    fun `type filter is applied to the one-query source list`() = runBlocking {
        val result = repository.getSearchSources(BookSearchScope(""), types = setOf(0))

        assertEquals(listOf("s1", "s5"), result.map { it.bookSourceUrl })
    }

    @Test
    fun `explicit source scope preserves only requested searchable sources`() = runBlocking {
        val scope = BookSearchScope.encodeSources(
            listOf(
                BookSearchScope.ScopeSourceItem("three", "s3"),
                BookSearchScope.ScopeSourceItem("one", "s1"),
            )
        )

        val result = repository.getSearchSources(BookSearchScope(scope), types = null)

        assertEquals(listOf("s1", "s3"), result.map { it.bookSourceUrl })
    }

    @Test
    fun `group scope keeps csv group token semantics`() = runBlocking {
        val scope = BookSearchScope.encodeGroups(listOf("g1"))

        val result = repository.getSearchSources(BookSearchScope(scope), types = null)

        assertEquals(listOf("s1", "s5"), result.map { it.bookSourceUrl })
    }


    @Test
    fun `explicit disabled source returns empty instead of searching every source`() = runBlocking {
        val scope = BookSearchScope.encodeSources(
            listOf(BookSearchScope.ScopeSourceItem("disabled", "s4"))
        )

        val result = repository.getSearchSources(BookSearchScope(scope), types = null)

        assertEquals(emptyList<String>(), result.map { it.bookSourceUrl })
    }

    @Test
    fun `explicit source without search url returns empty instead of searching every source`() =
        runBlocking {
            val scope = BookSearchScope.encodeSources(
                listOf(BookSearchScope.ScopeSourceItem("no-search", "s2"))
            )

            val result = repository.getSearchSources(BookSearchScope(scope), types = null)

            assertEquals(emptyList<String>(), result.map { it.bookSourceUrl })
        }

    @Test
    fun `unknown explicit source keeps legacy fallback to all searchable sources`() = runBlocking {
        val scope = BookSearchScope.encodeSources(
            listOf(BookSearchScope.ScopeSourceItem("missing", "missing-source"))
        )

        val result = repository.getSearchSources(BookSearchScope(scope), types = null)

        assertEquals(listOf("s1", "s3", "s5"), result.map { it.bookSourceUrl })
    }

    @Test
    fun `group with only non-searchable source returns empty instead of all sources`() = runBlocking {
        val scope = BookSearchScope.encodeGroups(listOf("g4"))

        val result = repository.getSearchSources(BookSearchScope(scope), types = null)

        assertEquals(emptyList<String>(), result.map { it.bookSourceUrl })
    }

    @Test
    fun `multiple groups are unioned without duplicates`() = runBlocking {
        val scope = BookSearchScope.encodeGroups(listOf("g1", "g2", "g3"))

        val result = repository.getSearchSources(BookSearchScope(scope), types = null)

        assertEquals(listOf("s1", "s3", "s5"), result.map { it.bookSourceUrl })
    }


    @Test
    fun `all scope executes one Room query for the full source set`() = runBlocking {
        val queries = mutableListOf<String>()
        val countedDb = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> queries += sql }, Executor { it.run() })
            .build()
        try {
            countedDb.bookSourceDao.insert(
                *Array(60) { index ->
                    source(
                        "counted-$index",
                        "counted",
                        0,
                        "https://counted-$index.example/search",
                        customOrder = index,
                    )
                }
            )
            queries.clear()

            SearchRepositoryImpl(countedDb).getSearchSources(BookSearchScope(""), types = null)

            assertEquals(
                1,
                queries.count { it.contains("from book_sources", ignoreCase = true) },
            )
        } finally {
            countedDb.close()
        }
    }

    private fun insertSources() {
        db.bookSourceDao.insert(
            source("s1", "g1", 0, "https://s1.example/search?key={{key}}", customOrder = 1),
            source("s2", "g1", 0, null, customOrder = 2),
            source("s3", "g2", 1, "https://s3.example/search?key={{key}}", customOrder = 3),
            source(
                "s4", "g1", 0, "https://s4.example/search?key={{key}}",
                customOrder = 4, enabled = false,
            ),
            source("s5", "g1,g3", 0, "https://s5.example/search?key={{key}}", customOrder = 5),
            source("s6", "g4", 0, null, customOrder = 6),
        )
    }

    private fun source(
        url: String,
        group: String,
        type: Int,
        searchUrl: String?,
        customOrder: Int,
        enabled: Boolean = true,
    ) = BookSource(
        bookSourceUrl = url,
        bookSourceName = url,
        bookSourceGroup = group,
        bookSourceType = type,
        searchUrl = searchUrl,
        customOrder = customOrder,
        enabled = enabled,
    )
}
