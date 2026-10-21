package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.replace.ReplaceEditRoute
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute : NavKey

@Serializable
data class MainRouteToc(val bookUrl: String, val initialPage: Int = 0) : MainRoute

@Serializable
data class MainRouteBookInfoEdit(val bookUrl: String) : MainRoute

@Serializable
data class MainRouteReplaceRules(val bookUrl: String? = null) : MainRoute

@Serializable
data class MainRouteReplaceEdit(val editor: ReplaceEditRoute) : MainRoute

@Serializable
data object MainRouteHome : MainRoute

@Serializable
data class MainRouteSourceLogin(
    val type: SourceLoginType,
    val sourceKey: String? = null,
    val bookUrl: String? = null,
) : MainRoute

@Serializable
data class MainRouteWebView(
    val title: String? = null,
    val url: String,
    val sourceOrigin: String? = null,
    val sourceName: String? = null,
    val sourceType: Int? = null,
    val sourceVerificationEnable: Boolean = false,
    val refetchAfterSuccess: Boolean = true,
    val html: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookSourceManage(
    val importUrl: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookSourceEdit(val sourceUrl: String? = null) : MainRoute

@Serializable
data class MainRouteBookSourceDebug(val sourceUrl: String? = null) : MainRoute

@Serializable
data object MainRouteSettings : MainRoute

@Serializable
data object MainRouteSettingsOther : MainRoute

@Serializable
data object MainRouteSettingsRead : MainRoute

@Serializable
data object MainRouteSettingsCover : MainRoute

@Serializable
data object MainRouteSettingsCoverAlbums : MainRoute

@Serializable
data object MainRouteSettingsTheme : MainRoute

@Serializable
data object MainRouteSettingsBackup : MainRoute

@Serializable
data object MainRouteSettingsCustomTheme : MainRoute

@Serializable
data object MainRouteSettingsThemeManage : MainRoute

@Serializable
data object MainRouteSettingsPrivate : MainRoute

@Serializable
data object MainRouteSettingsLabConfig : MainRoute

@Serializable
data object MainRouteSettingsDownloadCache : MainRoute

@Serializable
data object MainRouteImportLocal : MainRoute

@Serializable
data object MainRouteImportRemote : MainRoute

@Serializable
data object MainRouteReadRecord : MainRoute

@Serializable
data object MainRouteReadRecordOverview : MainRoute

@Serializable
data class MainRouteCache(val groupId: Long) : MainRoute

@Serializable
data object MainRouteBookCacheManage : MainRoute

@Serializable
data class MainRouteReadBook(
    val bookUrl: String? = null,
    val inBookshelf: Boolean = true,
    val chapterChanged: Boolean = false,
    val sharedCoverKey: String? = null,
) : MainRoute

@Serializable
data class MainRouteSearch(
    val key: String?,
    val scopeRaw: String? = null
) : MainRoute

@Serializable
data class MainRouteBookInfo(
    val name: String?,
    val author: String?,
    val bookUrl: String,
    val origin: String? = null,
    val coverPath: String? = null,
    val sharedCoverKey: String? = null,
    val openRequestId: Long = 0L,
) : MainRoute

@Serializable
data object MainRouteRuleSub : MainRoute

@Serializable
data class MainRouteExploreShow(
    val title: String?,
    val sourceUrl: String,
    val exploreUrl: String?,
) : MainRoute

@Serializable
data class MainRouteSearchContent(
    val bookUrl: String,
    val searchWord: String? = null,
    val searchResultIndex: Int = 0,
    val autoFocus: Boolean = true,
) : MainRoute

@Serializable
data object MainRouteHighlightTagRule : MainRoute

@Serializable
data object MainRouteAbout : MainRoute

object MainRouteConst {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SOURCE_LOGIN = "source/login"
    const val ROUTE_WEB_VIEW = "web/view"
    const val ROUTE_BOOK_SOURCE_MANAGE = "source/book/manage"
    const val ROUTE_BOOK_SOURCE_EDIT = "source/book/edit"
    const val ROUTE_BOOK_SOURCE_DEBUG = "source/book/debug"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_OTHER = "settings/other"
    const val ROUTE_SETTINGS_READ = "settings/read"
    const val ROUTE_SETTINGS_COVER = "settings/cover"
    const val ROUTE_SETTINGS_COVER_ALBUMS = "settings/cover/albums"
    const val ROUTE_SETTINGS_THEME = "settings/theme"
    const val ROUTE_SETTINGS_BACKUP = "settings/backup"
    const val ROUTE_SETTINGS_AI = "settings/ai"
    const val ROUTE_SETTINGS_AI_SUMMARY = "settings/ai/summary"
    const val ROUTE_SETTINGS_AI_PROMPT = "settings/ai/prompt"
    const val ROUTE_AI_CHAT = "ai/chat"
    const val ROUTE_SETTINGS_CUSTOM_THEME = "settings/custom_theme"
    const val ROUTE_SETTINGS_PRIVATE = "settings/private"
    const val ROUTE_SETTINGS_LAB_CONFIG = "settings/lab_config"
    const val ROUTE_SETTINGS_DOWNLOAD_CACHE = "settings/download_cache"
    const val ROUTE_IMPORT_LOCAL = "import/local"
    const val ROUTE_IMPORT_REMOTE = "import/remote"
    const val ROUTE_CACHE = "cache"
    const val ROUTE_BOOK_CACHE_MANAGE = "book/cache/manage"
    const val ROUTE_READ_BOOK = "book/read"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_SEARCH_CONTENT = "book/searchContent"
    const val ROUTE_BOOK_INFO = "book/info"
    const val ROUTE_BOOK_CHARACTER_DETAIL = "book/character/detail"
    const val ROUTE_BOOK_CHARACTER_NETWORK = "book/character/network"
    const val ROUTE_BOOK_CHARACTER_LIST = "book/character/list"
    const val ROUTE_BOOK_KNOWLEDGE_LIST = "book/knowledge/list"
    const val ROUTE_BOOK_KNOWLEDGE_DETAIL = "book/knowledge/detail"
    const val ROUTE_BOOK_EVENT_LIST = "book/event/list"
    const val ROUTE_BOOK_EVENT_DETAIL = "book/event/detail"
    const val ROUTE_EXPLORE_SHOW = "explore/show"
    const val ROUTE_RULE_SUB = "rss/rule_sub"
    const val ROUTE_READ_RECORD = "read_record"
    const val ROUTE_READ_RECORD_OVERVIEW = "read_record_overview"
    const val ROUTE_ABOUT = "about"
}
