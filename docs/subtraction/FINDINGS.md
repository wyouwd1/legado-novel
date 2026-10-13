# 侦察事实（Findings）

> 只记录**实际验证过**的事实与可复现命令；推断一律标注「推断」。侦察方式：Agent Teams 只读 scout
> （RSS 组、漫画组）+ 主控直接测绘（AI / 知识库 / 首页模块 / Web / 依赖 / 护栏）。
> 侦察时间：2026-10-14 ｜ 基线提交：`070ca1a9b`

---

## 1. 顶层结构事实

| 项 | 事实 | 命令 |
|---|---|---|
| Gradle 模块 | `:app`、`:modules:book`（umd/epub 解析）、`:modules:rhino`、`:baselineprofile`；`modules/web` 为独立 Vue3 工程（不参与 Gradle） | `cat settings.gradle` |
| 包结构 | `app/src/main/java/io/legado/app/{api,base,constant,core,data,di,domain,exception,feature,help,lib,model,receiver,service,ui,utils,web}` | `ls` |
| Compose 迁移区 | `feature/{explore,onboarding,readaloud,reader}`（reader 67 文件 / 12,573 行） | `find/wc` |
| 遗留 View 区 | `ui/**`（含 `ui/widget` 207 文件 / 34,195 行，多为通用组件） | `find/wc` |
| 一级导航 | `MainDestination`：`Home / Bookshelf / Explore / Rss / My`（顺序可由设置调整） | `ui/main/MainDestination.kt` |
| 书籍类型 | `BookType` 位标志：`video/text/updateError/audio/image/webFile/local/archive/notShelf` | `constant/BookType.kt` |
| 书源类型 | `BookSourceType`：`default(0 文本)/audio(1)/image(2)/file(3)` | `constant/BookSourceType.kt` |
| 工作区状态 | `main` 干净，仅 `app/src/main/assets/defaultData/bookSources.json` 已修改、`docs/book-source-verification/` 未跟踪 | `git status --short` |

---

## 2. 各域规模测绘（主源行数，不含资源）

| 域 | 文件数 | 行数 | 关键路径 |
|---|---|---|---|
| RSS（含 dao/entity/repo/socket/api） | 52 | 8,649 | `ui/rss` 27/5,896、`ui/main/rss` 3/686、entity 5/440、dao 4/270、repo 5/254 |
| 漫画 | 37（+20 测试） | 9,289（+3,104 测试） | `ui/book/manga` 14/6,745、`help/coil/Manga*`、`domain/model/manga` |
| 朗读 TTS 簇 | 44+ | 15,147（`ui/book/readaloud`） | `service/*ReadAloud*`、`help/TTS.kt`、`help/readaloud/**` |
| 音频书/播放器 | ~10 | ~1,900 | `ui/book/audio` 4/1,641、`AudioPlayService`、`core/ui/player` |
| AI | 34 | 6,030 | `ui/ai` 10/3,170、`data/repository/ai` 1,507、`bookshelf/autoGroup` 1,353 |
| 知识库/角色 | 12 | ~4,700 | `ui/book/knowledge` 4,568 |
| 首页自定义模块 | 19 | 4,983 | `ui/main/homepage`、`HomepageModule(Set)` entity |
| Web 服务/API | 12 | ~1,055 | `service/WebService.kt` 236、`api/**` 819 |
| 字典 | 8 | 1,329 | `ui/dict`、`DictRule` |
| 翻译 | 3 | 174 | `ui/config/translation`、`TranslationCache` |
| 替换净化（保留） | 7 | 1,911 | `ui/replace` |
| 高亮/标签规则（保留） | 7 | 1,297 | `ui/highlightTagRule`、`ui/tagGroupRule` |
| 书籍关联/导入（保留，含 RSS 部分） | 24 | 3,357 | `ui/association`（`ImportRssSource*` 471 行随 RSS 删） |

---

## 3. RSS 组侦察结论（scout sa-a9c44）

- **切除是单向的**：删 RSS 域后，需要修改的目录外文件约 25 个（净改动估 300–500 行，多为删 import/删分支/删注册）。
- **必须先搬家的共享类**（否则第一步就编译失败）：
  - `ui/rss/read/RssJsExtensions.kt` ← 被 `help/webView/WebJsExtensions.kt:22`（含 `appDb.rssSourceDao` 引用）、`ui/login/SourceLoginJsExtensions.kt`、`ui/rss/read/VisibleWebView.kt` 继承/使用
  - `ui/rss/read/VisibleWebView.kt` ← 被 `VisibleWebViewCompose` 等 3 处 import
- **热点改动点**：`ui/main/MainNavGraph.kt:163-173,477-518,617-646,1206-1281`（36 处）、`MainNavigator.kt`（18 处）、`di/appModule.kt:82-87,322-333,525-530,591-601,822`、`di/appDatabaseModule.kt`、`data/AppDatabase.kt:138-161,304-315` + `DatabaseMigrations.kt:11-27`、`help/source/{SourceHelp,BaseSourceExtensions}.kt`、`model/{Debug,analyzeRule/AnalyzeRule}.kt`、`help/{DefaultData,RuleBigDataHelp}.kt`、`help/config/LocalConfig.kt`、`help/storage/{Backup,Restore,BackupConfig}.kt`、`theme*` 5 文件、`utils/ImageUtils.kt`、`App.kt:350`（Rhino 注册 `RssSource`）、`web/KtorServer.kt`、`api/ReaderProvider.kt`
- **需要人工决策**：Room 表策略、主题包/备份字段兼容、`RuleSubScreen` 归属、`modules/web` 的 `/saveRssSource` 端点、`rssArticle` JS 绑定是否保留、`RssJsExtensions`/`VisibleWebView` 的新包名
- **最大编译断裂点顺序**：`WebJsExtensions`/`SourceLoginJsExtensions` 失父类 → `VisibleWebViewCompose` 三处 import → `di` 注册 → `AppDatabase`（运行时 schema 校验） → `MainNavGraph`/`MainNavigator` → `SourceHelp:48,56,112` → `AnalyzeRule:66,845`/`Debug.kt`
- **资源面**：5 drawable、`assets/defaultData/rssSources.json`、`assets/web/help/md/SourceMRssHelp.md`、`arrays.xml` 的 `rss_type`、4 语言各 12 条字符串
- 未验证：未执行任何 Gradle 编译，以上断裂点由静态引用推断

---

## 4. 漫画组侦察结论（scout sa-c26b4）

- **零反向依赖**：漫画目录外没有代码 `import io.legado.app.ui.book.manga.*` 或 `data/repository/manga.*`，仅 DI 与路由引用 → 切除单向、干净。
- **可直删**：`ui/book/manga/**`、`ui/config/readMangaConfig/`、`data/repository/manga/**`、`data/repository/MangaSettingsRepository.kt`、`data/model/MangaFooterConfig.kt`、`domain/model/manga/**`、`domain/model/settings/MangaSettings.kt`、`domain/gateway/Manga*`、`domain/reader/MangaRegionDecoder.kt`、`help/coil/Manga*.kt`（37 文件 / 9,289 行 + 20 测试 / 3,104 行）
- **必须修改**（~25 文件）：`MainNavGraph/MainNavKey/MainNavigator/MainIntent/MainActivity/MainBookPageNavigation`、`ContextExtensions:66-107`、`FragmentExtensions:24-71`、`BookInfo{RouteScreen:85,147,Contract:56,ViewModel:2127}`、`OtherSettings/OtherConfig*`、`FeatureSettingsRepositories:517,546`、`SearchScreen:747-761`、`ImportBook{,ViewModel}`、`DownloadCacheConfig*`、`PreferKey`（**43 键**）、`BackupConfig`、`EventBus:33`、`appModule:407,432-443,567`（`okHttpClientManga`）、`HttpHelper:149-155,174,192`、Coil 三件套 + Glide 三件套、`BookShelfItem/BookItem`、`BookDao/BookGroup/AppDatabase`、`Book.kt:487-490`、strings ×4 + `pref_config_other.xml`
- **共享 Coil 管线（关键风险）**：`help/coil/CoverInterceptor.kt:27-35,84-122`、`CoverFetcher.kt:68-102,251-259`、`CoverExtras.kt:25-35` 同时服务**小说封面**，删 `help/coil/Manga*.kt` 前必须先清理这些点
- **人工决策**：本地图片目录导入与旧 View 漫画模式、历史数据（`books.type & 64`、`book_groups` -7 行、备份 JSON 漫画偏好）、`BookSourceType.image` 是否保留、`show_manga_ui` 死开关
- 未验证：未执行构建

---

## 5. 主控测绘结论（AI / 知识库 / 首页 / Web / 护栏）

- **AI 依赖链**：`ReadBookScreen.kt:48`（`DictSheet`）、`read/sheet/AiTextCleanSheet.kt:21-23`、`read/sheet/AiCastDialogSheet.kt:43-54`（配音 + `ui/ai/chat` 组件）、`bookshelf/autoGroup/*` → AI 与**阅读页、配音、书架**三处纠缠，非叶节点。
- **首页模块 ↔ 书源耦合（关键）**：`data/entities/BookSource.kt:105 homepageModules: String?`、`:273` 相等性比较、`data/dao/BookSourceDao.kt:87-90 flowHomepageModules()`、`data/repository/BookSourceRepository.kt:25-26` → 删模块系统会牵动**书源实体与 DAO**；`help/storage/{Backup,Restore,BackupConfig}.kt` 也引用。
- **Web 服务耦合**：`MainActivity.kt:75,339,373-418`（启动/前台服务/局域网权限）、`ui/config/otherConfig/*`（自动启动、WakeLock、重启）、`web/socket/BookSearchWebSocket.kt`（架构门基线 2）、`RssSourceDebugWebSocket`（随 RSS 删）。Ktor 依赖系列：`ktor.server.{core,cio,content-negotiation,cors,websockets}` + `ktor-serialization-gson`。
- **MyScreen 入口清单**（减法后的"我的"页）：规则段＝书源管理 / 替换净化 / TXT 目录规则 / 字典规则 / 高亮标签；其他段＝**AI 对话** / 设置 / 书签 / 阅读记录 / 缓存管理 / 文件管理 / 关于 / 退出；另含 Web 服务卡片（`MyScreen.kt:89,128-312`）。
- **构建护栏（硬事实）**：根 `build.gradle.kts:216-302` 注册 `VerifyConfigArchitectureTask`，三张基线表（`legacyPreferenceCallBaseline` 23 项、`legacyDaoInjectionBaseline` 2 项、`legacyUiDaoAccessBaseline` 11 项）；`:281` 使所有 `assemble*`/`compile*` 依赖它。受影响条目示例：`ContextExtensions.kt`=12、`MainNavGraph.kt`=2、`SearchViewModel.kt`=3、`ImportRssSourceDialog.kt`=1、`RssJsExtensions.kt`=8、`RssArticlesCompose.kt`=1、`WebService.kt`=2、`CloudTtsViewModel.kt`=13、`OtherConfigViewModel.kt`=1、`App.kt`=3、`LocalConfig.kt`=3、`Restore.kt`=2。
- **可移除依赖（候选）**：`libs.media3.exoplayer`、`libs.media3.datasource.okhttp`（`app/build.gradle.kts:219-220`，唯一消费者是音频/播放器域，需 D4a 落地后确认）；`libs.media.media` 是否仍被 TTS 域使用需在 W2 复核。

---

## 6. TTS / 朗读 / 配音 / 音频书域侦察结论（bg_delegate，只读，2026-10-14）

**规模实测要点**：服务层 `BaseReadAloudService.kt` 2,064、`HttpReadAloudService.kt` 1,941、`TTSReadAloudService.kt` 781、`AudioPlayService.kt` 756；`ui/book/readaloud/**` 44 主文件（`cloudtts/CloudTtsScreen.kt` 1,338 + `CloudTtsViewModel.kt` 1,319、`player/ReadAloudPlayerScreen.kt` 717）；`ui/book/read/sheet/` 内 `AiCastDialogSheet.kt` 1,142、`ReadAloudConfigContent.kt` 618、`ReaderAudioDownloadSheet.kt` 477、`BgmSceneTableSheet.kt` 356；`help/readaloud/**`（含 `cast/` 25 文件，`AiCastAssignUseCase.kt` 692、`CastSpeechOverlay.kt` 459、`CastAssignmentStore.kt` 419、`BookCastStore.kt` 372）；`help/readaloud/playback/` 18 文件（云 TTS provider 群）；`domain/model/readaloud/` 13 文件；13 个 DAO、6 个 repository、6 个 gateway、2 个 usecase；**测试 ≥30 文件**。

**最大发现（决定成本）：`cast`/`bgm` 不是旁挂，而是阅读排版主路径的一部分**
- `feature/reader/core/layout/ReaderPaginator.kt`（1,147）`:80,112,132` 持有 `ReaderCastOptions` 字段与 `Disabled` 默认值
- 传导链：`ReaderPaginator` → `legacy/LegacyReaderChapterPaginator.kt:129,169-248`（cast/bgm 胶囊高度参与分页）→ `core/source/ReaderChapterSource.kt:150-228` → `core/layout/ReaderChapterBlockMeasurer.kt:178,330,616` → `ReaderCanvasSurface.kt:3169-3356`（3,713 行）
- `reader/ReaderChapterInput.kt:24`：cast 渲染签名**参与分页缓存键**
- `core/model/ReaderPage.kt:299,316` 定义 `ReaderElement.RoleCast` / `BgmScene`

**必须保留（只剥离引用，禁止整文件删）**
| 文件 | 原因 |
|---|---|
| `feature/reader/core/cast/CastMarkers.kt`(228) | 引号跟踪/`injectParagraph`/`strip` 被**文本**链路使用（`ReaderChapterSource.kt:150-228`、`LegacyReaderStyleRangeMapper.kt:89-113`、`CastAssignmentStore.kt:39`）→ 只能收敛为纯文本标记 |
| `ReaderPaginator` / `ReaderChapterBlockMeasurer` / `LegacyReaderChapterPaginator` / `ReaderChapterSource` / `ReaderCanvasSurface` / `LegacyReaderStyleRangeMapper` | 文本分页与绘制主路径 |
| `model/ReadBook.kt` / `model/CacheBookModel.kt` 的 `preserveReadAloudPosition`（`CacheBookModel.kt:779-964`、`ReadBook.kt:1246-1283,1363-1473`） | 本质是"重载不跳位"的阅读进度语义；删朗读后须保语义、只去 `BaseReadAloudService.isRun` 依赖 |
| `core/ui/player/PlayerMorphHost.kt`、`core/ui/morph/BookCoverMorphAnchors.kt` | morph 锚点是通用转场（书架封面共用）→ 只删 `AudioPlayerMorphOverlay`/`ReadAloudPlayerMorphHost` 分支 |
| `ReadBookContract/Screen/FloatingActionBar/MenuBar*` | 文件保留，只删朗读 Intent/Effect/Sheet 成员 |
| `AutoReadSheet.kt`、`SimulatedReadingSheet.kt` | 自动翻页/模拟阅读，与朗读无关 |

**可整文件删**：`help/TTS.kt`、`help/MediaHelp.kt`、`help/exoplayer/**`、`help/readaloud/**`、`ui/book/readaloud/**`、`ui/book/audio/**`、四个 service、`service/{playback,readaloud}/**`、`model/{ReadAloud,AudioPlay,ReadAloudSessionStore,ReadAloudLayoutPolicy,PlaybackCapsuleState}.kt`、`domain/model/readaloud/**`、`feature/readaloud/**`、对应 dao/entity/repository/gateway/usecase、`ui/widget/components/VoiceAuditionButton.kt`

**依赖面（media3 / androidx.media）**：声明在 `app/build.gradle.kts:218-220`、`libs.versions.toml:61,184-186`。消费者除朗读/音频/试听链外，**只剩 `model/analyzeRule/AnalyzeUrl.kt:6,816` 的 `getMediaItem()`**（书源 JS 扩展）。⇒ 只要保留 audio 书源类型或该 JS 扩展，**media3 不能整体移除**。

**BookType.audio 的连带面（比朗读更广）**：`data/dao/BookDao.kt:169,199,243,276,717,868-870,981,998`（SQL 位运算 + `IdAudio` 分组统计）、`DatabaseMigrations.kt:510-511`、`help/book/BookExtensions.kt:58,333`、`help/source/BookSourceExtensions.kt:132`、`help/webView/WebJsExtensions.kt:75`、`help/storage/ImportOldData.kt:140,191`、`ui/book/info/edit/BookInfoEditViewModel.kt:138,141`、`ui/login/SourceLoginRoute.kt:64`、`ui/main/bookshelf/BookShelfItem.kt:42`、`MainNavGraph.kt:560,1830`、`data/entities/BookGroup.kt:55` ⇒ 应作为**独立一步**执行并单独验证。`BookType.video` 仅 `BookExtensions.kt:64`，与语音无关。

**接线面（删漏会崩溃或残留）**：`AndroidManifest.xml:11(SYSTEM_ALERT_WINDOW),16,19,27,424-431(三个 mediaPlayback service),447-450(MediaButtonReceiver)`；`NotificationId.kt:9,10,17`；`AppConst.kt:22`（channelIdReadAloud）；`IntentAction.kt:21,23,36,39`；`PreferKey.kt:42,43,121,166,171-184,211-213,287,367,439-444`；`res/values*/strings.xml` 四语言（约 50 个 key）；`help/storage/{BackupConfig:20-21,86,99,115, Backup:82,187-188, Restore:25,42-43,47,237-243,573-601, ImportOldData:139-140,191}`。

**数据层策略（重要）**：**不要改历史 migration**（`DatabaseMigrations.kt:510-511` 等），否则老库升级崩溃；只在新版本 migration 里 drop 或保留空表。相关表：`read_aloud_*`、`bgm_*`、`voice_pool*`、`cast_character`、`chapter_role_assignment`、`book_cast_memory`、`cloud_tts_engine`、`httpTTS`。旧备份里的 `httpTTS.json` 必须**忽略而非报错**（`dbIsNotIgnored("httpTTS")` 兼容分支保留）——与 D11 决策三一致。

**测试硬约束（S0 必须先改）**：`ui/main/MainNavigatorReadAloudTest.kt:77` 直接读取 `service/BaseReadAloudService.kt` 源码文本；`ui/book/read/ReadBookDomainSplitBoundaryTest.kt:49-50,321` 断言文件存在。

**推荐删除顺序（每步应可编译）**
- **S0** 改测试硬约束 + 同步下调 `build.gradle.kts` 基线
- **S1** 摘引用（单人串行热点）：`ui/book/read/{ReadBookContract,ReadBookViewModel,ReadBookController,ReadBookScreen,ReadBookFloatingActionBar,ReadBookMenuBar*,ReadAloudCastDelegate}` → `model/{ReadBook,CacheBookModel,ReadAloud*}` → `ui/main/{MainNavKey,MainNavigator,MainIntent,MainNavGraph,MainScreen,MainActivity}` → `di/appModule.kt`
- **S2** 剥共享核心：`feature/reader/**` + `CastMarkers` 收敛为纯文本标记（**不要删文件**）
- **S3** 删叶节点文件（①③④⑤⑥ + service + help/{TTS,MediaHelp,exoplayer,readaloud}）
- **S4** 清接线（Manifest / NotificationId / AppConst / IntentAction / PreferKey / strings 四语言 / MediaButtonReceiver 分支）
- **S5** 数据层（entity/dao/repository/gateway/usecase + `appDatabaseModule` 绑定；新 migration）
- **S6** 依赖与门禁（media3 是否可删取决于 `AnalyzeUrl.kt:816`；`BookType.audio` 类型位清理另立一步）

**未验证**：报告基于静态只读分析，**未运行编译**；`ReadBookContract.kt`、`domain/model/readaloud/**`、各 Cloud TTS Provider、四语言 strings 未逐项实测。

## 7. 热点文件（单 writer 清单）

```
ui/main/{MainNavGraph,MainNavKey,MainNavigator,MainIntent,MainDestination,MainScreen,
         MainActivity,MainViewModel,MainContract,MainBookPageNavigation}.kt
di/{appModule,appDatabaseModule}.kt
data/{AppDatabase,DatabaseMigrations}.kt
constant/PreferKey.kt
help/storage/{Backup,Restore,BackupConfig}.kt
build.gradle.kts            # 架构门基线
app/src/main/res/values*/strings.xml
app/src/main/AndroidManifest.xml
utils/{ContextExtensions,FragmentExtensions}.kt
```

---

## 8. 未验证项（必须在后续波次消解）

| 项 | 原因 | 消解方式 |
|---|---|---|
| 所有编译断裂点 | 侦察为静态分析，未跑 Gradle | W1 第一刀后 `:app:compileAppDebugKotlin` |
| BGM/配音域精确规模 | 本轮未逐文件统计 | W1 补充 `find help/readaloud/cast` 等测绘 |
| Room schema 变更后果 | 未跑迁移测试 | W3 迁移测试 + 真机升级安装 |
| `modules/web` 与后端端点的对应关系 | 仅确认存在，未逐端点核对 | W2 逐端点清单 |
| 小组件（AppWidget）子集范围 | `ui/widget` 207 文件未细分 | W4 前专门测绘 |
| 真机行为（阅读渲染、TTS、Web 服务） | 无设备执行 | W4 手测清单 |
