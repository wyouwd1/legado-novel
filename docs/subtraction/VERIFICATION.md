# 验证矩阵（Verification）

> 规则：**证据优先**。任何"已完成"必须有可复现命令 + 关键输出片段；无法执行的一律登记到「未验证项」。
> Verifier 不得由执行该波次的 Surgeon 兼任（[`README.md`](./README.md) 独立验证原则）。

---

## 1. 每波出口命令（必须全部执行并记录）

```powershell
git diff --check

# Kotlin 快速编译（第一道门）
.\gradlew.bat :app:compileAppDebugKotlin

# 当前主验证集（AGENTS.md 定义）
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
```

`verifyConfigArchitecture` 在本仓库中由**所有** `assemble*`/`compile*` 任务前置依赖
（根 `build.gradle.kts:281`），因此它一旦报红，表象可能是"编译失败"——先看它的输出。

### 1.1 本机环境事实（W0 实测，2026-10-14）

| 项 | 事实 | 结论 |
|---|---|---|
| JDK | OpenJDK 21.0.12 | ✓ 符合 AGENTS.md |
| Android SDK（原） | `/home/handx-aijiu/android-sdk`，属其他用户，**不可写**、无 sudo | 不可直接用 |
| 缺组件 | `platforms;android-37.1`（项目 `compileSdk=37` + `compileSdkMinor=1`，注释明确 android-37.0 会被 `checkAarMetadata` 拒绝） | 已补装 |
| 现行 SDK 方案 | `~/android-sdk`：`platforms` 实拷（473M），其余（build-tools/platform-tools/cmdline-tools/cmake/licenses/ndk）**符号链接**到原目录（硬链接被 `protected_hardlinks` 拦截） | ✓ |
| `local.properties` | 已写 `sdk.dir=/home/zx/android-sdk`（在 `.gitignore:3`，不入版本控制） | ✓ |
| 网络 | 直连 `services.gradle.org` 超时；代理 `127.0.0.1:7890` 可用 | 构建需 `JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"` |
| Gradle | 9.8.0（首次拉取 ~11 分钟）；无需 NDK | ✓ |

**复现命令**：
```bash
export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
./gradlew :app:compileAppDebugKotlin --console=plain
```

W4 追加：

```powershell
.\gradlew.bat assembleAppRelease
.\gradlew.bat assembleAppNoR8
pnpm --dir modules/web build          # 仅当 D8 保留网页端
```

### 1.2 架构门实测记录（W2，2026-10-14）

**触发场景**：W2 删除朗读域 225 文件后首次编译，`exit=1` 但 `grep -c '^e: '` 为 **0** —— 即“失败但无 Kotlin 错误”。日志尾部：

```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':verifyConfigArchitecture' (registered in build file 'build.gradle.kts').
> 配置架构护栏失败:
  app/src/main/java/io/legado/app/ui/book/readaloud/cloudtts/CloudTtsViewModel.kt: 文件已移除，请删除 DAO 直连基线
```

**结论**：架构门对**基线键 ↔ 文件存在性**做硬校验；删除被跟踪文件后必须同步删键。
删掉该键后重编译，错误数从 0 跳到实际的 **466** —— 即架构门先把编译挡住了，误判成
“编译无错却失败”会浪费一整轮。

**基线值变化（本工程实测）**：

| 时点 | legacyPreferenceCallBaseline | legacyDaoInjectionBaseline | legacyUiDaoAccessBaseline |
|---|---|---|---|
| W0 基线 | 23 键 / 74 | 2 键 / 13 | 11 键 / 30 |
| W1-0 后 | 22 键 / 73 | 2 / 13 | 11 / 30 |
| W1-3 后 | 22 键 / 73 | 2 / 13 | 8 键 / 20 |
| W2（删 `CloudTtsViewModel` + `MediaButtonReceiver` 键） | **21 键 / 71** | **1 键 / 0** | 8 / 20 |

> `legacyDaoInjectionBaseline` 剩下的 `ReadBookViewModel.kt to 0` 是**0 值哨兵**，不是遗漏。

**诊断命令**（列出指向不存在文件的基线键，应在每波提交前跑）：

```bash
python3 - <<'PY'
import re, os
src = open('build.gradle.kts', encoding='utf-8').read()
for name in ['legacyPreferenceCallBaseline', 'legacyDaoInjectionBaseline', 'legacyUiDaoAccessBaseline']:
    m = re.search(name + r'\.set\(\s*mapOf\((.*?)\n\s*\)', src, re.S)
    if not m:
        print(name, '未找到'); continue
    keys = re.findall(r'"([^"]+)"\s+to\s+(\d+)', m.group(1))
    missing = [(k, v) for k, v in keys if not os.path.exists(os.path.join('app/src/main/java', k))]
    print(f"{name}: {len(keys)} 键 / 总计 {sum(int(v) for _, v in keys)}；缺失文件 {len(missing)}")
    for k, v in missing:
        print('   缺失:', k, '->', v)
PY
```

**任务名坑**：`verifyConfigArchitecture` 是**根项目**任务。写成 `:app:verifyConfigArchitecture` 会直接
“Selection failed / task not found”，而非报护栏错误。

---

## 2. 基线快照（W0 填写，后续每波对照）

| 指标 | W0 基线 | W1 后 | W2 后 | W3 后 | W4 后 |
|---|---|---|---|---|---|
| `testAppDebugUnitTest` 用例数 / 失败 | **1705 / 0**（309 个 XML / 311 个测试源文件） | | | | |
| `legacyPreferenceCallBaseline` 条目 / 合计计数 | **23 / 74** | | | | |
| `legacyDaoInjectionBaseline` 条目 / 合计计数 | **2 / 13** | | | | |
| `legacyUiDaoAccessBaseline` 条目 / 合计计数 | **11 / 30** | | | | |
| `app/src/main/java` 文件数 / 行数 | **1,752 / 335,597** | | | | |
| `lintAppDebug` | 通过（187 warnings / 26 hints；基线屏蔽 29 errors / 263 warnings） | | | | |
| `assembleAppDebug` 产物大小 | 71.7/71.2/78.2 MB（arm64 / armv7 / universal，详见 §2.1） | | | | |

### 2.1 W0 备注：`packageAppDebug` 首次失败（已澄清）

第一次完整验证集结果：`verifyConfigArchitecture` ✓、`testAppDebugUnitTest` ✓（1705/0）、`lintAppDebug` ✓、
**`:app:packageAppDebug` ✗**（`PackageAndroidArtifact$IncrementalSplitterRunnable`）。

**澄清后的事实**：APK **实际已产出**（ABI 分包，位于 `app/build/outputs/apk/app/debug/`，非我最初查的 `apk/debug/`），
单独重跑 `:app:packageAppDebug` 为 `BUILD SUCCESSFUL`（dex 已缓存，5s）。
推测为首次构建时 dex 分包阶段的瞬时失败（非代码问题，当时尚未删任何代码）→ 正在做第二次完整集确认可重复性。

**W0 基线 APK 体积（2026-10-14 16:21）**——这是后续减法体积收益的对照基准：

| 产物 | 大小 |
|---|---|
| `app-app-arm64-v8a-debug.apk` | 71,670,349 B（≈68.3 MiB） |
| `app-app-armeabi-v7a-debug.apk` | 71,210,171 B（≈67.9 MiB） |
| `app-app-universal-debug.apk` | 78,241,598 B（≈74.6 MiB） |

---

## 3. 反证检索（Verifier 必做：证明被删符号零残留）

> 目的：不是"编译过"就算完，而是证明删除是**彻底**的（无死代码、无死表、无死偏好、无死字符串依赖）。

| 域 | 命令（在 `app/src/main/java` 下） | 期望 |
|---|---|---|
| RSS | `grep -rn "Rss\|rssSource\|rssArticle" --include=*.kt .` | 仅剩裁决保留项（如 `RuleSub` 的书源订阅路径、保留的 JS 绑定） |
| 漫画 | `grep -rni "manga\|BookType.image\|isComic\|comicStyle" --include=*.kt .` | 零命中（或仅注释/历史说明） |
| 类型位 | `grep -rn "BookType.audio\|BookType.image\|BookType.video" --include=*.kt .` | 零命中 |
| 书源类型 | `grep -rn "BookSourceType.audio\|BookSourceType.image" --include=*.kt .` | 零命中（D1=A 时） |
| AI | `grep -rn "io.legado.app.ui.ai\|io.legado.app.data.repository.ai\|AiProviderProfile\|AiChatConversation" --include=*.kt .` | 零命中（D5=A 时） |
| 首页模块 | `grep -rn "homepageModules\|HomepageModule\|HomepageCustomSet" --include=*.kt .` | 仅剩 DB 字段声明（D7 决策） |
| 音频/播放 | `grep -rn "AudioPlayService\|media3\|ExoPlayer" --include=*.kt .` | 零命中（D4a=删时） |
| BGM | `grep -rn "BgmScene\|BgmPool" --include=*.kt .` | 零命中（D4d=删时） |
| DB 表 | `grep -n "entities = \[" -A 60 data/AppDatabase.kt` | 与删除清单一致 |
| 偏好键 | `grep -rn "PreferKey\." --include=*.kt . \| sort -u` 对照 `constant/PreferKey.kt` | 无未定义引用、无残留死键 |
| 基线 | `grep -n "Baseline" -A 30 build.gradle.kts` 对照实际文件 | 每个键对应文件**真实存在**且计数一致 |
| 字符串 | `grep -rn "R.string.rss_\|R.string.manga_\|R.string.ai_" --include=*.kt .` | 零命中或已裁决保留 |
| 资源 | `ls app/src/main/res/drawable* \| grep -i "rss\|manga"` | 零命中 |

**残留处理规则**：命中项必须是**裁决明确保留**的，否则视为该波次未完成。

---

## 4. 真机手测清单（W4，需设备）

| # | 路径 | 期望 | 结果 |
|---|---|---|---|
| 1 | 冷启动 | 无崩溃；书架加载、封面正常显示 | ⬜ |
| 2 | 搜书 → 加书架 → 打开 → 翻页 → 目录跳转 | 全文正常、进度保存 | ⬜ |
| 3 | 换源 | 切换书源后正文正确 | ⬜ |
| 4 | 缓存下载 N 章 → 删除缓存 | 任务正常、文件清理 | ⬜ |
| 5 | 书源管理：新增/编辑/调试/校验 | 规则调试页可用 | ⬜ |
| 6 | 发现页（Explore） | 书源探索出书 | ⬜ |
| 7 | 本地 TXT / EPUB 导入 → 阅读 | 目录与正文正确 | ⬜ |
| 8 | 替换净化 | 规则生效 | ⬜ |
| 9 | 阅读设置 / 主题切换 / 自定义主题导入 | 生效、无残留 RSS/漫画设置项 | ⬜ |
| 10 | 备份 → 恢复（含**旧版本**备份文件） | 成功恢复，被删域字段被忽略不报错 | ⬜ |
| 11 | 书签 / 高亮 / 阅读记录 | 增删查正常 | ⬜ |
| 12 | Web 服务（若 D8 保留） | 局域网访问、书源调试 socket 可用 | ⬜ |
| 13 | 桌面小组件（若 D10 保留） | 显示与跳转正常 | ⬜ |
| 14 | **升级安装**（覆盖装有旧数据的版本） | 不崩溃；旧图片/音频书按 D11 预期处理 | ⬜ |
| 15 | 朗读（若 D4b 保留） | 朗读、切章、停止正常 | ⬜ |

---

## 5. 未验证项登记（每波更新）

| 波次 | 未验证项 | 原因 | 计划消解 |
|---|---|---|---|
| W0 | 真机行为 | 无设备 | W4 |
| — | 待填 | — | — |

---

## 6. 证据格式约定

每次验证在 `TASK-BOARD.md` 对应行记：

```
证据：<命令> → <结果>（<关键输出片段/路径>）
例：证据：.\gradlew.bat :app:compileAppDebugKotlin → BUILD SUCCESSFUL（1m42s，0 warning 新增）
```

---

## 7. 波次执行记录

### 7.1 W1-1 · RSS 域删除（2026-10-14）

**规模**：79 文件 / **-7,698 行**（13 行新增，含 `RuleSub` 包名迁移与 `entryProvider<NavKey>` 显式泛型）。

| 类别 | 内容 |
|---|---|
| 删除源文件 | 35 个：`ui/rss/{article,favorites,read,source}/**`、`ui/main/rss/**`、`model/rss/**`、`api/controller/RssSourceController.kt`、`web/socket/RssSourceDebugWebSocket.kt`、`help/source/RssSourceExtensions.kt`、`ui/association/ImportRssSource*` |
| 保留并迁移 | `RuleSub*` → `ui/book/source/subscription/`（书源订阅）；删 `RuleSubType.RSS_SOURCE` 及其 UI 分支（D2b 裁决） |
| 删除资源 | `assets/defaultData/rssSources.json`、`assets/web/help/md/SourceMRssHelp.md`、5 个 drawable、`arrays.xml` 的 `rss_type` 与 `default_home_page(_value)` 的 rss 项、`pref_config_theme.xml` 的 `showRss` 项 |
| 代码手术 | `MainNavGraph` 1971→1821、`MainIntent` 401→347、`MainActivity`、`MainNavigator`、`MainScreen`、`MainNavKey`、`MainDestination`、`MainContract`、`MainViewModel`、`Debug` 423→285、`ReaderProvider`、`KtorServer`、`DefaultData` 132→116、`AppIcons`、`SourceIcon`、`ImageUtils`、`ThemeConfig(ViewModel)`、`EditThemeSheet`、`SourceLoginContract/ViewModel`、`BaseAssociationViewModel`、`DebugScenarioActivity`（debug 源集）、`appModule` |
| **保留兼容**（D11 决策三） | `AppShellSettings.showRss/navIconRss*`、`ThemeExportData` 同名字段（旧备份/主题包可恢复）；仅改 `mainNavigationOrder` 默认值为 `home,bookshelf,explore,my`；`R.string.{rss_source,rss_star,show_rss}` 因 `BackupConfig` 引用保留至 W4 |
| 数据层 | `Rss*` entity/dao/repository **保留至 W4** 统一迁移（避免每删一域就动一次 DB 版本） |

**架构门两次拦截并修复**（实证 D12 机制）：删除已移除文件的基线键（`RssJsExtensions.kt`、`ImportRssSourceDialog.kt`、`RssArticlesCompose.kt`）。

**错误收敛**：首轮 802 错 → 二轮 20 错 → 三轮 **0 错**。

**隐藏坑（已修）**：`MainNavGraph.mainEntryProvider` 的 `entryProvider { }` 原依赖被删的 RSS 路由类型（直接实现 `NavKey`）才能推断出 `NavKey`；删除后推断收窄为 `MainRoute` → 显式写 `entryProvider<NavKey> { }`。

**反证检索**（`grep -rn "Rss\|rss" app/src/main app/src/test app/src/debug`）仅剩：
W4 待删的数据层（`data/{dao,entities,repository}/Rss*`）、`appDatabaseModule` 的 DAO 注册、`BackupConfig` 的表名/偏好名、`MainNavKey.ROUTE_RULE_SUB = "rss/rule_sub"`（书源订阅路由，功能保留）。

### 7.2 W1-2 · 漫画域删除（2026-10-14）

**规模**：95 文件变更 / 删除 55 文件（36 主源码 + 19 测试）/ **-13,968 行**。

| 类别 | 内容 |
|---|---|
| 删除源文件（36） | `ui/book/manga/**`(14)、`help/coil/Manga*.kt`(8)、`data/repository/manga/**`(5)、`domain/{gateway,model,reader}/**Manga*`(6)、`ui/config/readMangaConfig/**`、`data/model/MangaFooterConfig.kt`、`data/repository/MangaSettingsRepository.kt`、`help/glide/LegadoDataUrlLoader.kt` |
| 删除测试（19） | `help/coil/Manga*Test`(7)、`ui/book/manga/*Test`(5)、`data/repository/manga/*Test`(2)、`MangaSettingsMappingTest`、`LocalMangaRegionRecoveryTest`、`MangaFileRequestTest`、`MainNavigatorMangaTest` |
| Coil 管线剥离 | `CoverInterceptor` 209→126、`CoverFetcher` 265→257、`CoverExtras` 删 7 个 Manga key、`OkHttpModelLoader` 删 `mangaOption`/`mangaBookUrlOption` |
| Glide 管线剥离 | `OkHttpStreamFetcher`（删 `manga`/`mangaBook`/`loadLocalImage`；`skipDecode(source, !manga)`→`skipDecode(source, true)`）、`LegadoGlideModule`（删 `LegadoDataUrlLoader.Factory()` 注册）、`LegadoDataUrlLoader` 整文件删除 |
| HTTP 管线剥离 | `HttpHelper` 删 `okHttpClientManga`（100MB `manga_cache` + 进度上报拦截器）、`HttpCacheType.MANGA` 枚举与清理分支 |
| 导航/入口手术 | `MainNavKey`（删 `MainRouteReadManga` + `ROUTE_READ_MANGA`）、`MainNavGraph`（1821→1732：entry 块 64 行、`showMangaUi` 参数、2 处 `else if`、`onOpenMangaReader` 回调）、`MainNavigator`（5 处）、`MainActivity`（7 处：`createReadMangaIntent`、`mangaSettingsGateway`、`activeMangaKeyHandler`） |
| 其他手术 | `LocalBook`（`importMangaDirectory` + 压缩包漫画分支）、`BookCover`（`loadManga`/`preloadManga`）、`SearchScreen`、`BookInfo*`、`ReadBook:701`、`appModule`（15 处 DI）、`OtherConfig*`（开关 + `ShowMangaUiChanged` intent）、`DownloadCacheConfig*`（漫画缓存项 + `ClearMangaCache`）、`EventBus.UP_MANGA_CONFIG`、`ContextExtensions`/`FragmentExtensions`（`isAudio` 分支**保留给 W2**） |
| **保留兼容**（D11） | `OtherSettings.showMangaUi` 及 `FeatureSettingsRepositories` 映射（旧偏好/备份可恢复）、`Book.isImage` 类型位、`R.string.manga`（`BookItem` 类型标签）、`BookType.image` 处理 |
| 测试修正 | `BookInfoWithSettingsTest`（删 `showMangaUi` 断言）、`MainBookPageRoutesTest`（`MainRouteReadManga`→`MainRouteReadBook`，删 1 用例） |

**验证证据**：

| 命令 | 结果 |
|---|---|
| `:app:compileAppDebugKotlin` | **BUILD SUCCESSFUL**（6 轮收敛：623 → 544 → 9 → **0** 错） |
| `testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache` | **BUILD SUCCESSFUL in 6m 41s** |
| 单测 | **1578 / 0 失败**（基线 1705 → 少 127，与删除的 19 个漫画测试文件相符） |
| APK | arm64 **69M**（基线 71.7M，−2.7M）、armv7 68M（基线 71.2M）、universal **75M**（基线 78.2M，−3.2M） |
| 反证检索 | `grep -rn "Manga\|manga" app/src/main/java` → 11 文件，均为 W4 数据层或兼容保留字段（`OkHttpModelLoader` 死键已清） |
| 架构门 | 通过（本波无基线键移除需求；删除文件对应的基线键在 W1-1 已处理） |

**方法论事故与修复（本波最重要教训）**：三次因**行扫描式块删除脚本**破坏 Kotlin 结构：

1. `CoverFetcher`：删 `if/else` 分支留下多余 `}`。
2. `HttpHelper`：`while 缩进配平找 }` 在嵌套 `interceptors.add { }` 处提前终止 → 级联 647 错。
3. `MainActivity`/`MainNavigator`/`MainNavGraph`/`MainNavKey`：`drop_method` 对**表达式体函数**与**多行 `data class`** 配平失败，静默吃掉扩展函数与大段代码（`resolveStartRoute`/`navigateBack` 等整段消失）。

→ 结论已固化为 `README.md` §六「协作协议」条目 1。

### 7.3 W1-3 · AI / 知识库 / 配音界面层删除（2026-10-14）

**提交**：`7a14c1f8b`
**决策**：D5（AI 全删）、D6（知识库删）、D4c（配音删）。
**实际边界**（与最初计划不同，见下「三次转向」）：只删**用户界面与入口**，AI 底座与朗读配音链整体保留到 W2。

| 项 | 结果 |
|---|---|
| 规模 | **净删 51 文件**（主源 48 + 测试 3）；主源 1,681→**1,631** 文件、318,075→**303,092** 行（−14,983） |
| 删除分组 | `ui/book/knowledge`(19)、`ui/config/ai`(15)、`ui/ai`(7，保留 `chat/` 思考卡片三件套)、`ui/main/bookshelf/autoGroup`(4)、`ui/book/read/sheet` 的 AI 面板(4)、`ReadAi{Contract,Delegate}`(2) |
| 编译 | `:app:compileAppDebugKotlin` **0 错**；主源+测试源 `BUILD SUCCESSFUL`。**14 轮收敛**：485 → 340 → 261 → 195 → 61 → 12 → 6 → 1 → 0（另 5 轮为回滚重试） |
| 完整验证集 | `testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug` → **BUILD SUCCESSFUL in 5m 31s**（140 actionable tasks）；清基线后复跑 **BUILD SUCCESSFUL** |
| 单测 | **1571 / 0 失败 / 0 错误**（W1-2 后 1578，少 7 = 删除的 `AiAutoGroupViewModelTest`） |
| APK | arm64 **69M**、armv7 **68M**、universal **75M**（与 W1-2 持平：本波删的是 UI 与死代码，未被 R8 计入体积） |
| 架构门 | `verifyConfigArchitecture` 通过；`legacyPreferenceCallBaseline` **74 → 73**（删 `CheckSourceConfig.kt` 键，23 → 22 键） |
| lint 基线 | `app/lint-baseline.xml` **425 → 330 issue / 428 → 333 location**（纯删除 0 插入） |
| 反证检索 | 见下 |

**反证检索**：`grep -rn "ui\.book\.knowledge\|ui\.config\.ai\|ReadAiDelegate\|AiAutoGroup" app/src/main` → 0 命中（仅剩 W1-3-SCOPE 登记的保留项）。

**三次转向（本波核心方法学）**：

1. 原计划把 AI 域作独立一波整体删除。侦察发现 AI 底座被**朗读链反向依赖**（`AiReasoningLevel`/`AiProfileGateway`/`AiCastAssignUseCase` 等），整域删除会连锁拖入整个 W2。
2. 转向「先删上层消费者（知识库+配音）再删底座」；实测仍失败：`AiReasoningModeButton`/`AiThinkingCard` 被 `AiCastDialogSheet`（配音）与 `BookCharacterListScreen`（知识库）**共用**，删任一层都让另一层断；试图「保留契约」则需连带保留 `data/repository/ai/**` 与 4 个 AI 用例，等于底座整体保留。
3. 最终定界：**只删界面与入口**，底座整体留给 W2。此边界编译后立即收敛，且不制造任何临时垫片。

**方法论事故与修复（新增三条纪律实践）**：

1. `git rm` 整文件删除**安全**（91 文件一次删净）；风险全部来自「接入点编辑」。
2. `sed -i '335,357d'` 删 `BookInfoContract` 时**行号偏移**误删接口抽象成员 → 编译报「未实现抽象成员」+ 语法错误 → `git checkout --` 回滚后用精确文本编辑重做。
3. `sed -i '428,476d'` 在 `MainNavigator` 留残余行 → 编译报「返回类型不匹配」→ 定位后精确修正。
4. **`updateLintBaseline` 会放宽基线**：425 → 502 issue（把当前全部 502 问题写入）。这违反 D12「不接受先放宽基线再迁移」→ 立即 `git checkout --` 回滚，改用自写文本级按块删除脚本（只删指向不存在文件的 `issue`/`location`，逐行保留原文）→ 425 → 330，diff 为 **纯删除 0 插入**。

**未验证项 / 遗留**：

- **真机手测未做**：知识库/配音入口消失后，书籍详情页与朗读设置页在真机上的布局、滚动、返回行为未验证。
- **翻译状态刷新语义变化（未验证风险）**：`ReadBookViewModel` 原通过 `ReadAiDelegate.observeChapterTranslation` **订阅**翻译任务流；AI 域移除后改为在 `syncFromReadBook` 中直接读 `TranslationManager.getChapterTaskStateFlow(...).value`。失去持续订阅，翻译状态仅在 `syncFromReadBook` 被触发时刷新。**需 W3 翻译域收敛时校正为正确订阅**。
- **翻译的 AI provider 入口断链**：`TranslationConfigScreen` 删除了跳转 AI 配置的入口，但设置项仍可选 `PROVIDER_APP_AI` → 用户选中后无处配置。登记 W3 处理。
- **lint 基线仍有 33 条过时条目**（指向**存在**的文件、但问题已消失）：静态脚本无法判定，需用 lint 报告精确清理 → 登记 W5。
- **AI/知识库专用 strings 未清理**（4 语言，含 `ai_*`、`book_characters`、`character_network`、`plot_events` 等）→ 登记 W5；当前仅产生 lint warning，不影响构建。

### 7.4 W2a · 朗读 / 音频 / TTS / cast / BGM 域删除（2026-10-21）

**提交**：`dd61c2bfd`（289 files / +103 / **-53,373**）
**边界**：整文件删除 228 个 app 源码（177 main + 51 test）；保留 `AnalyzeUrl` 的 media3、正文分页/绘制、`preserveReadAloudPosition` 语义、书源/备份链路。Manifest 同步删除 3 个朗读/音频 service 与 `MediaButtonReceiver`。

| 命令 | 结果 |
|---|---|
| `:app:compileAppDebugKotlin` | **BUILD SUCCESSFUL**（209 → 0 错；W2a 收敛完成） |
| `testAppDebugUnitTest` | **1262 / 0 失败 / 0 错误**（235 XML；基线 1571，减少来自删除的 50+ 朗读/播放测试文件） |
| `verifyConfigArchitecture` | **通过**（21 键/71、1 键/0、8 键/20；缺失文件键 0） |
| `assembleAppDebug` | **通过**；APK arm64 **65M**、armv7 **64M**、universal **71M**（W1-3 基线 69/68/75M） |
| `git diff --check` | 0 |
| 架构门基线脚本 | 三个基线均无指向不存在文件的键 |
| lint | **未完成复跑**：上一轮仅 4 个 `MissingClass`（TTS/HttpReadAloud/AudioPlay/MediaButtonReceiver），已从 Manifest 移除；全量 lint 在 `lintAnalyzeAppDebug` 阶段超时（10 分钟），登记 W5 |

**残留/顺延**：
- `BookType.audio` / `BookSourceType.audio` 类型位与 DAO 位运算仍保留 → W2-S6/W4。
- `ReaderElement.RoleCast/BgmScene`、`castRenderHash`、`CastMarkers` 文本工具仍保留 → W2b（已侦察，见 `team/W2B-CORE-PLAN.md`）。
- `readAloudParagraphIndex`、`readAloud: Color`（文字高亮色）保留 → W2c；不得误删。
- 真机手测未做 → W5。

### 7.5 W3 · 首页 / Web / 翻译 / 导航收敛（2026-10-21）

| 子波 | 提交 | 结果 |
|---|---|---|
| W3a 首页 + 3 tab | `2db39755a` | 31 文件 / -7,423 行；Banner 迁 `ui/widget/components/book/BookBannerRow`；`MainDestination` 收敛为 Bookshelf/Explore/My |
| W3b Web 归档 | `ce91fb1b0` | 11 源码 + assets/web 58 文件移出 APK；`attic/webservice/` 保留恢复路径；删除 6 个 ktor 依赖；ShortCuts 移到 `App.onCreate` |
| W3c 翻译删除 | `97fa73e8b` | 16 文件 / -1,961 行；阅读页/导出/设置链路清零；保留字典与 AI 底座共享模型 |

**W3 出口验证（提交后）**：

| 命令 | 结果 |
|---|---|
| `:app:compileAppDebugKotlin` | **BUILD SUCCESSFUL**（逐子波各一次） |
| `testAppDebugUnitTest` | **1255 / 0 / 0 / 0**（233 XML） |
| `verifyConfigArchitecture` | **通过**（20 键/68、1/0、8/20；无缺失键） |
| `assembleAppDebug` | **通过**；APK arm64 **60M**、armv7 **59M**、universal **66M**（W2a 65/64/71M） |
| `git diff --check` | 0 |

**残留/顺延**：翻译/AI/TTS 专用 strings 4 语言、`PreferKey.llm*`、`BookType.audio`/DB 旧表与 DAO → W4/W5；lint 复跑仍受 `lintAnalyzeAppDebug` 超时限制。

> 注：W3b 的提交号与 W3a/W3c 以 `git log` 实际值为准；本文按交付时 HEAD 顺序记录。

### 7.6 W4 · 数据层收口（2026-10-21）

| 项 | 结果 |
|---|---|
| 提交 | `d167de175` |
| 范围 | 137 文件，+2,292 / -13,101（含 110 删除、26 修改、1 新增 schema） |
| Room | `version 131→132`；`Migration_131_132` 用 `@DeleteTable` 删除 40 张旧表 |
| 旧数据 | `books.type` 中 audio/image/video 位清零，并补 `text+notShelf`，保留数据、移出书架 |
| 删除域 | RSS、AI/知识库、朗读/TTS、配音 cast、BGM、首页模块、HTTP TTS 的 entity/DAO/repository/usecase/UI |
| 兼容 | 旧备份中 RSS/httpTTS/homepage 文件忽略；书籍编辑仅允许 text |
| 验证 | `compileAppDebugKotlin`、`compileAppDebugUnitTestKotlin`、`verifyConfigArchitecture` 通过；`testAppDebugUnitTest` **1,222 / 0**；`assembleAppDebug` 通过 |

### 7.7 W5 · 字符串与偏好清理（2026-10-21）

| 项 | 结果 |
|---|---|
| 提交 | `7fb79c9d1` |
| strings | 4 语言删除 931 个已裁撤域键（zh-HK/TW 各 443）；删除 `book_type_audio/image` |
| PreferKey | 删除 Translation 段与 `aiRewritePresets` |
| 验证 | `processAppDebugResources`、`compileAppDebugKotlin` 通过 |

### 7.8 W2b/W2c · 阅读核心 cast/BGM 与朗读高亮残留（2026-10-21）

| 项 | 结果 |
|---|---|
| W2b 提交 | `90d57490d`；18 文件，+77 / -1,282 |
| 删除 | `ReaderCastOptions/Profile`、`RoleCast/BgmScene` 测量与页面类型、`CastCapsuleGeometry/Style`、Legacy 胶囊预算、`castRenderHash` |
| CastMarkers | 收敛为纯文本注入/剥离/偏移工具；新增 `CastMarkersTextTest` 5 例 |
| W2c | 删除 `ReaderPage.Text.readAloud`、`readAloudParagraphIndex` 与 Canvas `isReadAloud` 分支；保留文字强调色 `readAloud: Color` |
| 验证 | `compileAppDebugKotlin`/`compileAppDebugUnitTestKotlin` 通过；`feature.reader.*` 测试通过；全量 `testAppDebugUnitTest` **1,210 / 0**；架构门通过；`assembleAppDebug` 通过 |
