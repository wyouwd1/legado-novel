# W3 外围域编辑计划（D7 / D8 / D9 / D7a）

> 快照：2026-10-21 11:08 CST
> 分支：`subtract/novel-only`（W1/W2a 删除已在工作区、未提交；写本文前 `git status` 约 298 项 / 234 个删除）
> 性质：只读侦察。未修改任何源码，未执行 Gradle。
> 行号说明：下列 `CUR` 行号是本次快照的工作区行号；主控并行编辑后行号会漂移，**执行时以首/尾锚点为准**。块删除均整块删除，括号净差 0。

## 0. 先看结论（P0）

1. **D7a 目前不是 3 tab**：`MainDestination.mainDestinations` 仍是 4 项 `Home / Bookshelf / Explore / My`（Rss 已在 W1 删除）。必须做一次导航收敛，不能只改文案。
2. **D7 不能整包直删 `ui/main/homepage`**：`BookInfoScreen.kt:114,2080-2083` 复用 `modules/BannerModule.kt` 与 `HomepageBookItemUi` 做“相关书籍”横幅。先把 Banner 移到共享组件，再删其余 18 个文件。
3. **D8 `ReaderProvider` 不是 Web 服务之外的干净保留项**：它 `import api.controller.BookController/BookSourceController` 和 `ReturnData`（`ReaderProvider.kt:13-14,60-119`）。如果 `api/controller/**` 归档，`ReaderProvider` 无法编译。必须在 W3-2 开工前裁决（见 §3.4）。
4. **D7 的 `HomepageModule/HomepageCustomSet` entity/DAO 和 `AppDatabase` 不在 W3 删**：Room `version = 131`（`AppDatabase.kt:139`）未变时删除 entity 会改变 schema identity hash，App 启动即报 “Room cannot verify the data integrity”。这些随 W4-1 的 `version+1` + migration 一起删。
5. **D9 `TranslationCache` 是文件缓存模型，不是 Room 表**（`data/entities/TranslationCache.kt`，读写 `.chunks.jsonl`），可以在 W3 直接删。
6. **D8 的书源调试 socket 属于 D8**：`web/socket/BookSourceDebugWebSocket.kt` 只被 `KtorServer.kt:150-152` 实例化；应用内书源调试走 `model/Debug.kt` + `BookSourceDebugViewModel/Screen`，不随 socket 删除。
7. **media3 不删**：`app/build.gradle.kts:218-220` 的 `media3-exoplayer/media3-datasource-okhttp` 仍服务书源 JS 的 `AnalyzeUrl.getMediaItem()`（`AnalyzeUrl.kt:6,821-828`，W2-S6 已内联保留）。不要把 media3 当 D8/D9 的清理对象。

---

## 1. 可整删文件清单（路径 + 行数 + 依据）

### 1.1 D7 / D7a：W3 直接整删（31 文件，约 7,316 行）

`ui/main/homepage/**` 中除 `modules/BannerModule.kt` 外的 18 个文件（Banner 要移走，见 §1.2）：

| # | 路径 | 行数 | 依据 |
|---:|---|---:|---|
| 1 | `app/src/main/java/io/legado/app/ui/main/homepage/HomepageContract.kt` | 140 | 模块系统 UiState/Sheet/Action 契约；`HomepageBookItemUi` 先移到 Banner 新组件 |
| 2 | `.../homepage/HomepageEffect.kt` | 20 | 模块系统 Effect |
| 3 | `.../homepage/HomepageModuleFeed.kt` | 584 | 模块瀑布流/排行/按钮组渲染 |
| 4 | `.../homepage/HomepageModuleManageSheet.kt` | 533 | 模块管理 Sheet |
| 5 | `.../homepage/HomepageViewModel.kt` | 1,197 | 模块拉取、书源模块解析、自定义集合 |
| 6 | `.../homepage/manage/AddCustomModuleDialog.kt` | 185 | 自定义模块对话框 |
| 7 | `.../homepage/manage/BrowseSourcesPage.kt` | 44 | 浏览书源模块页 |
| 8 | `.../homepage/manage/CustomSetAddModulesPage.kt` | 91 | 自定义集合加模块页 |
| 9 | `.../homepage/manage/SetDetailPage.kt` | 200 | 自定义集合详情 |
| 10 | `.../homepage/manage/SetListPage.kt` | 127 | 自定义集合列表 |
| 11 | `.../homepage/manage/SourceBrowseDetailPage.kt` | 399 | 书源模块浏览详情 |
| 12 | `.../homepage/modules/ButtonGroupModule.kt` | 166 | 首页按钮组模块 |
| 13 | `.../homepage/modules/CardModule.kt` | 152 | 首页卡片模块 |
| 14 | `.../homepage/modules/GridModule.kt` | 67 | 首页网格模块 |
| 15 | `.../homepage/modules/GridRankingModule.kt` | 214 | 首页网格排行模块 |
| 16 | `.../homepage/modules/RankingModule.kt` | 178 | 首页排行模块 |
| 17 | `.../homepage/modules/SkeletonPlaceholders.kt` | 405 | 首页骨架屏 |
| 18 | `.../homepage/modules/WaterfallModule.kt` | 169 | 首页瀑布流模块 |

`ui/main/home/**` 整个 Home tab 的宿主/VM/dashboard（D7a 合并后无宿主）：

| # | 路径 | 行数 | 依据 |
|---:|---|---:|---|
| 19 | `app/src/main/java/io/legado/app/ui/main/home/HomeContract.kt` | 115 | `HomeIntent/HomeUiState/HomeSheet/HomeDialog` 仅 Home tab 使用 |
| 20 | `.../home/HomeScreen.kt` | 1,433 | `HomeRouteScreen` 同时承载 Home dashboard + Homepage feed；Home tab 删除后零宿主 |
| 21 | `.../home/HomeViewModel.kt` | 384 | Home dashboard 统计/备份卡/最近阅读；唯一消费者是 `HomeScreen.kt` |

Home dashboard 的 domain/data 支撑（只被 `HomeViewModel` 消费）：

| # | 路径 | 行数 | 依据 |
|---:|---|---:|---|
| 22 | `app/src/main/java/io/legado/app/data/repository/HomeDashboardRepository.kt` | 95 | 唯一消费者 `HomeViewModel`；`di/appModule.kt:282` |
| 23 | `app/src/main/java/io/legado/app/domain/gateway/HomeDashboardGateway.kt` | 27 | 同上 |
| 24 | `app/src/main/java/io/legado/app/domain/usecase/HomeDashboardUseCase.kt` | 91 | 同上；`di/appModule.kt:361` |
| 25 | `app/src/main/java/io/legado/app/domain/model/HomeDashboard.kt` | 48 | `HomeDashboardSection` 等仅 HomeScreen/HomeViewModel |

Homepage 的 domain/data 支撑：

| # | 路径 | 行数 | 依据 |
|---:|---|---:|---|
| 26 | `app/src/main/java/io/legado/app/data/repository/HomepageModulesRepository.kt` | 98 | 唯一消费者 `HomepageViewModel`；`di/appModule.kt:411` |
| 27 | `app/src/main/java/io/legado/app/data/repository/HomepageSettingsRepository.kt` | 29 | 唯一消费者 `HomepageViewModel`；`di/appModule.kt:404` |
| 28 | `app/src/main/java/io/legado/app/domain/gateway/HomepageModulesGateway.kt` | 35 | 同上 |
| 29 | `app/src/main/java/io/legado/app/domain/gateway/HomepageSettingsGateway.kt` | 10 | 同上 |
| 30 | `app/src/main/java/io/legado/app/domain/model/HomepageModels.kt` | 75 | `ModuleItem/CustomSetItem/ModuleDef/HomepageModuleType` 仅模块系统 |
| 31 | `app/src/main/java/io/legado/app/domain/model/settings/HomepageSettings.kt` | 5 | `hiddenSourceUrlsJson` 仅模块系统 |

> 合计：**31 文件 / 7,316 行**（不含要移动的 Banner 112 行）。
> 删除依据链：`HomepageViewModel` → `HomepageModulesGateway/HomepageSettingsGateway` → 两个 Repository → 两个 DAO（DAO 留 W4）；`HomeScreen` → `HomeViewModel` → `HomeDashboardUseCase/Repository`。已对包外引用逐个 grep，除 `BookInfoScreen` 的 Banner 与 `BookSourceEditViewModel` 的字段展示外无外部消费者。

### 1.2 `BannerModule.kt`：不是删除，是迁移（112 行）

| 原路径 | 新路径 | 动作 |
|---|---|---|
| `app/src/main/java/io/legado/app/ui/main/homepage/modules/BannerModule.kt` | `app/src/main/java/io/legado/app/ui/widget/components/book/BookBannerRow.kt` | `git mv` 后改包名/composable 名；`HomepageBookItemUi` 改成新文件内的 `@Stable BookBannerItemUi`（`SearchBook` + `BookShelfState`），不保留对 homepage 包的 import |
| `app/src/main/java/io/legado/app/ui/book/info/BookInfoScreen.kt:114` | 同文件 | import 改 `io.legado.app.ui.widget.components.book.BookBannerRow` |
| `BookInfoScreen.kt:2080-2083` | 同文件 | 调用改为 `BookBannerRow(books = books.map { BookBannerItemUi(it) }.toImmutableList(), ...)` |

这不是新增抽象：`BookInfoScreen` 的“相关书籍”横幅是真实消费者；Banner 留在 homepage 包会让 D7 删不干净。

### 1.3 D7 的 W4 延迟删除（W3 不要动）

| 路径/声明 | 行数/行号 | 处理 |
|---|---:|---|
| `app/src/main/java/io/legado/app/data/entities/HomepageModule.kt` | 25 | W4-1 与 `version+1` + migration 同批删 |
| `app/src/main/java/io/legado/app/data/entities/HomepageCustomSet.kt` | 12 | 同上 |
| `app/src/main/java/io/legado/app/data/dao/HomepageModuleDao.kt` | 67 | 同上 |
| `app/src/main/java/io/legado/app/data/dao/HomepageCustomSetDao.kt` | 52 | 同上 |
| `AppDatabase.kt:39-40,104-105,147,321-322` | 8 行声明 | 同上；`@Database(version = 131)` 不动 |
| `di/appDatabaseModule.kt:26-27,88-89` | 4 行 | 同上 |

**原因**：Room 的 identity hash 由 `@Database` 的 entity 列表决定；只删 entity 不升 version，已有 131 库启动会失败。W3 只删“消费者”，保留空壳 DAO/entity 不影响编译。

### 1.4 D8：源码归档清单（11 文件，约 1.3k 行）

移到 `attic/webservice/`，保留原仓库相对路径：

| 路径 | 行数 | 说明 |
|---|---:|---|
| `app/src/main/java/io/legado/app/web/KtorServer.kt` | 214 | HTTP + WebSocket 宿主，唯一 `WebService` 调用方 |
| `app/src/main/java/io/legado/app/web/ReadMe.md` | 3 | web 包说明 |
| `app/src/main/java/io/legado/app/web/socket/BookSearchWebSocket.kt` | 124 | 仅 KtorServer 使用 |
| `app/src/main/java/io/legado/app/web/socket/BookSourceDebugWebSocket.kt` | 63 | 仅 KtorServer 使用；应用内调试不依赖它 |
| `app/src/main/java/io/legado/app/web/utils/AssetsWeb.kt` | 45 | 读 `assets/web`，随 Web 服务归档 |
| `app/src/main/java/io/legado/app/api/controller/BookController.kt` | 328 | 仅 KtorServer + ReaderProvider 使用 |
| `app/src/main/java/io/legado/app/api/controller/BookSourceController.kt` | 80 | 同上 |
| `app/src/main/java/io/legado/app/api/controller/ReplaceRuleController.kt` | 98 | 仅 KtorServer 使用 |
| `app/src/main/java/io/legado/app/api/ReturnData.kt` | 29 | 仅 controller/KtorServer/ReaderProvider |
| `app/src/main/java/io/legado/app/service/WebService.kt` | 236 | 前台服务入口 |
| `app/src/main/java/io/legado/app/service/WebTileService.kt` | 98 | QS 磁贴，只开关 WebService |

可选（取决于 §3.4 裁决）：

| 路径 | 行数 | 说明 |
|---|---:|---|
| `app/src/main/java/io/legado/app/api/ReaderProvider.kt` | 145 | 依赖上面 2 个 controller + ReturnData；不能单独保留 |

**APK 侧资源删除**：

| 路径 | 规模 | 动作 |
|---|---:|---|
| `app/src/main/assets/web/**` | 58 文件 / 1.7 MB | 整个目录 `git rm -r`；这是 `modules/web/scripts/sync.js` 同步进来的前端产物 |
| `app/src/main/res/drawable/ic_web_service_noti.xml` | 1 文件 | 仅 WebService 通知使用，删 |
| `app/src/main/res/values/non_translat.xml:10` | `http_ip` 1 条 | 仅 WebService 使用，删 |
| `app/src/main/res/values*/strings.xml` | 8 条 × 4 语言 | `web_service`、`web_service_desc`、`web_service_wake_lock`、`web_service_wake_lock_summary`、`web_service_local_network_permission_denied`、`web_service_auto_start`、`web_port_title`、`network_connection_unavailable`；`copy_url`/`open_in_browser` 被 WebView 复用，保留 |
| `app/src/main/res/xml/pref_main.xml:14-21`、`pref_config_other.xml:27-31,179-183,204-207` | 4 个 XML 块 | 这些 XML 当前无 Kotlin 引用（历史 PreferenceFragment 产物）；删 Web 项，避免删字符串后资源链接悬空 |

`modules/web/**` **原位保留**，不参与 APK/Gradle 构建；`app/src/main/assets/web` 只是它的构建产物。

### 1.5 D9：翻译域整删（16 文件，约 1,603 行）

| # | 路径 | 行数 | 依据 |
|---:|---|---:|---|
| 1 | `app/src/main/java/io/legado/app/ui/config/translation/TranslationConfigContract.kt` | 17 | 翻译设置页契约 |
| 2 | `.../ui/config/translation/TranslationConfigScreen.kt` | 97 | 翻译设置页（含 `TranslationConfigRouteScreen`） |
| 3 | `.../ui/config/translation/TranslationConfigViewModel.kt` | 46 | 翻译设置 VM |
| 4 | `app/src/main/java/io/legado/app/model/translation/TranslationChapterState.kt` | 35 | `TranslationChapterKey/Status/State`，仅翻译链 |
| 5 | `app/src/main/java/io/legado/app/model/translation/TranslationManager.kt` | 205 | 翻译任务单例 |
| 6 | `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt` | 612 | 章节翻译用例 |
| 7 | `app/src/main/java/io/legado/app/domain/model/PartialTranslationAssembler.kt` | 58 | 仅翻译用例 |
| 8 | `app/src/main/java/io/legado/app/domain/model/TranslationResultPreviewParser.kt` | 80 | 仅翻译用例 |
| 9 | `app/src/main/java/io/legado/app/domain/model/settings/TranslationSettings.kt` | 11 | 翻译设置模型 |
| 10 | `app/src/main/java/io/legado/app/domain/gateway/TranslationCacheGateway.kt` | 70 | 翻译缓存接口 |
| 11 | `app/src/main/java/io/legado/app/domain/gateway/TranslationSettingsGateway.kt` | 10 | 翻译设置接口 |
| 12 | `app/src/main/java/io/legado/app/data/repository/TranslationCacheRepositoryImpl.kt` | 192 | 文件缓存实现 |
| 13 | `app/src/main/java/io/legado/app/data/entities/TranslationCache.kt` | 28 | `.chunks.jsonl` 文件模型，不是 Room entity |
| 14 | `app/src/main/java/io/legado/app/ui/book/read/TranslationThinkingCapsule.kt` | 57 | 翻译“模型思考中”浮层 |
| 15 | `app/src/test/java/io/legado/app/domain/model/PartialTranslationAssemblerTest.kt` | 35 | 随用例删 |
| 16 | `app/src/test/java/io/legado/app/domain/model/TranslationResultPreviewParserTest.kt` | 48 | 随用例删 |

**不要删的 Translation 文件**（有其它域消费者）：

| 文件 | 消费者 | 原因 |
|---|---|---|
| `domain/model/TranslationModels.kt` | `DictionaryRepositoryImpl.kt:8-9`、`DictionaryGateway`、`ContentChunker.kt` | `DictPair/BookDictionary/TextChunk` 是字典/分块共享模型 |
| `domain/model/TranslationDictionaryPolicy.kt` | `DictionaryRepositoryImpl.kt:7,56` | 翻译词典合并策略，属于字典域 |
| `domain/model/TranslationConstants.kt` | `AiModels.kt:191-192,307`、`AiProfileRepository.kt:22,214-222,319-321` | `DEFAULT_TEMPERATURE/DEFAULT_PROMPT` 仍被保留的 AI 底座使用 |
| `domain/model/TranslationModels.kt`（`RetryReason`） | `TranslateChapterUseCase` 删除后无引用，但 `DictPair` 等仍需要，故保留整文件 | — |
| `data/repository/DictionaryRepositoryImpl.kt` / `DictionaryGateway` | 字典功能（保护项） | 不属 D9 |

`TranslationConstants.providerDisplayNames/targetLanguages` 仅翻译设置页使用；文件保留但可后续在 W5 AI 清理时再收口，不要在本波为了 30 行牵动 AI 底座。

---

## 2. 必须精确编辑的接入点

### 2.1 导航宿主（D7a + D9）

#### `MainDestination.kt`（D7a，目标 3 tab）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 `Home` object | 11-14 | `object Home : MainDestination(` | `labelId = R.string.home` 后的 `)` |
| 改主列表为 3 项 | 33 | `val mainDestinations = persistentListOf<MainDestination>(` | `Home, Bookshelf, Explore, My)` |
| 保留 `ordered()` 兼容旧顺序 | 35-43 | — | `mapNotNull(byRoute::get)` 会自动丢弃旧 `"home"`/`"rss"` |

> 旧 `mainNavigationOrder = "home,bookshelf,..."` 不会崩：`mapNotNull` 丢弃未知 route，剩余 3 项按顺序 + `filterNot` 补齐。旧 `defaultHomePage = "home"` 在 `MainScreen.kt:198-204` 的 `indexOfFirst` 返回 -1，落到 index 0（Bookshelf），无需数据迁移。

#### `MainScreen.kt`（D7a + D8）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 HomeRouteScreen import | 87 | `import io.legado.app.ui.main.home.HomeRouteScreen` | 同左 |
| 删除 `onNavigateToBackupSettings` 参数 | 137 | `onNavigateToBackupSettings: () -> Unit,` | 同左 |
| 删除 `onNavigateToReadRecordOverview` 参数 | 146 | `onNavigateToReadRecordOverview: () -> Unit,` | 同左 |
| 删除 `MainEffect.OpenUrl` / `CopyUrl` 两个分支 | 158-164 | `is MainEffect.OpenUrl -> {` | `is MainEffect.CopyUrl -> context.sendToClip(effect.url)` |
| 删除 `MainDestination.Home` 整块 | 502-523 | `MainDestination.Home -> HomeRouteScreen(` | 对应 `)`（`animatedVisibilityScope` 后） |
| `MyRouteScreen` 调用去掉 `state/onIntent` | 574-585 | `MainDestination.My -> MyRouteScreen(` | `)` |

- 删除 Home 分支后 `onNavigateToBackupSettings`/`onNavigateToReadRecordOverview` 在 `MainScreen` 无消费者；`MainNavGraph.kt:445-447,485-487` 同步删实参，避免“传了但没人用”的死参数。
- `onNavigateToExploreShow` 仍被 Explore 分支使用（`MainScreen.kt:554`），保留。
- 删除 OpenUrl/CopyUrl 后 `context.sendToClip` import（105）与 `toUri` import（70）可删；`Intent` 仍用于 `StartActivity` 分支。

#### `MainNavGraph.kt`（D7a + D9）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 MainScreen 的 `onNavigateToBackupSettings` 实参 | 445-447 | `onNavigateToBackupSettings = {` | `},` |
| 删除 MainScreen 的 `onNavigateToReadRecordOverview` 实参 | 485-487 | `onNavigateToReadRecordOverview = {` | `},` |
| 删除 TranslationConfig import | 99 | `import io.legado.app.ui.config.translation.TranslationConfigRouteScreen` | 同左 |
| 删除 ConfigNav 的翻译实参 | 508 | `onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },` | 同左 |
| 删除翻译 entry | 551-556 | `entry<MainRouteSettingsTranslation> {` | 对应 `}`（`TranslationConfigRouteScreen` 后） |

#### `MainViewModel.kt`（D7a + D8）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 `MainDestination.Home` 过滤分支 | 101 | `MainDestination.Home -> appShell.showHome` | 同左 |
| 删除 OpenUrl/CopyUrl 的 2 个 `PrefClickEvent` 分支 | 77-78 | `is PrefClickEvent.OpenUrl ->` | `is PrefClickEvent.CopyUrl -> ...` |
| 可选清理 Home 图标字段赋值 | 114,118 | `navIconHome = appShell.navIconHome,` | `navIconHomeSelected = appShell.navIconHomeSelected,` |

#### `MainContract.kt`（D7a + D8）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 `MainDestination.Home` 两处 when 分支 | 29,36 | `MainDestination.Home -> navIconHome` | `MainDestination.Home -> navIconHomeSelected` |
| 删除 `MainEffect.OpenUrl/CopyUrl` | 49-50 | `data class OpenUrl(val url: String) : MainEffect` | `data class CopyUrl(val url: String) : MainEffect` |
| 可选清理 `navIconHome`/`navIconHomeSelected` 字段 | 16,20 | `val navIconHome: String = "",` | `val navIconHomeSelected: String = "",` |

#### `AppIcons.kt`（D7a，必需）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 `MainDestination.Home` 图标分支 | 127-131 | `MainDestination.Home -> if (isMiuix) {` | `if (selected) Icons.Default.Home else Icons.Outlined.Home` 后的 `}` |

#### 主题/导航设置（D7a，必需）

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `MainNavigationSettingsSheet.kt` | 删除 `MainDestination.Home` 可见性分支 | 64 | `MainDestination.Home.route -> settings.showHome` | 同左 |
| `NavIconManageSheet.kt` | 删除 `"home"` 图标项 | 56-61 | `NavIconDestination(`（`"home"`） | `settings.navIconHomeSelected,` 后的 `)` |
| `NavIconManageSheet.kt` | 删除 Home 的 `mainDestination` 映射 | 219 | `MainDestination.Home.route -> MainDestination.Home` | 同左 |
| `ThemeConfigViewModel.kt` | 删除 `setMainDestinationVisible` 的 Home 分支 | 292-297 | `MainDestination.Home.route -> { current ->` | `}` |
| `ThemeConfigViewModel.kt` | 删除 `selectNavigationIcon` 的 Home/HomeSelected 分支 | 328-330,340-342 | `MainDestination.Home.route -> { settings ->` | `settings.copy(navIconHomeSelected = intent.path)` 后的 `}` |
| `ThemeConfigViewModel.kt` | 删除 `navIconPath` 的 Home/HomeSelected 分支 | 373,377 | `MainDestination.Home.route -> navIconHome` | `"${MainDestination.Home.route}:selected" -> navIconHomeSelected` |
| `ThemeConfigViewModel.kt` | 可选：从 `navIconFileUsage` 删除 Home 字段 | 385,389 | `navIconHome,` | `navIconHomeSelected,` |
| `EditThemeSheet.kt` | 可选但推荐：删除“显示首页”开关 | 186-190 | `CompactSwitchSettingItem(`（`R.string.show_home`） | `onCheckedChange = { data = data.copy(showHome = it) }` 后的 `)` |
| `MainNavigationSettingsSheet.kt` | 可选：`customIconCount` 去掉 Home 两项 | 112,116 | `settings.navIconHome,` | `settings.navIconHomeSelected,` |

> `showHome`、`navIconHome*` 仍保留在 `AppShellSettings`/`ThemeExportData`/`ThemePackageSettingsRepository` 中，仅删除 UI 入口；旧主题包/备份继续可读。`ThemeConfig.kt:41` 的 `showHome` getter 也可留到 W5 文档清理，不参与编译阻塞。

#### `MainNavKey.kt` / `MainNavigator.kt`（D9）

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `MainNavKey.kt` | 删除 `MainRouteSettingsTranslation` | 92-93 | `@Serializable`（该块） | `data object MainRouteSettingsTranslation : MainRoute` |
| `MainNavKey.kt` | 删除 `ROUTE_SETTINGS_TRANSLATION` | 184 | `const val ROUTE_SETTINGS_TRANSLATION = "settings/translation"` | 同左 |
| `MainNavigator.kt` | 删除设置组中的翻译 route | 122 | `MainRouteSettingsTranslation,` | 同左 |
| `MainNavigator.kt` | 删除 `resolveStartRoute` 映射 | 334 | `MainRouteConst.ROUTE_SETTINGS_TRANSLATION -> MainRouteSettingsTranslation` | 同左 |

#### `ConfigNavScreen.kt`（D9）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除参数 `onNavigateToTranslation` | 30 | `onNavigateToTranslation: () -> Unit,` | 同左 |
| 删除翻译设置项 | 81-84 | `ClickableSettingItem(`（`R.string.translation_config`） | `onClick = onNavigateToTranslation` 后的 `)` |

---

### 2.2 D8 宿主/入口接入点

#### `MainActivity.kt`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 `WebService` import | 69 | `import io.legado.app.service.WebService` | 同左 |
| 删除 Web 专用本地网络 launcher | 258-270 | `private val webServiceLocalNetworkPermissionLauncher =` | `}`（`toastOnUi(R.string.web_service_local_network_permission_denied)` 后） |
| 删除启动时 WebService 自启块 | 297-303 | `// 智能自启：如果上次是手动开启状态` | `}`（`startWebServiceWithLocalNetworkPermission()` 后） |
| 删除 `startWebServiceWithLocalNetworkPermission()` | 338-348 | `private fun startWebServiceWithLocalNetworkPermission() {` | `}` |
| 删除 `onNewIntent` 的 Web extra 分支 | 368-371 | `if (intent.getBooleanExtra(MainIntent.EXTRA_WEB_SERVICE_LOCAL_NETWORK, false)) {` | `}`（`return` 后） |

保留：`Manifest`/`LocalNetworkAccess`/`localNetworkRequestPermissionLauncher`（书源/图源/WebDAV 的本地网络权限仍需要）；`ACCESS_LOCAL_NETWORK` 权限保留。

#### `MyScreen.kt` + `MyViewModel.kt`

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `MyScreen.kt` | 删除 Web 服务卡片调用 | 142-152 | `SplicedColumnGroup(`（`title = ""`） | `}`（`WebServiceSettingBlock` 后） |
| `MyScreen.kt` | 删除 `WebServiceSettingBlock` 整个函数 | 259-309 | `@Composable`（`fun WebServiceSettingBlock`） | 文件末 `}` |
| `MyScreen.kt` | `MyRouteScreen` 去掉 VM/权限 launcher/effect | 70-107 | `@OptIn(...)` 前的 `@SuppressLint` | `MyScreen(` 前的 `}` |
| `MyScreen.kt` | `MyScreen` 签名去掉 `state/onIntent` | 110-115 | `fun MyScreen(` | `onNavigate: (PrefClickEvent) -> Unit,` |
| `MyViewModel.kt` | 删除 `MyUiState` | 19-23 | `@Stable` | `)` |
| `MyViewModel.kt` | 删除 `MyIntent`/`MyEffect` | 39-49 | `sealed interface MyIntent {` | `sealed interface MyEffect { ... }` 的 `}` |
| `MyViewModel.kt` | 删除 `MyViewModel` 类 | 51-96 | `class MyViewModel(` | 文件末 `}` |
| `MyViewModel.kt` | 删除 Web 专用 `PrefClickEvent` | 26-27,35 | `data class OpenUrl` | `object ToggleWebService` |

- **保留 `MyViewModel.kt` 里的 `PrefClickEvent`**（`MainViewModel`/`MainScreen`/阅读页都用它），只删 Web 相关成员和 `MyViewModel` 类；文件可保留原名，避免大范围 import 迁移。
- `MyRouteScreen` 变成 `MyRouteScreen(onOpenSettings, onNavigate)`，内部直接 `MyScreen(onOpenSettings, onNavigate)`；不需要空 ViewModel。
- `di/appModule.kt:254,477` 删除 `MyViewModel` import/绑定。

#### `OtherConfigScreen.kt` / `OtherConfigViewModel.kt` / `OtherConfigContract.kt` / `OtherConfigRouteScreen.kt`

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `OtherConfigScreen.kt` | 删除 Web 自启开关 | 78-82 | `SwitchSettingItem(`（`R.string.web_service_auto_start`） | `)` |
| `OtherConfigScreen.kt` | 删除 WakeLock 开关 | 142-147 | `SwitchSettingItem(`（`R.string.web_service_wake_lock`） | `)` |
| `OtherConfigScreen.kt` | 删除 Web 端口输入 | 162-166 | `InputSettingItem(`（`R.string.web_port_title`） | `)` |
| `OtherConfigContract.kt` | 删除 state 字段 | 13,22,24 | `val webServiceAutoStart` | `val webPort` |
| `OtherConfigContract.kt` | 删除 intent | 77,86,88 | `data class WebServiceAutoStartChanged` | `data class WebPortChanged` |
| `OtherConfigContract.kt` | 删除 effect | 115 | `data object RestartWebService` | 同左 |
| `OtherConfigViewModel.kt` | 删除 intent 分支 | 67-68,83-84,87-93 | `is OtherConfigIntent.WebServiceAutoStartChanged ->` | `}`（WebPortChanged 分支） |
| `OtherConfigViewModel.kt` | 删除 `toUiState` 三个映射 | 307,316,318 | `webServiceAutoStart = webServiceAutoStart,` | `webPort = webPort,` |
| `OtherConfigRouteScreen.kt` | 删除 WebService import + Restart 分支 | 12,42-47 | `import io.legado.app.service.WebService` | `}`（`WebService.start(context)` 后） |

保留 `OtherSettings.webServiceAutoStart/webServiceWakeLock/webPort` 字段和 `FeatureSettingsRepositories` 映射（旧备份/旧设置兼容）；只是 APK 内不再有 UI/服务消费者。

#### `App.kt` / 常量

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `App.kt` | 删除 `channelIdWeb` import | 25 | `import io.legado.app.constant.AppConst.channelIdWeb` | 同左 |
| `App.kt` | 删除 `webChannel` 创建块 | 323-332 | `val webChannel = NotificationChannel(` | `}` |
| `App.kt` | 从 `createNotificationChannels` 删除 `webChannel` | 340 | `webChannel` | 同左 |
| `AppConst.kt` | 删除 `channelIdWeb` | 23 | `const val channelIdWeb = "channel_web"` | 同左 |
| `EventBus.kt` | 删除 `WEB_SERVICE` | 20 | `const val WEB_SERVICE = "webService"` | 同左 |
| `NotificationId.kt` | 删除 `WebService` | 13 | `const val WebService = 105` | 同左 |
| `PreferKey.kt` | 可选：删除未使用的 `webService` | 85 | `const val webService = "webService"` | 同左 |

#### `MainIntent.kt`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 Web extra 常量 | 38 | `const val EXTRA_WEB_SERVICE_LOCAL_NETWORK = "webServiceLocalNetwork"` | 同左 |
| 删除 `createWebServiceLocalNetworkIntent` | 57-64 | `/**`（`磁贴等无 Activity`） | `}`（`putExtra(EXTRA_WEB_SERVICE_LOCAL_NETWORK, true)` 后） |

#### `AndroidManifest.xml`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 WebService + WebTileService 两个 service | 408-422 | `<service>`（`.service.WebService`） | `</service>`（WebTileService 的 `</service>`） |
| 删除 ReaderProvider（仅当 §3.4 选 A） | 454-459 | `<provider>`（`.api.ReaderProvider`） | `tools:ignore="ExportedContentProvider" />` |
| 改写本地网络权限注释 | 8-9 | `<!-- 自 Android 17 (API 37) 起本地网络保护强制生效` | `否则 Web 服务对局域网内其他设备不可达。targetSdk < 37 时不得声明该权限。 -->` |

`ACCESS_LOCAL_NETWORK` 权限本身**保留**（书源/图源/WebDAV 局域网请求仍需要）。

#### `build.gradle.kts` / `libs.versions.toml`

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `app/build.gradle.kts` | 删除 6 个 ktor 依赖 | 244-249 | `implementation(libs.ktor.server.core)` | `implementation(libs.ktor.server.websockets)` |
| `gradle/libs.versions.toml` | 删除 ktor 版本 + 6 个 library | 41,193-198 | `ktor = "3.6.0"` | `ktor-server-websockets = ...` |
| 根 `build.gradle.kts` | 删除 `WebService.kt` 基线 | 235 | `"io/legado/app/service/WebService.kt" to 2,` | 同左 |
| 根 `build.gradle.kts` | 删除 `BookSearchWebSocket.kt` 基线 | 244 | `"io/legado/app/web/socket/BookSearchWebSocket.kt" to 2,` | 同左 |
| 根 `build.gradle.kts` | 若 D7 删 HomeDashboard，删除该基线 | 229 | `"io/legado/app/data/repository/HomeDashboardRepository.kt" to 3,` | 同左 |

> `app/build.gradle.kts:218-220` 的 media3 三行**不要动**（见 §6）。

---

### 2.3 D7 数据/书源/备份接入点

#### `BookSourceDao.kt` / `BookSourceRepository.kt`

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `BookSourceDao.kt` | 删除 `flowHomepageModules()` 查询 | 84-90 | `@Query(`（`homepageModules is not null`） | `fun flowHomepageModules(): Flow<List<BookSource>>` |
| `BookSourceRepository.kt` | 删除包装方法 | 25-27 | `fun flowHomepageModules(): Flow<List<BookSource>> {` | 对应 `}` |

#### `BookSource.kt`（保护项，不删）

| 动作 | CUR 行号 | 说明 |
|---|---|---:|
| **保留** `homepageModules: String?` | 105 | 书源 JSON/备份兼容；D7 只删消费端 |
| **保留** `equals` 中的 `equal(homepageModules, source.homepageModules)` | 273 | 保持书源去重语义 |

#### `BookSourceEditViewModel.kt`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除书源编辑页的 homepageModules 只读展示 | 365 | `f("homepageModules", R.string.homepage_modules),` | 同左 |

#### `BackupConfig.kt` / `Backup.kt` / `Restore.kt`

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `BackupConfig.kt` | 删除两个 db key 常量 | 89-90 | `private const val dbKeyHomepageModules` | `private const val dbKeyHomepageCustomSets` |
| `BackupConfig.kt` | 从 `dbIgnoreKeys` 删除两项 | 100 | `dbKeyDictRule, dbKeyHomepageModules, dbKeyHomepageCustomSets,` | 同左 |
| `BackupConfig.kt` | 从 `dbIgnoreTitle` 删除两项 | 118-119 | `appCtx.getString(R.string.homepage_modules),` | `appCtx.getString(R.string.homepage_custom_sets),` |
| `Backup.kt` | 从备份文件清单删除两个文件名 | 85-86 | `"homepageModules.json",` | `"homepageCustomSets.json",` |
| `Backup.kt` | 删除两个备份写出块 | 196-205 | `if (BackupConfig.dbIsNotIgnored("homepageModules", true)) {` | `}`（homepageCustomSets 块后） |
| `Restore.kt` | 删除两个 entity import | 23-24 | `import io.legado.app.data.entities.HomepageCustomSet` | `import io.legado.app.data.entities.HomepageModule` |
| `Restore.kt` | 删除两个恢复块 | 258-267 | `if (BackupConfig.dbIsNotIgnored("homepageModules")) {` | `}`（homepageCustomSets 块后） |

> 旧备份里的 `homepageModules.json`/`homepageCustomSets.json` 变成“未识别文件”，`Restore.kt` 不读即可，不会使恢复失败。

#### `appModule.kt`（D7 + D9 + D8 MyViewModel）

| 动作 | CUR 行号 | 首锚点/尾锚点 |
|---|---:|---|
| 删除 HomeDashboard/Homepage repository imports | 57-59 | `import io.legado.app.data.repository.HomeDashboardRepository` … `HomepageSettingsRepository` |
| 删除 Translation repository imports | 83-84 | `TranslationCacheRepositoryImpl`、`TranslationSettingsRepository` |
| 删除 gateways imports | 124-126,140-141 | `HomeDashboardGateway` … `HomepageSettingsGateway`；`TranslationCacheGateway` … `TranslationSettingsGateway` |
| 删除 usecase import | 170 | `import io.legado.app.domain.usecase.HomeDashboardUseCase` |
| 删除 VM imports | 241,252-254 | `TranslationConfigViewModel`；`HomeViewModel`、`HomepageViewModel`、`MyViewModel` |
| 删除 gateway 绑定 | 282,333,380,404,411 | `HomeDashboardGateway`、`TranslationSettingsGateway`、`TranslationCacheGateway`、`HomepageSettingsGateway`、`HomepageModulesGateway` |
| 删除 usecase 绑定 | 361 | `single { HomeDashboardUseCase(get(), Clock.System) }` |
| 删除 `Clock` import | 269 | `import kotlin.time.Clock`（仅此处使用） |
| 删除 `TranslateChapterUseCase` import/绑定 | 183,442 | `import ...TranslateChapterUseCase` / `singleOf(::TranslateChapterUseCase)` |
| 删除 VM 绑定 | 477,480,481,516 | `MyViewModel`、`HomeViewModel`、`HomepageViewModel`、`TranslationConfigViewModel` |

---

### 2.4 D9 阅读页/导出接入点

#### `ReadBookContract.kt`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 `TranslationChapterStatus` import | 20 | `import io.legado.app.model.translation.TranslationChapterStatus` | 同左 |
| 删除 `translationMode/translationStatus` 状态 | 249-251 | `// Translation` | `val translationStatus: ... = TranslationChapterStatus.Idle,` |
| 删除 `ToggleTranslation` intent | 475 | `data object ToggleTranslation : ReadBookIntent` | 同左 |
| 删除 `RetranslateCurrentChapter` intent | 563 | `data object RetranslateCurrentChapter : ReadBookIntent` | 同左 |
| 从按钮池删除 `"translate"` | 399 | `"translate",` | 同左 |

> 没有独立的 `ReadBookEffect.Translation`；翻译只通过 state + `ToggleTranslation/Retranslate` intent 进入。没有 translation sheet。

#### `ReadBookViewModel.kt`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除翻译 import | 65 | `import io.legado.app.model.translation.TranslationChapterStatus` | 同左 |
| 删除构造参数 `translateChapterUseCase` | 105 | `val translateChapterUseCase: io.legado.app.domain.usecase.TranslateChapterUseCase,` | 同左 |
| 删除 intent 分发 | 786,924 | `is ReadBookIntent.ToggleTranslation -> toggleTranslation()` | `is ReadBookIntent.RetranslateCurrentChapter -> retranslateCurrentChapter()` |
| 删除 `syncFromReadBook` 的翻译状态块 | 1708-1718 | `// 原先由 ReadAiDelegate 订阅翻译任务流` | `?: TranslationChapterStatus.Idle` |
| 删除 copy 中两字段 | 1737-1738 | `translationMode = book?.getTranslationMode() ?: false,` | `translationStatus = translationStatus,` |
| 删除 `toggleTranslation()` | 2097-2105 | `private fun toggleTranslation() {` | 对应 `}` |
| 删除 `retranslateCurrentChapter()` | 2107-2116 | `private fun retranslateCurrentChapter() {` | 对应 `}` |

- `aiProfileGateway`（117）当前已无使用（W1 残留）；可顺手删除构造参数，但不阻塞 D9，建议 W5 单独清理，避免与保留的 AI 底座牵动。
- `viewModelScope`/`launch` 仍被大量其它方法使用，不要因删除 `retranslate` 而删 import。

#### `ReadBook.kt`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 3 个 translation import | 50-52 | `import io.legado.app.model.translation.TranslationChapterState` | `import io.legado.app.model.translation.TranslationManager` |
| 删除 `translationObserverJobs` 字段 | 171 | `private val translationObserverJobs = ...` | 同左 |
| 删除 contentKey 中的翻译位 | 386 | contentKey 内 `translationMode` 分量 | 同左 |
| 删除 `clearTextChapter()` 中的清理调用 | 717 | `clearTranslationObserverJobs()` | 同左 |
| 删除 `clearTranslationObserverJobs()` | 748-754 | `private fun clearTranslationObserverJobs() {` | 对应 `}` |
| 简化 `loadContent()` 的内容分支 | 1318-1328 | `val content = if (book.getTranslationMode()) {` | `}`（`BookHelp.getContent` 后） |
| 简化 `loadContentAwait()` 的内容分支 | 1368-1379 | `if (book.getTranslationMode()) {` | `}`（`BookHelp.getContent(...) ?: downloadAwait` 后） |
| 删除 `startTranslationObserver()` | 1468-1505 | `/**`（`Start observing a translation task`） | 对应 `}` |
| 删除 `takeWhile` import | 83 | `import kotlinx.coroutines.flow.takeWhile` | 同左 |

> `contentKey` 删除翻译位会使所有书籍的分页缓存键变化一次（一次性失效），属预期；不影响阅读行为。`MutableStateFlow` 仍被 `_snapshot` 使用，保留。

#### `ReadBookRouteScreen.kt` / `ReadMenuActionDefs.kt` / 三个 MenuBar

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `ReadBookRouteScreen.kt` | 删除 translation import | 85 | `import io.legado.app.model.translation.TranslationChapterStatus` | 同左 |
| `ReadBookRouteScreen.kt` | 删除 Thinking 浮层 | 844-850 | `AnimatedVisibility(`（`visible = state.translationStatus`） | 对应 `}` |
| `ReadMenuActionDefs.kt` | 删除 translate 按钮元数据 | 191-195 | `ReadMenuButtonInfo(`（`"translate"`） | 对应 `),` |
| `ReadMenuActionDefs.kt` | 删除 translate 动作 | 356-358 | `def("translate", isActive = state.translationMode) {` | 对应 `}` |
| `ReadBookMenuBarBottom.kt` | 从 remember key 删除 `state.translationMode` | 223 | `state.translationMode,` | 同左 |
| `ReadBookMenuBarFloating.kt` | 同上 | 78 | `state.translationMode,` | 同左 |
| `ReadBookMenuBarTitle.kt` | 同上 | 831 | `state.translationMode,` | 同左 |

#### `ExportBookService.kt`

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---:|---|
| 删除 TranslationCache/Settings import | 19,23 | `import ...TranslationCacheGateway` | `import ...TranslationSettingsGateway` |
| 删除 `ExportConfig.targetLanguage` | 101 | `val targetLanguage: String,` | 同左 |
| 删除构造时的目标语言赋值 | 141 | `targetLanguage = translationSettingsGateway.currentSettings.targetLanguage,` | 同左 |
| 删除 `ContentSource.Translation`（建议 enum 只留 `Original` 作为最小改动垫片） | 109-112 | `private enum class ContentSource {` | 对应 `}` |
| 删除翻译 repository/settings 字段 | 114,116 | `private val translationCacheRepository` | `private val translationSettingsGateway` |
| 删除 `currentTargetLanguage` | 124 | `private var currentTargetLanguage = "zh"` | 同左 |
| 删除赋值 | 225 | `currentTargetLanguage = exportConfig.targetLanguage` | 同左 |
| 删除“同时导出翻译”两段 | 241-244,253-257 | `// Also export translation if cache exists` | `}`（对应块） |
| `exportTxt` 简化 filename 分支 | 300-307 | `val targetLanguage = currentTargetLanguage` | `}`（`when` 后） |
| `exportTxt` 图片判断简化 | 316 | `if (source == ContentSource.Original) {` | 同左（可保留恒真条件，W5 再清） |
| 删除 `getTranslatedFileName` + `hasAnyTranslatedChapter` | 339-360 | `/**`（`Get translated filename`） | `}`（`hasAnyTranslatedChapter` 后） |
| `getExportData` 简化 content 分支 | 410-414 | `val targetLanguage = currentTargetLanguage` | `}`（`when` 后） |
| `getExportData` 图片判断简化 | 426 | `if (currentExportSettings.exportPictureFile && source == ContentSource.Original) {` | 同左 |
| 删除未使用的 `exportEpub(path, book, source)` 重载 | 453-458 | `private suspend fun exportEpub(path: String, book: Book, source: ContentSource) {` | 对应 `}` |
| `exportEpub(fileDoc...)` 简化 filename 分支 | 461-468 | `val targetLanguage = currentTargetLanguage` | `}`（`when` 后） |
| `setEpubContent` 删除 targetLanguage/translation 分支 | 638-663 | `source: ContentSource = ContentSource.Original` | `}`（`fixPic` 分支后） |

> 最小编译策略：`ContentSource` 保留单值 `Original`、`source` 参数暂留，删除所有 `ContentSource.Translation` 分支和翻译专用成员；W5 再彻底移除单值枚举/参数，避免本波大范围改签名。

#### `Book.kt`

| 动作 | CUR 行号 | 说明 |
|---|---:|---|
| 删除 `setTranslationMode/getTranslationMode` | 344-350 | 删除后无调用方；`BookConfig.translationMode`（494）**保留**，旧书配置/备份继续可读 |
| 保留 `BookConfig.translationMode` 字段 | 494 | 不删，避免旧 book config JSON 丢字段；W4 再评估 |

#### `FeatureSettingsRepositories.kt` / `PreferKey.kt` / 测试

| 文件 | 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---|---|---:|---|
| `FeatureSettingsRepositories.kt` | 删除 TranslationSettingsRepository 类 | 117-132 | `class TranslationSettingsRepository : TranslationSettingsGateway {` | 对应 `}` |
| `FeatureSettingsRepositories.kt` | 删除两个 mapping 函数 | 228-250 | `internal fun Preferences.toTranslationSettings()` | `)`（`TranslationSettings.toPrefMap` 后） |
| `FeatureSettingsRepositories.kt` | 删除 3 个 import | 13,14,22 | `TranslationSettingsGateway` | `TranslationSettings` |
| `PreferKey.kt` | 删除 llm* 键 | 457-468 | `// Translation` | `const val llmPrompt = "llmPrompt"` |
| `Phase2SettingsMappingTest.kt` | 删除翻译设置往返测试 | 5,11,17-32 | `import ...TranslationConstants` | 第一个 `@Test` 的 `}` |
| `PartialTranslationAssemblerTest.kt` | 整文件删除 | 35 | — | — |
| `TranslationResultPreviewParserTest.kt` | 整文件删除 | 48 | — | — |
| `TranslationDictionaryPolicyTest.kt` | **保留**（字典域） | — | — | — |

#### D9 字符串（4 语言 × 34 条）

删除 `values/strings.xml`、`values-zh-rCN`、`values-zh-rHK`、`values-zh-rTW` 中匹配以下名称的条目（当前各 34 条）：

```
translation_config, llm_translate_enabled, translation_provider, llm_provider,
llm_base_url, llm_api_key, llm_model, translation_options, llm_target_language,
llm_max_chars_per_chunk, llm_concurrent_chunks, llm_retry_count, llm_temperature,
llm_temperature_description, translation_prompt, llm_prompt, translate_chapter,
show_original, retranslate_chapter, retranslate_confirm, translation_in_progress,
translation_failed, no_translation_cache, delete_translation_cache,
translation_cache_size, clear_translation_cache, translation_cache, translate,
return_to_original, translation_progress, translation_started,
translation_app_ai_provider, translation_app_ai_provider_summary,
translation_model_thinking
```

以上为 34 条 × 4 语言。`openai_config` 当前 0 个 Java 引用，但属 AI 历史字符串，留给 W5-6 AI 字符串批次，不要混进 D9 的翻译删除。

---

## 3. D8 归档方案

### 3.1 目录布局（保留原仓库相对路径，恢复时可直接 `cp -r`）

```text
attic/webservice/
├── README.md
└── app/src/main/java/io/legado/app/
    ├── web/
    │   ├── KtorServer.kt
    │   ├── ReadMe.md
    │   ├── socket/BookSearchWebSocket.kt
    │   ├── socket/BookSourceDebugWebSocket.kt
    │   └── utils/AssetsWeb.kt
    ├── api/
    │   ├── ReturnData.kt
    │   ├── controller/BookController.kt
    │   ├── controller/BookSourceController.kt
    │   ├── controller/ReplaceRuleController.kt
    │   └── ReaderProvider.kt        # 仅 §3.4 选 A 时
    └── service/
        ├── WebService.kt
        └── WebTileService.kt
```

`attic/` 不在 Gradle source set 内，不参与编译；不要给 `attic` 加 `build.gradle.kts`。

### 3.2 `attic/webservice/README.md` 必须写清

1. 归档来源 commit（执行时的 `git rev-parse HEAD`）与日期。
2. 恢复顺序：
   - `git mv attic/webservice/app/src/main/java/io/legado/app/web app/src/main/java/io/legado/app/`
   - `git mv attic/webservice/app/src/main/java/io/legado/app/api/{ReturnData.kt,controller} app/src/main/java/io/legado/app/api/`
   - `git mv attic/webservice/app/src/main/java/io/legado/app/service/{WebService.kt,WebTileService.kt} app/src/main/java/io/legado/app/service/`
   - 恢复 `AndroidManifest` 两个 service + `ReaderProvider`（若归档）
   - 恢复 `MainActivity` 自启/权限 launcher、`MyScreen` 卡片、`OtherConfig` 三项、`App.kt` web channel、`MainIntent` extra
   - 恢复 `app/build.gradle.kts` 6 个 ktor 依赖与 `libs.versions.toml` catalog
   - `pnpm --dir modules/web build`（需 `GITHUB_ENV` 或手动把 `modules/web/dist` 拷到 `app/src/main/assets/web`）
3. 说明 `BookSourceDebugWebSocket` 只服务 Web 端调试；应用内调试 `model/Debug.kt` 不依赖它。
4. 说明 `modules/web` 一直保留在仓库，不需要从 attic 恢复。

### 3.3 删除的入口/依赖/资源（汇总）

- 入口：`MainActivity` Web 自启/launcher；`MyScreen` Web 服务卡片；`OtherConfig` 自启/WakeLock/端口；`App.kt` web notification channel；`MainIntent` Web extra；`AndroidManifest` 两个 service；`WebTileService` QS 磁贴。
- 常量：`AppConst.channelIdWeb`、`NotificationId.WebService`、`EventBus.WEB_SERVICE`。
- 依赖：`app/build.gradle.kts:244-249` + `libs.versions.toml:41,193-198`。
- 资源：`assets/web/**`、`ic_web_service_noti`、`http_ip`、8 条 web strings、4 个 legacy pref XML 块；`ic_cfg_web.xml` 仅被 `pref_main.xml` 的 Web 项引用，可一并删除。
- 文档：`app/src/main/java/io/legado/app/service/README.md:8` 的 “WebService web服务” 行随归档删除（或留给 W5 统一）。
- 保留：`ACCESS_LOCAL_NETWORK` 权限（书源/WebDAV 本地网络）、`LocalNetworkAccess.kt`、`NetworkChangedListener`、`modules/web/**`、`api/ShortCuts.kt`、`model/Debug.kt`、`ui/book/source/debug/**`。

### 3.4 P0 决策：`ReaderProvider` 的归属

**事实**：`ReaderProvider.kt:13-14` import `BookController`/`BookSourceController`；`:60-119` 调用它们并 `ReturnData`。D8 决策图把它列入“保留”，但这个保留与“`api/controller/**` 归档”互斥。

- **方案 A（推荐，纯减法）**：`ReaderProvider.kt` 一起移入 attic；删 `AndroidManifest.xml:454-459` 的 provider；把 `ShortCuts.buildShortCuts(context)` 从 `ReaderProvider.onCreate()`（`:48`）移到 `App.onCreate()`（`startKoin` 之后，约 `App.kt:117`）。否则动态快捷方式（书架/继续阅读）不再创建。
  - 另需处理 `ShortCuts.kt:47-56` 的 `buildReadAloudShortCutInfo`：W2 已删朗读，该快捷方式应一并从 `buildShortCuts` 列表移除，避免留下无效入口（可作为 W3-2 的附带清理）。
- **方案 B（保 ContentProvider API）**：`ReaderProvider` 保留，则 `api/controller/BookController.kt`（328）、`BookSourceController.kt`（80）、`api/ReturnData.kt`（29）不能归档（合计 437 行）；`ReaderProvider` 自身 145 行也保留。`ReplaceRuleController` 只被已归档的 KtorServer 引用，仍可单独归档。D8 归档缩水约 437 行，且 controller 与 :app 内部 DAO 的耦合面继续留在 APK。
- **建议**：除非确认有外部 App 依赖 `readerProvider` authority，选 A。此决定必须在 W3-2 开工前落到 `DECISION-MAP.md`，不要在执行时临时二选一。

---

## 4. D7a 3 tab 收敛细节

### 4.1 现状

| 事实 | 证据 |
|---|---|
| 当前 4 tab | `MainDestination.kt:33`：`persistentListOf(Home, Bookshelf, Explore, My)` |
| `MainScreen` 4 个 pager 分支 | `MainScreen.kt:502-585` |
| `MainViewModel` 过滤 Home/Explore 可见性 | `MainViewModel.kt:99-105` |
| Rss 已删 | `MainDestination` 已无 Rss；`MainDestination` 只剩 4 项 |

### 4.2 目标

`Bookshelf / Explore / My` 三项；Home 专属 dashboard 删除，Bookshelf 作为唯一书架入口。旧设置兼容：

- `mainNavigationOrder` 中的 `"home"` 被 `ordered()` 的 `mapNotNull(byRoute::get)` 丢弃。
- `defaultHomePage = "home"` 被 `MainScreen.kt:198-204` 的 `indexOfFirst` 判为 -1，回退 index 0（Bookshelf）。
- `showHome`、`navIconHome*` 字段保留在设置/主题包结构里，不再有 UI；不要为了“清干净”删 `AppShellSettings`/`ThemeExportData` 字段，否则旧主题包反序列化会丢配置（D11 兼容优先）。
- **不要删 `MainRouteHome` / `ROUTE_MAIN` / `MainActivity.createHomeIntent`**：它们是“主界面宿主 route”，不是 Home tab；快捷方式、欢迎页、`ShortCuts` 都用它启动 MainActivity。

### 4.3 执行顺序（避免中间态不可编译）

1. 先做 `MainDestination` + 所有 `when` 分支（MainViewModel/MainContract/MainScreen/AppIcons/ThemeConfig/NavIcon/MainNavigationSettings）。
2. 再删 `MainScreen` Home 分支与 `MainNavGraph` 两个实参；此时 `HomeRouteScreen` 已无引用。
3. 最后删 `ui/main/home/**`、`HomeDashboard*`、`homepage/**`（Banner 已迁走）。
4. 单独编译一次再进入 D7 homepage 删除，避免把“导航 3 tab”与“首页模块删除”混在一个不可定位的失败里。

---

## 5. D8 与 D7/D9 的交叉点（执行顺序）

1. **D7a 先做**（只动导航，不删 homepage 包）：保证 `MainScreen` 3 分支可编译，Banner 迁移也在这步完成。
2. **D7 homepage 再删**：删 18 个 homepage 文件 + `ui/main/home/**` + `HomeDashboard*` + repository/gateway；`AppDatabase` 不动。
3. **D8 归档**：先 `git mv` 源码到 attic，再删 manifest/入口/依赖/资源；最后 `ReaderProvider` 裁决与 ShortCuts 迁移。
4. **D9 翻译**：独立于 D7/D8，可与 D7 homepage 并行；只共享 `appModule`/`MainNavGraph`/`MainScreen` 热点，由 Integrator 串行收口。
5. **W3-5**：跑全量验证 + 架构门基线 + APK assets 反证。

---

## 6. 保护项与风险

### 6.1 必须保留

| 项 | 位置 | 原因 |
|---|---|---|
| `BookSource.homepageModules` 字段 | `data/entities/BookSource.kt:105` | 书源 JSON/备份兼容；D7 只删消费端 |
| `BookSource.equals` 的 homepageModules 比较 | `BookSource.kt:273` | 书源去重语义 |
| `BannerModule` / `BookBannerRow` | 迁到 `ui/widget/components/book/` | `BookInfoScreen` 相关书籍横幅真实消费者 |
| `TranslationModels.kt`、`TranslationDictionaryPolicy.kt` | domain/model | 字典域 `DictionaryRepositoryImpl` 使用 |
| `TranslationConstants.kt` | domain/model | 保留的 AI 底座 `AiModels/AiProfileRepository` 使用 |
| media3 三依赖 + `AnalyzeUrl.getMediaItem()` | `app/build.gradle.kts:218-220`、`AnalyzeUrl.kt:6,821-828` | 书源 JS API 兼容，W2-S6 明确保留 |
| `model/Debug.kt`、`ui/book/source/debug/**` | 应用内书源调试 | 不依赖 `BookSourceDebugWebSocket` |
| `modules/web/**` | 独立 Vue 工程 | D8 只删 APK 产物，源码原位保留 |
| `ACCESS_LOCAL_NETWORK`、`LocalNetworkAccess.kt`、`LocalNetworkBlockedNotifier.kt` | 权限/网络层 | 书源/图源/WebDAV 局域网请求仍需要 |
| `api/ShortCuts.kt` | 桌面快捷方式 | 若 ReaderProvider 归档，需把 `buildShortCuts` 调用移到 `App.onCreate` |

### 6.2 主要风险

1. **Room schema 风险（最高）**：D7 entity/DAO/AppDatabase 不能在 W3 删；Room `version=131` 未变时改 entity 列表会启动崩溃。W4-1 必须同批 `version+1` + migration + 删除声明。
2. **ReaderProvider 双输风险**：不裁决就开工，会出现“controller 已归档、ReaderProvider 编译失败”或“controller 被迫留在 APK、D8 名存实亡”。见 §3.4。
3. **ShortCuts 静默失效**：`ReaderProvider.onCreate()` 是 `buildShortCuts` 唯一调用点；归档 provider 不补 `App.onCreate` 调用，桌面快捷方式消失但无编译错误，属运行时静默回归。
4. **Home dashboard 产品损失**：删 `ui/main/home/**` 会丢失阅读统计、最近阅读、WebDAV 备份卡。若产品要求保留，应另做“把 `HomeDashboardContent` 嵌入 BookshelfScreen 顶部”的设计；不能靠保留 Home tab 绕过 3 tab 裁决。默认真减法按删除执行。
5. **D8 assets 回潮**：`modules/web/scripts/sync.js` 仍指向 `app/src/main/assets/web`。本地 `pnpm build` 无 `GITHUB_ENV` 时不会复制；CI/GitHub Actions 会重建。W3 验证不要带 `GITHUB_ENV` 跑 `pnpm build`，否则会把刚删的 assets 又写回来。
6. **W2 Manifest 残留阻塞 assemble**：`AndroidManifest.xml:424-430` 仍声明 W2 已删的 `TTSReadAloudService/HttpReadAloudService/AudioPlayService`，W3-5 `assembleAppDebug` 会先被这些独立问题挡住；请 W2 负责人先清理，别把失败误判为 D8。
7. **字符串/XML 悬空**：D8 删 8 条 web strings 前必须同步删 `pref_main.xml/pref_config_other.xml` 的 Web 项，否则资源链接失败；D9 删 34 条翻译 strings 前先确认 `openai_config` 的 AI 归属（建议留给 W5-6）。
8. **`contentKey` 一次性失效**：`ReadBook.kt:386` 删除 `translationMode` 会让所有书籍分页缓存键变化一次；不是行为回归，但手测时应预期首次翻页重新排版。
9. **旧 `defaultHomePage`/`mainNavigationOrder`**：已确认会静默回退到 Bookshelf，无需 DB/偏好迁移；如果不接受静默，W4 可加迁移，但不要在 D7a 里做。
10. **翻译磁盘缓存残留**：删除翻译功能不会删除用户已有 `*.<lang>.nb` / `.chunks.jsonl` 缓存文件；W4 数据清理可加一次性删除，否则只是占空间，不影响运行。
11. **无 Gradle 验证**：本报告未运行构建；D8/D9 行号与 import 删除必须在执行后由 `:app:compileAppDebugKotlin` 校验，不能把本报告当编译证明。

---

## 7. 验证命令与反证

### 7.1 每步最小验证

```bash
cd /home/zx/workspace/legado-with-MD3
git diff --check
./gradlew :app:compileAppDebugKotlin
```

### 7.2 W3-5 出口

```bash
./gradlew testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug \
  --continue --no-configuration-cache
```

> 环境：JDK 21；`ANDROID_HOME=/home/handx-aijiu/android-sdk`（仓库无 `local.properties`）。不要跑 `pnpm --dir modules/web build`（见风险 5）。

### 7.3 反证 grep（执行后应满足）

```bash
# D7: homepage 模块消费端清零（允许 BookSource 字段/equals + W4 保留的 entity/DAO 声明）
grep -RInE 'HomepageViewModel|HomepageModuleFeed|HomepageModuleManageSheet|HomepageModulesGateway|HomepageSettingsGateway|HomepageModuleType|ModuleDef\b|CustomSetItem' \
  app/src/main/java app/src/test/java || true

# D7a: 无 MainDestination.Home
grep -RIn 'MainDestination.Home' app/src/main/java app/src/test/java || true

# D8: 编译路径无 Web 服务
grep -RInE 'WebService|WebTileService|KtorServer|io\.ktor|BookSearchWebSocket|BookSourceDebugWebSocket' \
  app/src/main/java app/src/main/AndroidManifest.xml app/build.gradle.kts || true

# D8: APK 无前端产物（assemble 后）
unzip -l app/build/outputs/apk/app/debug/*.apk 2>/dev/null | grep 'assets/web/' || echo 'OK: no assets/web'

# D9: 翻译消费端清零（允许 TranslationModels/DictionaryPolicy/Constants 等保留文件）
grep -RInE 'TranslationManager|TranslateChapterUseCase|TranslationConfig|TranslationCacheGateway|translationStatus|translationMode' \
  app/src/main/java app/src/test/java || true

# D9: 字典保留项仍在
test -f app/src/main/java/io/legado/app/data/repository/DictionaryRepositoryImpl.kt && echo OK
```

### 7.4 预期构建基线调整

- 根 `build.gradle.kts:229`：若删 `HomeDashboardRepository.kt`，删该行。
- 根 `build.gradle.kts:235`：`WebService.kt` 删除，删该行。
- 根 `build.gradle.kts:244`：`BookSearchWebSocket.kt` 删除，删该行。
- `OtherConfigViewModel.kt` / `Restore.kt` / `ReadBookViewModel.kt` 等其它计数若因删除而变化，由 `verifyConfigArchitecture` 报“减少了就下调”，按实际输出同步，不要预调未知数字。
