# W1-3 范围：AI / 知识库 / 配音角色分配（D5 + D6 + D4c）

> 侦察时间：本波开工前。对应 `DECISION-MAP.md` 的 D5（AI 全删）、D6（知识库/评论删）、D4c（配音删）。
> 本文件是 W1-3 的**单一事实来源**：删除清单、保留清单、接入点清单、验证方案。

## 〇、实际执行结果（2026-10-14，边界已收缩；提交 `7a14c1f8b`）

**计划 vs 实际**：本文件第一节论证的「AI + 知识库 + 配音整体删除」在执行中**主动收缩为「界面层删除」**。

原因（实测）：AI 底座被朗读链反向依赖（`AiReasoningLevel`、`AiProfileGateway`、`AiCastAssignUseCase`…），
且 `AiReasoningModeButton`/`AiThinkingCard` 被配音页与知识库页**共用**；整体删会连锁吞掉整个 W2，
而「保留契约」又需连带保留 `data/repository/ai/**` 与 4 个 AI 用例，等于底座整体保留。

- **实删 51 文件**：`ui/book/knowledge/**`(19)、`ui/config/ai/**`(15)、`ui/ai/**`(7)、
  `ui/main/bookshelf/autoGroup/**`(4)、`ui/book/read/sheet` 的 4 个 AI 面板、`ReadAi{Contract,Delegate}.kt`(2)。
- **保留到 W2**：第三节「保留清单」中的全部 AI 底座与朗读配音链（含 `ui/ai/chat/` 思考卡片三件套）。
- **波次结果**：编译 14 轮收敛 485→0；完整验证集 `BUILD SUCCESSFUL 5m31s`；单测 1571/0；
  架构门基线 74→73；lint 基线 425→330（纯删除）。详见 `VERIFICATION.md` §7.3。

> 下方第一~五节保留为**原始侦察存档**，供 W2 接手 AI 底座时直接参考（依赖关系、接入点已核实）。

---

## 一、为什么把三域合成一波（原计划）

原始 TASK-BOARD 把 W1-3（AI）、W1-4（知识库）分开。侦察后发现三者构成**交织簇**，分开删会互相制造断链：

```
AI 底座（AiReasoningLevel / AiProfileGateway / AiProfileRepository）
  ├── AI UI 功能（ui/ai、ui/config/ai、阅读页 AI 面板、autoGroup）
  ├── 朗读 TTS 域（ReadAloud*、help/readaloud/cast、PrepareChapterSpeechPlan）  ← W2
  │      └── 配音角色分配（BookVoiceCasting）                                    ← D4c
  │             └── 知识库（BookKnowledgeGateway、CharacterAvatar* 组件）        ← D6
  ├── 知识库（IdentifyBookCharactersUseCase）
  └── AI 章节摘要 / 净化 / 重写（ChapterSummarySheet、CleanSelectedText、AiText*Sheet）
```

关键依赖事实（均经 grep 验证）：

| 事实 | 证据 |
|---|---|
| `ui/config/ai/**` 的外部入边只有 `appModule` + `MainNavGraph` | 逐类名 grep 外部消费者 |
| `ui/ai/**` 的外部入边只有 `BookCharacterListScreen`（知识库）、`AiCastDialogSheet`（配音）、`AiText*Sheet`/`ChapterSummarySheet`（AI 自身）、`appModule` | 同上 |
| `data/repository/ai/**`（9 文件）**无任何包外消费者**，只被要删的 `AiTextRepositoryImpl` 引用 | 逐文件 grep |
| `AiProfileRepository` 不 import `data/repository/ai/**` 任何符号 | 其 import 列表 |
| `ui/book/knowledge/**`（21 文件 / 4,568 行）外部入边仅 `appModule`、`MainNavGraph`、`BookVoiceCasting*` | 逐目录 grep |
| `BookVoiceCasting` 横跨 15 个朗读文件（导航 / 菜单 / 效果转发 / DI） | grep 全仓 |

**择优结论**：合并为一波。`ReadBook*`、`Main*`、`appModule` 等热点文件被三域共同引用，分开删会把同一批热点文件编辑三遍（每遍都是一次破坏风险）；而编译错误驱动收敛已被 W1-2 证明可行（623 → 0，6 轮）。**减少热点文件触碰次数优先于减小单波体量。**

## 二、删除清单

### UI 层（59）

| 目标 | 数量 | 说明 |
|---|---|---|
| `ui/ai/**` | 10 | AI 聊天、推理按钮、任务结果面板 |
| `ui/config/ai/**` | 15 | AI 供应商/模型/提示词/摘要配置页 |
| `ui/book/knowledge/**` | 21 | 知识库（角色/事件/条目/头像） |
| `ui/book/read/ReadAi{Contract,Delegate}.kt` | 2 | 阅读页 AI 状态宿主 |
| `ui/book/read/sheet/{AiTextCleanSheet,AiTextRewriteSheet,AiRewritePresetConfigSheet,AiCastDialogSheet,ChapterSummarySheet}.kt` | 5 | 净化/重写/预设/配音弹窗/章节摘要 |
| `ui/main/bookshelf/autoGroup/**` | 3 | AI 自动分组 |
| `ui/book/readaloud/casting/BookVoiceCasting{Contract,Screen,ViewModel}.kt` | 3 | 配音角色分配页（D4c） |

### 领域层（15）

- `domain/gateway/Ai{Chat,Memory,Tool,Artifact,PromptPreset}Gateway.kt`（5）
- `domain/gateway/BookKnowledgeGateway.kt`（1）
- `domain/model/Ai{MessageParts,ModelRegistry}.kt`（2）
- `domain/usecase/{AiChatGenerationUseCase,AiToolAwareGenerationUseCase,AiTaskManager,AiTextFactoryUseCase}.kt`（4）
- `domain/usecase/{CleanSelectedTextUseCase,GenerateChapterSummaryUseCase,GenerateBookshelfAutoGroupPlanUseCase}.kt`（3）

### 数据层（16）

- `data/repository/ai/**`（9，整包无外部消费者）
- `data/repository/Ai{Chat,Memory,Artifact,PromptPreset,Tool}Repository.kt`（5）
- `data/repository/AiTextRepositoryImpl.kt`（1）
- `data/repository/BookKnowledgeRepository.kt`（1）

## 三、保留清单（每项标注去向，均非死代码）

| 保留项 | 谁还在用 | 清除波次 |
|---|---|---|
| `domain/model/AiModels.kt`（`AiReasoningLevel`/`AiTaskType`/`AiModelRegistry` 等） | 朗读链 `ReadAloudSettings`、`AiCastPresetStore`、`BaseReadAloudService` | W2 |
| `domain/gateway/AiProfileGateway.kt` + `data/repository/AiProfileRepository.kt` | `ReadAloudDelegate.getTaskPreset()`、`help/readaloud/cast/**` | W2 |
| `domain/usecase/{IdentifyBookCharactersUseCase,RefineSpeechWithAiUseCase,PrepareChapterSpeechPlanUseCase,ResolveLocalSpeakersUseCase}.kt` | 朗读链（`AiCastAssignUseCase`、`BaseReadAloudService`） | W2 |
| `domain/usecase/TranslateChapterUseCase.kt` | `model/translation/TranslationManager.kt`（翻译功能） | W3 |
| `help/readaloud/**`、`ui/book/readaloud/**`（除 BookVoiceCasting）、`service/BaseReadAloudService.kt` | 朗读 TTS 本体 | W2 |
| `data/entities/Ai*.kt`(7)、`data/dao/Ai*Dao.kt`(6)、`data/entities/BookKnowledge.kt`、`data/dao/BookKnowledgeDao.kt`、`CastCharacter*`、`AppDatabase` 内声明 | Room schema 一致性（D11） | W4 |
| `constant/PreferKey.kt` 的 AI/RSS 遗留键 | 备份读取兼容 | W4 |

## 四、接入点清单（需精确编辑）

### 导航与宿主

- `ui/main/MainNavGraph.kt`：删 knowledge 7 路由 entry、AI 5 路由 entry、配音 1 entry、对应 imports、`BookInfoEditScreen` 的 4 个知识库回调参数
- `ui/main/MainNavKey.kt`：删 `MainRouteBook{CharacterDetail,CharacterNetwork,CharacterList,KnowledgeList,KnowledgeDetail,EventList,EventDetail}`、`MainRouteBookVoiceCasting`、`MainRouteAiChat`、`MainRouteSettingsAi{ProviderEdit,ModelEdit,Summary,Prompt}`
- `ui/main/MainNavigator.kt`、`ui/main/MainActivity.kt`：删配音路由判定与跳转
- `ui/main/bookshelf/GroupManageSheet.kt`：删 AI 自动分组入口（`showAiAutoGroup` 状态 + Sheet + 菜单项）

### 阅读页

- `ui/book/read/ReadBookScreen.kt`：删 AI sheet 渲染（`AiTextCleanSheet`/`AiTextRewriteSheet`/`AiRewritePresetConfigSheet`/`AiCastDialogSheet`/`ChapterSummarySheet`）、`aiState` 参数、`ReadAiUiState` 引用
- `ui/book/read/ReadBookContract.kt`：删 `ReadBookSheet.Ai*`、AI 相关 Intent（`OpenAiCurrentChapterRewrite`、`OpenAiCastDialog`、`SetAi*ReasoningLevel`、`EditAiRewritePreset`、`RequestDeleteAiRewritePreset`）、`ReadBookEffect.OpenBookVoiceCasting`
- `ui/book/read/ReadBookViewModel.kt`：删 `aiHost`/`aiDelegate`/AI gateways 注入/AI intent 分支
- `ui/book/read/ReadBookController.kt`、`ReadBookRouteScreen.kt`：删 AI 效果转发
- `ui/book/read/ReadMenuActionDefs.kt`：删 2 个 AI 菜单项
- `ui/book/read/ReadAloudDelegate.kt`：删 `openBookVoiceCasting()`
- `ui/book/read/sheet/ReadAloudConfigContent.kt`：删配音入口按钮（保留 AI 预设显示，W2 处理）

### 书籍信息与朗读宿主

- `ui/book/info/{BookInfoEditScreen,BookInfoRouteScreen,BookInfoViewModel}.kt`：删知识库入口回调与 `BookKnowledgeGateway` 注入
- `ui/book/readaloud/ReadAloudPlayerMorphHost.kt`、`ui/book/readaloud/player/ReadAloudConfigIntentMapper.kt`：删配音跳转
- `ui/book/readaloud/casting/CastBubbleSheet.kt`：检查是否引用 `BookVoiceCasting*`（只应引用 `CastCharacter`，保留）

### DI 与资源

- `di/appModule.kt`：删 AI/knowledge/配音的 ViewModel、Gateway、Repository、UseCase 绑定与 imports
- `di/appDatabaseModule.kt`：**保留** `bookKnowledgeDao` / AI dao factory（W4）
- `constant/PreferKey.kt`：`aiRewritePresets` 留 W4（备份兼容）
- `res/values*/strings.xml`：删 4 语言中 AI/知识库/配音专用字符串
- 测试：knowledge/AI/autoGroup/配音相关测试随域删除，保留 AI 类型的朗读测试

## 五、验证方案（波次出口三件套）

1. `:app:compileAppDebugKotlin` → 0 错（预期多轮收敛，逐轮记录错误数）
2. `testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache` → BUILD SUCCESSFUL
3. 数字入 `VERIFICATION.md`：删除文件数 / 行数 / 单测数 / APK 体积，与基线对照

**执行纪律**（README §六）：删除只用整文件 `git rm`；接入点只用精确文本编辑；禁止按缩进配平的行扫描删除；不确定就 `git checkout --` 回滚重做。
