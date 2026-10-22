# W2 导航宿主接入点侦察报告

> 快照时间：2026-10-21 10:52 CST  
> 分支：`subtract/novel-only`  
> 侦察范围：`MainNavGraph.kt` / `MainNavKey.kt` / `MainNavigator.kt` / `MainActivity.kt`，必要时 `MainScreen.kt` / `MainIntent.kt`  
> 重要前提：侦察期间主控已并行落盘了大部分删除。本报告同时给出 **当前残余清单** 与 **已应用删除的审计台账**；行号分两套：`HEAD:` 为改动前 209 错误快照的行号，`CUR:` 为当前工作区行号。若主控继续编辑，按锚点文本定位，不要只信行号。

## 0. 结论（先做这三件事）

1. **P0 源码唯一残余编译阻塞**：`ConfigNavScreen.kt:26` 的 `onNavigateToReadAloud` 参数未删，而 `MainNavGraph.kt:500-511` 已不再传它。删参数 + 删 `ConfigNavScreen.kt:70-74` 的“朗读设置”入口即可；不要用 `= {}` 默认值垫片，否则留下死 UI。
2. **P0 测试编译阻塞**：`MainPlayerRouteRestoreTest.kt` 整个删除；`MainIntentTest.kt` 删首个测试并去掉 `readAloud = true`；`MainNavigatorBackStackTest.kt:109` 去掉 `readAloud = true`。
3. **不要动**：`MainScreen` 底栏本体、`MainScreen.kt:233-234` 的通用形态判定、`MainActivity.kt:467` 的 `playerUnderlaySemantics(false)`、`MainNavigator` 的阅读页去重/回退逻辑。这些是减法后仍必须保留的宿主语义。

## 1. 当前文件状态

| 文件 | CUR 行数 | SHA-256（快照） | 状态 |
|---|---:|---|---|
| `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt` | 1050 | `e5305a5f5752c2c49c17de9559b486130632356592fb680e42efe457d51e9f4c` | 音频/朗读 entry、import、overlay 已删；仍有 1 个跨文件编译阻塞 + 死函数/无用 import |
| `MainNavKey.kt` | 205 | `ed5e0654a45ae1dd736c5d182746b7c09134a4aa890e31fd4650a24ae1b31945` | 朗读路由 key / `readAloud` 字段 / 常量已删；无残余 |
| `MainNavigator.kt` | 385 | `c17db21786693c78ec6bf2c811b12e17df8787471303832341e77b0fecd2b54c` | 音频 route 分支已删；仅剩两处“朗读胶囊”注释 |
| `MainActivity.kt` | 787 | `59f823536821f530fc0fb75d28b4af23e24e0fac4c061c9a565e47b22193ff6d` | 播放器叠层/回调已删；仅剩无用 import + 保留的 `playerUnderlaySemantics` |
| `MainScreen.kt` | 798 | `28504b977b340a1cb286230733445c6a073c6c7a1ce0abc1587943a4a8de7c7a` | 胶囊 slot / 参数已删；底栏本体保留；仅剩无用 import + 旧注释 |
| `ConfigNavScreen.kt` | 103 | — | **未改**；仍要求 `onNavigateToReadAloud`，当前唯一 nav 侧源码编译阻塞 |

### 1.1 当前 nav 域反证 grep（应无输出）

```bash
grep -RInE 'MainRoute(AudioPlay|ReadAloudPlayer|BookVoiceCasting|CloudTtsEngines|TtsCache|MultiRoleRule|VoicePool|BgmPool|VoiceEffect|CastCapsuleStyle|RegexCastRule|MultiRoleRecognition)|ROUTE_(AUDIO_PLAY|TTS_CACHE)|readAloud[[:space:]]*=' \
  app/src/main/java/io/legado/app/ui/main \
  app/src/main/java/io/legado/app/utils/ContextExtensions.kt \
  app/src/main/java/io/legado/app/utils/FragmentExtensions.kt
```

实测当前**无输出**（上述 route 名、常量、`readAloud =` 均已从 nav 宿主与两个扩展文件中消失）；`MainNavGraph.isReaderOnTop` 与 `MainNavigator` / `MainScreen` 的“胶囊”字样是普通注释，不含 route 名。

## 2. P0：当前仍需执行的精确编辑

### 2.1 `app/src/main/java/io/legado/app/ui/config/ConfigNavScreen.kt`（必须改，跨文件阻塞）

| 动作 | CUR 行号 | 首锚点 | 尾锚点 |
|---|---:|---|---|
| 删除参数 `onNavigateToReadAloud: () -> Unit,` | 26 | `onNavigateToRead: () -> Unit,` | `onNavigateToCover: () -> Unit,` |
| 删除整个 `ClickableSettingItem` 块 | 70-74 | `ClickableSettingItem(`（`title = stringResource(R.string.aloud_config)`） | `)`（`onClick = onNavigateToReadAloud` 后） |

改写后应形成：

```kotlin
onNavigateToRead: () -> Unit,
onNavigateToCover: () -> Unit,
```

以及：

```kotlin
ClickableSettingItem(
    title = stringResource(R.string.read_config),
    onClick = onNavigateToRead
)
ClickableSettingItem(
    title = stringResource(R.string.cover_config),
    onClick = onNavigateToCover
)
```

- 括号净差：参数删除 `{`/`}` 0/0；`ClickableSettingItem` 块本身无花括号，0/0；`SplicedColumnGroup` 的 `{}` 原样保留。
- 不选方案 B（给参数加 `= {}`）：会保留“朗读设置”入口但点击无反应，违反减法验收。
- `R.string.aloud_config` / `R.string.multi_role_rule_summary` 的字符串清理归 W5-6，不在本批删，避免牵动四语言资源。

### 2.2 测试编译（W2-S0 漏网，必须与 route key 删除同批）

| 文件 | 动作 | 行号/锚点 |
|---|---|---|
| `app/src/test/java/io/legado/app/ui/main/MainPlayerRouteRestoreTest.kt` | **整文件删除**（28 行，仅测试已删的 `MainRouteReadAloudPlayer` / `MainRouteAudioPlay` 反序列化） | 首：`class MainPlayerRouteRestoreTest {`；尾：文件末 `}` |
| `app/src/test/java/io/legado/app/ui/main/MainIntentTest.kt` | 删除第一个测试方法 `media control reader reuses task and requests home parent`（17-31），它引用已删的 `MainIntent.createReadBookMediaControlIntent` 和 `EXTRA_READ_ALOUD`；第 40 行 `createReadBookIntent(context, readAloud = true)` 改为 `createReadBookIntent(context)`；删掉不再使用的 `assertTrue` import | 首：`@Test`（17）；尾：`}`（31） |
| `app/src/test/java/io/legado/app/ui/main/MainNavigatorBackStackTest.kt` | 第 109 行 `MainRouteReadBook(readAloud = true)` → `MainRouteReadBook()`；测试名建议改为“reader with resetToHome replaces stale route…”（语义仍在测 `resetToHome`） | `val reader = MainRouteReadBook(readAloud = true)` |
| `app/src/test/java/io/legado/app/ui/main/MainNavRouteRegistryTest.kt` | 可选清理：110-112 行白名单中的 `MainRouteBookVoiceCasting` / `MainRouteCloudTtsEngines` / `MainRouteTtsCache` 三个字符串删掉；不删也不会编译失败，但会掩盖“白名单指向不存在 route”的腐化 | `"MainRouteBookVoiceCasting",` … `"MainRouteTtsCache",` |
| `app/src/test/java/io/legado/app/ui/book/read/ReadBookDomainSplitBoundaryTest.kt` | 仅注释提到 `MainRouteReadAloudPlayer`（143 行），不影响编译；随 W2-S4 文档清理 | `（MainRouteReadAloudPlayer）` |

### 2.3 死代码 / 旧注释（不阻塞编译，但属于本次删除制造的无用代码）

| 文件 | 动作 | CUR 行号 | 说明 |
|---|---|---:|---|
| `MainNavGraph.kt` | 删除 `isReaderOnTop` 整个 KDoc + 函数 | 214-221 | 原供 `AudioPlayerMorphOverlay` 的“经典控制”判定；唯一调用方已随 overlay 删除，当前零引用。净花括号 0/0。 |
| `MainNavGraph.kt` | 修改注释 `...search scene or player host.` | 204 | 已无 player host，改为 `search scene or source-login overlay` |
| `MainNavigator.kt` | 修改两处“全局朗读胶囊”注释 | 33、240 | 改为“导航栈跟踪器”；逻辑不动 |
| `MainScreen.kt` | 修改注释“没有胶囊的底栏” | 603 | 改为“不把空位拉满” |
| `MainNavGraph.kt` | 删除无用 import | 5、6、7、22、24、25、30、31、41、42、43、44、49、63、64、109、113、120、121 | 逐行确认零引用；`getValue`/`setValue` 因 `by` 委托保留 |
| `MainActivity.kt` | 删除无用 import | 27、30、32、33、35、37、89、92、93 | `CompositionLocalProvider` / `derivedStateOf` / `key` / `mutableStateOf` / `rememberCoroutineScope` / `rememberSaveable` / `first` / `Mutex` / `withLock`；`getValue`/`setValue` 保留 |
| `MainScreen.kt` | 删除无用 import | 9、10、11、12、57 | `animateDpAsState` / `animateFloatAsState` / `snap` / `spring` / `rememberSaveable`，均只服务已删的胶囊展开动画 |

> `FloatingBottomBar.kt` 仍保留 `compactProgress` / `expandedWidth` / `onRestoreNavigation` 三个已无调用方的参数及其分支。它们是通用组件 API，不是 nav 宿主接入点；本轮**不建议**顺手删，避免把风险扩散到组件层。可在 W2-S4 单独清理。

## 3. route key / entry / navigator 同批删除台账（已在工作区应用）

### 3.1 为什么必须三件套同批

`MainRoute` 是 `@Serializable sealed interface`，`MainNavGraph.entryProvider` 的 `when` 由 `MainNavigator.navigateToRoute` 消费：

- **删 key、留 entry** → 编译错误（`entry<MainRouteXxx>` 找不到类型）。
- **删 key、留 navigator 分支** → 编译错误（分支引用未解析）。
- **留 key、删 entry** → 编译通过，但运行时导航到该 key 抛 `IllegalStateException("Unknown screen …")`（`MainNavRouteRegistryTest` 就是为这个失效模式建的）。
- **留 key、删 navigator 分支** → `when` 作为语句不强制穷尽，route 不压栈，表现为“点击无反应”；tracker 仍会收到未变化的栈快照。
- **删 key、留旧 saved back stack / 旧 Intent** → 反序列化异常或 `resolveStartRoute` 落到 `else -> MainRouteHome`（静默回主页）。本分支未发布，可接受；若要保升级兼容，只能保留 key + 空 entry，不能只保一半。

### 3.2 `MainNavKey.kt`（HEAD 266 → CUR 205）

| HEAD 行号 | 删除对象 | 动作 | 状态 |
|---:|---|---|---|
| 4 | `import io.legado.app.ui.book.read.ReadAloudSubPage` | 删 import | 已删 |
| 27-29 | `data object MainRouteReadAloudPlayer : MainRoute` | 整块删（含 KDoc） | 已删 |
| 121 | `val readAloud: Boolean = false,`（`MainRouteReadBook`） | 删字段 | 已删 |
| 127-132 | `MainRouteAudioPlay` data class | 整块删 | 已删 |
| 151-160 | `MainRouteBookVoiceCasting` / `MainRouteCloudTtsEngines` / `MainRouteTtsCache` | 整块删 | 已删 |
| 183-209 | `MainRouteMultiRoleRule` / `VoicePool` / `BgmPool` / `MultiRoleRecognition` / `VoiceEffect` / `CastCapsuleStyle` / `RegexCastRule` | 整块删（含各自 KDoc） | 已删 |
| 214-219 | `fun ReadAloudSubPage.toMainRoute()` | 整块删 | 已删 |
| 249 | `ROUTE_AUDIO_PLAY` | 删常量 | 已删 |
| 265 | `ROUTE_TTS_CACHE` | 删常量 | 已删 |

括号：只有 `toMainRoute` 的表达式体 `= when (this) { … }` 有 1 个 `{` / 1 个 `}`，净差 0；其余 data class/object/常量删除 0/0。

### 3.3 `MainNavigator.kt`（HEAD 440 → CUR 385）

| HEAD 行号 | 删除对象 | 首/尾锚点 | 括号净差 | 状态 |
|---:|---|---|---:|---|
| 99 | `MainRouteReadAloudPlayer -> backStack.add(route)` | 单行 | 0/0 | 已删 |
| 149-167 | `is MainRouteAudioPlay -> { … }` 单例播放器栈语义分支 | `is MainRouteAudioPlay -> {` / `}` | 6/6 = 0 | 已删 |
| 206-222 | `is MainRouteBookVoiceCasting, is MainRouteCloudTtsEngines, MainRouteTtsCache -> { … }` | `is MainRouteBookVoiceCasting,` / `}` | 3/3 = 0 | 已删 |
| 250 | 多标签分支中的 `MainRouteMultiRoleRule,` | `MainRouteHighlightTagRule,` / `MainRouteReadRecord ->` | 0/0 | 已删（只删该行，保留前后逗号） |
| 261-268 | `MainRouteVoicePool, MainRouteBgmPool, MainRouteVoiceEffect, MainRouteCastCapsuleStyle, MainRouteRegexCastRule, MainRouteMultiRoleRecognition -> backStack.add(route)` + 上方 KDoc | `// 朗读设置的子页…` / `... -> backStack.add(route)` | 0/0（单行分支） | 已删 |
| 397 | `readAloud = intent?.getBooleanExtra(MainIntent.EXTRA_READ_ALOUD, false) == true,` | 单行 | 0/0 | 已删 |
| 404-407 | `MainRouteConst.ROUTE_AUDIO_PLAY -> MainRouteAudioPlay(…)` | `MainRouteConst.ROUTE_AUDIO_PLAY ->` / `)` | 0/0 | 已删 |

保留项（不要误删）：`MainRouteReadBook` 的两段去重逻辑（HEAD 58-76 / CUR 58-76）——详情页打开已存在阅读页、栈顶重复阅读页不压栈；`resetToHome` 清栈（CUR 51-54）。

### 3.4 `MainNavGraph.kt`（HEAD 1460 → CUR 1050）

#### 导入 / 签名 / 普通调用点

| HEAD 行号 | 删除对象 | 动作 | 状态 |
|---:|---|---|---|
| 45-46 | `PlayerMorphAppearance` / `PlayerMorphHost` | 删 import | 已删 |
| 48-49 | `PlaybackCapsuleSource` / `PlaybackCapsuleState` | 删 import | 已删 |
| 52 | `AudioPlay` | 删 import | 已删 |
| 54 | `ReadAloudSessionStore` | 删 import | 已删 |
| 56-57 | `AudioPlayService` / `BaseReadAloudService` | 删 import | 已删 |
| 61-64 | `ui.book.audio.*` 4 个 import | 删 import | 已删 |
| 86-102 | `ui.book.readaloud.*` 17 个 import | 删 import | 已删 |
| 282-290 | `mainEntryProvider` 的 `readAloudMorph` / `homePlaybackCapsuleEnabled` / `capsuleAnchorPreview` 参数 + KDoc | 删整块 | 已删 |
| 450-452 | `MainScreen(...)` 对应三个实参 | 删三行 | 已删 |
| 478-494 | `onOpenBookshelfBook` 的 `if (book.isAudio) MainRouteAudioPlay else MainRouteReadBook` | 改写为始终 `MainRouteReadBook(...)` | 已改（净 3/3 = 0） |
| 554-555 | `ConfigNavScreen` 的 `onNavigateToReadAloud = { backStack.add(MainRouteMultiRoleRule) }` | 删两行（注释 + 实参） | 已删；**参数本身仍在 ConfigNavScreen，见 §2.1** |

#### 阅读页内部

| HEAD 行号 | 删除对象 | 首/尾锚点 | 括号净差 | 状态 |
|---:|---|---|---:|---|
| 746-754 | `readAloudSessionStore` + `readAloudFollow` + `LaunchedEffect` 收集 `followReadAloudPosition` | `// 是否跟随朗读位置…` / `}` | 4/4 = 0 | 已删 |
| 760-765 | `if (BaseReadAloudService.isRun && readAloudFollow[0]) { … BackToSpeakingPosition }` | `// 回到阅读界面时…` / `}` | 1/1 = 0 | 已删 |
| 810-821 | `onOpenVoiceCasting` / `onOpenReadAloudSubPage` / `onOpenTtsEnginesAndVoices` / `onOpenTtsCache` 四个 lambda 实参 | `onOpenVoiceCasting = { bookUrl ->` / `},`（`onOpenTtsCache` 后） | 4/4 = 0 | 已删 |
| 832 | `DisposableEffect(controller, lifecycleOwner, route.readAloud)` | 改写为 `DisposableEffect(controller, lifecycleOwner)` | 花括号不增不减 | 已改 |
| 837-841 | `controller.onStartContentLoadFinish = { if (route.readAloud) ReadBook.readAloud() }` | `controller.onStartContentLoadFinish = {` / `}` | 2/2 = 0 | 已删 |
| 866 | `controller.clearTts()` | 单行 | 0/0 | 已删 |

> 风险点：**不要整块删 `DisposableEffect`**。它同时包含 `activeReadBookInputHandler`、生命周期观察者、`pauseReader()`、`ReadBookIntent.OnDispose`、系统栏恢复；只改 key 行 + 删 5 行 TTS 回调。当前文件花括号仍平衡（243/243）。

#### 整屏 entry

| HEAD 行号 | 删除对象 | 首/尾锚点 | 括号净差 | 状态 |
|---:|---|---|---:|---|
| 888-893 | `entry<MainRouteReadAloudPlayer>` 兼容旧栈 | `entry<MainRouteReadAloudPlayer>(` / `}` | 4/4 = 0 | 已删 |
| 896-907 | `entry<MainRouteAudioPlay>` 兼容旧栈 | `entry<MainRouteAudioPlay>(` / `}` | 4/4 = 0 | 已删 |
| 1109-1166 | `MainRouteBookVoiceCasting` / `MainRouteCloudTtsEngines` / `MainRouteTtsCache` 三个 entry | `entry<MainRouteBookVoiceCasting>` / `entry<MainRouteTtsCache>` 结束 `}` | 17/17 = 0 | 已删 |
| 1204-1251 | `MainRouteMultiRoleRule` / `CastCapsuleStyle` / `VoicePool` / `BgmPool` / `VoiceEffect` / `MultiRoleRecognition` / `RegexCastRule` 七个 entry | `entry<MainRouteMultiRoleRule>` / `entry<MainRouteRegexCastRule>` 结束 `}` | 21/21 = 0 | 已删 |
| 1277-1460 | `MainActivity.AudioPlayerMorphOverlay(...)` 整个 @Composable 函数 | `/** 有声书的 Android 宿主动作… */` / 文件末 `}` | 35/35 = 0 | 已删（当前文件到 `mainEntryProvider` 结束） |

#### `isReaderOnTop`

HEAD 249-250 的 `isReaderOnTop` 当前仍在 CUR 214-221，但已零引用（原唯一调用方是已删的 `AudioPlayerMorphOverlay`）。动作：删 KDoc + 函数，净 0/0。这是本次删除制造的 dead code，应清掉；不删不影响编译。

### 3.5 `MainActivity.kt`（HEAD 808 → CUR 787）

已应用的删除（只列宿主接入点，不逐行抄 289 行 diff）：

| HEAD 行号 | 删除对象 | 说明 |
|---:|---|---|
| 59、62-64、73、77-78、82-88 | 朗读/播放相关 import | `ReadAloudSettingsRepository`、`PlaybackCapsuleGateway`、`PlaybackCapsuleSource/State`、`AudioPlay`、`AudioPlayViewModel`、`ReadAloudControlsRequestBus`、`ReadAloudPlayerMorphHost`、`ReadAloudPlayerOverlayBus`、`ReadAloudShellHost`、`CapsuleAnchorKind`、`LocalReadAloudMorph`、`rememberReadAloudMorphState`、`ReadAloudPlayerViewModel` |
| 117 | `MainActivity : BaseComposeActivity(), AudioPlay.CallBack` | 改为 `MainActivity : BaseComposeActivity()` |
| 124 | `activeAudioPlayViewModel` 字段 | 删 |
| 137 | `KEY_RESTORE_READ_ALOUD` | 删 |
| 145 | `hasActiveAudioPlayRoute` | 删 |
| 201、207 | `createReadBookIntent(readAloud = ...)` 参数/实参 | 删，保留 `inBookshelf`/`chapterChanged` |
| 212-224 | `createAudioPlayIntent` 包装 | 删 |
| 291 | `readAloudSettingsRepository` 注入 | 删 |
| 404-407 | `onNewIntent` 的 `EXTRA_OPEN_READ_ALOUD_PLAYER -> ReadAloudPlayerOverlayBus.request()` | 删 |
| 449 | `resolved is MainRouteAudioPlay -> arrayOf(MainRouteHome)` | 删 |
| 476-574 | 全局播放器状态、`readAloudMorph`、`playbackGateway`、`openPlayer`、`ReadAloudPlayerOverlayBus.events` 收集、`useHomeCapsule`、胶囊锚点预览 | 删 |
| 582-591 | `routeEvents` 中 `MainRouteAudioPlay` 分支 | 删 |
| 618 | `.playerUnderlaySemantics(playerVisible || morphPresent)` | **保留调用，改为 `.playerUnderlaySemantics(false)`**（CUR 467） |
| 710-726 | `onNavigateToRoute` 中 `MainRouteAudioPlay` 播放器分流 | 删，统一走 `MainNavigator.navigateToRoute` |
| 735-737 | `mainEntryProvider(...)` 的三个播放参数 | 删 |
| 741-828 | `CompositionLocalProvider(LocalReadAloudMorph…)`、`ReadAloudShellHost`、`ReadAloudPlayerMorphHost`、`AudioPlayerMorphOverlay` 调用 | 删 |
| 686-687 | `outState.putBoolean(KEY_RESTORE_READ_ALOUD, readRoute.readAloud)` | 删 |
| 695-699 | `restoreReadBookRoute` 的 `readAloud = ...` | 删 |
| 769-786 | `AudioPlay.CallBack` 的 `upLoading/upLyric/upLyricP` 重写 | 删 |

保留项：
- `playerUnderlaySemantics`（CUR 58 import / 467 调用）**保留**。`false` 表示当前无模态播放器，不清理下层语义；若未来恢复播放器，应改为 `playerPresent` 实参，而不是删除该 modifier。
- `BackHandler(enabled = !configuration.appShell.predictiveBackEnabled)`（CUR 578）是删除 `shouldHandleActivityBack(predictiveBackEnabled, playerPresent)` 后的正确等价形态；没有播放器时不需要再让位。
- `onSaveInstanceState` / `restoreReadBookRoute` 仍保留 `bookUrl`、`inBookshelf`、`chapterChanged` 三个字段，不能连带删掉。
- `PrivateAppStartGate`、`SharedTransitionLayout`、`rememberViewModelStoreNavEntryDecorator`、`ModalOverlaySceneStrategy` 均与朗读无关，未动。

### 3.6 `MainScreen.kt`（HEAD 798 → CUR 798）

已应用的删除：
- HEAD 84-88：`ReadAloudBarCapsuleEndPadding` / `Size` / `Slot` / `ReadAloudPlayerOverlayBus` / `ReadAloudMorphState` import。
- HEAD 134-137：`readAloudMorph` / `homePlaybackCapsuleEnabled` / `capsuleAnchorPreview` 参数。
- HEAD 240-244：`shouldUseHomePlaybackCapsule(...)` 调用。
- HEAD 601-735 一带：`readAloudCapsuleShown`、`playbackControlsExpanded`、`expansion`、`capsuleReserve`、`expandedCapsuleWidth`、`capsuleWidth`、`navigationWidth`、`ReadAloudBarCapsuleSlot`、`FloatingBottomBar` 的 `compactProgress/expandedWidth/onRestoreNavigation` 实参。

保留项见 §4.1。

### 3.7 `MainIntent.kt`（已应用，供审计）

- 删除 `EXTRA_OPEN_READ_ALOUD_PLAYER`、`EXTRA_READ_ALOUD`。
- `createReadBookIntent` 删除 `readAloud` 参数与 `putExtra(EXTRA_READ_ALOUD, ...)`。
- 删除 `createReadBookMediaControlIntent`。
- 删除 `createAudioPlayIntent` 及 `ROUTE_AUDIO_PLAY` 写入。
- `MainActivity.createReadBookIntent` 包装同步删除 `readAloud`；`ContextExtensions.kt` / `FragmentExtensions.kt` 的 `book.isAudio -> createAudioPlayIntent` 已改为统一 `createReadBookIntent`。
- `BookInfoRouteScreen` 的 `onOpenAudioPlay` 分支已改为直接 `onOpenReader`（当前 `BookInfoRouteScreen.kt:130-136`），不再有音频 book 的 no-op 分支。

## 4. 必须保留的语义

### 4.1 底栏本体与通用形态判定

`MainScreen.kt:233-234` 现在是：

```kotlin
val useFloatingBottomBar =
    mainUiState.showBottomView && mainUiState.useFloatingBottomBar && !useRail
```

它等价于原 `shouldUseHomePlaybackCapsule(onMainRoute = true, showBottomView, useFloatingBottomBar, useRail)`，因为 `MainScreen` 只在主页组合，`onMainRoute` 恒为 true。**必须保留四个条件中的三个变量项**：

- `showBottomView`：设置里隐藏底栏时不能仍画悬浮底栏；
- `useFloatingBottomBar`：普通/悬浮两种底栏形态切换；
- `!useRail`：平板 rail 模式不能再叠一个底栏。

不要简化为 `mainUiState.useFloatingBottomBar`。同时保留：
- `if (!useRail && mainUiState.showBottomView && !useFloatingBottomBar) AppNavigationBar(...)`（CUR 423-...）——非悬浮底栏本体；
- `if (useRail && mainUiState.showBottomView) WideNavigationRail...`（CUR 249 附近）——rail 本体；
- `FloatingBottomBar` 内的自然宽度居中算法（CUR 602-608）：`normalNavigationWidth` 取最小自然宽度、`navigationStartInset` 居中，不要拉满 `maxWidth`；
- `FloatingBottomBarItem` 的 `enabled` 参数已删（原用于胶囊展开时禁用导航项），现在是默认可用；不要为了“恢复”再引入胶囊状态。

### 4.2 `playerUnderlaySemantics`

`MainActivity.kt:58` import、`MainActivity.kt:467` 调用：

```kotlin
.fillMaxSize()
.playerUnderlaySemantics(false)
```

保留。它是通用的“播放器下层语义”边界：当前 `false` 等价于 `this`（无播放器，正常暴露下层控件）；未来若有播放器叠层，传 `playerPresent`。删除该调用不会立即改变行为，但会丢掉这层契约，后续恢复播放器时容易漏掉无障碍隔离。

### 4.3 `MainNavigator` 的阅读页语义

- `resetToHome` 先清栈再压 `MainRouteHome`（CUR 51-54）。
- `MainRouteReadBook` 从 `MainRouteBookInfo` 打开时，若栈中已有同 `bookUrl` 的阅读页，回退到该页而不是新压一份；`chapterChanged` 时替换栈内条目（CUR 58-69）。
- 栈顶已是同书/空 `bookUrl` 的 `MainRouteReadBook` 时不重复压栈（CUR 71-76）。
- `ReadBook.prefetchForOpen` 只在非 `chapterChanged` 时触发（CUR 78-82）。
- `navigateBack` 的 `fromRoute` 校验、`backNavigationInProgress` 防抖、根页 `finish()` 语义不变（CUR 244-266）。

这些都与朗读无关，是减法后阅读主链路的核心，禁止为了让 `navigateToRoute` 的 `when` 变短而删除。

### 4.4 启动/恢复语义

- `MainActivity.startRoutes` 的 `restoredReadBookRoute`、`defaultToRead`、`shouldOpenRouteWithHomeParent` 分支保留；只删除了 `resolved is MainRouteAudioPlay`。
- `restoreReadBookRoute` 仍恢复 `bookUrl` / `inBookshelf` / `chapterChanged`；`KEY_RESTORE_READ_ALOUD` 删除后，旧 Bundle 中的该键会被忽略，不会崩溃。
- `MainIntent.createReadBookIntent` 仍写 `EXTRA_START_ROUTE = ROUTE_READ_BOOK`、`EXTRA_BOOK_URL`、`EXTRA_IN_BOOKSHELF`、`EXTRA_CHAPTER_CHANGED`；`MainNavigator.resolveStartRoute` 仍按这些键构造 `MainRouteReadBook`。

## 5. 括号平衡审计

### 5.1 当前文件总账（静态字符计数）

| 文件 | `{` | `}` | 净差 |
|---|---:|---:|---:|
| `MainNavGraph.kt` | 243 | 243 | 0 |
| `MainNavKey.kt` | 1 | 1 | 0 |
| `MainNavigator.kt` | 63 | 63 | 0 |
| `MainActivity.kt` | 112 | 112 | 0 |
| `MainScreen.kt` | 141 | 141 | 0 |
| `ConfigNavScreen.kt`（待改） | 7 | 7 | 0 |

### 5.2 各删除块净差

| 块 | `{` | `}` | 净差 |
|---|---:|---:|---:|
| `MainNavGraph` 有声书/CloudTts/TtsCache 三 entry（HEAD 1109-1166） | 17 | 17 | 0 |
| `MainNavGraph` 朗读设置七 entry（HEAD 1204-1251） | 21 | 21 | 0 |
| `MainNavGraph` `AudioPlayerMorphOverlay`（HEAD 1277-1460） | 35 | 35 | 0 |
| `MainNavGraph` `onStartContentLoadFinish`（HEAD 837-841） | 2 | 2 | 0 |
| `MainNavGraph` 书架 `if (book.isAudio)`（HEAD 478-494） | 3 | 3 | 0 |
| `MainNavGraph` `readAloudSessionStore` 块（HEAD 746-754） | 4 | 4 | 0 |
| `MainNavGraph` `resumeReader` 对齐 if（HEAD 760-765） | 1 | 1 | 0 |
| `MainNavigator` `MainRouteAudioPlay` 分支（HEAD 149-167） | 6 | 6 | 0 |
| `MainNavigator` BookVoiceCasting/CloudTts/TtsCache 分支（HEAD 206-222） | 3 | 3 | 0 |
| `MainNavKey` `toMainRoute`（HEAD 214-219） | 1 | 1 | 0 |
| `ConfigNavScreen` 参数 + item（CUR 26、70-74） | 0 | 0 | 0 |

### 5.3 重点风险

1. **`DisposableEffect` 不能整块删**：它的 `{` 同时包住 reader 生命周期与 `onDispose`；只改 key 行 + 删 `onStartContentLoadFinish` 五行。整块删会少 4+ 个 `}`，直接把 `entry<MainRouteReadBook>` 之后的结构打散。
2. **`AudioPlayerMorphOverlay` 是文件末尾函数**：整块删到 EOF 后，`mainEntryProvider` 的 `}` 必须保留。当前 `MainNavGraph` 最后一个字符是该 `}`，总账 243/243。
3. **`MainNavigator` 多标签分支**：`MainRouteMultiRoleRule,` 只是 `MainRouteHighlightTagRule, MainRouteReadRecord ->` 前的一行；删时保留逗号结构，不要删成 `MainRouteHighlightTagRule MainRouteReadRecord ->`。
4. **`MainNavKey` 的 `toMainRoute` 是表达式体 `= when (...) { ... }`**：它的 `}` 是函数体结束，不是独立 block；删时整段 214-219 一起删，别只删 `when` 分支。
5. **`ConfigNavScreen` 的 `ClickableSettingItem` 没有花括号**：删除 70-74 时不要误删 `SplicedColumnGroup {` / `}`；当前 7/7 平衡，改后仍应为 7/7。

## 6. 运行期风险 / 未验证项

1. **旧导航栈兼容性**：删除 `MainRouteReadAloudPlayer` / `MainRouteAudioPlay` 等可序列化 key 后，旧版本保存的 Nav3 back stack 在进程恢复/升级时可能反序列化失败。`MainPlayerRouteRestoreTest` 原本就是保这个语义的，现应删除。若产品要求跨版本恢复，则不能删 key，只能保留 key + 空 entry；这与 D-d“无消费者全删”冲突，需要主控裁决。
2. **旧 Intent/PendingIntent**：旧 `ROUTE_AUDIO_PLAY = "book/read/audio"` 的 Intent 仍可能由旧通知/桌面快捷方式触发。`resolveStartRoute` 已无该分支，会落到 `else -> MainRouteHome`，表现为静默回主页，不会崩溃。可接受则保留；不可接受则加一次性迁移/清理。
3. **`MainRouteReadBook` 序列化 schema 变化**：删除 `readAloud` 字段后，旧 JSON 中多出的键在默认 `Json` 配置下可能触发 `unknown key`（取决于 Nav3 的 serializer 配置）；与第 1 条同批处理。当前没有 `ignoreUnknownKeys` 兜底，建议在 W2-S4 明确“旧栈不兼容”或加迁移。
4. **音频书数据仍在**：`BookType.audio`、`Book.isAudio`、`BookDao` 音频查询保留到 W2-S6；`ContextExtensions` / `FragmentExtensions` 现在把所有书统一送 `MainRouteReadBook`，`BookInfoRouteScreen` 也走 `onOpenReader`。旧音频书点开可能进入文本阅读器（内容为空/不可读），这是数据层未清完的临时表现，W2-S6 前不要把它当成回归。
5. **未运行 gradle**：按任务约束只读分析，未执行 `:app:compileAppDebugKotlin` 或测试。`ConfigNavScreen` 的缺失参数是静态确定的编译错误；其余“无编译错误”结论基于对当前源码的符号 grep，不是编译证明。主控编辑后必须立即编译确认。
6. **主控并发编辑**：本报告写入时 `MainNavGraph.kt` 已在 10:48 被主控从 1460 行削到 1050 行，`MainNavKey.kt` 10:49、`MainNavigator.kt` 10:49、`MainActivity.kt` 10:48 均已改。若主控继续编辑，§2 的 CUR 行号可能漂移；用锚点文本和 §1.1 的 grep 复核。

## 7. 建议的收口顺序

1. 改 `ConfigNavScreen.kt`（§2.1）→ 编译 `:app:compileAppDebugKotlin`。
2. 删 `MainPlayerRouteRestoreTest.kt`、改 `MainIntentTest.kt` / `MainNavigatorBackStackTest.kt`（§2.2）→ 编译 `testAppDebugUnitTest`。
3. 删 `MainNavGraph.isReaderOnTop` + 清无用 import（§2.3，可选但推荐，属本次删除制造的 dead code）。
4. 跑 §1.1 反证 grep；确认 `MainNavRouteRegistryTest` 仍通过（每个声明的 `MainRoute` 都有 `entry<>`）。
5. 再跑完整验证集（`testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug`），重点看旧导航栈恢复测试与 `MainNavRouteRegistryTest`。
