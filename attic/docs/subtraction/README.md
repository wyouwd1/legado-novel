# 项目减法工程（只保留小说阅读与书源）

> 目标：把本仓库收敛为一台**纯小说阅读器**——阅读、书源、书架、搜索、缓存、本地导入、
> 阅读设置/主题、替换净化、备份恢复；移除与小说阅读无关的扩展域。
>
> 状态：**W1–W7 已落地并提交**；W7 在 Android 35 模拟器完成全新安装/阅读冒烟并修复首次建库崩溃，1,315 单测全绿；剩余 lint 全量复跑、NoR8 打包、真实 ARM 真机手测与真实设备升级未完成，见 [`DELIVERY.md`](./DELIVERY.md)、[`W6-ADVERSARIAL.md`](./W6-ADVERSARIAL.md) 与 [`W7-EMULATOR.md`](./W7-EMULATOR.md)。决策见 [`DECISION-MAP.md`](./DECISION-MAP.md)，候选方案择优见 [`PLAN-CANDIDATES.md`](./PLAN-CANDIDATES.md)。
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
| W1 | 共享点剥离 + 叶节点域删除（RSS / 漫画 / AI / 知识库·角色·评论） | 域内并行，热点串行 | 编译 + 单测 + 架构门通过并提交 | 🟢 完成 |
| W2 | 朗读大块（D4b TTS/听书 + D4c 配音 + D4d BGM + D4a 音频书/播放器） | 1–2 路（与阅读页深度耦合） | 同上 | 🟢 完成 |
| W3 | 首页自定义模块（D7）+ Web 归档（D8）+ 翻译（D9）+ 导航收敛（D7a） | 2–3 路并行 | 同上 | 🟢 完成 |
| W4 | 数据层收口：Room 版本 + 迁移 + 旧数据清理 + 备份/主题兼容 | 串行 | 迁移测试 + 升级安装验证 | 🟡 SQL + 设备端 AutoMigration 通过；真实设备升级未跑 |
| W5 | 收口：基线复核、官方文档同步、release 打包、真机手测 | 串行 | 全绿 + 手测清单勾完 | 🟡 lint/NoR8/真实 ARM 手测残留 |
| W6 | 对抗性测试：异常输入、边界、并发、网络失败、错误假设 | 3 路并行 + 主控 | 105 新增测试 + 全量门禁 | 🟢 完成（`W6-ADVERSARIAL.md`） |
| W7 | Android 模拟器：全新安装、onboarding、书源入口、本地阅读、设备端迁移 | 串行 | 设备证据 + 回归测试 | 🟢 完成（`W7-EMULATOR.md`） |

---

## 五、验证门（每波必过，细节见 `VERIFICATION.md`）

```powershell
git diff --check
.\gradlew.bat :app:compileAppDebugKotlin
.\gradlew.bat testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
```

### 5.1 架构门 `verifyConfigArchitecture` 机制（W2 实测，必须理解）

定义在根 `build.gradle.kts:216-302`（`VerifyConfigArchitectureTask`），被**所有** `compile*`/`assemble*`
前置依赖（`:281`），因此它报红时表象是“编译失败” —— **先看它的输出**。
它是**根项目**任务，不能写成 `:app:verifyConfigArchitecture`。

三条基线（`MapProperty<String, Int>`，键 = 相对 `app/src/main/java` 的文件路径，值 = 允许的违规数）：

| 基线 | 约束对象 | W0 基线值 |
|---|---|---|
| `legacyPreferenceCallBaseline` | 旧偏好 API 直连（`getPref*`/`putPref*`） | 23 键 / 74 |
| `legacyDaoInjectionBaseline` | **文件名含 `ViewModel`** 的文件注入 DAO | 2 键 / 13 |
| `legacyUiDaoAccessBaseline` | 其余 UI 层文件直连 DAO（宽松档，防止把债“洗白”） | 11 键 / 30 |

**两条不变式（缺一即报红）**：

1. **计数棘轮（双向）**：实际违规数 > 基线 → 失败；删代码使计数下降后**必须同步下调基线**。
   门禁不接受“先放宽基线再迁移”。
2. **键 ↔ 文件存在性（硬失败）**：基线键指向的文件被删除时必须同步删键，否则直接失败，报错原文：

   ```
   配置架构护栏失败:
     app/src/main/java/io/legado/app/ui/.../XxxViewModel.kt: 文件已移除，请删除 DAO 直连基线
   ```

   ⇒ **每个波次删除被基线跟踪的文件后，必须同一步删键**（W1-3 删 `CheckSourceConfig.kt` 键、
   W2 删 `CloudTtsViewModel.kt to 13` 键都是这条规则）。危险信号：编译 `exit=1` 但 `grep -c '^e: '` 为 **0**。

**0 值哨兵**：形如 `"ReadBookViewModel.kt" to 0` 的条目**是刻意的**，源码注释写明“保留 0 值条目让棘轮
继续盯着这个文件——新增一处直连就报红”。清理过时条目时**不要删 0 值条目**。

**与 `app/lint-baseline.xml` 的区别（别混用手段）**：

| | 架构门基线 | lint 基线 |
|---|---|---|
| 位置 | 根 `build.gradle.kts` | `app/lint-baseline.xml` |
| 不匹配时 | **硬失败**（计数超限或文件不存在） | 过时条目仅**警告**，不失败 |
| 放宽手段 | 手工改 map（`updateLintBaseline` 管不到） | ⚠️ `./gradlew :app:updateLintBaseline` 会把**当前全部问题**写进基线（W1-3 实测 425 → 502），**等于放宽棘轮，禁止使用** |
| 清理过时条目 | 按内容精确删行 | 文本级按块删除（只删指向不存在文件的 `issue`，保持纯删除 0 插入） |

**波次收尾核对**：列出三条基线中指向不存在文件的键（脚本见 `VERIFICATION.md` §1.2），逐个处理；
删域后同步下调计数。`git diff --check` 与这一项都要过。

**删键操作纪律**：删除基线键时**只删该行**，不得把它“替换”为另一个文件或别的计数——
那等于凭空给未审计的文件发放额度（W2 实操中犯过，已修正为纯删除）。同时不要删 0 值哨兵。

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

**7. 提交前必须核对索引内容。** `git commit` 提交的是**整个索引**，不是“本次 `git add` 的那些”。当工作区已有 `git rm`/`git add` 暂存的改动时（例如波次删除正在进行中），只 `git add docs/` 再 commit 会把**已暂存的代码删除**一并提交，污染历史。

- 每次 commit 前跑 `git diff --cached --stat` 核对文件集；
- 只想提交部分路径时，先 `git reset -- <其他路径>` 把它们移出索引（**工作区不受影响**）；
- 一旦发生，用 `git reset --soft HEAD~1` → `git reset -- <路径>` → 重新 `git add`/`commit` 修正；`--soft` 不改工作区，无损失；
- 判断是否踩中：`git show --stat --format='' HEAD | tail -1` 的文件数/行数远大于预期。

W2 实操事故：本应是纯文档的提交 `8e5426555` 实际含 **229 文件 / −50,034 行**（因为 W2a 的 225 个 `git rm` 已在索引里），已用上述方法修正为 4 文件纯文档提交。

**8. 删除区间必须做「括号净差」校验（W2 新增失败模式）。** 条目 1 防的是「按缩进配平找 `}`」的**脚本**；本条防的是**按行号区间删除时吃掉相邻语法单元**——两者不是一回事：

- **误删内容**（删错对象）：靠「区间内必须出现预期片段」的边界校验拦截——已有。
- **配平破坏**（删对了对象，但吃了相邻的 `}`）：边界校验**拦不住**，因为区间内容全部符合预期。

W2 实测事故：`MainActivity.kt` 批 B 删 `(563, 572)` 时，572 行是 `} else {`——删除把属于 `if` 的 `}` 一起吃掉了，而 `else` **体**的闭合 `}`（579 行）仍在，留下孤儿 `}`。后果不是普通的「未解析引用」，而是**类体提前闭合**：

```
L794:1 Syntax error: Expecting a top level declaration.
backStack / lifecycleScope / textSheetFlow / activeReadBookInputHandler … 大批「未解析」
```

这些符号明明在同文件同类内定义（`backStack` 在 451 行）。**症状特征是「整类成员互相引用全部失败」**——见到这个模式，先查花括号平衡，不要去逐个改那些引用。

**强制做法（三条，缺一不可）**：

1. 纯删除：该区间 `{` 数必须 == `}` 数，否则拒绝该块；
2. 替换：被替换区间的括号净差 == 替换文本的括号净差；
3. **写入前**再校验整文件净差 == 0，不为 0 则放弃写入并报错。

诊断命令（任何时候都该能跑）：

```bash
python3 -c "s=open('<file>',encoding='utf-8').read(); print('diff', s.count('{')-s.count('}'))"
```

定点定位法：恢复上一次已知良好备份 → 逐个重放脚本 → 找净差首次变负的那一步。
W2 事故修复实录见 `VERIFICATION.md` §7.5。
