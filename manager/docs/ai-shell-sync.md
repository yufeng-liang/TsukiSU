# AI 助手双外壳同步清单

AI 助手有两套可切换的界面外壳，都由 `AiFeatureProviderImpl` 按 `miuix: Boolean` 选择：

| 外壳 | 入口分发 | 聊天页 | 主页卡片（含设置页） |
| --- | --- | --- | --- |
| Miuix | `AiFeatureProviderImpl.kt` | `AiChatMiuix.kt` | `AiHomeCardMiuix.kt` + `AiSettingsSectionsMiuix.kt` + `AiProviderEditDialogMiuix.kt` |
| Material 3 | 同上 | `AiChatMaterial.kt` | `AiHomeCardMaterial.kt` + `AiSearchSettingsSection.kt` + `AiToolsSettingsSection.kt` + `AiProviderEditDialog.kt` |

外壳外壳并存是既定决策：**任何一侧的改造都要同步到另一侧**，只对齐信息架构与语义，观感交给各自的组件库（M3 不照抄 Miuix 的边框、蓝底、压暗等细节）。

## 一、必须放到共享层的东西

逻辑与文案规则不许在两侧各写一份，统一放 `ai/src/main/java/com/sukisu/ultra/ai/AiSettingsLogic.kt`（全部 `internal`，同包直接调用）：

- `McpAuthMode` / `McpAuthInput` / `detectMcpAuth()` / `buildMcpHeaders()`：MCP 认证头的「方式 + 密钥」互转。
- `toggleSearchProvider()` / `reorderSearchProviders()`：联网搜索开关三态（关最后一个 → 关总闸；关着总闸点开某个 → 顺手开总闸）与排序。
- `nextNumberedName()` / `nextProviderName()`：新增条目一律带序号（提供商 1、会话 1……），按第一个未占用序号取名。
- `upsertProvider()` / `removeProvider()` / `fallbackProvider()`：提供商增删改与回落。
- `AiSystemPromptPreset` / `mergePromptPreset()`：自定义指令预设与幂等追加。
- `isValidMcpUrl()`（在 `AiToolsSettingsSection.kt`）、`parseHeaders()`（同文件）：URL 校验与请求头文本解析，两侧共用。
- `AiModelInfo.looksLikeImageModel()`（在 `AiHomeCardMaterial.kt`，已改 `internal`）。

新增这类规则时先写进共享层 + `ai/src/test/java/com/sukisu/ultra/ai/AiSettingsLogicTest.kt` 单测，再让两套外壳各调一次。

## 二、逐条同步清单

改动任一侧后，对着这张表把另一侧过一遍：

1. **提供商列表**：平铺行列表（当前项高亮 + ✓ + 「编辑」+ 删除图标，仅剩一个时不给删），底部整行「新增提供商」；不要退回下拉框 + 独立弹窗的旧形态。
2. **模型选择**：主模型、会话标题模型、生图模型三处都是「下拉 + 获取模型」；打开下拉即触发拉取，菜单底部可手填模型 ID；切换模型把推理档位重置为 `AiThinkingMode.AUTO`。
3. **生图模型位置**：在「高级 API 模式」开关**上方**，带一句说明（`ai_image_model_summary`）。
4. **会话标题**：模型下拉（含「跟随主模型」）+ 「标题生成默认关闭推理」说明；不提供推理档位选择（`titleEffort` 固定 `OFF`）。
5. **自定义指令**：独立分组（不塞在连接卡里），说明 + 预设 chips（风趣幽默 / 亲和力强 / 通俗易懂 / 专业严谨 / 简洁直接 / 先确认再执行）+ 多行输入；「清空 ×」和「放大编辑」都收在输入框内部右下角（Miuix 用 `TextField.trailingIcon`、M3 用 `OutlinedTextField.trailingIcon`），框外不再单独占一行，放大后编辑的是同一份草稿。
6. **联网搜索**：总开关 + 服务行（拖拽排序、每行开关、缺 Key 提示）；总闸关闭时整组压暗但可操作；空链与缺 Key 数量要明说。
7. **MCP 服务器**：认证方式三档（Bearer / X-API-Key / 自定义）+ 只填密钥的输入框（默认打码，右侧眼睛切换）+ 默认收起的「其他请求头」。禁止回到「一个多行请求头文本框」。
8. **顶栏图标**：清空聊天记录 `DeleteSweep`、会话 `History`、设置 `Tune`，两侧一致。
9. **下拉/选择框边框**：Miuix 用 1dp `outline` 描边，M3 用 `OutlinedTextField` 自带描边，观感不同但都要有边界感。
10. **文案改动**：中英两份 `strings.xml` 都要加，英文串不能出现裸单引号（AAPT 会报 `does not contain a valid string resource`）。
11. **主页「隐藏环境进行中」卡片**：和其它卡片同一套底——默认 `surfaceBright`、无描边，进度只体现在圆环与文案上（M3 在 `HomeMaterial.kt` 的 `HideEnvCard`，Miuix 在 `HomeMiuix.kt` 的 `HideEnvCardMiuix`）。
12. **输入框快捷条**：模型名（点击换模型）、推理强度 `Speed`、联网搜索 `Language`；生效中的图标用 primary 色，关闭时回落普通色。推理强度弹窗的滑杆要按档位画刻度点（Miuix `steps` + `showKeyPoints`）并在落档时给触感；档位名居中压在滑杆正上方，底部留 16dp，别让大字贴着轨道。弹窗里的「助手设置」带落点：`AiSettingsTarget`（Model / Search）+ `AiSettingsFocus` 一次性信使，设置页用 `bringIntoViewRequester` 滚到目标分组、描边 1.4s 后淡出，别把用户丢在页面顶部。
13. **新增图标**：`ai` 模块对 `material-icons-extended` 是 `compileOnly`，运行时图标来自 `app/libs/material-icons-custom.jar`（165 个 rounded 图标）。加图标前先 `unzip -l app/libs/material-icons-custom.jar | grep 图标名` 核对，否则编译通过、真机 `ClassNotFoundException`（踩过 `Bolt`、`Public`）。
14. **会话列表**：只列有内容的会话 + 当前会话（空会话在弹窗里堆成一片「新对话」没法认），每行右侧红色 `DeleteOutline` 删除按钮必须先弹二次确认；当前会话已经空着时点「新对话」不新建（`AiChatViewModel.newSession` 里守卫），删掉当前会话优先退到最近一个有内容的会话，一条不剩才新建。列表过滤规则统一走 `visibleSessions()`（在 `AiSettingsLogic.kt`）。
15. **MCP 添加服务器是两入口**：点「添加服务器」先出选择弹窗（`McpAddChoiceDialog*`，标题「添加 MCP 服务器」），两个整行可点入口——「导入 JSON 配置」和「手动填写」，底部取消。选导入进粘贴弹窗（`McpImportDialog*`），选手动进表单弹窗（`McpServerAddDialog*`，参数带 `prefill`）。三种界面由 `McpAddServerMode { Choose, Import, Form }` 驱动，统一在 `McpAddServerFlow*` 里 `when(mode)` 切换，不要再用「表单弹窗里塞一个 showImport 状态」的老结构。
    - 导入弹窗的输入框必须有 7 行的示例占位符（`ai_mcp_import_placeholder`），排版按真实配置的缩进写：`{` → `"mcpServers"` → 服务器名 → `"url"`，每级缩进 2 个空格。XML 里普通连续空格会被 aapt 折叠，缩进必须写成 `\u0020`；示例 URL 用短的 `https://example.com`，否则窄屏会把值折到下一行。
    - 高度要容得下整段占位符：Miuix `minLines = 7`、Material `minLines = 7`（`maxLines = 12`）。Material 的占位符 `Text` 用 `bodySmall`，正文输入字号不变；并且**不要给 M3 那个输入框加 label**——有 label 时占位符只在聚焦后才出现，空态会看到一片空框。
    - 解析统一走 `importMcpServers()`（在 `AiMcpImport.kt`）：兼容 `mcpServers` 包裹、单个服务器对象（`url` / `serverUrl` / `endpoint`）、名称到配置的裸映射；只收 HTTP 传输，`command` 型记进 `skipped` 并 toast 提示。解析出 1 台就切到表单并预填（用户还能改名补密钥），多台直接落库并关闭整个向导。

## 三、验收流程

1. `cd manager && export JAVA_HOME="E:/.jdks/ms-17.0.15" && ./gradlew :ai:testDebugUnitTest :app:assembleDebug --offline`——资源错误只有 `:app:assembleDebug` 会暴露。
2. `adb install -r app/build/outputs/apk/debug/TsukiSU_mod_*-debug.apk`（先 `export MSYS_NO_PATHCONV=1`；设备有多条 adb 连接时补 `-s` 序列号）。
3. 切到被改的那一侧，逐屏截图：主页卡片 → 助手设置（提供商 / 模型 / 自定义指令 / 联网搜索 / MCP）。
4. 再切到另一侧，同样逐屏过一遍，只核对语义与信息架构，不追像素级一致。
5. 截图放 `test-artifacts/screenshots/YYYY-MM-DD/场景/`，不提交。
