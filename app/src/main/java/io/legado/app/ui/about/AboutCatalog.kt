package io.legado.app.ui.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.help.about.LegalDocuments
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * 关于页的单一内容模型。
 *
 * Material 与 Miuix 两套 renderer 只负责布局和视觉，条目顺序、文案 key、目标动作都从这里读取，
 * 避免两页各自维护一份 item 清单。
 */
@Stable
data class AboutSection(
    val items: ImmutableList<AboutItem>,
)

@Stable
data class AboutItem(
    @StringRes val titleRes: Int,
    val action: AboutAction,
    /** 行尾文本；目前只有 contributors 使用 GitHub。 */
    @StringRes val trailingTextRes: Int? = null,
)

@Stable
data class AboutHeaderAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val action: AboutAction,
)

sealed interface AboutAction {
    data object CheckUpdate : AboutAction
    data class OpenUrl(@StringRes val urlRes: Int) : AboutAction
    data class ShowMarkdown(
        @StringRes val titleRes: Int,
        val fileName: String,
    ) : AboutAction

    data object ShowCrashLogs : AboutAction
    data object SaveLog : AboutAction
    data object CreateHeapDump : AboutAction
}

object AboutCatalog {

    // 当前行为：主页、GitHub、贡献者都指向仓库根路径。旧 View 关于页曾分别使用
    // legado_url / github_url / contributors_url，是否恢复这三种语义需要产品确认；
    // 在确认前保持现有行为，避免把去重改动变成用户可见的链接变更。
    private val repositoryUrlRes = R.string.github_url

    val checkUpdate = AboutItem(
        titleRes = R.string.check_update,
        action = AboutAction.CheckUpdate,
    )

    private val checkUpdateHeader = AboutHeaderAction(
        titleRes = checkUpdate.titleRes,
        iconRes = R.drawable.ic_import,
        action = checkUpdate.action,
    )

    val headerActions: ImmutableList<AboutHeaderAction> = persistentListOf(
        AboutHeaderAction(
            titleRes = R.string.about_open_project_homepage,
            iconRes = R.drawable.ic_web_outline,
            action = AboutAction.OpenUrl(repositoryUrlRes),
        ),
        AboutHeaderAction(
            titleRes = R.string.about_open_github,
            iconRes = R.drawable.ic_github,
            action = AboutAction.OpenUrl(repositoryUrlRes),
        ),
        checkUpdateHeader,
    )

    val contributors = AboutSection(
        persistentListOf(
            AboutItem(
                titleRes = R.string.contributors,
                action = AboutAction.OpenUrl(repositoryUrlRes),
                trailingTextRes = R.string.git_hub,
            ),
        ),
    )

    val documents = AboutSection(
        persistentListOf(
            AboutItem(
                titleRes = R.string.privacy_policy,
                action = AboutAction.ShowMarkdown(
                    titleRes = R.string.about_privacy_policy_title,
                    fileName = LegalDocuments.PRIVACY_POLICY,
                ),
            ),
            AboutItem(
                titleRes = R.string.license,
                action = AboutAction.ShowMarkdown(
                    titleRes = R.string.about_license_title,
                    fileName = LegalDocuments.LICENSE,
                ),
            ),
            AboutItem(
                titleRes = R.string.disclaimer,
                action = AboutAction.ShowMarkdown(
                    titleRes = R.string.about_disclaimer_title,
                    fileName = LegalDocuments.DISCLAIMER,
                ),
            ),
        ),
    )

    val diagnostics = AboutSection(
        persistentListOf(
            AboutItem(
                titleRes = R.string.crash_log,
                action = AboutAction.ShowCrashLogs,
            ),
            AboutItem(
                titleRes = R.string.save_log,
                action = AboutAction.SaveLog,
            ),
            AboutItem(
                titleRes = R.string.create_heap_dump,
                action = AboutAction.CreateHeapDump,
            ),
        ),
    )

    /** Material 的 setting 列表（检查更新已作为顶部图标按钮单独展示）。 */
    val sections: ImmutableList<AboutSection> = persistentListOf(
        contributors,
        documents,
        diagnostics,
    )

    /** Miuix 首张卡片：检查更新 + 贡献者。 */
    val overview: AboutSection = AboutSection(
        (persistentListOf(checkUpdate) + contributors.items).toImmutableList(),
    )
}

@Composable
fun AboutItem.toAboutIntent(): AboutIntent = action.toAboutIntent()

@Composable
fun AboutHeaderAction.toAboutIntent(): AboutIntent = action.toAboutIntent()

@Composable
private fun AboutAction.toAboutIntent(): AboutIntent = when (this) {
    AboutAction.CheckUpdate -> AboutIntent.CheckUpdate
    is AboutAction.OpenUrl -> AboutIntent.OpenUrl(stringResource(urlRes))
    is AboutAction.ShowMarkdown -> AboutIntent.ShowMdFile(
        title = stringResource(titleRes),
        fileName = fileName,
    )

    AboutAction.ShowCrashLogs -> AboutIntent.ShowCrashLogs
    AboutAction.SaveLog -> AboutIntent.SaveLog
    AboutAction.CreateHeapDump -> AboutIntent.CreateHeapDump
}
