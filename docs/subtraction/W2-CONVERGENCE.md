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

## 四、验收标准（每个任务）

1. 该文件不再出现未解析引用（编译错误归零）。
2. 删除的是**朗读专属**代码；被保留功能（书源、正文渲染、分页、本地书、TTS 之外的一切）引用的一律不改语义。
3. 不引入任何临时垫片（空实现、`@Suppress`、`TODO()`）。
4. 主控编辑完成后**立即编译**核对；波次出口跑完整验证集。
