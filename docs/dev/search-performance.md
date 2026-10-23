# 多书源搜索性能

> 状态：2026-10-22 已实现并完成 JVM 调度级验证。真机网络与 WebView 场景仍需后续 macrobenchmark。

## 目标与范围

优化全局搜书（`SearchBooksUseCase`）在启用几十到几百个书源时的完成时间，重点处理单个慢书源拖住整个搜索的问题；不改变书源规则、匹配排序和分页语义。

## 瓶颈

1. 书源解析是 N+1 Room 查询：先查 `book_sources_part`，再为每个书源执行一次 `getBookSource()`。200 个书源 = 201 次读取。
2. 默认并发数复用下载设置 `threadCount`（默认 16），在大量书源下会排队。
3. 每个书源有 60 s 硬超时，且 `Finished` 必须等所有书源结束。一个卡死书源会把整个搜索拖到约 60 s。
4. `SearchViewModel` 每个 source `Progress` 都重建、排序全部结果并扫描书架；失败/空结果也会触发重建。

## 选定方案

| 改动 | 说明 |
|------|------|
| 专用搜索并发 | `DEFAULT_SEARCH_CONCURRENCY = 32`，硬上限 `MAX_SEARCH_CONCURRENCY = 48`，不再跟随下载线程数 |
| 20 s 全局截止 | active-time 计时；暂停/后台期间不消耗预算，超时后取消未完成书源，发出 `Finished(isPartial=true, pendingSources=...)` |
| 单次书源查询 | 新增 `BookSourceDao.getSearchSources*()`，按全部/显式/分组直接取完整 `BookSource` 行 |
| 结果流降噪 | 零增量 `Progress` 不再重建列表；分组布局只在分组模式计算 `groupBy`；重复 `bookUrl` 用 O(1) 桶索引 |
| 书架 O(1) 解析 | `BookShelfLookup` 预构建精确 URL 集与 name+author 集，替代每本书两次线性扫描 |

UI 通过 `search_partial_timeout` 明确提示“部分结果”，结果列表底部也会显示 `search_partial_footer`，不会把超时搜索伪装成完整结果或误显示“无结果”；超时后 `hasMore=false`，避免自动翻页立刻发起下一轮长搜索。

## 验证结果

JVM 虚拟时间调度测试（`SearchBooksUseCaseSchedulingTest`，固定 fake，不受网络/设备噪声影响）：

| 场景 | 优化前 | 优化后 | 变化 |
|------|--------|--------|------|
| 60 书源，2 个 59 s 慢源 | p50/p95 59,600 ms | p50/p95 20,000 ms | **-66.4%** |
| 120 书源健康源，16 并发 | 1,650 ms | — | 基线 |
| 120 书源健康源，32 并发 | — | 950 ms | **-42.4%** |
| 首个结果 | 100 ms | 100 ms | 不变 |
| 60 书源结果完整性 | 60/60 | 58/58 已完成源，2 个明确标记 pending | 无静默丢失 |
| 全部书源 Room 读取（60 源） | 61 次（1+N，静态计数） | 1 次（Room QueryCallback） | **-98.4%** |

命令：

```bash
scripts/build-217.sh :app:testAppDebugUnitTest \
  --tests 'io.legado.app.domain.usecase.SearchBooksUseCaseSchedulingTest' \
  --tests 'io.legado.app.data.repository.SearchRepositorySearchSourcesTest' \
  --tests 'io.legado.app.domain.usecase.ResolveBookShelfStateUseCaseTest' \
  --tests 'io.legado.app.ui.book.search.SearchResultOrderingTest' \
  --tests 'io.legado.app.domain.usecase.SearchBooksUseCaseTest' \
  --no-configuration-cache
```

基线由同一测试类中的 `baseline scheduler reproduces the sixty second tail` 用例复现：16 并发、120 s 非绑定截止、60 s 单源超时；5 次确定性采样。

## 取舍与未验证风险

- **慢源覆盖**：20 s 后仍未返回的书源会被取消；这是用覆盖换尾延迟。UI 会提示 pending 数量；若产品要求最终完整覆盖，需要后续做有界 drain 或两阶段后台继续。
- **WebView 书源**：32 并发可能同时创建多个 WebView。默认数据中未发现 `webView` 书源；真机仍需用 WebView 书源做压力测试，必要时加独立 `Semaphore(2..4)`。
- **暂停语义**：全局 20 s 只统计 resumed 时间，暂停/后台期间不消耗预算；已在途的单源 25 s 超时仍是墙钟，若暂停超过 25 s，个别在途源可能先失败。
- **单源超时**：从 60 s 降为 25 s，作为全局 20 s 截止之外的兜底；正常路径不会触发。
- **分组作用域**：分组搜索仍是每组一次查询（G 次），不是字面意义的单条 SQL；全部/显式书源为 1 次。
- **显式禁用书源**：现在返回空并由上层报“可搜索书源为空”；旧实现会尝试搜索显式选中的禁用书源。这是有意收紧，避免把失效 scope 扩大成全量搜索。
- **遗留回退**：未知显式 URL、以及“仅含禁用书源”的分组仍回退到全部可搜索书源，与旧逻辑一致；如果产品希望改为报错，需要单独决策。
- **真机未验证**：JVM 虚拟时间证明调度策略，不证明 OkHttp、Rhino、Room 和 Compose 在具体设备上的绝对耗时。
- **兄弟路径**：`ChangeSourceSearchUseCase` 仍是 60 s/下载线程数策略，未纳入本次改动。

## 最终门禁

- `:app:testAppDebugUnitTest`：248 个测试类、1,349 个测试，0 failures / 0 errors。
- `:app:lintAppDebug --offline`：0 errors；125 条既有 warning 与 `lint-baseline.xml` 一致，其中 24 errors 被既有基线条目过滤。
- `:app:assembleAppDebug`、`verifyConfigArchitecture`：通过。
- 独立 Reviewer：**ACCEPT WITH CAVEATS**；B1（错误 scope 回退）、B2（基线/查询计数证据）、B3（取消/合并/分页/排序测试）已关闭。
- 新增 `search_partial_footer`，部分结果不再与空结果文案冲突；`SearchBooksUseCaseSchedulingTest` 增加到 12 个用例（含同 URL 去重/别名合并、暂停/恢复、单源超时隔离）。
- 尚未做真机 macrobenchmark；active-time 截止已由虚拟时间暂停 30 s 的回归用例覆盖，但真机 WebView/Rhino 取消语义仍需验证。
