# W6-C 存储/迁移对抗性测试报告

> 分支 `subtract/novel-only` ｜ 执行时间 2026-10-21 14:05–14:32 CST ｜ 未 commit。
> 范围：`utils/compress/**`、`help/storage/**`、`data/DatabaseMigrations.kt`（仅证明缺陷时）、对应单测。
> 结论：**修复 6 个已证明缺陷，新增 30 个永久回归测试；定向测试 49 项 + 迁移测试 2 项全绿。**

## 0. 结果速览

| 项 | 结果 |
|---|---|
| 基线命令 `/tmp/legado-gradle.sh :app:testAppDebugUnitTest --tests 'io.legado.app.help.storage.*' --tests 'io.legado.app.utils.compress.*'` | 改动前 BUILD SUCCESSFUL（旧测试全绿，无对抗用例） |
| 修复后定向测试（`--rerun-tasks`，日志 `/tmp/w6c-targeted-rerun.log`） | **BUILD SUCCESSFUL；6 个测试类 / 49 项 / 0 失败** |
| 迁移测试（日志 `/tmp/w6c-migration.log`） | **BUILD SUCCESSFUL；2 项 / 0 失败**（`/tmp/w6c-migration-result.xml`） |
| `git diff --check`（本次涉及文件） | exit 0 |
| 真实 Room `MigrationTestHelper` | **未跑**：`room-testing` 只在 `androidTestImplementation`，未改构建；见 §5 未验证风险 |
| 新增永久测试 | **30 项**（ZIP 12、normalizeConfig 3、BookRestorePlanner 5、BackupConfig 6、旧域隔离 2、迁移 2） |

## 1. 已证明并修复的缺陷

### C1-1（高危）ZIP 同前缀兄弟目录越界写

- **输入**：目标目录 `.../backup`，ZIP 条目 `../backup-evil/evil.txt`。
- **实际（修复前）**：`entryFile.canonicalPath.startsWith(dir.canonicalPath)` 对 `.../backup-evil/evil.txt` 返回 true；解压器创建 `backup-evil/` 并把内容写到目标目录外。
- **期望**：抛 `SecurityException`，目标外不产生任何文件。
- **测试名**：`ZipUtilsSecurityTest.same-prefix sibling directory cannot escape the target`
- **修复点**：`ZipUtils.unZipToPath` 改为 `canonicalDir.path` 严格边界：只允许 `canonical == dir`（且必须是目录条目）或 `canonical.startsWith(dir + File.separator)`。
- **修复前证据**：该用例 FAILED（`AssertionError`）；修复后 12 个 ZIP 用例全过。

### C1-2（中危）非 ZIP/损坏包静默成功

- **输入**：普通文本文件、截断 ZIP、空条目名、反斜杠路径。
- **实际（修复前）**：
  - 非 ZIP 文本：`ZipInputStream.nextEntry` 返回 null，方法返回空列表，调用方误以为“解压成功”；
  - 空条目名：`FileOutputStream(dir)` 抛 `FileNotFoundException`，错误类型不明确；
  - 反斜杠条目在 Linux 上被当作普通文件名，不做路径归一化。
- **期望**：非 ZIP/损坏包明确抛 `IOException`/`SecurityException`；空白条目名拒绝；反斜杠按分隔符归一化后再做边界判断。
- **测试名**：
  - `ZipUtilsSecurityTest.non-zip input fails explicitly`
  - `ZipUtilsSecurityTest.truncated zip fails explicitly`
  - `ZipUtilsSecurityTest.blank entry name is rejected`
  - `ZipUtilsSecurityTest.backslash traversal is rejected`
- **修复点**：解压前校验 ZIP 文件头（`PK\x03\x04` / 空包 `PK\x05\x06` / spanned `PK\x07\x08`）；空条目名抛 `SecurityException`；`entryName.replace('\\','/')` 后再 `canonicalFile`。
- **兼容性**：合法嵌套路径、中文文件名、目录条目、重复条目名、空 ZIP 均保持可解压；见 `legal nested and chinese file names extract intact`、`duplicate names keep the last entry`、`empty zip extracts nothing without failing`。

### C3-1（高危）Migration_131_132 把位或写成逻辑 `or`，整列 type 变 0/1

- **输入**：v131 `books` 中 `type` 含 audio(32)/image(64)/video(4) 位的行，例如 `audio`、`audio|local`、`text|audio`。
- **实际（修复前）**：`onPostMigrate` SQL 为
  `UPDATE books SET type = (type & ~100) or 8 or 1024 WHERE type & 100 > 0`
  SQLite 的 `OR` 是逻辑或，结果恒为 `0/1`，实际所有命中行 `type = 1`。
  **受影响范围**：所有带 audio/image/video 任一旧位的旧书；包括本地音频/图片书（`local` 位 256 被清）、`text|audio`（text 位 8 被清）、`webFile|audio`（webFile 位 128 被清）。行数据、书名、作者、章节进度列不受影响，但 `type` 语义丢失，`isLocal`/`isText` 判定失效，书可能从书架消失或无法打开。
- **期望**：清除 audio/image/video 位，保留其余位，并置 `text|notShelf`。例如：
  - `audio` (32) → `text|notShelf` (1032)
  - `audio|local` (288) → `text|notShelf|local` (1288)
  - `webFile` (128) → 128（不命中 WHERE，不应改）
- **测试名**：`DatabaseMigration131To132Test.onPostMigrate converts legacy audio image video books and preserves data`
- **修复前证据**：`java.lang.AssertionError: expected:<1032> but was:<1>`（`/tmp/w6c-storage-mid.log`；修复前测试 XML 报错行 `DatabaseMigration131To132Test.kt:90`）。
- **修复点**：`DatabaseMigrations.kt` 的 SQL 改为：
  ```sql
  UPDATE books SET type = (type & ~100) | 8 | 1024
  WHERE (type & 100) > 0
  ```
  （生产代码用 `BookType.audio or BookType.image or BookType.video` 计算 `100`，用 `| ${BookType.text} | ${BookType.notShelf}` 拼接位或。）
- **修复后证据**：同一测试通过，7 行 type 分别符合 8 / 1032 / 1032 / 1032 / 1288 / 1032 / 128；书名/作者/行数保持不变。
- **附带校验**：`DatabaseMigration131To132Test.schema 132 removed tables equal delete-table annotation` 断言 131→132 删除表集合恰好 **40 张**，且 `132.json` 不再声明它们；132 新增表为 0。

### C2-1（中危）Restore 配置 `null` 被写成字符串 `"null"`

- **输入**：`config.xml`/JSON 中 `webDavPassword = null`，本地无密码，解密失败。
- **实际（修复前）**：`value.toString()` 得到 `"null"`，作为密码写入 DataStore。
- **期望**：缺字段/显式 null 直接忽略；非字符串类型（数字/对象）也不得 `toString` 后写入密码。
- **测试名**：
  - `NormalizeConfigMapTest.null 值不会变成字符串 null`
  - `NormalizeConfigMapTest.webDavPassword 非字符串类型被忽略`
- **修复点**：`normalizeConfigMap` 首部跳过 null；`webDavPassword` 分支只接受 `value as? String`，其余忽略。

### C2-2（中危）BackupConfig 一个坏字段导致整张忽略配置丢失

- **输入**：`restoreIgnore.json` = `{"readConfig":true,"badType":123,"localBook":true}`。
- **实际（修复前）**：`GSON.fromJsonObject<HashMap<String, Boolean>>` 遇到数字抛 `JsonSyntaxException`，`getOrNull()` 返回 null，整张 map 变空——用户设置的 `readConfig/localBook` 忽略项全部失效，恢复时可能覆盖本机阅读配置。
- **期望**：逐键保留 Boolean，坏字段忽略，好的忽略项继续生效。
- **测试名**：`BackupConfigParsingTest.one bad value does not discard the whole config`
- **修复点**：新增 `parseIgnoreConfigMap`：先按 `Map<String, Any?>` 解析，再只保留 `Boolean` 值；四个 `*IgnoreConfig` lazy 统一走它。
- **兼容性**：缺文件、空字符串、`{}`、`[]`、`null`、坏 JSON 均返回空 map，不抛；旧域字段（如 `rss/httpTTS/homepage`）作为未知键不会导致解析失败。

### C2-3（中危）旧备份/旧书导入把 audio/image 书恢复回已裁撤域

- **输入**：`bookshelf.json` 或 `myBookShelf.json` 中 `type = BookType.audio` / `BookType.image`（含 `local` 位）。
- **实际（修复前）**：`Book.upType()` 只处理 `type < 8` 的旧枚举，不处理 32/64 位；`planBookRestore` 原样保存；`ImportOldData.fromOldBooks` 只把 `AUDIO` 映射成 `BookType.audio`。旧音频/图片书恢复后仍是 audio/image，重新进入已裁撤域。
- **期望**：与 131→132 迁移一致：清除 audio/image/video 位，置 `text|notShelf`，保留 `local`、章节进度等其余字段。
- **测试名**：
  - `BookRestorePlannerTest.legacy audio book is restored as text and moved off the shelf`
  - `BookRestorePlannerTest.legacy image book keeps local flag and data while leaving the shelf`
  - `BookRestorePlannerTest.plain text book type is not changed by restore`
- **修复点**：`BookRestorePlanner.kt` 新增 `migrateLegacyBookType(type)`，在 `restoredForSave` 统一归一化；`ImportOldData.fromOldBooks` 对 `AUDIO/IMAGE/VIDEO` 旧 source type 复用同一函数。
- **修复前证据**：planner 用例断言 `text|notShelf`，修复前实际为 `audio`/`image`，用例 FAILED。

## 2. 新增/修改文件

### 生产代码（5 个）

| 文件 | 改动 |
|---|---|
| `app/src/main/java/io/legado/app/utils/compress/ZipUtils.kt` | 严格 canonical 边界、ZIP 头校验、反斜杠归一化、空条目名拒绝 |
| `app/src/main/java/io/legado/app/help/storage/RestoreConfigNormalizer.kt` | null / 非字符串 webDavPassword 过滤 |
| `app/src/main/java/io/legado/app/help/storage/BackupConfig.kt` | `parseIgnoreConfigMap` 逐键解析，四个 ignore 配置复用 |
| `app/src/main/java/io/legado/app/help/storage/BookRestorePlanner.kt` | `migrateLegacyBookType`；恢复书籍类型归一化 |
| `app/src/main/java/io/legado/app/help/storage/ImportOldData.kt` | 旧书架 AUDIO/IMAGE/VIDEO → text+notShelf；复用 `migrateLegacyBookType` |
| `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt` | **仅在证明 SQL 缺陷后**：`or` → `|`，WHERE 加括号 |

### 测试代码（8 个）

| 文件 | 新增测试 |
|---|---|
| `app/src/test/java/io/legado/app/utils/compress/ZipUtilsSecurityTest.kt` | 12 |
| `app/src/test/java/io/legado/app/help/storage/NormalizeConfigMapTest.kt` | +3（原 11） |
| `app/src/test/java/io/legado/app/help/storage/BookRestorePlannerTest.kt` | +5（原 8） |
| `app/src/test/java/io/legado/app/help/storage/BackupConfigParsingTest.kt` | 6 |
| `app/src/test/java/io/legado/app/help/storage/RemovedDomainBackupIsolationTest.kt` | 2（静态源码 allowlist 断言） |
| `app/src/test/java/io/legado/app/data/DatabaseMigration131To132Test.kt` | 2（schema 静态 + 真实 SQLite 执行 onPostMigrate） |

## 3. 旧域数据/现有数据保护

| 检查 | 证据 |
|---|---|
| Backup 不再枚举 RSS/httpTTS/homepage 文件 | `RemovedDomainBackupIsolationTest.backup does not enumerate removed domain files`（源码断言，7 个旧文件名零命中） |
| Restore 不再读取上述文件 | `RemovedDomainBackupIsolationTest.restore does not enumerate removed domain files` |
| 现有书籍不因空备份被删 | `BookRestorePlannerTest.empty backup leaves every existing book untouched` |
| 备份中缺失的现有书籍不被删 | `BookRestorePlannerTest.book missing from backup is not deleted` |
| 现有书源不被删 | 代码审计：`Restore` 对 `bookSource.json` 只 `insert`/`ImportOldData.importOldSource`，无 delete；`Backup` 仅导出当前域 |
| 旧 audio/image 书保留数据但移出书架 | §C2-3 的 planner/import 测试；迁移测试验证行数/书名/作者不变 |

> 说明：`Restore` 完整链路依赖 `appDb`/Koin/Android，本波未跑真实恢复；上面“现有书源不被删”是代码审计 + 静态 allowlist 证据，不是运行时集成测试。

## 4. 命令与证据

```bash
# 基线（改动前，旧测试）
/tmp/legado-gradle.sh :app:testAppDebugUnitTest \
  --tests 'io.legado.app.help.storage.*' --tests 'io.legado.app.utils.compress.*'
# BUILD SUCCESSFUL（无对抗用例）

# 定向测试（修复后，强制重跑）
/tmp/legado-gradle.sh :app:testAppDebugUnitTest --rerun-tasks \
  --tests 'io.legado.app.help.storage.*' --tests 'io.legado.app.utils.compress.*'
# BUILD SUCCESSFUL；49 tests / 0 failed

# 迁移测试（修复后，强制重跑）
/tmp/legado-gradle.sh :app:testAppDebugUnitTest --rerun-tasks \
  --tests 'io.legado.app.data.DatabaseMigration131To132Test'
# BUILD SUCCESSFUL；2 tests / 0 failed

git diff --check -- <本次涉及文件>
# exit 0
```

日志与结果快照：

- `/tmp/w6c-targeted-rerun.log`（定向 49 项）
- `/tmp/w6c-migration.log`、`/tmp/w6c-migration-result.xml`（迁移 2 项）
- `/tmp/w6c-zip-before.log`（ZIP 修复前 4 失败）
- `/tmp/w6c-results/`（定向测试 XML 快照）

## 5. 未验证风险

| 风险 | 说明 | 建议 |
|---|---|---|
| 真实 Room 迁移未跑 | `room-testing` 只在 `androidTestImplementation`，JVM 单测没有 `MigrationTestHelper`；本报告用「131/132 schema JSON 静态一致性 + Robolectric framework SQLite 直接执行生产 `onPostMigrate`」替代。**AutoMigration 生成的 40 条 DROP TABLE 未在真实 Room 打开流程中执行/校验**。 | W6-D 或 androidTest 跑一次 `MigrationTestHelper`（v131 种 3 类书 → 打开 v132，验证 40 表消失 + type 1032/1288） |
| 旧 `BookSource` 的 audio/image 类型未归一化 | `ImportOldData.fromOldBookSource` 仍把 `"AUDIO"` 映射为 `BookSourceType.audio`；`BookSourceExtensions.getBookType()` 会生成 `BookType.audio`，旧音频源导入后仍可产生音频书。本波只归一化了书籍类型，未动源类型（避免把音频源误改成文本源后规则解析失败）。 | 产品裁决：跳过 audio/image 旧源，或映射为 text 并标记不可用 |
| Restore 完整链路未跑 | `restoreUnzipped()` 依赖 `appDb`、Koin、`appCtx.toastOnUi`、`withContext(Main)`，不适合 JVM 纯单测；本波只覆盖纯函数与 planner。 | W6-D 用 Robolectric/真机导入备份冒烟 |
| ZIP 兼容性收窄 | 现在拒绝带自解压前缀（非 `PK` 开头）的 ZIP；App 自身备份始终是标准 ZIP。若用户导入第三方自解压包会从“静默空解压”变为 `ZipException`。 | 如产品需要兼容，先检测 EOCD 再放宽，不要恢复 `startsWith` 边界 |
| BackupConfig 字符串布尔值 | 旧实现 Gson 会把 `"true"` 字符串强制转 true；新实现只认 JSON Boolean，字符串值被忽略（忽略项失效=恢复更多设置，不会删数据）。App 自身写入的是 Boolean，兼容正常备份。 | 如需兼容手改文件，可在 parser 中显式接受 `"true"/"false"` |
| 迁移测试未覆盖完整 65 表 schema | 测试手工建 `books` 表后直接调用 `onPostMigrate`，不经过 Room 的 65 表校验、外键/索引重建。 | 与“真实 Room 迁移未跑”合并由 androidTest 覆盖 |
| `ImportOldData` 旧书 JSON 解析未直接测 | `fromOldBooks` 私有且依赖 `appDb`；只测了共享的 `migrateLegacyBookType` 和 planner 路径。 | 后续可抽纯 `parseOldBookType` 测试 |

## 6. 受影响的旧书范围（迁移 SQL）

修复前 `type = 1` 的命中条件：`type & (BookType.audio | BookType.image | BookType.video) != 0`，即 type 的 bit 2（video=4）、bit 5（audio=32）、bit 6（image=64）任一为 1。

| 旧 type 示例 | 修复前结果 | 修复后结果 |
|---|---:|---:|
| `audio` 32 | 1 | `text|notShelf` 1032 |
| `image` 64 | 1 | 1032 |
| `video` 4 | 1 | 1032 |
| `audio|local` 288 | 1 | `text|notShelf|local` 1288 |
| `text|audio` 40 | 1 | `text|notShelf` 1032 |
| `webFile` 128 | 128（WHERE 不命中） | 128 |
| `text` 8 | 8 | 8 |

受影响的是**所有带音频/图片/视频旧位的书籍行**；行本身不删除，书名/作者/章节进度保留，但修复前 type 被逻辑或压成 1，导致 `isLocal`、`isText` 等位判定失真。修复后按 131→132 预期迁移，不丢数据。
