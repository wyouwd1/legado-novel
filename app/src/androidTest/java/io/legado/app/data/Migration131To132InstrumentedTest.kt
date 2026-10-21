package io.legado.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.constant.BookType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机/模拟器上的 131 → 132 验收。
 *
 * 与 JVM 的 Robolectric 测试不同，这里让 Room 自己执行生成的 AutoMigration：
 * 40 张裁撤表必须真的被 DROP，132 schema 必须通过 Room 校验，onPostMigrate 必须保留
 * 旧媒体书的其余 type 位与阅读进度。
 */
@RunWith(AndroidJUnit4::class)
class Migration131To132InstrumentedTest {

    private val testDb = "migration-131-132-instrumented"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(DatabaseMigrations.Migration_131_132()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun autoMigrationDropsLegacyTablesAndConvertsMediaBooks() {
        helper.createDatabase(testDb, 131).apply {
            execSQL(
                "INSERT INTO books(bookUrl, name, author, type, durChapterPos, durChapterIndex) " +
                    "VALUES('legacy-audio', 'Audio', 'Author', ?, 321, 7)",
                arrayOf<Any>(BookType.audio or BookType.text or BookType.local),
            )
            execSQL(
                "INSERT INTO books(bookUrl, name, author, type) VALUES(?, ?, ?, ?)",
                arrayOf<Any>("plain-text", "Text", "Author", BookType.text),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            testDb,
            132,
            true,
            *DatabaseMigrations.migrations,
        ).close()

        val db = SQLiteDatabase.openDatabase(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getDatabasePath(testDb).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        db.use {
            it.rawQuery(
                "SELECT type, durChapterPos, durChapterIndex FROM books WHERE bookUrl = ?",
                arrayOf("legacy-audio"),
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(
                    BookType.text or BookType.local or BookType.notShelf,
                    cursor.getInt(0),
                )
                assertEquals(321, cursor.getInt(1))
                assertEquals(7, cursor.getInt(2))
            }

            it.rawQuery(
                "SELECT type FROM books WHERE bookUrl = ?",
                arrayOf("plain-text"),
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(BookType.text, cursor.getInt(0))
            }

            it.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'rssSources'",
                null,
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }

            assertFalse(
                it.rawQuery(
                    "SELECT 1 FROM books WHERE bookUrl = 'legacy-audio' AND type = 1",
                    null,
                ).use { it.moveToFirst() },
            )
        }
    }
}
