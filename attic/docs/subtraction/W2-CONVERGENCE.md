# W2 收敛作业单（Agent Teams 同步载体）

> 状态：🔵 进行中 ｜ 目标：把 W2a 的编译错误从 **629 收敛到 0**，然后提交 W2a
> 角色：**主控（Surgeon）是唯一 writer**；Scout 只读侦察并回报，产物由主控写入本文档；Verifier 独立复核。
> 硬约束（不可违反）：`feature/reader/core/cast/CastMarkers.kt` **保留**；`feature/reader` 分页/绘制链路**不整删**；
> media3 **保留**（`AnalyzeUrl.kt:816`）；架构门删文件必须同步删键（见 `README.md` §5.1）。

---

## 一、当前事实（实测，勿重复推理）

- 已删除 **225 文件**（`git rm` 已暂存）；已修 **11 个接入点**。
- 最近一次编译日志：`/tmp/w2a5.log` → **629 错误 / 20 个接入点文件**。
- 错误提取模板：
  ```bash
  grep '^e: ' /tmp/w2a5.log | sed 's|.*/io/legado/app/||' | sed 's/:[0-9]*:[0-9]* .*//' | sort | uniq -c | sort -rn
  grep '^e: ' /tmp/w2a5.log | grep 'ReadBookViewModel.kt:' | sed 's|.*ReadBookViewModel.kt:||' | sed 's/:.*//' | sort -n | uniq | tr '\n' ' '
  ```
- 编译命令（构建期间**禁止改工作区**）：
  ```bash
  export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
  ./gradlew :app:compileAppDebugKotlin --console=plain
  ```

## 二、任务分解与认领

| # | 文件组 | 错误数 | 认领 | 状态 | 出口 |
|---|---|---|---|---|---|
| T1 | `ReadBookContract.kt` | 9 | Scout-1 → 主控 | 🔵 侦察中 | 精确删除清单 |
| T2 | `ReadBookViewModel.kt` | 117 | Scout-1 → 主控 | 🔵 侦察中 | 精确删除清单（含构造参数同步项） |
| T3 | `di/appModule.kt` | 88 | 主控（依赖 T2 结果） | ⬜ | 与 T2 **同一步**删注入参数 |
| T4 | `ReadBookController.kt` | 37 | Scout-3 → 主控 | 🔵 侦察中 | 清单 |
| T5 | `ReadBookScreen.kt` / `ReadBookMenuBar.kt` / `ReadBookRouteScreen.kt` | 25+12+6 | Scout-3 → 主控 | 🔵 侦察中 | 清单 |
| T6 | `model/ReadBook.kt` | 51 | Scout-3 → 主控 | 🔵 侦察中 | 清单（**保 preserveReadAloudPosition 语义**） |
| T7 | `ui/book/toc/TocViewModel.kt` | 14 | Scout-3 → 主控 | 🔵 侦察中 | 清单 |
| T8 | `feature/reader/ReaderCanvasSurface.kt` | 14 | Scout-3 → 主控 | 🔵 侦察中 | 清单（cast/bgm 绘制分支） |
| T9 | `ui/main/MainNavGraph.kt` | 133 | Scout-2 → 主控 | 🔵 侦察中 | 路由 entry 删除清单 |
| T10 | `ui/main/MainActivity.kt` | 85 | Scout-2 → 主控 | 🔵 侦察中 | intent/叠层删除清单 |
| T11 | `ui/main/MainScreen.kt` | 18 | Scout-2 → 主控 | 🔵 侦察中 | 叠层删除清单 |
| T12 | 小件群：`AnalyzeUrl`、`Restore`、`CrashHandler`、`PlayerMorphHost`、`App.kt`、`SourceLoginRoute`、`FileAssociationActivity`、`SharedReceiverActivity`、`OnLineImportActivity`、`SourceLoginJsExtensions`、`AppConfig`、`ReadBookLoadDelegate` | 2+2+2+2+2+1+1+1+2+2+1+2 | **主控** | 🔵 进行中 | 逐个精确编辑 |
| T13 | 波次出口 | — | Verifier | ⬜ | 编译 0 错 + 完整验证集 + 反证 grep |

## 三、待择优的决策点（自动择优：给出 ≥2 方案后取更小改动/更少临时垫片者）

| # | 决策点 | 方案 A | 方案 B | 选择依据 |
|---|---|---|---|---|
| D-a | `ReadBookContract.kt` 的 `ReadBookIntent`/`ReadBookEffect` 中朗读成员（约 60 个） | 全删成员 | 保留空 data object 占位 | 删除后 `when` 是否仍穷尽；**优先 A**（不留死代码，符合 AGENTS.md） |
| D-b | `ReadBookSheet.ReadAloudConfig` 与 `isReadAloudConfigOpen` | 删 sheet + 属性 + 所有引用 | 保留属性恒 false | **优先 A**，除非引用面过大（>15 处）导致本轮风险过高 |
| D-c | `ReadAloudSubPage` 枚举 | 删枚举 + 相关 effect | 保枚举、只删 UI 页面 | 同上 |
| D-d | `MainNavGraph` 的朗读路由 Key | 删 `MainNavKey` 中的路由定义 + graph entry | 只删 graph entry | Key 无消费者则全删（**优先 A**） |
| D-e | `model/ReadBook.kt` 的 `preserveReadAloudPosition` | 保留参数、调用方传 false | 删参数 | **A**（FINDINGS §6 要求保"重载不跳位"语义） |

## 五、执行日志（每步更新）

### 2026-10-14 第 1 步：T12 小件群 ✔ 完成

12 个文件全部修复，编译错误 **629 → 539**；架构门无报错（基线键修正生效）。
涉及：`AnalyzeUrl`（内联 `createMediaItem`，保留书源 JS API 面与 media3）、`Restore`（恢复被误删的
`DirectLinkUpload` import）、`CrashHandler`、`App.kt`、`SourceLoginRoute`、`FileAssociationActivity`、
`SharedReceiverActivity`、`OnLineImportActivity`、`SourceLoginJsExtensions`、`AppConfig`、
`ReadBookLoadDelegate`（另：`MediaButtonReceiver`、`IdentifyBookCharactersUseCase` 已整文件删）。

### 2026-10-14 策略修正（自动择优，替代原计划）

**Scout 可靠性实测**：派出的 3 个 Scout 中 **Scout-1、Scout-3 在 2–3 分钟内双双 `aborted` 失败**；
仅 Scout-2、Scout-3b（缩小范围后）存活。⇒ **不再把 Scout 报告作为阻塞依赖**，主控串行推进，
Scout 返回只作交叉校验。

**执行顺序修正（关键）**：原计划按错误数从大到小修。修正为**自底向上**：

```
叶子消费者（ReadBookScreen / ReadBookMenuBar / TocViewModel / ReadBookRouteScreen / PlayerMorphHost）
   → ReadBookViewModel + di/appModule（同步）
   → ReadBookContract（此时字段已无消费者，删了不会转移错误）
   → 内核（model/ReadBook、ReadBookController、ReaderCanvasSurface）
   → 宿主（MainNavGraph、MainActivity、MainScreen）
```

理由：`ReadBookContract` 是**类型面**。若先删它的字段，错误会**转移到**尚未修改的 ViewModel/Screen，
错误数不降反升；先删消费者则 Contract 的字段自然变成“无引用”，一次删除、零转移。
这与 W1-1/W1-2/W1-3 已验证的“从叶到根”模式一致。

### 剩余接入点（539 错误，第 6 轮实测）

| 文件 | 错误 | 排期 |
|---|---|---|
| `ui/main/MainNavGraph.kt` | 133 | 最后（宿主） |
| `ui/book/read/ReadBookViewModel.kt` | 117 | 第 2 步 |
| `ui/main/MainActivity.kt` | 85 | 最后 |
| `model/ReadBook.kt` | 51 | 第 4 步 |
| `ui/book/read/ReadBookController.kt` | 37 | 第 4 步 |
| `ui/book/read/ReadBookScreen.kt` | 25 | 第 1 步（当前） |
| `ui/main/MainScreen.kt` | 18 | 最后 |
| `di/appModule.kt` | 16 | 第 2 步（与 VM 同步） |
| `ui/book/toc/TocViewModel.kt` | 14 | 第 1 步 |
| `feature/reader/ReaderCanvasSurface.kt` | 14 | 第 4 步 |
| `ui/book/read/ReadBookMenuBar.kt` | 12 | 第 1 步 |
| `ui/book/read/ReadBookContract.kt` | 9 | 第 3 步 |
| `ui/book/read/ReadBookRouteScreen.kt` | 6 | 第 1 步 |
| `core/ui/player/PlayerMorphHost.kt` | 2 | 第 1 步 |

---

### 2026-10-14 第 2、3 步：RouteScreen + TocViewModel + Scout-3b 报告落盘

- 编译 7：**500 错误**（539→500）；`ReadBookScreen` / `ReadBookMenuBar` / `PlayerMorphHost` 清零。
- `ReadBookRouteScreen.kt` 1125 → **1097 行**（删 4 宿主回调 + 4 effect 分支 + 朗读进度收集
  + `CompositionLocalProvider(LocalCastSheetVisuals…)` 包裹层，含 13 行缩进上提）。
- `TocViewModel.kt` 1121 → **1047 行**（整条「目录配音/听书状态标记」特性链：
  3 import + 4 字段 + 2 intent + `TocStatusFlags`/`statusFlagsFlow`/`combine` 形参
  + 2 处构造 + 3 个函数；另补删漏网 2 行）。

#### Scout 可靠性结论（实测，勿再假设）

| 任务规模 | 结果 |
|---|---|
| 2–6 文件 / 500+ 行输出上限 | **全部 aborted**（Scout-1 @2m49s、Scout-2 @3m34s、Scout-3 @2m01s） |
| **3 文件 / 250 行输出上限** | **成功**（Scout-3b，21 turns / 67 tool calls） |

⇒ `bg_delegate` 在本环境可用，但**必须把任务切到 3 文件量级**；大任务不可靠。
主控串行仍是主路径，Scout 仅作交叉校验与清单生成。

#### Scout-3b 的关键交付（已落盘 `W2-SCOUT-3B.md`，18,505 bytes / sha256 校验通过）

1. 三文件逐块清单（行号 + 动作 + 依据 + 风险），错误行号与源码逐条核对吻合。
2. **`preserveReadAloudPosition` 最小改法**：形参/实参链全保留，只删两处“从已删服务推导”的地方
   （`ReadBook.kt:1402-1403` 删 + `1406` 改 `= false`；`1246-1266` 整块删）。
3. **连带项表**（跨文件同批处理依据）：
   `ReadBook.readAloud()` 有 **7 个外部调用点**（`MainNavGraph:839`、`ReadBookViewModel:1244/1302/1769`、
   `ReadBookController:2461/2589/2600`）；`onComposeManualPageTurn`、`updateReadAloudProgress`、
   `setComposeVisibleBodyTextPositionProvider`、`openBgmSceneAtSelection` 各 1–2 处。
4. **6 个不确定项**明确标注“禁止猜测，需编译验证”。
5. 纠正一处易误报：`ReaderCanvasSurface:1981` 的 `readAloud` 是**高亮文字色**，与朗读域无关。

---

## 六、验收标准（每个任务）

1. 该文件不再出现未解析引用（编译错误归零）。
2. 删除的是**朗读专属**代码；被保留功能（书源、正文渲染、分页、本地书、TTS 之外的一切）引用的一律不改语义。
3. 不引入任何临时垫片（空实现、`@Suppress`、`TODO()`）。
4. 主控编辑完成后**立即编译**核对；波次出口跑完整验证集。
