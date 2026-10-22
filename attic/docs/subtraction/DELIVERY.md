# 减法交付说明（W1–W7，W5 剩余项）

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

主源码从 W0 的 **1,752 文件 / 335,597 行** 收敛到约 **1,300 文件 / 230,000 行**（W3 后 1,398 / 244,560；W4 删除 110 文件）。

## 2. 结果（提交后验证）

- `:app:compileAppDebugKotlin`：**BUILD SUCCESSFUL**
- `testAppDebugUnitTest`：**1,315 / 0 失败 / 0 错误 / 0 跳过**（243 XML；W6 新增 105 项对抗回归，W7 新增 1 项首次建库回归）
- `verifyConfigArchitecture`：**通过**（20 键/68、1 键/0、8 键/20，无缺失键）
- `assembleAppDebug`：**通过**；APK arm64 **62,302 KB**、armv7 **61,853 KB**、universal **68,719 KB**（W0 基线 71.7/71.2/78.2 MB）
- `git diff --check`：0
- 独立验证：`team/W2A-VERIFY.md`（PASS；反证 grep 无删除符号代码引用；保留项核对通过）

## 3. 保留的关键能力

书架/分组、书籍详情、全局搜书、书源管理/编辑/调试/校验/订阅导入、正文阅读/翻页/手势、目录与 TXT 目录规则、正文搜索、换源、缓存下载、本地导入（TXT/EPUB/UMD/压缩包）、阅读进度/记录、书签/高亮/标签规则、替换净化、阅读设置/主题、备份恢复（含 WebDAV）、文件关联/二维码/关于。

## 4. 明确未完成 / 风险

1. **W5 剩余**：lint 全量复跑仍受 `lintAnalyzeAppDebug` 超时限制；`assembleAppNoR8` 在打包阶段因 Gradle daemon 内存崩溃（`mergeDexAppNoR8` 后 daemon disappeared），未取得 NoR8 APK；`lintVitalAnalyzeAppNoR8` 在崩溃前已通过。
2. **真机手测仍未完成**：W7 已在 Android 35 x86_64 模拟器完成全新安装、onboarding、书源管理入口、Settings 和本地 TXT 两页阅读；真实 ARM 设备的阅读渲染/手势/选区、真实书源网络、缓存下载、完整备份恢复和升级安装仍未跑。W7 的 `Migration131To132InstrumentedTest` 已在设备端真实执行 AutoMigration（1/1 通过）；2026-10-22 已在 PLK110（Android 16）真机重跑同一组 5 个用例，**OK (5 tests)**。
3. **兼容性**：旧 Nav3 保存栈中的朗读/音频 route 已删除，升级后首次恢复可能回主页；`BookSource.homepageModules` 字段保留读取但不展示；旧 `httpTTS.json`/RSS/首页模块备份文件忽略而不报错。

## 4.1 W6 对抗性测试（2026-10-21）

- 新增 **105 项**永久回归测试，修复 **24 项**缺陷；详细证据见 [`W6-ADVERSARIAL.md`](./W6-ADVERSARIAL.md)。
- 高危修复：`Migration_131_132` 的 SQL 位或误写为逻辑 `or`（旧 audio/image/video 书 type 被压成 1）；
  `ZipUtils` 同前缀兄弟目录可越界写；缓存队列 `Int.MAX_VALUE` 死循环与模型层超大 Range OOM。
- 最终门禁：`testAppDebugUnitTest` **1,314 / 0**；`verifyConfigArchitecture` 通过；`assembleAppDebug` 通过。

## 4.2 W7 模拟器验证（2026-10-21）

- 环境：Android 35 x86_64 default image，emulator 37.2.12（217 测试机，`zx` 账号）；该用户不在 `kvm` 组，使用 TCG 软件模拟。模拟器只在 217 使用，本机 WSL 不装 emulator。
- **发现并修复首次安装阻断**：`AppDatabase.dbCallback.onOpen` 仍执行 `update rssSources/httpTTS`，全新安装抛
  `no such table: rssSources` 后进程被杀死；删除三条旧域 SQL 和 `RSS_SOURCE_TABLE_NAME`，新增
  `AppDatabaseFreshOpenTest`，并重新卸载/全新安装验证 onboarding 与书架正常。
- 设备端定向测试：`BookDaoTest` **2/2**、`Migration131To132InstrumentedTest` **1/1**、`AndroidJsTest` **2/2**。
- 真机复核（2026-10-22）：PLK110 / Android 16，安装与首次启动 `Status: ok`，同一组用例 **OK (5 tests)**；详见 [`W7-EMULATOR.md`](./W7-EMULATOR.md) §3.1。
- UI 冒烟：Sources Management、Settings 入口收敛为小说域；本地 TXT 阅读第 1/2 页 50%、第 2/2 页 100%。
- 完整证据与复现命令见 [`W7-EMULATOR.md`](./W7-EMULATOR.md)。

## 5. 复现命令

```bash
export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
./gradlew :app:compileAppDebugKotlin
./gradlew testAppDebugUnitTest verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
git diff --check
```
