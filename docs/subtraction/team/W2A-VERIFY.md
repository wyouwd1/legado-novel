# W2a 独立验证报告（Verifier）

> 快照：2026-10-21 11:20 CST（Asia/Shanghai）
> 分支：`subtract/novel-only`
> 验证对象：`dd61c2bfd`（W2a 提交）；当时 HEAD 已推进到 `f90f240c6`（docs-only）
> 角色：独立 Verifier（只读源码 + 验证命令）；本文件之外未修改源码/资源
> 结论：**PASS（有 3 项待确认）**

## 0. 结论

| 验收项 | 结果 | 证据 |
|---|---|---|
| 删除范围属于朗读/音频/TTS/cast/BGM | PASS | 177 个 main 源码 + 53 个 test 源码，逐路径核对均属该域 |
| 保留功能未被误删 | PASS | AnalyzeUrl media3、preserveReadAloudPosition、书源、正文渲染、分页、本地书、备份链路均在 |
| 反证 grep | PASS（有已登记残留） | 250 处 Kotlin 命中全部可归类；删除符号 grep 零命中；详细见 §3 |
| 单测 1262/0 与删测文件相符 | PASS | 235 XML / 1262 tests / 0 failures / 0 errors；1557−302−1=1254 个 `@Test` + 8 个参数化用例 |
| 架构门 21/71、1/0、8/20 且无缺失键 | PASS | 30 个基线条目文件全部存在；W2a 只删掉 2 个对应已删文件的条目 |
| `git diff --check` | PASS | `git diff --check dd61c2bfd^ dd61c2bfd` → exit 0 |
| 独立编译 | PASS（干净副本） | `git archive dd61c2bfd` + `:app:compileAppDebugKotlin` → BUILD SUCCESSFUL；`verifyConfigArchitecture` 实际执行通过 |
| lint | **未验证** | 上一轮 lint analyze 超时；报告中的 4 个 MissingClass 对应 Manifest 组件已在 W2a 中删除，但未复跑 lint |
| APK 体积 | PASS（注意口径） | 67,286,052 / 66,825,874 / 73,857,301 B = 64.2 / 63.7 / 70.4 MiB，`ls -lh` 65/64/71 MB |

3 项待确认：
1. 提交信息写“228 个源码文件”，实际 `git diff --numstat` 为 **230** 个删除（177 main + 53 test）；289 个总变更 = 230 D + 59 M，提交内容自洽，只是文案数字少 2。
2. lint 未复跑；4 个 MissingClass 已从 Manifest 删除，但不能据此宣称 lint 全绿。
3. APK 产物时间早于 Manifest 清理（11:00 vs 11:11），体积数字可用，但不是提交后重新打包的 bit-identical 产物。

## 1. 提交形状

```text
dd61c2bfd refactor(subtract): W2a 删除朗读/音频域（289 文件 / -53373 行）
parent: 824f80097
289 files changed, 103 insertions(+), 53373 deletions(-)
```

| 类别 | 数量 | 说明 |
|---|---:|---|
| `app/src/main` 删除 | 177 | 176 个 Kotlin + 1 个 `ChapterStatusIcons.kt`（cast/audio 状态图标） |
| `app/src/test` 删除 | 53 | 全部为 readaloud/audio/playback/cast 测试 |
| `app/src/main` 修改 | 52 | 51 个 Kotlin 接入点 + `AndroidManifest.xml` |
| `app/src/test` 修改 | 6 | 删除朗读字段/构造参数/路由断言 |
| `build.gradle.kts` 修改 | 1 | 仅删除 2 个已删文件的架构门基线键 |
| **合计** | **289** | 230 D + 59 M |

`git diff --check dd61c2bfd^ dd61c2bfd` → 0；`git show --check dd61c2bfd` → 0。
工作区在 W2a 提交后仍有 8 个无关删除（`.codex/**` 6 个、`.codebuddy/**` 2 个）与 docs 修改，均未进入 W2a 提交；提交范围干净。

## 2. 删除清单审计

- 177 个 main 删除全部落在：`ui/book/audio/**`、`ui/book/readaloud/**`、`ui/book/read/**` 的朗读件、`feature/readaloud/**`、`service/{readaloud,playback}/**`、4 个朗读/音频 Service、`help/{readaloud,TTS,MediaHelp,exoplayer}`、`domain/model/readaloud/**`、cast/TTS/音频相关 repository/gateway/usecase、`MediaButtonReceiver`、播放器 widget。
- 53 个 test 删除全部是上述域的测试；没有删除书源、正文、分页、本地书、备份域的测试。
- 9 个路径不带明显关键字但已核对归属：`IdentifyBookCharactersUseCase`/`ResolveLocalSpeakersUseCase`（AI 配音角色）、`MediaHelp`（音频元数据）、`ChapterStatusIcons`（目录 cast/音频状态）、5 个 `ui/widget/components/player/*`（播放器控件）。
- `build.gradle.kts` 只删两条基线：`receiver/MediaButtonReceiver.kt to 2`、`ui/book/readaloud/cloudtts/CloudTtsViewModel.kt to 13`，二者均在本次删除清单内，无“基线指向已删文件”的残留。

## 3. 反证 grep

命令（大小写不敏感）：

```bash
grep -riE 'readaloud|audio-?play|\btts\b|\bbgm\b|\bcast\b' app/src/main
```

结果：Kotlin **59 文件 / 250 处**；含 `res/` 与 assets 共 **70 文件 / 417 处**。逐类核对如下，均有明确归属，不是漏删的编译引用：

| 类别 | 代表文件 / 处数 | 判定 |
|---|---|---|
| 阅读高亮色 | `ReaderCanvasSurface.kt` 22、`ReaderPage.kt` 3、`ReadBookController.kt` 1 | **保留**：`readAloud: Color` 与 `readAloudParagraphIndex` 是正文高亮渲染语义，不是朗读服务 |
| cast 文本工具 | `feature/reader/core/cast/*`（CastMarkers/CastSyntax/CastCapsule*）、`LegacyReaderStyleRangeMapper.kt` 18、`LegacyReaderChapterPaginator.kt` 3、`ReaderChapterSource.kt` 2 | **保留到 W2b**：文本引号/标记解析工具；`ReaderCastOptions(...)` 只有定义和 `Disabled`，`castLabels`/`bgmScenes` 零调用方，生产链路已无 cast/BGM 生产者。W2b 计划明确保留 `CastMarkers.kt`/`CastSyntax.kt` 并收敛 |
| Room schema 兼容 | `data/entities` 13 个、`data/dao` 10 个、`AppDatabase.kt`、`di/appDatabaseModule.kt`、`ReadAloudVoiceDao.kt` | **保留到 W4**：`AppDatabase version = 131` 未变；删除 entity 会改变 schema identity hash，必须在 W4 与 `version+1` + migration 同批 |
| 旧偏好/通知/Intent 常量 | `PreferKey.kt` 20、`NotificationId.kt` 3、`AppConst.channelIdReadAloud` + `App.kt` channel 创建、`IntentAction.kt` 4、`EventBus.kt` 2、`OtherSettings.audioPlayUseWakeLock` | **保留到 W2-S4/W5**：旧设置、通知渠道、广播常量的数据兼容；当前无运行时消费者 |
| 设置 UI 残留 | `ReadConfigScreen.kt`/`MoreConfigSheet.kt` 的 `readAloudDetachReminderEnabled`、`ReadBookContract.kt` 的死按钮 id、`ReadButtonConfigDelegate.kt` 的 `"read_aloud"` 默认项、`ReadBookViewModel.kt:5` 未使用的 `TextToSpeech` import | **已知延后**：W2-READER-EDITS §0 明确建议 W4 处理；当前是可点击但无效果的设置项，非编译阻塞 |
| morph 死代码 | `core/ui/player/PlayerMorphHost.kt` 616 行（仅 `playerUnderlaySemantics` 被 `MainActivity` 用）、`PlayerMorphCover.kt` 68 行无引用、`ui/book/readaloud/morph/ReadAloudMorphState.kt` 305 行仅被前者引用 | **待清理**：`PlayerMorphFrames.kt` 仍被 `BookMorphState.kt` 使用，不能整包删；其余无生产调用方 |
| 桌面快捷方式 | `api/ShortCuts.kt:47-62` 仍构建 `readAloud` 动态快捷方式，但 `SharedReceiverActivity` 的 `"readAloud"` 分支已删 | **已知延后**：W3-EDIT-PLAN §3.4 明确列为 W3-2 附带清理；当前点击快捷方式会静默落到无操作 |
| 资源 | `res/xml/pref_config_aloud.xml`（5 处，零引用）、`pref_config_other.xml:138`、四语言 `strings.xml` 约 153 处、`ic_read_aloud`/`ic_st_aloud`/`ic_shortcut_read_aloud` | **保留到 W2-S4/W5-6**：资源清理会牵动四语言，按计划后置；`assets/defaultData/httpTTS.json` 仍被 `DefaultData`/`Backup`/`Restore` 使用，属于保留项 |

删除符号反证：

```bash
grep -rnE '\b(BaseReadAloudService|ReadAloud|AudioPlay|ReadAloudPlayerViewModel|PlaybackCapsuleGateway|CloudTtsViewModel|HttpTtsRepository|CastAssignmentStore|BgmSceneStore|TTSReadAloudService)\b' app/src/main/java --include='*.kt'
```

结果：仅 2 处 KDoc 历史引用（`CastMarkers.kt:32`、`ReaderChapterSource.kt:58`），无代码引用。
`ReaderCastOptions(` 仅 `ReaderPaginator.kt:112`（定义）与 `:132`（`Disabled`）；`castLabels`/`bgmScenes` 除 `ReaderChapterSource.kt` 的形参外零调用方。

## 4. 关键保留文件核对

| 保留项 | 证据 |
|---|---|
| `AnalyzeUrl.kt` 的 media3 / 书源 JS API | `androidx.media3.common.MediaItem` import 保留；`getMediaItem()` 在 `AnalyzeUrl.kt:811-829` 内联复刻原 `ExoPlayerHelper.createMediaItem`（同 `\uD83D\uDEA7` 分隔符 + GSON headers）；`app/build.gradle.kts:219-220` 的 media3 依赖未动 |
| `preserveReadAloudPosition` | `ReadBook.kt:1153/1233/1306/1426/1529/1558` 参数链完整；`loadOrUpContent` 在 `:1275-1279` 注释说明“朗读域已移除，语义保留，恒传 false”；`CacheBookModel.kt` 的 pending 合并逻辑保留 |
| 书源链路 | `help/source/**`、`BookSource`/`Book.kt`、`BookSourceExtensions` 无删除；`SourceHelp.kt` 只删 `AudioPlay.bookSource` 分支；书源 JS 的 `getMediaItem()` 保留 |
| 正文/渲染 | `ContentProcessor`、`ReaderChapterSourceParser`、`ReaderPaginator`、`ReaderCanvasSurface` 的文本绘制与 `readAloud` 高亮分支未删；仅删 `RoleCast`/`BgmScene` 的绘制调用（分支保留为 no-op） |
| 分页 | `LegacyReaderChapterPaginator.kt` 只把 `CastCapsuleStyleStore` 改为 `CastCapsuleStyle.Default`（`castOptions.enabled` 恒 false，不参与测量）；文本分页、页码估算、`ReaderPaginationConfig` 几何字段均未改 |
| 备份 | `Backup.kt` 仍备份 `httpTTS.json`（`:82/:188`）；`Restore.kt` 仍恢复 `HttpTTS` 行（`:235`），只删 `restoreHttpTtsCatalogs`（朗读音色目录）与 `SyncHttpTtsCatalogUseCase`；`BackupConfig` 未改 |
| 本地书 | `model/localBook/**`、`BookCacheManage`、`BookContent` 无本次 diff；`BookType.audio` 与 `Book.isAudio` 全链路保留（书目过滤、分组、编辑、导入旧数据），只是音频播放入口已删 |
| 测试保留 | 235 个测试类中 `ReaderPaginatorTest`、`LegacyReaderPaginationBatchTest`、`ReaderChapterSourceParserTest`、`BookRestorePlannerTest`、`LocalBookImportPlannerTest`、`AnalyzeRuleFastPathReproTest` 等均存在且 XML 0 失败 |

## 5. 单测数量

- 当前 `app/src/test`：**235 个文件、1254 个 `@Test` 注解**。
- `app/build/test-results/testAppDebugUnitTest`：**235 个 XML、1262 tests、0 failures、0 errors、0 skipped**。
- 差值 8 来自 `BookHelpImageFileTest`（8 个 `@Test` 注解 × `@Config(sdk = [26, 35])` 双 SDK 展开 = 16 个运行用例），1254 + 8 = 1262。
- 删除算术：W2a 前 1557 个 `@Test`；53 个删除测试文件合计 **302** 个 `@Test`；`MainIntentTest.kt` 修改中再删 1 个 → 1557 − 303 = 1254。测试数下降与删测文件完全对得上。
- 53 个删除测试文件的路径逐一核对，全部属于朗读/音频/播放/cast/播放器域。

## 6. 架构门

静态核对 `build.gradle.kts` 的三张基线：

| 基线 | 键数 / 基线和 | 缺失文件 |
|---|---:|---:|
| `legacyPreferenceCallBaseline` | 21 / 71 | 0 |
| `legacyDaoInjectionBaseline` | 1 / 0 | 0 |
| `legacyUiDaoAccessBaseline` | 8 / 20 | 0 |

30 个基线路径全部存在；W2a 只删除了 `MediaButtonReceiver.kt`、`CloudTtsViewModel.kt` 两条并同步删除对应基线键，无“键指向已删文件”。干净副本的 `:verifyConfigArchitecture` 实际执行通过。

## 7. APK 体积

`app/build/outputs/apk/app/debug/`（2026-10-21 11:00 CST 产物）：

| ABI | 字节 | MiB | `ls -lh` | W0 基线（MiB） | 差 |
|---|---:|---:|---:|---:|---:|
| arm64-v8a | 67,286,052 | 64.16 | 65M | 68.35 | −4.19 |
| armeabi-v7a | 66,825,874 | 63.72 | 64M | 67.91 | −4.19 |
| universal | 73,857,301 | 70.43 | 71M | 74.62 | −4.19 |

注：上述 APK 生成于 11:00，Manifest 清理在 11:11，提交在 11:13；体积数字对 W2a 源码主体有效，但不是提交后重新打包的产物。

## 8. 独立复验记录

1. 主工作区直接跑 `:app:compileAppDebugKotlin` 失败，原因是**并发 W3 改动**已把 `ui/main/homepage/modules/BannerModule.kt` 移到 `ui/widget/components/book/BookBannerRow.kt`，`verifyConfigArchitecture` 读旧路径报 `FileNotFoundException`；与 W2a 无关，不能作为 W2a 失败证据。
2. 用 `git archive dd61c2bfd` 生成干净副本（补 `local.properties`），在副本运行：
   ```bash
   ./gradlew :app:compileAppDebugKotlin --no-configuration-cache --console=plain
   ```
   结果 **BUILD SUCCESSFUL in 3s**；`:verifyConfigArchitecture` 实际执行通过，`:app:compileAppDebugKotlin` 命中构建缓存（输入与提交源码一致）。
3. 未运行 `testAppDebugUnitTest`（主控已有 1262/0 的 XML，本报告只复核 XML）与 `assembleAppDebug`（主控已产出 65/64/71MB，本报告只测量产物）。
4. lint：`app/build/reports/lint-results-appDebug.xml` 记录 156 个 issue，其中 4 个 error 全是 `MissingClass`，指向 `TTSReadAloudService`/`HttpReadAloudService`/`AudioPlayService`/`MediaButtonReceiver`；当前 `AndroidManifest.xml` 已无这些引用。因 lint analyze 阶段超时、未成功复跑，**lint 结论保持未验证**。

## 9. 未验证风险 / 后续

| 项 | 状态 | 建议 |
|---|---|---|
| lint 全绿 | 未验证 | W5 重跑 `lintAppDebug`；先确认这 4 个 MissingClass 已消失 |
| 真机阅读冒烟 | 未验证 | 打开 txt/epub、翻页/滚动、目录、备份恢复、书源搜索各一次；音频书条目现在会走文本阅读入口，需确认提示可接受 |
| 设置页死开关 | 已知残留 | `readAloudDetachReminderEnabled` 在阅读设置与更多设置仍可见；按 W2-READER-EDITS §0 建议 W4 与 DataStore/备份兼容一起删 |
| 动态快捷方式 | 已知残留 | `ShortCuts` 仍创建 `readAloud` 快捷方式，W3-2 迁 `ReaderProvider` 时一并移除 |
| morph 死代码 | 已知残留 | `PlayerMorphHost`/`PlayerMorphCover`/`ReadAloudMorphState` 无生产调用方；`PlayerMorphFrames` 有 `BookMorphState` 使用，不能整包删 |
| Room 表/实体 | 按计划保留 | W4-1 与 `version+1` + migration 同批删除，禁止 W2/W3 提前删 |
| 提交文案 “228” | 已记录 | 实际 230 个删除；若不改历史提交，至少在 W2a 交付说明中注明 230 |
| docs commit 空白 | 非 W2a | `f90f240c6` 的 `git diff --check` 报 6 处 docs 行尾空格（`W2-NAV-EDITS.md`/`W2B-CORE-PLAN.md`）；不影响 W2a commit，若要求全仓 clean 可修文档 |

## 10. 复现命令

```bash
cd /home/zx/workspace/legado-with-MD3

# 提交形状
git show --shortstat --format='%H %s' dd61c2bfd
git diff --check dd61c2bfd^ dd61c2bfd

# 反证
grep -riE 'readaloud|audio-?play|\btts\b|\bbgm\b|\bcast\b' app/src/main
grep -rnE '\b(BaseReadAloudService|ReadAloud|AudioPlay|ReadAloudPlayerViewModel|PlaybackCapsuleGateway|CloudTtsViewModel|HttpTtsRepository|CastAssignmentStore|BgmSceneStore|TTSReadAloudService)\b' \
  app/src/main/java --include='*.kt'

# 测试 XML 汇总
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=s=0
for p in glob.glob('app/build/test-results/testAppDebugUnitTest/TEST-*.xml'):
    r=ET.parse(p).getroot()
    t += int(r.get('tests',0)); f += int(r.get('failures',0))
    e += int(r.get('errors',0)); s += int(r.get('skipped',0))
print('tests',t,'failures',f,'errors',e,'skipped',s)
PY

# 干净副本编译
rm -rf /tmp/w2a-verify-src && mkdir -p /tmp/w2a-verify-src
git archive dd61c2bfd | tar -x -C /tmp/w2a-verify-src
cp local.properties /tmp/w2a-verify-src/local.properties
cd /tmp/w2a-verify-src && ./gradlew :app:compileAppDebugKotlin --no-configuration-cache --console=plain
```
