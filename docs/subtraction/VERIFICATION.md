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
