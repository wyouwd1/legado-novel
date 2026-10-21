# 任务看板（Task Board）

> 状态机：`⬜ 待启动` → `🔵 进行中` → `🟢 完成（含证据）` ｜ `🔴 阻塞` ｜ `⚪ 取消`
> 本轮决策已裁决（见 `DECISION-MAP.md` 第 3 节），波次已启动。
> 每条任务必须附：认领人（agent 代号）、涉及文件（已 `claim_files`）、出口证据。
> **调度方式**：`subagent` 池被历史会话占满（上限 16，停用未立即释放）→ 梯队改用 `bg_delegate`（inspect 只读）+ 主控串行收口。

---

## W0 · 基线冻结与专项侦察 ✅（基本完成）

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W0-1 | 建工作分支 `subtract/novel-only` | Orchestrator | 🟢 | `git switch -c` 成功，HEAD `070ca1a9b` |
| W0-2 | 基线编译 `:app:compileAppDebugKotlin` | Verifier | 🟢 | `BUILD SUCCESSFUL in 4m 52s`（任务 `b271da78f`） |
| W0-2b | 完整基线验证集（单测 + lint + 架构门 + assemble，并记录 APK 体积） | Verifier | 🟢 | 首次 `packageAppDebug` 瞬时失败（已澄清，见 `VERIFICATION.md` §2.1）；第二次重跑 `BUILD SUCCESSFUL`；APK 3 个 ABI 产物已产出 |
| W0-6 | 基线数字入档 | Verifier | 🟢 | 源文件 1,752 / 335,597 行；测试 311 文件；护栏表 23/74、2/13、11/30 |
| W0-3 | TTS/朗读域专项侦察（15k+ 行，与阅读页深度耦合） | Scout（bg_delegate） | 🟢 | 报告 22,305 B，结论已入 `FINDINGS.md` §6 |
| W0-4 | 环境排障：SDK 路径 / 代理 / JDK | Orchestrator | 🟢 | `ANDROID_HOME=/home/handx-aijiu/android-sdk`；`JAVA_TOOL_OPTIONS` 走 `127.0.0.1:7890` |
| W0-5 | 既有未提交改动归属确认（`bookSources.json`、`docs/book-source-verification/`） | Orchestrator + 用户 | ⬜ | 待用户确认是否纳入/暂挂 |

## W1 · 共享点剥离 + 叶节点域删除（4 路并行，热点串行）

### W1-0 执行细则（已核实源码，可直接动刀）

**共享类 1：`ui/rss/read/RssJsExtensions.kt`（210 行，被两个保留功能继承）**
- 消费者：`help/webView/WebJsExtensions.kt:22,38`（书源 JS 基类，**保留**）、`ui/login/SourceLoginJsExtensions.kt:11,28`（书源登录，**保留**）；`ui/rss/read/RssReadWebController.kt:332` 随 RSS 删。
- 类内分界（实测）：通用（`activityRef`/`sourceRef`/`getSource`/`getTag`/`put`/`get`/`searchBook`×2/`addBook`/`showPhoto`/`getStringList`/`getString` + `open()` 的 `login`·`search`·`explore` 分支）vs RSS 专属（`open()` 的 `sort`/`rss` 分支，依赖 `appDb.rssSourceDao`/`rssStarDao`/`rssArticleDao`/`rssReadRecordDao`、`RssReadRecord`、`RssSource`、`MainActivity.createRss*Intent`）。
- 动作：新建 `help/webView/BaseJsExtensions.kt`（包 `io.legado.app.help.webView`）承接上述通用部分，`open()` 只保留 `login`（去 `RssSource` 分支，仅 `BookSource`）/`search`/`explore`；两个子类改继承新基类。

**共享类 2：`ui/rss/read/VisibleWebView.kt`（177 行，含 `VisibleWebView` + `VisibleWebViewCompose`）**
- 消费者：`ui/browser/WebViewRouteScreen.kt:44,167`、`ui/login/SourceLoginSheets.kt:45,315`、`ui/dict/DictSheet.kt:45,155`（**全部保留**）；`ui/rss/read/RssReadRouteScreen.kt:443` 随 RSS 删。**零 RSS 依赖** → 纯搬家。
- 动作：移到 `help/webView/VisibleWebView.kt`（改包名 + 更新上述 3 个引用）。

**共享类 3：Coil 三件套漫画分支**（`help/coil/{CoverInterceptor,CoverFetcher,CoverExtras}.kt`）—— 同时服务小说封面，只能剔分支、不能删文件（详细位置见 `FINDINGS.md` §4）。

**补充核实（W1-0 动手前）**：
- `help/webView/WebJsExtensions.kt:38` 除继承 `RssJsExtensions` 外，还 import 了 `RssSource`（随 RSS 删）与 `AudioPlay`/`BookType`（随 W2 删）→ W1-0 只改继承关系，其余 import 随各自域清理。
- `ui/login/SourceLoginJsExtensions.kt:28` 继承同上；其 `clearTtsCache()`（依赖 `ReadAloud.upReadAloudClass()` 与 `HttpTTS`）属于 **W2** 清理范围，不在 W1-0 动它。
- `SourceLoginJsExtensions.open()` 已 `override` 并只在 `name == "login"` 时特判，新基类的 `open()` 保留 `login`/`search`/`explore` 三支即可。

**约束**：W1-0 单独一个提交，**先编译通过再开 W1-1~W1-4**（后续四个域删除都依赖它）。

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W1-0 | 共享点剥离独立提交（细则见上）：`RssJsExtensions` → `help/webView/BaseJsExtensions`、`VisibleWebView` 搬家、Coil 漫画分支剥离 | Surgeon-shared（主控执行） | 🟢 | 提交 `ae3e09b3d`；编译 `BUILD SUCCESSFUL`；架构门 `RssJsExtensions` 8→6 |
| W1-1 | RSS 域（D2） | Surgeon-rss（主控执行） | 🟢 | 提交 `2318cfb84`；删除 **79 文件 / -7,698 行**；编译 0 错；单测 **1702/0**；详见 `VERIFICATION.md` §7.1 |
| W1-2 | 漫画域（D3，含 20 测试、类型位、偏好键） | Surgeon-manga（主控执行） | 🟢 | 提交 `df02effd7`；删除 **55 文件 / -13,968 行**；编译 6 轮收敛 623→**0** 错；完整验证集 **BUILD SUCCESSFUL**；单测 **1578/0**；APK arm64 −2.7MB；详见 `VERIFICATION.md` §7.2 |
| W1-3 | AI + 知识库 + 配音的**界面层**（D5/D6/D4c） | Surgeon-ai（主控执行） | 🟢 | 提交 `7a14c1f8b`；净删 **51 文件 / -14,983 行**；编译 14 轮收敛 485→**0**；完整验证集 **BUILD SUCCESSFUL 5m31s**；单测 **1571/0**；架构门基线 74→**73**；lint 基线 425→**330**（纯删除）；详见 `VERIFICATION.md` §7.3 与 `W1-3-SCOPE.md` |
| W1-4 | 知识库/评论（D6） | Surgeon-knowledge | ⚪ | **已并入 W1-3**（知识库界面与 AI 界面共用组件，无法分开删） |
| W1-5 | **热点收口**：导航 / DI / AppDatabase 注册 / PreferKey / 基线 | Integrator | 🟢 | 已在 W1-3 内完成导航、DI、`appModule`、架构门键与 lint 基线；strings 清理转 W5-6 |
| W1-6 | 独立验证：编译、单测、架构门、反证 grep | Verifier | ⬜ | `VERIFICATION.md` W1 段 |

### W1-3 实际执行边界（2026-10-14 执行；原侦察边界已被取代）

> **原计划**（下方存档供对照）是「AI 域整体删除」。执行时发现 AI 底座被**朗读链反向依赖**，
> 整域删除会连锁拖入整个 W2；且 `AiReasoningModeButton`/`AiThinkingCard` 被配音与知识库共用，
> 「保留契约」也解不开。最终**缩小为「界面层删除」**：AI 底座与朗读配音链整体保留到 W2。
> 转向过程详见 `VERIFICATION.md` §7.3 与 `W1-3-SCOPE.md`。

**实删（51 文件）**：`ui/book/knowledge/**`(19)、`ui/config/ai/**`(15)、`ui/ai/**`(7，保留 `chat/` 思考卡片三件套)、
`ui/main/bookshelf/autoGroup/**`(4)、`ui/book/read/sheet` 的 4 个 AI 面板、`ReadAi{Contract,Delegate}.kt`(2)、
`AiAutoGroupViewModelTest.kt`。

**保留到 W2（避免断链）**：AI 底座（`AiProfileGateway`/`AiProfileRepository`、`data/repository/ai/**`、
`AiModels`、`AiModelRegistry`、`AiChatGenerationUseCase`、`AiChatContract`）、朗读配音链（`help/readaloud/**`、
`ReadAloudCastDelegate`、`AiCast*`/`AiScene*`、`ui/book/readaloud/**`、`BookVoiceCasting*`、
`ui/ai/chat/{AiThinkingCard,AiThinkingStepsCard,AiChatMessageParts}`、3 个角色头像工具、`AiCastDialogSheet`）、
数据层 entity/dao 与 `AppDatabase` 声明（W4）。

**接入点改动（全部精确编辑，无行扫描块删除）**：`MainNavGraph`、`MainNavKey`、`MainNavigator`、`MainScreen`、
`MyScreen`、`appModule`、`ReadBook{Contract,Screen,ViewModel,Controller}`、`ReadMenuActionDefs`、
`TextActionSelectionMenu`、`BookInfo{Contract,Screen,ViewModel,RouteScreen,EditScreen}`、`GroupManageSheet`、
`BookVoiceCastingScreen`、`TranslationConfigScreen`、`ConfigNavScreen`、`values/ids.xml`、`app/lint-baseline.xml`、
`build.gradle.kts`。

---

#### （对照存档）原侦察边界（AI 域整体删除）

可整删层：

| 层 | 文件 | 数量 |
|---|---|---|
| UI | `ui/ai/**`、`ui/config/ai/**`（含 `prompt/`、`summary/`）、`ui/book/read/ReadAi{Contract,Delegate}.kt`、`ui/book/read/sheet/{AiCastDialogSheet,AiRewritePresetConfigSheet,AiTextCleanSheet,AiTextRewriteSheet}.kt`、`ui/main/bookshelf/autoGroup/AiAutoGroup*` | 31 |
| 领域 | `domain/gateway/Ai*Gateway.kt`(7)、`domain/model/Ai{MessageParts,ModelRegistry,Models}.kt`、`domain/usecase/Ai{ChatGeneration,TextFactory,ToolAwareGeneration}UseCase.kt`+`AiTaskManager.kt`+`RefineSpeechWithAiUseCase.kt` | 14 |
| 数据 | `data/repository/ai/**`(9)、`data/repository/Ai*Repository.kt`(6)、`data/dao/Ai*Dao.kt`(6)、`data/entities/Ai*.kt`(7) | 28 |

执行顺序（避免正文链断裂）：

1. **先解耦正文链**：`ui/book/read/**` 对 `ReadAiDelegate`/`AiTextFactoryUseCase`/`AiTextGateway` 的调用点先摘（阅读净化/重写入口是 AI 专有的，摘后正文渲染不受影响——需逐处核实并记录）。
2. `ui/**` 叶节点（31）→ 编译。
3. `domain/**`（14）→ 编译。
4. `data/repository/ai/**`（9）+ `data/repository/Ai*Repository.kt`（6）→ 编译；`dao`/`entities`（13）**保留到 W4**（Room 版本不动）。
5. 清接线：`di/appModule.kt`、`appDatabaseModule.kt`、`PreferKey`、四语言 `strings.xml`、`AndroidManifest`（若有 AI 服务）。
6. 测试：`data/repository/ai/*Test`(4)、`domain/model/AiReasoningLevelTest`、`domain/usecase/RefineSpeechWithAiParamsTest`、`help/readaloud/cast/AiCast*Test`(2)、`help/readaloud/segment/AiSpeechAtomizerTest`、`ui/main/bookshelf/autoGroup/AiAutoGroupViewModelTest` → 随所属层删；**保留** `help/readaloud/**` 下的 AI 文件到 W2（与 TTS 同批，D4b 裁决）。

待首次 grep 核实：`BookDomainRepositoryImpl`、`DatabaseMaintenanceRepository`、`AppStartupMaintenanceUseCase` 等名字含假阳性（`-iname "*ai*"` 会匹配 `Main`/`Detail`/`Plain`），不要按文件名盲删。

## W2 · 朗读大块（TTS / 配音 / BGM / 音频书）— 按 S0–S6 串行

> 依赖 W0-3 结论（`FINDINGS.md` §6）。**cast/bgm 参与阅读排版主路径**，禁止整目录删除。
> 热点文件必须单人串行：`ReadBookController.kt`、`ReadBookViewModel.kt`、`model/ReadBook.kt`、
> `ReadBookContract.kt`、`ReaderCanvasSurface.kt`、`ReaderPaginator.kt`、`CacheBookModel.kt`、`MainNavGraph.kt`、`MainActivity.kt`、`di/appModule.kt`

| # | 步骤 | 认领 | 状态 | 出口 |
|---|---|---|---|---|
| W2-S0 | 改测试硬约束（`MainNavigatorReadAloudTest.kt:77`、`ReadBookDomainSplitBoundaryTest.kt:49-50,321`）+ 基线下调 | Surgeon-readaloud（主控执行） | 🟢 | 实际约束点比侦察多 3 处：①删 `MainNavigatorReadAloudTest.kt`（唯一通用测试并入 `MainNavigatorBackStackTest`，消掉读 `BaseReadAloudService.kt` 源码的断言）；②`runtimeFiles` 去 4 个朗读文件；③DOMAINS 去【多角色分配】【朗读】两条。验证：测试编译 0 错 + 59 测试通过。提交 `2fa89aadc`。**基线下调与 `hostOwnedState` 两条字段条目（`_readAloudProgress`/`closeReadBookKeepReadAloud`）必须等产线文件/字段删除**（架构门为双向棘轮，先删键会失败） |
| W2a | **朗读/音频域删除** | Surgeon-readaloud | 🟢 | 提交 `dd61c2bfd`；228 源码文件 / -53,373 行；编译 0 错；单测 1262/0；架构门/assemble 通过；APK 65/64/71MB | 实际已删 **225 文件**（UI 页面 + 服务/合成层 + 领域/数据实现层一并删，因分层在朗读域不成立）：`ui/book/{audio,readaloud}/**`、`feature/readaloud/**`、`service/{readaloud,playback}/**`、4 个朗读 service、`help/{readaloud,exoplayer,TTS,MediaHelp}`、`domain/model/readaloud/**`、7 个 usecase、6 个 gateway、6 个 repository、阅读页朗读件与 9 个 sheet、`MediaButtonReceiver`。编译收敛 **1299 → 708 → 629**；已修 10 个接入点。剩余 20 个接入点：`MainNavGraph`(133)、`ReadBookViewModel`(117)、`appModule`(88)、`MainActivity`(85)、`model/ReadBook`(51)、`ReadBookController`(37)、`ReadBookScreen`(25)、`MainScreen`(18)、`TocViewModel`(14)、`ReaderCanvasSurface`(14) 等 |
| W2b | **服务与合成层删除**：`service/{BaseReadAloudService,HttpReadAloudService,TTSReadAloudService,AudioPlayService}.kt`、`service/readaloud/**`、`service/playback/**`、`model/{ReadAloud,AudioPlay,ReadAloudSessionStore,ReadAloudLayoutPolicy}.kt`、`help/TTS.kt`、`help/MediaHelp.kt`、`help/exoplayer/**`、`help/readaloud/**`(45)、`domain/model/readaloud/**`(13)、`domain/usecase/{AnalyzeChapterSpeech,BuildSpeechPlan,PrepareChapterSpeechPlan,Sync*}.kt` | Surgeon-readaloud | 🟢 | W2a `dd61c2bfd`；289 文件 / -53,373 行 |
| W2c | **剥阅读页耦合**（热点串行）：`ui/book/read/{ReadAloudDelegate,ReadAloudCastDelegate,ReadAloudCapsule,ReadAloudControlsRequestBus}.kt`、`ui/book/read/sheet/` 朗读簇、`feature/reader/core/cast/**`（`CastMarkers` 收敛为纯文本标记，**不删文件**）、`feature/reader/core/readaloud/**`、`ReaderPaginator`/`ReaderChapterBlockMeasurer`/`LegacyReaderChapterPaginator`/`ReaderChapterSource`/`ReaderCanvasSurface`/`LegacyReaderStyleRangeMapper` 去 cast 字段、`model/{ReadBook,CacheBookModel}.kt` 保住重载不跳位语义 | Surgeon-reader | 🟢 | W2b `90d57490d` + W2c `fba4c7f2f`；reader.* 测试通过 |
| W2-S4 | 清接线：Manifest、`NotificationId`、`AppConst`、`IntentAction`、`PreferKey`、四语言 strings、MediaButtonReceiver、`ui/main/**` 导航/Intent | Integrator | 🟢 | W2a/W5 `7fb79c9d1`；编译 + strings 资源通过 |
| W2-S5 | 数据层：entity/dao/repository/gateway/usecase 删除 + `appDatabaseModule` 绑定（**不改历史 migration**；新迁移归 W4） | Migrator | 🟢 | W4 `d167de175`；110 文件删除 |
| W2-S6 | 依赖与门禁：media3 保留（`AnalyzeUrl.kt:816`）；`BookType.audio` 类型位另立一步；架构门基线键同步移除 | Integrator | 🟡 | media3 保留；audio/image 旧类型位保留给 Room 迁移兼容 |
| W2-V | 独立验证：编译、完整验证集、架构门、反证 grep | Verifier | 🟢 | `VERIFICATION.md` §7.4/§7.8；反证 grep 无 cast/BGM 代码引用 |

### W2 中断恢复指引（2026-10-14，工作区未提交）

W2a 删除已落盘（**225 文件 `git rm` 已暂存**）+ 11 个接入点已修，但**编译尚未通过**（629 错误 / 20 接入点），因此尚未提交。若会话中断，按以下顺序恢复：

1. `git status --porcelain | grep -c '^D'` 应为 225；`git diff --cached --stat | tail -1` 看规模
2. 继续修接入点（按错误数降序，逐个“读精确上下文 → 精确文本编辑”）：
   `ui/main/MainNavGraph.kt`(133) → `ui/book/read/ReadBookViewModel.kt`(117) → `di/appModule.kt`(88) →
   `ui/main/MainActivity.kt`(85) → `model/ReadBook.kt`(51) → `ui/book/read/ReadBookController.kt`(37) →
   `ui/book/read/ReadBookScreen.kt`(25) → `ui/main/MainScreen.kt`(18) → `ui/book/toc/TocViewModel.kt`(14) →
   `feature/reader/ReaderCanvasSurface.kt`(14) → `ui/book/read/ReadBookMenuBar.kt`(12) → `ReadBookContract.kt`(9)
   小件：`ReadBookRouteScreen`、`SourceLoginJsExtensions`、`ReadBookLoadDelegate`、`OnLineImportActivity`、
   `AnalyzeUrl`、`Restore`、`CrashHandler`、`PlayerMorphHost`、`App.kt`、`SourceLoginRoute`、
   `FileAssociationActivity`、`SharedReceiverActivity`、`AppConfig`
3. 错误提取模板：`grep '^e: ' /tmp/w2aN.log | sed 's|.*/io/legado/app/||' | sed 's/:[0-9]*:[0-9]* .*//' | sort | uniq -c | sort -rn`
4. **关键同步约束**：`di/appModule.kt` 里 `ReadBookViewModel` 的注入参数
   （`readAloudSettingsRepository` / `aiCastAssignUseCase` / `aiSceneAssignUseCase` /
   `syncReadAloudVoicesUseCase` / `readAloudSessionStore` / `httpTtsRepository`）
   **必须与 `ReadBookViewModel` 的构造参数同一步删除**，否则会持续报“No parameter with name”
5. 若确认不可继续：`git checkout HEAD -- app/src` + `git reset` 可完整回滚（无历史损失）

**已修完的接入点（勿重复）**：`appModule`(41 行 import/绑定)、`AppConfig`、`ReadConfig`、`OtherConfig`×3、
`SourceLogin`×2、`Restore`、`ReadContentProcessDelegate`、`SourceHelp`、`WebJsExtensions`、
`BookInfoViewModel`、`LegacyReaderChapterPaginator`；整文件删除：`MediaButtonReceiver`、`IdentifyBookCharactersUseCase`

### W2 规模实测（2026-10-14，主控测绘）

- `find` 按 `*readaloud*/*tts*/*audio*/*cast*/*bgm*/*speech*/*voice*/*playback*/*player*/*exoplayer*` 命中 **195 文件**；
  排除通用组件（`core/ui/player/**` morph、`ui/widget/components/player/**`）后真身约 **150 文件**，估 **4 万+ 行**。
- 按子波切分的理由：整波一次性删除会产生数百编译错误且上下文不可控；
  子波每步可编译、可提交、可回滚（W1-1/W1-2/W1-3 已验证该模式）。

## W3 · 外围域（2–3 路并行）

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W3-1 | 首页自定义模块（D7）+ `BookSource.homepageModules` 用法清理 | Surgeon-homepage | 🟢 | W3a `2db39755a`；31 文件 / -7,423 行 |
| W3-2 | Web 服务归档（D8）：源码迁 `attic/webservice/` + 删 `assets/web/**` + 删 APK 侧入口与 ktor 依赖 | Surgeon-web | 🟢 | W3b `ce91fb1b0`；11 源码 + 58 assets 移出 APK |
| W3-3 | 翻译域删除（D9） | Surgeon-misc | 🟢 | W3c `97fa73e8b`；16 文件 / -1,961 行 |
| W3-4 | 导航收敛 3 tab（D7a） | Integrator | 🟢 | W3a 内完成；旧顺序/默认页自动降级 |
| W3-5 | 热点收口 + 基线同步 + 独立验证 | Integrator/Verifier | 🟢 | 架构门通过；单测 1255/0；APK 60/59/66M |

## W4 · 数据层收口（串行）

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W4-1 | Room `version+1` + 新 migration（**不改历史 migration**）：DROP 或保留空表（`read_aloud_*`/`bgm_*`/`voice_pool*`/`cast_*`/`cloud_tts_engine`/`httpTTS`/rss/ai/homepage 表） | Migrator | 🟢 | `d167de175`；132 schema；40 表 @DeleteTable |
| W4-2 | `books.type` 僵尸数据清理（audio/image 移出书架） | Migrator | 🟢 | onPostMigrate：audio/image→text+notShelf；编辑只允许 text |
| W4-3 | 旧备份/主题包字段兼容（`httpTTS.json` 忽略而非报错；`showRss`/`navIconRss*` 保留读取） | Migrator | 🟢 | 备份/恢复停止读写 RSS/httpTTS/homepage 文件 |
| W4-4 | 迁移测试 + 真机升级安装验证 | Verifier | 🟡 | schema + 编译验证；真机升级未做 |

## W5 · 收口交付（串行）

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W5-1 | 基线最终复核（每条与实际一致）+ 清理 lint 基线中 33 条「文件存在但问题已消失」的过时条目 | Verifier | ⬜ | 架构门输出 + lint 报告 |
| W5-6 | 清理 AI/知识库专用 strings（4 语言：`ai_*`、`book_characters`、`character_network`、`plot_events` 等） | Scribe | 🟢 | `7fb79c9d1`；4 语言删除 931 键 |
| W5-2 | 文档同步：`README.md`、`AGENTS.md`、`docs/dev/feature-catalog.md`、`docs/index.md` | Scribe | 🟡 | 减法文档已同步；全仓 feature catalog 待 W2b 后一次更新 |
| W5-3 | `assembleAppDebug` / `assembleAppRelease` / `assembleAppNoR8` + APK 体积对比 | Verifier | 🟡 | debug 60/59/66M；NoR8 打包阶段 Gradle daemon 内存崩溃 |
| W5-4 | 真机手测清单 | Verifier + 用户 | ⬜ | `VERIFICATION.md` 手测表 |
| W5-5 | 交付说明：改动范围、取舍、验证、未验证风险 | Orchestrator | ⬜ | 交付文档 |

---

## 冲突与认领记录

| 时间 | agent | 文件/域 | 说明 |
|---|---|---|---|
| 2026-10-14 | Orchestrator | `docs/subtraction/**` | 规划文档（未被其他任务认领） |
| 2026-10-14 | Scout | 只读全仓 | TTS 域测绘，未写文件 |
| 2026-10-14 | Verifier | `.pi/tasks/**` | 基线编译输出 |
