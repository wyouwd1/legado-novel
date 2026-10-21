# 减法交付说明（W1–W3）

> 分支：`subtract/novel-only` ｜ 生成：2026-10-21 ｜ 依据：`PLAN-CANDIDATES.md` 方案 A（加权 4.00/5）

## 1. 已完成范围

| 波次 | 提交 | 内容 | 净删除 |
|---|---|---|---|
| W1-1~W1-3 | `2318cfb84` / `df02effd7` / `7a14c1f8b` | RSS、漫画、AI/知识库/配音界面 | 约 -36.6k 行 |
| W2a | `dd61c2bfd` | 朗读/听书/TTS/cast/BGM/音频服务、阅读页接线、Manifest、路由 | **-53,373 行**（230 删除 + 59 修改） |
| W3a | `2db39755a` | 首页自定义模块、Home tab、3 tab 导航 | -7,423 行 |
| W3b | `ce91fb1b0` | 内置 Web 服务归档 `attic/webservice/`、assets/web 与 ktor 依赖移出 APK | -9.6k 行（含 58 assets） |
| W3c | `97fa73e8b` | 翻译设置/任务/缓存/阅读页入口/导出翻译 | -1,961 行 |
| 测试依赖 | `7437a6469` | 删除 ktor 后显式补 `kotlin-reflect` | +1 行 |

主源码从 W0 的 **1,752 文件 / 335,597 行** 收敛到 **1,398 文件 / 244,560 行**。

## 2. 结果（提交后验证）

- `:app:compileAppDebugKotlin`：**BUILD SUCCESSFUL**
- `testAppDebugUnitTest`：**1,255 / 0 失败 / 0 错误**（233 XML）
- `verifyConfigArchitecture`：**通过**（20 键/68、1 键/0、8 键/20，无缺失键）
- `assembleAppDebug`：**通过**；APK arm64 **60M**、armv7 **59M**、universal **66M**（W0 基线 71.7/71.2/78.2 MB）
- `git diff --check`：0
- 独立验证：`team/W2A-VERIFY.md`（PASS；反证 grep 无删除符号代码引用；保留项核对通过）

## 3. 保留的关键能力

书架/分组、书籍详情、全局搜书、书源管理/编辑/调试/校验/订阅导入、正文阅读/翻页/手势、目录与 TXT 目录规则、正文搜索、换源、缓存下载、本地导入（TXT/EPUB/UMD/压缩包）、阅读进度/记录、书签/高亮/标签规则、替换净化、阅读设置/主题、备份恢复（含 WebDAV）、文件关联/二维码/关于。

## 4. 明确未完成 / 风险

1. **W2b**：`ReaderElement.RoleCast/BgmScene`、`ReaderCastOptions`、`castRenderHash`、`CastMarkers` 等阅读核心死代码仍在（生产链路不可达，已侦察：`team/W2B-CORE-PLAN.md`）。不删除不影响行为，但不满足“代码零残留”的最终形态。
2. **W4**：Room `version = 131` 未升版；RSS/AI/TTS/cast/BGM/Homepage 旧 entity/DAO/表仍在数据库定义中。删除必须与 `version+1` + migration 同批，尚未执行。`BookType.audio/image` 类型位与 `books.type` 僵尸数据也在这一步清理。
3. **W5**：翻译/AI/TTS 专用字符串（4 语言）、`PreferKey.llm*`、部分 lint 基线过时条目未清；lint 全量复跑受 `lintAnalyzeAppDebug` 超时限制（最近一次仅 4 个 Manifest MissingClass，已修复）。
4. **未做真机手测**：阅读、换源、缓存、书源调试、备份恢复、升级安装均未在真机验证。
5. **兼容性**：旧 Nav3 保存栈中的朗读/音频 route 已删除，升级后首次恢复可能回主页；`BookSource.homepageModules` 字段保留读取但不展示；旧 `httpTTS.json` 备份忽略而不报错（待 W4 验证）。

## 5. 复现命令

```bash
export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
./gradlew :app:compileAppDebugKotlin
./gradlew testAppDebugUnitTest verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
git diff --check
```
