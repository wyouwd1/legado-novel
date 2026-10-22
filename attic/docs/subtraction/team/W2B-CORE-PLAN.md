# W2b 阅读核心 cast/BGM 残留收敛计划

> 快照：2026-10-21 11:02 CST ｜ 分支 `subtract/novel-only` ｜ HEAD `824f80097` ｜ 工作区含 W2a 未提交删除。  
> 角色：Scout（只读）。本文件之外未修改任何源码/资源，未运行 Gradle。  
> 行号基于当前工作区；主控并发编辑后请按「锚点文本」复核，不要只信行号。  
> 路径修正：`ReaderChapterInput.kt` 实际位于 `app/src/main/java/io/legado/app/model/reader/ReaderChapterInput.kt`。

## 0. 结论（先做这 6 件事）

1. **生产链路已无 cast/BGM 生产者，可安全做纯删除**：`ReaderCastOptions` 全仓只有定义与 `Disabled`；`ReaderChapterSourceParser.parse` 的 `castLabels`/`bgmScenes` 零调用方；`ReaderChapterInput.castRenderHash` 永远取默认 `0`；`ReaderElement.RoleCast/BgmScene` 在 `ReaderCanvasSurface` 只剩 no-op，在 `ReaderTextBackgroundRun` 只剩「透明跳过」。
2. **W2b 删除面**：`ReaderCastOptions`/`ReaderCastProfile`、`ReaderMeasuredInlineItem.RoleCast/BgmScene`、`ReaderChapterInlineSource.BgmScene`、`ReaderElement.RoleCast/BgmScene`、`ReaderPaginationConfig.castOptions`、`LegacyReaderChapterPaginator.castOptions`、`LegacyReaderStyleRangeMapper.castBubbles/CAST_BUBBLE_PRIORITY`、`ReaderChapterInput.castRenderHash`、`LegacyReaderChapterLayoutIdentity.castRenderHash`、`CastCapsuleGeometry.kt`、`CastCapsuleStyle.kt`。
3. **必须保留的文本分页类型**：`ReaderPage`、`ReaderPageId`、`ReaderRect`、`ReaderElement.Text/Image/Review/Action/Spacer/ParagraphMarker/Rule`、`ReaderTextStyle`、`ReaderMeasuredBlock.*`、`ReaderMeasuredParagraph`、`ReaderMeasuredInlineItem.Text/Image`、`ReaderChapterSource(Block/InlineSource) 的文本/图片/换行/PageBreak`、`ReaderPaginationConfig` 的全部几何字段（除 `castOptions`）。`RoleCast/BgmScene` **不是**文本分页依赖，见 §3 反证。
4. **`CastMarkers.kt` 收敛为纯文本工具，不删文件**：保留 `QuotePairs`、`CastQuoteTracker`（含 `lastClosedSpan`/`fedCharacters`）、`injectParagraph`、`strip`、`stripKeepingOffsets`/`Spoken`；删除胶囊/朗读专用的 `SingleQuoteOpens`、`markerText`、`labelOf`、`blank`、`markerRanges`、`parse`、`Match/findMarkers`、`isValidName`。
5. **执行顺序**：P0 补分页文本快照测试并记录基线 → P1 去数据流（核心 6 文件 + Legacy/Controller 身份）→ P2 删 `ReaderElement.RoleCast/BgmScene` 及全部 `when` 分支 → P3 收敛 `CastMarkers` 并补纯文本单测 → P4 反证 grep + 完整验证集。每阶段独立可编译。
6. **本轮未验证**：所有结论来自静态只读扫描；最终以主控真实编译与测试为准。若分页快照出现普通文本页数/页首差异，立即回退 P1。

## 1. 事实基线（可复现反证）

当前 8 个目标文件指纹：

| 文件 | 行数 | SHA-256（前 16 位） |
|---|---:|---|
| `core/layout/ReaderPaginator.kt` | 1146 | `bdf04a9484c8dc17` |
| `legacy/LegacyReaderChapterPaginator.kt` | 399 | `8858a7349d6b78ff` |
| `core/source/ReaderChapterSource.kt` | 257 | `2f3f9597186530f2` |
| `core/layout/ReaderChapterBlockMeasurer.kt` | 772 | `89c7027ffc7d7061` |
| `legacy/LegacyReaderStyleRangeMapper.kt` | 421 | `0ab15ec8911a46ce` |
| `model/reader/ReaderChapterInput.kt` | 36 | `3348d8c9fb6ed4ca` |
| `core/model/ReaderPage.kt` | 437 | `06c4a01d4cbd754f` |
| `core/cast/CastMarkers.kt` | 227 | `5fcd02e0990e1344` |

关键反证命令与结果：

```bash
# 1) 只有定义 + Disabled，没有 enabled=true 的生产构造
grep -rn "ReaderCastOptions(" app/src/main app/src/test --include=*.kt
# 结果：ReaderPaginator.kt:112（定义）、132（Disabled）

# 2) 两个注入参数零调用方
grep -rn "castLabels\|bgmScenes" app/src/main app/src/test --include=*.kt
# 结果：仅 ReaderChapterSource.kt 定义处命中

# 3) cast 渲染签名只有默认 0
grep -rn "castRenderHash" app/src/main app/src/test --include=*.kt
# 结果：ReaderChapterInput.kt:29（默认 0）、LegacyReaderChapterPaginator.kt:63（默认 0）、
#       ReadBookController.kt:1421（把 input 的默认值再传出去）

# 4) 元素类型的生产消费面只有 no-op / 透明跳过
grep -rn "ReaderElement.RoleCast\|ReaderElement.BgmScene" app/src/main app/src/test --include=*.kt
# 结果：Paginator（创建/平移/文本）、CanvasSurface（2 处 Unit）、ReadBookController（2 处 false）、
#       ReaderTextBackgroundRun（跳过）、2 个测试文件

# 5) 胶囊几何/样式只被 Legacy 的死分支与测试引用
grep -rn "CastCapsuleGeometry\|CastCapsuleStyle" app/src/main app/src/test --include=*.kt
# 结果：LegacyReaderChapterPaginator.kt:7-8/174-249、CastCapsule* 自身、CastCapsuleGeometryTest
```

**为什么这些代码当前不可达**

| 路径 | 开关 | 当前值 | 结论 |
|---|---|---|---|
| `ReaderPaginationConfig.castOptions` | `castOptions.enabled` | `Disabled`（`enabled=false`） | `LegacyReaderChapterPaginator` 的 `castTracker=null`，`ReaderMeasuredInlineItem.RoleCast` 永不产出 |
| `ReaderChapterSourceParser.parse(castLabels=...)` | `castLabels != null` | 所有调用方未传，默认 `null` | `injectCast()` 直接 `return text`，`CastQuoteTracker` 未被喂入，语义文本不变 |
| `ReaderChapterSourceParser.parse(bgmScenes=...)` | `bgmScenes` 非空 | 所有调用方未传，默认空表 | `ReaderChapterInlineSource.BgmScene` 永不产出 |
| `LegacyReaderStyleRangeMapper.map(castBubbles=...)` | 非空 | Legacy 传 `castOptions.bubbles`，即空表 | `castBubbleRanges` 不执行 |
| `ReaderElement.RoleCast/BgmScene` | 上游 measured 元素 | 永不产出 | 仅测试手工构造；绘制 no-op、背景 run 透明 |

## 2. 逐文件编辑清单

### 2.1 `core/layout/ReaderPaginator.kt`（1146 行）

| 位置（CUR） | 对象 | 决定 | 具体动作 / 替代 |
|---|---|---|---|
| 15 | `import ...core.style.ReaderCharacterStyle` | 删 | 仅被 `ReaderCastOptions.bubbles` 使用 |
| 79-80 | `ReaderPaginationConfig.castOptions` | 删 | 连同默认值 `ReaderCastOptions.Disabled` |
| 99-104 | `ReaderCastProfile` | 删 | 无外部引用 |
| 106-134 | `ReaderCastOptions`（含 `hasVoiceEffect`/`Disabled`） | 删 | 无生产构造；不要保留空对象垫片 |
| 239-252 | `ReaderMeasuredInlineItem.RoleCast` | 删 | 胶囊测量项；上游不再产出 |
| 254-262 | `ReaderMeasuredInlineItem.BgmScene` | 删 | 零语义字符的视觉项；上游不再产出 |
| 265-274 | `tallItemHeightPx()` | 保留但简化 | 只剩 `Image -> heightPx`；`else -> 0f` |
| 470-471 | `shiftElement` 的 RoleCast/BgmScene 分支 | 删（P2，随元素类型删） | 其余分支不动 |
| 702-703 | `addInlineParagraph` 的 clusters 分支 | 删 | 只剩 `Text`/`Image`；`Image -> "\uFFFC"` |
| 955-992 | 放置 RoleCast / BgmScene 的两个 `when` 分支 | 删 | 整段删除，不留 `Unit` 分支 |
| 997-999 | `pageText.append` 的 RoleCast/BgmScene 分支 | 删 | 只剩 `Text -> value`、`else -> "\uFFFC"`（Image） |
| 1007-1015 | 胶囊对 `previousItemBackground` 透明的 `if` | 删条件，保留两条赋值 | 删除后所有行内项都是真实内容，直接更新 `previousItemBackground` / `previousMatchSpacingAfterPx` |

**本文件无 cast 专属布局常量**；胶囊宽度/高度全部来自 `ReaderCastOptions` 与 `LegacyReaderChapterPaginator` 的闭包，随上游删除。

### 2.2 `core/layout/ReaderChapterBlockMeasurer.kt`（772 行）

| 位置（CUR） | 对象 | 决定 | 具体动作 / 替代 |
|---|---|---|---|
| 3 | `import ...core.cast.CastMarkers` | 删 | 删除后无 CastMarkers 引用 |
| 173-190 | 构造参数：`castTracker`、`castCapsuleWidthPx`、`castCapsuleHeightPx`、`castPlaceholderWidthPx`、`castHasVoiceEffect`、`bgmCapsuleWidthPx`、`bgmCapsuleHeightPx` | 全删 | 调用方 `LegacyReaderChapterPaginator` 同步删；测试均未传这些具名参数 |
| 245-253 | `addStyledParagraph(..., castFeed: Boolean = true)` | 删参数 | 仅 HTML 调用点传 `false`，见 604 |
| 328-331 | `castActive` / `castPieces` / `splitCastPieces` | 删 | 文本段恒为单段；`text` 即 `item.value` |
| 333-432 | `for ((pieceText, pieceMarker) in castPieces)` 循环 | 改写为单段 | 保留 `offset` 逐 cluster 累加与 `position = item.chapterPosition + offset`；删除 `pieceEnd` 与标记分支 |
| 398-413 | 未分配锚点的占位 `RoleCast` | 删 | 无 tracker 即无占位胶囊 |
| 415-431 | 已分配标记的 `RoleCast` | 删 | 不再按 `findMarkers` 切段 |
| 434-443 | `ReaderChapterInlineSource.BgmScene` 分支 | 删 | 源类型同步删除 |
| 604 | `castFeed = false` | 删 | 参数已删 |
| 615-627 | `private fun splitCastPieces(...)` | 删 | 无调用方 |

改写后的文本循环应保持：`offset` 从 0 开始、每个 cluster 累加 `cluster.length`、`position` 仍为「源段起始 + 段内偏移」。这是章节字符坐标的唯一来源，不能改成 `clusterIndex`（多码元 cluster 会错位）。

**必须保留**：`ReaderCharacterStyle.merge`（754-772）、`ReaderImageOptions`/图片布局、`ReaderParagraphDecoration`、`styleRanges` 编译与逐字样式解析、缩进/HTML 行距。它们与 cast 无关。

### 2.3 `core/source/ReaderChapterSource.kt`（257 行）

| 位置（CUR） | 对象 | 决定 | 具体动作 / 替代 |
|---|---|---|---|
| 3 | `import ...core.cast.CastMarkers` | 删 | — |
| 55-65 | `ReaderChapterInlineSource.BgmScene` | 删 | 只由 `bgmScenes` 参数产生，已无调用方 |
| 133-144 | `parse(castLabels, bgmScenes)` | 删两个参数 | 调用方全部使用默认值 |
| 149-150 | `val castTracker = CastMarkers.CastQuoteTracker()` | 删 | 无注入时 tracker 从未被喂入，纯死对象 |
| 171-176 | `fun injectCast(text)` | 删 | 文本直接使用原 `text` |
| 181-184 / 195-198 | `castText` 变量与 `semanticContent.append(castText)` | 改为 `text` | 语义文本逐字节不变；`position += text.length` 不变 |
| 201-215 | 段首 BGM 胶囊的 `ReaderChapterSourceBlock.Paragraph` | 删 | 无 BGM 分配来源 |
| 225-247 | `fun castAnchorText(...)` | 删 | 全仓零调用方（AI 分配已删） |

**不可动**：`semanticContent` 的构造顺序、`\n` 段落分隔、`\uFFFC` 图片占位、`characterCount = position`。这些是 Canvas 选区、搜索、翻页定位的坐标基准。

### 2.4 `legacy/LegacyReaderChapterPaginator.kt`（399 行）

| 位置（CUR） | 对象 | 决定 | 具体动作 / 替代 |
|---|---|---|---|
| 7-9 | `CastCapsuleGeometry` / `CastCapsuleStyle` / `CastMarkers` import | 删 | 本文件改完后三个都无引用 |
| 10 | `ReaderChapterInlineSource` import | 删 | 仅 203 行 BGM 扫描使用 |
| 11 | `ReaderCastOptions` import | 删 | 参数删除后无引用 |
| 62-63 | `LegacyReaderChapterLayoutIdentity.castRenderHash` | 删 | 恒 0；见 §3 缓存键 |
| 129-130 | `paginate(..., castOptions: ReaderCastOptions = Disabled)` | 删参数 | 全仓无调用方传它；调用点均为具名参数，删最后一个参数安全 |
| 158-164 | `LegacyReaderStyleRangeMapper.map(..., castBubbles = castOptions.bubbles)` | 删 `castBubbles` 实参 | 只保留 `source/rules/processes` |
| 169-250 | 整块 cast/BGM 排版预算：`castTracker`、`roleCapsuleStyle`、`placeholderCapsuleStyle`、`castLabelPaint`、`castPoolPaint`、`castWidthOf`、`bgmEnabled`、`bgmLabelPaint`、`bgmWidthOf` | 全删 | 无 producer；`CastCapsuleGeometry.heightPx/placeholderWidthPx/bgmWidthPx/textScale/poolScale` 随之无消费者 |
| 239-249 | `ReaderChapterBlockMeasurer` 的 cast/bgm 具名参数 | 删 | 与 2.2 构造参数同步 |
| 277 | `ReaderPaginationConfig.castOptions = castOptions` | 删 | 字段已删 |

**不可动**：`bodyPaint`/`titlePaint`（文本 shaper、字号、字距）、`styleRanges`、所有 `ReaderPaginationConfig` 几何字段、`CHAPTER_END_PADDING_DP = 20f`、`ReaderPerfTrace` 分段。

### 2.5 `legacy/LegacyReaderStyleRangeMapper.kt`（421 行）

| 位置（CUR） | 对象 | 决定 | 具体动作 / 替代 |
|---|---|---|---|
| 8 | `import ...core.cast.CastMarkers` | 删 | — |
| 28-34 | `const val CAST_BUBBLE_PRIORITY = 20_000` | 删 | 仅角色气泡使用 |
| 40-41 | `map(..., castBubbles: Map<String, ReaderCharacterStyle> = emptyMap())` | 删参数 | Legacy / 测试同步删实参 |
| 79-81 | `if (castBubbles.isNotEmpty()) result += castBubbleRanges(...)` | 删 | — |
| 85-159 | `castBubbleRanges(...)` 整函数 | 删 | 依赖 `CastMarkers.findMarkers`/`CastQuoteTracker.lastClosedSpan` |
| 161-201 | `ReaderCharacterStyle?.overriddenBy(cast, ...)` | 删 | 只被 `castBubbleRanges` 调用 |
| 203-214 | `fun styleOf(rule)` | 删 | 只用于平台层装配 `ReaderCastOptions.bubbles`；全仓零调用方 |
| 244-249 | `semanticBodyText` 的 `BgmScene -> Unit` 分支 | 删 | `ReaderChapterInlineSource` 类型删除后必须删；`when` 仍穷尽 Text/Image/BlankLine |

**必须保留**：`rangesForLiteralRange`（`HighlightRuleEditSheet.kt:1069` 在用）、`matchRanges` 的首/中/末三段切分、`toReaderStyle()`、`TextProcessStyle.toReaderStyle()`、`backgroundImageSize`。这些是高亮规则/用户标记的文本样式主路径。

### 2.6 `model/reader/ReaderChapterInput.kt`（36 行）

| 位置（CUR） | 对象 | 决定 | 具体动作 / 替代 |
|---|---|---|---|
| 23-29 | `val castRenderHash: Int = 0` 及其 KDoc | 删 | 全仓唯一读取点是 `ReadBookController.layoutIdentity()`；构造 `ReaderChapterInput` 从未显式传值 |
| `ReadBookController.kt:1409-1422` | `layoutIdentity()` 的 `castRenderHash = castRenderHash,` | 删该行 | `LegacyReaderChapterLayoutIdentity` 字段同步删 |
| `LegacyReaderChapterLayoutIdentity:62-63` | 同名字段 | 删 | 见 2.4 |

**缓存键影响**：删除后 `ReaderChapterInput` 的 `equals/hashCode` 少一个恒 0 字段，`LegacyReaderChapterLayoutIdentity` 的相等性同样少一个恒 0 字段；对当前所有输入，比较结果与删除前完全一致。`ReaderChapterInput.sourceHash = readerSource.hashCode()` 也不变（无 BGM/标签产物）。**不要**改动 `contentHash`、`contentProcessesHash`、`sourceHash`、`bookSourceHash`、`pageEstimateGeneration`。

> 决策边界：`W2-READER-EDITS.md:96` 在 W2a 时建议「保留 `castRenderHash`（默认 0）」，目的是不在 W2a 动分页身份。W2b 的任务定义已把 `castRenderHash` 列为残留；本计划按 W2b 目标删除。若主控要求 W2b 也零风险保留，可只保留该字段不删——它恒 0，不影响本计划其余部分。

### 2.7 `core/model/ReaderPage.kt`（437 行）

| 位置（CUR） | 对象 | 决定 | 具体动作 / 替代 |
|---|---|---|---|
| 291-322 | `ReaderElement.RoleCast`、`ReaderElement.BgmScene` | 删（P2） | 先确认 P1 已无 `ReaderMeasuredInlineItem` 产出；再删类型 |
| 369-370 | `ReaderPage.readAloudParagraphIndex` | **保留**（本波不动） | 朗读高亮残留字段，消费方 `ReaderCanvasSurface:2944`；属 W2c，不要混入本波 |
| 238 | `ReaderElement.Text.readAloud` | **保留**（本波不动） | 同上，文字高亮色/强调语义 |

> 决策边界：`W2-READER-EDITS.md:100` 在 W2a 时写「`RoleCast/BgmScene` 类型保留（W2 硬约束）」。那是 W2a 为避免与主控并发改分页热路径的暂缓项；W2b 任务定义明确把这两个类型列为残留，`PLAN-CANDIDATES.md §5.3` 也把「共享阅读核心去 cast/bgm」列为 W2b 出口。若主控坚持保留，可只执行到 P1（类型无生产者、编译与行为仍安全），跳过 P2；代价是 `ReaderElement` 保留两个死类型与 4 个 no-op/透明分支，后续 W2c 再删。**不要**在 P1 未完成时先删类型（会留下 `when` 断裂）。

P2 删除类型时必须同步的穷尽 `when` / 类型引用（全仓已核对）：

| 文件 | 位置（CUR） | 动作 |
|---|---|---|
| `core/layout/ReaderPaginator.kt` | 470-471 | 删两个 `shiftElement` 分支 |
| `core/model/ReaderTextBackgroundRun.kt` | 21 | 删 `RoleCast || BgmScene -> return@forEach`；胶囊不存在后该透明逻辑无意义 |
| `feature/reader/ReaderCanvasSurface.kt` | 2056-2057 | 删两个 `Unit` 分支 |
| `feature/reader/ReaderCanvasSurface.kt` | 2777-2778 | 删两个 `Unit` 分支 |
| `ui/book/read/ReadBookController.kt` | 562、564 | 删 `RoleCast -> false` / `BgmScene -> false` |
| `test/.../ReaderPaginatorTest.kt` | 596-678 | 删两个胶囊背景 run 测试 |
| `test/.../ReaderTextBackgroundRunTest.kt` | 261-317、371-383 | 删 3 个胶囊测试 + `capsule()` helper |
| `test/.../ReaderTextBackgroundRunTest.kt` | 276-283 | `BgmScene` 构造块随测试删除 |

### 2.8 `core/cast/CastMarkers.kt`（227 行）

见 §4 的最终 API 表。原则：只保留「纯文本标记」的注入、剥离、偏移映射与引号跟踪；删除一切胶囊/朗读分配 API。

## 3. 必须保留的类型与反证

### 3.1 文本分页依赖链（不可删/不可改语义）

```text
BookContent.textList
  → ReaderChapterSourceParser.parse
  → ReaderChapterSource{Block.Text/Paragraph/Html/PageBreak, InlineSource.Text/Image/BlankLine}
  → ReaderChapterBlockMeasurer.measure
  → ReaderMeasuredBlock.{Paragraph, InlineParagraph, Image, BlankLine, Rule, PageBreak}
  → ReaderPaginator / ReaderPaginationSession
  → ReaderPage{text, elements=ReaderElement.Text/Image/...}
  → ReaderPageNavigator / ReaderCanvasSurface（文本绘制、选区、翻页）
```

必须保留的具体类型/字段：

| 类型 | 必须保留的原因 |
|---|---|
| `ReaderPageId`、`ReaderPage`、`ReaderRect` | 页身份与命中/几何；`ReaderPageId(chapterIndex,pageIndex)` 是页表键 |
| `ReaderElement.Text` 及 `value/chapterPosition/paragraphIndex/bounds/baselinePx/style` | Canvas 绘制、选区、搜索、进度映射的唯一正文载体 |
| `ReaderElement.Image/Review/Action/Spacer/ParagraphMarker/Rule` | 图片、段评、动作、空白、引用/项目符号、分隔线 |
| `ReaderTextStyle`、`ReaderTextBackgroundImage`、`ReaderUnderline`、`ReaderTextShadow` | 高亮/背景图/下划线/阴影样式 |
| `ReaderMeasuredBlock.*`、`ReaderMeasuredParagraph`、`ReaderMeasuredInlineItem.Text/Image` | 测量到分页的中间契约；`InlineParagraph` 的 `justifyAtWordBoundaries` 等字段不能动 |
| `ReaderChapterSource`、`ReaderChapterSourceBlock.*`、`ReaderChapterInlineSource.Text/Image/BlankLine` | 语义坐标与 block 结构；`semanticContent` 是搜索/选区的绝对坐标 |
| `ReaderPaginationConfig` 除 `castOptions` 外的全部字段 | 视口、padding、行高、基线、分栏、滚动、标题间距、下划线、强调样式 |
| `ReaderChapterInput` 除 `castRenderHash` 外的身份字段 | 分页任务去重、整书页数估算 |
| `LegacyReaderChapterLayoutIdentity` 除 `castRenderHash` 外的字段 | 章级排版身份；删错会导致旧页被复用或反复重排 |

### 3.2 `RoleCast/BgmScene` 为什么不是文本分页依赖

- `ReaderMeasuredInlineItem.RoleCast/BgmScene` 只在 `castTracker != null`（即 `castOptions.enabled=true`）或 `bgmScenes` 非空时创建；两者当前都不可达。
- 即使手工构造，`ReaderPaginator.pageText` 对 RoleCast 追加 `raw`（标记原文）、对 BgmScene 追加空串；胶囊不在 `ReaderElement.Text` 中，不参与 `elementRange`/选区/搜索。
- `ReaderTextBackgroundRun` 明确把它们当透明按钮跳过；`ReaderCanvasSurface` 两个绘制分支已是 `Unit`；`ReadBookController.onComposeReaderElementClick` 已是 `false`。
- 结论：删除两个元素类型只影响「不可达的胶囊」和对应测试，不影响任何文本页的页数、页首、字符坐标或几何。

## 4. `CastMarkers.kt` 收敛方案

### 4.1 最终保留 API

| API | 当前行 | 保留理由 |
|---|---:|---|
| `val QuotePairs` | 22-25 | 纯文本引号对；引号跟踪的唯一字符表 |
| `class CastQuoteTracker(maxDepth)`、`Frame`、`ClosedSpan`、`lastCastOrdinal`、`fedCharacters`、`lastClosedSpan`、`feed()` | 38-97 | 章级引号/锚点跟踪；注入侧与测量侧共用同一规则 |
| `fun injectParagraph(paragraph, tracker, assignmentLabels)` | 200-226 | 在开引号后插入调用方给的纯文本标签；不依赖 DB/UI |
| `fun strip(text)` | 120-121 | 去掉标记，得到上屏/发声文本 |
| `fun stripKeepingOffsets(text, base)` + `class Spoken` | 123-167 | 去标记同时保留「引擎下标 → 正文下标」映射；偏移体系不能丢 |
| `private val syntax` + `CastSyntax` | 20 | 仅用于 `markerRegex`；语法文件保留 |

### 4.2 删除 API

| API | 当前行 | 删除理由 |
|---|---:|---|
| `val SingleQuoteOpens` | 27-36 | 只服务已删除的 `CastAssignmentStore.thoughtOrdinals` / `VoiceEffectStore`（朗读域） |
| `fun isValidName` | 99-100 | 只服务已删除的分配弹层输入 |
| `fun markerText` | 102-104 | 只服务已删除的分配写入 |
| `fun labelOf` | 106-108 | 只服务胶囊标签 |
| `fun blank` | 110-118 | 只服务朗读偏移（用等长空格抹标记） |
| `fun markerRanges` | 170-172 | 无调用方 |
| `fun parse` / `data class Parsed` | 174-178 | 无调用方（`CastSyntax.parse` 保留给语法测试） |
| `data class Match` / `fun findMarkers` | 180-198 | 只服务测量期切段与角色气泡区间 |
| 胶囊相关 KDoc（`chapter_role_assignments`、`ReaderMeasuredInlineItem.RoleCast`、朗读链路） | 3-16 | 改为「纯文本标记工具」说明，避免误导后续维护者 |

收敛后的文件骨架（只表达 API 边界，不是要求逐字照抄）：

```kotlin
object CastMarkers {
    val QuotePairs: List<Pair<Char, Char>>

    class CastQuoteTracker(private val maxDepth: Int = 2) {
        var lastCastOrdinal: Int; private set
        val fedCharacters: Int
        var lastClosedSpan: ClosedSpan?; private set
        fun feed(ch: Char): Boolean
    }

    fun injectParagraph(paragraph: String, tracker: CastQuoteTracker, assignmentLabels: Map<Int, String>): String
    fun strip(text: String): String
    fun stripKeepingOffsets(text: String, base: Int = 0): Spoken

    class Spoken(val text: String, ...) { fun storedOf(spokenIndex: Int): Int }
}
```

`CastMarkers` 不再引用 `chapter_role_assignments`、`CastAssignmentStore`、`VoiceEffectStore`、`ReaderMeasuredInlineItem`；这些类型要么已删，要么属 W4/朗读域。

### 4.3 `CastSyntax.kt` 的边界

- **保留文件**（W2 硬约束；`strip`/`markerRegex` 依赖它）。
- **P1 后 `CastMarkers` 在 `app/src/main` 已无生产调用方**（只剩 `ChapterRoleAssignment.kt` 的 KDoc 引用）。这是 W2 硬约束「`CastMarkers.kt` 保留、只收敛为纯文本标记」的直接结果：本波保留它作为受测试保护的纯文本工具，不强行接回分页链路；若主控要求零 dead code，需要用户重新裁决是否删除整个文件，W2b 不擅自删除。
- `CastSyntaxHolder.current` 的写入方 `CastSyntaxStore` 已删除，当前是「只读的 `@Volatile var`」。建议同批改为 `val current: CastSyntax = CastSyntax.DEFAULT`，并更新 94 行 KDoc（不再提 `CastSyntaxStore`）。这不影响行为。
- `CastSyntax.markerText/labelOf/isValidName/parse` 目前只被 `CastSyntaxTest` 使用。W2b 可保留（纯语法 API、测试即规格）；若主控要求零 dead code，可在 P3 一并删这些方法和 `CastSyntaxTest`，但**不要删 `markerRegex` / 常量 / `forbidden` / `ALT_SEPARATORS`**，否则 `strip` 失效。

### 4.4 新增纯文本单测（P3 必做）

`app/src/test/java/io/legado/app/feature/reader/core/cast/CastMarkersTextTest.kt`（纯 JVM，不依赖 Android）：

1. `injectParagraph` 在两个连续段落中按开引号插入标签，`CastQuoteTracker.lastCastOrdinal` 连续递增（0、1）。
2. 未命中 `assignmentLabels` 时输出原文，且 tracker 仍然推进（与测量侧 ordinal 一致）。
3. `strip` 删除 `<<名（池）>>` 与 `<<名>>`，正文其余字符逐字不变。
4. `stripKeepingOffsets` 的 `storedOf` 把引擎下标映射回含标记正文的绝对下标（含 `base` 偏移与多标记场景）。
5. `CastQuoteTracker` 嵌套深度 `maxDepth=2`、`lastClosedSpan` 的 `start/endExclusive` 正确。

## 5. 缓存键与布局常量影响

### 5.1 缓存键

| 键/身份 | 当前组成 | 删除后 | 影响 |
|---|---|---|---|
| `ReaderChapterInput.castRenderHash` | 恒 `0` 默认值 | 字段删除 | 无；`ReaderChapterInput` 相等性只少一个常量 |
| `LegacyReaderChapterLayoutIdentity.castRenderHash` | 恒 `0` | 字段删除 | `paginatedChapterIdentities` 比较结果不变 |
| `ReaderChapterInput.sourceHash` | `readerSource.hashCode()` | 不变 | 无 BGM/标签产物时 `ReaderChapterSource` 结构逐字节一致 |
| `ReaderChapterInput.contentHash` / `contentProcessesHash` | `BookContent` 哈希 | 不变 | 不触碰 |
| `ReaderPaginationConfig.revision` | `31 * environmentKey.hashCode() + chapterIndex` | 不变 | 不触碰 |
| `ReaderPage.revision` / `layoutRevision` | 分页会话传入 | 不变 | 不触碰 |
| `ReaderPageId` | `(chapterIndex, pageIndex)` | 不变 | 页表键不触碰 |
| `directReaderPaginationEnvironmentKey` | 排版环境（尺寸/样式） | 不变 | 与 cast 无关 |

**必须验证**：同一组 `paragraphs` 在删除前后 `ReaderChapterSourceParser.parse(...).hashCode()` 与 `semanticContent` 完全一致。若不一致，说明误改了 `\n`/`\uFFFC`/position 逻辑，回退。

### 5.2 布局常量

| 常量/闭包 | 当前作用 | 删除后 |
|---|---|---|
| `CastCapsuleGeometry.heightPx(bodyPaint.textSize)` → `castCapsuleHeightPx` / `bgmCapsuleHeightPx` | 胶囊行高；只在 tracker/BGM 存在时使用 | 随 Legacy 死块删除；文本行高 `paginationStyle.bodyTextHeightPx` 不变 |
| `CastCapsuleGeometry.placeholderWidthPx(...)` → `castPlaceholderWidthPx` | 未分配占位胶囊宽度 | 只在 `castActive` 内调用，永不执行；删除 |
| `CastCapsuleGeometry.widthOf(...)` / `textScale` / `poolScale` / `avatarRatio` | 胶囊测量宽度 | Legacy 死块 + `CastCapsuleGeometry.kt` 删除 |
| `CastCapsuleGeometry.bgmWidthPx` / `bgmLabel` | BGM 胶囊宽度/标签 | 无 BgmScene；删除 |
| `CastCapsuleStyle.Default`（role/placeholder） | 胶囊圆角/头像/显隐 | 随 `CastCapsuleStyle.kt` 删除 |
| `ReaderPaginationConfig` 的 `lineHeight/padding/columnCount/continuousScroll/...` | 文本分页几何 | **一律不动** |

**结论**：所有 cast/BGM 布局常量都只在不可达分支求值；删除后文本页的 `scrollExtentPx`、`contentTop/Bottom`、`contentLeft/Right`、`elements.bounds` 应逐值不变。若出现差异，优先怀疑 `ReaderChapterBlockMeasurer` 的 `offset/position` 改写，而不是常量。

## 6. 删除顺序（每阶段独立编译）

### P0 基线冻结（不改生产代码）

1. `git diff --check`；记录当前 8 文件 SHA-256。
2. 新增（测试代码，允许）`ReaderCoreTextSnapshotTest`：固定 3 组纯文本 fixture（普通段落、标题+段落、含 `\n`/空段），断言 `pages.size`、`pages.map { it.text }`、`elements.filterIsInstance<ReaderElement.Text>().map { it.chapterPosition }`。先在当前树跑一次，记录输出为基线。
3. `./gradlew :app:compileAppDebugKotlin --console=plain` 确认起点绿。

### P1 去数据流（编译单元 1）

同批修改，避免中间态引用断裂：

1. `ReaderPaginator.kt`：删 `castOptions` 字段、`ReaderCastProfile`、`ReaderCastOptions`、`ReaderMeasuredInlineItem.RoleCast/BgmScene` 及放置/文本分支；`tallItemHeightPx` 简化；背景 run 透明 `if` 改直赋值。
2. `ReaderChapterBlockMeasurer.kt`：删构造参数、`castFeed`、`castActive`/`castPieces`/占位+标记分支、BGM 分支、`splitCastPieces`；文本循环改单段。
3. `ReaderChapterSource.kt`：删 `BgmScene`、`castLabels/bgmScenes`、tracker、`injectCast`、BGM block、`castAnchorText`。
4. `LegacyReaderChapterPaginator.kt`：删 import、`castOptions` 参数、capsule/bgm 预算块、measurer 参数、config 字段；保留 `styleRanges`。
5. `LegacyReaderStyleRangeMapper.kt`：删 `castBubbles`/`CAST_BUBBLE_PRIORITY`/`castBubbleRanges`/`overriddenBy`/`styleOf`/BGM 分支。
6. `ReaderChapterInput.kt` + `ReadBookController.layoutIdentity()` + `LegacyReaderChapterLayoutIdentity`：删 `castRenderHash`。
7. 删除 `CastCapsuleGeometry.kt`、`CastCapsuleStyle.kt`、`CastCapsuleGeometryTest.kt`（此时零引用）。
8. 更新 `ReaderPaginatorTest` 两个胶囊测试、`LegacyReaderStyleRangeMapperTest` 四个气泡测试、`bubbleImage()`；顺手删掉因此不再使用的 import（`ReaderCharacterStyle`、`ReaderTextBackgroundImage`、`ReaderChapterSourceBlock`，以编译器/IDE 提示为准）。
9. 编译 + 定向测试（见 §7）。通过后提交 P1。

> P1 结束时 `ReaderElement.RoleCast/BgmScene` 仍存在但已无生产者；`ReaderPaginator.shiftElement` 暂留两个分支，保证中间态可编译。

### P2 删元素类型（编译单元 2）

1. `ReaderPage.kt` 删 `ReaderElement.RoleCast/BgmScene`。
2. 按 §2.7 的表删 `ReaderPaginator.shiftElement` 两分支、`ReaderTextBackgroundRun` 透明分支、`ReaderCanvasSurface` 两处 no-op、`ReadBookController` 两处 `false`。
3. `ReaderTextBackgroundRunTest` 删 3 个胶囊测试与 `capsule()` helper；`ReaderPaginatorTest` 若 P1 未删干净，一并清。
4. 编译 + 定向测试；通过后提交 P2。

### P3 收敛 `CastMarkers`（编译单元 3）

1. 按 §4 删除胶囊/朗读 API；更新 KDoc。
2. `CastSyntaxHolder` 改 `val`（可选但推荐）；更新 `CastSyntax` 94 行 KDoc。
3. 新增 `CastMarkersTextTest`；跑该测试。
4. 编译 + 定向测试；通过后提交 P3。

### P4 终验

1. 反证 grep（§8）。
2. `git diff --check`。
3. 完整验证集（§7）。
4. 更新 `docs/subtraction/VERIFICATION.md`（由主控/验证组写，不在本 Scout 范围）。

## 7. 编译与验证策略

### 7.1 每阶段最小验证

```bash
git diff --check

./gradlew :app:compileAppDebugKotlin --console=plain

./gradlew :app:testAppDebugUnitTest \
  --tests "io.legado.app.feature.reader.core.layout.ReaderPaginatorTest" \
  --tests "io.legado.app.feature.reader.core.layout.ReaderChapterBlockMeasurerTest" \
  --tests "io.legado.app.feature.reader.legacy.LegacyReaderStyleRangeMapperTest" \
  --tests "io.legado.app.feature.reader.legacy.LegacyReaderPaginationBatchTest" \
  --tests "io.legado.app.feature.reader.core.model.ReaderTextBackgroundRunTest" \
  --tests "io.legado.app.feature.reader.core.cast.CastMarkersTextTest"
```

### 7.2 最终完整验证集

```bash
./gradlew testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug \
  --continue --no-configuration-cache
```

环境：JDK 21；`export ANDROID_HOME=/home/handx-aijiu/android-sdk`（仓库无 `local.properties`）。  
注意：`verifyConfigArchitecture` 是根项目任务且被 `compile*` 前置；若报「配置架构护栏失败」而非 `e:`，先看它的输出。本次改动不涉及被基线跟踪的 `ViewModel`/DAO 直连文件，**不要**用 `updateLintBaseline`。

### 7.3 行为对比

- 用 P0 快照测试对比 P1/P2/P3 前后：页数、页文本、`Text.chapterPosition` 序列必须完全一致。
- 另加一条 `ReaderChapterSourceParser` 测试：同一组段落解析前后 `semanticContent` 与 `hashCode` 一致（无标签/BGM 输入时）。
- 手工冒烟（主控/验证组）：打开普通 txt/epub 章节，翻 3 页、滚动模式、标题页、含图章节；确认无空页、无页首错位、无重复/丢字。

## 8. 反证 grep（终验应无输出/仅允许项）

```bash
# 1) cast/BGM 生产符号清零
grep -rnE 'ReaderCastOptions|ReaderCastProfile|CastCapsuleGeometry|CastCapsuleStyle|castRenderHash|CAST_BUBBLE_PRIORITY' \
  app/src/main app/src/test --include=*.kt

# 2) 字段/参数清零
grep -rnE 'castOptions|castBubbles|castLabels|bgmScenes|castFeed|splitCastPieces' \
  app/src/main app/src/test --include=*.kt

# 3) 元素/测量类型清零
grep -rnE 'ReaderElement\.(RoleCast|BgmScene)|ReaderMeasuredInlineItem\.(RoleCast|BgmScene)|ReaderChapterInlineSource\.BgmScene' \
  app/src/main app/src/test --include=*.kt

# 4) CastMarkers 只允许文本 API；CastSyntax 允许
grep -rn 'CastMarkers' app/src/main --include=*.kt
# 期望：仅 core/cast/CastSyntax.kt（KDoc）、core/cast/CastMarkers.kt 自身、ChapterRoleAssignment.kt（KDoc）
```

允许保留项：`CastMarkers`/`CastSyntax` 文件本身（W2 硬约束）；`ChapterRoleAssignment`/`BgmSceneMark`/`BgmPool*` 的 entity/DAO（W4 数据层清单）；`ReaderPage.readAloudParagraphIndex`/`ReaderElement.Text.readAloud`（W2c，不是 cast/BGM）。

## 9. 风险与回滚

| 风险 | 触发点 | 概率/影响 | 缓解 | 回滚 |
|---|---|---|---|---|
| 普通文本分页页数/页首变化 | `ReaderChapterBlockMeasurer` 文本循环改写 | 中 / 高 | P0 快照 + 逐段 `offset` 保留；先跑 `ReaderChapterBlockMeasurerTest`/`ReaderPaginatorTest` | 回退 P1；保留 P0 测试 |
| `ReaderChapterSource.hashCode()` 变化导致 `sourceHash` 失效/重复重排 | 误改 `semanticContent`/`\n`/`\uFFFC` | 低 / 中 | 新增 parse 前后 hash 断言；对比 `semanticContent` | 回退 P1 |
| 删除元素类型漏改 `when` | `ReaderCanvasSurface`/`ReaderTextBackgroundRun`/`Controller` | 中 / 编译失败 | P2 全仓 grep + 编译；编译器穷尽检查即证据 | 回退 P2 |
| `CastMarkers` 收敛误删 `strip` 依赖 | 误删 `markerRegex`/`ALT_SEPARATORS` | 低 / 中 | 保留 `CastSyntax`；新增 `CastMarkersTextTest` | 回退 P3 单文件 |
| 测试删除掩盖回归 | 删胶囊测试时误删普通文本用例 | 低 / 中 | 只删 §2.7/§2.5 列出的方法；保留 `inline image still breaks the run` 等 | `git checkout -- <test>` |
| 旧备份/旧 DB 数据无消费者 | `chapter_role_assignments`/`bgm_scene_marks` 仍有表 | 低 / 无 | 本波不碰 DB；W4 统一 drop/保留空表 | 不需要回滚（无 schema 变更） |
| 主控并发编辑导致行号漂移 | 同一工作区多人编辑 | 中 / 低 | 用锚点文本 + SHA-256 复核；每阶段 `git diff -- <file>` | 重新侦察 |

**回滚粒度**：P1/P2/P3 各自一个 commit，可单独 `git revert`。本波**不改** Room schema、migration、`PreferKey`、备份 JSON、Gradle 基线、Manifest；因此无持久化/构建配置回滚成本。页表/分页缓存均为内存态，进程重启即清空。

## 10. 不做（明确排除）

- 不删 `feature/reader/**` 分页/绘制链路；不删 `CastMarkers.kt` / `CastSyntax.kt` 文件。
- 不删 `ReaderPage.readAloudParagraphIndex`、`ReaderElement.Text.readAloud`、`ReaderCanvasSurface` 的 `readAloud: Color`（文字高亮色）。
- 不删 `ChapterRoleAssignment`/`BgmSceneMark`/`BgmPool*` entity/DAO/DB 表（W4）。
- 不改 `ReaderPaginationConfig` 的文本几何字段、`LegacyReaderChapterLayoutIdentity` 的非 cast 字段、`ReadBookController` 的分页调度/身份比较逻辑。
- 不改 `ReaderChapterSourceParser` 的 `semanticContent` 构造、`ReaderPaginator` 的页/元素装配几何。
- 不跑 `updateLintBaseline`、不改架构门基线。
