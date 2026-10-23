#!/usr/bin/env bash
# 在 217 构建机上执行 legado-novel 的所有 Gradle 构建/测试。
# 本机（WSL）禁止直接运行 ./gradlew；统一走这个脚本。
set -euo pipefail

HOST="${BUILD_HOST:-test-env}"
REMOTE_USER="${REMOTE_BUILD_USER:-zx}"
REMOTE_DIR="${REMOTE_DIR:-/home/zx/builds/legado-novel}"
REMOTE_SDK="${REMOTE_SDK:-/home/zx/android-sdk}"
REMOTE_JAVA_HOME="${REMOTE_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
REMOTE_GRADLE_JVMARGS="${REMOTE_GRADLE_JVMARGS:--Xmx3g -XX:MaxMetaspaceSize=768m}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

usage() {
  cat <<'EOF'
用法: scripts/build-217.sh [--offline] [--] <Gradle 参数...>

所有 legado-novel 的 Gradle 构建/测试都在远程 217 构建机执行。
脚本会把当前工作区 rsync 到远端构建目录，然后以远端构建用户
运行 ./gradlew --no-daemon。

选项:
  --offline   给 Gradle 传 --offline（使用 217 上已热的依赖缓存）
  -h, --help  显示本帮助

示例:
  scripts/build-217.sh :app:compileAppDebugKotlin
  scripts/build-217.sh testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache
  scripts/build-217.sh --offline :app:assembleAppRelease
EOF
}

OFFLINE=()
GRADLE_ARGS=()
while (($#)); do
  case "$1" in
    --offline) OFFLINE+=(--offline); shift ;;
    -h|--help) usage; exit 0 ;;
    --) shift; GRADLE_ARGS+=("$@"); break ;;
    *) GRADLE_ARGS+=("$1"); shift ;;
  esac
done

if ((${#GRADLE_ARGS[@]} == 0)); then
  GRADLE_ARGS=(:app:compileAppDebugKotlin)
fi

for cmd in ssh rsync flock; do
  command -v "$cmd" >/dev/null || { echo "缺少命令: $cmd" >&2; exit 1; }
done
[[ -x "$REPO_DIR/gradlew" ]] || { echo "找不到可执行的 gradlew: $REPO_DIR/gradlew" >&2; exit 1; }
[[ "$REMOTE_DIR" == /* ]] || { echo "REMOTE_DIR 必须是绝对路径: $REMOTE_DIR" >&2; exit 1; }
case "$REMOTE_DIR" in
  "/"|"/home"|"/home/$REMOTE_USER")
    echo "拒绝危险的 REMOTE_DIR: $REMOTE_DIR" >&2
    exit 1
    ;;
esac

# 同一台机器同时只跑一个 217 构建，避免 15 GiB 内存被并发构建打满。
LOCK_FILE="${TMPDIR:-/tmp}/legado-novel-build-217.lock"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "已有 217 构建在运行（锁: $LOCK_FILE），等待中..." >&2
  flock 9
fi

REMOTE_HOSTNAME="$(ssh -G "$HOST" | awk '/^hostname /{print $2; exit}')"
echo "==> 构建机: $HOST ($REMOTE_HOSTNAME)"
echo "==> 同步: $REPO_DIR -> $REMOTE_DIR"

ssh -T -o BatchMode=yes -o ConnectTimeout=10 "$HOST" \
  "sudo -n -u $REMOTE_USER mkdir -p '$REMOTE_DIR'"

# 远端是构建镜像，不是 git 工作区：.git/.gradle/build 都不同步；
# 远端 build/ 目录保留，保证增量构建缓存不丢。
if git -C "$REPO_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git -C "$REPO_DIR" diff --check
fi

rsync -a --delete --human-readable --info=stats1 \
  --exclude '.git/' \
  --exclude '.gradle/' \
  --exclude '.kotlin/' \
  --exclude '.idea/' \
  --exclude '.vscode/' \
  --exclude '.pi/' \
  --exclude '.planning/' \
  --exclude 'docs/book-source-verification/' \
  --exclude 'build/' \
  --exclude '**/build/' \
  --exclude 'local.properties' \
  --exclude '*.jks' \
  --exclude '*.keystore' \
  --exclude 'hs_err_pid*.log' \
  --exclude 'replay_pid*.log' \
  --exclude '*.hprof' \
  --rsync-path="sudo -u $REMOTE_USER rsync" \
  -e "ssh -o BatchMode=yes" \
  "$REPO_DIR/" "$HOST:$REMOTE_DIR/"

echo "==> 远端执行: ./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs=\"$REMOTE_GRADLE_JVMARGS\" ${OFFLINE[*]} ${GRADLE_ARGS[*]}"

# 用 stdin 把脚本交给远端 bash，避免 ssh -> sudo 多重引号丢失参数。
# 这里的变量需要在本地展开后再发给远端，SC2087 是预期行为。
# shellcheck disable=SC2087
ssh -T -o BatchMode=yes "$HOST" "sudo -n -u $REMOTE_USER bash -s" <<EOF
set -euo pipefail
exec 9>/tmp/legado-novel-build-217.lock
if ! flock -n 9; then
  echo "217 上已有构建在运行，等待远端锁..." >&2
  flock 9
fi
cd "$REMOTE_DIR"
printf '%s\n' "sdk.dir=$REMOTE_SDK" > local.properties
export HOME="/home/$REMOTE_USER"
export GRADLE_USER_HOME="\$HOME/.gradle"
export JAVA_HOME="$REMOTE_JAVA_HOME"
export ANDROID_HOME="$REMOTE_SDK"
export ANDROID_SDK_ROOT="$REMOTE_SDK"
export PATH="\$JAVA_HOME/bin:\$PATH"
./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs="$REMOTE_GRADLE_JVMARGS" ${OFFLINE[*]} $(printf '%q ' "${GRADLE_ARGS[@]}")
EOF

echo "==> 完成: $REMOTE_DIR"
