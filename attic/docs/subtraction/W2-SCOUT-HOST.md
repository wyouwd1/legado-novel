# W2 宿主三件侦察报告（Scout-宿主三件）

> 来源：`bg_delegate`（inspect 只读），task `dccbf3359a4c27eda9be8ccd6919f7d76`
> 模型 `new-api/deepseek-v4.1-flash` · 21 turns / 51 tool calls
> 产出 22576 bytes，sha256 `c52e49f47e0d17d411545f97fd90a000980d483cfc8a0208fdd25ecff87484b3`（已验证）
> **边界声明**：报告的行号基于编译 10（`/tmp/w2a10.log`）；三文件自编译 10 起未改动，行号仍有效。

侦察目标：`MainNavGraph.kt`(140 错) · `MainActivity.kt`(85 错) · `MainScreen.kt`(18 错)

---

## 1. 结论（Scout 整体判断）

1. 三文件 242 个错误 **100% 由已删朗读/有声书/胶囊符号派生**，无一条是渲染或导航逻辑自身的缺陷。
   伴随的 `Cannot infer type 'T'/'R'`、`Property delegate must have a getValue`、`Inapplicable candidate`、
   `No parameter with name` 都是同一批未解析符号的**二次报错**，删掉根符号即消失。
2. **删除顺序：`MainScreen` → `MainActivity` → `MainNavGraph`**（更省编）。
   理由：`MainScreen` 的 3 个胶囊参数 + 胶囊槽是 `MainNavGraph:446-451` / `MainActivity:735-737` 的**上游**。
3. **唯一「改写」而非「删」的点**：`shouldUseHomePlaybackCapsule`（见 §3）。
4. ⚠️ **编译通过 ≠ 收尾**：三文件内另有约 20 行「能编译但语义已死」的残留（见 §5），
   漏掉不报错但会留下朗读接线。
5. ⚠️ **不能只删 entry 保留 route key**：`MainNavKey.kt` 的朗读 route 键与本批 entry 必须**同批**处理（见 §4）。

---

## 2. 整块删除候选（按 Scout 给出的行号）

### MainNavGraph.kt（1461 行）
- `746–754` `readAloudSessionStore` 订阅块（含 746 注释）
- `760–765` `resumeReader` 内「回到朗读位置」if 块
- `886–903` 两个「兼容旧栈」entry（ReadAloudPlayer / AudioPlay，注释在 887、895）
- `1109–1166` BookVoiceCasting + CloudTtsEngines + TtsCache 三个 entry
- `1204–1252` MultiRoleRule + 6 个 cast/voice 子页 entry
- `1278–1461` `AudioPlayerMorphOverlay` 整函数（有声书宿主）
- `249–252` `isReaderOnTop`（删 762-829 后成死代码；先确认无新调用方）
- 连带（须同批以保编译）：`479–485` `isAudio` 分支 · `546–556` `ConfigNavScreen onNavigateToReadAloud` · `837–841` 阅读页 `route.readAloud` 分支

### MainActivity.kt（1047 行）
- `486–547` 播放浮层状态 + `openPlayer` + 初始 `openPlayer` 触发
- `548–572` `playerVisible`/`matchingCapsule`/`capsuleAnchorPreview`/`useHomeCapsule`/`expectCapsuleAnchors`
- `710–727` · `735–737` · `744–759` · `762–829` · `831–840`（entryProvider 实参 + 全局胶囊 + 听书浮层 + 有声书浮层 + BackHandler）
- `1031–1047` `AudioPlay.CallBack` 三个 override（含 1031 注释）
- `404–406` `onNewIntent` 内朗读播放器分支

### MainScreen.kt（~755 行）
- `604–606` 胶囊显示状态；`625–634` 胶囊预留宽度；`643–649` 胶囊宽度计算；`716–732` 胶囊 Slot 调用
- `135–137` 三个参数（连带 MainNavGraph 446-451）
- ⚠️ **不可删**：`if (useFloatingBottomBar) { … FloatingBottomBar(…) }`（602-733）与其内 660-715 的导航项循环
  —— 这是 **D7a 三 tab 底栏本体**；只删胶囊插槽与宽度修正项。

---

## 3. 语义保护（必须保留 / 不可按「删行」处理）

### 3.1 `shouldUseHomePlaybackCapsule` —— 唯一需要「改写」的点

- 消费者：`MainScreen:243`、`MainActivity:566`
- 定义原在 `ui/main/PlaybackPlayerAnchors.kt:15`，该文件已随朗读域删除
- **原式已从 `070ca1a9b` 取回**：
  ```kotlin
  internal fun shouldUseHomePlaybackCapsule(
      onMainRoute: Boolean,
      showBottomView: Boolean,
      useFloatingBottomBar: Boolean,
      useRail: Boolean,
  ): Boolean = onMainRoute && showBottomView && useFloatingBottomBar && !useRail
  ```
- **判定**：这是**纯通用的底栏形态判定**，只是名字带 capsule。
  被 `MainScreen` 用来决定底栏与液态玻璃（245 `useLiquidGlass`、602 `if (useFloatingBottomBar)`）。
- **处置（主控决定）**：**内联到两个调用点**（只有 2 处消费者，且 `MainScreen` 处 `onMainRoute` 恒 `true`），
  不重建单方法抽象——符合仓库「不为单个逻辑建包装」的约束。

### 3.2 MainScreen 602-733
`if (useFloatingBottomBar) { FloatingBottomBar(...) }` = D7a 三 tab 底栏结构，**本轮不动**；只剥胶囊。

### 3.3 MainNavKey.kt 的 route 键（阻塞项，见 §4）
全部朗读 route 键仍在。**本批只删 entry 会让 NavDisplay 收到无 entry 的 key（运行期崩溃）**，
故 entry 与 route key 必须同批。

### 3.4 MainActivity 117 / 1031-1047
`AudioPlay.CallBack` + `upLoading`/`upLyric`/`upLyricP`：接口随 `AudioPlay` 删除，
但 1031 注释说明它是有声书播放器回调 → **整块删**，不要只删方法体留接口。

### 3.5 MainActivity 212-213
`createReadBookMediaControlIntent` 确认属朗读域（`MainIntent` 实现是 `createReadBookIntent(readAloud = true)`，
原调用方是已删的 `MediaButtonReceiver` 与通知），**不是通用媒体控制** → 可删。
已 grep 确认：本文件除这一处外**没有任何** MediaButton / 音频焦点 / 前台服务代码，无误伤面。

---

## 4. 阻塞约束：route key 与 entry 必须同批

- `MainNavKey.kt` 29/128/152/157/160/185/189/193/197/201/205/209（+216-218 `ReadAloudSubPage.toMainRoute()`）
- `MainNavigator.kt:84` 的 `when(route)` 是 **sealed 语句且无 else** → 删 route key 必须同批删
  `99`、`149-166`、`206-213`、`250`、`263-268` 分支，否则非穷尽报错
- `MainNavigator.kt:332` 的 `when` 有 `437` 的 `else` → 安全
- `MainActivity:449` 的 `when {}` 有 `else` → 安全

---

## 5. 「能编译但语义已死」的残留（约 20 行，勿漏）

| 文件 | 行 | 内容 |
|---|---|---|
| MainNavGraph | 249-252 | `isReaderOnTop`（唯一调用方 MainActivity:773，随 762-829 删除后成死代码） |
| MainNavGraph | 479-485 | `isAudio` 分支 |
| MainNavGraph | 555 | `onNavigateToReadAloud` |
| MainNavGraph | 832-841 | `route.readAloud` 三分支 |
| MainNavGraph | 866 | `controller.clearTts()`（`clearTts` 仍在 → 归 W2b） |
| MainNavGraph | 1085-1096 | ⚠️ **最隐蔽**：`BookInfoRouteScreen` 的 `onOpenAudioPlay` 参数已删掉引用点却仍在此传参 |
| MainActivity | 137 | `KEY_RESTORE_READ_ALOUD` |
| MainActivity | 146 | `hasActiveAudioPlayRoute`（唯一写方 MainNavGraph 1346/1354，随整函数删） |
| MainActivity | 212-220 | `createReadBookMediaControlIntent` / `createAudioPlayIntent` |
| MainActivity | 946, 956 | `putBoolean`/`getBoolean(readAloud)` |
| MainScreen | 135-136, 604, 607, 613 | 胶囊参数与本地状态 |

---

## 6. 归 W2c 处置（本轮不要删类）

| 对象 | 理由 |
|---|---|
| `ReadAloudMorphState` / `LocalReadAloudMorph` / `CapsuleAnchorKind` | 类文件仍在，编译不报错；仅服务朗读播放层 |
| `PlayerMorphHost` / `PlayerMorphAppearance`（`core/ui/player`） | 属**通用几何/形变库**（W2a 已明令保护） |
| `MainActivity:605-619` `playerUnderlaySemantics(playerVisible \|\| morphPresent)` | `core/ui/player` 通用语义 API；删朗读后恒 `false`。保留传 `false` 还是整体删，取决于 W2c 对 `core/ui/player` 的处置 |

## 7. Scout 明确「不确定、未擅自归类」

- `MainNavigator:404` `MainRouteConst.ROUTE_AUDIO_PLAY -> MainRouteAudioPlay(...)`：route 键仍在故不报错；
  属路由**字符串常量**（外部 Intent/deeplink 面）→ 是否与 route key 同批删需单独确认。
- `FragmentExtensions:56` / `ContextExtensions:73,89` 的 `book.isAudio -> createAudioPlayIntent`：
  属 D4a / `BookType.audio` 类型位清理面，不在本 3 文件内。
