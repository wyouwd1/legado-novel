# W2 阅读内核接入点侦察（scout-reader）

> 快照：2026-10-21 10:57（Asia/Shanghai），分支 `subtract/novel-only`，工作区未提交。
> 范围：`ReadBookController.kt` / `ReaderCanvasSurface.kt` / `ReadBookContract.kt` / `ReadBookViewModel.kt` / `model/ReadBook.kt`。
> **主控在 10:50–10:56 已并发完成本组大部分删除。** 本报告按 10:57 的真实行号给出**剩余可编辑项**；10:46 的 45/14 错误已不再是当前状态。
> 本次侦察未修改任何 .kt/.xml，未运行 gradle；“无引用”结论来自全仓 `grep`/静态符号比对，最终以主控编译为准。

---

## 0. 结论

1. **本组已无编译阻塞项。** 全仓静态扫描：`ReadBookIntent` / `ReadBookEffect` / `ReadBookSheet` / `ReadBookMenuRoute` 均 **0 个未解析引用**；VM 的 `when (intent)` 已覆盖当前全部 211 个 intent。
2. **剩余 5 处非阻塞清理**（删了更干净，不删不影响编译）：
   - `ReadBookContract.kt` 两个按钮池的 6 个朗读/音频 id；
   - `ReadBookContract.kt:822` 的空 `// TTS` 注释；
   - `ReadBookViewModel.kt:5` 未使用的 `TextToSpeech` import；
   - `model/ReadBook.kt:1162–1170` 无调用方的 `upTextChapterAloudSpan`；
   - `ReadBookContract.kt:1585–1587` 的 `ReadAloudDetachReminderEnabled`（牵连 3 个设置文件，建议 W4，不建议本轮单独删）。
3. **必须保留的语义**：`ReadBook.kt` 的 `preserveReadAloudPosition` 参数链、`ReaderCanvasSurface` 的 `readAloud: Color`（文字高亮色）、`feature/reader/core/cast/CastMarkers.kt` 与 `ReaderElement.RoleCast/BgmScene` 类型。详见 §4–§6。

---

## 1. `ReadBookContract.kt`（当前 1626 行）剩余精确编辑

### 1.1 按钮池 id（推荐删，防设置页出现“有 id 无动作”）

| 行号 | 首锚点 | 尾锚点 | 动作 |
|---|---|---|---|
| 390 | `"read_aloud",` | 同左 | 删整行 |
| 401 | `"multi_role_read",` | 同左 | 删整行 |
| 402–404 | `"multi_role_cast",` | `"ai_cast",` | 删 3 行 |
| 408 | `"source_custom_button", "change_source", "refresh", "download", "audio_download",` | 同左 | 只删 `"audio_download", `，其余 token 保留 |

**同批消费者（必须一起改，否则 UI 仍有死配置）：**

- `ReadButtonConfigDelegate.kt:214` 的 `DEFAULT_ENABLED_BUTTON_IDS` 含 `"read_aloud"`：删该字符串。
- `ReadMenuActionDefs.kt` 已无这些 id 的 `def(...)`/`ReadMenuButtonInfo` 条目（主控 10:53–10:56 已清），无需再改。
- `ReadBookButtonIds` 是 `internal`，被 `ReadMenuActionDefs.ReadMenuActionIds` 消费；`MoreActionIds` 被 `ReaderMoreActionsSheet`/`ReadButtonConfigDelegate` 消费。删除上面 id 后，两个池自动收缩。

### 1.2 其余精确编辑

| 行号 | 锚点 | 动作 | 说明 |
|---|---|---|---|
| 16 | `import io.legado.app.domain.model.AiReasoningLevel` | 删整行 | 朗读设置字段删完后仅剩 import（grep 计数 1） |
| 25 | `import kotlinx.collections.immutable.ImmutableSet` | 删整行 | 仅 `readAloudContentSplitSymbols` 用过，字段已删 |
| 28 | `import kotlinx.collections.immutable.persistentSetOf` | 删整行 | 同上 |
| 822 | `// TTS` | 删整行 | 空注释段 |
| 1585–1587 | `data class ReadAloudDetachReminderEnabled(val value: Boolean) : ConfigUpdate {` | 可选，整块删 | 若删，必须同批：`MoreConfigSheet.kt:410–416`、`ReadConfigUpdateDelegate.kt:697–700`；否则保留到 W4 |

### 1.3 已完成删除的 Contract 面（审计清单，勿回退）

- **UiState 字段**：`isReadAloudRunning`、`isReadAloudPaused`、`readAloudFollow`、`readAloudDetachReminderEnabled`、`readAloudEngineName`、`readAloudCharacterName`、`readAloudChapterPosition`、`readAloudChapterLength`、`preSynthesisConcurrency`、`audioCacheCleanTime`、`readAloudIgnoreAudioFocus`、`readAloudPauseOnPhoneCall`、`readAloudWakeLock`、`readAloudKeepOnExit`、`showReadAloudCapsule`、`capsuleAutoCollapse`、`readAloudCapsuleOffsetX/Y`、`readAloudMediaButtonPerNext`、`readAloudByPage`、`readAloudContentSplitSymbols`、`readAloudSystemMediaCompat`、`readAloudAndroidMediaControl`、`readAloudStreamAudio`、`readAloudTtsFollowSys`、`readAloudTtsSpeechRate`、`readAloudTtsTimer`、`readAloudFinishCurrentChapterAfterTimer`、`readAloudTimerChapters`、`speechAnalysisMode`、`speechAnalysisReasoningLevel`、`useMultiSpeaker`、`multiRoleCast`、`bgmAssign`、`bgmVolume`、`readAloudParagraphInterval`、`readAloudConfigTab`、`isReadAloudConfigOpen`。
- **Intent**：全部 `ReadAloud*`/`SetReadAloud*`/`SetBgm*`/`SetSpeech*`/`SetUseMultiSpeaker`/`SetMultiRoleCast`/`ConfirmRoleCast`/`CreateRoleCast`/`OpenAiCastDialog`/`OpenAiSceneDialog`/`StartAiCast`/`CancelAiCast`/`DeleteChapterCastAssignments`/`UnassignRoleCast`/`TextActionAloud`/`MediaButtonPressed`/`TtsProgress`/`OpenSystemTtsSettings`/`ClearTtsCache`/`OpenTts*`/`OpenBookVoiceCasting`/`OpenReadAloud*`/`OpenClassicReadAloudControls`。
- **Effect**：`TtsCacheCleared`、`UpAloudState`、`ToggleReadAloud`、`TextActionAloudPosition`、`TextActionSpeak`、`OpenSystemTtsSettings`、`OpenTtsEnginesAndVoices`、`OpenTtsCache`、`OpenBookVoiceCasting`、`OpenReadAloudSubPage`。
- **Sheet**：`AudioDownload`、`AiCastDialog`、`RoleCast`、`BgmScene`、`BgmSceneTable`、`ReadAloudConfig`、`PreDownloadConfig`、`PreSynthesisConcurrencyConfig`、`AudioCacheCleanConfig`、`ParagraphIntervalConfig`。
- **Route / enum**：`ReadBookMenuRoute.ReadAloud`、`ReadAloudSubPage` 整个枚举。
- **CloseReadBook**：已改为无参 `data object CloseReadBook : ReadBookIntent`；`readAloud()`/`keepReadAloud` 已无引用。

> 当前全仓 `grep` 已无 `ReadBookIntent.*Aloud` / `ReadBookEffect.*Aloud` / `ReadBookSheet.*Aloud` / `ReadBookMenuRoute.ReadAloud` 的未定义引用。

---

## 2. `ReadBookViewModel.kt`（当前 2348 行）

### 2.1 `when` 穷尽性：已修复，勿破坏

当前 `when (intent)` 从 **621** 行开始，已覆盖 211 个 intent（静态比对无缺口）。其中两个本不属于朗读域、但曾被误删的分支现在已在 **1302–1303**：

```kotlin
            ReadBookIntent.ConfirmAddCurrentBookToBookshelf -> addCurrentBookToBookshelfAndFinish()
            ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf -> removeCurrentNotShelfBookAndFinish()
```

- **为什么重要**：Kotlin 2.2 对 sealed 类型的 `when` **语句**也强制穷尽；这两个 intent 仍由 `ReadBookScreen.kt:225/232/234` 派发，处理器在 VM **1885/1907** 行。若后续再删/加 `ReadBookIntent` 成员，必须同步这里，不要用 `else -> Unit` 掩盖。
- **`TextActionAloud` 已从 Contract 删除**，因此当前无需分支；若有人恢复该 intent，必须补分支或删 intent，否则 623 行会再次报 non-exhaustive。

### 2.2 剩余清理

| 行号 | 动作 | 依据 |
|---|---|---|
| 5 | 删 `import android.speech.tts.TextToSpeech` | 文件已无 `TextToSpeech` / `sysEngines` 使用（grep 仅 import 本身） |
| 1301 | `ReadBookIntent.CloseReadBook -> closeReadBook()` 保持 | `keepReadAloud` 形参已从 Contract/VM 删除 |
| 1872 | `private fun closeReadBook()` 保持无参 | 原参数未被使用，删参后行为不变 |

> VM 已无任何 `readAloud*` 字段赋值；`refreshFromReadBook` 中的 `readAloudDetachReminderEnabled` 同步行已删，无需再动。

---

## 3. `ReadBookController.kt`（当前 3075 行）

**状态：45 个原错误已全部清除；当前无需再删任何朗读块。** 与朗读相关的 grep 只剩两处合法渲染字段：

| 行号 | 内容 | 处置 |
|---|---|---|
| 830 | `readAloudParagraphIndex = null,` | 保留。`ReaderPage.readAloudParagraphIndex` 仍是渲染模型字段（`feature/reader/core/model/ReaderPage.kt:370`），传 `null` 即关闭高亮。 |
| 1421 | `castRenderHash = castRenderHash,` | 保留。`ReaderChapterInput.castRenderHash` 是排版身份字段（默认 0），不是已删的 `CastRenderOptions`。 |

当前 Controller 已无：`TTS` / `ReadAloud` / `BaseReadAloudService` / `BgmSceneStore` / `ReadAloudSettingsGateway` import；`tts` 字段与 `speak()`；`composeVisibleBodyTextPositionProvider`；`updateReadAloudProgress` / `setComposeVisibleBodyTextPositionProvider` / `readAloudFromComposeVisibleStart` / `toggleReadAloud`；`menu_aloud` / `menu_insert_bgm_*` 菜单与 `openBgmSceneAtSelection`；`CastRenderOptions.load` 参数；`volumeKeyPageOnPlay` 朗读判定；所有 `ReadBookEffect.*Aloud*` 分支。

- `onComposeReaderElementClick` 的 `RoleCast`/`BgmScene` 已是 **562/564** 的 `false`（类型保留，只是不处理点击）。
- `onComposeTapAction` 的 `READ_ALOUD_*` 三个 action 已是 **596–597/616** 的 `Unit`（`ReaderTapAction` 枚举仍保留，属渲染输入层，W2c 再收）。
- `volumeKeyPage()` 当前已只按 `volumeKeyPage` 设置判断，无朗读播放子句。

---

## 4. `ReaderCanvasSurface.kt`（当前 3444 行）

**状态：14 个原错误已全部清除。** 当前实现符合“只删 cast/bgm 绘制与 visible-text-position provider”的边界：

- `ReaderVisibleTextPosition` / `ReaderVisibleTextPositionPolicy` import 已删；`onVisibleBodyTextPositionProvider` 形参及 `DisposableEffect` 已删；`ReadBookController.setComposeVisibleBodyTextPositionProvider` 与 `ReadBookRouteScreen` 的具名实参也已删（全仓 grep 0）。
- cast/bgm 绘制已改为 no-op：**2056–2057**（滚动栈）与 **2777–2778**（翻页）为 `is ReaderElement.RoleCast -> Unit` / `is ReaderElement.BgmScene -> Unit`。
- `drawCapsuleBackground` / `drawBgmScene` / `drawRoleCast` / `labelColor` / `drawCastPersonIcon` / `drawCastEffectMark` 已整块删除（grep 无定义）。
- ⚠️ **禁止误删** `readAloud: Color`：它是正文文字高亮色，当前在 **1521、1545、1896、1967、1992、2035、2119、2137–2150、2177、2194、2274、2287、2300、2603、2756** 等处使用。`readAloudParagraphIndex`（**2944** 的 `isReadAloud`）也是文字高亮判定，不是 cast/bgm，留到 W2c。
- 可延后：`castColorArgb` / `castVariantArgb` 参数链（1904–1905、1972–1973、1997–1998 等）在 cast 绘制 no-op 后已无实际消费者，但删它会改动滚动绘制热路径签名；本轮保留，登记 W2c。

---

## 5. `model/ReadBook.kt`（当前 1974 行）

### 5.1 `preserveReadAloudPosition` 语义保护（必须保留）

**语义**：`true` = 重载正文时保留当前阅读锚点，不跳回章首/旧位置；`false` = 允许按默认定位重排。它与朗读域已无关系，只服务“重载不跳位”。

| 位置 | 当前行 | 处置 |
|---|---|---|
| `curPageChanged(...)` | 1150–1158 | 形参保留；函数体已无朗读判定 |
| `loadContent(resetPageOffset, preserveReadAloudPosition, success)` | 1231–1245 | 转发链保留 |
| `relayoutContent()` | 1266–1271 | **固定传 `true`，不要改**（主题/规则重排保持位置） |
| `loadOrUpContent()` | 1273–1282 | 已传 `false`（原朗读判定来源删除后的等价结果） |
| `loadContent(index, ...)` | 1302–1308 | 形参保留 |
| `contentLoadFinish(...)` | 1523–1547 | 形参保留 |
| `layoutLoadedChapter(...)` | 1556–1617 | 形参保留，最终传 `curPageChanged` |
| `download(...)` | 1420–1452 | 形参保留，传 `CacheBook.download` |

结论：**不要按名字删参数，也不要改 `relayoutContent` 的 `true`**；否则换字体/主题/规则后阅读位置会跳回旧位置。当前唯一由朗读服务推导的调用点（`loadOrUpContent`）已经改为 `false`，保持现状即可。

### 5.2 可选死代码

- `upTextChapterAloudSpan(chapterStart: Int)` 当前 **1162–1170**，全仓无调用方（grep 仅定义本身）。可随 W2c 删除；不影响编译，不建议为本轮扩大改动面。

---

## 6. 当前外部消费者 / 残余设置

| 对象 | 当前消费者 | 处置 |
|---|---|---|
| `ConfigUpdate.ReadAloudDetachReminderEnabled`（Contract 1585–1587） | `MoreConfigSheet.kt:410–416`、`ReadConfigUpdateDelegate.kt:697–700`、`ReadConfigViewModel` 的设置流 | **可选**；本轮保留不会报错。要删就三处同批，否则登记 W4（备份/偏好兼容）。 |
| `ClickActionConfigSheet.kt:55–56, 63` | 点击动作 5/6/13 仍显示“上一段/下一段/暂停朗读” | 非编译阻塞；对应 `ReaderTapAction.READ_ALOUD_*` 仍是 no-op。建议 W2c 与枚举一起清。 |
| `ReadButtonConfigDelegate.kt:214` | `DEFAULT_ENABLED_BUTTON_IDS` 含 `"read_aloud"` | 若删 Contract 的 `read_aloud` id，同批删除；否则默认配置会指向不存在的动作（`loadTitleBarActions` 会静默丢弃，但设置页仍显示）。 |
| `ReadMenuActionDefs` | 已无朗读 id 的 icon/label/defs（主控已清） | 无需再改。 |
| `ReadBookFloatingActionBar` / `ReadBookMenuBar*` | 已无朗读状态字段引用（主控已清） | 无需再改。 |

---

## 7. 验证与剩余风险

### 7.1 只读反证命令（未运行 gradle）

```bash
cd /home/zx/workspace/legado-with-MD3
# 1) 朗读域实现引用（应只剩文字高亮 readAloud / ReaderPage 渲染字段）
grep -rnE 'BaseReadAloudService|ReadAloud\.|help\.readaloud|ReadAloudSettingsGateway|ReaderVisibleTextPosition' app/src/main/java --include='*.kt'
# 2) 未解析的 Contract 成员（静态脚本结果应为空）
#    本次已用 Python 对 ReadBookIntent/Effect/Sheet/MenuRoute 全量比对，0 条。
# 3) VM when 缺口（应为空）
grep -n 'ReadBookIntent.TextActionAloud\|ReadBookIntent.ConfirmAddCurrentBookToBookshelf\|ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf' app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt
# 注意：/tmp/w2b1.log 是 10:46 的 209 错旧日志，不代表当前工作区
```

### 7.2 剩余风险（按严重度）

1. **行号继续漂移**：主控仍可能在编辑；本报告行号为 10:57 快照，编辑前用首/尾锚点复核。`ReadBookContract` 已从 1929 行缩到 1626 行，`ReadBookMenuBar` 已缩到 879 行。
2. **`when` 穷尽性回归**：当前 VM 已覆盖全部 211 个 intent；再删/加 intent 必须同步 621 行的 `when`。`ConfirmAdd...` / `Exit...` 两个非朗读分支不要误删（`ReadBookScreen` 仍在派发）。
3. **`preserveReadAloudPosition`**：参数链与 `relayoutContent = true` 一旦误删，换字体/主题/规则后会跳回旧位置；按 §5 的 8 个锚点复核。
4. **Canvas `readAloud: Color`**：文字高亮色，不是朗读域；只允许删 cast/bgm 绘制与 visible-text-position provider。`readAloudParagraphIndex` 段落高亮留 W2c。
5. **核心类型保留**：`ReaderElement.RoleCast` / `BgmScene`、`feature/reader/core/cast/CastMarkers.kt` 仍在（W2 硬约束）；Canvas 只做 no-op 绘制，不要删除这些类型或波及分页/绘制链路。
6. **未验证项**：本报告未运行 gradle；`/tmp/w2b1.log` 为过期日志，最终以主控 `:app:compileAppDebugKotlin` 为准。

---

## 8. 附：10:46 旧错误点与 10:57 现状对照

| 旧错误组（`/tmp/w2b1.log`） | 旧位置 | 10:57 现状 |
|---|---|---|
| Controller imports / gateway / TTS | 43/60/63/68/78/138/327 | 已删；grep 无残留 |
| Controller speak/clearTts | 468–473/482–483 | 已删 |
| Controller visible provider / readaloud progress | 371/2288/2562–2578 | 已删 |
| Controller BGM/cast/readAloud | 1567/2055–2093/2461 | 已删；仅剩合法 `castRenderHash` |
| Controller tap/effect no-op | 624/661–665/1115–1117/2431–2464/2541–2548/2595–2614/2724 | 已清理 |
| Canvas imports/provider | 120–121/236/360–370 | 已删 |
| Canvas cast/bgm 绘制 | 2070–2076/2796–2802/3171–3410 | 已改 no-op/删函数 |
| Contract 朗读类型面 | 原 242–323/489–498/538–979/1011–1117/1149–1195 | 已删；剩余见 §1 |
| ViewModel 1 错 | 628（when） | 已修复；`when` 无缺口（见 §2） |
