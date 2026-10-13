# 决策地图 · 小说阅读器减法

> 本文件是唯一的决策事实源。用户逐项裁决后写入「裁决」列；agent 不得自行改变裁决。
> 侦察数据来源见 [`FINDINGS.md`](./FINDINGS.md)；验证方式见 [`VERIFICATION.md`](./VERIFICATION.md)。

图例：✅ 已裁决 ｜ 🟡 待用户裁决 ｜ ⛔ 阻塞其它决策 ｜ 🔁 可与同波次并行 ｜ ➡️ 必须串行

---

## 0. 终点形态快照（推荐目标）

**保留（小说阅读主干）**
书架与分组 · 书籍信息/详情 · 全局搜书 · 书源（管理/编辑/调试/校验/订阅导入） · 阅读页（文本渲染、翻页、手势） ·
目录与 TXT 目录规则 · 正文搜索 · 换源 · 缓存/下载 · 本地导入（TXT/EPUB/UMD/压缩包） · 阅读进度与阅读记录 ·
书签与高亮（含高亮标签规则） · 替换净化 · 阅读设置与主题 · 备份/恢复（含 WebDAV） · 文件关联打开 · 二维码导入导出 · 关于

**移除（非小说域）**
RSS 订阅 · 漫画 · 音频书与音频播放器 · AI（对话/摘要/自动分组/AI 配音） · 知识库与角色卡 · 章节评论 ·
首页自定义模块系统 · 翻译 · BGM 氛围音 · （待裁决）朗读/听书、字典、Web 服务与网页端

**一级导航收敛**：`Home / Bookshelf / Explore / Rss / My` → 推荐 `书架 / 发现 / 我的`（见 D7a）

---

## 1. 决策依赖图

```
D0 形态与分支策略
 └─ D1 内容类型边界（text/local/webFile 留；audio/image/video 删）⛔
     ├─ D3 漫画 ──────────┐
     ├─ D4a 音频书/播放器 ─┤
     └─ D11 数据兼容策略  │（依赖 D2/D3/D5/D6/D7 的"删表清单"）
                          │
D2 RSS ───────────────────┤
D4b/c/d 朗读·配音·BGM ────┤
D5 AI ──> D4c（AI 配音）──┤
D6 知识库/角色/评论 ──────┤
D7 首页模块 (+D7a 导航) ──┤
D8 Web 服务/网页端 ───────┤
D9 字典/翻译 ─────────────┤
D10 其他杂项 ─────────────┘
                          ↓
                     D11 数据层收口 ➡️
                     D12 构建护栏基线 ➡️
                     D13 验证策略（贯穿）
                     D14 波次与并行策略 ➡️
```

**必须串行的关键路径**：`D1 → (D3/D4a) → D11 → D12 → 最终验证`
**可并行推进**：D2 / D3 / D5 / D6 / D8 / D9 / D10 的**实施**（域内自治，热点文件由 Integrator 独占）

---

## 2. 决策点

### D0 · 减法形态与分支策略 🟡 ⛔

| 项 | 内容 |
|---|---|
| 选项 | **A** 原地删除（在 `main` 派生的 `subtract/novel-only` 分支上分波次提交）· **B** 原地删除 + Gradle product flavor 同时保留完整版 · **C** 另建精简仓库/永久分支 |
| 权衡 | A 最符合"做减法"，可回退靠分支与提交；B 能保留完整版但让构建、护栏基线、CI、测试矩阵翻倍，且 `verifyConfigArchitecture` 需按变体分别维护；C 丢历史、CI、issue 上下文 |
| 推荐 | **A**（工作分支 `subtract/novel-only`，每波次一个可回退提交） |
| 依赖·并行 | 阻塞全部；串行第一项 |

### D1 · 内容类型边界（决定漫画/音频/图片书源是否存续） 🟡 ⛔

| 项 | 内容 |
|---|---|
| 事实 | `constant/BookType.kt`：`video/text/updateError/audio/image/webFile/local/archive/notShelf`（位标志）；`constant/BookSourceType.kt`：`default(文本)/audio/image/file` |
| 选项 | **A** 保留 `text + local + webFile`（下载型书源本质是小说下载），删除 `audio/image/video` · **B** 只保留 `text + local`，连 `webFile` 也删 · **C** 保留全部类型，只删 UI 层 |
| 权衡 | A 语义最干净，但需改 `BookExtensions`/阅读入口分派/导入/搜索过滤/书架类型图标；B 会破坏"下载站书源"这类常见小说源；C 省事但留下死类型与僵尸数据 |
| 推荐 | **A**，并把 `BookSourceType` 收窄为 `default/file`（音频/图片书源在搜索与校验中直接忽略） |
| 影响面 | `BookType.kt`、`BookSourceType.kt`、`help/book/BookExtensions.kt`、`utils/ContextExtensions.kt`、`utils/FragmentExtensions.kt`、`MainActivity` 分派、`ui/book/import`、`ui/book/search`、`ui/book/info`、`data/dao/BookDao` |
| 依赖·并行 | ⛔ 阻塞 D3、D4a、D11 |

### D2 · RSS 订阅域 🟡

| 项 | 内容 |
|---|---|
| 事实 | 52 文件 / 8,649 行（`ui/rss` 27/5,896、`ui/main/rss` 3/686、entity 5/440、dao 4/270、repository 5/254、socket/api 135、资源若干）；一级导航含 `Rss` tab |
| 选项 | **A** 整域删除 · **B** 删 UI 保留后端/表 · **C** 保留 |
| 权衡 | A 收益最大（约 -8.6k 行）；需搬家 2 个被复用的共享类（`ui/rss/read/RssJsExtensions.kt`、`VisibleWebView.kt`，被 `WebJsExtensions`、`SourceLoginJsExtensions`、`VisibleWebViewCompose` 继承/引用）；B 无 UI 收益但保留 DB 面；C 与"纯小说"目标冲突 |
| 推荐 | **A**；**D2a** 共享类迁至 `help/webView/` 通用包（不改行为）；**D2b** `RuleSub`（规则订阅机制）**保留**——书源订阅 JSON 依赖它，仅删除 `RuleSubType.RSS_SOURCE` 分支与 `ImportRssSourceDialog` |
| 依赖·并行 | 🔁 可与 D3/D5/D6 同波次并行；热点文件（导航/DI/DB/strings/备份）交 Integrator |

### D3 · 漫画域 🟡

| 项 | 内容 |
|---|---|
| 事实 | 37 主文件 / 9,289 行（`ui/book/manga` 14/6,745、`data/repository/manga`、`domain/model/manga`、`help/coil/Manga*`、`ui/config/readMangaConfig`）+ 20 测试文件 / 3,104 行；**零反向依赖**（漫画目录外仅 DI 与路由引用） |
| 选项 | **A** 删（含 `BookType.image`、`BookSourceType.image`、`show_manga_ui` 开关） · **B** 只删渲染器保留类型位 · **C** 保留 |
| 权衡 | A 干净；**共享点必须先剥离**：`help/coil/CoverInterceptor.kt`、`CoverFetcher.kt`、`CoverExtras.kt` 同时服务小说封面；`ContextExtensions:66-107`、`FragmentExtensions:24-71` 承担类型分派；`PreferKey` 43 个漫画键 |
| 推荐 | **A**；**D3a**：本地图片目录导入与旧 View 漫画模式**一起删**（`LocalBook.importMangaDirectory`、`isComic`、`comicStyleSelect`、`ReadBook.kt:366,480,677-701`）——否则文本阅读器仍留漫画分支；**D3b**：`show_manga_ui` 与漫画偏好键彻底移除（不保留死开关） |
| 依赖·并行 | 依赖 D1；🔁 可与 D2/D5/D6 并行；热点＝`MainNavGraph`、Coil 三件套、`appModule`、`build.gradle.kts` 基线 |

### D4 · 多媒体域（音频书 / 朗读 / 配音 / BGM） 🟡 ⛔（需要用户拍板）

| 子项 | 事实规模 | 选项与权衡 | 推荐 |
|---|---|---|---|
| **D4a** 音频书与播放器 | `ui/book/audio` 4/1,641、`AudioPlayService`、`service/playback`、`core/ui/player`、`help/exoplayer`、media3 依赖 | 删（去掉一类"书"，并可能移除 media3-exoplayer / media3-datasource-okhttp 依赖）vs 留（用户若用音频书源） | **删**（与"小说文本阅读"无关，且与 D1 一致） |
| **D4b** TTS 朗读 / 听书 | `ui/book/readaloud` 44/15,147、`service/{TTS,Http}ReadAloudService`、`help/TTS.kt`、`help/readaloud/**` | 删（最大单块收益）vs 留（听小说＝阅读延伸） | ✅ **裁决（用户）：不留 → 整块删除**。这是本工程第二大的删除块，需专项侦察后执行（见 W2） |
| **D4c** 多人配音 / 角色语音 | `help/readaloud/cast/**`、`CastCharacter`、`VoicePoolEntities`、`VoiceEffectPreset`、`ui/book/read/sheet/AiCastDialogSheet.kt` | 保留 vs 删 | ✅ **裁决：删**（配音是 TTS 的产品化外壳，D4b 删除后无宿主；D4b 裁决的必然推论） |
| **D4d** BGM 氛围音 | `BgmPoolEntities`、`BgmSceneMark`、`BgmSceneStore` | 删 vs 留 | ✅ **裁决：删** |

| 依赖·并行 | 依赖 D1；D4c 依赖 D5；**D4b 删除使阅读页 `ReadBookController`/`ui/book/read` 内的朗读入口与 cast 标记成为热点**，与 `feature/reader` 的 `core/{cast,readaloud}` 交织，必须专项测绘（W2） |

### D5 · AI 功能 🟡 ⛔（与 D4c 联动）

| 项 | 内容 |
|---|---|
| 事实 | `ui/ai` 10/3,170 + `data/repository/ai` 1,507 + `ui/config/ai`（含 prompt/summary）+ 书架 `autoGroup` 1,353 + 8 entity + 5 DAO |
| 选项 | **A** 全删 · **B** 保留 provider/chat，删摘要、翻译、自动分组 · **C** 保留 |
| 权衡 | A 收益 ~6k 行并消除 API Key 配置面；牵动面：阅读页 `AiTextCleanSheet`、`AiReasoningModeButton`、`AiCastDialogSheet`（配音）、书架 AI 自动分组、书籍信息页 AI 摘要、设置 AI 分组；B 折中但保留密钥与网络面 |
| 推荐 | ✅ **裁决：A（全删）**，由 D4b=删 反推（AI 配音宿主已不存在；保留 AI 需保留 provider/密钥/网络面，与减法目标冲突） |
| 依赖·并行 | 阻塞 D4c；🔁 实施可与 D2/D3/D6 并行（AI 目录外引用点集中于阅读页/书架/设置） |

### D6 · 知识库 / 角色卡 / 章节评论 🟡

| 项 | 内容 |
|---|---|
| 事实 | `ui/book/knowledge` 4,568 行（人物、事件、知识条目）+ `BookKnowledge`、`BookChapterReview`、`CastCharacter`、`BookCastMemory`、`ChapterRoleAssignment`、`BgmSceneMark` 等 entity |
| 选项 | **A** 全删 · **B** 保留（阅读辅助） |
| 权衡 | A 收益 ~4.6k 行 + 多表；牵动 `BookInfoScreen`、阅读页入口；与 D4c/D5 有实体交叉（`CastCharacter`、`BookCastMemory` 属于配音域，需按域归属分开裁决） |
| 推荐 | **A**（`CastCharacter`/`BookCastMemory` 归 D4c 决定，不重复裁决） |
| 依赖·并行 | 🔁 可与 D2/D3/D5 并行 |

### D7 · 首页自定义模块系统（+ 导航收敛） 🟡 ⛔

| 项 | 内容 |
|---|---|
| 事实 | `ui/main/homepage` 19/4,983（模块系统、瀑布流、Banner、排行、自定义集合）；耦合点：`BookSource.homepageModules` 字段 + `BookSourceDao.flowHomepageModules()` + `BookSourceRepository` + `HomepageModule/CustomSet` entity/DAO + `HomeScreen.kt` 1,433 |
| 选项 | **A** 删模块系统（Home 页退化为简约书架/发现） · **B** 保留 |
| 权衡 | A 删掉 ~5k 行，但**必然改动 `BookSource` 实体与 DB**（字段删除影响 Room schema 与备份兼容）；Home 页 1,433 行需保留一个"简单首页"或直接合并到书架 |
| 推荐 | **A**，`BookSource.homepageModules` 字段保留在表内但删 UI/DAO 用法（降低 DB 风险，见 D11） |
| **D7a** 一级导航 | `Home/Bookshelf/Explore/Rss/My` → **`书架 / 发现 / 我的`**（Bookshelf 与 Home 合并；Explore 即书源发现；Rss 随 D2 移除） |✅ **裁决：接受 3 tab** |
| 依赖·并行 | 依赖 D11（字段/表处理）；实施可与其他域并行，但热点文件（导航、BookSource entity）归 Integrator |

### D8 · Web 服务与网页端 🟡

| 项 | 内容 |
|---|---|
| 事实 | `service/WebService.kt` 236 + `web/**` 6 文件（含 `BookSourceDebugWebSocket`、`BookSearchWebSocket`、`RssSourceDebugWebSocket`）+ `api/**` 5 controller / 819 行 + `modules/web`（独立 Vue3 工程，含 source/bookshelf 页） |
| 选项 | **A** 全删（Ktor + API + Vue 工程 + 依赖） · **B** 保留后端（书源调试/搜索 socket + 书源与替换规则 API），删除 RSS controller/socket 与 Vue 里的 RSS 页 · **C** 全保留 |
| 权衡 | A 一次性去掉 ktor-server 系列依赖与整个前端工程，收益大；但网页版书源编辑/调试是**书源功能的延伸**，删掉会削弱核心能力。B 保留核心工具、去掉 RSS 面；C 不解决"减法" |
| 裁决 | ✅ **用户：源码保留，APK 不保留** |
| 落地方式（已核实事实后择优） | **归档保留 + 从编译路径移除**。核实结论：独立 Gradle 模块**不可行**——`api/controller/BookController.kt` 等服务端直接调用 `appDb.*Dao`（:app 内部类型），新模块只能反向依赖 `:app`，而 `:app` 是 application 模块不可被依赖；为它引入抽象层违背 AGENTS.md「不建无调用方抽象」与减法目标。<br>**故采用**：① 删 `app/src/main/assets/web/**`（`pnpm build` 经 `modules/web/scripts/sync.js` 同步进来的前端产物，直接占 APK 体积）；② `app/src/main/java/io/legado/app/{web/**,api/controller/**,api/ReturnData.kt}`、`service/WebService.kt` 迁入归档目录 `attic/webservice/`（保留原包结构 + `attic/README.md` 说明恢复步骤）；③ 删 APK 侧引用与入口：`MyScreen` Web 服务卡片、`ui/config/otherConfig` 的 autostart/wakelock 项、`MainActivity` 启动与前台服务、`AndroidManifest` 的 service 声明、`libs.ktor.server.*` 依赖、架构门基线中的 `WebService.kt` 条目；④ `modules/web`（Vue3 源码）**原位保留**，不参与 APK 构建。<br>**保留（不属于 Web 服务）**：`api/ShortCuts.kt`（桌面快捷方式）、`api/ReaderProvider.kt`（内容提供者）——需在执行时逐个核对归属 |
| 取舍 | 归档源码**不参与编译**，会失去 CI 保护（已知代价）；换来 APK 干净与零架构债务。恢复成本＝按 `attic/README.md` 迁回＋恢复入口 |
| 依赖·并行 | 与 D2 有交集（RSS controller/socket 随 D2 删）；🔁 可与 D2 同波次 |

### D9 · 字典与翻译 🟡

| 项 | 内容 |
|---|---|
| 事实 | 字典：`ui/dict` 8/1,329 + `DictRule` entity + `dict-rule` 文档；翻译：`ui/config/translation` 174 行 + `TranslationCache` entity |
| 选项 | 字典 **留**（阅读划词查询属阅读辅助，MyScreen 有"字典规则"入口）vs **删**；翻译 **删**（弱相关）vs **留** |
| 权衡 | 两者规模都不大，删除收益低；翻译与 AI 翻译可能有耦合 |
| 推荐 | 字典 **留**、翻译 **删**（若 D5=A 全删 AI，翻译一并确认） |
| 依赖·并行 | 🔁 独立，可任意波次 |

### D10 · 其他杂项 🟡

| 子项 | 事实 | 推荐 |
|---|---|---|
| 桌面小组件 | `ui/widget` 207 文件 / 34,195 行（**多数是通用 Compose 组件，不可整删**；小组件相关只是一部分） | 保留（阅读进度小组件属小说相关）；需单独测绘小组件子集 |
| 文件关联 / 外部打开 / 导入书源 | `ui/association` 24/3,357（含 `ImportRssSource*` 471 行随 D2 删） | 保留（去除 RSS 部分） |
| 二维码 | `ui/qrcode` 4/118 | 保留（书源/配置分享） |
| 欢迎页、关于 | `ui/welcome`、`ui/about` 8/1,542 | 保留（`AboutScreen` 需删除已删功能的相关说明） |
| 书源登录 | `ui/login` 5/1,041（`SourceLoginJsExtensions` 继承 `RssJsExtensions`！） | 保留（书源登录必需）+ 按 D2a 改造父类 |
| 文件管理 | `ui/file` 3/282 | 保留（本地小说导入） |

### D11 · 数据兼容与迁移策略 🟡 ➡️（必须最后做）

| 项 | 内容 |
|---|---|
| 决策一：被删域的表 | **A** DB `version+1` + `DROP TABLE`（干净，丢历史数据） · **B** 保留空表不删（零风险，留下死 schema） |
| 决策二：`books.type` 僵尸数据 | 删除 `audio/image` 类型后，旧书架里的图片/音频书会变成打不开的条目 → **A** 迁移时把这类书标记 `notShelf`/移出书架 · **B** 保留可见并提示不支持 |
| 决策三：旧备份/主题包字段 | `BackupConfig`、`ThemeExportData`、`AppShellSettings`（`showRss`、`navIconRss*` 等）→ **A** 保留字段读取（收下即忽略）以保证旧备份可恢复 · **B** 删除字段（破坏旧备份/旧主题包导入） |
| 推荐 | 决策一 **A**（被删功能的数据无保留价值）；决策二 **A**（避免"打不开的书"）；决策三 **A**（兼容优先，字段随后续大版本清理） |
| 影响文件 | `data/AppDatabase.kt`、`data/DatabaseMigrations.kt`、`help/storage/{Backup,Restore,BackupConfig}.kt`、`help/config/*`、`constant/PreferKey.kt`、迁移测试 |
| 依赖·并行 | 必须在 D2/D3/D5/D6/D7 的删除清单冻结后执行；➡️ 串行 |

### D12 · 构建护栏基线收口 🟡 ➡️

| 项 | 内容 |
|---|---|
| 事实 | 根 `build.gradle.kts:216-302` `VerifyConfigArchitectureTask` 三张基线表；**双向棘轮**（计数下降必须下调），**基线条目文件被删即硬失败**；所有 `assemble*`/`compile*` 依赖它 |
| 已知受影响键 | `MainNavGraph.kt`(2)、`ContextExtensions.kt`(12)、`SearchViewModel.kt`(3)、`ImportRssSourceDialog.kt`(1)、`RssJsExtensions.kt`(8)、`RssArticlesCompose.kt`(1)、`WebService.kt`(2)、`CloudTtsViewModel.kt`(13)、`OtherConfigViewModel.kt`(1)、`App.kt`(3)、`LocalConfig.kt`(3)、`Restore.kt`(2) … |
| 推荐 | 每个波次**同步**修改基线（不积压），删除文件对应的键直接移除；数值下降按实际下调 |
| 依赖·并行 | ➡️ 与每个波次的删除同步进行，最后一波统一复核 |

### D13 · 验证策略 🟡（贯穿）

| 层 | 手段 |
|---|---|
| 编译 | `:app:compileAppDebugKotlin`（每波必过） |
| 单测 | `testAppDebugUnitTest`（注意删除/改写 20 个漫画测试、`AppShellSettingsMappingTest`） |
| 架构门 | `verifyConfigArchitecture`（同步基线） |
| 静态 | `lintAppDebug`、`git diff --check` |
| 打包 | `assembleAppDebug`（每波）/ `assembleAppRelease`（W4） |
| **反证** | Verifier 用 grep 证明"被删符号零残留引用"（`BookType.image`、`Rss*`、`homepageModules`…），并抽查被删目录外的引用点 |
| 真机 | 手测清单（启动/书架/搜索/书源管理/发现/阅读翻页/目录/缓存/换源/TXT 导入/备份恢复/小组件） |
| 未验证项 | 必须在 `VERIFICATION.md` 显式列出（默认：真机 UI 行为、TTS 真机朗读、Web 服务局域网访问） |

### D14 · 波次与并行策略 🟡 ➡️

| 波次 | 内容 | 并行 | 出口 |
|---|---|---|---|
| W0 | 基线冻结：分支、全量验证跑通、记录基线数字、TTS/朗读域专项侦察 | 串行 | 验证证据入档 |
| W1 | 共享点剥离 + 叶节点域：RSS（D2）、漫画（D3）、AI（D5）、知识库/评论（D6） | 4 路并行（域内），热点 Integrator 收口 | 编译/单测/架构门通过 |
| W2 | **朗读大块**：TTS/听书（D4b）+ 配音（D4c）+ BGM（D4d）+ 音频书与播放器（D4a） | 1–2 路（与阅读页深度耦合，热点集中） | 同上 |
| W3 | 首页模块（D7）、Web 归档（D8）、翻译（D9）、导航收敛（D7a） | 2–3 路并行 | 同上 |
| W4 | 数据层收口：DB 版本+迁移+旧数据清理+备份兼容（D11） | 串行 | 迁移测试通过 |
| W5 | 基线复核、文档同步（README/AGENTS/feature-catalog）、release 打包、真机手测 | 串行 | 全绿 + 手测勾完 |

**热点文件（同一时刻只能一个 writer，必须串行）**：
`ui/main/{MainNavGraph,MainNavKey,MainNavigator,MainIntent,MainDestination,MainScreen,MainActivity,MainViewModel,MainContract,MainBookPageNavigation}.kt`、
`di/{appModule,appDatabaseModule}.kt`、`data/{AppDatabase,DatabaseMigrations}.kt`、`constant/PreferKey.kt`、
`help/storage/{Backup,Restore,BackupConfig}.kt`、`build.gradle.kts`（基线）、`res/values*/strings.xml`、`AndroidManifest.xml`

---

## 3. 待裁决汇总（请逐项给出「同意 / 改成 …」）

> 图例：**✅用户** = 用户已明确裁决；**✅默认** = 按推荐自动生效（用户授权自主推进，可随时否决）；🟡 = 仍待裁决

| ID | 决策 | 裁决 | 状态 |
|---|---|---|---|
| D0 | 减法形态与分支：原地删除，分支 `subtract/novel-only`，分波次提交 | 同意推荐 | ✅默认 |
| D1 | 内容类型边界：留 `text/local/webFile`，删 `audio/image/video`；`BookSourceType` 收窄为 `default/file` | 同意推荐 | ✅默认 |
| D2 | RSS：整域删除；`RuleSub` 书源订阅机制保留；共享 WebView/JS 类迁 `help/webView` | 同意推荐 | ✅默认 |
| D3 | 漫画：整域删除（含类型位、本地图片导入、漫画偏好键、20 个测试） | 同意推荐 | ✅默认 |
| D4a | 音频书与播放器：删除 | 同意推荐 | ✅默认 |
| D4b | TTS 朗读/听书 | **不留，整块删除** | ✅用户 |
| D4c | 多人配音/角色语音 | 删（D4b 的必然推论） | ✅用户推论 |
| D4d | BGM 氛围音 | 删 | ✅默认 |
| D5 | AI：全删 | 同意推荐（且 D4b 已删，无保留理由） | ✅默认 |
| D6 | 知识库/角色卡/评论：全删 | 同意推荐 | ✅默认 |
| D7 | 首页自定义模块：删；`BookSource.homepageModules` 字段留表内 | 同意推荐 | ✅默认 |
| D7a | 一级导航收敛为 `书架 / 发现 / 我的` | **接受 3 tab** | ✅用户 |
| D8 | Web 服务：**源码保留、APK 不保留** → 归档 `attic/webservice/` + 删 assets/web 产物与全部 APK 侧入口 | 用户定范围，方式经核实后择优 | ✅用户 |
| D9 | 字典留、翻译删 | 同意推荐 | ✅默认 |
| D10 | 小组件/关联/二维码/欢迎/关于/登录/文件管理 全留（RSS 部分随 D2 删） | 同意推荐 | ✅默认 |
| D11 | DB `version+1` + DROP 被删表；旧图片/音频书移出书架；旧备份/主题字段保留读取兼容 | 同意推荐 | ✅默认 |
| D12 | 基线收口：每波同步修改 | 同意推荐 | ✅默认 |
| D13 | 验证：五层验证 + 反证检索 + 真机手测 | 同意推荐 | ✅默认 |
| D14 | 波次：W0–W5（TTS 独立成波） | 调整后生效 | ✅默认 |

> 若对任一「✅默认」有异议，直接说"D<编号> 改成 …"，未执行的波次按其重新排。

---

## 4. 预期收益（按默认推荐估算）

| 域 | 删除文件 | 删除行数（主源） | 备注 |
|---|---|---|---|
| RSS | ~52 | ~8,649 | 含资源/字符串 |
| 漫画 | 37 + 20 测试 | 9,289 + 3,104 | 零反向依赖 |
| AI | ~34 | ~6,030（含 autoGroup 1,353） | +8 entity/5 DAO |
| TTS 朗读/听书（D4b） | 44+ | 15,147 | +`service/*ReadAloud*`、`help/readaloud/**`、`help/TTS.kt`、`feature/readaloud` |
| 配音/BGM/音频（D4c/d + D4a） | ~14 | ~3,500（估） | +`CastCharacter`/`VoicePool*`/`Bgm*` entity；可能移除 media3 依赖 |
| 知识库/角色/评论 | ~12 | ~4,700 | 实体交叉需分域裁决（已随 D4c/D6 统一删） |
| 首页模块 | 19 | 4,983 | 牵动 `BookSource` 字段 |
| Web 服务（D8，归档而非删除） | ~12 | ~1,055 | 源码移入 `attic/webservice/`，另删 `assets/web/**` 前端产物 |
| 翻译 | 3 | 174 | +TranslationCache |
| **合计** | **~230** | **~56,000+** | 不含测试与资源；APK 侧预计同时移除 ktor-server 系列与可能的 media3 依赖 |

---

## 5. 风险登记

| 风险 | 影响 | 缓解 |
|---|---|---|
| 共享 Coil/WebView/JS 类被误删 | 小说封面、书源 JS、登录功能静默损坏 | 每域先做"共享点剥离"独立提交，再删域 |
| 架构门基线未同步 | CI 直接红（且 `compile*` 依赖它，会伪装成"编译失败"） | D12 纳入每波出口条件 |
| DB 变更破坏升级安装 | 老用户升级崩溃/数据异常 | W3 专项迁移测试 + 升级安装验证 |
| 旧备份/主题包导入失败 | 用户资产丢失 | D11 决策三选兼容读取 |
| 并行 agent 改同一热点文件 | 冲突、半截状态 | 热点文件单 writer + `claim_files` |
| "验证不足即宣布完成" | 交付不可信 | Verifier 独立于执行者；未验证项显式登记 |
