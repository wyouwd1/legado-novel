# Web 服务归档（D8）

本目录保存从 APK 中移除的内置 Web 服务源码，**不参与 Gradle 构建**。保留原仓库相对路径，恢复时可直接复制回 `app/src/main/java/io/legado/app/`。

## 归档内容

| 原路径 | 说明 |
|---|---|
| `app/src/main/java/io/legado/app/web/**` | Ktor HTTP/WebSocket 宿主、书源调试 socket、assets 读取 |
| `app/src/main/java/io/legado/app/api/controller/**` | 书架 / 书源 / 替换规则 HTTP API |
| `app/src/main/java/io/legado/app/api/ReturnData.kt` | API 返回模型 |
| `app/src/main/java/io/legado/app/api/ReaderProvider.kt` | ContentProvider（依赖 controller；迁出后由 `App.onCreate` 创建快捷方式） |
| `app/src/main/java/io/legado/app/service/WebService.kt` | 前台服务入口 |
| `app/src/main/java/io/legado/app/service/WebTileService.kt` | QS 磁贴 |

同步移除的内容：`app/src/main/assets/web/**`（Vue 构建产物）、`:modules/web` 仍在原位保留前端源码、Ktor server 依赖、Manifest service/provider、MyScreen 与设置页入口。

## 恢复步骤

1. 将本目录 `app/src/main/java/...` 覆盖回仓库同路径。
2. 从 `gradle/libs.versions.toml` 恢复 `ktor = "3.6.0"` 与 6 个 `ktor-*` library，并在 `app/build.gradle.kts` 加回 6 条 `implementation(libs.ktor...)`。
3. 从 `modules/web` 执行 `pnpm build`，把产物放回 `app/src/main/assets/web/`。
4. 恢复 `AndroidManifest.xml` 的 WebService/WebTileService/ReaderProvider 声明与 `ACCESS_LOCAL_NETWORK` 注释。
5. 恢复 `MainActivity` 的 WebService 自启、`MyScreen` 服务卡片、`OtherConfig` Web 开关、`App` 通知渠道与 `EventBus.WEB_SERVICE`。
6. `ShortCuts.buildShortCuts` 目前由 `App.onCreate` 调用；若恢复 `ReaderProvider`，可移回其 `onCreate`，避免重复创建。
