package io.legado.app.data

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.gson.JsonParser
import io.legado.app.constant.BookType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 131→132 的迁移验收：
 * - 132 schema 比 131 少的表必须恰好等于 @DeleteTable 注解的 40 张表；
 * - onPostMigrate 的 type 位运算在真实 SQLite 上执行，audio/image/video 保留数据并变 text+notShelf。
 *
 * 真实 room-testing MigrationTestHelper 需要 androidTest 依赖/schema assets，本模块单测
 * testImplementation 没有 room-testing；因此这里用 Robolectric 的 framework SQLite 直接执行
 * 生产 onPostMigrate SQL，并另用 schema JSON 做静态一致性校验。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DatabaseMigration131To132Test {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `schema 132 removed tables equal delete-table annotation`() {
        val tables131 = schemaTables(131)
        val tables132 = schemaTables(132)
        val removed = tables131 - tables132
        val annotated = deleteTableNames()

        assertEquals(40, removed.size)
        assertEquals(removed, annotated)
        assertTrue(removed.none { it in tables132 })
    }

    @Test
    fun `onPostMigrate converts legacy audio image video books and preserves data`() {
        val helper = openHelper()
        helper.use {
            val db = it.writableDatabase
            db.execSQL(
                "CREATE TABLE books (" +
                    "bookUrl TEXT NOT NULL PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "author TEXT NOT NULL, " +
                    "type INTEGER NOT NULL)"
            )
            insertBook(db, "text", BookType.text)
            insertBook(db, "audio", BookType.audio)
            insertBook(db, "image", BookType.image)
            insertBook(db, "video", BookType.video)
            insertBook(db, "audio-local", BookType.audio or BookType.local)
            insertBook(db, "image-video", BookType.image or BookType.video)
            insertBook(db, "web-file", BookType.webFile)

            DatabaseMigrations.Migration_131_132().onPostMigrate(db)

            val rows = db.query(
                "SELECT bookUrl, name, author, type FROM books ORDER BY bookUrl"
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Row(
                                bookUrl = cursor.getString(0),
                                name = cursor.getString(1),
                                author = cursor.getString(2),
                                type = cursor.getInt(3),
                            )
                        )
                    }
                }
            }

            assertEquals(7, rows.size)
            assertTrue(rows.all { it.name == "name-${it.bookUrl}" && it.author == "author-${it.bookUrl}" })
            assertEquals(BookType.text, rows.single { it.bookUrl == "text" }.type)
            assertEquals(
                BookType.text or BookType.notShelf,
                rows.single { it.bookUrl == "audio" }.type,
            )
            assertEquals(
                BookType.text or BookType.notShelf,
                rows.single { it.bookUrl == "image" }.type,
            )
            assertEquals(
                BookType.text or BookType.notShelf,
                rows.single { it.bookUrl == "video" }.type,
            )
            assertEquals(
                BookType.text or BookType.notShelf or BookType.local,
                rows.single { it.bookUrl == "audio-local" }.type,
            )
            assertEquals(
                BookType.text or BookType.notShelf,
                rows.single { it.bookUrl == "image-video" }.type,
            )
            assertEquals(BookType.webFile, rows.single { it.bookUrl == "web-file" }.type)
        }
    }

    private fun insertBook(db: SupportSQLiteDatabase, bookUrl: String, type: Int) {
        db.compileStatement(
            "INSERT INTO books(bookUrl, name, author, type) VALUES(?, ?, ?, ?)"
        ).use { statement ->
            statement.bindString(1, bookUrl)
            statement.bindString(2, "name-$bookUrl")
            statement.bindString(3, "author-$bookUrl")
            statement.bindLong(4, type.toLong())
            statement.executeInsert()
        }
    }

    private fun openHelper(): SupportSQLiteOpenHelper {
        val dbFile = File(folder.root, "migration-131-132.db")
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
    }

    private fun schemaTables(version: Int): Set<String> {
        val file = projectFile("app/schemas/io.legado.app.data.AppDatabase/$version.json")
        val entities = JsonParser.parseString(file.readText())
            .asJsonObject
            .getAsJsonObject("database")
            .getAsJsonArray("entities")
        return entities.mapTo(linkedSetOf()) { it.asJsonObject.get("tableName").asString }
    }

    private fun deleteTableNames(): Set<String> {
        val source = projectFile(
            "app/src/main/java/io/legado/app/data/DatabaseMigrations.kt"
        ).readText()
        return Regex("""DeleteTable\(tableName\s*=\s*"([^"]+)"\)""")
            .findAll(source)
            .mapTo(linkedSetOf()) { it.groupValues[1] }
    }

    private fun projectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("app/$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("文件不存在：$relativePath（cwd=${File(".").absolutePath}）")
    }

    private data class Row(
        val bookUrl: String,
        val name: String,
        val author: String,
        val type: Int,
    )
}
