# 仓库文件清理计划（Legado Novel）

> 状态：**已验证（PASS），待执行**。2026-10-22 在 217 构建机对清理后的副本跑通：
> `assembleAppDebug` + `testAppDebugUnitTest`（1,315 tests / 0 failures）+ `assembleAppRelease`（R8/lintVital 通过，产物 `io.legadonovel` / 3.26.16）。
> 依据：2026-10-22 的 A/B/C/D 四份审计（本地 `.planning/2026-10-22-repo-cleanup/team/`，未入库）与 `30-CANDIDATES.md`。
> 基线：`main` @ `f35d474e25`，跟踪文件 2,494 个 / 46.6 MB，`.git` 187 MB。

## 1. 目标与边界

**目标**：删除无效文件与死代码，把一次性资料移出构建/发布面，压缩 Git 对象库；不改变 APK 行为。

**不做**：不改包名、不升级依赖、不重写 Git 历史、不删 `app/schemas`、不删 `app/cronetlib`、不删 `attic/webservice`、不删 docs 站点。

**暂缓（需产品判断）**：WebService 设置链路（`pref_*.xml` 之外的偏好字段）、隐私政策中的 Firebase 表述、`auto-release.yml` 发布段、`gradle.properties` 过期 flag。

## 2. 执行阶段

### 阶段 0：Git 维护（无提交）

```bash
# 0.1 先备份对象库（约 187 MB，1 分钟）
cp -a .git /home/zx/legado-git-backup-20261022

# 0.2 合并重复 pack：预计 187 MB → ~112 MB，所有 SHA 不变
git repack -ad && git count-objects -vH

# 0.3 删除已完全并入 main 的本地分支与孤儿 tracking ref
git branch -d srv217-subtract/novel-only
git update-ref -d refs/remotes/srv217/main
git update-ref -d refs/remotes/srv217/subtract/novel-only

# 0.4 upstream-archive 有 28 个独有提交：先打 tag 再决定
git tag archive/upstream-pre-subtract-20260916 upstream-archive
```

### 阶段 1：删除失效入口与 CI（1 个提交）

```bash
git rm CLAUDE.md package.json api.md CHANGELOG.md avd.sh avd.bat
git rm .github/workflows/{autoupdatefork,cronet,stale,release,web,retrace-log,build-apk-for-user,docs}.yml
git rm .github/scripts/{cronet.sh,lzy_web.py,tg_bot.py}
git rm .github/workflows/legado.jks
```

理由：`CLAUDE.md` 的 6 类事实（Firebase、`io.legato.kazusa`、`.claude/skills`、DB v85、CI JDK17、Web 前端）全部过期；其余为上游残留或永假门 workflow。`legado.jks` 是零引用的公开 JKS（上游同 blob），与 217 上的发布密钥 `legado-novel.jks` 无关。

同步编辑（不删除）：
- `.github/ISSUE_TEMPLATE/config.yml`、`01-bugReport.yml`：去掉 `gedoor`/Web 前端指向。
- `.github/dependabot.yml`：删除 `modules/web` 的 npm 条目。

### 阶段 2：删除零引用代码与资源（1 个提交）

```bash
# 2.1 15 个非 Preference 死类（另 3 个 PreferenceDialog 在 2.2 一并删除；18 个合计 122 KB）
git rm app/src/main/java/io/legado/app/ui/widget/recycler/{DragSelectTouchHelper,ViewPager2Container,ItemTouchCallback,LoadMoreView}.kt
git rm app/src/main/java/io/legado/app/help/WebDavManager.kt
git rm app/src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt
git rm app/src/main/java/io/legado/app/utils/{ConstraintModify,ParcelFileDescriptorChannel}.kt
git rm app/src/main/java/io/legado/app/domain/model/ContentChunker.kt
git rm app/src/main/java/io/legado/app/base/adapter/DiffRecyclerAdapter.kt
git rm app/src/main/java/io/legado/app/lib/cronet/CronetCoroutineInterceptor.kt
git rm app/src/main/java/io/legado/app/ui/config/bookshelfConfig/BookshelfConfig.kt
git rm app/src/main/java/io/legado/app/ui/book/read/config/{WaveUnderlineSpan,SvgUnderlineSpan}.kt
git rm app/src/main/java/io/legado/app/ui/config/otherConfig/OtherConfig.kt
git rm app/src/main/java/io/legado/app/service/README.md

# 2.2 死 Preference 链：7 个 pref XML + about.xml + 14 个 lib/prefs 类 + 3 个 layout
git rm app/src/main/res/xml/{about,pref_main,pref_config_aloud,pref_config_backup,pref_config_cover,pref_config_other,pref_config_read,pref_config_theme}.xml
git rm app/src/main/java/io/legado/app/lib/prefs/*.kt
git rm app/src/main/res/layout/view_preference*.xml

# 2.3 未加载的默认书源语料（1.9 MB；同内容已保存在 docs/book-source-verification/sources/01-*.json）
git rm app/src/main/assets/defaultData/bookSources.json

# 2.4 两个与 baseline-prof.txt 完全同哈希的 startup-prof.txt（8.4 MB）
git rm app/src/appRelease/generated/baselineProfiles/startup-prof.txt
git rm app/src/appNoR8/generated/baselineProfiles/startup-prof.txt

# 2.5 未引用的旧 Rhino jar（1.44 MB；构建实际用 Maven rhino 1.8.1）
git rm modules/rhino/lib/rhino-1.7.14.jar
```

同步编辑：
- 四语言 `strings.xml` 删除 `web_service_local_network_permission_denied`（各 1 条）。
- `modules/rhino/build.gradle.kts` 删除未被源码使用的 OkHttp 依赖。
- `app/proguard-rules.pro` 删除 Ktor 专用 3 行。

### 阶段 3：归档一次性资料与归档前端（1 个提交，全部 `git mv`）

```bash
mkdir -p attic/docs/dev attic/webservice
git mv docs/subtraction attic/docs/subtraction
git mv docs/dev/{manga-subsampling-plan,readaloud-player-morph,track-c-compose-reader-plan,track-c3-reader-parity-baseline,track-e-reader-settings-udf-plan}.md attic/docs/dev/
git mv docs/handover-tts-multi-speaker-fixes.md attic/docs/
git mv docs/superpowers attic/docs/superpowers
git mv docs/tts-multi-speaker-design.md attic/docs/
git mv modules/web attic/webservice/web
git rm docs/guide/rss-source.md docs/dev/tts-rule.md
```

说明：`mad-modernization-plan.md` 被长期路线图引用，**保留原位**；其余 5 个一次性执行计划移入 `attic/docs/dev/`。

同步编辑 `docs/.vitepress/config.ts`：
- `base: '/legado-with-MD3/'` → `'/legado-novel/'`
- 页脚许可证 `Apache-2.0` → `GPL-3.0`
- 移除 `guide/rss-source`、`dev/tts-rule` 两处侧栏项
- `logo: '/logo.png'` 要么补 `docs/public/logo.png`，要么删掉该行
- `socialLinks`/`editLink` 指回 `wyouwd1/legado-novel`

### 阶段 4：构建配置收口（1 个提交）

- 根 `build.gradle.kts`：删除只有 `compile_sdk_version`/`build_tool_version` 的 `buildscript {}` 块。
- `app/build.gradle.kts`：删除未使用的 `val appName`。
- `gradle/libs.versions.toml`：删除 3 个无用 `[versions]` 键：`biometric`、`kotlinxCoroutinesAndroid`、`kotlinxSerializationJson`。
- `baselineProfile/src/main/java/io/legado/baselineprofile/{StartupBenchmarks,BaselineProfileGenerator}.kt`：fallback `io.legato.kazusa` → `io.legadonovel`。
- `.gitignore` 追加：

```gitignore
/.planning/
/docs/book-source-verification/
*.jks
*.keystore
```

### 阶段 5：提交与发布

```bash
git add -A && git commit -m "chore(cleanup): 删除无效入口、死代码与失效 CI，归档一次性资料"
git push origin main
```

四个提交建议按阶段拆开，便于 `git revert` 单阶段回滚。不要在同一个 commit 里混入 docs 站点修正。

## 3. 收益

| 项 | 现状 | 清理后 |
|---|---|---|
| 跟踪文件 | 2,494 | 2,429（-65） |
| 跟踪字节 | 46.6 MB | ~35.1 MB（-11.5 MB） |
| `.git` | 187 MB | ~112 MB（repack，-75 MB） |
| 顶层文件 | 26 个 | 20 个 |
| 生效 CI workflow | 10 个（8 个永假/有害） | 2 个（`verify.yml`、`auto-release.yml` 的 build 段） |
| 对外文档页 | 63 页（含 22 页内部报告） | ~34 页 |

## 4. 回滚

- 每个阶段独立 commit：`git revert <sha>` 即可回滚对应阶段。
- `attic/**` 全部是 `git mv`，回滚后路径不变。
- `.git` repack 不改 SHA；如需恢复旧对象库，使用阶段 0.1 的备份。
- 发布签名密钥不在此次变更范围内；若 `legado.jks` 被证实为生产密钥，改为只从 HEAD 删除并轮换，不执行阶段 1 的其余动作。

## 5. 有效性验证（执行前必跑）

在 217 构建机（SDK 37、Gradle 缓存已热）上，对**清理后的副本**执行：

```bash
# 1) 复制并应用删除/移动清单（不碰工作区）
rsync -a --exclude .git --exclude build --exclude app/build /path/to/repo/ zx@217:/tmp/cleanup-verify/
# 2) 按 31-DELETE-LIST.txt / 31-MOVE-LIST.tsv 应用
# 3) 编译 + 资源链接 + 单测
./gradlew :app:assembleAppDebug :app:testAppDebugUnitTest --offline --no-daemon
# 4) Release 打包（验证 baseline profile 与 R8 规则）
./gradlew :app:assembleAppRelease --offline --no-daemon \
  -PRELEASE_STORE_FILE=/home/zx/keys/legado-novel.jks \
  -PRELEASE_STORE_PASSWORD=<...> -PRELEASE_KEY_ALIAS=legado-novel -PRELEASE_KEY_PASSWORD=<...>
```

验收线：两条命令均 `BUILD SUCCESSFUL`；`testAppDebugUnitTest` 无新增失败；`aapt2 dump badging` 的包名/版本与清理前一致。任何一条失败 → 该阶段退回，不放行。

**2026-10-22 实测结果**：基线单测 245 s（1,315 tests / 0 failures）；清理后打包+单测 222 s（1,315 tests / 0 failures）；Release 319 s（BUILD SUCCESSFUL，universal APK 21,818,886 B，签名 `CN=Legado Novel`）。首轮发现的 `PrivateConfigViewModelTest` 原有编译错误已单独修复（`fix(test)` 提交），修复后两次运行均通过。

## 6. 待人工决策（不计入本次自动执行）

1. `legado.jks` 是否为生产签名密钥（用已知口令导出证书，与已发布 APK 的 `apksigner verify --print-certs` 比对）。
2. WebService 设置链路是否彻底删除（`OtherSettings` 三字段 + 4 个测试断言）。
3. `privacyPolicy.md` / `disclaimer.md` 的 Firebase 表述改写。
4. `auto-release.yml` 是否保留发布链（当前 HapeLee 门永假）。
5. `gradle.properties` 6 个 `android.*` 过期 flag 是否删除（先 `--warning-mode all` 记录警告）。
6. `upstream-archive` 分支的 28 个独有提交是否需要长期保留（已打 tag）。
