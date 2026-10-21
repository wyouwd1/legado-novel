# PLAN-CANDIDATES · 减法收尾方案评估与择优

> 任务：`task-3`（Architect 只读评估）｜分支：`subtract/novel-only`｜HEAD：`824f80097`
> 约束：**未修改任何源码、未运行 Gradle**；本文件是唯一写入。
> 输入：`README.md`、`TASK-BOARD.md`、`DECISION-MAP.md`、`VERIFICATION.md`、`W2-CONVERGENCE.md`、`W2-SCOUT-3B.md`、`W2-SCOUT-HOST.md`，以及当前 `git status` / `git diff --stat` 实测。

## 0. 结论（先给答案）

**选方案 A：在当前工作区继续 W2a 的错误驱动接入点收敛，编译转绿并完成完整验证后提交 227 个删除，再按 W2b→W2c→W3→W4→W5 小波推进。**

加权总分：**A = 4.00 / 5**，C = 3.35，B = 2.40。A 不是“最高分略高”，而是在正确性、残留债、可验证性三项主权重上同时领先；C 只在回滚成本上占优，B 只在速度上占优。

---

## 1. 事实基线（只读核对）

### 1.1 工作区实测（快照 2026-10-21 10:52；并行 writer 仍在推进，数字会变化）

> 注意：本文件写作期间其他 writer 正在修 W2a 接入点（`MainNavGraph`/`MainNavigator`/`MainIntent`/`ReadBookController` 等文件仍在变化）。下表是**快照**，不是冻结基线；W2a 开工前必须重新跑 `git status` / `git diff --stat`，以当时值为准。

| 项 | 实测 | 说明 |
|---|---|---|
| 当前分支 / HEAD | `subtract/novel-only` / `824f80097` | W1 与 W2-S0 已提交 |
| 工作区删除 | **235 个** `D`/` D` | 其中 **227 个 app 源码删除**（177 `main` + 50 `test`），另有 8 个 `.codebuddy`/`.codex` 无关删除 |
| 工作区修改 | **47 个**（快照） | 含 W2a 接入点修复；不是全部都属于 W2a |
| 净删除规模 | **281 files changed / +212 / −53,629**（快照） | 主要是 W2a 的 227 个 app 源码删除 |
| 当前主源码 | **1,454 个 .kt / 256,040 行**（快照） | W0 基线 1,752 / 335,597；W1-3 后 1,631 / 303,092 |
| 当前编译错误 | **209 个**（主控最新口径） | 本文未复跑 Gradle，见 §7 未验证项 |
| `git diff --check` | **失败 2 处**：`ReadBookViewModel.kt:2356`、`MainNavGraph.kt:1050` 文件尾空行 | W2a 提交前必须清零 |
| 架构门基线 | `legacyPreferenceCall` 21 键/71；`legacyDaoInjection` 1 键/0；`legacyUiDaoAccess` 8 键/20；**缺失文件键 0** | 当前键与文件存在性一致；W2a 删文件后仍需复查 |

### 1.2 文档与实测的 5 处差异（直接影响执行）

1. **209 与文档的 629/539/500 不一致**：`W2-CONVERGENCE.md` 最后记录的是收敛中段值；209 是主控最新口径。W2a 开工前必须重新编译一次，以新日志为准，**不要按旧行号/旧错误数施工**。
2. **225 与实测 227 不一致**：文档写 W2a 删 225 文件；实测 app 源码删除 227（177 main + 50 test）。差 2 个文件，可能是后续补删。W2a 提交前必须对账删除清单，并在 `TASK-BOARD.md`/`W2-CONVERGENCE.md` 改为实际数。
3. **“已暂存”与实测不一致**：文档称 `git rm` 已暂存；实测只有 **1 个删除在索引**（`ChapterStatusIcons.kt`），其余 234 个是工作区未暂存删除。**不能直接 `git commit` 当作 W2a**，必须重新 `git add` 并核对 `git diff --cached --stat`。
4. **W2b 原始清单已被 W2a 覆盖**：`service/{BaseReadAloudService,HttpReadAloudService,TTSReadAloudService,AudioPlayService}.kt`、`service/{readaloud,playback}/**`、`model/{ReadAloud,AudioPlay,...}.kt`、`help/{TTS,MediaHelp,exoplayer,readaloud}`、`domain/model/readaloud/**` 实测均已不存在。W2b 不能再按旧清单重复派工，必须先做“已删/残留”对账；否则会制造虚假工作量或误建回已删文件。
5. **`W2-CONVERGENCE.md` 的行号已过期**：当前 `MainNavGraph.kt`、`ReadBookViewModel.kt` 等工作区文件已被部分修改（`git diff --stat` 显示分别删了 422 / 289 行），Scout 行号只能作线索。每条删除前必须 `read` 精确上下文，并按括号净差校验。

### 1.3 剩余热点（静态 grep 计数，仅作排期线索，不等同于 209 错误归属）

| 文件 | `readaloud` | `audioplay` | `morph` | `tts` | `capsule` |
|---|---:|---:|---:|---:|---:|
| `ui/main/MainNavGraph.kt` | 4 | **50** | **26** | **14** | **10** |
| `ui/book/read/ReadBookController.kt` | **43** | 0 | 0 | **12** | 0 |
| `feature/reader/ReaderCanvasSurface.kt` | **33** | 0 | 0 | 0 | **16** |
| `model/ReadBook.kt` | **17** | 0 | 0 | 0 | 0 |
| `ui/main/MainNavigator.kt` | 2 | 3 | 0 | 4 | 1 |
| `ui/main/MainNavKey.kt` | 8 | 1 | 0 | 3 | 2 |
| `ui/main/MainIntent.kt` | 5 | 1 | 0 | 0 | 0 |
| `ui/book/read/ReadBookViewModel.kt` | 4 | 0 | 0 | 4 | 1 |
| `ui/main/MainActivity.kt` | 5 | 3 | 0 | 0 | 0 |

> 结论：W2a 尾部不是“平均分布在 20 个文件”，而是 **MainNavGraph + ReadBookController + ReaderCanvasSurface + ReadBook + 路由三件套** 为主。先修叶子消费者，再修 Contract，最后修宿主，避免类型面删除导致错误转移。

---

## 2. 候选方案

### 方案 A（推荐）· 继续当前 W2a 错误驱动收敛，再小波收尾

**范围**
- 当前工作区：227 个 app 源码删除 + 约 47 个文件修改（快照，见 §1.1），目标 209 错误 → 0。
- 之后按本文 §5 执行 W2b（共享阅读核心去 cast）、W2c（宿主/阅读页接线）、W2-S5/S6、W3、W4、W5。
- 不新增删除范围；所有删除必须命中 `DECISION-MAP.md` 已裁决域（D2/D3/D4/D5/D6/D7/D8/D9）。

**步骤**
1. **冻结当前进度**：生成工作区 checkpoint（`git stash create` + `git update-ref refs/wip/w2a-pre-converge`），记录 `git status` 与删除清单；把两个 EOF 空行修掉，使 `git diff --check` 归零。
2. **W2a 自底向上修接入点**：叶子（`ReadBookScreen`/`ReadBookMenuBar`/`ReadBookRouteScreen`/`TocViewModel`/`TocScreen`/`PlayerMorphHost`）→ `ReadBookViewModel` + `di/appModule`（同一步删构造参数）→ `ReadBookContract` → 内核（`model/ReadBook`、`ReadBookController`、`ReaderCanvasSurface`、分页/源文件）→ 宿主（`MainScreen`→`MainActivity`→`MainNavGraph` + `MainNavKey`/`MainNavigator`/`MainIntent` 同批）→ 小件群。
3. **清“能编译但语义已死”的残留**：按 `W2-SCOUT-HOST.md` §5 清 `isReaderOnTop`、`isAudio` 分支、`route.readAloud` 分支、`createReadBookMediaControlIntent`、`KEY_RESTORE_READ_ALOUD`、`hasActiveAudioPlayRoute`、胶囊参数等；不要只求编译过。
4. **完整验证后提交 W2a**：编译 0、完整验证集成功、单测下降只来自 50 个已删测试文件、架构门键一致、反证 grep 通过、APK 体积记录；只 stage `app/src/main/java` 与 `app/src/test/java`，排除 8 个无关删除和 docs。
5. **W2b→W5**：按 §5 小波推进；每波一个可回退提交，不跨波留半截状态。

**风险**
- 工作区是 52k 行未提交大块；误 `git add` 会把 `.codebuddy`/`.codex` 或 docs 混入 W2a 提交（历史事故模式，见 README §6.7）。
- 旧行号/旧错误数导致删错块；必须 `read` 复核并用括号净差校验（README §6.8）。
- 架构门可能在“0 个 `e:`”时失败（基线键指向已删文件），容易误判为编译问题。
- W2a 只过 compile 不过完整验证集，会把测试/lint/打包问题推迟到 W3，污染后续判断。
- 227 个删除中有跨域文件（如通用 morph、`AnalyzeUrl` 的 media3），误删会破坏保留功能；必须按 FINDINGS §6 的保护清单执行。

**回滚**
- 单文件：`git checkout HEAD -- <file>`，只回退该接入点，保留其它 W2a 删除。
- 全量 W2a：`git checkout HEAD -- app/src/main/java app/src/test` + `git reset`（清除索引，不动 docs/未跟踪文件）；执行前先保存 `git stash create` 的 checkpoint ref。
- 禁止 `git reset --hard`；禁止用 `git checkout -- .` 覆盖未跟踪的规划文档。

---

### 方案 B（淘汰）· 先加 no-op 垫片快速转绿，再清理

**范围**
- 为已删的 `ReadAloud`/`AudioPlay`/TTS/cast/BGM 类型补最小空实现、空对象、空方法或恒 false 分支，让 209 错误先归零并提交 W2a；后续波次再删垫片。

**步骤**
1. 从编译日志提取未解析符号。
2. 新建/恢复最小 stub（`object ReadAloud { fun play() = Unit }`、空 `AudioPlayService`、`PlayerMorphHost` 空重载等）。
3. 编译 0 后提交“W2a 完成”。
4. 后续再逐波删除 stub，并重新修一遍真实接入点。

**风险**
- 编译绿是假信号：UI 入口、服务、路由仍可能指向空实现，运行时表现为“点了没反应/朗读按钮仍在/通知不出现”，比编译错误更难定位。
- 违反 AGENTS.md「不建空模块、单方法包装、无调用方抽象」与 `W2-CONVERGENCE.md` §六「不引入任何临时垫片」。
- 垫片会进入 DI/导航/Manifest，删除时又是一次全量 W2；工作量不是省掉，而是翻倍。
- 测试无法覆盖大多数 UI/服务接线，CI 绿灯不能证明减法完成。

**回滚**
- 删除 stub 提交即可；但若 stub 与真实修复混在同一提交，需要逐文件剥离，回滚成本高于“一开始就不加”。

---

### 方案 C（淘汰）· 回滚 W2a，重新按子域分小波

**范围**
- `git checkout HEAD -- app/src/main/java app/src/test` + `git reset` 回到 W1-3 后的干净状态，放弃当前 227 文件删除与 11+ 接入点修复。
- 重做 W2：TTS UI → 服务/help 层 → 配音/BGM → 音频书/播放器 → 阅读页接线 → 数据/类型位，每步单独编译提交。

**步骤**
1. 回滚到 `824f80097` 的源码状态。
2. 按 5–6 个子域重新测绘、重新删除、重新修接入点。
3. 每个子域提交后跑完整验证集。

**风险**
- 重做 52k 行删除与 209 错误收敛，工作量约为 A 的 2–3 倍；W2a 已经完成的 11+ 接入点修复全部作废。
- 重新执行 225 文件级删除会再次暴露 W1-2/W2 已记录的括号配平、行号偏移、`else` 误删等事故模式。
- 回滚本身干净，但“重做”不是零风险；对当前分支是净损失。

**回滚**
- 回滚即方案本身：`git checkout HEAD -- app/src/main/java app/src/test` + `git reset`；源码回到 W1-3，docs/未跟踪文件保留。此方案的回滚成本最低（5 分），但这是唯一优势。

---

## 3. 评分表与自动选择

### 3.1 维度、权重、评分锚点

| 维度 | 权重 | 5 分含义 | 1 分含义 |
|---|---:|---|---|
| 正确性 | 30% | 行为语义保持、不掩盖错误、删除均属已裁决域 | 用假实现/假绿灯掩盖真实断链 |
| 残留债 | 25% | 完成后无垫片、无死接线、无未解释死代码 | 留下大量 stub/死路由/死类型 |
| 可验证性 | 20% | 每步可用编译/单测/架构门/反证独立证明 | 只能靠“看起来对”或单一绿灯 |
| 工作量 | 15% | 最小必要改动，复用已完成进度 | 全量重做或双倍工作 |
| 回滚成本 | 10% | 单文件/单提交可回退，历史干净 | 大面积不可逆或需要重做 |

### 3.2 评分结果

| 方案 | 正确性 30% | 残留债 25% | 可验证性 20% | 工作量 15% | 回滚成本 10% | **加权总分** | 排名 |
|---|---:|---:|---:|---:|---:|---:|---:|
| **A 继续 W2a 收敛 + 小波收尾** | 4 | **5** | 4 | 3 | 3 | **4.00** | **1** |
| B no-op 垫片快速转绿 | 2 | 1 | 2 | **5** | 4 | **2.40** | 3 |
| C 回滚 W2a 重新分小波 | 3 | 4 | 4 | 1 | **5** | **3.35** | 2 |

计算式：

```text
总分 = 0.30×正确性 + 0.25×残留债 + 0.20×可验证性 + 0.15×工作量 + 0.10×回滚成本

A = 0.30×4 + 0.25×5 + 0.20×4 + 0.15×3 + 0.10×3 = 4.00
B = 0.30×2 + 0.25×1 + 0.20×2 + 0.15×5 + 0.10×4 = 2.40
C = 0.30×3 + 0.25×4 + 0.20×4 + 0.15×1 + 0.10×5 = 3.35
```

**敏感性检查**：把“工作量”权重提高到 35%、正确性降到 20%、残留债 20%、可验证性 15%、回滚 10%，则 A=3.75、B=3.05、C=2.85，A 仍第一。A 的领先不依赖某一组权重。

**自动选择结论**：按上表，**A 为最优**。C 的分小波原则不是被否定，而是被 A 吸收到 W2b/W2c/W3/W4/W5 的波次设计里；不需要用“整体回滚”来获得小波。

---

## 4. 淘汰理由

### B 为什么淘汰

1. **正确性 2 分**：no-op 垫片让编译通过，但保留功能的真实断链（朗读入口、音频路由、服务通知）仍在；这是用“假实现”制造“已完成”的假象。
2. **残留债 1 分**：stub 会进入 DI、导航、Manifest 或 UI 分支，后续清理必须再做一次全量接入点修复；债务不是消失，而是延期并计息。
3. **可验证性 2 分**：完整验证集只能证明“能编译/测试过”，不能证明“朗读域已删干净”；反证 grep 会命中 stub，无法区分兼容保留与临时垫片。
4. **违反硬约束**：AGENTS.md 禁止无调用方抽象/空实现；`W2-CONVERGENCE.md` §六明确“不引入任何临时垫片（空实现、@Suppress、TODO()）”。因此 B 不是低分候选，而是**不可接受做法**（见 §6）。

### C 为什么淘汰

1. **工作量 1 分**：要重做 227 文件/52k 行删除和 209 错误收敛；A 已完成的 11+ 接入点修复、`MainNavGraph` 422 行/`ReadBookViewModel` 289 行删减全部作废。
2. **正确性 3 分，不高于 A**：C 的“干净重做”只是理论优势；实际重做会再次触发 W1-2/W2 已记录的括号配平、行号偏移、`else` 误删事故，风险不比继续当前工作区低；且需重新做 227 文件级删除。
3. **回滚成本 5 分是唯一优势**：但 A 可以用 checkpoint ref + 单文件 `git checkout` 获得接近的可回退性；C 用“回滚整个 W2a”换来的干净历史，不值得 2–3 倍工作量。
4. **结论**：C 的原则正确、执行代价过高；保留其“小波可编译提交”的方法，淘汰其“整体回滚重做”的路径。

### A 为什么胜出

- 正确性 4：不新增删除范围，只修接入点；保留 `preserveReadAloudPosition` 等阅读语义；按 FINDINGS §6 的保护清单处理 cast/morph/media3。
- 残留债 5：错误驱动收敛会逼出“能编译但语义已死”的残留（Scout-宿主 §5），W2a 一次清掉；后续 W2b/W2c 再清共享核心。
- 可验证性 4：每个文件组都可独立编译，最终完整验证集 + 架构门 + 反证 grep + 单测计数 + APK 体积。
- 工作量 3：剩余 209 错误 + 后续波次，显著小于 C。
- 回滚成本 3：大工作区是弱点，但 checkpoint + 单文件回退可把风险降到可控；优于 B 的“垫片混入后难剥离”。

---

## 5. 选中方案 A 的 W2→W5 执行顺序

### 5.1 总览

| 波次 | 范围 | 出口门（一句话） |
|---|---|---|
| W2a | 当前 227 文件删除 + 209 错误收敛 + 死接线清理 | 编译 0 + 完整验证集 + 单测计数匹配 + W2a 提交 |
| W2b | 共享阅读核心去 cast/bgm：`ReaderPaginator`/`LegacyReaderChapterPaginator`/`ReaderChapterSource`/`ReaderChapterBlockMeasurer`/`ReaderCanvasSurface`/`LegacyReaderStyleRangeMapper`；保留 `CastMarkers` 纯文本标记 | 编译 0 + 分页快照/测试无未解释变化 |
| W2c | 阅读页/宿主收尾：`ReadBook*` 成员、路由 key/entry 同批、`AudioPlayerMorphOverlay`、通知/Manifest/strings/PreferKey | 编译 0 + 完整验证集 + 阅读页真机冒烟 |
| W2-S5 | 非 DB 的 repository/gateway/usecase 残留清理；**entity/dao/AppDatabase 注册并入 W4** | 编译 0 + 反证 grep；无 schema 变更 |
| W2-S6 | `BookType.audio`/`BookSourceType.audio` 类型位；media3 依赖复核 | 编译 0 + 依赖树证据 + 零残留 |
| W3 | 首页模块（D7）→ Web 归档（D8）→ 翻译（D9）→ 导航 3 tab（D7a） | 编译 0 + 完整验证集 + APK 无 `assets/web` + 导航冒烟 |
| W4 | Room `version+1` + 新 migration + 删表/删 entity/dao + `books.type` 清理 + 备份兼容 | 迁移测试 + 旧库升级安装 + 完整验证集 |
| W5 | 基线复核、strings、文档、debug/release/noR8、真机手测、交付 | 全绿 + 手测清单完成 + 无残留 grep |

> **关键排序原则**：DB schema 变更只允许在 W4 发生。W2/W3 可以留下“已无消费者但仍在 `AppDatabase` 注册”的 entity/DAO（有意残留，W4 统一收口），以避免出现“schema 已变、version 未升”的不可安装中间态。

### 5.2 W2a（当前，必须先完成）

**第 0 步：冻结与对账**
1. 保存 checkpoint：`git stash create` 得到 commit，`git update-ref refs/wip/w2a-pre-converge <sha>`；记录 `git status --porcelain > /tmp/w2a-status.txt`。
2. 对账：确认 app 删除 227（177 main + 50 test），记录 8 个无关 `.codebuddy`/`.codex` 删除并排除；若实际数与 227 不符，先更新 `W2-CONVERGENCE.md`，不得带疑问提交。
3. 修掉 `git diff --check` 的 2 处 EOF 空行（`ReadBookViewModel.kt:2356`、`MainNavGraph.kt:1050`）。
4. 重新编译一次，拿到**当前**错误清单；后续以该日志为准，不引用旧行号。

**第 1 步：叶子消费者（先删消费者，避免错误转移）**
- `ui/book/read/ReadBookScreen.kt`、`ReadBookMenuBar.kt`、`ReadBookRouteScreen.kt`
- `ui/book/toc/TocViewModel.kt`、`TocScreen.kt`
- `core/ui/player/PlayerMorphHost.kt`：只删 `AudioPlayerMorphOverlay`/`ReadAloudPlayerMorphHost` 分支；**保留** `PlayerMorphHost` 与 `BookCoverMorphAnchors` 的通用转场（FINDINGS §6 保护清单）。
- 每改完一组立即编译；错误数必须下降，不能把错误“转移”到 Contract。

**第 2 步：ViewModel + DI（同一步）**
- `ui/book/read/ReadBookViewModel.kt` 构造参数与 `di/appModule.kt` 注入参数必须同一步删除：`readAloudSettingsRepository`、`aiCastAssignUseCase`、`aiSceneAssignUseCase`、`syncReadAloudVoicesUseCase`、`readAloudSessionStore`、`httpTtsRepository` 等。
- 这是 W2a 最容易产生“No parameter with name”循环报错的地方；禁止先改一边再编译。

**第 3 步：Contract 类型面**
- `ReadBookContract.kt`：删朗读 Intent/Effect/Sheet 成员；确认所有消费者已在第 1–2 步删除，避免错误反向转移。
- `ReadBookSheet.ReadAloudConfig`、`isReadAloudConfigOpen`、`ReadAloudSubPage` 等按 `W2-CONVERGENCE.md` 决策点执行；优先全删，不留恒 false 属性。

**第 4 步：内核**
- `model/ReadBook.kt`：按 `W2-SCOUT-3B.md` §1 精确删除；**保留 `preserveReadAloudPosition` 形参/实参链**，只把来源改为 `false`（重载不跳位语义不变）。
- `ui/book/read/ReadBookController.kt`：按 Scout-3B §2；`ReadBookController` 的 `when` 分支用 `Unit`/`false` 保持穷尽，不引入空实现类。
- `feature/reader/ReaderCanvasSurface.kt`：删 cast/bgm 绘制函数与胶囊参数；保留 `CastMarkers`/`CastSyntax` 文本链；`readAloud: Color` 参数是文字高亮色，**不要误删**。
- `feature/reader/{legacy/LegacyReaderChapterPaginator,core/source/ReaderChapterSource,core/layout/ReaderChapterBlockMeasurer,legacy/LegacyReaderStyleRangeMapper}.kt`：本轮只去 cast/bgm 接线，完整去字段放到 W2b，避免 W2a 同时改分页热路径。

**第 5 步：宿主（最后）**
- 顺序：`MainScreen` → `MainActivity` → `MainNavGraph`。
- `MainNavKey`/`MainNavigator`/`MainIntent` 必须与 `MainNavGraph` 的 route entry **同批**处理；只删 entry 保留 key 会让 `NavDisplay` 在运行期收到无 entry 的 key。
- 删 `AudioPlayerMorphOverlay`、播放胶囊参数、`isReaderOnTop`、`isAudio` 分支、`route.readAloud` 分支、`createReadBookMediaControlIntent`、`KEY_RESTORE_READ_ALOUD`、`hasActiveAudioPlayRoute` 等“能编译但已死”的接线（`W2-SCOUT-HOST.md` §5）。

**第 6 步：小件群与死代码**
- `ReadBookLoadDelegate`、`SourceLoginJsExtensions`、`AnalyzeUrl`、`Restore`、`CrashHandler`、`App.kt`、`SourceLoginRoute`、`FileAssociationActivity`、`SharedReceiverActivity`、`OnLineImportActivity`、`AppConfig`。
- 按“精确文本替换 / 行号区间 + 括号净差校验”执行，禁止行扫描块删除脚本。

**第 7 步：W2a 出口门**
- `git diff --check` → 0。
- `./gradlew :app:compileAppDebugKotlin` → **0 个 `e:`**；若 `exit=1` 但 `e:` 为 0，先查 `verifyConfigArchitecture` 基线键。
- 完整验证集：
  ```bash
  ./gradlew testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
  ```
  → `BUILD SUCCESSFUL`；单测数下降与 50 个已删测试文件对得上，不允许失败/非预期 skip。
- 架构门：21 键/71、1 键/0、8 键/20；跑 `VERIFICATION.md` §1.2 的缺失键脚本，缺失必须为 0。
- 反证 grep 允许清单：`CastMarkers`/`CastSyntax`（纯文本）、`ReaderPage.RoleCast/BgmScene`（解析类型，若保留）、W4 entity/dao、`PlayerMorphHost` 通用 morph、`AnalyzeUrl` 的 media3。
- 提交前：`git add -A -- app/src/main/java app/src/test/java`；`git diff --cached --name-only | grep -v '^app/src'` 必须为空；`git diff --cached --stat` 必须无 `.codebuddy`/`.codex`/docs；`git show --stat --format='' HEAD | tail -1` 复核文件数/行数。
- 提交信息：`refactor(subtract): W2a 删除朗读/音频/配音 UI 与服务层（文件 227 / 行 -52k）`，正文附验证命令与结果。

### 5.3 W2b（共享阅读核心去 cast/bgm）

**范围**
- `feature/reader/core/layout/ReaderPaginator.kt`：删 `castOptions` 字段/参数/`ReaderCastOptions` 默认值。
- `LegacyReaderChapterPaginator.kt`：删 cast/bgm 胶囊高度、`CastCapsuleGeometry`/`CastCapsuleStyle` 使用；保留文本引号跟踪。
- `ReaderChapterSource.kt` / `ReaderChapterBlockMeasurer.kt` / `LegacyReaderStyleRangeMapper.kt`：只删 cast/bgm 预算与渲染签名，保留 `CastMarkers` 的引号跟踪/`injectParagraph`/`strip` 文本语义。
- `CastCapsuleGeometry.kt`、`CastCapsuleStyle.kt`：确认无外部引用后删除；`CastSyntax.kt`、`CastMarkers.kt` **保留**（`CastMarkers` 明确禁止整文件删）。
- `ReaderCanvasSurface.kt`：删 `drawRoleCast`/`drawBgmScene`/`drawCapsuleBackground` 等绘制函数；`CastAvatarCache` 等随引用删除；不碰文字高亮色参数。

**出口门**
- 编译 0；完整验证集成功。
- 分页行为：对固定章节做“改动前/改动后”页数/页首字符快照对比；差异只能来自 cast/bgm 胶囊高度消失，不能出现普通文本段落错位。若仓库没有现成 fixture，W2b 必须先补一个最小分页快照测试（测试属于本波范围，不算临时垫片）。
- 反证：`ReaderCastOptions`、`CastCapsuleGeometry`、`CastCapsuleStyle` 零引用；`CastMarkers` 仍被文本链引用。

**停止条件**
- 任一分页快照出现未解释差异；或 `ReaderPaginator` 签名改动导致 CacheBook/download 调用方连锁超过 10 处 → 停止，拆成更小一步，不要硬改。

### 5.4 W2c（宿主/阅读页收尾 + 接线）

**范围**
- `ReadBookContract/Screen/FloatingActionBar/MenuBar*` 的朗读成员、`ReadAloud*` sheet、`ReadBookEffect` 朗读分支。
- 路由 `MainNavKey`/`MainNavigator`/`MainIntent`/`MainNavGraph` 的朗读/音频 route key 与 entry 同批删除。
- `MainActivity` 胶囊/播放浮层/`AudioPlay.CallBack`/`createAudioPlayIntent` 残留。
- `AndroidManifest.xml` 的 mediaPlayback service、`NotificationId`、`AppConst`、`IntentAction`、`PreferKey` 朗读键、四语言 strings、`MediaButtonReceiver`（文件已删）。
- `AudioPlayerMorphOverlay` 删除；`PlayerMorphHost`/`BookCoverMorphAnchors` 保留（通用转场）。

**出口门**
- 编译 0；完整验证集成功；`lintAppDebug` 无新增 error。
- 真机/模拟器冒烟：冷启动→书架→打开书→翻页→目录跳转→返回；不得出现 `NavDisplay` 无 entry 崩溃、白屏、返回栈异常。
- 反证：`AudioPlay`、`ReadAloud`、`BgmScene`、`CastCharacter` 在 `ui/`/`service/`/`manifest` 零命中（W4 entity/dao 除外）。

### 5.5 W2-S5 / W2-S6

**W2-S5（非 DB 数据层）**
- 删除已无消费者的 `repository`/`gateway`/`usecase` 残留；**不要**在 W2 删 `data/entities`/`data/dao`/`AppDatabase` 注册（并入 W4，避免 schema/version 不一致）。
- 出口：编译 0；`grep -rn 'ReadAloud\|AudioPlay\|BgmScene\|CastCharacter' data/repository domain` 零命中（允许 W4 清单登记项）。

**W2-S6（类型位与依赖）**
- 独立一步删 `BookType.audio`/`BookSourceType.audio` 及其分支（`BookExtensions`、`ContextExtensions`、`FragmentExtensions`、`WebJsExtensions`、`BookDao` SQL 位运算、`MainNavGraph` 等）。
- media3 **保留**：`AnalyzeUrl.kt:816` 的 `getMediaItem()` 仍服务书源 JS；只有确认 audio 书源类型与该 JS API 都删除后才可移除依赖。输出 `:app:dependencies` 证据。
- 出口：编译 0；完整验证集成功；`grep -rn 'BookType.audio\|BookSourceType.audio' app/src/main` 零命中（W4 迁移中的历史数据处理除外）。

### 5.6 W3（外围域）

**W3-1 首页自定义模块（D7）**
- 删 `ui/main/homepage/**`（19 文件），`HomeScreen` 移除 `HomepageViewModel`/`HomepageModuleFeed`/管理 sheet 入口，退化为简单首页或并入书架。
- `BookSource.homepageModules` 字段保留在 entity/表中；只删 UI/DAO 用法（D7 决策）。
- 出口：编译 0；Home/书架启动冒烟；`grep -rn 'homepageModules\|HomepageModule' app/src/main/java` 仅剩 entity 字段声明。

**W3-2 Web 服务归档（D8）**
- `git mv` `service/WebService.kt`、`web/**`、`api/controller/**`、`api/ReturnData.kt` 到 `attic/webservice/`（保留原包结构 + `attic/README.md` 恢复说明）；**不要**移动 `api/ShortCuts.kt`、`api/ReaderProvider.kt`。
- 删 `app/src/main/assets/web/**`（实测 1.7M）；删 `MyScreen` Web 卡片、`OtherConfig` autostart/wakelock、`MainActivity` 启动/前台服务、Manifest service、`libs.ktor.server.*` 依赖、架构门 `WebService.kt` 键。
- `modules/web` Vue 源码原位保留，不参与 APK 构建。
- 出口：编译 0；`assembleAppDebug`；`unzip -l ... | grep assets/web` 零；`:app:dependencies` 无 `ktor-server`；`grep -rn 'WebService' app/src/main/java` 零命中（attic 不参与编译）。

**W3-3 翻译（D9）**
- 删 `ui/config/translation/**`（3 文件）、翻译入口/设置项、`TranslateChapterUseCase`、`TranslationCacheGateway`/`RepositoryImpl`（entity 保留到 W4）；清理 `ReadBookViewModel` 中 W1-3 遗留的翻译状态读取，避免“已删 AI 但翻译状态仍刷新”的半链。
- 出口：编译 0；`grep -rn 'TranslationConfig\|TranslateChapter\|TranslationCacheGateway' app/src/main/java` 零命中（entity 声明除外）。

**W3-4 导航 3 tab（D7a）**
- 一级导航收敛为 `书架 / 发现 / 我的`；`MainDestination`/`MainNavKey`/`MainNavGraph`/`MainScreen`/`MainNavigator`/`MainIntent` 热点单 writer 串行。
- 出口：编译 0；完整验证集；冷启动→三 tab 切换→书架→发现→我的→返回；截图/手测记录。

**W3-5 收口**
- 架构门键清理、lint 基线纯删除、反证 grep、APK 体积记录；**不允许** `updateLintBaseline`。

### 5.7 W4（数据层收口，串行）

**W4-1 DB 版本 + 迁移**
- `AppDatabase` version+1；`DatabaseMigrations.kt` **只追加新 migration，不改历史**；DROP 被删域表（`read_aloud_*`、`bgm_*`、`voice_pool*`、`cast_*`、`cloud_tts_engine`、`httpTTS`、rss/ai/homepage 相关表；具体清单以 D11 冻结为准）。
- 从 `AppDatabase.entities`/`appDatabaseModule` 删除 entity/dao 注册；这一步与 W4-1 同批，不能提前到 W2。

**W4-2 `books.type` 僵尸数据**
- 迁移把 `audio`/`image` 书标记为 `notShelf` 或移出书架；`video` 按 D1 处理。迁移测试断言旧数据不出现“打不开的书”。

**W4-3 备份/主题兼容**
- 旧备份 `httpTTS.json` 忽略而非报错；`showRss`/`navIconRss*` 等字段保留读取（D11 决策三）；`BackupConfig`/`Restore` 测试覆盖旧版本备份。
- `ThemeExportData`/`AppShellSettings` 旧字段兼容。

**W4-4 验证**
- 迁移测试（Room schema + 数据断言）+ 旧版本 APK 覆盖安装 + 冷启动/书架/阅读冒烟；Verifier 独立执行，不接受 Migrator 自证。
- 出口：迁移测试全绿、升级安装不崩溃、完整验证集 `BUILD SUCCESSFUL`。

### 5.8 W5（收口交付）

1. **W5-1 基线复核**：三条架构门键与实际文件/计数逐项一致；清理 `app/lint-baseline.xml` 中 33 条“文件存在但问题已消失”的过时条目（只删 issue/location，纯删除）。
2. **W5-6 strings**：四语言清理 `ai_*`、`book_characters`、`character_network`、`plot_events`、`rss_*`、`manga_*`、朗读专用 key；`grep -c` 归零或登记保留项。
3. **W5-2 文档**：`README.md`、`AGENTS.md`、`docs/dev/feature-catalog.md`、`docs/index.md` 同步；README 顶部“规划阶段（未动代码）”改为实际状态。
4. **W5-3 构建**：`assembleAppDebug`、`assembleAppRelease`、`assembleAppNoR8`；记录 APK 三 ABI 体积对比（W0 基线 71.7/71.2/78.2 MB）。
5. **W5-4 真机手测**：按 `VERIFICATION.md` §4 清单逐项勾选；重点：冷启动、搜书→加书架→阅读→翻页→目录、换源、缓存、TXT/EPUB 导入、替换净化、主题、旧备份恢复、升级安装、三 tab 导航。
6. **W5-5 交付说明**：改动范围、取舍、验证结果、未验证风险；不得把“已生成代码/已移动文件”当完成标准。

### 5.9 每波独立验证点与停止条件

| 波次 | 独立验证点（Verifier，非执行者） | 停止条件 |
|---|---|---|
| W2a | 重新编译 0 `e:`；完整验证集；架构门缺失键脚本；单测数与 50 个删测文件匹配；`git diff --cached --name-only` 仅 app 源码；反证 grep；APK 体积 | 209 连续两轮不降；括号净差 ≠ 0；错误转移到未认领文件；`git diff --check` 非 0；删除数 ≠ 227 且未解释 |
| W2b | 分页快照/Reader 测试对比；`ReaderCastOptions`/`CastCapsule*` 零引用；完整验证集 | 普通文本分页出现未解释差异；分页签名连锁 >10 处 |
| W2c | 冷启动/阅读页/目录/返回冒烟；route key↔entry 一致性 grep；Manifest/Notification/PreferKey 反证 | `NavDisplay` 运行期异常、白屏、返回栈错误；route key 或 entry 单边残留 |
| W3 | Home 冒烟；APK `assets/web` 零；ktor-server 依赖零；翻译 grep 零；三 tab 冒烟 | 首页无法退化启动；三 tab 导航崩溃；Web 产物仍进 APK |
| W4 | 迁移测试；旧库覆盖安装；`books.type` 断言；旧备份恢复 | 迁移失败/升级崩溃；schema 与 version 不一致；数据丢失 |
| W5 | release/noR8 构建；手测清单；最终反证 grep；APK 体积；基线逐项复核 | R8/release 失败；核心阅读路径手测失败；残留 grep 未解释 |

**全局停止条件（任何波次触发即停手并上报 lead）**
1. 需要临时垫片、`@Suppress`、`TODO()`、空实现或恒 false 分支才能继续。
2. 需要放宽架构门/lint 基线，或删除基线键但文件仍存在。
3. `verifyConfigArchitecture` 失败但 `e:` 为 0（先处理基线键，不要当编译错误修）。
4. 同一文件连续 2 轮错误不降，或括号净差 ≠ 0；按 README §6 回退到已知良好状态重做。
5. 需要修改未认领热点文件，或两个 writer 同时改同一文件。
6. 完整验证集未过却想提交；或同一问题连续 3 次修复失败（AGENTS.md 停手规则）。

### 5.10 提交与回滚纪律（W2a 尤其重要）

- 提交前必须 `git diff --cached --stat` 核对文件集；`git commit` 提交整个索引，不是“本次 `git add` 的那些”。
- 只 stage `app/src/main/java` 和 `app/src/test/java`；8 个 `.codebuddy`/`.codex` 删除、docs、`docs/book-source-verification/` 不进入 W2a。
- 若发现索引混入无关路径：`git reset -- <path>` 移出索引（工作区不受影响）；不要用 `git add docs` 后再 commit。
- 每波提交后 `git show --stat --format='' HEAD | tail -1` 复核文件数/行数，异常则 `git reset --soft HEAD~1` 修正。
- 全量回滚只允许 `git checkout HEAD -- app/src/main/java app/src/test` + `git reset`；先确认 checkpoint ref 存在。**禁止 `git reset --hard`**。
- 文档提交与源码提交分开；`PLAN-CANDIDATES.md` 本身不进入 W2a 提交。

---

## 6. 不可接受做法（硬红线）

### 6.1 临时垫片

- 新建空 `object`/空 `class`/空方法、`TODO()`、`@Suppress`、`if (false)`、恒 `false` 属性来“让编译过”。
- 保留已删朗读 route key 但只删 entry，或保留空 `ReadAloudSubPage` 枚举占位；这会在运行期造成无 entry 的 `NavDisplay` 崩溃或死入口。
- 用“兼容保留”名义保留临时 stub；兼容保留只允许 `DECISION-MAP.md` 明确列出的旧备份/旧偏好字段。

### 6.2 放宽基线

- 运行 `:app:updateLintBaseline`（会把当前全部问题写入基线，W1-3 实测 425→502，等于放宽棘轮）。
- 手工调高 `legacyPreferenceCallBaseline`/`legacyDaoInjectionBaseline`/`legacyUiDaoAccessBaseline` 的计数。
- 删除指向已删文件的基线键时，把该键“替换”为另一个文件或凭空加计数；只允许纯删除该行。
- 删除 0 值哨兵（如 `ReadBookViewModel.kt to 0`）；它们是刻意的棘轮监视点。

### 6.3 跳过验证

- 只跑 `:app:compileAppDebugKotlin` 就宣布 W2a 完成；必须跑完整验证集。
- 编译 0 但不跑单测/lint/架构门/assemble；不记录 APK 体积。
- Verifier 由执行该波的 Surgeon 兼任；或只写“看起来没问题”而无命令+输出片段。
- 单测数下降不核对删除测试文件；把“少跑的测试”当成“通过”。
- 提交时编译未过、验证未跑、或构建进行中修改工作区。

### 6.4 其他已记录事故模式

- 行扫描式块删除脚本（`while line.strip() != '}'`/`drop_method`/`drop_indent_block`）；W1-2 已造成 3 次结构破坏。
- 按行号区间删除但不做括号净差校验；W2 `MainActivity` 曾吃掉 `} else {` 的 `}`，导致整类成员互相引用失败。
- 按文件名/`find *manga*`/`*ai*` 盲删；`Main`/`Detail`/`Plain` 等假阳性会误伤保留代码。
- 改历史 `DatabaseMigrations.kt`；只允许追加新 migration。
- 用 `git reset --hard` 或 `git checkout -- .` 覆盖未提交工作/未跟踪文档。
- 把 docs 与源码删除混在一个提交；或 `git add docs` 时索引里已有代码删除（README §6.7 的 229 文件事故）。

---

## 7. 未验证项与假设

| 项 | 状态 | 说明 |
|---|---|---|
| 209 错误 | **未由 Architect 复跑** | 按任务上下文采用；W2a 开工前必须重新编译确认 |
| 当前错误文件分布 | 未验证 | 本文 §1.3 是静态 grep，不是编译器归属 |
| 227 vs 225 删除差 | 未解释 | 需 W2a 前对账并更新文档 |
| `git diff --check` 2 处 EOF 空行 | 已实测 | 当前工作区确实失败，必须修 |
| W3/W4/W5 规模 | 来自既有文档 | 未重新逐文件测绘；执行前由对应 Scout 复核 |
| 真机行为 | 未验证 | 无设备执行；W2c/W5 手测消解 |
| 评分权重 | 架构判断 | 已在 §3 做敏感性检查；A 对权重不敏感 |

**假设**
1. 用户已裁决 D4b/D4c/D4d/D5/D6/D7/D8/D9，W2a 删除范围有效。
2. `attic/webservice/` 归档方式按 D8 执行，`modules/web` 源码保留但不进 APK。
3. 所有执行波次遵循“单 writer + 热点串行 + 每波编译/提交”的既有协作协议。
4. W4 之前不发布任何包含 DB schema 变更的构建；W2/W3 的 entity/dao 残留视为有意债务，W4 统一收口。
