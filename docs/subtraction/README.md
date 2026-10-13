# 项目减法工程（只保留小说阅读与书源）

> 目标：把本仓库收敛为一台**纯小说阅读器**——阅读、书源、书架、搜索、缓存、本地导入、
> 阅读设置/主题、替换净化、备份恢复；移除与小说阅读无关的扩展域。
>
> 状态：**规划阶段（未动代码）**。决策待用户逐项裁决，见 [`DECISION-MAP.md`](./DECISION-MAP.md)。
>
> 创建：2026-10-14 ｜ 分支基线：`main` @ `070ca1a9b`

---

## 一、范围

**在范围内**：删除/收敛非小说功能域（代码、资源、路由、DI、DB 表、偏好键、字符串、测试、文档）；
同步下调构建护栏基线；保证删除后编译器、单测、架构门、lint、assemble 全绿。

**不在范围内**：
- 不改动保留下来的小说功能的行为（阅读渲染、书源规则引擎、Rhythm/JS 执行、缓存下载逻辑）。
- 不引入新架构、不顺手重构、不动 `modules/rhino`、不改书源规则语义。
- 不借机升级依赖（除非某依赖的唯一消费者被删光，此时才移除该依赖声明）。

---

## 二、文档地图（Agent Teams 同步载体）

| 文档 | 作用 | 谁写 | 谁读 |
|---|---|---|---|
| `README.md`（本文件） | 总纲、团队编制、波次看板、协作协议 | Orchestrator | 全体 |
| `DECISION-MAP.md` | 决策点、选项、权衡、推荐、依赖、用户裁决记录 | Orchestrator | 全体 + 用户 |
| `FINDINGS.md` | 侦察事实（各域文件清单、耦合点、命令证据） | 侦察组 | 全体 |
| `TASK-BOARD.md` | 波次任务、认领人、状态、产出证据 | Orchestrator | 全体 |
| `VERIFICATION.md` | 验证矩阵：命令、结果、未验证项、手测清单 | 验证组 | 全体 |

**同步规则**：
1. 任何 agent 开工前先 `claim_files` 认领文件；完成后在 `TASK-BOARD.md` 写状态与证据路径。
2. 决策变更只能改 `DECISION-MAP.md` 的「裁决」列，禁止在对话里"口头约定"。
3. 侦察结论必须附**可复现命令**；未能验证的写成「未验证」而不是结论。
4. 波次之间以**编译通过 + 提交**为界，不允许跨波次留下半截状态。

---

## 三、Agent Teams 编制

| 角色 | 代号 | 职责 | 工具权限 | 不能做 |
|---|---|---|---|---|
| Orchestrator | 主控（本会话） | 裁决落地、热点文件串行收口、波次合并、最终交付 | 全部 | 不跳过验证门 |
| Scout ×N | 侦察组 | 只读测绘（文件面、耦合点、断裂点） | 只读 | 不改文件、不建构建 |
| Surgeon ×N | 域切除组 | 删域内文件 + 清域内引用 | 读写（仅本域 + 已认领热点） | 不改他人域、不改基线 |
| Integrator | 集成者 | 热点文件统一收口（导航/DI/DB/strings/基线/备份） | 读写（热点文件独占） | 不删域内文件 |
| Migrator | 数据组 | Room 版本与迁移、旧数据清理、兼容字段处理 | 读写（DB/迁移） | 不碰 UI |
| Verifier | 验证组 | 独立验证：编译、单测、架构门、反证检索 | 只读 + 执行验证命令 | **不得参与删除**，不得修改源码 |
| Scribe | 文档组 | README/AGENTS/docs/dev/feature-catalog 同步 | 读写（仅文档） | 不改代码 |

**独立验证原则**：Verifier 与被验证的 Surgeon 不是同一个 agent；Verifier 的结论必须来自
真实执行输出（命令 + 关键输出片段），不接受"看起来没问题"。

**执行环境事实（W0 已核实）**：JDK 21.0.12 ✓；Android SDK `/home/handx-aijiu/android-sdk`（platform `android-37.2`、build-tools `37.0.0`）→ 构建前必须 `export ANDROID_HOME=/home/handx-aijiu/android-sdk`（仓库无 `local.properties`）。
**调度限制**：`subagent` 池上限 16 且被历史会话占用，改用 `bg_delegate`（inspect 能力，只读）作为侦察梯队的等价替代；执行阶段同样以 `bg_delegate` + 主控串行为主。

---

## 四、波次看板（待 `DECISION-MAP.md` 裁决后启动）

| 波次 | 内容 | 并行度 | 出口条件 | 状态 |
|---|---|---|---|---|
| W0 | 基线冻结：全量验证跑通 + 记录基线快照 + 建工作分支 + TTS 域专项侦察 | 串行 | 基线证据入 `VERIFICATION.md` | 🟢 完成 |
| W1 | 共享点剥离 + 叶节点域删除（RSS / 漫画 / AI / 知识库·角色·评论） | 域内并行，热点串行 | 编译 + 单测 + 架构门通过并提交 | 🔵 进行中（W1-0 已落地） |
| W2 | 朗读大块（D4b TTS/听书 + D4c 配音 + D4d BGM + D4a 音频书/播放器） | 1–2 路（与阅读页深度耦合） | 同上 | ⬜ 未开始 |
| W3 | 首页自定义模块（D7）+ Web 归档（D8）+ 翻译（D9）+ 导航收敛（D7a） | 2–3 路并行 | 同上 | ⬜ 未开始 |
| W4 | 数据层收口：Room 版本 + 迁移 + 旧数据清理 + 备份/主题兼容 | 串行 | 迁移测试 + 升级安装验证 | ⬜ 未开始 |
| W5 | 收口：基线复核、官方文档同步、release 打包、真机手测 | 串行 | 全绿 + 手测清单勾完 | ⬜ 未开始 |

---

## 五、验证门（每波必过，细节见 `VERIFICATION.md`）

```powershell
git diff --check
.\gradlew.bat :app:compileAppDebugKotlin
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
```

- 架构门 `verifyConfigArchitecture` 是**双向棘轮**：删代码导致基线计数下降必须同步下调，
  否则门禁报红；基线条目对应的文件被删除会**硬失败**（见根 `build.gradle.kts:216-302`）。
- 波次提交信息格式：`refactor(subtract): W<N> 删除 <域>（文件 N / 行 M）`，正文附验证命令与结果。

---

## 六、协作协议（Agent Teams 硬约束）

**1. 禁止行扫描式块删除脚本。** 删除 Kotlin 代码块时，不得用“按缩进找配对花括号”（`while line.strip() != '}'` / `drop_method` / `drop_indent_block`）的脚本。它在以下情形会配平失败并**静默吃掉大段代码**：

- 表达式体函数（`fun f(): T = ...`，体内无闭合花括号）；
- 嵌套 lambda / 匿名对象（`interceptors.add { ... }`）；
- 多行 `data class`/`when` 分支。

替代手段（按优先级）：① `edit` 工具的精确文本替换（oldText 取完整块）；② 用 `grep -n` 确认起止行号后按**行号区间**删除；③ 先 `git checkout --` 恢复再用 ① 重做。W1-2 因违此条产出 3 次结构破坏（详见 `VERIFICATION.md` §7.2）。

**2. 脚本产出后立即执行并核对。** 写了脚本却不运行会静默漏项（W1-2 的 `fix_manga_refs6.py` 漏执行导致 `OtherConfig`/`ClearMangaCache` 残留到下一轮）。

**3. 构建/测试运行期间不改工作区。** 启动 gradle 后至结束前，不修改任何源文件（只读侦察允许）。

**4. 波次出口三件套。** 编译 0 错 → 完整验证集 `BUILD SUCCESSFUL` → 单测/APK 数字与基线对照入 `VERIFICATION.md`。未跑完不提交、不声称完成。

**5. 热点文件串行。** `ui/main/{MainNavGraph,MainNavKey,MainNavigator,MainIntent,MainActivity,...}.kt`、`di/appModule.kt`、`res/values*/strings.xml` 同一时刻只能一个波次改。

**6. 不确定的自建改动优先回滚重做。** 一旦发现脚本/编辑破坏了既有结构，不要在其上继续修补（会叠加语义漂移），而是 `git checkout -- <file>` 恢复到上一个已知良好状态后精确重做。
