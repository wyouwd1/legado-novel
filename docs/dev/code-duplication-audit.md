# 代码重复审计与治理方案

- 日期：2026-10-23
- 基线：`92fea7ddd`（任务开始时工作区干净）
- 目标 commit：`80b5688bcf81eb968aa3c8ef877cf7b78b4f5cfd`
- 团队记录：`.planning/2026-10-23-code-duplication-audit/team/`

## 结论

1. **80b5688 不是重复的制造者。** 它只是把同一个 repo URL 的 4 个硬编码点做了一次 find-replace：`AboutScreen.kt:151/157/178`、`MiuixAboutScreen.kt:398`。重复结构在 `f0bbd4697` 新增 Miuix 关于页时已经形成，更早的 `58893de5c` 把 `R.string.github_url` 换成了字面量。
2. **About 的重复是“同一内容模型 + 两套 UI DSL”，不是整套页面逻辑复制。** `AboutUiState`/`AboutIntent`/`AboutViewModel`/`AboutOverlays`/`AboutSheets` 已经共享；重复集中在 8 个条目的标题 key、动作映射、3 个 markdown 文件名和 repo URL。
3. **全库精确克隆率审计基线为 3.10% 行 / 3.13% token（jscpd weak，420 克隆）；剔除 import/package 样板后的净口径为 2.74% 行 / 2.53% token（356 克隆）。本轮修复后为 3.07% / 2.72%（418 / 354 克隆）。** 不是“到处都在复制”，但重复高度集中，Top 3 包承担了大部分绝对行数。
4. **About 是唯一一对整页双实现（1/52 个 `*Screen.kt`），但不是孤例模式。** 另有 `UpdateDialog`/`UpdateSheet`、`MoreConfigSheet`/`ReadConfigScreen` 两对同内容双 UI；42/157 个 `widget/components` 文件有 Material/Miuix 分支。根因是 About 绕过了项目已有的 `ClickableSettingItem` adapter（28 文件/110 处使用）。
5. **首选方案：A+B-lite+D 分阶段。** Phase 1 B-lite 已落地并通过全量质量门禁：About 单一内容模型、Miuix 死导航已删除；B1 的 `ClickableSettingItem` adapter 扩展作为后续可选 PR；D 用 jscpd baseline 冻结存量、只拦新增，仍待接入。T6 独立复核建议不采用 T5 完整 B 的 `AboutIntent` 资源化/`AboutDocument`/`RendererProfile` 额外抽象。C（批量热点）和 E（统一 Material/Miuix 设计系统）本轮不做。

## 1. 目标 diff 的重复点

`80b5688` 的 diff anchor 经路径 SHA-256 匹配为 `app/src/main/java/io/legado/app/ui/about/AboutScreen.kt`。

| 位置 | 重复内容 | 说明 |
|---|---|---|
| `AboutScreen.kt:151` | `FilledTonalIconButton` + `OpenUrl(repo)` + web icon | 与下一行按钮仅 icon/contentDescription 不同 |
| `AboutScreen.kt:157` | `FilledTonalIconButton` + 同一 `OpenUrl(repo)` + GitHub icon | 相邻两个按钮 `onClick` 完全相同 |
| `AboutScreen.kt:178` | “贡献者” `OpenUrl(repo)` | 同一个 URL 在单文件第 3 次出现 |
| `MiuixAboutScreen.kt:398` | “贡献者” `OpenUrl(repo)` | Material/Miuix 双份内容模型 |
| `non_translat.xml:8` | `github_url` 资源定义 | 0 引用；`contributors_url`/`git_hub` 也 0 引用 |
| `AppUpdateGitHub.kt:25` | `repoPath = "wyouwd1/legado-novel"` | 跨层第 5 种 repo 表示 |

历史证据：`ccaf8973f` 的旧 View 关于页曾分别使用 `R.string.github_url`、`R.string.legado_url`、`R.string.contributors_url`；`58893de5c` 的 Compose 重写删除了资源间接层，`f0bbd4697` 新增 Miuix 页时把主页占位 URL 和贡献者链接都换成了 repo 根路径，导致两个按钮同源、贡献者语义丢失。80b5688 只是把这个已经重复的结构再改了一次。

## 2. 全库精确克隆扫描

工具：jscpd 5.1.2，Kotlin，`min-tokens 50` / `min-lines 10`，排除 `build/lib/attic/modules/web/generated`。

| 口径 | 文件数 | 行数 | 克隆数 | 重复行 | 行重复率 | token 重复率 |
|---|---:|---:|---:|---:|---:|---:|
| mild（含注释/许可证头） | 1,164 | 223,518 | 433 | 7,083 | 3.17% | 3.40% |
| weak（`--skip-comments`，主口径） | 1,156 | 223,413 | 420 | 6,918 | 3.10% | 3.13% |
| weak 净口径（剔除 64 个 import/package 克隆） | 1,156 | 223,413 | 356 | 6,127 | 2.74% | 2.53% |

净口径中 206/356（58%）是同文件自克隆（重载、对称分支），150/356（42%）才是跨文件复制。原始 mild 的 Top 8 全部是 `modules/rhino` 的 GPL 许可证头；Rhino 是 vendored 第三方，不治理。

## 3. 同类问题热点

| 优先级 | 区域 | 证据 | 性质 |
|---|---|---|---|
| P1 | `ui/association` | 858 行 / 45 克隆 / 12 文件；`ImportDictRuleDialog.kt:54-126 ↔ ImportTxtTocRuleDialog.kt:57-129` 73 行 / 333 token，同一段还复制到 `ImportThemeDialog`；4 个 ViewModel 也互抄 | View/XML 遗留模板族，可治理但属高风险外部导入流程 |
| P2 | `data/dao` | 759 行 / 84 克隆；`BookDao.kt` 700/1,205 行（58.09%）、80 克隆、31 份 23 字段 SELECT、49 处 `PUBLIC_BOOK_FILTER` | Room 编译期 SQL 的结构性重复；只适合抽投影常量，不适合抽 Kotlin 函数 |
| P3 | `ui/widget/components` | 690 行 / 46 克隆；`AppTextField.kt` 252/430 行（58.60%）、20 克隆；`LoadMoreFooter.kt` 166 行 | Compose 公开重载/默认参数样板；可抽 private core，保留 public API |
| P4 | `ui/widget/text` | `ScrollTextView.kt` 243/272 行（89.34%）与 `ScrollMultiAutoCompleteTextView.kt` 243/275 行（88.36%）互为 184 行克隆 | 两个自定义 TextView 同一滚动实现换基类；ROI 最高但触控回归风险最高 |
| P5 | `ui/book/read/sheet` | 511 行 / 37 克隆 / 9 文件；`ReaderBookSheet` 132 行、`TypographyTabs` 87 行 | 阅读器高频交互区，单条较小、回归面大 |
| 其他 | About / Update / ReadConfig | About 两页 803 行；`UpdateDialog.kt:22-98` vs `AboutSheets.kt:64-175`；`MoreConfigSheet` vs `ReadConfigScreen` 33 个同名标题、30 个同字段 | 3 对同内容双 UI，合计约 2,000 行承载代码 |

## 4. 根因

| ID | 根因 | 证据 | 判断 |
|---|---|---|---|
| R1 | Compose 重写丢失资源间接层 | 旧 View 用 `R.string.github_url`；`58893de5c` 后变成字面量；`github_url` 0 引用 | 可治理 |
| R2 | 页面绕过 `ClickableSettingItem` adapter + 无共享内容模型 | `ClickableSettingItem.kt:17-54` 已在 28 文件/110 处使用；About 直接写 7 个 `SettingItem` + 8 个 `ArrowPreference` | 可治理，About 的主因 |
| R3 | View/XML 遗留复制 | `Import*Dialog` 73 行克隆、`Scroll*View` 184 行克隆 | 混合；部分随迁移消失 |
| R4 | Room 编译期 SQL 结构性重复 | `BookDao` 31 份投影、49 处 filter | 结构性为主 |
| R5 | 迁移期 View/Compose 双栈 | `UpdateDialog` vs `UpdateSheet`，两个 `UpdateMode` enum，`checkVariant` 逐字重复 | 迁移期必要；映射可先治理 |
| R6 | 缺少重复率 baseline/CI 门禁 | `.github/workflows/verify.yml` 无 jscpd；420 个存量克隆不在 CI 可见 | 流程缺口 |
| R7 | repo identity 无跨层单一事实源 | UI 4 处 + `repoPath` + resource + CI URL + docs 22 处；`cf105e4ed` 与 `80b5688` 连续两天补漏 | 跨层根因 |
| R8 | 死代码/零引用资源放大重复感 | Miuix `LocalNavigator.push("License")` 无 consumer（本轮已删除 `LocalNavigator`/`MiuixNavigator`）；`git_hub`/`contributors_url`/`contributors_summary` 0 引用；`AppSearchBar` 0 调用 | 低风险清理 |

## 5. 候选方案与评分

权重：重复削减 30%、回归风险控制 25%、实施成本 20%、可验证性 15%、架构一致性 10%；每项 1–5 分，风险/成本分数越高代表越好。

| 方案 | 重复削减 | 风险控制 | 成本 | 可验证性 | 架构一致性 | 加权 | 排名 |
|---|---:|---:|---:|---:|---:|---:|---:|
| **A+B1+D 分阶段** | 4 | 4 | 3 | 5 | 5 | **4.05** | 1 |
| B1（内容模型 + adapter） | 3 | 4 | 3 | 4 | 5 | 3.60 | 2 |
| D（baseline + CI） | 1 | 5 | 4 | 5 | 5 | 3.60 | 3 |
| B0（仅内容模型，POC） | 3 | 5 | 4 | 5 | 4 | 3.50 | 4 |
| A（仅常量） | 1 | 5 | 5 | 4 | 3 | 3.45 | 5 |
| C（批量热点） | 5 | 1 | 1 | 3 | 2 | 2.60 | 6 |
| E（统一设计系统） | 2 | 1 | 1 | 2 | 2 | 1.55 | 7 |

T6 独立复核后的修正：**A+B-lite+D ≈ 4.15**（B-lite 已由 POC 实测，省去 adapter 的额外风险/成本），B-lite 单独约 3.9；B1 仍是目标架构但不应阻塞 Phase 1。

- **不选 C**：收益最大但跨 View/Compose、触控、阅读器、Room 多个边界，2–4 周，违反“一个 PR 一个主要边界变化”。
- **不选 E**：整页复制只有 About 一对；42/157 组件分支是必要 adapter，统一设计系统会拆掉现有隔离层。
- **不选 A 单独**：只改 URL 不消除 16 个条目声明。
- **B0 是 Phase 1 可立即交付的基线**；B1（adapter 扩展）是目标架构，独立 PR。

## 6. 首选方案：Phase 1 About 垂直切片

范围：

1. 新增 `ui/about/AboutCatalog.kt`：`AboutSection`/`AboutItem`/`AboutAction`，8 个 canonical entries（check update、contributors、privacy、license、disclaimer、crash logs、save log、heap dump）+ 3 个 header actions（homepage、GitHub、check update）。
2. `AboutScreen.kt` / `MiuixAboutScreen.kt` 只保留渲染 shell，循环消费同一模型；Miuix 三张重复 Card 收敛为本地 `AboutSectionCard`。
3. 新增 `help/about/LegalDocuments.kt`，About 与 Onboarding 共用 `privacyPolicy.md`/`LICENSE.md`/`disclaimer.md`。
4. `AboutCatalogTest` 覆盖条目顺序、动作、URL resource、markdown 文件名、图标归属。
5. 行为保持不变：Material 3 个 header 按钮 + 7 行；Miuix 首卡 2 行 + 2 张 3 行卡；contributors 的 “GitHub” 尾巴保留；当前三个链接仍指向 repo 根（`legado_url`/`contributors_url` 的语义修复作为独立产品决策/后续 commit）。

## 7. 修复与验证结果

改动已应用到主工作区（基于 `92fea7ddd`）；验证时保留 `/tmp/legado-dup-poc` 作为对照快照。

| 指标 | Before | After | 证据 |
|---|---:|---:|---|
| About 两文件 repo URL 字面量 | 4 | **0** | `rg -F` |
| About 条目逐条声明 | 16（8×2 页） | **8 canonical entries** | `rg 'SettingItem\\(|ArrowPreference\\('` + 单测 |
| About 两文件 markdown 文件名引用 | 7（含 1 条死路径） | **0**；Onboarding 剩余 3 处已收敛到 `LegalDocuments` | `rg` |
| jscpd `ui/about`（50/10） | 2 克隆 / 57 行 / 3.70% | **0 克隆 / 0 行** | jscpd JSON |
| jscpd `ui/about`（30/5） | 9 克隆 / 95 行 / 6.16% | **7 克隆 / 38 行 / 2.43%**（剩余为 import/签名/AboutSheets/ViewModel 既有） | jscpd JSON |
| jscpd 全库 weak（50/10） | 420 克隆 / 6,918 行 / 3.0965% | **418 克隆 / 6,861 行 / 3.0708%** | `/tmp/jscpd-full-after-fix` |
| jscpd 全库 net | 356 克隆 / 6,127 行 / 2.7425% | **354 克隆 / 6,070 行 / 2.7168%** | `analyze.py` |
| `AboutCatalogTest` | 无 | **6 tests / 0 failures** | 217 XML（最终快照） |
| 全量单测 | — | **1,358 tests / 0 failures / 0 errors**（250 个 XML） | 217 `testAppDebugUnitTest` |
| `:app:lintAppDebug` | — | **任务成功：0 errors / 127 warnings / 8 hints**（另有 24 errors 由 `lint-baseline.xml` 过滤） | 217 lint 报告 |
| `:app:verifyConfigArchitecture` | — | **BUILD SUCCESSFUL** | 217 Gradle 输出 |
| `:app:assembleAppDebug` | — | **BUILD SUCCESSFUL**；arm64 APK 57 MB | 217 `app/build/outputs/apk/app/debug/legado-novel-3.26.19-arm64-v8a.apk` |

最终验证命令：

```bash
scripts/build-217.sh testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
```

`BUILD SUCCESSFUL in 5m 58s`。首次完整运行曾在 `:app:packageAppDebug` 抛 `IncrementalSplitterRunnable` 失败；单独重跑 `:app:packageAppDebug` 成功，随后完整复跑 `assembleAppDebug` 也成功，未复现，按 217 打包环境瞬时错误记录。最终快照追加 header title/icon 测试后，`testAppDebugUnitTest` 再次 `BUILD SUCCESSFUL in 3m 37s`，1,358 tests / 0 failures。

生产代码净增约 32 行（主工作区口径）：AboutCatalog 169 + LegalDocuments 12 + Onboarding 1 - 两页缩减 150 = +32（Miuix 死导航删除又减少 17 行）；测试新增 88 行。T6 原口径的 +49 是死代码清理前数字；应以“重复维护点”而非 LOC 作为验收指标。

## 8. 后续路线图

| 批次 | 目标 | 前置条件 |
|---|---|---|
| Phase 1b（可选） | B1：扩展 `ClickableSettingItem` 的 Miuix 分支（trailing content），About 两页改用 adapter | B-lite 合入；两引擎手工 parity；T6 建议不阻塞 Phase 1 |
| Phase 1c | A：`contributors_url` 指向当前 fork contributors 页；`legado_url` 主页语义恢复；repo identity 跨层校验 | 产品确认链接语义 |
| Phase 1d | D：jscpd baseline + `--fail-on-new-clones`，先 report-only 再阻塞 | clean tree exit 0；注入重复 exit 1 |
| Batch 1 | 规则页 `TxtRule`/`DictRule`/`HighlightTagRule` 抽 `RuleListScaffold` | D baseline 已冻结 |
| Batch 2 | `ScrollTextView`/`ScrollMultiAutoCompleteTextView` 抽滚动 delegate | 触控行为基线/真机 |
| Batch 3 | `Import*Dialog`/ViewModel 抽 `BaseImportDialog`/`BaseImportViewModel`，或决定直接 Compose 迁移 | 导入流程手工基线 |
| Batch 4 | `ReadConfigScreen`/`MoreConfigSheet` 抽 `ReadSettingDescriptor` 或一致性测试 | 字段一致性测试 |
| Batch 5 | `ReaderBookSheet`/`TypographyTabs` 局部去重 | 阅读器行为基线 |
| Batch 6 | `BookDao` 抽 `BOOK_SHELF_SELECT` 常量（先 3 个查询 POC） | Room 测试/217 构建 |
| Batch 7 | Settings/Theme 全量 round-trip 测试，再考虑字段描述符/生成器 | 不合并两套模型 |

不建议：`modules/rhino`（vendored）、`DatabaseMigrations`（历史 SQL 必须稳定）、`ui/theme/colorScheme`（声明式、收益低）、`AppTextField` public overload 合并（API 兼容风险，只抽 private core）。

## 9. 未验证风险

- 未在真机/模拟器上验证 Material 与 Miuix 两套渲染、点击、返回、弹层、深色/浅色/blur；本轮验证到编译、单测、lint、架构检查和 APK 打包，不等同于 UI 行为验收。
- Miuix 缺主页/GitHub 图标、缺 `about_description` 是设计简化还是遗漏，未获产品确认；本轮只删除无 consumer 的死导航，没有静默改 UI。
- 当前三个链接均指向 repo 根；旧 View 曾使用 `legado_url`/`contributors_url` 三种语义。是否恢复需产品确认；建议单独 commit，避免去重改动混入行为变更。
- `MiuixNavigator`/`LocalNavigator` 已删除（全库 0 引用）；删除后仅保留 `LocalIsWideScreen` 的 CompositionLocal，`AboutCatalogTest` 6/0、全量单测 1,358/0 通过。
- jscpd baseline 门禁已在临时样例验证 clean→exit 0、注入 15 行重复→exit 1、删除→exit 0、文件改名不误报；尚未接入 `.github/workflows/verify.yml`，D 仍待落地。
- T6 最终裁决为 **Approve**：3 项 required changes（header action 非空类型、trailingTextRes 字段、action 映射断言）已关闭；本轮又补 header title/icon 断言，`AboutCatalogTest` 为 6 tests / 0 failures。
- 全库 3.07%/2.72% 是当前 jscpd 口径；语义重复（About 类）不在精确克隆统计内，不能反过来用 3% 证明“没有重复”。

## 10. 团队产物

- `.planning/2026-10-23-code-duplication-audit/team/STATUS.md`
- `10-target-analysis.md`（T1）
- `20-clone-scan.md`（T2，含 `data/` 原始 JSON/CSV）
- `21-ui-semantic-audit.md`（T3）
- `22-data-domain-audit.md`（T4）
- `30-root-cause-options.md`（T5）
- `50-verification.md`（T6，历史独立复核）
- `60-final-validation.md`（Lead，修复后全量验证）
