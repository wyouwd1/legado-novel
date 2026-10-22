# W2 侦察报告：阅读内核三件套（Scout-3b）

> **来源**：`bg_delegate` 只读侦察，task `d870365b44ccd9c97a9bae6bf76dba76d`，
> route `new-api/deepseek-v4.1-flash`，21 turns / 67 tool calls，
> 答案 18,505 bytes（sha256 `bf941add0117501a…`，落盘时已校验）。
> **性质**：只读分析产物，**未经验证**。所有行号须由主控在编辑前用 `read` 复核；
> 报告自身标注的 6 个「不确定项」禁止猜测，需编译验证。
> **用途**：指导 T4（`ReadBookController.kt`）、T6（`model/ReadBook.kt`）、
> T8（`ReaderCanvasSurface.kt`）的执行；文末「连带项」表是**跨文件同批处理**的依据。

---

# W2 收敛侦察报告：ReadBook.kt / ReadBookController.kt / ReaderCanvasSurface.kt

**只读侦察，未修改任何文件、未跑 gradle。** 证据来源：`/tmp/w2a6.log`（错误行号）+ 对这三个文件的逐块 `read`（日志行号与源码内容逐条核对，**全部吻合**），外加少量符号级 `grep` 用于判断"删/留"（不读其它文件正文，`grep` 依据在表中标注）。

**整体判断**：三个文件的错误 100% 来自"朗读域已删类"的残留引用，没有一个是渲染/分页逻辑本身的问题。**唯一需要保留语义的是 `preserveReadAloudPosition` 参数链**（见最后一节）。最危险的处理点是 `ReadBook.readAloud()` —— 它有 7 个外部调用点（不在本任务 3 文件内），删函数必须同批处理。

---

## 1. `app/src/main/java/io/legado/app/model/ReadBook.kt`（51 错）

| 行号(区间) | 代码摘要 | 建议动作 | 依据 | 风险 |
|---|---|---|---|---|
| 29–31 | `import io.legado.app.help.readaloud.cast.{CastAssignmentStore,BgmSceneStore,CastRenderOptions}` | 删整块 29..31 | `help/readaloud/**` 整目录已删（`find` 实测目录不存在） | 低 |
| 57 | `import io.legado.app.service.BaseReadAloudService` | 删整行 | 服务已删 | 低 |
| 104 | KDoc：`isReadingAloud` 真实来源是 `BaseReadAloudService.isRun` | 改写为 `刻意不含 isReadingAloud / isLoading：两者都只有间接信号，塞进来会得到静默陈旧字段。` | 仅注释，避免悬空引用 | 无 |
| 165 | `private val readAloudSessionStore: ReadAloudSessionStore by inject()` | 删整行 | 同包类已删（无 import，故只报使用点） | 中：其余使用点 259/260/627/1026/1028/1249 均随本表其余块一并删除 |
| 244 | `restoreReadAloudFollowIfBackOnPosition()`（`publishSnapshot()` 内） | 删整行 | 被调函数删除 | 低：`_snapshot.value = buildSnapshot()` 不动 |
| 247–264 | KDoc + `restoreReadAloudFollowIfBackOnPosition()` | 删整块 247..264 | 全为"翻回朗读位置恢复跟随"逻辑 | 低 |
| 514–515 | `parse()` 的 `castLabels = if (ReadConfig.multiRoleCast) CastAssignmentStore…` / `bgmScenes = if (ReadConfig.bgmAssign) BgmSceneStore…` | 删整块 514..515 | `parse` 两参数有默认值（`ReaderChapterSource.kt:139 castLabels: Map<Int,String>? = null`、`:144 bgmScenes = emptyMap()`）；`ReadConfig` 两属性已删 | 低：`null` 与 `emptyMap()` 等价（`:174 castLabels ?: return text`） |
| 534 | `castOptions = CastRenderOptions.load(book.bookUrl, chapter.index),` | 删整行 | `LegacyReaderChapterPaginator.kt:130 castOptions: ReaderCastOptions = ReaderCastOptions.Disabled` | 低 |
| 614 | `resumeReadAloudWaitingForChapter(snapshots)` | 删整行 | 被调函数删除 | 低 |
| 617–631 | KDoc + `resumeReadAloudWaitingForChapter()` | 删整块 617..631 | 朗读换章重启逻辑 | 低 |
| 1017–1030 | KDoc + `prepareManualNavigation()` | 删整块 1017..1030 | 朗读脱离状态机 | **中：连带 1071 / 1082 / 1093 三处调用行必须同批删** |
| 1032–1041 | KDoc + `followReadAloudAfterManualNavigation()` | 删整块 1032..1041 | 同上 | **中：连带 1078 / 1088 两处调用行** |
| 1043–1053 | KDoc + `onComposeManualPageTurn()` | 删整块 1043..1053 | 朗读联动；外部唯一调用 `ReadBookController:1115` | 中 |
| 1246–1266 | `curPageChanged` 内 `readerChapterInputWindow.current?.let { input -> if (BaseReadAloudService.isRun) {…} }` | 删整块 1246..1266（`let` 块内除朗读判定外无其它语句） | 朗读重启/跟随判定 | 中：边界请复核 1265–1267（见「不确定项」） |
| 1271–1278 | KDoc + `fun readAloud(play, chapterPosition)` | 删整块 1271..1278 | 只转发 `ReadAloud.play` | **高：7 个外部调用点见「连带项」** |
| 1280–1286 | `fun syncReadAloudPage(...)` | 删整块 1280..1286 | `grep` 仅本文件命中（无外部调用） | 低 |
| 1288–1296 | KDoc + `fun upTextChapterAloudSpan(...)` | 删整块 1288..1296 | 唯一调用点 262（同批删） | 低 |
| 1324 | `if (BaseReadAloudService.isRun) return`（`prefetchForOpen`） | 删整行 | 朗读中不预取的门 | 低 |
| 1402–1403 | `val preserveReadAloudPosition = BaseReadAloudService.isRun && …` | 删整块 1402..1403，并把 1406 改为 `preserveReadAloudPosition = false,` | **专节给出可直接套用文本** | 低 |
| 1712–1718 | `val castLabels = …` / `val bgmScenes = …` | 删整块 1712..1718 | 同 514–515 依据 | 低 |
| 1719 | `val castRenderHash = CastRenderOptions.signatureFor(...)` | 删整行 | 类已删 | 低 |
| 1728–1729 | `castLabels = castLabels,` / `bgmScenes = bgmScenes,`（`parse` 实参） | 删整块 1728..1729 | 随 1712–1718 删除（否则报未解析局部量） | 低 |
| 1744 | `castRenderHash = castRenderHash,` | 改写为 `castRenderHash = 0,` | `ReaderChapterInput.kt:29 val castRenderHash: Int = 0`；多角色渲染删除后签名恒 0 语义等价 | 低 |
| 1243 / 1396 / 1433 / 1463 / 1553 / 1656 / 1685 / 1754 / 1789 | `preserveReadAloudPosition` 形参与实参 | **保留（说明原因）**：形参语义＝"重载不跳位"，本轮只删其"朗读判定来源"，参数链保持不变以免波及 CacheBook/download 等其它文件 | 低 |

---

## 2. `app/src/main/java/io/legado/app/ui/book/read/ReadBookController.kt`（37 错）

| 行号(区间) | 代码摘要 | 建议动作 | 依据 | 风险 |
|---|---|---|---|---|
| 43 | `import …core.readaloud.ReaderVisibleTextPosition` | 删整行 | 包已删（`find` 实测无 `core/readaloud`） | 低 |
| 60 | `import io.legado.app.help.TTS` | 删整行 | 已删 | 低 |
| 63 | `import …help.readaloud.cast.BgmSceneStore` | 删整行 | 目录已删 | 低 |
| 68 | `import io.legado.app.model.ReadAloud` | 删整行 | 类已删 | 低 |
| 78 | `import io.legado.app.service.BaseReadAloudService` | 删整行 | 服务已删 | 低 |
| 138 | `private val aloudSettingsGateway get() = …get<…ReadAloudSettingsGateway>()` | 删整行 | gateway 已删；`grep aloudSettingsGateway` 全文件**仅此一处**（无使用） | 低 |
| 327 | `private var tts: TTS? = null` | 删整行 | 类型已删 | 低 |
| 370 | `private var readAloudPosition: Pair<Int, Int>? = null` | 删整行 | 朗读高亮锚点 | 中：连带 853–856 / 888–890 / 1116 / 2432 |
| 371 | `private var composeVisibleBodyTextPositionProvider: (() -> ReaderVisibleTextPosition?)? = null` | 删整行 | 类型已删 | 中：连带 2574–2578 |
| 468–473 | `private fun speak(text) { … tts?.speak(text) }` | 删整块 468..473 | 唯一消费者 TTS | 中：调用点 2464 需同批 no-op |
| 482–483 | `tts?.clearTts()` / `tts = null`（`clearTts()` 内） | 删整块 482..483 | 字段已删；`clearTts()` 其余逻辑**保留** | 低 |
| 595–605 | `is ReaderElement.RoleCast -> { …ReadBookSheet.RoleCast… }` / `is ReaderElement.BgmScene -> { … }` | 改写为 `is ReaderElement.RoleCast -> false` 与 `is ReaderElement.BgmScene -> false` | `when` 需穷尽，`ReaderElement.RoleCast/BgmScene` 类型**仍在** `core/model/ReaderPage.kt:299` | 中：若 `ReadBookSheet.RoleCast/BgmScene` 仍存在，也可保留原样（见「不确定项」） |
| 624 | `BaseReadAloudService.isRun -> viewModel.onIntent(ReadBookIntent.ReadAloudAction)` | 删整行 | 服务已删 | 中：VM 侧朗读 intent 需同批删 |
| 646–649 | `READ_ALOUD_PREVIOUS/NEXT_PARAGRAPH -> …ReadAloudPrev/NextParagraph` | 改写为 `ReaderTapAction.READ_ALOUD_PREVIOUS_PARAGRAPH -> Unit` / `…NEXT_PARAGRAPH -> Unit` | `when` 穷尽（无 else） | 中 |
| 661–665 | `TOGGLE_READ_ALOUD_PAUSE -> if (BaseReadAloudService.isPlay()) ReadAloud.pause else ReadAloud.resume` | 改写为 `ReaderTapAction.TOGGLE_READ_ALOUD_PAUSE -> Unit` | 同上 | 中 |
| 853–856 | `val aloudPosition = readAloudPosition` + `val aloudParagraphIndex = aloudPosition?.let{…}` | 删整块 853..856 | 朗读高亮锚点 | 中 |
| 878–879 | `val pageHasAloudParagraph = …` | 删整块 878..879 | 同上 | 低 |
| 888 | `readAloudParagraphIndex = aloudParagraphIndex.takeIf { pageHasAloudParagraph },` | 改写为 `readAloudParagraphIndex = null,` | `ReaderPage` 字段仍在（传 null 即无高亮） | 低 |
| 890 | `… xor (aloudPosition?.hashCode()?.toLong() ?: 0L),` | 改写为只保留 `xor (selection?.hashCode()?.toLong() ?: 0L)` 一项 | 同上 | 低 |
| 1115–1117 | `if (BaseReadAloudService.isRun && ReadBook.onComposeManualPageTurn()) { readAloudPosition = … }` | 删整块 1115..1117 | 两依赖均删 | 低 |
| 1567–1570 | `castOptions = io.legado.app.help.readaloud.cast.CastRenderOptions.load(...)` | 删整块（该具名参数整段） | paginate 默认 `ReaderCastOptions.Disabled` | 低 |
| 2055 / 2057 | `R.id.menu_insert_bgm_before/after -> return openBgmSceneAtSelection(...)` | 删整行（两行） | BGM 域删除 | 中：需确认 `ids.xml` 是否已删该 id |
| 2065–2093 | KDoc + `openBgmSceneAtSelection()` | 删整块 2065..2093 | BGM 场景插入 | 中 |
| 2288 | `viewModel.readAloudProgress.value?.let(::updateReadAloudProgress)` | 删整行 | 被调函数删除 | 中 |
| 2432–2435 | `is ReadBookEffect.UpAloudState -> { readAloudPosition = null; … }` | 只删 2432 一行，保留 `directReaderPageIndex?.let(::publishDirectReaderWindow)` | 字段删除 | 低 |
| 2456 | `is ReadBookEffect.ToggleReadAloud -> onToggleReadAloud?.invoke() ?: toggleReadAloud()` | 改写为 `is ReadBookEffect.ToggleReadAloud -> Unit` | `toggleReadAloud` 删除；effect 类仍在（日志未报） | 中 |
| 2460–2462 | `is ReadBookEffect.TextActionAloudPosition -> { ReadBook.updateReadingPosition; ReadBook.readAloud }` | 改写为 `is ReadBookEffect.TextActionAloudPosition -> Unit` | `readAloud` 删除 | 中 |
| 2464 | `is ReadBookEffect.TextActionSpeak -> speak(effect.text)` | 改写为 `is ReadBookEffect.TextActionSpeak -> Unit` | `speak` 删除 | 中 |
| 2562–2572 | `fun updateReadAloudProgress(chapterStart)` | 删整块 2562..2572 | 朗读高亮锚点；调用点 2288 + `ReadBookRouteScreen:252` | 中 |
| 2574–2578 | `fun setComposeVisibleBodyTextPositionProvider(provider)` | 删整块 2574..2578 | 类型已删；调用点 `ReadBookRouteScreen:798` | 中 |
| 2580–2587 | `private fun readAloudFromComposeVisibleStart()` | 删整块 2580..2587 | 仅被 2600/2604 调用（同批删） | 低 |
| 2595–2611 | `private fun toggleReadAloud()`（2598/2599/2603/2606/2609 报错） | 删整块 2595..2611 | 朗读开关 | 中：调用点 2456 |
| 2614 | `toggleAutoPage()` 内 `ReadAloud.stop(activity)` | 删整行 | 自动翻页不依赖朗读服务 | 低 |
| 2724 | `if (!…volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) return false` | 改写为 `if (!readSettingsGateway.currentSettings.volumeKeyPageOnPlay) return false` | 删"朗读播放中禁止音量键翻页"子句；`volumeKeyPageOnPlay` 成孤儿设置（登记 W4/W5） | 低 |

---

## 3. `app/src/main/java/io/legado/app/feature/reader/ReaderCanvasSurface.kt`（14 错）

| 行号(区间) | 代码摘要 | 建议动作 | 依据 | 风险 |
|---|---|---|---|---|
| 120–121 | `import …core.readaloud.{ReaderVisibleTextPosition, ReaderVisibleTextPositionPolicy}` | 删整块 120..121 | 包已删 | 低 |
| 236 | 形参 `onVisibleBodyTextPositionProvider: ((() -> ReaderVisibleTextPosition?)?) -> Unit,` | 删整行 | 唯一消费是"从可见处开始朗读" | 中：调用方 `ReadBookRouteScreen.kt:798`（具名参数）需同批删 |
| 360–370 | `val latestVisibleBodyTextPosition by rememberUpdatedState {…}` + `DisposableEffect(onVisibleBodyTextPositionProvider) {…}` | 删整块 360..370（362 为 `ReaderVisibleTextPositionPolicy.firstVisibleBodyText`） | 同上 | 中 |
| 2070–2076 | `is ReaderElement.RoleCast -> drawRoleCast(…)` / `is ReaderElement.BgmScene -> drawBgmScene(…)`（含 2074 `CastAvatarCache`） | 改写为两行：`is ReaderElement.RoleCast -> Unit` / `is ReaderElement.BgmScene -> Unit` | `when` 穷尽；类型保留；`help.readaloud.cast` 已删 | 低 |
| 2796–2802 | 同结构第二处（2800 `CastAvatarCache`） | 同样改写为两行 no-op | 同上 | 低 |
| 3171–3200(≈) | `private fun drawCapsuleBackground(...)`（3184/3186 `CastCapsuleImageCache`、3185 `isRecycled`） | 删整块（`private fun drawCapsuleBackground(` 起至其闭合 `}`） | `grep` 调用者仅 3220/3259（同批删） | 低：闭合行号请复核 |
| 3206–3238(≈) | `private fun drawBgmScene(...)`（3215/3216 `CastCapsuleStyleStore`） | 删整块 | BGM 绘制 | 低：同上 |
| 3242–3352(≈) | `private fun drawRoleCast(...)`（3252 `CastCapsuleStyleStore`） | 删整块 | 角色胶囊绘制 | 低：同上 |
| 3356–末尾(≈) | `private fun labelColor(baseArgb, style)` | 删整块 | `grep` 命中 3223/3269/3281/3330/3350 全在被删函数内 | 低：确认无其它调用后再删 |
| 1918–1919 / 1986–1987 / 2006 / 2011–2012 / 2138 / 2156–2213 / 2293 / 2306 / 2319 / 2622 / 2633–2634 | `castColorArgb` / `castVariantArgb` 参数链 | **本轮保留（说明原因）**：`when` no-op 后它们仅剩未使用形参，Kotlin 不报错；整链清理是同文件大范围签名改动（触及分页/滚动绘制热路径），建议另立一步 | 保证本轮快速到 0 错 | 中：属"死参数"，须登记后续清理 |

---

## 4. `ReadBook.kt` 的 `preserveReadAloudPosition` 最小改法（可直接套用）

**原则**：形参、实参链、默认值**全部不动**（1243 / 1396 / 1433 / 1463 / 1553 / 1656 / 1685 / 1754 / 1789），只删掉两处"从已删服务推导该值"的地方。

**(a) 删除 1402–1403，改写 1404–1409**（现状为 `val preserveReadAloudPosition = BaseReadAloudService.isRun && BaseReadAloudService.currentChapterIndex == durChapterIndex` 后传入）：

```kotlin
    fun loadOrUpContent(success: (() -> Unit)? = null) {
        if (readerChapterInputWindow.current == null) {
            // 朗读域已移除：preserveReadAloudPosition 形参语义保留（重载不跳位），
            // 原判定来源（朗读服务是否停在当前章）随朗读一并删除，恒传 false。
            loadContent(
                durChapterIndex,
                preserveReadAloudPosition = false,
            ) {
                success?.invoke()
            }
        } else {
```
> 语义等价性：删除朗读后 `BaseReadAloudService.isRun` 永假 → 原表达式恒 `false`，改写后行为不变。

**(b) 改写 1241–1246 区域的 `curPageChanged`**（删 1246..1266 整块，参数保留）：

```kotlin
    private fun curPageChanged(
        pageChanged: Boolean = false,
        // 形参保留：属"重载不跳位"阅读进度语义位；本轮唯一读取点（朗读重启判定）已随朗读域删除。
        preserveReadAloudPosition: Boolean = false,
    ) {
        renderCallBack?.pageChanged()
        upReadTime()
        preDownload()
    }
```
> `let` 块（1246–1266）内除朗读判定外无其它语句（已逐行核对 1246–1257 并推断至 1265）；`input` 无其它用处，故整块可删。`pageChanged` 仍被子句使用（`isScroll && pageChanged`），改后该形参变为未使用形参 —— 不报错；若要彻底清理，需同时确认 `contentLoadFinish`（1715 附近 `curPageChanged(preserveReadAloudPosition = preserveReadAloudPosition)`）的调用形态。

---

## 5. 连带项（**不在本任务 3 文件内，必须同批处理，否则"删完即报未解析"**）

| 被删符号 | 外部调用点（`grep` 实测） |
|---|---|
| `ReadBook.readAloud(...)` | `ui/main/MainNavGraph.kt:839`、`ReadBookViewModel.kt:1244`（line 1244 在 1240–1246 删块内）、`:1302`、`:1769`、`ReadBookController.kt:2461`、`:2589`、`:2600` |
| `ReadBook.onComposeManualPageTurn()` | `ReadBookController.kt:1115`（本表已含） |
| `updateReadAloudProgress` | `ReadBookRouteScreen.kt:252`、`ReadBookController.kt:2288`（本表已含） |
| `setComposeVisibleBodyTextPositionProvider` | `ReadBookRouteScreen.kt:798`（与 `ReaderCanvasSurface` 形参 236 同批） |
| `openBgmSceneAtSelection` | `ReadBookController.kt:2055`、`:2057`（本表已含） |
| `ReadBook.syncReadAloudPage` / `upTextChapterAloudSpan` / `prepareManualNavigation` / `followReadAloudAfterManualNavigation` | **无外部调用**，可安全删（`grep` 仅命中本文件） |
| `ReadAloudSettingsGateway`（已删） | 全库已无其它引用（`grep` 只命中 `ReadBookController:138`） |
| 影响面登记（非编解码问题） | `ReadBookConfig.readAloudDetachReminderEnabled`、`ReadSettings.readAloudDetachReminderEnabled`、`PreferKey.readAloudDetachReminderEnabled`、`ReadSettings.volumeKeyPageOnPlay`、`PreferKey.multiRoleCast/bgmAssign` 在删除上述函数后成为无消费的孤儿设置 → 按 D11 保留读取兼容，登记到 W4/W5 清理 |

---

## 6. 不确定项（**禁止猜测，需编译验证**）

1. `ReadBookSheet.RoleCast/BgmScene` 是否仍存在 → 决定 `ReadBookController:595–605` 是"no-op 化"还是"保留原样"。我未读 `ReadBookContract.kt`。
2. `R.id.menu_insert_bgm_before/after` 是否仍在 `res/xml`（`ids.xml`/菜单资源）→ 决定 `2055/2057` 是"删行"还是本来就会另报错。
3. `ReadBook.kt` 中 `curPageChanged` 的 `let` 块闭合行（我核对到 1257，1246 起始行确定）→ 删块前用 `read` 复核 1265–1267。
4. `ReaderCanvasSurface` 中 `drawCapsuleBackground` / `drawBgmScene` / `drawRoleCast` / `labelColor` 的精确闭合并行号（起止已给近似，`grep` 给出的函数起点行是确定的）→ 逐函数 `read` 复核后再删。
5. `castColorArgb/castVariantArgb` 参数链能否整删（跨 1918→2006→2138→2293→2622 多层签名）→ 建议本轮保留、另立一步；证据：`2049/2775` 使用的是 `readAloud.toArgb()`（Color，与 cast 无关），说明该链只服务 cast 绘制，但删除会触及滚动绘制热路径。
6. `ReaderCanvasSurface.kt:1981` 的 `readAloud,` 参数是 `readAloud: Color`（高亮文字色，已 grep 确认 1535/1559/1910/2006 等），**与朗读域无关，不要动**（无误报）。

**操作纪律提醒**（沿用 README §六 / W1-3 教训）：表中所有"删整块"动作请**先 `read` 复核首尾行内容**再精确编辑；整文件删除用 `git rm`；删完一轮先跑 `:app:compileAppDebugKotlin`，并注意"架构门 `verifyConfigArchitecture` 失败"会以 `exit=1` 但 `grep -c '^e: '` 为 0 的形式出现。
