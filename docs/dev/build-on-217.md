# 217 构建机

## 硬约束

本机（WSL）不跑 Gradle。`legado-novel` 的所有本地/代理侧构建、单测、lint、assemble 统一走
`scripts/build-217.sh`；直接执行 `./gradlew` / `gradlew.bat` 视为违规。GitHub Actions 仍在云端运行，
不受此脚本约束。

原因：2026-10-22 本机跑搜索性能最终门禁后，Gradle daemon 常驻 4 GiB 堆，`vmmemWSL` 峰值 10.8 GB，
把 32 GB Windows 主机推到 85%。217 是独立构建机，构建内存不再计入本机。

## 主机信息

| 项 | 值 |
|---|---|
| SSH 别名 | `test-env` |
| 地址 / hostname | 由本机 SSH 配置提供（不写入仓库） |
| SSH 登录用户 | 由本机 SSH 配置提供 |
| 构建用户 | 由 `REMOTE_BUILD_USER` 指定 |
| JDK | `/usr/lib/jvm/java-21-openjdk-amd64`（21.0.12） |
| Android SDK | `/home/zx/android-sdk`（platform 37.0/37.1/37.2） |
| 远端构建目录 | `/home/zx/builds/legado-novel` |
| Gradle | wrapper 9.8.0，依赖缓存已热 |
| 内存 | 15 GiB；脚本用本地 + 远端 `flock` 串行化构建，禁止并发 |

## 用法

```bash
# 快速编译
scripts/build-217.sh :app:compileAppDebugKotlin

# 主验证集
scripts/build-217.sh testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug \
  --continue --no-configuration-cache

# 使用 217 的热缓存、不访问网络
scripts/build-217.sh --offline :app:assembleAppDebug

# 查看帮助
scripts/build-217.sh --help
```

不传任务时默认执行 `:app:compileAppDebugKotlin`。除 `--offline` 外的参数都原样传给 Gradle。

## 同步规则

- `rsync -a --delete` 当前工作区到 217；**未提交改动也会同步**，以本机工作区为准。
- 排除 `.git/`、`.gradle/`、`.kotlin/`、`build/`、`app/build/`、`.planning/`、
  `docs/book-source-verification/`、`local.properties`、`*.jks`、`*.keystore`。
- 远端是构建镜像，不是 git 工作区；不要在 217 上提交、切分支或把它当源码主副本。
- 远端 `build/` 目录保留，增量构建缓存不会因 rsync 丢失。
- 每次构建前脚本会在远端生成 `local.properties`：`sdk.dir=/home/zx/android-sdk`。

## Release 签名

217 上已有 `$HOME/keys/legado-novel.jks`。签名参数不要写进仓库或 shell 历史，按现有凭据流程注入后
传给 Gradle：

```bash
scripts/build-217.sh assembleAppRelease \
  -PRELEASE_STORE_FILE="$HOME/keys/legado-novel.jks" \
  -PRELEASE_KEY_ALIAS=legado-novel \
  -PRELEASE_STORE_PASSWORD=<REDACTED> \
  -PRELEASE_KEY_PASSWORD=<REDACTED>
```

`app/build.gradle.kts` 只在检测到 `RELEASE_STORE_FILE` 时启用签名配置；缺参数会产出未签名包。

## 排障

1. `Permission denied` / `sudo: a password is required`：确认 `ssh test-env` 能免密登录，且
   `sudo -n -u "$REMOTE_BUILD_USER" true` 在 217 上返回 0。
2. 本机还有旧 Gradle daemon（`pgrep -af GradleDaemon` 能看到）：先执行一次
   `./gradlew --stop` 清理；之后所有构建只走 `scripts/build-217.sh`。
3. `local.properties` / SDK 找不到：脚本每次覆盖该文件；仍失败时检查
   `/home/zx/android-sdk/platforms/android-37.1` 是否存在。
4. `--offline` 报缺依赖：去掉 `--offline` 重跑一次，让 217 拉取并缓存新依赖。
5. 构建被锁住：本地锁在 `/tmp/legado-novel-build-217.lock`，远端锁在
   `/tmp/legado-novel-build-217.lock`；确认没有另一个构建在跑。
6. 217 内存不足：先 `ssh test-env 'free -h'`；不要并发跑多个 `build-217.sh`，脚本会串行等待。
