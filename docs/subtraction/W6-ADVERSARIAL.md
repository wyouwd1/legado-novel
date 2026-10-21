# W6 · 对抗性测试与修复（2026-10-21）

> 分支 `subtract/novel-only` ｜ 基线 HEAD `ef36b4b2f` ｜ 目标：只保留小说阅读/书源，且留下功能可用。
> 本波不是“补覆盖率”，而是按攻击者思路找**会让读者打不开书、排错版、丢数据、卡死或越界写文件**的缺陷；
> 每个确认缺陷先写失败测试，再修复，测试永久保留。

## 0. 攻击面与验收

| # | 面 | 攻击输入 | 期望 | 所有者 |
|---|---|---|---|---|
| A1 | 分页/断行 | 空章、单字、超长段、零/负/NaN 尺寸、1px 视口、行高大于页高 | 不崩溃、不死循环、页边界单调、文本不丢 | attacker-reader |
| A2 | 章节源解析 | 未闭合/畸形 `<img>`/`<usehtml>`、重复/空 src、代理对/组合字符、`[newpage]` 变体 | 不抛异常、位置空间自洽、正文不丢 | attacker-reader |
| A3 | 标题/正文过滤 | 空标题、非法分隔正则、CRLF、用户文本里出现占位标记 | 标题可读、正文折叠/还原不丢内容 | attacker-reader |
| A4 | 角色标记纯文本工具 | 未配对/嵌套引号、标记在首尾、任意下标映射 | 偏移映射单调且不越界 | attacker-reader |
| B1 | 缓存队列 | 负下标、`Int.MAX_VALUE`、倒序区间、重复/重叠入队、先删后到、重复回调 | 有界终止、计数不双计、不丢待下载章 | attacker-cache |
| B2 | 下载状态机 | 并发 enqueue/next/remove、暂停/恢复、失败重试、重复成功/失败 | 无异常、状态自洽、失败章可重试 | attacker-cache |
| B3 | 网络失败语义 | 超时/断连/空响应/取消 | 不伪装成功、可重试、不无限占用 running | attacker-cache |
| C1 | ZIP 解包 | `../`、绝对路径、同前缀兄弟目录、反斜杠、损坏/截断包、重复条目 | 只写目标目录；损坏包明确失败 | attacker-storage |
| C2 | 备份/恢复 | 缺文件、空 JSON、非法类型、旧域字段 | 忽略未知域、不崩、不误删现有数据 | attacker-storage |
| C3 | Room 131→132 | 旧表/旧书 type 位 | 40 张旧表删除、audio/image 书保留数据并移出书架 | attacker-storage |

## 1. 验证命令

```bash
# 串行 Gradle（防止多个 agent 同时抢 daemon）
/tmp/legado-gradle.sh :app:testAppDebugUnitTest --tests 'io.legado.app.feature.reader.*'
/tmp/legado-gradle.sh :app:testAppDebugUnitTest --tests 'io.legado.app.model.cache.*'
/tmp/legado-gradle.sh :app:testAppDebugUnitTest --tests 'io.legado.app.help.storage.*' --tests 'io.legado.app.utils.compress.*'
```

最终门禁（主控执行，不并发）：

```bash
./gradlew testAppDebugUnitTest verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache --console=plain
```

## 2. 纪律

- 只改自己认领的包和对应测试；不碰 `docs/subtraction/**` 以外的他人文件。
- 不删除既有测试来“修绿”；不放宽断言；不引入 `Thread.sleep` 作为并发测试的同步手段。
- 修复必须保持旧阅读器语义；无法确定旧行为时先记录证据，不静默改。
- 每个发现写：输入 → 实际 → 期望 → 测试名 → 修复点。报告写到 `docs/subtraction/team/W6-<area>.md`。
- 完成后报告：新增/修改文件、测试命令与结果、未验证风险。不要提交 git（主控统一复核提交）。

## 3. 状态

| 子波 | 状态 | 报告 |
|---|---|---|
| W6-A 阅读核心 | 🟢 完成（42 测试 / 8 缺陷） | `team/W6-A-READER.md` |
| W6-B 缓存并发 | 🟢 完成（28 测试 / 10 缺陷） | `team/W6-B-CACHE.md` |
| W6-C 存储/迁移 | 🟢 完成（30 测试 / 6 缺陷） | `team/W6-C-STORAGE.md` |
| W6-D 主控复核/门禁 | 🟢 完成（5 测试 + 门禁） | 本文 §4 |

## 4. 汇总（待主控填写）

- 新增永久回归测试：TBD
- 修复缺陷：TBD
- 全量单测：TBD
- 架构门/打包：TBD

## 4. 汇总（2026-10-21 完成）

### 4.1 修复缺陷（24 项，均有先失败/红跑证据）

| 子波 | 缺陷 | 严重度 | 证据 |
|---|---|---|---|
| W6-A | 断行器 NaN/Inf/负字宽写进行宽 | 高 | 7 个断行用例；修复后行宽有限非负 |
| W6-A | 分页器图片固有尺寸 NaN/Inf 污染整页 y/后续正文 | 高 | `ReaderPaginatorAdversarialTest` |
| W6-A | 分页器段落字宽/行内图 NaN 传播到元素 bounds | 高 | 同上 |
| W6-A | HTML resolver 空结果/全空 item 吞整块正文 | 高 | `ReaderChapterBlockMeasurerAdversarialTest` |
| W6-A | 非法图片/HTML span 尺寸产生 NaN 行内图 | 中 | 同上 |
| W6-A | `CastMarkers.storedOf` 负下标/`Int.MAX_VALUE` 越界与溢出 | 中 | `CastMarkersAdversarialTest` |
| W6-A | 正文含 `〔图片1〕` 字面量时折叠/还原插错图片 | 中 | `ReaderChapterSourceAdversarialTest` |
| W6-B | 队列负下标/倒序/`Int.MAX_VALUE` 回绕与 `waitingCount` 死循环 | 高 | 基线红跑 10 分钟不返回 |
| W6-B | `prioritize` 未入队章被实际调度 | 中 | 随机对拍 step=7 |
| W6-B | 队列并发 CME/NPE | 高 | 6 线程 ×2500 次 |
| W6-B | 重复 `markSuccess` 膨胀、removeBook 后迟到回调复活 | 中 | `CacheDownloadStateStoreAdversarialTest` |
| W6-B | 部分暂停 `isRunning` 误报、负 waitingCount | 中 | 同上 |
| W6-B | 准入队列 `maxActiveBooks<=0` 永久不推进 | 中 | `CacheDownloadAdmissionQueueAdversarialTest` |
| W6-B* | `CacheBookModel` 超大 Range 在入队前 `(start..end).toSet()` OOM | 高 | `CacheBookModelFailureTest.hugeRangeIsRejectedBeforeExpandingTheRange` |
| W6-C | ZIP 同前缀兄弟目录越界写 | **高（安全）** | `ZipUtilsSecurityTest` 修复前 4 失败 |
| W6-C | 非 ZIP/截断 ZIP/空条目名/反斜杠静默或错误处理 | 中 | 同上 |
| W6-C | `Migration_131_132` SQL 位或写成逻辑 `or`，type 整列变 0/1 | **高（数据语义）** | `expected:<1032> but was:<1>` |
| W6-C | `normalizeConfigMap` null→`"null"`、非字符串密码 | 中 | `NormalizeConfigMapTest` |
| W6-C | BackupConfig 一个坏字段丢弃整张忽略配置 | 中 | `BackupConfigParsingTest` |
| W6-C | 旧备份 audio/image 书恢复回已裁撤域 | 中 | `BookRestorePlannerTest` |
| W6-D | gzip 截断/空体未锁定 IO 失败契约 | 中 | `HttpFailureHandlingTest` |

> W6-B* 由主控在收口时补修：模型层先于队列展开 Range，故在 `CacheDownloadModels` 增加共享上限和
> `sanitizedForCache()`，`CacheBookModel.addRequest` 在展开前拒绝非法选择。

### 4.2 新增永久测试（105 项）

- W6-A：42（5 个 `*AdversarialTest`）
- W6-B：28（7 个文件；含 1 项主控补的模型超大 Range）
- W6-C：30（ZIP 12、配置/恢复 16、迁移 2；另含旧域隔离 2）
- W6-D：5（`HttpFailureHandlingTest`）

### 4.3 最终门禁

| 项 | 结果 |
|---|---|
| `:app:testAppDebugUnitTest` | **1,314 / 0 failures / 0 errors / 0 skipped**（242 XML） |
| `verifyConfigArchitecture` | 通过（20 键/68、1 键/0、8 键/20；无缺失键） |
| `:app:assembleAppDebug` | 通过；APK arm64 **62,308 KB**、armv7 **61,860 KB**、universal **68,724 KB** |
| `git diff --check` | 0 |
| 阅读包专项 | 79 类 / 544 项，0 失败 |
| 缓存专项 | 66 项（含既有）0 失败 |
| 存储专项 | 49 项 + 迁移 2 项，0 失败 |

### 4.4 仍未验证

- 真实设备渲染/命中/选区、缓存下载与网络失败、备份恢复完整链路、Room 131→132 升级安装均未在真机执行。
- `MigrationTestHelper` 未跑（room-testing 仅 androidTest）；当前用 schema 静态对比 + Robolectric framework SQLite 直接执行生产 `onPostMigrate` 替代。
- 旧 `BookSource` 的 audio/image 源类型未归一化；`Restore` 完整链路仍只覆盖纯函数/planner。
- `lintAppDebug` 全量与 `assembleAppNoR8` 的既有超时/daemon 问题未解决。
