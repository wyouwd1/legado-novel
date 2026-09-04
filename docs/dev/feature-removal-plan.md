# 功能减法：盘点与删除方案（定稿，已批准）

> 状态：**9 份审计全部完成，决策点已拍板，方案定稿**。执行中：本地仓库改代码，远程 test-env-wifi（192.168.31.217，
> 15G 内存）跑构建验证。审批通过前不执行删除 —— 已通过，开始逐片执行。
> 本文是唯一执行依据；各片实施 agent 以本文对应小节为权威清单。

## 1. 决策记录（已与用户逐轮确认）

- **第一目标**：产品功能瘦身优先；死代码/架构/体积清理只在删完功能后随片清理，不做全局清扫。
- **使用者**：个人自用；**接受永久分叉**（删除上游文件无合并顾虑）。
- **删除深度**：彻底物理删除（UI/服务/Model/资源/规则类/独立依赖全删，不留入口与兼容层）。
- **数据库**：接受 AppDatabase 版本升级，迁移中 DROP 被删功能表；保留功能的表与数据必须完好。
- **执行节奏**：完整盘点+删除方案已审 → 按 8 个垂直切片逐片删除，每片独立验证并提交。
- **验证门槛（每片）**：`:app:compileAppDebugKotlin`、`testAppDebugUnitTest`、`lintAppDebug`、
  `verifyConfigArchitecture`、`assembleAppDebug` 通过；UI 无该功能入口残留。**在远程构建环境执行**。
- **体积验收**：基线已采集，交付时报告 delta，不设硬性目标。
- **文档同步**：开发文档收窄、删除孤立帮助页、`modules/web` 前端同步、更新用户可见日志。

### 最终保留集（红线 + 明确保留）

- 小说核心闭环：书架/分组/我的（底部两 tab：书架、我的）；书源搜索/书源管理/换源；TXT 目录规则；
  阅读器渲染与排版；进度/书签/标注/高亮/标签规则；缓存下载；txt/epub 导入与本地格式解析；阅读记录；
  主题外观（MD3 主题系统）与阅读/通用设置。
- **备份与书源（用户强调必须保留）**：备份/恢复 + WebDAV；书源规则 + Rhino JS 引擎、源登录、
  源调试工具（BookSourceCheckService 等）。
- **破例保留**：RSS 订阅（入口从底部 tab 移至“我的”）、替换规则/正文净化、modules/web + Ktor
  （内置 Web 服务）、内置浏览器、二维码、文件关联、更新检查。

### 最终删除集（8 块，彻底物理删除）

| # | 模块 id | 功能 | 主要内容物 |
|---|---|---|---|
| 1 | `remove-manga` | 漫画阅读 | ui/book/manga、漫画设置、图源/漫画渲染分支 |
| 2 | `remove-tts-audio` | 听书 TTS + AudioPlay | 朗读/播放服务×4、播放器 UI、media3 依赖、TTS 规则执行、相关 DB 表 |
| 3 | `remove-home-discovery` | 首页模块 + 书城发现 | Home/Explore 底部 tab、home/homepage/explore UI、homepageModules/discoveryUrl 消费 |
| 4 | `remove-ai-chat` | AI 对话 | ui/ai、AI provider 配置与网络对话、阅读 AI 任务、书架 AI 自动分组 |
| 5 | `remove-dict-translation` | 词典/翻译 | ui/dict、翻译配置、划词翻译入口与执行 |
| 6 | `remove-related-books` | 关联书籍/书评周边 | relatedBooks 字段消费、详情页周边区块、文档 |
| 7 | `remove-book-knowledge` | 书籍知识/人物事件谱 | ui/book/knowledge、人物/事件/知识实体与 DAO、Gateway |
| 8 | `remove-onboarding` | 欢迎页/新手引导 | feature/onboarding、ui/welcome、首启判定分支、资源 |

**执行顺序（已校正）**：②听书(104→105) → ⑤词典/翻译(105→106，先摘 AI 翻译通道保编译绿) →
⑦+④ 知识+AI 同批(106→107，AiToolRepository 互依赖，P2 全删 AI) → ①漫画 → ⑥关联书籍（须先于③） →
③首页/发现(底栏 5→2 + RSS 迁“我的” + homepage 两表 bump) → ⑧欢迎页 → 收尾(res/xml pref 统一清理 +
文档/前端/日志/基线 delta)。

## 2. 审计清单（9 个只读 agent 全部完成 ✓）

| 审计 agent | 模块 | 状态 | 归档节 |
|---|---|---|---|
| A1 漫画 | remove-manga | 已完成 ✓ | 3C |
| A2 听书 | remove-tts-audio | 已完成 ✓ | 3H |
| A3 首页/发现 | remove-home-discovery | 已完成 ✓ | 3G |
| A4 AI | remove-ai-chat | 已完成 ✓ | 3E |
| A5 词典/翻译 | remove-dict-translation | 已完成 ✓ | 3F |
| A6 关联书籍 | remove-related-books | 已完成 ✓ | 3A |
| A7 书籍知识 | remove-book-knowledge | 已完成 ✓ | 3D |
| A8 欢迎页 | remove-onboarding | 已完成 ✓ | 3B |
| A9 结构面 | 跨功能共享 | 已完成 ✓ | 4 |

## 3A. A6 remove-related-books（已审计 ✓）

**结论**：自包含中等规模功能，物理删除可行；无 DB/DI/Manifest/依赖/前端影响；repo 无独立「书评/热评」。

- 功能面：`BookSource.ruleBookInfo.relatedBooks`（模型字段 `BookInfoRule.kt:25`）；编辑入口
  `BookSourceEditViewModel.kt:379`；加载链路全在 `ui/book/info/BookInfoViewModel.kt`（scheduleRelatedBooksLoad/
  parseRelatedBookModules/loadRelatedBooks/resolveAndExplore，复用保留引擎 `WebBook.exploreBookWithResolvedUrl`）；
  渲染 `BookInfoScreen.kt:391-402` + private RelatedBooksBanner(1637-1678)；跳转 effect：RelatedBookClick→BookInfo、
  RelatedBooksMore→ExploreShow（仅本功能使用）。
- 删整文件（2）：`app/src/main/assets/web/help/md/relatedBooksHelp.md`、`docs/spec/related-books.md`。
- 保留文件定点删改：
  - `data/entities/rule/BookInfoRule.kt:25` 删 relatedBooks 字段（Gson/Rhino 注册不需动，旧 JSON 多余 key 静默忽略）
  - `ui/book/info/BookInfoContract.kt`：UiState.relatedBooks(:37)、RelatedBooksUi(146-152)、
    RelatedBookClick/RelatedBooksMore(210-211)、Effect NavigateToBookInfo/NavigateToExploreShow(255-266)、import SearchBook(:8)
  - `ui/book/info/BookInfoViewModel.kt`：179/197/233/359-360/850/882/898/919/1447/1465-1486/1620-1708/1788-1792 +
    import SearchBook(:21)，顺手清 unused async/coroutineScope/delay
  - `ui/book/info/BookInfoScreen.kt`：391-402 渲染块、1637-1678 RelatedBooksBanner；import SearchBook(:100)、BannerModule(:105)
  - `ui/book/info/BookInfoRouteScreen.kt`：59-60 两回调参数、176-182 两 effect 分支（sealed when 同步删）
  - `ui/book/info/BookInfoActivity.kt:64-77` 两 lambda 实参；`ui/main/MainNavGraph.kt:1147-1152` 两 lambda 实参
  - `ui/book/source/edit/BookSourceEditViewModel.kt:379` 字段条目
  - 可选：`model/webBook/WebBook.kt` exploreBookWithResolvedUrl(158-194) 删后成死代码，建议一并删
    （保留兄弟 exploreBook*/exploreBookWithRuleData，被 Explore/BookSourceCheck 共用）
- 资源字符串：`related_books`（values:1193 / zh-RCN:1152 / zh-rTW:1682 / zh-rHK:2138）、
  `a11y_related_books_more`（values:2626 / zh-RCN:2621）——删代码后可删。勿误删 ai_book_results(A4)、段评组字符串。
- 文档索引行：`docs/spec/index.md:4`、`docs/dev/index.md:46`、`docs/dev/source-fields.md:71`。
- modules/web 零引用不改；README/updateLog 等仅泛化措辞勿动。
- 测试：BookInfoWithSettingsTest/BookInfoBackdropStyleTest 以全默认参数构造 UiState，删默认属性安全，跑后确认。
- 风险：① Contract/ViewModel/RouteScreen 三文件必须同 commit（sealed when 编译强制）；② WebBook.kt 共享勿误删兄弟；
  ③ 旧源 JSON 多余 key 无运行时风险；④ grep 清扫勿误伤「相关书籍」泛化文案。
- 验收：编译/主验证集 → grep 清零（relatedBooks|RelatedBooks|related_books|RelatedBooksUi|RelatedBooksDef|
  relatedBooksHelp|a11y_related_books_more）→ 手工：带 relatedBooks 旧源详情页无区块无崩溃；源编辑器无字段。

## 3B. A8 remove-onboarding（已审计 ✓）

**结论**：onboarding = `io.legado.app.feature.onboarding`（4 个 Compose/UDF 文件）+ `ui/welcome/WelcomeActivity.kt`（薄宿主）+
3 处启动接线 + 字符串组；无 DB 表、无独立依赖、无测试、无 modules/web 引用。

- 功能面：MainActivity.onCreate → `checkStartupRoute()`（MainActivity.kt:500-509），`LocalConfig.isFirstOpenApp`
  （SharedPreferences 文件 `local` 键 `"firstOpen"`，默认 true，读取自清零）为 true 时 startActivity<WelcomeActivity>
  并 finish（onCreate L300 早退）；WelcomeActivity → OnboardingRouteScreen → 完成 → MainActivity.createHomeIntent。
  4 步引导：隐私 → WebDav/备份恢复 → 书籍文件夹 → 主题；不在 Nav3 图内。
- 删整文件（7 Kotlin，无专属资源文件）：
  - feature/onboarding/{OnboardingContract,OnboardingRouteScreen,OnboardingScreen,OnboardingViewModel}.kt
  - ui/welcome/WelcomeActivity.kt
- 保留文件定点删改：
  - MainActivity.kt：L63 import；L300 早退点；checkStartupRoute()（L500-509）整体删
  - AndroidManifest.xml：L193-198 `.ui.welcome.WelcomeActivity` activity 块
  - di/appModule.kt：L293 import、L593 viewModelOf(::OnboardingViewModel)
  - docs/dev/feature-catalog.md:36、ui/README.md:25
- DB/偏好：无 Room 表；onboarding 只读写共享偏好（WebDAV url/account/password、defaultBookTreeUri、theme/themeMode、
  password——键保留由设置页维护）。**`"firstOpen"` 键与 `LocalConfig.isFirstOpenApp` 保留**（MainActivity.upVersion
  L527 帮助对话框分支仍用）；`privacy_policy_ok`（LocalPreferences.kt:27）仅 onboarding 写、无读者 → 可删
  LocalConfig.privacyPolicyOk(LocalConfig.kt:54-65)+键常量，旧值遗留无副作用。
- 字符串 Group A 必删（onboarding 用，4 个 strings 文件）：`onboarding_*` 11 名 + `welcome_book_folder_tip` +
  `welcome_book_folder_not_selected`。Group B 建议删（旧 View 启动页残留、0 引用）：welcome_title、first_read、
  welcome、welcome_style、welcome_style_summary、show_welcome_text、welcome_text、custom_welcome、custom_welcome_summary。
  按 name 删（zh-rHK/TW 中与 onboarding 块分离）。lint 兜底孤儿字符串。
- 风险/保留：ProcessStartupUpdateCheckGate（MainActivity.kt:100 + ProcessStartupUpdateCheckGate.kt）与其 consume
  （L301-303）保留；readHelpVersionIsLast 等保留；启动框架（installSplashScreen/版本更新/崩溃提示/备份同步/
  自动刷新/自动检查更新）保留；privacyPolicy.md/disclaimer.md（About 在用）、ThemeColorSelector 等组件保留。
- 行为差异：全新安装首启直达主界面；upVersion 帮助弹窗分支回到删前旧行为。
- 验收：编译+主验证集；grep 清零（onboarding/Onboarding/ui.welcome/Group A 名）；手工：清数据全新安装直达主界面、
  连续启动不弹帮助、改版本号仍弹更新日志、WebDAV/主题/书籍文件夹设置仍可编辑。

## 3C. A1 remove-manga（已审计 ✓）

**结论**：manga 横跨 UI/domain/data/test，无漫画专用 Room 表、无 modules/web、无 androidTest、无专属依赖、
无 Service/BroadcastReceiver、无“我的”条目。

- 直接删除文件：`ui/book/manga/**`（15 文件）、`ui/config/readMangaConfig/`（1，死代码）、
  domain/gateway/Manga{SettingsGateway,ReaderDataGateway,ReaderSession,ReaderSessionFactory}.kt、
  domain/model/manga/、domain/model/settings/MangaSettings.kt、data/repository/MangaSettingsRepository.kt、
  data/repository/manga/**（5）、data/model/MangaFooterConfig.kt、测试 6、Manifest ReadMangaActivity 块。
- 保留文件定点删改（行号会漂移，执行时 grep 复核）：MainActivity.kt(45,198-204,284,292,355-356,480,668)、
  MainNavGraph.kt(80-81,164,341-342,741-756,1130-1136)、MainNavigator.kt(37-45,119,179,212,454-464)、
  MainNavKey.kt(139-145,273)、MainIntent.kt(232-243)、ContextExtensions.kt(47,66-67,78-84,97-103)、
  FragmentExtensions.kt(20,25-26,62-68)、BookInfo{Activity.kt:54-61,RouteScreen.kt:57,74,136-139,
  Contract.kt:54,ViewModel.kt:1776}、OtherConfig{Contract.kt:25,94,Screen.kt:189-191,ViewModel.kt:112-113,367}、
  OtherSettings.kt:17、FeatureSettingsRepositories.kt:517,546、BackupConfig.kt(64,137-171,346-369,406,418,442-443,461-462)、
  PreferKey.kt(126-136,203-204,284-314,402-403)、EventBus.kt:33、downloadCacheConfig 三文件、
  HttpHelper.kt(148-150,169-183)、CoverExtras.kt(25,28)、CoverFetcher.kt(67-68,142-147,167-175)、
  appModule.kt(68,99-101,148-150,218,253,384,407-416,528,713)、Book.kt ReadConfig(479-482)、
  glide OkHttpModelLoader/OkHttpStreamFetcher/LegadoDataUrlLoader/BookCover 的 manga 分支。
- DB：无漫画专用表；BookDao 漫画分组查询/分支（BookDao.kt:45,67,620,653,850,879,1013）、BookGroup.IdManga(-7)、
  AppDatabase.kt:272-282 onOpen 插入漫画分组行。
- 保留共享件：coil/Glide 底座、ImageProvider、阅读器 legacy isImage/isComic/imageStyle 渲染分支、
  BookType.image、BookSourceType.image 规则映射、clearComicCache 缓存逻辑。showMangaUi 双模型同一 pref key（删除协调）。
- **已拍板决策**：①书架“漫画”分组彻底删（迁移删行，仅删 onOpen 插入块 + BookDao 函数/分支 + IdManga；
  存量库可选加 migration 删 book_groups.groupId=-7 行，book 数据保留）；②保留阅读器图片章节渲染兜底
  （image 书回落文本阅读器，删漫画阅读器 UI 与 isImage 打开分支到 ReadBook）；③搜索页「漫画」chip 保留作类型标签；
  ④本地漫画目录/压缩包导入删（LocalBook.kt:250,286-301,351-360、ImportBookScreen.kt:121 菜单项、LocalMangaLoader）；
  ⑤下载缓存页「漫画缓存」行删（DownloadCacheConfig 三文件）。
- 验收：编译+主验证集；grep 清零（ReadManga|MangaReader|MangaSettings|readMangaConfig|okHttpClientManga|
  HttpCacheType.MANGA|CoverExtras.Manga|IdManga）；手工：书架点开 isImage 书→文本阅读器；导入菜单无漫画项；
  缓存页无漫画行；设置无 showMangaUi；Manifest 无 ReadMangaActivity；升级后书架/本地书/PDF 仍在可打开。

## 3D. A7 remove-book-knowledge（已审计 ✓）

**结论**：独立子功能；入口全在书籍详情/编辑页（详情页主体保留）；DB 影响大（5 表）；跨片依赖（听书/AI 片均注入
BookKnowledgeGateway，删除顺序：听书片先行）。

- 直接删除 26 文件：ui/book/knowledge/ 整目录 21；data/entities/BookKnowledge.kt（5 实体：book_character_profiles/
  events/relations、book_knowledge_entries、book_outline_nodes）；data/dao/BookKnowledgeDao.kt；
  data/repository/BookKnowledgeRepository.kt；domain/gateway/BookKnowledgeGateway.kt；
  domain/usecase/IdentifyBookCharactersUseCase.kt（AI 识别人物）。
- 保留文件定点删改：BookInfoScreen.kt（BookInfoCharacters 区块 + 死代码 BookInfoKnowledge/BookInfoEvents）；
  BookInfoEditScreen.kt（BookKnowledgeEditCard 四按钮）；BookInfo* 五文件 + ui/main 五文件（7 路由+const+Intent 包装+
  7 nav entry）；di/appModule.kt（gateway 绑定/Identify usecase/7 VM）；di/appDatabaseModule.kt（DAO factory）。
- DB：**version 104**；新增手动 Migration(104,105) DROP 5 表；保留 AutoMigration 43→104 链与 schema 95–104 json；
  5 表仅 bookUrl 文本关联无 FK。备份/WebDAV（Backup.kt/Restore.kt 白名单）不引用知识。
- **跨片协调**：① data/repository/AiToolRepository.kt（AI 片属）有 9 个知识工具 → 与 remove-ai-chat 同批删除；
  ② AiModels.kt AiTaskType.IDENTIFY_CHARACTERS + AiPromptConfigViewModel + 3 条 ai_prompt_* 归 AI 片；
  ③ 听书片（先于本片）BookVoiceCastingViewModel/RefineSpeechWithAiUseCase/ResolveLocalSpeakersUseCase 注入
  BookKnowledgeGateway → 听书片先删则本片无此牵制；④ 字符串按 key 粒度删（voice_*、book_characters、
  ai_prompt_* 共享键保留待别片）。
- 无 Manifest/测试/modules/web/依赖/帮助页引用。Avatar 运行时文件 filesDir/character_avatars 可选清理。
- 验收：主验证集 + 覆盖升级（104→105 5 表消失、保留表完好）+ grep 清零
  （BookCharacter|BookEvent|BookKnowledge|BookOutline|IdentifyBookCharacters|book_character_|book_knowledge_|
  MainRouteBook(Character|Knowledge|Event)|ROUTE_BOOK_(CHARACTER|KNOWLEDGE|EVENT)|createBook(Character|Knowledge|Event)|
  EXTRA_(CHARACTER_ID|ENTRY_ID|EVENT_ID)|identify_characters；app/schemas 95-104 json 允许保留）+ 详情页无知识区块/按钮。

## 3E. A4 remove-ai-chat（已审计 ✓）

**结论**：无独立 AI SDK 依赖（自研于共享 okhttp/Gson 之上）、无 Manifest 组件；AI 底座被 5 个消费者共用
（对话/阅读 AI 任务族/知识/翻译/听书语音分析）—— 全部在被删名单上，**P2 全删 AI 平台**；DB 8 张 ai_* 表。

- 直接删除 ≈61 个 kt：
  - UI：ui/ai/{AiReasoningModeButton,AiTaskResultSheet}.kt + ui/ai/chat/ 8 文件；ui/config/ai/ 18 文件
  - 网络/传输：data/repository/ai/ 8 文件、data/repository/AiTextRepositoryImpl.kt、domain/gateway/AiTextGateway.kt
  - 对话专属：data/entities/{AiChatConversation,AiChatMessage,AiMemory}.kt、data/dao/{AiChatDao,AiMemoryDao}.kt、
    data/repository/{AiChatRepository,AiMemoryRepository,AiToolRepository}.kt、
    domain/gateway/{AiChatGateway,AiMemoryGateway,AiToolGateway}.kt、domain/usecase/AiChatGenerationUseCase.kt
  - 阅读区 AI 任务族：ui/book/read/{ReadAiContract,ReadAiDelegate}.kt + sheet/{ChapterSummarySheet,AiTextCleanSheet,
    AiTextRewriteSheet,AiRewritePresetConfigSheet}.kt + domain/usecase/{GenerateChapterSummaryUseCase,
    CleanSelectedTextUseCase,AiTextFactoryUseCase}.kt
  - AI 共享底座：domain/model/AiModels.kt、AiMessageParts.kt、AiModelRegistry.kt、AiProfile/AiArtifact/AiTaskPreset/
    AiPromptPreset 系 entities+DAO+Repository+Gateway、AiTaskManager、AiToolAwareGenerationUseCase
  - 书架 AI 自动分组生成半部：ui/main/bookshelf/autoGroup/{AiAutoGroupContract,AiAutoGroupSheet,AiAutoGroupViewModel}.kt、
    domain/usecase/GenerateBookshelfAutoGroupPlanUseCase.kt、BookshelfAutoGroupPlanParser.kt、
    data/repository/BookshelfAutoGroupPromptRepository.kt、domain/model/BookshelfAutoGroupPromptText.kt
- 保留文件定点删改：MyScreen.kt(:70,78,89,192-195)、MainScreen.kt(:129,608)、MainNavGraph.kt（import 47,110-114、
  My 入口 313、AI entries 482-540、onNavigateToAi 442/549）、MainNavKey.kt(:74-94,260-263)、MainNavigator.kt(:82-101,429-430)、
  ConfigNavScreen.kt(:29,82-83)、ReadBookContract.kt（ai_summary/ai_rewrite 405-406、AI Intent 族 495-498/709-724、
  ReadBookSheet AI 枚举 ~981）、ReadBookViewModel.kt（import/注入 127-136、aiHost 277/aiDelegate 308/aiState 320、
  分发 898-935、ShowSheet 1001、onSheetDismissed）、ReadBookScreen.kt（import、ReadAiUiState 98/138、sheet 396-414）、
  ReadBookController.kt（划词 399/1261-1290/1321-1322/2243-2244）、ReadBookMenuBarFloating.kt:405-406、
  ReadBookMenuBarTool.kt:509-513、SystemMenuPage.kt ~918-935、res/values/ids.xml:23-24、GroupManageSheet.kt(:39,71,149,258-261)。
- DB：8 张 ai_* 表（ai_provider_profiles/ai_model_profiles/ai_task_presets/ai_artifacts/ai_chat_conversations/
  ai_chat_messages/ai_memory/ai_prompt_presets）DROP（本片 106→107 或随 ⑦ 同批）；AppDatabase entities :124-127、
  DAO :233-237、appDatabaseModule factory :56-60；**book_content_processes 旧 ai_clean/ai_rewrite 行随迁移
  DELETE（kind IN ('ai_clean','ai_rewrite')）+ BookContentProcess.kt:37-38 常量删（内容处理底座保留）**。
- 保留：SaveBookContentProcessUseCase/BookContentProcess/ContentProcessor；自动分组**应用半部**
  （ApplyBookshelfAutoGroupPlanUseCase/BookshelfAutoGroupRepository/BookshelfAutoGroup.kt）保留但入口不展示；
  共享 okHttpClient/GSON/Nav3 框架。
- 字符串：ai_* 前缀 values/zh-rCN 各 256 条、zh-rHK/zh-rTW 各 199 条全清；ids.xml 2 条；勿混删 speech_analysis_*（TTS 片）、
  rule_with_ai/ai_understanding（朗读设置）。
- 测试：删 OpenAiChatThinkingTest、AiReasoningLevelTest、AiAutoGroupViewModelTest、GenerateBookshelfAutoGroupPlanUseCaseTest；
  改 ReadBookDomainSplitBoundaryTest.kt(:82-87,261-274)。AiSpeechAtomizerTest 是非 AI（保留）。
- 验收：主验证集 + 入口清零（我的→AI Chat、设置→AI 设置、阅读工具栏/划词 AI 项、AI 自动分组）+
  grep 无 ui.ai/config.ai/domain.model.Ai*/data…Ai* import + DB 旧库升级 8 表消失保留表完好 + 手工冒烟。

## 3F. A5 remove-dict-translation（已审计 ✓）

**结论**：词典（划词）+ 翻译（划词/章节翻译/翻译模式 + 配置）整域删除；Room 表 dictRules、2 Manifest activity、
文件缓存（非 Room）；与 AI 片（TranslateChapterUseCase/AiTaskType.TRANSLATE_CHAPTER/温度常量）先改后删；共享件含
划词框架、替换净化、ContentChunker、简繁转换图标。

- 直接删除：ui/dict/（8，含 rule/）、ui/config/translation/（3）、model/translation/ 相关（TranslationManager 等）、
  ui/book/read/TranslationThinkingCapsule.kt、domain/usecase/TranslateChapterUseCase.kt、domain gateway 3
  （TranslationCache/TranslationSettings/Dictionary）、domain/model/{TranslationConstants,settings/TranslationSettings,
  PartialTranslationAssembler,TranslationResultPreviewParser,TranslationDictionaryPolicy}.kt、
  data/entities/{DictRule,TranslationCache}.kt、data/dao/DictRuleDao.kt、data/repository/{DictRuleRepository,
  DictionaryRepositoryImpl,TranslationCacheRepositoryImpl}.kt、ui/association/ImportDictRule{Dialog,ViewModel}.kt、
  res/menu/dict_rule{,_edit,_sel}.xml、assets/defaultData/dictRules.json、assets/web/help/md/dictRuleHelp.md、
  docs/dev/dict-rule.md、测试 4、Manifest:276-286 两条 activity。
  ⚠️ domain/model/TranslationModels.kt 不可整删：TextChunk 被 ContentChunker/AI 共用，需先拆分。
- 保留文件定点删改：ReadBook.kt(39-41,175,410,743,774-779,1330-1340,1377-1387,1473-1510)、ReadBookScreen(52,580-585)、
  ReadBookContract(22,250-252,420,494,580,708,1005)、ReadBookViewModel(77-79,119,598-599,897,1065,1497-1499,1998,
  2022-2023,2082-2116,2411-2429,2637)、ReadBookRouteScreen(78,794-800)、ReadBookController(1292-1295,1318,2240 仅删
  menu_dict 项)、ReadBookMenuBarTool/Floating/Bottom、SystemMenuPage:919、ReadAiDelegate(22,247-257 仅翻译分支)、
  Book.kt(336-342,486)+MangaBookSnapshot(136,156,178)、FeatureSettingsRepositories(13,22,117-130,228-250)、
  PreferKey.kt:441-448、DefaultData/LocalConfig/Backup/BackupConfig/Restore（dictRule.json 分支）、
  FileAssociationActivity:95、OnLineImportActivity:44/74、ExportBookService（ContentSource.Translation 支路）、
  MyScreen:172-180、ConfigNavScreen:85-88、AiConfigScreen:132-137、VisibleWebView(61-133)、
  MainNavGraph(125,444,496,546-551)/MainNavKey:109/MainNavigator(83-107,429-434)。
- AI 保留区（A4 片属，先改后删协调）：AiModels.kt（AiTaskType.TRANSLATE_CHAPTER:21 + 温度常量引用 181/206/280）、
  AiProfileRepository(205,304)、AiChatGenerationUseCase:42、AiTextFactoryUseCase:277、CleanSelectedTextUseCase:294、
  AiChatViewModel:164、AiConfigViewModel:36-40、AiPromptConfigViewModel:43-48。
- 绝不能删：AnalyzeUrl/AnalyzeRule 引擎、ActionMenuItem/TextActionSelectionMenu 划词框架、替换/净化全链路、
  ContentChunker+TextChunk、Icons.Default.Translate/ic_translate.xml（charset/简繁转换共用）、
  dialog_recycler_view.xml/item_source_import.xml。
- DB：dictRules DROP（本片 105→106；schema 60..104 重导出 106.json）；TranslationCache 文件缓存非 Room；
  translationMode 在 Book.config JSON 无需迁移。
- DI 21 行：appModule(350,387,454,514-515,534-535,542,596,720)+appDatabaseModule(22,84)；字符串 31 键×4 语言
  （16 键无消费者）；proguard 65-72 陈旧规则清理；无专属 gradle 依赖。
- 验收：主验证集 + 词典/翻译入口清零 + 旧库 105→106 dictRules 消失 + grep 残留清零 + 手工（划词框架其它项、
  替换净化、简繁转换正常）。

## 3G. A3 remove-home-discovery（已审计 ✓）

**结论**：删除 Home/Explore 底部 tab 及首页模块/发现/探索结果 UI；**MainRouteHome 是壳路由（栈底+外部 Intent/
快捷方式/欢迎页落点）≠ Home tab，必须保留**；Explore/搜索**引擎层与 ui/widget/components/explore/ 套件是共享件**
（全局搜索“单来源展开全部结果” SearchViewModel.kt:946-956 与源登录 SourceLoginSheets 复用）；发现相关书源 JSON
字段保留只删 UI。

- 直接删除：
  - UI 27：ui/main/home/（3）、ui/main/homepage/（16 含 manage/ modules/）、ui/main/explore/（2）、ui/book/explore/（3）
  - 首页模块专属 domain/data 10：HomepageModels.kt、HomepageSettings.kt、{HomepageModulesGateway,HomepageSettingsGateway}.kt、
    {HomepageModulesRepository,HomepageSettingsRepository}.kt、entities {HomepageModule,HomepageCustomSet}.kt、
    dao {HomepageModuleDao,HomepageCustomSetDao}.kt
  - Home 仪表盘链 4（HomeDashboard.kt/Gateway/UseCase/Repository）——**已拍板：快捷备份/恢复迁入“我的”，其余随删**
  - 文档：assets/web/help/md/homepageModulesHelp.md、docs/spec/homepage-modules.md、docs/dev/discovery-url.md
- 保留文件定点删改（核心）：
  - MainDestination.kt：删 Home(11-14)/Explore(21-24)；mainDestinations(:37)→(Bookshelf, My)；Rss(26-29) 随 tab 摘除
  - MainScreen.kt：imports 83-84、参数 139、pager when Home 527-547/Explore 577-587/Rss 588-605、defaultHomePage 220-226、
    FloatingBottomBar 623-697、My 分支 606-617 扩展 RSS 入口
  - MainNavGraph.kt：imports 54-56、Home entry 内 ExploreShow lambda 367-375、BookInfo 过渡动画引用 1065-1099、
    entry<MainRouteExploreShow> 1373-1401、BookInfo onNavigateToExploreShow 1150-1152
  - MainNavigator.kt：160/176/227、222-235、534-542
  - MainActivity.kt：createExploreShowIntent 274-279、startRoutes 367-393（MainRouteHome 壳保留）
  - MainIntent.kt：EXTRA_EXPLORE_NAME:24/URL:29、createExploreShowIntent 364-376
  - MainNavKey.kt：删 MainRouteExploreShow(222-227)、ROUTE_EXPLORE_SHOW(285)；MainRouteHome(10-11) 保留
  - 穷尽 when 同步：MainContract.kt:30-44、AppIcons.kt:126-159、NavIconManageSheet.kt:218-225、MainViewModel.kt:102-109
  - 主题/导航设置：ThemeConfigScreen.kt:336-343、MainNavigationSettingsSheet.kt:46-85,97-103、ThemeConfigViewModel.kt:290-321、
    EditThemeSheet.kt:186-200,235-241、NavIconManageSheet.kt:55-76、AppIcons.kt:126-159、arrays.xml default_home_page(279-285)/
    _value(295-301) 收窄 bookshelf/my、AppShellSettings.kt:7-9,19（mainNavigationOrder 默认 "bookshelf,my"）、
    ThemeExportData.kt/ThemeImportExport.kt 主题包 schema 收窄
  - BookInfo 族（与切片 6 联动）：BookInfoScreen.kt:105（BannerModule import）、1669-1671；BookInfoContract.kt:262-266、
    ViewModel.kt:1477-1486、RouteScreen.kt:60,180-181、Activity.kt:73-75
  - RSS 保留区内：ui/rss/read/RssJsExtensions.kt:190-205（JS open("explore") 分支移除——已拍板）；Rss 路由族保留
  - CoverConfigScreen.kt:172-185 探索过滤下拉 + CoverConfigViewModel.kt:68 + CoverSettings.kt:18 +
    FeatureSettingsRepositories.kt:191,211 + PreferKey.kt:407 + LocalPreferences.kt:21-23
  - 备份：Backup.kt:84-85,193-199、Restore.kt:22-23,250-259、BackupConfig.kt:80-81,91,109-110
  - BookSourceDao 死查询可清：flowHomepageModules:85-90、flowExploreSources:92-97、flowExploreSourceParts:99-104、
    flowExplore():106-110/128-148/159-166/408-412、flowEnabled/DisabledExplore:122-126、allEnabled/DisabledExplore:249-253
    ——保留 enableExplore（书源管理菜单 848-857）
  - DI：appModule 删 imports 237/299/300/301、绑定 556/561/562/761 + 首页模块链 60-61/141-142/467/474 + 仪表盘链
    （59/140/191/338/437）；保留 55-56/139/183-184/419-420/483-485（Explore 引擎绑定）；appDatabaseModule 26-27/88-89
  - **“我的”页新增 RSS 入口**：MyScreen 新增 RSS 条目组（RssSourceManage/RssFavorites/RssRead/RuleSub 等，路由族保留）、
    MyViewModel 加 PrefClickEvent、MainScreen My onNavigate 分支 609-616 接线
  - modules/web：bookSourceEditConfig.ts find 组 175-248 + enabledExplore 559-560 + source.d.ts:47-48,63-68 +
    utils/souce.ts:73（字段保留但前端隐藏/收窄入口）
- 共享件绝不能删：ui/widget/components/explore/ 六件套（源登录复用；仅 ExploreKindSelectSheet/calculateExploreKindRows
  可选清）、Explore 引擎（ExploreBooksUseCase+ExploreBooksGateway+ExploreRepositoryImpl+WebBook.exploreBook*+BookList+
  BookSourceExtensions exploreKinds/getExploreInfoMap+rule/ExploreKind+FlexChildStyle）、共享 UseCase
  （SaveSearchBooks/ResolveBookShelfState/AddToBookshelf）。
- DB：homepage 两表 DROP（本片 bump）；book_sources/book_sources_part 的发现/首页**列字段保留**（已拍板：只删 UI/死查询，
  不物理删列，避免 GsonExtensions/App.kt Rhino/ImportOldData/书源 JSON 生态连锁）。
- 验收：两 tab 底栏、RSS 从 My 可达且源管理/阅读/收藏/规则订阅路由族正常、搜索“单来源展开”仍工作、源登录表单正常、
  书源管理/编辑/校验/调试无发现残留（字段保留故只删展示）、EditThemeSheet 仅书架/我的槽位、深链 ROUTE_MAIN/ROUTE_RSS_*
  回归、数据迁移旧库完好、备份 zip 兼容、grep 文案清零、README/English/help md 收窄。

## 3H. A2 remove-tts-audio（已审计 ✓，最大片）

**结论**：4 服务 + 1 接收器 + 播放器/朗读 UI + media3 依赖 + 6 张 DB 表 + 阅读器内核 preserveReadAloudPosition 参数链；
**help/TTS.kt 已拍板一并删除**（划词朗读/RSS 朗读随删，不再有保留调用点）；BookType.audio/音频分组/徽标一并下线。

- 直接删除：
  - 服务/接收器 5：service/{AudioPlayService,BaseReadAloudService,HttpReadAloudService,TTSReadAloudService}.kt、
    receiver/MediaButtonReceiver.kt
  - model 4：AudioPlay.kt、ReadAloud.kt、ReadAloudSessionStore.kt、ReadAloudLayoutPolicy.kt
  - help：help/MediaHelp.kt、help/exoplayer/{ExoPlayerHelper,InputStreamDataSource}.kt、help/readaloud/ 全树 18、
    **help/TTS.kt**（随删）
  - UI：ui/book/audio/（4）、ui/book/readaloud/ 全树（13）、ui/book/read/{ReadAloudCapsule,ReadAloudDelegate}.kt、
    sheet/{ReadAloudScreen,ReadAloudConfigContent}.kt、feature/reader/core/readaloud/ReaderReadAloudChapter.kt、
    ui/widget/components/player/（5）
  - association 内 2：ImportHttpTts{Dialog,ViewModel}.kt
  - data：entities 3 文件 6 实体（HttpTTS.kt、CloudTtsEngineEntity.kt、ReadAloudSpeech.kt 含 4 实体）、DAO 4、
    repo 6、security/CloudTtsCredentialCipher.kt
  - domain：gateway 5、usecase 6（含 RefineSpeechWithAiUseCase）、settings/ReadAloudSettings.kt、domain/model/readaloud/ 11
  - utils/TTSCacheUtils.kt；资源：res/xml/pref_config_aloud.xml + 13 drawable/mipmap + raw/silent_sound.mp3 +
    assets/defaultData/httpTTS.json；文档 4 整删（httpTTSHelp.md、docs/dev/tts-rule.md、docs/tts-multi-speaker-design.md、
    docs/handover-tts-multi-speaker-fixes.md）；测试 26（含 androidTest HttpTtsTest）
- 保留文件定点删改（阅读器内核高风险，单 commit、编译门禁逐段闭合）：model/ReadBook.kt（preserveReadAloudPosition
  参数链 43,102,163,243-261,606,616-620,974-1002,1152-1210,1235,1264-1653 等）、ReadBookViewModel.kt（构造参数+朗读状态+
  HttpTtsRepository，imports 5,22,41,50,67,68,73,80 + 行 122,138-148,165-171,512-549,870-874,1292-1369,1428,1768-1797,
  2188-2239,2400-2404,2639-2640）、ReadBookController.kt、ReadBookLoadDelegate.kt:30,191、ReadBookRouteScreen.kt、
  ReadBookScreen.kt、ReadBookMenuBar*.kt、MoreConfigSheet.kt、ReadConfigUpdateDelegate.kt:689-691、
  ui/config/readConfig/{ReadConfigScreen:355-362,ReadConfigViewModel:140,211,ReadConfigContract:41,112}、
  宿主：MainActivity.kt（AudioPlay.CallBack/桥/包装）、MainNavGraph.kt（5 entry+参数）、MainNavKey.kt、MainNavigator.kt、
  MainIntent.kt、utils/ContextExtensions.kt:72-105、FragmentExtensions.kt:56-75
  - 书源/登录分支：help/source/SourceHelp.kt:12,37-38、help/webView/WebJsExtensions.kt:16,76-77、
    model/analyzeRule/AnalyzeUrl.kt:803-807（仅删 media3 getMediaItem 段）、ui/login/SourceLogin*（HttpTts/AudioBook
    枚举分支+构造参数）、api/ShortCuts.kt（朗读快捷方式）、receiver/SharedReceiverActivity.kt:36-38
  - 组合根/常量：App.kt（:31 import、:296-305 readAloudChannel、**:345 HttpTTS Rhino 注册——只删这一行**）、
    help/config/AppConfig.kt:11,32,45,57,70（拆读参数，对象保留）、constant/{AppConst.kt:22,24,NotificationId:9-10,
    IntentAction 仅听书项,EventBus 仅听书项,PreferKey ~20 键+ReadAloudBgMode 480-485}
  - 崩溃：help/CrashHandler.kt:18,60
  - 数据模型：data/entities/Book.kt:284-289,472（setTtsEngine/ReadConfig.ttsEngine）+ MangaBookSnapshot 复制点
  - 阅读记录/缓存：model/CacheBookModel.kt（preserveReadAloudPosition）
  - 备份/默认数据：Backup.kt:81,184-185、Restore.kt:24,226-229、BackupConfig.kt:77,90,106,117-118,20-21、
    DefaultData.kt:6,27-28,45-51,113-115、LocalConfig.kt:98-99
  - DB：AppDatabase.kt（entities 121,129-131、DAO 205-207,220、**onOpen httpTTS fix 353-360 强制删**、version 105、
    迁移登记）；DatabaseMigrations.kt 迁移史 83,309-313,319 保留、新增 104→105 DROP 6 表
  - AI 提示词：ui/config/ai/prompt/AiPromptConfigViewModel.kt:66-72（ANALYZE_SPEECH）、domain/model/AiModels.kt:28（随删）
  - association 分发：BaseAssociationViewModel.kt:42-43、OnLineImportActivity.kt:34-36,70-72、FileAssociationActivity.kt:92
  - **划词朗读/RSS 朗读（已拍板一并删）**：ReadBookViewModel.kt:1427-1437 TextActionAloud、ReadBookController.kt:1638
    TextActionSpeak、ui/rss/read/ReadRssViewModel.kt:19,74,359-390（RSS 朗读）
- 共享件绝不能删：base/BaseService.kt（空钩子，删子类后无需改）、通知框架其余渠道、BookKnowledge 域（听书只读方勿动）、
  AppConfig 对象本体、书源/RSS 登录框架、Room 基架、共享图标（ic_play/ic_pause/ic_stop_black_24dp/ic_auto_page_stop/
  ic_launcher/ic_st_aloud_background 等）。
- DB：6 表无 FK；httpTTS 有 onOpen data-fix（删表后必须同删否则新装冷启动崩）；version 104→105 迁移 DROP；
  fallback 仅 1-9 版本，旧库漏配迁移会 IllegalStateException。
- Manifest：3 个 mediaPlayback service（L463-471）+ MediaButtonReceiver（L486-492）+ 权限
  FOREGROUND_SERVICE_MEDIA_PLAYBACK（L23）+ queries TTS_SERVICE（L541-543）删；MODIFY_AUDIO_SETTINGS 系统级共享
  核对勿删；L227 残留注释可清。
- 依赖：libs.versions.toml:57-58,174-176 + app/build.gradle.kts:216-218 三依赖（media3-exoplayer、
  media3-datasource-okhttp、androidx.media:media）全仓确认仅听书簇使用 → 随片删；libs.lyricViewx 全仓无 import
  （死依赖，随片清理复核）。
- 字符串 ~190+/语言（read_aloud 51、cloud_tts 105、audio_ 19、含 tts 137、speech_analysis_* 等）按 name 模式删。
- 测试：整删 26；保留文件改 12 处（OtherSettingsMappingTest、ReadSettingsMappingTest、ConfigUpdateActionsInvariantTest、
  ReadBookDomainSplitBoundaryTest、OtherConfigViewModelTest、MainIntentTest、MainNavigatorBackStackTest、
  CacheBookPendingReadRequestTest、ReaderPageTest/ReaderPageNavigatorTest）。
- 风险：阅读器内核朗读高亮耦合最重（textAccentColor 经 readAloud 参数）；HttpTTS Rhino 注册只删一行最安全。
- 验收：主验证集 + 朗读入口清零 + 书架音频书行为按拍板（一并下线）+ 冷启动（onOpen 不碰 httpTTS）+ 老库升级链路 +
  pnpm build 后 SourceHelp 无 httpTTSHelp 链接 + 体积 delta。

## 4. 结构面汇总（A9 已审计 ✓）

**事实校正**：AppDatabase 当前版本 **104**（AppDatabase.kt:115）；50 张实体表 + 1 @DatabaseView(BookSourcePart)；
39 个 abstract DAO；AutoMigration 43→44…101→102、103→104 + 手动 98→99/99→100/102→103；**AutoMigration 不能删表，
删表必须手动 Migration**（DatabaseMigrations.kt:13-26 数组追加）。

### 4.1 表 ↔ 模块对照（D = DROP）
| 模块 | 表（DROP） |
|---|---|
| ②听书 | httpTTS、read_aloud_voices、book_voice_bindings、chapter_speech_analysis、chapter_speech_segments、cloud_tts_engines |
| ③首页 | homepage_modules、homepage_custom_sets |
| ④AI | ai_provider_profiles、ai_model_profiles、ai_task_presets、ai_artifacts、ai_chat_conversations、ai_chat_messages、ai_memory、ai_prompt_presets |
| ⑤词典 | dictRules |
| ⑦知识 | book_character_profiles、book_character_events、book_character_relations、book_knowledge_entries、book_outline_nodes |
| ①⑥⑧ | 无专属表 |

- 保留红线表：books、book_groups、book_sources(+BookSourcePart view)、chapters、replace_rules、searchBooks、
  search_keywords、cookies、rss*、bookmarks、book_marks、txtTocRules、ruleSubs、caches、keyboardAssists、servers、
  search_content_history、readRecord*、highlightRules、highlight_tag_rules、tag_group_rules、book_content_processes、
  exact_chapter_page_counts（阅读器分页，勿当听书删）等。
- 多实体单文件：ReadAloudSpeech.kt 含 4 实体（②）、BookKnowledge.kt 含 5 实体（⑦）；删实体时整文件拆。
- 迁移写法：DatabaseMigrations.kt migrations 数组追加 Migration(n,n+1) + 逐表 DROP；AppDatabase version 递增；
  同 commit 删 @Database 实体列表/DAO 属性/DAO factory/实体与 DAO 文件/migration（Room 校验一致）；
  **每片串行 bump**（②104→105、⑤105→106、⑦④106→107、③→108…，链必须连续）；MigrationTest.kt（androidTest 骨架）
  升级为真实多版本演练。
- onOpen/种子：AppDatabase.kt:353-360（httpTTS 两 sql）；DefaultData.kt:27-28,45-51,113-115（httpTTS.json 种子+
  needUpHttpTTS）、:107 needUpDictRule；assets/defaultData/{httpTTS.json,dictRules.json,bookSources.json(听书源
  type=1)}；BackupConfig.kt:77,90,106 删条目。
- AI 底座共享消费者：书架智能分组、阅读 AI、知识、翻译、听书 —— P2 全删后无保留消费者，ai_* 表全 DROP。

### 4.2 DI/导航/Manifest/依赖/测试/启动 汇总
- DI：见各片归档；ReadBookViewModel L715-756 注入被删面参数按片删参。
- 底栏：mainDestinations (Home,Bookshelf,Explore,Rss,My)→(Bookshelf,My)；AppShellSettings 默认顺序 "bookshelf,my"；
  主题包 schema 收窄；MyScreen 新增 RSS 入口组。
- Manifest：② 3 service + MediaButtonReceiver + queries TTS_SERVICE；① ReadMangaActivity；⑤ DictRuleActivity+DictActivity；
  ⑧ WelcomeActivity。权限候选删：FOREGROUND_SERVICE_MEDIA_PLAYBACK、MODIFY_AUDIO_SETTINGS（核对）；
  服务保留：CacheBook/BookSourceCheck/ExportBook/Download/Web/WebTile。
- 依赖：② media3-exoplayer、media3-datasource-okhttp、androidx.media:media；① telephoto-zoomable-image-coil3；
  okHttpClientManga 只删分支不删 client；quick-chinese-transfer 保留；:modules:book/:modules:rhino/:baselineprofile/
  modules/web 依赖不动。
- 启动/Rhino：App.kt initRhino L345 删 HttpTTS 注册+import；BookSource/RssSource 注册保留；readAloudChannel 删；
  ProcessStartupUpdateCheckGate 保留。
- 测试：A 整删 39 + B 定点改 15 + C 保留 171。
- 文档：assets/web/help/md 整删 4；defaultData 3 json；docs/dev 整删 dict-rule/tts-rule/discovery-url +
  docs/spec homepage-modules/related-books + 范围外 docs/tts-multi-speaker-design.md、handover-tts-multi-speaker-fixes.md；
  modules/web 3 处收窄。

## 5. 基线（已采集 ✓，HEAD=9db5ae6d0 未改动代码）

- **APK 体积（assembleAppDebug，ABI 拆分）**：armeabi-v7a **65,825,280 B**；arm64-v8a **66,285,458 B**；
  universal **72,856,707 B**。arm64-v8a md5=3a59d9a9725a7ec6a01d4fab9c99329d。
- **方法数（arm64-v8a debug）**：**599,842**。
- **代码量**：`:app` 主源码 1650 .kt / **289,778 行**；测试 218 .kt / **19,959 行**；res/xml 431 个。
- 每片提交时记录单片 delta，收尾时出总量 delta 报告。

## 6. 决策点（已全部拍板 ✓）

- **6.1 漫画**：①书架“漫画”分组彻底删；②保留阅读器图片章节渲染兜底；③搜索页「漫画」chip 保留；④漫画目录/压缩包导入删；
  ⑤漫画缓存行删。
- **6.2 AI = P2 全删**：聊天+阅读 AI（摘要/改写/净化）+书架 AI 自动分组生成半部+8 张 ai_* 表全删；book_content_processes
  旧 ai_clean/ai_rewrite 行随迁移清理；自动分组应用半部保留但入口不展示。
- **6.3 书源发现字段**：字段与规则体系保留，只删展示 UI/死查询。
- **6.4 Home 仪表盘**：快捷备份/恢复迁入“我的”，其余卡片随删。
- **6.5 RSS JS open("explore")**：分支随 ExploreShow 一并移除。
- **6.6 划词朗读/RSS 朗读**：一并删除（help/TTS.kt 及调用点随删）。
- **6.7 BookType.audio**：一并下线（类型/音频分组/BookDao 音频查询/“设为音频书”全删）。
- **6.8 res/xml pref**：独立收尾片统一清理全部 0 引用 pref XML。
- **6.9 DB 迁移**：逐片串行 bump。
- **6.10 Agent Teams**：每片 1 实施 agent + 1 验证 agent；跨片协调点由集成把关。

## 7. 审批

- [x] 用户已拍板全部决策点并批准执行；执行中：本地改码 + 远程 test-env-wifi 验证。

## 8. 执行进度（实时更新）

| 切片 | 状态 | 提交 | 验证 | delta |
|---|---|---|---|---|
| ②听书 TTS+AudioPlay | **✅ 完成** | bcc486dfc（-23,470 行）+ c46f6a965（文档）+ 28a14650a/ac74b2057/71e78d995（修复 r1-r2）+ 352786b71（lint）+ ec0a749a9（测试） | 远程 gate5 **BUILD SUCCESSFUL**：compile/test(855通过+3 ignored)/lint(0 error)/verifyConfigArchitecture/assemble 全绿；残留 grep 仅剩迁移史 SQL | APK 三 ABI 各 **−1,716,153 B**（arm64 66,285,458→64,569,305）；DB 104→105 DROP 6 表；schema 105.json 已生成 |
| ⑤词典/翻译 | **✅ 完成** | d50118c25（105 文件，-4,871 行）+ c6234bbf9（import 修复） | 远程 gate7 **BUILD SUCCESSFUL** 全绿；残留 grep 仅迁移史；schema 106.json 不含 dictRules | 本片 **−1,034,055 B**；累计 vs 基线 **−2,750,208 B（−4.15%）**；DB 105→106 |
| ⑦+④知识+AI | **✅ 完成** | c804fa983（154 文件，-25,524 行）+ 67efafda5（import 修复） | 远程 gate9 **BUILD SUCCESSFUL** 全绿；被删类 APK 级验证 0 命中；方法数 552,986（−46,856 vs 基线）；schema 107.json 生成 | DB 106→107 DROP 13 表 + 清 book_content_processes AI 行；自动分组应用半部保留无入口；APK 字节未动（收尾 clean 构建复核） |
| ①漫画 | 🔄 进行中 | — | — | — |
| ⑥关联书籍 | ⏳ | — | — | — |
| ③首页/发现 | ⏳ | — | — | — |
| ⑧欢迎页 | ⏳ | — | — | — |
| 收尾 | ⏳ | — | — | — |

**执行基础设施**：本地仓库（源码真相，实施 agent 只编辑不构建）；远程 test-env-wifi（192.168.31.217，
rsync 同步，排除 local.properties/app/build/.gradle；门禁命令 `./gradlew :app:compileAppDebugKotlin
testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache`）。

**已知遗留（非本任务范围）**：AnalyzeRuleFastPathReproTest 3 个用例 @Ignore（复现 main 既有 AnalyzeRule
NativeObject 快捷路径 bug，与功能删除无关；main 修复后删 @Ignore 转正）。
