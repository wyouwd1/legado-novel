package io.legado.app.ui.about

import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutCatalogTest {

    @Test
    fun `About 列表条目顺序保持稳定`() {
        assertEquals(
            listOf(R.string.check_update, R.string.contributors),
            AboutCatalog.overview.items.map { it.titleRes },
        )
        assertEquals(
            listOf(R.string.privacy_policy, R.string.license, R.string.disclaimer),
            AboutCatalog.documents.items.map { it.titleRes },
        )
        assertEquals(
            listOf(R.string.crash_log, R.string.save_log, R.string.create_heap_dump),
            AboutCatalog.diagnostics.items.map { it.titleRes },
        )
    }

    @Test
    fun `About 动作映射覆盖全部类型`() {
        assertEquals(AboutAction.CheckUpdate, AboutCatalog.checkUpdate.action)
        assertEquals(AboutAction.CheckUpdate, AboutCatalog.headerActions.last().action)
        assertEquals(
            AboutAction.OpenUrl(R.string.github_url),
            AboutCatalog.contributors.items.single().action,
        )
        assertTrue(
            AboutCatalog.documents.items.all { it.action is AboutAction.ShowMarkdown },
        )
        assertEquals(AboutAction.ShowCrashLogs, AboutCatalog.diagnostics.items[0].action)
        assertEquals(AboutAction.SaveLog, AboutCatalog.diagnostics.items[1].action)
        assertEquals(AboutAction.CreateHeapDump, AboutCatalog.diagnostics.items[2].action)
    }

    @Test
    fun `About 顶部操作保留标题与图标`() {
        assertEquals(
            listOf(
                R.string.about_open_project_homepage,
                R.string.about_open_github,
                R.string.check_update,
            ),
            AboutCatalog.headerActions.map { it.titleRes },
        )
        assertEquals(
            listOf(R.drawable.ic_web_outline, R.drawable.ic_github, R.drawable.ic_import),
            AboutCatalog.headerActions.map { it.iconRes },
        )
    }

    @Test
    fun `About 主操作只引用 canonical repo 资源`() {
        val urlRes = AboutCatalog.headerActions.take(2)
            .map { (it.action as AboutAction.OpenUrl).urlRes }
        assertEquals(listOf(R.string.github_url, R.string.github_url), urlRes)
    }

    @Test
    fun `Markdown 文件名集中在内容模型`() {
        val fileNames = AboutCatalog.documents.items.map {
            (it.action as AboutAction.ShowMarkdown).fileName
        }
        assertEquals(listOf("privacyPolicy.md", "LICENSE.md", "disclaimer.md"), fileNames)
    }

    @Test
    fun `About 条目标题不重复且行尾文本只属于贡献者`() {
        val listItems = AboutCatalog.overview.items +
            AboutCatalog.documents.items +
            AboutCatalog.diagnostics.items
        assertEquals(listItems.size, listItems.map { it.titleRes }.distinct().size)
        assertEquals(
            R.string.git_hub,
            AboutCatalog.contributors.items.single().trailingTextRes,
        )
        assertTrue(AboutCatalog.documents.items.all { it.trailingTextRes == null })
        assertTrue(AboutCatalog.diagnostics.items.all { it.trailingTextRes == null })
        assertNull(AboutCatalog.checkUpdate.trailingTextRes)
    }
}
