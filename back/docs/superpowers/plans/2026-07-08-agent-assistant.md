# 智能操作助手实现计划

**目标：** 将现有 AI 问答助手升级为 AI Agent，Spring AI ChatClient + @Tool + 两层权限 + 写操作确认。

**架构：** Agent 与现有 matching 模块并行共存。新增 `git.ggwzz.agent` 包。前端改造确认卡片 + 权限 Tab。

**设计规格：** `docs/superpowers/specs/2026-07-08-agent-assistant-design.md`（所有代码细节见此文件）

---

## Phase 1：后端骨架

### 任务 1：依赖 + 配置

- [ ] `pom.xml`：添加 `<spring-ai.version>1.1.8</spring-ai.version>` 到 properties
- [ ] `pom.xml`：在 `<dependencies>` 前添加 `spring-ai-bom` dependencyManagement
- [ ] `pom.xml`：添加 `spring-ai-openai-spring-boot-starter` + `spring-ai-starter-model-chat-memory-repository-jdbc` 依赖
- [ ] `application.yml`：添加 `spring.ai.openai.*` 配置（api-key、base-url、model），复用现有 `LLM_API_KEY` 环境变量
- [ ] `application-dev.yml`：补充 api-key 降级链
- [ ] 验证：`mvn dependency:resolve` 通过
- [ ] commit: `deps: add Spring AI 1.1.8 dependencies`

### 任务 2：数据库迁移

- [ ] 创建 `V24__ai_tool_permission.sql`：见规格 §5.2 DDL
- [ ] 创建 `V25__spring_ai_chat_memory.sql`：`SPRING_AI_CHAT_MEMORY` 表（conversation_id, content, type, timestamp）
- [ ] commit: `db: add ai_tool_permission and chat_memory tables`

### 任务 3：权限层（Entity + Mapper + Service）

- [ ] 创建 `agent/entity/AiToolPermission.java`：@TableName("ai_tool_permission")，字段 id/userId/toolName/enabled/createdAt/updatedAt
- [ ] 创建 `agent/mapper/AiToolPermissionMapper.java`：extends BaseMapper
- [ ] 创建 `agent/service/AiPermissionService.java`：getUserOverrides(userId) → Map<String, Boolean>，updateUserOverrides()，listAll()
- [ ] commit: `feat(agent): add AiToolPermission entity, mapper, service`

### 任务 4：工具注册表

- [ ] 创建 `agent/service/AgentToolRegistry.java`：LinkedHashMap<String, ToolMeta>，构造函数中 register 全部 23 个工具（名称、权限码、readOnly、分组），见规格 §5.4 映射表
- [ ] commit: `feat(agent): add AgentToolRegistry with 23 tool metadata entries`

### 任务 5：Tool 类（6 个文件，~23 个工具方法）

所有 Tool 方法内部 try-catch，不抛异常。调用现有 Service，通过 `SecurityUtils.currentUserId()` 获取用户。

- [ ] 创建 `agent/tools/EnterpriseTools.java`：6 个方法（search/getDetail/create/inbound/outbound/delete），调用 `EnterpriseService`
- [ ] 创建 `agent/tools/ChainTools.java`：6 个方法（listChains/getTree/search/createNode/link/unlink），调用 `ChainService` + `EnterpriseNodeRelService`
- [ ] 创建 `agent/tools/MatchingTools.java`：3 个方法（runSmartMatching/accept/reject），调用 `MatchingService`
- [ ] 创建 `agent/tools/TagTools.java`：4 个方法（list/create/bindTag/unbindTag），调用 `TagService` + `EnterpriseService`
- [ ] 创建 `agent/tools/AnalysisTools.java`：3 个方法（getDiagnosis/generateReport/getReport），调用 `ChainDiagnosisTreeService` + `AnalysisReportService`
- [ ] 创建 `agent/tools/DashboardTools.java`：1 个方法（getOverview），调用 `DashboardService`
- [ ] commit: `feat(agent): add 6 Tool classes with 23 tool methods`

### 任务 6：Advisor（权限过滤 + 写操作确认）

- [ ] 创建 `agent/advisor/PermissionFilterAdvisor.java`：实现 Advisor 接口，从 SecurityContext 取 RBAC 权限 + AiPermissionService 取覆盖层，过滤 ToolCallback 列表
- [ ] 创建 `agent/advisor/ConfirmationAdvisor.java`：实现 Advisor 接口，检查工具的 readOnly 属性，写操作时生成 confirmId、发 SSE confirm 事件、等待前端 POST /api/agent/confirm（60s 超时）
- [ ] commit: `feat(agent): add PermissionFilterAdvisor and ConfirmationAdvisor`

### 任务 7：AgentService + AgentController

- [ ] 创建 `agent/dto/AgentChatRequest.java`：sessionId, message, context(enterpriseId, chainId)
- [ ] 创建 `agent/dto/AgentConfirmRequest.java`：sessionId, confirmId, approved
- [ ] 创建 `agent/dto/AiToolPermissionRequest.java`：userId, permissions[]
- [ ] 创建 `agent/vo/AgentToolVO.java`：toolName, description, readOnly, group, enabled
- [ ] 创建 `agent/vo/AiToolPermissionVO.java`：userId, username, permissions[]
- [ ] 创建 `agent/service/AgentService.java`：注入 ChatClient.Builder + ToolCallbackProvider + AiPermissionService + ChatMemory，构建 ChatClient（defaultSystem + MessageChatMemoryAdvisor + PermissionFilterAdvisor + ConfirmationAdvisor + defaultToolCallbacks），chatStream() 返回 Flux<ServerSentEvent>
- [ ] 创建 `agent/controller/AgentController.java`：
  - POST /api/agent/chat/stream (SSE) — 调用 agentService.chatStream()
  - POST /api/agent/confirm — 调用 ConfirmationAdvisor 的确认队列
  - GET /api/agent/tools — 返回当前用户可用工具列表
  - GET /api/agent/permissions — 返回用户的 AI 权限覆盖配置
  - PUT /api/agent/permissions — 批量更新
  - GET /api/agent/permissions/all — 管理员查看全部
- [ ] commit: `feat(agent): add AgentService and AgentController with SSE + permission APIs`

---

## Phase 2：前端改造

### 任务 8：Agent API + 类型定义

- [ ] 创建 `front/src/types/agent.ts`：AgentEvent 类型（text/tool_call/tool_result/confirm/error/done）、AgentTool、AiToolPermission 类型
- [ ] 创建 `front/src/api/agent.ts`：agentChatStream()（SSE，复用 aiChatStream 的 fetch 模式）、agentConfirm()、fetchAgentTools()、fetchAgentPermissions()、updateAgentPermissions()
- [ ] commit: `feat(front): add agent API and type definitions`

### 任务 9：改造 AiChatStore 支持 confirm 消息

- [ ] 修改 `front/src/stores/aiChat.ts`：
  - AiChatMessage 扩展：新增 `type` 字段（'text' | 'confirm'），confirm 类型增加 confirmId/tool/args/description/riskLevel/status(pending/confirmed/rejected/timeout)
  - 新增 sendMessageViaAgent() 方法：调用 agentChatStream 而非 aiChatStream
  - 新增 confirmAction() 方法：调用 agentConfirm API
  - SSE 解析改造：处理 event: text/tool_call/confirm/done 事件
- [ ] commit: `feat(front): extend aiChatStore for agent mode with confirm support`

### 任务 10：改造 AiAssistantPanel 渲染确认卡片

- [ ] 修改 `front/src/views/assistant/AiAssistantPanel.vue`：
  - 消息列表中新增 confirm 类型消息渲染：内嵌卡片（操作描述 + 确认/取消按钮 + 状态标签）
  - 确认卡片样式：⚠️ 图标 + 操作描述 + [取消] [确认执行] 按钮
  - 状态变更：confirmed → ✅ 已确认，rejected → ❌ 已取消，timeout → ⏰ 已超时
  - 60s 超时自动标记
- [ ] commit: `feat(front): add inline confirmation card in AiAssistantPanel`

---

## Phase 3：AI 权限管理 UI

### 任务 11：SystemManage 新增 AI 权限 Tab

- [ ] 修改 `front/src/views/system/SystemManage.vue`：
  - tabList 新增 `{ key: 'ai-permissions', label: 'AI 助手权限', icon: MagicStick }`
  - 新增 ai-permissions Tab 内容：用户选择器 + 工具权限列表（按分组折叠）+ 每项开关 + 快捷操作按钮
  - 调用 fetchAgentTools / fetchAgentPermissions / updateAgentPermissions
- [ ] commit: `feat(front): add AI permission management tab in SystemManage`

---

## Phase 4：联调 + 测试

### 任务 12：端到端联调

- [ ] 启动后端，验证 Spring AI ChatClient 能连通 MiMo API
- [ ] 验证 ChatMemory JDBC 持久化正常（对话记录入库）
- [ ] 验证两层权限过滤：RBAC 无权工具不出现，AI 覆盖层禁用后工具消失
- [ ] 验证写操作确认流程：SSE confirm 事件 → 前端卡片 → 确认 → 执行
- [ ] 验证确认超时：60s 无响应自动取消
- [ ] commit: `test: verify agent end-to-end integration`

### 任务 13：Prompt 调优

- [ ] 测试常见场景：搜索企业、挂链、出库、创建节点、生成报告
- [ ] 调整 System Prompt 中的行为规则和工具描述
- [ ] 验证中文工具描述对 MiMo 的效果
- [ ] commit: `docs(agent): tune system prompt and tool descriptions`
