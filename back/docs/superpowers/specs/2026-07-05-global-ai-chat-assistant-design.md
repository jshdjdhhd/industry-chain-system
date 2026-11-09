+# 全局 AI 智能聊天助手设计规格
+
+## 1. 背景
+
+当前 AI 对话功能嵌入在
+`src/views/matching/MatchingTask.vue` 的智能挂链单企业工作区中。
+前端通过 `/api/matching/ai-chat/stream` 发起 SSE 请求，后端
+`AiChatRequest` 强制要求 `enterpriseId` 和 `chainId`。
+
+本次改造将聊天能力从智能挂链页面拆出，改为登录后业务页面可用的
+全局悬浮助手。后端以
+`back` 为唯一修改基线。
+
+## 2. 已确认需求
+
+- 登录后的所有页面显示右下角悬浮球，但系统管理页面不显示。
+- 点击悬浮球后显示右下角浮层，不跳转路由。
+- 桌面端面板参考尺寸为 `420px × 680px`，距右侧和底部各 `24px`。
+- 小屏设备改为距四边 `12px` 的近全屏面板。
+- 企业和产业链选择器的文字与下拉 SVG 图标必须位于同一水平线并垂直居中。
+- 未选择企业和产业链时支持通用产业链问答。
+- 同时选择企业和产业链时支持上下文分析。
+- 支持多个独立对话窗口。
+- 同一窗口内切换企业或产业链时保留已有对话，从下一条消息起使用新上下文。
+- 对话记录刷新后仍保留在浏览器本地，并按登录用户隔离。
+- 删除智能挂链页面原有内嵌聊天区域，仅保留全局悬浮助手。
+
+## 3. 范围
+
+### 3.1 本次包含
+
+- 全局悬浮球和聊天面板。
+- 多会话创建、切换、删除与清空。
+- 企业、产业链可选上下文。
+- SSE 流式回复、失败提示和手动重试。
+- 按用户隔离的 `localStorage` 持久化。
+- 后端通用问答和上下文问答双模式。
+- 请求判空、组合参数校验、历史消息校验。
+- 前后端测试和接口文档同步。
+
+### 3.2 本次不包含
+
+- 服务端会话表或跨设备同步。
+- 会话分享、导出、搜索和手工重命名。
+- 语音输入、文件上传、图片理解。
+- 同时并行生成多个 AI 回复。
+- 新增聊天专用路由或侧栏菜单。
+
+## 4. 方案选择
+
+### 4.1 已选择方案
+
+采用挂载在 `AdminLayout.vue` 下的全局悬浮面板。
+
+相比右侧抽屉，该方案遮挡面积更小且更接近参考图；相比独立路由页面，
+用户不会离开当前业务上下文。系统管理路由通过
+`meta.hideAiAssistant = true` 显式关闭助手，避免依赖字符串判断。
+
+### 4.2 组件边界
+
+- `AdminLayout.vue`
+  - 只决定当前路由是否挂载助手。
+  - 不承担聊天业务状态。
+- `views/assistant/AiAssistantPanel.vue`
+  - 渲染悬浮球、面板、会话历史、上下文选择器、消息区和输入区。
+  - 只调用 Store action，不直接调用 Axios。
+- `stores/aiChat.ts`
+  - 管理面板开关、当前会话、多会话状态、流式状态和本地持久化。
+  - 按会话 ID 将异步分片写回正确窗口。
+- `api/aiChat.ts`
+  - 封装普通聊天和 SSE 聊天。
+  - 负责 SSE 行解析、完成去重和错误回调。
+- `types/domain.ts`
+  - 定义聊天请求、消息、上下文、会话和持久化结构。
+
+## 5. 前端界面设计
+
+### 5.1 关闭状态
+
+- 右下角显示直径约 `52px` 的渐变悬浮球。
+- 悬浮球 `z-index` 高于普通页面内容，低于全局确认弹窗。
+- 提供 `aria-label="打开 AI 助手"` 和键盘焦点样式。
+
+### 5.2 打开状态
+
+面板从上到下包含：
+
+1. 标题栏：当前会话标题、最小化、关闭。
+2. 工具栏：新建对话、历史会话。
+3. 上下文栏：企业选择器、产业链选择器。
+4. 欢迎区或消息列表。
+5. 多行输入框和发送按钮。
+
+选择器使用 Element Plus 自带 suffix SVG 图标。文字和图标置于同一
+`inline-flex` 容器，统一 `align-items: center`，禁止使用受字体基线影响的
+`⌄` 字符。
+
+### 5.3 响应式
+
+- `> 720px`：宽 `420px`，高
+  `min(680px, calc(100vh - 96px))`，`right: 24px`，`bottom: 24px`。
+- `<= 720px`：`inset: 12px`，宽高自适应，悬浮球仍位于右下角。
+- 消息区独立滚动，标题、上下文栏和输入区保持可见。
+
+## 6. 会话与本地持久化
+
+### 6.1 数据结构
+
+每个会话保存：
+
+- `id`
+- `title`
+- `enterpriseId`、`enterpriseName`
+- `chainId`、`chainName`
+- `messages`
+- `createdAt`、`updatedAt`
+- `streaming`
+- `failedRequest`
+
+消息至少包含：
+
+- `id`
+- `role`：`user` 或 `assistant`
+- `content`
+- `status`：`streaming`、`completed` 或 `failed`
+- `createdAt`
+
+### 6.2 存储规则
+
+- 存储键：`industry-chain:ai-chat:v1:<userId>`。
+- 不存储 Token、权限、模型密钥或其他认证信息。
+- 每个用户最多保存 20 个会话。
+- 每个会话最多保留最近 50 条消息。
+- 退出登录时清空 Store 内存，不删除该用户的本地记录。
+- 新用户登录后必须先清空旧用户内存，再按新 `userId` 恢复记录。
+- JSON 损坏时只移除当前用户对应的键，并显示一次错误提示。
+- `QuotaExceededError` 时保留当前内存状态，提示用户删除旧会话，不静默丢弃。
+
+### 6.3 上下文切换
+
+- 两个 ID 都为空：通用问答模式。
+- 两个 ID 都非空：上下文分析模式。
+- 只选择一个：前端提示“请同时选择企业和产业链”，禁用发送。
+- 切换上下文不清空窗口消息。
+- 下一次请求携带新上下文和当前窗口此前的有效历史消息。
+
+## 7. 后端接口设计
+
+接口路径和权限保持不变：
+
+- `POST /api/matching/ai-chat`
+- `POST /api/matching/ai-chat/stream`
+- 权限：`matching:task:create`
+
+请求体：
+
+```json
+{
+  "enterpriseId": 1,
+  "chainId": 2,
+  "message": "请分析这家企业的挂链风险",
+  "history": [
+    {
+      "role": "user",
+      "content": "上一条问题"
+    },
+    {
+      "role": "assistant",
+      "content": "上一条回答"
+    }
+  ]
+}
+```
+
+通用模式省略或传 `null` 的 `enterpriseId`、`chainId`。
+
+### 7.1 提示词分支
+
+- 通用模式：
+  - 系统角色为产业链知识助手。
+  - 不查询企业和链节点。
+  - 明确不能虚构平台内部企业数据。
+- 上下文模式：
+  - 查询企业画像和指定产业链节点。
+  - 沿用现有挂链分析上下文。
+  - 当前上下文置于系统提示词，历史消息随后加入。
+
+## 8. 请求判空与防御性校验
+
+Controller 的 Jakarta Validation 和 Service 的业务校验必须同时存在，
+不能只依赖前端或 Controller。
+
+| 输入情况 | 处理 |
+| --- | --- |
+| 请求体缺失或无法解析 | HTTP 400，统一消息“请求参数格式错误” |
+| `message` 为 `null`、空串或全空白 | HTTP 400，“消息内容不能为空” |
+| `message` 超过 4000 字符 | HTTP 400，“消息内容不能超过 4000 字符” |
+| 两个上下文 ID 均为 `null` | 合法，进入通用模式 |
+| 两个上下文 ID 均为正整数 | 合法，进入上下文模式 |
+| 仅一个上下文 ID 有值 | HTTP 400，“企业和产业链必须同时选择” |
+| 上下文 ID 为 0 或负数 | HTTP 400，“企业和产业链 ID 必须为正整数” |
+| `history` 为 `null` 或空数组 | 合法，按无历史处理 |
+| `history` 超过 50 条 | HTTP 400，“历史消息不能超过 50 条” |
+| 历史数组包含 `null` 项 | HTTP 400，“历史消息不能为空” |
+| 历史项 `role` 不是 `user/assistant` | HTTP 400，“历史消息角色不合法” |
+| 历史项 `content` 为空白或超过 4000 字符 | HTTP 400，并返回对应字段错误 |
+| 企业不存在 | HTTP 404，“企业不存在” |
+
+`AiChatRequest` 使用 `@NotBlank`、`@Size`、`@Positive`、
+`List<@Valid ChatMessage>` 等声明式校验；“两个 ID 同时为空或同时有值”
+由类级校验表达。`AiChatServiceImpl` 在构造提示词前再次执行同一组关键
+判空和组合校验，防止非 Controller 调用绕过规则。
+
+## 9. SSE 和错误处理
+
+- 前端只允许当前会话存在一个生成任务。
+- 新用户消息先写入会话，再创建空的 AI 消息。
+- 所有分片按固定会话 ID 追加，不能依赖“当前活动会话”。
+- `[DONE]` 和底层流结束共享幂等完成标志，只触发一次完成逻辑。
+- 网络或模型失败时保留用户消息，将 AI 消息标记为 `failed`。
+- “重试”重新发送失败请求，旧失败气泡不伪装成成功回复。
+- 关闭面板和切换路由不取消生成。
+- 后端日志记录完整异常，但 SSE 对外只返回通用错误信息。
+- AI Markdown 必须经过清洗后再传给 `v-html`，禁止直接渲染未清洗 HTML。
+
+## 10. 预计文件变更
+
+### 10.1 前端
+
+- 修改：`src/layouts/AdminLayout.vue`
+- 修改：`src/router/index.ts`
+- 新建：`src/views/assistant/AiAssistantPanel.vue`
+- 新建：`src/views/assistant/AiAssistantPanel.spec.ts`
+- 新建：`src/stores/aiChat.ts`
+- 新建：`src/stores/aiChat.spec.ts`
+- 新建：`src/api/aiChat.ts`
+- 新建：`src/api/aiChat.spec.ts`
+- 修改：`src/types/domain.ts`
+- 修改：`src/views/matching/MatchingTask.vue`
+- 修改：`src/views/matching/MatchingTask.spec.ts`
+- 修改：`src/layouts/AdminLayout.spec.ts`
+- 修改：`src/router/index.spec.ts`
+- 修改：`package.json`、`package-lock.json`，加入 Markdown 清洗依赖
+
+### 10.2 后端
+
+- 修改：`src/main/java/git/ggwzz/matching/dto/AiChatRequest.java`
+- 修改：`src/main/java/git/ggwzz/matching/service/impl/AiChatServiceImpl.java`
+- 新建或修改：`src/test/java/git/ggwzz/matching/service/AiChatServiceImplTest.java`
+- 修改：`src/test/java/git/ggwzz/matching/controller/MatchingControllerTest.java`
+- 修改：`docs/API-INTERFACE-REGISTRY.md`
+- 增补聊天接口 HTTP 回归用例文件；不把聊天用例错误地写入
+  `src/test/resources/http/chain.http`
+
+## 11. 测试方案
+
+### 11.1 前端
+
+- API：通用模式、上下文模式、空白消息、SSE 分片、完成去重、失败。
+- Store：多会话、上下文切换、按用户隔离、刷新恢复、损坏 JSON、容量限制。
+- 组件：悬浮球、面板开关、新建/切换/删除会话、选择器、发送、重试。
+- 布局：普通业务路由显示，两个系统管理路由隐藏。
+- 清理：智能挂链页不再包含聊天模板、状态、样式和 API 引用。
+- 校验：Vitest、`vue-tsc`、Vite 生产构建。
+
+### 11.2 后端
+
+- 两个 ID 均空时不调用企业和节点查询，使用通用提示词。
+- 两个 ID 都有值时构建企业和节点上下文。
+- 请求体、消息、历史项和组合上下文的所有空值与边界测试。
+- Service 直接调用同样拒绝空白消息、半选 ID、非正数 ID 和非法历史。
+- 企业不存在返回 404。
+- Controller 权限、普通 JSON 响应和 SSE 响应测试。
+- 执行定向测试后执行 `mvn test`。
+
+## 12. 验收标准
+
+- 除 `/system/manage`、`/system/operation-logs` 外，所有登录后页面显示悬浮球。
+- 打开后的面板位置、尺寸和布局符合已确认原型。
+- 选择器文字和 SVG 箭头保持同一水平线。
+- 通用问答和上下文问答均可正常流式返回。
+- 只选择一个上下文时前端不能发送，后端直接调用也返回 400。
+- 多会话互不串流；切换页面、关闭面板和刷新不会丢失已持久化记录。
+- 不同用户不能读取彼此的本地聊天记录。
+- 智能挂链页不再存在内嵌聊天区域。
+- 前后端测试、类型检查和构建全部通过。
+
+## 13. Git 约束
+
+本任务只生成设计规格和编码计划，不由 Codex 执行任何 Git 提交、推送或上传操作。
