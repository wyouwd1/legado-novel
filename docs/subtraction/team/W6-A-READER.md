# W6-A 阅读核心对抗性测试报告

- 分支：`subtract/novel-only`（未切分支、未 commit）
- 基线 HEAD：`ef36b4b2f`
- 范围：`app/src/main/java/io/legado/app/feature/reader/core/**` 及其对应测试
- 结论：新增 **42 个永久 JUnit4 回归测试**，修复 **8 个可复现缺陷**；阅读包 544 项测试全部通过。

## 1. 发现与修复

### F1 断行器把 NaN/Infinity/负字宽写进行宽

- 输入：`ChineseLineBreaker(clusters=["正","文","内","容"], widthsPx=[10, NaN, 10, 10], widthPx=25)`（以及 `+Infinity`、`-Infinity`、`-5f`）
- 实际：`lineWidthsPx` 出现 `NaN` / `Infinity` / 负值；`Infinity - Infinity` 直接产生 `NaN`
- 期望：`lineStarts` 单调、覆盖全部字符；每行宽度有限且非负
- 测试：`ChineseLineBreakerAdversarialTest.nonFiniteAndNegativeClusterWidthsNeverLeakNaNOrNegativeLineWidths`
- 修复点：`ChineseLineBreaker.safeWidthsPx`（非有限/负 advance 按 0 宽处理，字符仍留在行区间内）

### F2 分页器把非法图片固有尺寸扩散成整页 NaN

- 输入：`ReaderMeasuredBlock.Image("bad", NaN, NaN, 0)`、`(Infinity, 10)`、`(10, Infinity)`、`(-Infinity, -5)`
- 实际：图片 `bounds.left/right/bottom = NaN`；`y += NaN` 后**后续正文字形 top/bottom 也变成 NaN**，整页命中/选区失效
- 期望：本页与后续正文几何全部有限，正文 `"￼正文"` 不丢
- 测试：`ReaderPaginatorAdversarialTest.nonFiniteImageIntrinsicSizesNeverLeakNaNGeometryIntoFollowingText`
- 修复点：`ReaderPaginator.addImage` 用 `finitePositiveOr(1f)` 收敛固有宽高（0/负仍按原有 1px 占位语义）

### F3 分页器把非有限段落字宽写进元素 bounds

- 输入：`ReaderMeasuredParagraph` 的 `clusterWidthsPx = [NaN, Infinity, -10, 10]`
- 实际：`ReaderElement.Text.bounds.right = NaN`，`Infinity` 时还会改变分页行数
- 期望：元素几何有限，页面文本仍为 `"正文内容"`
- 测试：`ReaderPaginatorAdversarialTest.nonFiniteParagraphClusterWidthsKeepFiniteBoundsAndText`
- 修复点：`addParagraph` 先构造 `clusterWidths = map { finiteNonNegative() }`，断行与落笔几何共用同一份安全字宽

### F4 分页器行内图 NaN 宽高污染同一行后续字形

- 输入：`InlineParagraph(items=[Text("甲",10), Image("bad", NaN, NaN), Text("乙",10)])`
- 实际：图片 `top/right/bottom = NaN`；后续 `Text("乙")` 的 `left/right` 也变成 `NaN`
- 期望：所有 bounds 有限，文本仍为 `"甲￼乙"`
- 测试：`ReaderPaginatorAdversarialTest.nonFiniteInlineImageKeepsFollowingGlyphsFinite`
- 修复点：`addInlineParagraph` 新增 `itemWidths` 安全宽度数组；图片高度与 `lineFinalWidthPx` 在落笔前收敛

### F5 测量器把“空 HTML 解析结果”当成无内容，整块正文丢失

- 输入：`ReaderChapterSourceBlock.Html("<b>正文</b>", 0, semanticLength=3)`，`semanticContent="正文\n"`，HTML resolver 返回 `emptyList()` 或 `[ReaderHtmlParagraph([Text("", pos)])]`
- 实际：测量结果 `blocks=[]`，`"正文"` 完全消失
- 期望：回落到语义正文，产出一个包含 `"正文"`（chapterPosition 0、1）的行内段
- 测试：`ReaderChapterBlockMeasurerAdversarialTest.emptyHtmlResolverResultFallsBackToSemanticTextInsteadOfDroppingBody`、`htmlResolverReturningOnlyBlankItemsFallsBackToSemanticText`
- 修复点：`ReaderChapterBlockMeasurer` 的 HTML 分支改为 `resolved?.takeIf { it.hasRenderableContent() } ?: fallbackHtmlParagraphs(...)`；新增 `hasRenderableContent()`

### F6 测量器把非法图片/HTML span 尺寸传成 NaN 行内图

- 输入：图片 resolver 返回 `NaN/Infinity/0/负`；或 `ReaderHtmlImageSpanExtent(NaN, NaN)`
- 实际：行内 `ReaderMeasuredInlineItem.Image.heightPx = NaN`（矩形比例 `NaN / 1`）
- 期望：行内图宽高有限，周围 `"前"`/`"后"` 正文不丢
- 测试：`ReaderChapterBlockMeasurerAdversarialTest.illegalImageDimensionsDoNotDropSurroundingText`、`htmlSpanExtentWithInvalidGeometryDoesNotDropText`
- 修复点：`ReaderImageDimensions.finiteOrUnit()`；HTML span 的宽/高仅在有限且 >0 时采用；`withWidth` 忽略非有限请求宽度

### F7 CastMarkers 对非法引擎下标越界/溢出

- 输入：`stripKeepingOffsets("<<甲>>正文", base=10)` 后调用 `storedOf(-1)`、`storedOf(Int.MAX_VALUE)`；以及空文本 `base=1`
- 实际：`storedOf(-1)` 返回 15（跳过段首标记，映射到正文中间）；`storedOf(Int.MAX_VALUE)` 先 `base + Int.MAX_VALUE` 溢出，再被夹成 `0`
- 期望：负下标映射到本段起点（`base`），超大下标映射到本段终点（`base + text.length`），所有下标单调且有界
- 测试：`CastMarkersAdversarialTest.stripKeepingOffsetsIsMonotonicAndBoundedForEverySpokenIndex`、`extremeSpokenIndicesClampWithoutOverflow`、`markersAtFirstAndLastPositionsMapToStoredBoundaries`
- 修复点：`Spoken` 保存 `storedStart`；`storedOf` 先按 `[0, text.length]` 收敛再查表

### F8 用户正文含 `〔图片1〕` 时折叠/还原把真实图片插错位置

- 输入：`"〔图片1〕<img src=\"real.jpg\"> 尾部"`
- 实际：`toBodyOnly` 生成两个同号标记；`reassemble` 命中用户手写的那个，返回 `"<img ...>〔图片1〕 尾部"`，图片与文字顺序被交换
- 期望：折叠→还原严格回到原文
- 测试：`ReaderChapterSourceAdversarialTest.bodyTextContainingPlaceholderLiteralDoesNotStealTheRealFragmentSlot`
- 修复点：`ReaderBodyOnlyFilter.scan` 检测标记字面量碰撞后整体后移编号（正常正文仍从 1 开始；搜索上限防死循环）

## 2. 行为锁定（生产代码未改）

以下输入按旧阅读器语义保留，测试只锁行为，不做“修复”：

- 零/负视口宽度：`lineStarts` 单调、无 `from > until`、覆盖全部字符（`zeroAndNegativeViewportWidthsProduceMonotonicRangesWithoutReversedLines`、`singleCharacterAtZeroWidthStillProducesOneCharacterLine`）
- 1px 视口、空块、空段落、`contentWidth=0`、2 列 1px、页高小于行高、`lineHeight=0/负`、连续 `pageBreak`：页数与文本长度有界、无 NaN（`ReaderPaginatorAdversarialTest` 其余用例）
- 空源、空白段、5000 字超长段、6000 字 HTML：不崩、逐字不丢（`ReaderChapterBlockMeasurerAdversarialTest`）
- 越界 `styleRanges`（`Int.MIN_VALUE..Int.MAX_VALUE`、负区间、空区间）：不抛异常、字形不丢
- 畸形/未闭合 `<img>`/`<usehtml>`、单引号 `<img>`（旧 `AppPattern.imgPattern` 只认双引号，按字面正文保留）、空/重复 `src`、代理对/emoji/组合字符：`chapterPosition` 与 `semanticContent` 自洽
- `[newpage]` 只识别 trim 后的小写形式；`[NEWPAGE]`、`[newpage]extra` 保持可见正文
- 标题 type 1/2/3、空 delimiter、非法正则、代理对距离切分：不抛异常，分段拼接等于原标题
- 未配对/同符号/嵌套引号、标记在首尾、连续标记：`injectParagraph`/`strip` 不丢可见文本，`ClosedSpan` 顺序正确

## 3. 改动文件

生产代码：

- `app/src/main/java/io/legado/app/feature/reader/core/layout/ChineseLineBreaker.kt`
- `app/src/main/java/io/legado/app/feature/reader/core/layout/ReaderPaginator.kt`
- `app/src/main/java/io/legado/app/feature/reader/core/layout/ReaderChapterBlockMeasurer.kt`
- `app/src/main/java/io/legado/app/feature/reader/core/source/ReaderBodyOnlyFilter.kt`
- `app/src/main/java/io/legado/app/feature/reader/core/cast/CastMarkers.kt`

新增测试（42 项）：

- `app/src/test/java/io/legado/app/feature/reader/core/layout/ChineseLineBreakerAdversarialTest.kt`（7）
- `app/src/test/java/io/legado/app/feature/reader/core/layout/ReaderPaginatorAdversarialTest.kt`（9）
- `app/src/test/java/io/legado/app/feature/reader/core/layout/ReaderChapterBlockMeasurerAdversarialTest.kt`（9）
- `app/src/test/java/io/legado/app/feature/reader/core/source/ReaderChapterSourceAdversarialTest.kt`（9）
- `app/src/test/java/io/legado/app/feature/reader/core/cast/CastMarkersAdversarialTest.kt`（8）

## 4. 运行的命令与结果

| 命令 | 结果 |
|---|---|
| `/tmp/legado-gradle.sh :app:testAppDebugUnitTest --tests 'io.legado.app.feature.reader.*'` | 基线通过（BUILD SUCCESSFUL，FROM-CACHE） |
| 同上，仅跑 5 个 `*AdversarialTest` | 42/42 通过，BUILD SUCCESSFUL |
| `:app:compileAppDebugUnitTestKotlin` | BUILD SUCCESSFUL（修复 IntArray/PageBreak 测试编译问题后） |
| `:app:testAppDebugUnitTest --tests 'io.legado.app.feature.reader.*' --rerun-tasks` | BUILD SUCCESSFUL in 2m35s；79 个测试类、544 项测试，0 failures / 0 errors / 0 skipped |
| `git diff --check -- app/src/main/java/io/legado/app/feature/reader/core app/src/test/java/io/legado/app/feature/reader/core` | 无输出（exit 0） |

完整结果副本：`/tmp/w6a-results-rerun/`。

## 5. 未验证风险

- 仅 JVM 单测，未在真机/模拟器验证渲染、命中测试、选区与朗读 UI；Canvas 字体量测在真实设备上是否会产生 NaN 未实测，修复属于防御性收敛。
- `ReaderPaginationConfig` 自身的 NaN 参数（如 padding、`lineHeightPx`、`baselineOffsetPx`、`paragraph.indentWidthPx`）没有统一清洗；任务列出的输入已覆盖，但若调用方直接传 NaN 配置，几何仍可能异常。
- 超长连续 Latin 单词的断行仍是 O(n²) 回退扫描：20k 字符在单测环境约 0.5s，未达死循环但 100k 级可能造成卡顿；本次未重构。
- `ReaderBodyOnlyFilter` 标记碰撞时编号后移会改变编辑框里显示的占位编号（如 `〔图片2〕`），未做 UI 流程验证。
- 单引号 `<img src='...'>` 仍按字面正文显示（与旧 `AppPattern.imgPattern` 一致）；若产品期望解析单引号，需要单独变更。
- `CastMarkers` 假定 `base >= 0`；负 `base` 现在不会崩，但会返回负偏移，未定义业务语义。
