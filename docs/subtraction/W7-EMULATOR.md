# W7 · Android 模拟器验证（2026-10-21）

> 目标：把“没有真机”从口头风险变成可执行的设备证据；同时用真实 Android 运行时复核
> Room 131→132 AutoMigration 与首次建库路径。

## 1. 环境与限制

| 项 | 值 |
|---|---|
| 主机 | 217 测试机（`test-env-wifi`，`zx` 账号）：Ubuntu 24.04，16 vCPU / 15 GiB；模拟器与 AVD 只装在这台机器 |
| Emulator | `emulator 37.2.12`（`$HOME/android-sdk/emulator`） |
| System image | `system-images;android-35;default;x86_64`（checksum `2d857d17…` 已核对） |
| AVD | `legado-api35`，Pixel 6 配置，运行分辨率降到 540×1200 |
| 硬件加速 | **不可用**：`/dev/kvm` 存在，但当前用户不在 `kvm` 组；`emulator -accel-check` 报权限失败 |
| 运行方式 | `-accel off -gpu swiftshader_indirect -no-window`（TCG 软件模拟） |
| APK | `app-app-universal-debug.apk`（x86_64 原生库已包含） |

TCG 下 1080p 会让 SystemUI 自身 ANR；降到 540×1200 后系统可交互，App 首次绘制约 **43.8 s**。
这是模拟器 CPU 性能限制，不是产品启动耗时结论。

本机 WSL 不装 emulator/AVD：模拟器测试一律在 217 执行；真机测试由本机 WSL 调 Windows 侧
`adb.exe` 完成（见 §3.1）。

## 2. 首次安装发现并修复的阻断缺陷

### 2.1 现象

全新安装后首次进入主界面，进程被 `Process.killProcess` 杀死并弹出崩溃报告。设备端 crash 文件：

```text
android.database.sqlite.SQLiteException: no such table: rssSources
while compiling: update rssSources set loginUi = null where loginUi = 'null'
at io.legado.app.data.AppDatabase$Companion$dbCallback$1.onOpen(AppDatabase.kt:345)
```

### 2.2 根因

131→132 已经通过 `@DeleteTable` 删除 `rssSources`、`httpTTS`，但
`AppDatabase.dbCallback.onOpen` 仍执行三条旧域 SQL：

- `update rssSources set loginUi = null ...`
- `update httpTTS set loginUi = null ...`
- `update httpTTS set concurrentRate = '0' ...`

JVM 测试的 `InMemoryAppDatabaseFixture` 刻意不安装生产 callback，因此没有覆盖到这条路径。

### 2.3 修复与回归

- 删除上述三条 SQL 和已无引用的 `RSS_SOURCE_TABLE_NAME` 常量。
- 新增 `app/src/test/java/io/legado/app/data/AppDatabaseFreshOpenTest.kt`：
  用 `Room.inMemoryDatabaseBuilder(...).addCallback(AppDatabase.dbCallback)` 真实触发首次建库与
  `onOpen`，并断言 `book_groups` 已初始化、旧域表不存在。
- 修复后重新**卸载/全新安装**并完成 onboarding：主书架正常显示，无 crash 文件。

## 3. 设备端定向测试

用 `am instrument` 直接运行，避免 Gradle `connectedAndroidTest` 的额外构建开销：

```bash
adb shell setprop wrap.io.legato.kazusa.debug logwrapper
adb shell setprop wrap.io.legato.kazusa.debug.test logwrapper
adb shell cmd package compile -m speed -f io.legato.kazusa.debug
adb shell cmd package compile -m speed -f io.legato.kazusa.debug.test
adb shell am instrument -w -r \
  -e class io.legado.app.data.dao.BookDaoTest,io.legado.app.data.Migration131To132InstrumentedTest,io.legado.app.AndroidJsTest \
  io.legato.kazusa.debug.test/androidx.test.runner.AndroidJUnitRunner
```

| 测试类 | 结果 | 覆盖 |
|---|---|---|
| `BookDaoTest` | **OK (2 tests)** | 真实 Room/SQLite 的书架摘要与 replaceAll |
| `Migration131To132InstrumentedTest` | **OK (1 test)** | 真实 Room AutoMigration、40 张旧表 DROP、媒体书 type 位与进度保留 |
| `AndroidJsTest` | **OK (2 tests)** | Rhino + Hutool/Java 包桥、ScriptBindings map 访问 |

`Migration131To132InstrumentedTest` 是 W7 新增的 androidTest；它补上了 W6 只做 Robolectric SQL
执行、未让 Room 自己跑 AutoMigration 的缺口。注意 `wrap` 只是 TCG 下绕过 20 秒进程启动超时的
测试夹具开关，不进入产品代码。

### 3.1 真机复核（2026-10-22，PLK110 / Android 16）

同一组用例在真机上重跑；安装包取自 217 的增量构建产物（`subtract/novel-only` `37a2bb51f9`，
APK 时间戳晚于对应源码最后修改时间，未做 clean 重打包）。真机 PLK110 接在本机 Windows 侧，
经 `adb.exe` 安装并执行。

```bash
adb install -r -t app/build/outputs/apk/app/debug/app-app-arm64-v8a-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk
adb shell am start -n io.legato.kazusa.debug/io.legado.app.ui.main.MainActivity
adb shell am instrument -w -r \
  -e class io.legado.app.data.dao.BookDaoTest,io.legado.app.data.Migration131To132InstrumentedTest,io.legado.app.AndroidJsTest \
  io.legato.kazusa.debug.test/androidx.test.runner.AndroidJUnitRunner
```

| 项 | 结果 |
|---|---|
| 安装（app / androidTest） | `Success` / `Success` |
| 首次启动 `MainActivity` | `Status: ok`，`3.26.16_debug`，logcat 无 `FATAL EXCEPTION`、crash 目录为空 |
| 定向测试 | **OK (5 tests)**，`Time: 0.24`；真机未使用 `wrap.*` 夹具开关 |

真机只复核了启动路径与这 5 个用例；阅读渲染、手势、选区、真实书源网络等手工项仍见 §6。

## 4. UI 冒烟（模拟器截图 + UIAutomator）

| 路径 | 结果 |
|---|---|
| 全新安装 onboarding | Welcome → Backup & Restore → Book Folder → Theme Style → Done，全部可点 |
| 主界面 | Bookshelf 空态、全部/书架/发现/我的 导航正常 |
| 我的 | Sources Management、Replacement Rules、Text Catalog Rules、Dictionary Rules、Tag Rules、Settings、Bookmarks |
| 书源管理 | `Book sources` 页可打开，空列表 + Add 按钮，无旧域入口 |
| Settings | 仅 Theme / Other / Reading Interface / Cover / Download & Cache / Backup & Restore / Privacy / Lab |
| 本地 TXT 阅读 | 导入后 reader 渲染中文段落；第 1/2 页 **50%**，点右半页后第 2/2 页 **100%** |

本地样本包含三章文本；分页边界、段落缩进、中文断行和页脚进度均由真实 Android 渲染验证。

## 5. 测试清理（编译阻断）

减法后以下 androidTest 已失效，导致 `assembleAppDebugAndroidTest` 无法编译；已删除/替换：

- `HttpTtsTest`：引用已删除的 HTTP TTS `speechRatePlay`。
- `MigrationTest`：空 `ALL_MIGRATIONS` + 从 v50 直接开最新版的占位测试，不能验证 131→132；由新
  `Migration131To132InstrumentedTest` 取代。
- `MangaWebtoonRegionInstrumentedTest`：引用已删除的 `domain.reader` 漫画类型。
- `ExampleInstrumentedTest`：查询已裁撤的 `ReaderProvider`，返回 null 后 NPE。
- `AndroidJsTest`：保留 2 个有效桥接用例，删除依赖 `DefiningClassLoader` / 旧 Java String 构造器的历史脚本。

## 6. 仍未覆盖

- 真实 ARM 设备上的阅读渲染/手势/选区、真实书源网络、缓存下载、完整备份恢复和升级安装。
- `HttpTest`、`UpdateTest` 等网络型 androidTest 未纳入本次定向执行。
- KVM 硬件加速未启用；若要让模拟器接近真机性能，需要把当前用户加入 `kvm` 组后重启会话：
  `sudo gpasswd -a $USER kvm`（本次环境无免密 sudo，未执行）。
- `lintAppDebug` 全量与 `assembleAppNoR8` 仍按 W5 记录保留为未完成项。
