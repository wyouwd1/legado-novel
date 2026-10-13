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
| W1-2 | 漫画域（D3，含 20 测试、类型位、偏好键） | Surgeon-manga（主控执行） | 🟢 | 删除 **55 文件 / -13,968 行**；编译 6 轮收敛 623→**0** 错；完整验证集 **BUILD SUCCESSFUL**；单测 **1578/0**；APK arm64 −2.7MB；详见 `VERIFICATION.md` §7.2 |
| W1-3 | AI 域（D5，含 autoGroup、阅读页 AI sheet） | Surgeon-ai | ⬜ | 侦察地图已入 `TASK-BOARD.md` §W1-3；难点：`ReadAiDelegate`/`AiTextFactoryUseCase` 被正文链引用 |
| W1-4 | 知识库/评论（D6） | Surgeon-knowledge | ⬜ | 同上 |
| W1-5 | **热点收口**：导航 / DI / AppDatabase 注册 / strings / PreferKey / 基线 | Integrator | ⬜ | 编译 + 架构门通过 |
| W1-6 | 独立验证：编译、单测、架构门、反证 grep | Verifier | ⬜ | `VERIFICATION.md` W1 段 |

### W1-3 执行细则（AI 域，2026-10-14 侦察）

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
| W2-S0 | 改测试硬约束（`MainNavigatorReadAloudTest.kt:77`、`ReadBookDomainSplitBoundaryTest.kt:49-50,321`）+ 基线下调 | Surgeon-readaloud | ⬜ | 测试可编译 |
| W2-S1 | 摘引用：`ui/book/read/**` → `model/{ReadBook,CacheBookModel,ReadAloud*}.kt` → `ui/main/**` → `di/appModule.kt` | Integrator（串行） | ⬜ | 编译通过 |
| W2-S2 | 剥共享核心：`feature/reader/**` 分页/绘制链路 + `CastMarkers` 收敛为纯文本标记 | Surgeon-reader | ⬜ | 编译 + 阅读行为不变 |
| W2-S3 | 删叶节点：`help/{TTS,MediaHelp,exoplayer,readaloud}/**`、`ui/book/{readaloud,audio}/**`、4 个 service、`model/ReadAloud*`、`domain/model/readaloud/**`、`feature/readaloud/**`、dao/entity/repository/gateway | Surgeon-readaloud | ⬜ | 编译通过 |
| W2-S4 | 清接线：Manifest、NotificationId、AppConst、IntentAction、PreferKey、四语言 strings、MediaButtonReceiver | Integrator | ⬜ | 编译 + lint 通过 |
| W2-S5 | 数据层：entities/dao/repository/gateway/usecase + `appDatabaseModule` 绑定（新 migration 见 W4） | Migrator | ⬜ | 编译通过 |
| W2-S6 | 依赖与门禁：media3 是否可删（取决于 `AnalyzeUrl.kt:816`）、`BookType.audio` 类型位另立一步 | Integrator | ⬜ | 依赖树证据 |
| W2-V | 独立验证：编译、单测、架构门、反证 grep、朗读残留检索 | Verifier | ⬜ | `VERIFICATION.md` W2 段 |

## W3 · 外围域（2–3 路并行）

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W3-1 | 首页自定义模块（D7）+ `BookSource.homepageModules` 用法清理 | Surgeon-homepage | ⬜ | 编译通过 |
| W3-2 | Web 服务归档（D8）：源码迁 `attic/webservice/` + 删 `assets/web/**` + 删 APK 侧入口与 ktor 依赖 | Surgeon-web | ⬜ | 编译通过 + APK 无 assets/web |
| W3-3 | 翻译域删除（D9） | Surgeon-misc | ⬜ | 编译通过 |
| W3-4 | 导航收敛 3 tab（D7a） | Integrator | ⬜ | 编译 + 手测 |
| W3-5 | 热点收口 + 基线同步 + 独立验证 | Integrator/Verifier | ⬜ | 架构门通过 |

## W4 · 数据层收口（串行）

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W4-1 | Room `version+1` + 新 migration（**不改历史 migration**）：DROP 或保留空表（`read_aloud_*`/`bgm_*`/`voice_pool*`/`cast_*`/`cloud_tts_engine`/`httpTTS`/rss/ai/homepage 表） | Migrator | ⬜ | 迁移代码 + 测试 |
| W4-2 | `books.type` 僵尸数据清理（audio/image 移出书架） | Migrator | ⬜ | 迁移测试断言 |
| W4-3 | 旧备份/主题包字段兼容（`httpTTS.json` 忽略而非报错；`showRss`/`navIconRss*` 保留读取） | Migrator | ⬜ | 备份恢复测试 |
| W4-4 | 迁移测试 + 真机升级安装验证 | Verifier | ⬜ | 测试输出 + 安装记录 |

## W5 · 收口交付（串行）

| # | 任务 | 认领 | 状态 | 证据 |
|---|---|---|---|---|
| W5-1 | 基线最终复核（每条与实际一致） | Verifier | ⬜ | 架构门输出 |
| W5-2 | 文档同步：`README.md`、`AGENTS.md`、`docs/dev/feature-catalog.md`、`docs/index.md` | Scribe | ⬜ | diff |
| W5-3 | `assembleAppDebug` / `assembleAppRelease` / `assembleAppNoR8` + APK 体积对比 | Verifier | ⬜ | 构建输出 |
| W5-4 | 真机手测清单 | Verifier + 用户 | ⬜ | `VERIFICATION.md` 手测表 |
| W5-5 | 交付说明：改动范围、取舍、验证、未验证风险 | Orchestrator | ⬜ | 交付文档 |

---

## 冲突与认领记录

| 时间 | agent | 文件/域 | 说明 |
|---|---|---|---|
| 2026-10-14 | Orchestrator | `docs/subtraction/**` | 规划文档（未被其他任务认领） |
| 2026-10-14 | Scout | 只读全仓 | TTS 域测绘，未写文件 |
| 2026-10-14 | Verifier | `.pi/tasks/**` | 基线编译输出 |
