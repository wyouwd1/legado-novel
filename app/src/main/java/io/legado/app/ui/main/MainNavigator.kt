package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import androidx.navigation3.runtime.NavKey
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MainNavigator {

    var backNavigationInProgress = false
        private set
    private val navigationScope by lazy(LazyThreadSafetyMode.NONE) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    private var backNavigationResetJob: Job? = null

    fun navigateToRoute(
        backStack: MutableList<NavKey>,
        route: NavKey,
        resetToHome: Boolean = false,
    ) {
        navigateToRoute(backStack, route, null, resetToHome)
    }

    /**
     * [tracker] 非空时在同一调用里记录栈顶路由：Activity 级叠层（全局朗读胶囊）据此
     * 在导航发生的那一刻就重算显隐，而不是等 back stack 快照回灌后才被动刷新。
     */
    fun navigateToRoute(
        backStack: MutableList<NavKey>,
        route: NavKey,
        tracker: MainNavRouteTracker?,
        resetToHome: Boolean = false,
    ) {
        navigateToRouteInternal(backStack, route, tracker, resetToHome)
    }

    private fun navigateToRouteInternal(
        backStack: MutableList<NavKey>,
        route: NavKey,
        tracker: MainNavRouteTracker?,
        resetToHome: Boolean,
    ) {
        if (resetToHome) {
            backStack.clear()
            backStack.add(MainRouteHome)
        }
        val currentRoute = backStack.lastOrNull()
        if (currentRoute == route) return

        // "Read" in a detail page opened by the reader returns to that existing session.
        if (route is MainRouteReadBook && currentRoute is MainRouteBookInfo) {
            val readerIndex = backStack.indexOfLast {
                it is MainRouteReadBook && it.bookUrl == route.bookUrl
            }
            if (readerIndex >= 0) {
                while (backStack.lastIndex > readerIndex) backStack.removeAt(backStack.lastIndex)
                if (route.chapterChanged) backStack[readerIndex] = route
                tracker?.onBackStackChanged(backStack)
                return
            }
        }

        // 朗读通知的「回到正在读的书」不带 bookUrl（空即最后读过的那本），路由键和栈顶那份必然不相等。
        // 栈顶已经是阅读页时再压一份，返回就要连点同样多次才出得了阅读页，所以在这里收住不压栈。
        if (route is MainRouteReadBook &&
            currentRoute is MainRouteReadBook &&
            (route.bookUrl == null || route.bookUrl == currentRoute.bookUrl)
        ) return

        if (route is MainRouteReadBook) ReaderPerfTrace.marker("open.request")
        // 导航动画和阅读页组合要花几百毫秒, 这段时间足够把正文读出来并排版好
        if (route is MainRouteReadBook && !route.chapterChanged) {
            route.bookUrl?.let { ReadBook.prefetchForOpen(it) }
        }

        when (route) {
            is MainRouteToc,
            is MainRouteBookInfoEdit,
            is MainRouteReplaceRules,
            is MainRouteReplaceEdit -> backStack.add(route)
            is MainRouteSourceLogin -> {
                backStack.add(route)
            }

            is MainRouteWebView -> backStack.add(route)

            is MainRouteBookSourceManage,
            is MainRouteBookSourceEdit,
            is MainRouteBookSourceDebug -> backStack.add(route)

            MainRouteReadAloudPlayer -> backStack.add(route)

            MainRouteHome -> {
                backStack.clear()
                backStack.add(MainRouteHome)
            }

            MainRouteSettings -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(MainRouteSettings)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(MainRouteSettings)
                }
            }

            MainRouteSettingsOther,
            MainRouteSettingsRead,
            MainRouteSettingsCover,
            MainRouteSettingsTheme,
            MainRouteSettingsBackup,
            MainRouteSettingsCustomTheme,
            MainRouteSettingsThemeManage,
            MainRouteSettingsDownloadCache,
            MainRouteSettingsTranslation,
            MainRouteSettingsPrivate -> {
                backStack.clear()
                backStack.add(MainRouteHome)
                backStack.add(MainRouteSettings)
                backStack.add(route)
            }

            MainRouteImportLocal,
            MainRouteImportRemote,
            is MainRouteCache,
            MainRouteBookCacheManage,
            is MainRouteReadBook -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteAudioPlay -> {
                // 播放器为单例语义：已在栈上（如通知栏再次进入）则替换，避免叠加多个播放界面
                val existingAudioIndex = backStack.indexOfLast { it is MainRouteAudioPlay }
                if (existingAudioIndex >= 0) {
                    while (backStack.lastIndex > existingAudioIndex) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    backStack[existingAudioIndex] = route
                } else if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteSearchContent -> {
                backStack.add(route)
            }

            is MainRouteSearch -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteExploreShow ||
                    currentRoute is MainRouteBookSourceManage ||
                    currentRoute is MainRouteSearch
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteBookInfo -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteCache ||
                    currentRoute is MainRouteReadBook
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteBookVoiceCasting,
            is MainRouteCloudTtsEngines,
            MainRouteTtsCache -> {
                if (
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteBookVoiceCasting ||
                    currentRoute is MainRouteCloudTtsEngines ||
                    currentRoute == MainRouteTtsCache ||
                    currentRoute is MainRouteReadBook
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            is MainRouteExploreShow -> {
                if (
                    currentRoute == MainRouteHome ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteSearch ||
                    currentRoute is MainRouteExploreShow
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteRuleSub -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteHighlightTagRule,
            MainRouteMultiRoleRule,
            MainRouteReadRecord -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            // 朗读设置的子页（设置 hub 与阅读界面朗读设置卡片两处入口）都只压栈、不清栈：
            // 从阅读界面进来要能直接返回阅读界面。
            MainRouteVoicePool,
            MainRouteBgmPool,
            MainRouteVoiceEffect,
            MainRouteCastCapsuleStyle,
            MainRouteRegexCastRule,
            MainRouteMultiRoleRecognition -> backStack.add(route)

            MainRouteAbout -> {
                if (currentRoute == MainRouteHome) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }

            MainRouteReadRecordOverview -> {
                if (currentRoute == MainRouteHome || currentRoute == MainRouteReadRecord) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteHome)
                    backStack.add(route)
                }
            }
        }
        // 同步栈快照：Activity 级叠层（全局朗读胶囊）据此立刻重算显隐，不必等 back stack 回灌
        tracker?.onBackStackChanged(backStack)
    }

    fun navigateBack(
        activity: Activity,
        backStack: MutableList<NavKey>,
        tracker: MainNavRouteTracker? = null,
        fromRoute: NavKey? = null,
    ): Boolean {
        if (fromRoute != null) {
            if (backStack.lastOrNull() != fromRoute) {
                return false
            }
        } else if (backNavigationInProgress) {
            return false
        }
        if (backStack.size > 1) {
            backNavigationInProgress = true
            backStack.removeLastOrNull()
            tracker?.onBackStackChanged(backStack)
            return true
        } else {
            activity.finish()
            return true
        }
    }

    fun onBackStackChanged() {
        backNavigationResetJob?.cancel()
        backNavigationResetJob = navigationScope.launch {
            delay(100)
            backNavigationInProgress = false
        }
    }

    fun resolveStartRoute(intent: Intent?): NavKey {
        val route = intent?.getStringExtra(MainIntent.EXTRA_START_ROUTE)
        return resolveStartRoute(route, intent)
    }

    private fun resolveStartRoute(route: String?, intent: Intent?): MainRoute {
        return when (route) {
            MainRouteConst.ROUTE_MAIN -> MainRouteHome
            MainRouteConst.ROUTE_SOURCE_LOGIN -> MainRouteSourceLogin(
                type = intent?.getStringExtra(MainIntent.EXTRA_SOURCE_LOGIN_TYPE)
                    ?.let { runCatching { io.legado.app.ui.login.SourceLoginType.valueOf(it) }.getOrNull() }
                    ?: io.legado.app.ui.login.SourceLoginType.BookSource,
                sourceKey = intent?.getStringExtra(MainIntent.EXTRA_SOURCE_LOGIN_KEY),
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
            )

            MainRouteConst.ROUTE_WEB_VIEW -> intent?.getStringExtra(MainIntent.EXTRA_WEB_VIEW_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { url ->
                    MainRouteWebView(
                        title = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_TITLE),
                        url = url,
                        sourceOrigin = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_ORIGIN),
                        sourceName = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_NAME),
                        sourceType = if (intent.hasExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_TYPE)) {
                            intent.getIntExtra(MainIntent.EXTRA_WEB_VIEW_SOURCE_TYPE, 0)
                        } else null,
                        sourceVerificationEnable = intent.getBooleanExtra(
                            MainIntent.EXTRA_WEB_VIEW_VERIFICATION, false
                        ),
                        refetchAfterSuccess = intent.getBooleanExtra(
                            MainIntent.EXTRA_WEB_VIEW_REFETCH, true
                        ),
                        html = intent.getStringExtra(MainIntent.EXTRA_WEB_VIEW_HTML),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_BOOK_SOURCE_MANAGE -> MainRouteBookSourceManage(
                intent?.getStringExtra(MainIntent.EXTRA_BOOK_SOURCE_IMPORT)
            )
            MainRouteConst.ROUTE_BOOK_SOURCE_EDIT -> MainRouteBookSourceEdit(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )

            MainRouteConst.ROUTE_BOOK_SOURCE_DEBUG -> MainRouteBookSourceDebug(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )
            MainRouteConst.ROUTE_RULE_SUB -> MainRouteRuleSub
            MainRouteConst.ROUTE_SETTINGS -> MainRouteSettings
            MainRouteConst.ROUTE_SETTINGS_OTHER -> MainRouteSettingsOther
            MainRouteConst.ROUTE_SETTINGS_READ -> MainRouteSettingsRead
            MainRouteConst.ROUTE_SETTINGS_COVER -> MainRouteSettingsCover
            MainRouteConst.ROUTE_SETTINGS_THEME -> MainRouteSettingsTheme
            MainRouteConst.ROUTE_SETTINGS_BACKUP -> MainRouteSettingsBackup
            MainRouteConst.ROUTE_SETTINGS_CUSTOM_THEME -> MainRouteSettingsCustomTheme
            MainRouteConst.ROUTE_SETTINGS_PRIVATE -> MainRouteSettingsPrivate
            MainRouteConst.ROUTE_SETTINGS_LAB_CONFIG -> MainRouteSettingsLabConfig
            MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE -> MainRouteSettingsDownloadCache
            MainRouteConst.ROUTE_SETTINGS_TRANSLATION -> MainRouteSettingsTranslation
            MainRouteConst.ROUTE_IMPORT_LOCAL -> MainRouteImportLocal
            MainRouteConst.ROUTE_IMPORT_REMOTE -> MainRouteImportRemote
            MainRouteConst.ROUTE_CACHE -> MainRouteCache(
                intent?.getLongExtra(
                    MainIntent.EXTRA_CACHE_GROUP_ID,
                    -1L
                ) ?: -1L
            )

            MainRouteConst.ROUTE_BOOK_CACHE_MANAGE -> MainRouteBookCacheManage
            MainRouteConst.ROUTE_READ_BOOK -> MainRouteReadBook(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
                readAloud = intent?.getBooleanExtra(MainIntent.EXTRA_READ_ALOUD, false) == true,
                inBookshelf = intent?.getBooleanExtra(MainIntent.EXTRA_IN_BOOKSHELF, true) != false,
                chapterChanged = intent?.getBooleanExtra(
                    MainIntent.EXTRA_CHAPTER_CHANGED,
                    false
                ) == true,
            )
            MainRouteConst.ROUTE_AUDIO_PLAY -> MainRouteAudioPlay(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
                inBookshelf = intent?.getBooleanExtra(MainIntent.EXTRA_IN_BOOKSHELF, true) != false,
            )
            MainRouteConst.ROUTE_SEARCH -> MainRouteSearch(
                key = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_KEY),
                scopeRaw = intent?.getStringExtra(MainIntent.EXTRA_SEARCH_SCOPE)
            )

            MainRouteConst.ROUTE_BOOK_INFO -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookInfo(
                        name = intent.getStringExtra(MainIntent.EXTRA_BOOK_NAME),
                        author = intent.getStringExtra(MainIntent.EXTRA_BOOK_AUTHOR),
                        bookUrl = bookUrl,
                        origin = intent.getStringExtra(MainIntent.EXTRA_BOOK_ORIGIN),
                        coverPath = intent.getStringExtra(MainIntent.EXTRA_BOOK_COVER)
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_EXPLORE_SHOW -> intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { sourceUrl ->
                    MainRouteExploreShow(
                        title = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_NAME),
                        sourceUrl = sourceUrl,
                        exploreUrl = intent.getStringExtra(MainIntent.EXTRA_EXPLORE_URL),
                    )
                } ?: MainRouteHome

            MainRouteConst.ROUTE_ABOUT -> MainRouteAbout

            else -> MainRouteHome
        }
    }
}
