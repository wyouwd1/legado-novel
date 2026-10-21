package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.core.ui.morph.BookCoverMorphAnchors
import io.legado.app.core.ui.morph.isAnchorVisibleInScreen
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.model.Download
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.about.AboutEffect
import io.legado.app.ui.about.AboutScreen
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowRouteScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.import.local.ImportBookRouteScreen
import io.legado.app.ui.book.import.remote.RemoteBookRouteScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.info.edit.BookInfoEditScreen
import io.legado.app.ui.book.info.edit.BookInfoEditViewModel
import io.legado.app.ui.book.knowledge.CharacterAvatarCropDialog
import io.legado.app.ui.book.knowledge.CharacterAvatarSourceSheet
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookInitRequest
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.ReaderSessionViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewRouteScreen
import io.legado.app.ui.book.readRecord.ReadRecordRouteScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchRouteScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.source.debug.BookSourceDebugRoute
import io.legado.app.ui.book.source.debug.BookSourceDebugViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditRoute
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.book.source.manage.BookSourceRouteScreen
import io.legado.app.ui.book.toc.TocRouteScreen
import io.legado.app.ui.browser.WebViewModel
import io.legado.app.ui.browser.WebViewRouteScreen
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.customTheme.CustomThemeRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.labConfig.LabConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.privateConfig.PrivateConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.themeManage.ThemeManageRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigRouteScreen
import io.legado.app.ui.highlightTagRule.HighlightTagRuleRouteScreen
import io.legado.app.ui.login.SourceLoginIntent
import io.legado.app.ui.login.SourceLoginRoute
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.login.SourceLoginViewModel
import io.legado.app.ui.replace.ReplaceRuleRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.book.source.subscription.RuleSubRouteScreen
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.ui.widget.components.privacy.PrivateReadGate
import io.legado.app.ui.widget.components.privacy.PrivateVerifyGate
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * WebView 类页面（内置浏览器、订阅阅读）只做位移转场。
 *
 * WebView 是 AndroidView interop view：所在子树一旦被加上 graphicsLayer（fade 的 alpha、
 * scaleOut 的缩放），Compose 会把网页一并画进离屏 RenderNode
 * （`AndroidViewHolder.draw` → `AndroidComposeView.drawAndroidView`），Chromium 在这条绘制路径上
 * 不稳定，部分设备会表现为网页闪烁。`slideIntoContainer` / `slideOutOfContainer` 只改 layout
 * offset、不产生图层，所以这里保留默认的位移与时长，去掉 fade 与 scale。
 */
private fun webViewEntryMetadata(predictiveBackEnabled: Boolean) = metadata {
    put(NavDisplay.TransitionKey) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            initialOffset = { fullWidth -> fullWidth }
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            targetOffset = { fullWidth -> fullWidth / 4 }
        )
    }
    put(NavDisplay.PopTransitionKey) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            initialOffset = { fullWidth -> -fullWidth / 4 }
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            targetOffset = { fullWidth -> fullWidth }
        )
    }
    if (predictiveBackEnabled) {
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) togetherWith slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                targetOffset = { fullWidth -> fullWidth }
            )
        }
    }
}

/** 全屏书页（阅读/漫画）走普通 nav3 场景：与文本阅读页同一段 600ms 交叉淡入淡出。 */
private fun readerEntryMetadata(predictiveBackEnabled: Boolean) = metadata {
    put(NavDisplay.TransitionKey) {
        fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
    }
    put(NavDisplay.PopTransitionKey) {
        fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
    }
    if (predictiveBackEnabled) {
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
        }
    }
}

/** 详情页更快一档：它是「打开一本书」的第二段，下面还压着阅读页。 */
private fun bookInfoEntryMetadata(predictiveBackEnabled: Boolean) = metadata {
    put(NavDisplay.TransitionKey) {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
    }
    put(NavDisplay.PopTransitionKey) {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
    }
    if (predictiveBackEnabled) {
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        }
    }
}

/** Keep parent overlays composed while NavDisplay leaves animation to the search scene or player host. */
private fun modalOverlayEntryMetadata(): Map<String, Any> =
    ModalOverlaySceneStrategy.modalOverlay() + metadata {
        put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            EnterTransition.None togetherWith ExitTransition.None
        }
    }

/**
 * 听书播放弹层「经典控制」的目标判定：栈顶是阅读界面时把请求交给它，
 * 否则新开阅读界面。抽成纯函数以便单测覆盖这条分支。
 *
 * 播放弹层不在导航栈上，所以判据是「当前顶层」而不是「上一站」。
 */
internal fun isReaderOnTop(backStack: List<NavKey>): Boolean =
    backStack.lastOrNull() is MainRouteReadBook

@Composable
private fun ConsumeBookPageResult(
    route: NavKey,
    isTop: Boolean,
    tracker: MainNavRouteTracker,
    onResult: suspend (BookPageResult) -> Unit,
) {
    val results by tracker.bookPageResults.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val result = results[route]
    LaunchedEffect(route, isTop, result, lifecycleOwner) {
        if (isTop && result != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (tracker.currentRoute == route && tracker.bookPageResults.value[route] == result) {
                    onResult(result)
                    tracker.takeBookPageResult(route)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun MainActivity.mainEntryProvider(
    backStack: MutableList<NavKey>,
    configuration: AppUiConfiguration,
    useRail: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateToRoute: (NavKey) -> Unit,
    onNavigateBack: () -> Unit,
) = entryProvider<NavKey> {
    entry<MainRouteToc> { route ->
        TocRouteScreen(
            bookUrl = route.bookUrl,
            initialPage = route.initialPage,
            onBackClick = { closeBookPage(backStack, route) },
            onChapterClick = { index ->
                closeBookPage(backStack, route, BookPageResult.ChapterSelected(index, 0))
            },
            onBookmarkClick = { index, position ->
                closeBookPage(backStack, route, BookPageResult.ChapterSelected(index, position))
            },
            onOpenReplaceRule = { editor ->
                onNavigateToRoute(editor?.let { MainRouteReplaceEdit(it) } ?: MainRouteReplaceRules(
                    route.bookUrl
                ))
            },
        )
    }
    entry<MainRouteBookInfoEdit> { route ->
        val viewModel = koinViewModel<BookInfoEditViewModel>()
        LaunchedEffect(route.bookUrl) { viewModel.loadBook(route.bookUrl) }
        BookInfoEditScreen(
            viewModel = viewModel,
            onBack = { closeBookPage(backStack, route) },
            onSave = {
                viewModel.save {
                    closeBookPage(
                        backStack,
                        route,
                        BookPageResult.InfoEdited
                    )
                }
            },
        )
    }
    entry<MainRouteReplaceRules> { route ->
        ReplaceRuleRouteScreen(
            bookUrl = route.bookUrl,
            onBackClick = { closeBookPage(backStack, route) },
            onNavigateToEdit = { onNavigateToRoute(MainRouteReplaceEdit(it)) },
        )
    }
    entry<MainRouteReplaceEdit> { route ->
        val viewModel =
            koinViewModel<ReplaceEditViewModel>(key = "replace_edit_${route.editor.sessionId}") {
                parametersOf(route.editor)
            }
        ReplaceEditRouteScreen(
            viewModel = viewModel,
            onBack = { closeBookPage(backStack, route) },
            onSaveSuccess = { closeBookPage(backStack, route) },
        )
    }
    entry<MainRouteWebView>(
        metadata = webViewEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val viewModel = koinViewModel<WebViewModel>(
            key = "WebView:${route.url}:${route.sourceOrigin}:${route.sourceVerificationEnable}",
        )
        val browserIntent = remember(route) {
            Intent().apply {
                putExtra("title", route.title)
                putExtra("url", route.url)
                putExtra("sourceOrigin", route.sourceOrigin)
                putExtra("sourceName", route.sourceName)
                route.sourceType?.let { putExtra("sourceType", it) }
                putExtra("sourceVerificationEnable", route.sourceVerificationEnable)
                putExtra("refetchAfterSuccess", route.refetchAfterSuccess)
                putExtra("html", route.html)
            }
        }
        WebViewRouteScreen(
            intent = browserIntent,
            viewModel = viewModel,
            onFinish = onNavigateBack,
            onImportBookSource = { importUrl ->
                onNavigateToRoute(MainRouteBookSourceManage(importUrl))
            },
        )
    }
    entry<MainRouteSourceLogin>(
        metadata = ModalOverlaySceneStrategy.modalOverlay(),
    ) { route ->
        DisposableEffect(route) {
            MainActivity.hasActiveSourceLoginRoute = true
            onDispose {
                MainActivity.hasActiveSourceLoginRoute = false
            }
        }
        val viewModel = koinViewModel<SourceLoginViewModel>(
            key = "SourceLogin:${route.type}:${route.sourceKey}:${route.bookUrl}",
        )
        SourceLoginRoute(
            request = SourceLoginIntent.Initialize(route.type, route.sourceKey, route.bookUrl),
            viewModel = viewModel,
            host = this@mainEntryProvider,
            onBack = onNavigateBack,
        )
    }
    entry<MainRouteBookSourceManage> { route ->
        BookSourceRouteScreen(
            initialImportUrl = route.importUrl,
            closeAfterImport = route.importUrl != null,
            onImportClosed = onNavigateBack,
            onBackClick = onNavigateBack,
            onAddSource = { onNavigateToRoute(MainRouteBookSourceEdit()) },
            onEditSource = { onNavigateToRoute(MainRouteBookSourceEdit(it)) },
            onLoginSource = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onSearchSource = { sourceName, sourceUrl ->
                val scopeRaw = BookSearchScope.encodeSource(sourceName, sourceUrl)
                onNavigateToRoute(MainRouteSearch(null, scopeRaw))
            },
            onDebugSource = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceDebug(sourceUrl))
            },
        )
    }
    entry<MainRouteBookSourceEdit> { route ->
        val viewModel = koinViewModel<BookSourceEditViewModel>(
            key = "BookSourceEdit:${route.sourceUrl.orEmpty()}",
        )
        BookSourceEditRoute(
            sourceUrl = route.sourceUrl,
            viewModel = viewModel,
            onBack = { savedSourceUrl ->
                if (backStack.size == 1) {
                    savedSourceUrl?.let {
                        this@mainEntryProvider.setResult(
                            android.app.Activity.RESULT_OK,
                            Intent().putExtra("origin", it),
                        )
                    }
                    this@mainEntryProvider.finish()
                } else onNavigateBack()
            },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onDebug = { onNavigateToRoute(MainRouteBookSourceDebug(it)) },
            onSearch = { onNavigateToRoute(MainRouteSearch(null, it.toString())) },
        )
    }
    entry<MainRouteBookSourceDebug> { route ->
        val viewModel = koinViewModel<BookSourceDebugViewModel>(
            key = "BookSourceDebug:${route.sourceUrl.orEmpty()}",
        )
        BookSourceDebugRoute(route.sourceUrl, viewModel, onNavigateBack)
    }
    entry<MainRouteHome> {
        val mainViewModel = koinViewModel<MainViewModel>()
        val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
        MainScreen(
            mainUiState = mainUiState,
            onIntent = mainViewModel::onIntent,
            effects = mainViewModel.effects,
            useRail = useRail,
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettings)
            },
            onNavigateToSearch = { key ->
                onNavigateToRoute(
                    MainRouteSearch(
                        key = key?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
            },
            onNavigateToScopedSearch = { scopeRaw ->
                onNavigateToRoute(MainRouteSearch(key = null, scopeRaw = scopeRaw))
            },
            onNavigateToRemoteImport = {
                onNavigateToRoute(MainRouteImportRemote)
            },
            onNavigateToLocalImport = {
                onNavigateToRoute(MainRouteImportLocal)
            },
            onNavigateToCache = { groupId ->
                onNavigateToRoute(MainRouteCache(groupId))
            },
            onNavigateToBookCacheManage = {
                onNavigateToRoute(MainRouteBookCacheManage)
            },
            onOpenBookshelfBook = { book, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteReadBook(
                        bookUrl = book.bookUrl,
                        sharedCoverKey = sharedCoverKey,
                    )
                )
            },
            onNavigateToBackupSettings = {
                onNavigateToRoute(MainRouteSettingsBackup)
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(
                    MainRouteExploreShow(
                        title = title,
                        sourceUrl = sourceUrl,
                        exploreUrl = exploreUrl
                    )
                )
            },
            onNavigateToSourceLogin = { type, sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(type, sourceUrl))
            },
            onNavigateToBookSourceManage = {
                onNavigateToRoute(MainRouteBookSourceManage())
            },
            onNavigateToReplaceRules = { onNavigateToRoute(MainRouteReplaceRules()) },
            onNavigateToBookSourceEdit = {
                onNavigateToRoute(MainRouteBookSourceEdit(it))
            },
            onNavigateToRuleSub = {
                onNavigateToRoute(MainRouteRuleSub)
            },
            onNavigateToReadRecord = {
                onNavigateToRoute(MainRouteReadRecord)
            },
            onNavigateToReadRecordOverview = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            },
            onNavigateToHighlightTagRule = {
                onNavigateToRoute(MainRouteHighlightTagRule)
            },
            onNavigateToAbout = {
                onNavigateToRoute(MainRouteAbout)
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteSettings> {
        ConfigNavScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToOther = { backStack.add(MainRouteSettingsOther) },
            onNavigateToRead = { backStack.add(MainRouteSettingsRead) },
            onNavigateToCover = { backStack.add(MainRouteSettingsCover) },
            onNavigateToTheme = { backStack.add(MainRouteSettingsTheme) },
            onNavigateToBackup = { backStack.add(MainRouteSettingsBackup) },
            onNavigateToDownloadCache = { backStack.add(MainRouteSettingsDownloadCache) },
            onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
            onNavigateToLab = { backStack.add(MainRouteSettingsLabConfig) },
            onNavigateToPrivate = { backStack.add(MainRouteSettingsPrivate) }
        )
    }

    entry<MainRouteSettingsOther> {
        OtherConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsRead> {
        ReadConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCover> {
        CoverConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCoverAlbums = {
                backStack.add(MainRouteSettingsCoverAlbums)
            },
        )
    }

    entry<MainRouteSettingsCoverAlbums> {
        CoverAlbumManageRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTheme> {
        ThemeConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCustomTheme = { backStack.add(MainRouteSettingsCustomTheme) },
            onNavigateToThemeManage = { backStack.add(MainRouteSettingsThemeManage) }
        )
    }

    entry<MainRouteSettingsBackup> {
        BackupConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsDownloadCache> {
        DownloadCacheConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTranslation> {
        TranslationConfigRouteScreen(
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteSettingsLabConfig> {
        LabConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsPrivate> {
        // 隐私设置页可以关掉三种验证时机，所以进入前要求确认身份（生物或密码）。
        // 只校验、不授予：进来一次不该顺带解锁私密内容。
        PrivateVerifyGate(onCancel = { onNavigateBack() }) {
            PrivateConfigRouteScreen(onBackClick = { onNavigateBack() })
        }
    }

    entry<MainRouteSettingsCustomTheme> {
        CustomThemeRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteSettingsThemeManage> {
        ThemeManageRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteImportLocal> {
        ImportBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteImportRemote> {
        RemoteBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteCache> { route ->
        BookshelfManageRouteScreen(
            groupId = route.groupId,
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { name, author, bookUrl ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl
                    )
                )
            }
        )
    }

    entry<MainRouteBookCacheManage> {
        BookCacheManageRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadBook>(
        metadata = readerEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        // 私密闸门：阅读记录 / 通知栏 / 深链等直开阅读器的路径全都在这里被拦一次，
        // 未授权时阅读器根本不会被组合（也就没有加载正文的机会）
        PrivateReadGate(
            bookUrl = route.bookUrl,
            onExit = {
                if (backStack.size > 1) {
                    MainNavigator.navigateBack(
                        this@mainEntryProvider,
                        backStack,
                        navRouteTracker,
                        fromRoute = route,
                    )
                } else {
                    // 通知 / 深链可能让阅读器成为栈里唯一一项：没有"当前页"可退，
                    // 按约定落到书架，而不是把应用关掉
                    backStack.clear()
                    backStack.add(MainRouteHome)
                }
            },
            onOpenLocalPasswordSettings = {
                onNavigateToRoute(MainRouteSettingsPrivate)
            },
        ) {
            ReaderPerfTrace.marker("nav.entry.begin")
            val readBookViewModel = koinViewModel<ReadBookViewModel>(
                key = "ReadBook:${route.bookUrl ?: "last-read"}"
            )
            ReaderPerfTrace.marker("nav.book-vm.ready")
            val readerSessionViewModel = koinViewModel<ReaderSessionViewModel>(
                key = "ReaderSession:${route.bookUrl ?: "last-read"}"
            )
            ReaderPerfTrace.marker("nav.viewmodels.ready")
            val controller = remember(readBookViewModel, readerSessionViewModel) {
                ReadBookController(
                    this@mainEntryProvider,
                    readBookViewModel,
                    readerSessionViewModel,
                    // 画布在首次组合就会请求分页，本路由要开哪本书必须随构造就位：
                    // 那期间 ReadBook 单例里可能还是上一本书的章节。
                    routeBookUrl = route.bookUrl,
                )
            }
            ReaderPerfTrace.marker("nav.controller.ready")
            val lifecycleOwner = LocalLifecycleOwner.current
            val initRequest = remember(route) {
                ReadBookInitRequest(
                    bookUrl = route.bookUrl,
                    inBookshelf = route.inBookshelf,
                    chapterChanged = route.chapterChanged,
                )
            }
            val effectsReady = remember(readBookViewModel) { CompletableDeferred<Unit>() }
            ConsumeBookPageResult(
                route,
                backStack.lastOrNull() == route,
                navRouteTracker
            ) { result ->
                effectsReady.await()
                when (result) {
                    is BookPageResult.ChapterSelected -> readBookViewModel.onIntent(
                        ReadBookIntent.OpenChapterResult(result.index, result.position)
                    )

                    BookPageResult.RulesClosed -> readBookViewModel.onIntent(ReadBookIntent.ReplaceRuleResult)
                    else -> Unit
                }
            }
            val bookInfoResults by navRouteTracker.bookInfoResults.collectAsStateWithLifecycle()
            LaunchedEffect(bookInfoResults[route], backStack.lastOrNull()) {
                if (backStack.lastOrNull() == route && bookInfoResults.containsKey(route)) {
                    effectsReady.await()
                    navRouteTracker.takeBookInfoResult(route)?.let { deleted ->
                        readBookViewModel.onIntent(ReadBookIntent.BookInfoResult(deleted))
                    }
                }
            }
            val readerResumeState = remember(controller, lifecycleOwner) { booleanArrayOf(false) }
            val collectorReady = remember(readBookViewModel) { booleanArrayOf(false) }
            fun resumeReader() {
                if (readerResumeState[0]) return
                readerResumeState[0] = true
                controller.onResume()
                readBookViewModel.onIntent(ReadBookIntent.OnResume)
            }

            fun pauseReader() {
                if (!readerResumeState[0]) return
                readerResumeState[0] = false
                readBookViewModel.onIntent(ReadBookIntent.OnPause)
                controller.onPause()
            }

            ReadBookRouteScreen(
                viewModel = readBookViewModel,
                readerSessionViewModel = readerSessionViewModel,
                host = controller,
                controller = controller,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                sharedCoverKey = route.sharedCoverKey,
                isTopRoute = (backStack.lastOrNull() as? MainRouteReadBook)?.let {
                    route.bookUrl == null || it.bookUrl == route.bookUrl
                } ?: false,
                onEffectsReady = { effectsReady.complete(Unit) },
                onOpenToc = { bookUrl, page -> onNavigateToRoute(MainRouteToc(bookUrl, page)) },
                onOpenReplaceRule = { bookUrl, editor ->
                    onNavigateToRoute(editor?.let { MainRouteReplaceEdit(it) }
                        ?: MainRouteReplaceRules(bookUrl))
                },
                onOpenBookInfo = { name, author, bookUrl ->
                    onNavigateToRoute(
                        MainRouteBookInfo(
                            name, author, bookUrl,
                            openRequestId = System.nanoTime(),
                        )
                    )
                },
                onOpenSearch = { word, bookUrl, autoFocus ->
                    onNavigateToRoute(
                        MainRouteSearchContent(
                            bookUrl = bookUrl,
                            searchWord = word,
                            searchResultIndex = readBookViewModel.uiState.value.searchResultIndex,
                            autoFocus = autoFocus,
                        )
                    )
                },
                onNavigateBack = {
                    MainNavigator.navigateBack(
                        this@mainEntryProvider,
                        backStack,
                        navRouteTracker,
                        fromRoute = route,
                    )
                },
            )

            DisposableEffect(controller, lifecycleOwner) {
                activeReadBookInputHandler = controller
                activeReadBookRoute = route
                MainActivity.hasActiveReadBookRoute = true
                controller.onClose = { onNavigateBack() }

                val lifecycleObserver = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            if (collectorReady[0]) resumeReader()
                        }

                        Lifecycle.Event.ON_PAUSE -> pauseReader()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                onDispose {
                    pauseReader()
                    readBookViewModel.onIntent(ReadBookIntent.OnDispose)
                    lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                    if (activeReadBookInputHandler === controller) {
                        activeReadBookInputHandler = null
                    }
                    if (activeReadBookRoute == route) {
                        activeReadBookRoute = null
                    }
                    MainActivity.hasActiveReadBookRoute = false
                    controller.onClose = null
                    this@mainEntryProvider.toggleSystemBar(configuration.appShell.showStatusBar)
                }
            }

            LaunchedEffect(route, readBookViewModel, lifecycleOwner) {
                // Resolving the book and applying its read style do not depend on launcher effects.
                // Start that I/O immediately; initData still waits below because it can emit effects.
                val initialBook = readBookViewModel.initReadBookConfig(initRequest)
                effectsReady.await()
                collectorReady[0] = true
                readBookViewModel.initData(initRequest, initialBook) {
                    readBookViewModel.markJustInitData()
                    controller.onRouteInitialized()
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        resumeReader()
                    }
                }
            }
        }
    }

    entry<MainRouteSearchContent>(
        metadata = modalOverlayEntryMetadata() + ModalOverlaySceneStrategy.searchSlide()
    ) { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = "SearchContent:${route.bookUrl}",
            parameters = { parametersOf(route) }
        )
        SearchContentRouteScreen(
            viewModel = viewModel,
            isTopRoute = backStack.lastOrNull() == route,
            predictiveBackEnabled = configuration.appShell.predictiveBackEnabled,
            autoFocus = route.autoFocus,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteSearch> { route ->
        val searchViewModel = koinViewModel<SearchViewModel>()

        LaunchedEffect(route.key, route.scopeRaw, searchViewModel) {
            searchViewModel.onIntent(
                SearchIntent.Initialize(
                    key = route.key,
                    scopeRaw = route.scopeRaw
                )
            )
        }

        SearchRouteScreen(
            viewModel = searchViewModel,
            onBack = {
                onNavigateBack()
            },
            onOpenBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onOpenSourceManage = {
                onNavigateToRoute(MainRouteBookSourceManage())
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }




    entry<MainRouteRuleSub> {
        RuleSubRouteScreen(
            onBackClick = { onNavigateBack() },
            onImportBookSource = {
                onNavigateToRoute(MainRouteBookSourceManage(it))
            },
        )
    }

    entry<MainRouteReadRecord> {
        ReadRecordRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        onNavigateToRoute(MainRouteSearch(key = name))
                    }
                }
            },
            onSummaryClick = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            }
        )
    }

    entry<MainRouteReadRecordOverview> {
        ReadRecordOverviewRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        onNavigateToRoute(MainRouteSearch(key = name))
                    }
                }
            }
        )
    }

    entry<MainRouteBookInfo>(
        metadata = bookInfoEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val bookInfoViewModel = koinViewModel<BookInfoViewModel>(key = "BookInfo:${route.bookUrl}")
        ConsumeBookPageResult(route, backStack.lastOrNull() == route, navRouteTracker) { result ->
            when (result) {
                is BookPageResult.ChapterSelected -> bookInfoViewModel.onTocResult(
                    Triple(result.index, result.position, false)
                )

                BookPageResult.TocCancelled -> bookInfoViewModel.onTocResult(null)
                BookPageResult.InfoEdited -> bookInfoViewModel.onInfoEdited()
                else -> Unit
            }
        }
        BookInfoRouteScreen(
            bookUrl = route.bookUrl,
            name = route.name,
            author = route.author,
            origin = route.origin,
            coverPath = route.coverPath,
            viewModel = bookInfoViewModel,
            onOpenToc = { onNavigateToRoute(MainRouteToc(it)) },
            onOpenInfoEdit = { onNavigateToRoute(MainRouteBookInfoEdit(it)) },
            onBack = {
                MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
            },
            onFinish = { resultCode, _ ->
                val parent = backStack.getOrNull(backStack.lastIndex - 1)
                val popped = MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
                if (popped) {
                    if (parent is MainRouteReadBook) {
                        navRouteTracker.reportBookInfoResult(
                            parent,
                            resultCode == Activity.RESULT_OK
                        )
                    }
                }
                popped
            },
            onOpenSearch = { keyword ->
                onNavigateToRoute(MainRouteSearch(key = keyword))
            },
            onOpenBookSourceEdit = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceEdit(sourceUrl))
            },
            onOpenSourceLogin = { sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, sourceUrl))
            },
            // 私密内容需要本地密码：直接把人送到私密设置页，而不是只弹一句提示
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettingsPrivate)
            },
            onOpenReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadBook(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                        // 与详情页封面登记在同一个 key 上，封面才能从详情页继续飞进阅读页。
                        sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(bookUrl),
                    )
                )
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl, origin, coverPath))
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(MainRouteExploreShow(title, sourceUrl, exploreUrl))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(route.bookUrl),
        )
    }

    entry<MainRouteExploreShow> { route ->
        val exploreViewModel = koinViewModel<ExploreShowViewModel>()

        LaunchedEffect(route.sourceUrl, route.exploreUrl, exploreViewModel) {
            exploreViewModel.onIntent(
                ExploreShowIntent.InitData(route.sourceUrl, route.exploreUrl)
            )
        }

        ExploreShowRouteScreen(
            viewModel = exploreViewModel,
            title = route.title ?: "探索",
            onBack = { onNavigateBack() },
            onBookClick = { book, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = book.name,
                        author = book.author,
                        bookUrl = book.bookUrl,
                        origin = book.origin,
                        coverPath = book.coverUrl,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteHighlightTagRule> {
        HighlightTagRuleRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteAbout> {
        val viewModel = koinViewModel<AboutViewModel>()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is AboutEffect.OpenUrl -> context.openUrl(effect.url)
                    is AboutEffect.ShowToast -> context.toastOnUi(effect.message)
                    is AboutEffect.StartDownload -> Download.start(
                        context,
                        effect.url,
                        effect.fileName
                    )
                }
            }
        }
        AboutScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            onBack = { onNavigateBack() },
        )
    }
}
