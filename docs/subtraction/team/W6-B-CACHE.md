# W6-B 缓存对抗性测试报告（2026-10-21）

分支：`subtract/novel-only`（未切分支、未 commit）
范围：`app/src/main/java/io/legado/app/model/cache/**`、对应测试；`CacheBookModel` 仅新增公开 API 测试。

## 结论

- 新增 27 个 JUnit4 回归测试（7 个文件，随机测试固定种子 `20261021`）。
- 修复 9 项可复现缺陷（5 类）：非法下标/`Int.MAX_VALUE` 回绕与死循环、`prioritize` 未入队章被调度、
  队列并发无锁导致的 `ConcurrentModificationException`/NPE、状态存储重复成功回调膨胀与迟到回调复活、
  准入队列 `maxActiveBooks<=0` 永久不推进；并修正部分暂停时 `isRunning` 误报。
- 作用域内最终结果：65 tests / 0 failed / BUILD SUCCESSFUL。

## 缺陷：输入 → 实际 → 期望 → 测试 → 修复点

| # | 输入 | 基线实际 | 期望 | 测试 | 修复点 |
|---|---|---|---|---|---|
| 1 | `Single(-1)`、`Range(-5,3)`、`Range(4,2)`、`Indices(-3,-1)` | `waitingCount()` 返回 9，`next` 可返回负下标 | 非法负下标/倒序 Range 不进入队列，计数为 0 | `CacheDownloadQueueAdversarialTest.negativeAndReversedSelectionsAreIgnored` | `CacheDownloadQueue.sanitized()`：拒绝负下标、倒序与超大 Range；`removeChapter` 拒绝非法下标 |
| 2 | `Range(Int.MAX_VALUE-2, Int.MAX_VALUE)`、`Range(0, Int.MAX_VALUE)` | `waitingCount()` 的 `i++` 越过 `Int.MAX_VALUE` 回绕为负，测试进程 10 分钟未返回、CPU 99%（红跑时强制终止）；`next` 越过 MAX 后会返回 `Int.MIN_VALUE` 附近的负章 | 有界终止并拒绝超大输入 | `nearIntMaxRangeIsRejectedBeforeItCanWrap`、`fullRangeToIntMaxTerminatesAndIsRejected`（均 `timeout=5000`） | 同上：`end <= 1_000_000` 防御上限，避免游标/计数溢出 |
| 3 | 空队列 `prioritize(99)` | 把 99 插入 `indices`，`waitingCount` 从 0 变 1，`next` 返回从未入队的 99；随机对拍第 7 步 `waitingCount` 期望 3 实际 4 | 未入队章不调度、不改变计数 | `prioritizeUnknownChapterDoesNotScheduleIt`、`CacheDownloadQueueRandomizedTest.thousandRandomOperationsMatchNaiveModel` | `prioritize` 增加 `isValidIndex` + `isWaiting` 前置判断 |
| 4 | 6 线程并发 `enqueue/next/remove/waitingCount/prioritize`（每线程 2500 次，`CountDownLatch` 同步） | 抛出 `ConcurrentModificationException`、`ArrayDeque` 元素被并发清空导致 `RangeCursor` NPE；最终队列状态不可信 | 无异常、最终 `waitingCount == waitingIndices.size`、不丢未删除章 | `CacheDownloadQueueConcurrencyTest.concurrentOperationsDoNotCorruptQueueOrLoseChapters` | 队列公开方法统一 `@Synchronized`（`enqueue/next/remove/clear/snapshot/waiting*/prioritize/hasLaunchable`） |
| 5 | `markSuccess("a",1)` 连续 3 次 | `successCount = 3` | 同一章重复成功回调只计 1 次 | `CacheDownloadStateStoreAdversarialTest.duplicateSuccessCallbacksCountAChapterOnlyOnce` | `CacheBookDownloadState.succeededIndices` + `markSuccess` 去重 |
| 6 | `removeBook("a")` 后迟到的 `markSuccess/markFailed/updateChapterProgress/clearFailure` | 回调重新创建 `books["a"]`（successCount=1），已移除书复活 | 运行期回调只更新仍被跟踪的书 | `lateCallbacksAfterRemoveBookDoNotResurrectState` | `CacheDownloadStateStore.updateExistingBook`：书不存在时 no-op；`markBookFailed` 仍允许显式创建失败状态 |
| 7 | 1 章 waiting + 1 章 paused | `isRunning=false`（`totalWaiting > totalPaused` 为 false） | 仍有可运行等待章时为 true | `partialPauseStillReportsRunningWhenAnotherChapterIsWaiting` | `recalculate()` 改为 `totalWaiting > 0 || totalRunning > 0` |
| 8 | `updateBookQueue(waitingCount=-5)` | `totalWaiting=-5` | 钳制为 0 | `negativeWaitingCountIsClampedToZero` | `updateBookQueue` 内 `coerceAtLeast(0)` |
| 9 | `CacheDownloadAdmissionQueue(maxActiveBooks=0/-1/Int.MIN_VALUE)` | `shouldQueue(empty)=true` 且 `pollReady` 永远返回 null（请求永久卡住） | 至少按 1 本推进 | `CacheDownloadAdmissionQueueAdversarialTest.nonPositiveMaxActiveBooksStillAdmitsQueuedRequests` | 构造参数 `coerceAtLeast(1)` |

补充回归（基线已通过，防回归）：重复/重叠 enqueue 在一次待调度批次内不重复发章、重复 remove/remove 已发出章不复活、
`waitingCount/waitingIndices` 与 `next` 一致、失败章可重新入队、取消后不自动重入队、FIFO 对不存在项
`moveToHead/moveToTail` no-op、重复 ensure/remove、`drainAll` 后为空。

## 新增测试（27 个）

| 文件 | 数量 | 覆盖 |
|---|---:|---|
| `app/src/test/java/io/legado/app/model/cache/CacheDownloadQueueAdversarialTest.kt` | 8 | 负下标/倒序/Int.MAX、重复重叠 enqueue、remove 已发出、prioritize 未入队、失败重入队/取消、计数一致 |
| `.../CacheDownloadQueueRandomizedTest.kt` | 1 | 1000 次固定种子随机操作与朴素模型对拍 |
| `.../CacheDownloadQueueConcurrencyTest.kt` | 1 | 6 线程 ×2500 次并发 enqueue/next/remove/count/prioritize |
| `.../CacheDownloadStateStoreAdversarialTest.kt` | 10 | 重复成功/失败、成功失败交错、removeBook/clear/clearRuntimeState、failureMessage 生命周期、负计数、纯层失败重试 |
| `.../CacheDownloadAdmissionQueueAdversarialTest.kt` | 3 | maxActiveBooks<=0、drainAll、重复 add/remove |
| `.../ExplicitCacheBookFifoAdversarialTest.kt` | 3 | 不存在项 move、重复 ensure/remove、clear 重用 |
| `app/src/test/java/io/legado/app/model/CacheBookModelFailureTest.kt` | 1 | 公开 `removeDownload` 不自动重入队、显式请求可重新加入 |

已有 `CacheBookModelResumeTest` 的暂停/恢复用例保持通过，覆盖“暂停后恢复只恢复选中章”、预下载不覆盖暂停。

## 改动文件

生产代码（4）：

- `app/src/main/java/io/legado/app/model/cache/CacheDownloadQueue.kt`
  - 输入净化（负下标/倒序/超 1,000,000 的 Range）；
  - 全部公开操作 `@Synchronized`；
  - `prioritize` 只处理有效且等待中的章。
- `app/src/main/java/io/legado/app/model/cache/CacheDownloadStateStore.kt`
  - `updateExistingBook` 阻止迟到回调复活；
  - `markSuccess` 按章节去重 `successCount`；
  - `waitingCount` 下限钳制；`isRunning` 重算修正。
- `app/src/main/java/io/legado/app/model/cache/CacheDownloadModels.kt`
  - `CacheBookDownloadState.succeededIndices`（默认空集，向后兼容）。
- `app/src/main/java/io/legado/app/model/cache/CacheDownloadAdmissionQueue.kt`
  - `maxActiveBooks` 至少 1。

测试：上述 7 个新增文件。未删除/放宽任何既有测试，未改 `CacheBookModel.kt` 生产代码，未 commit。

## 命令与结果

```bash
# 1) 基线（改动前）
/tmp/legado-gradle.sh :app:testAppDebugUnitTest \
  --tests 'io.legado.app.model.cache.*' --tests 'io.legado.app.model.CacheBookModel*'
# BUILD SUCCESSFUL（既有 38 个用例）

# 2) 红：把 cache 生产文件临时还原到 HEAD 后跑新增用例（随后已恢复修复版）
# 17 tests completed, 10 failed；失败覆盖负下标、prioritize、并发、随机对拍、状态存储、准入队列
# 另外 nearIntMaxRangeIsRejectedBeforeItCanWrap 在基线 waitingCount 中不终止，测试进程被强制终止

# 3) 绿：修复后作用域全量
/tmp/legado-gradle.sh :app:testAppDebugUnitTest \
  --tests 'io.legado.app.model.cache.*' --tests 'io.legado.app.model.CacheBookModel*'
# 65 tests completed, 0 failed, BUILD SUCCESSFUL in 5s

# 4) 编译
/tmp/legado-gradle.sh :app:compileAppDebugUnitTestKotlin
# BUILD SUCCESSFUL
```

## 未验证风险

1. **真实网络失败未联网验证**：只验证了“`markFailed` → 重新 enqueue → 再 `markSuccess`/`clearFailure`”的纯状态迁移；
   DNS/超时/断连/空响应/取消在 `WebBook` 层的真实异常类型与重试次数未执行。未伪造网络通过。
2. **`CacheBookModel` 私有 `onError` 未直接触发**：未为测试放宽可见性；失败重入队用队列+状态存储同路径覆盖，
   暂停/恢复用现有公开 API 测试。`onPreError`/`onPostError` 与真实协程取消的竞态未覆盖。
3. **模型层超大 Range 仍在入队前展开**：`CacheBookModel.addRequest` 的 `selectionIndices()` 会先 `(start..end).toSet()`；
   即使缓存队列拒绝 `Int.MAX_VALUE`，从模型层传入超大 Range 仍可能 OOM。该文件不在本任务允许修改的生产范围。
4. **`CacheBook.pendingChapterCount()`** 对 Range 仍用 `end - start + 1`，超大边界可溢出；不在 cache 包范围。
5. **并发测试依赖 JVM 调度**：无确定性交错、无 TSAN/Android 真机；只证明“无共享可变结构直接并发访问”。
6. **`CacheDownloadAdmissionQueue` 自身未加锁**，仍依赖 `CacheBookService` 外部 `synchronized`；本报告只覆盖纯队列语义。
7. **1,000,000 上限是防御策略**：真实书目录超过 100 万章会被拒绝；当前仓库未见这种书。
8. Android Service/Compose UI 集成、下载通知与多书 FIFO 调度未在本次单测中启动。
