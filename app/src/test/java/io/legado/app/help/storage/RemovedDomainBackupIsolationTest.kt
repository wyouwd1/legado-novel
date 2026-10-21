package io.legado.app.help.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 静态回归门：备份/恢复的 allowlist 不得重新出现已裁撤域的文件。
 *
 * 这里无法在 JVM 单测里跑完整 Restore（依赖 appDb/Koin/Android），所以用源码断言锁住
 * “Backup 不写、Restore 不读”这两个接入点；真实恢复链路由 W6-D 的 Robolectric/手测覆盖。
 */
class RemovedDomainBackupIsolationTest {

    private val removedFiles = listOf(
        "rssSources.json",
        "rssArticles.json",
        "rssReadRecords.json",
        "rssStars.json",
        "httpTTS.json",
        "homepage_modules.json",
        "homepage_custom_sets.json",
    )

    @Test
    fun `backup does not enumerate removed domain files`() {
        val source = source("app/src/main/java/io/legado/app/help/storage/Backup.kt")
        removedFiles.forEach { fileName ->
            assertFalse("Backup.kt 不应再引用 $fileName", source.contains(fileName))
        }
        assertTrue(source.contains("bookshelf.json"))
        assertTrue(source.contains("bookSource.json"))
    }

    @Test
    fun `restore does not enumerate removed domain files`() {
        val source = source("app/src/main/java/io/legado/app/help/storage/Restore.kt")
        removedFiles.forEach { fileName ->
            assertFalse("Restore.kt 不应再引用 $fileName", source.contains(fileName))
        }
        assertTrue(source.contains("bookshelf.json"))
        assertTrue(source.contains("bookSource.json"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("app/$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("文件不存在：$relativePath（cwd=${File(".").absolutePath}）")
    }
}
