# 智能操作助手设计规格

> 将现有 AI 问答助手升级为具备系统操作能力的 AI Agent，通过 Spring AI + 工具调用实现，两层权限控制确保安全。

## 1. 目标

用户通过自然语言与 AI 对话，Agent 自动调用系统工具完成企业管理、产业链操作、智能挂链等任务。所有写操作需前端二次确认，权限两层控制（RBAC + AI 覆盖层）。

## 2. 核心决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| LLM 接入 | 并行共存：Agent 用 Spring AI ChatClient，matching 模块保留自研 LlmClient | 风险隔离，Agent 独立交付 |
| 工具范围 | 全量 ~20 个工具，覆盖 6 类操作 | 用户要求完整覆盖 |
| 权限模型 | 两层：RBAC 权限 + AI 覆盖层（前端可独立禁用） | 用户可精细控制 AI 能力 |
| 上下文模式 | 混合：用户可手动指定，也可不指定让 Agent 自己查 | 灵活 |
| 危险操作 | 系统级自动拦截，所有写操作前端内嵌确认卡片 | 不依赖 LLM 判断 |
| 对话持久化 | 后端 JDBC（JdbcChatMemoryRepository） | 跨设备、Agent 多轮调用不丢失 |
| 工具注解 | @Tool（进程内），未来可平滑升级 @McpTool（协议暴露） | 降低当前复杂度 |

## 3. 整体架构

```
┌─────────────────────────────────────────────────────┐
│                    前端 (Vue 3)                      │
│  AiAssistantPanel (改造)                             │
│    ├── SSE 事件解析（text / tool_call / confirm / done）│
│    ├── 内嵌确认卡片（写操作）                           │
│    └── 消息模型扩展（confirm 消息类型）                 │
│  AI权限管理页（新增 Tab in SystemManage）               │
│    └── 按用户开关各工具的AI权限                         │
└──────────────┬──────────────────────────┬────────────┘
               │ SSE stream              │ REST
               ▼                         ▼
┌─────────────────────────────────────────────────────┐
│                  后端 (Spring Boot 3.5.15)            │
│                                                      │
│  AgentController (新增)                              │
│    POST /api/agent/chat/stream  (SSE)                │
│    POST /api/agent/confirm      (确认/取消写操作)      │
│    GET  /api/agent/tools        (当前用户可用工具)     │
│    GET/PUT /api/agent/permissions (AI权限覆盖层)      │
│                                                      │
│  AgentService (新增)                                  │
│    ├── ChatClient 配置（MiMo via OpenAI 兼容）         │
│    ├── SystemPrompt 组装                              │
│    ├── MessageChatMemoryAdvisor（JDBC 持久化）         │
│    ├── PermissionFilterAdvisor（两层权限过滤）          │
│    └── ConfirmationAdvisor（写操作自动拦截）            │
│                                                      │
│  Tool 层 (@Tool 注解，新增)                            │
│    ├── EnterpriseTools  (~6个)                        │
│    ├── ChainTools       (~5个)                        │
│    ├── MatchingTools    (~3个)                        │
│    ├── TagTools         (~4个)                        │
│    ├── AnalysisTools    (~3个)                        │
│    └── DashboardTools   (~1个)                        │
│                                                      │
│  AI 权限层 (新增)                                      │
│    ├── ai_tool_permission 表                          │
│    └── AiPermissionService                            │
│                                                      │
│  现有模块（不动）                                       │
│    ├── MatchingService + LlmClient（保留自研）          │
│    ├── EnterpriseService / ChainService / ...          │
│    └── Spring Security RBAC                           │
└─────────────────────────────────────────────────────┘
```

## 4. Tool 层设计

每个 Tool 是 `@Tool` 注解的方法，底层调用现有 Service，不重复业务逻辑。通过 `SecurityUtils.currentUserId()` 获取当前用户。

### 4.1 EnterpriseTools

```java
@Component
public class EnterpriseTools {
    @Tool(description = "根据关键词搜索企业，返回企业名称、状态、主营产品", readOnly = true)
    List<EnterpriseSummary> searchEnterprises(String keyword);

    @Tool(description = "查询企业详细信息，包括标签、关联的产业链节点", readOnly = true)
    EnterpriseDetail getEnterpriseDetail(Long enterpriseId);

    @Tool(description = "创建新企业，状态为未入库")
    String createEnterprise(String name, String creditCode, String legalPerson,
                           String mainProducts, String address);

    @Tool(description = "将未入库企业变更为在库状态")
    String inboundEnterprise(Long enterpriseId);

    @Tool(description = "将在库企业变更为出库状态")
    String outboundEnterprise(Long enterpriseId);

    @Tool(description = "删除未入库企业，仅限未入库状态")
    String deleteEnterprise(Long enterpriseId);
}
```

### 4.2 ChainTools

```java
@Component
public class ChainTools {
    @Tool(description = "查询所有产业链列表", readOnly = true)
    List<ChainSummary> listChains();

    @Tool(description = "查询指定产业链的完整节点树结构", readOnly = true)
    ChainNodeTree getChainNodeTree(Long chainId);

    @Tool(description = "根据关键词搜索产业链节点", readOnly = true)
    List<NodeSearchResult> searchNodes(String keyword, Long chainId);

    @Tool(description = "在指定父节点下创建新的产业链节点")
    String createNode(Long chainId, Long parentId, String nodeName, String keywords, String definition);

    @Tool(description = "将企业手动关联到产业链节点（挂链）")
    String linkEnterpriseToNode(Long enterpriseId, Long nodeId);

    @Tool(description = "解除企业与产业链节点的关联（解挂）")
    String unlinkEnterpriseFromNode(Long enterpriseId, Long nodeId);
}
```

### 4.3 MatchingTools

```java
@Component
public class MatchingTools {
    @Tool(description = "对指定企业发起AI智能挂链推荐，返回候选节点及置信度")
    String runSmartMatching(Long enterpriseId, Long chainId);

    @Tool(description = "接受智能挂链推荐结果，正式创建企业-节点关联")
    String acceptMatchingResult(Long resultId);

    @Tool(description = "拒绝智能挂链推荐结果")
    String rejectMatchingResult(Long resultId, String reason);
}
```

### 4.4 TagTools / AnalysisTools / DashboardTools

```java
@Component
public class TagTools {
    @Tool(description = "查询标签列表，可按类型筛选", readOnly = true)
    List<TagInfo> listTags(String type);
    @Tool(description = "创建新标签")
    String createTag(String name, String type, String color, String description);
    @Tool(description = "给企业绑定标签")
    String bindTag(Long enterpriseId, Long tagId);
    @Tool(description = "解除企业标签绑定")
    String unbindTag(Long enterpriseId, Long tagId);
}

@Component
public class AnalysisTools {
    @Tool(description = "对指定产业链生成健康分析报告")
    String generateReport(Long chainId, String scopeType, List<Long> nodeIds);
    @Tool(description = "查询产业链诊断树，含各节点健康评分", readOnly = true)
    List<DiagnosisNode> getDiagnosis(Long chainId);
    @Tool(description = "查询报告详情", readOnly = true)
    ReportDetail getReport(Long reportId);
}

@Component
public class DashboardTools {
    @Tool(description = "获取数据看板概览，包括企业统计、挂链统计", readOnly = true)
    DashboardOverview getOverview();
}
```

### 4.5 Tool 方法错误处理

Tool 方法内部 try-catch，不抛异常给框架：

```java
@Tool(description = "创建新企业")
String createEnterprise(...) {
    try {
        Long id = enterpriseService.create(...);
        return "成功，企业ID: " + id;
    } catch (BusinessException e) {
        return "操作失败: " + e.getMessage();
    }
}
```

## 5. 两层权限模型

### 5.1 过滤流程

```
用户发起对话
    │
    ▼
第一层：RBAC 权限过滤
  从 SecurityContext 获取用户权限集
  遍历所有 @Tool，比对工具所需权限码
  用户无权的工具直接移除，不传给 LLM
    │
    ▼
第二层：AI 覆盖层过滤
  查询 ai_tool_permission 表
  enabled=0 的工具再次移除
    │
    ▼
最终可用工具集（传给 LLM）
```

### 5.2 数据库表

```sql
CREATE TABLE ai_tool_permission (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    tool_name       VARCHAR(100) NOT NULL COMMENT '工具方法名',
    enabled         TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=允许 0=禁止',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_tool (user_id, tool_name)
) COMMENT='AI助手工具权限覆盖层';
```

### 5.3 默认行为

- 表里无记录 → 跟随 RBAC（用户有权限就允许）
- `enabled=0` → 用户主动禁用，即使有 RBAC 权限也不可用
- `enabled=1` → 显式允许（与默认行为一致，但记录存在）

### 5.4 工具与权限码映射

| 工具 | 所需 RBAC 权限 | 类型 |
|---|---|---|
| searchEnterprises | enterprise:enterprise:query | 只读 |
| getEnterpriseDetail | enterprise:enterprise:query | 只读 |
| createEnterprise | enterprise:enterprise:create | 写入 |
| inboundEnterprise | enterprise:enterprise:inbound | 写入 |
| outboundEnterprise | enterprise:enterprise:outbound | 写入 |
| deleteEnterprise | enterprise:enterprise:delete | 删除 |
| listChains | chain:chain:query | 只读 |
| getChainNodeTree | chain:node:query | 只读 |
| searchNodes | chain:node:query | 只读 |
| createNode | chain:node:create | 写入 |
| linkEnterpriseToNode | chain:node:link-enterprise | 写入 |
| unlinkEnterpriseFromNode | chain:node:unlink-enterprise | 写入 |
| runSmartMatching | matching:task:create | 写入 |
| acceptMatchingResult | matching:result:accept | 写入 |
| rejectMatchingResult | matching:result:reject | 写入 |
| listTags | enterprise:tag:query | 只读 |
| createTag | enterprise:tag:create | 写入 |
| bindTag | enterprise:tag:bind | 写入 |
| unbindTag | enterprise:tag:unbind | 写入 |
| generateReport | analysis:report:create | 写入 |
| getDiagnosis | analysis:diagnosis:query | 只读 |
| getReport | analysis:report:query | 只读 |
| getOverview | (任意已认证用户) | 只读 |

### 5.5 PermissionFilterAdvisor

```java
public class PermissionFilterAdvisor implements Advisor {
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallNext chain) {
        // 1. 获取当前用户 RBAC 权限
        Set<String> rbacPermissions = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        // 2. 查询 AI 覆盖层
        Long userId = SecurityUtils.currentUserId();
        Map<String, Boolean> aiOverrides = permissionService.getUserOverrides(userId);

        // 3. 过滤工具回调，只保留允许的
        // 通过修改 request 的 toolCallbacks 实现
        ...
    }
}
```

## 6. 写操作确认机制（系统级自动拦截）

### 6.1 设计原则

- **LLM 不感知确认机制**，正常调用工具
- **ConfirmationAdvisor 在工具执行前自动拦截**，基于 `@Tool(readOnly)` 判断
- readOnly=true → 直接放行
- 无 readOnly 标记（默认 false）→ 自动发 confirm 事件，暂停执行

### 6.2 SSE 事件流示例

```
→ 用户: "帮我把XX企业出库"

← event: text      "好的，我来帮你处理。先查一下这家企业..."
← event: tool_call  { tool: "searchEnterprises", args: {keyword: "XX"} }
← event: tool_result { ... 企业信息 ... }
← event: text       "找到了 XX 企业（ID:42），当前状态为在库。出库操作将把该企业移出资源池。"
← event: confirm    {
                      confirmId: "c_abc123",
                      tool: "outboundEnterprise",
                      args: { enterpriseId: 42 },
                      description: "将「XX科技有限公司」从在库变更为出库",
                      riskLevel: "medium"
                    }
  ← 此时 SSE 暂停，等待前端确认

→ 用户点击 [确认执行]
→ POST /api/agent/confirm { sessionId, confirmId: "c_abc123", approved: true }

← event: tool_call  { tool: "outboundEnterprise", args: {enterpriseId: 42} }
← event: tool_result { success: true }
← event: text       "已完成，XX科技有限公司已出库。"
← event: done
```

### 6.3 前端内嵌确认卡片

在 AiAssistantPanel 消息列表中，confirm 事件渲染为内嵌卡片：

```
┌─────────────────────────────────────────┐
│ ⚠️ 请求操作：企业出库                      │
│                                          │
│ 目标：XX科技有限公司                        │
│ 操作：在库 → 出库                          │
│                                          │
│    [ 取消 ]        [ 确认执行 ]            │
└─────────────────────────────────────────┘
```

- 确认/取消后卡片状态变更（✅ 已确认 / ❌ 已取消 / ⏰ 已超时）
- 超时 60s 自动取消
- 不使用额外弹窗，内嵌在对话消息流中

### 6.4 ConfirmationAdvisor 伪逻辑

```java
public class ConfirmationAdvisor implements Advisor {
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest request, CallNext chain) {
        // 检查是否是工具执行阶段
        // 遍历待执行的工具调用
        for (ToolCall toolCall : toolCalls) {
            ToolCallback callback = findCallback(toolCall.name());
            if (callback.getToolDefinition().isReadOnly()) {
                // 只读，直接放行
                continue;
            }
            // 写操作：生成 confirmId，发 confirm 事件给前端，暂停
            String confirmId = generateConfirmId();
            emitConfirmEvent(confirmId, toolCall);
            // 等待前端 POST /api/agent/confirm
            boolean approved = waitForConfirmation(confirmId, 60, SECONDS);
            if (!approved) {
                // 取消或超时，返回取消信息给 LLM
                return "用户取消了该操作";
            }
        }
        // 全部确认，继续执行
        return chain.nextAroundCall(request);
    }
}
```

## 7. AgentService 编排

```java
@Service
public class AgentService {

    private final ChatClient chatClient;

    public AgentService(ChatClient.Builder builder,
                        ToolCallbackProvider toolProvider,
                        AiPermissionService permissionService,
                        ChatMemory chatMemory) {
        this.chatClient = builder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .defaultAdvisors(new PermissionFilterAdvisor(permissionService))
            .defaultAdvisors(new ConfirmationAdvisor())
            .defaultToolCallbacks(toolProvider)
            .build();
    }

    public Flux<AgentEvent> chatStream(Long userId, String sessionId, String message,
                                        AgentContext context) {
        // 1. 注入上下文到 system prompt（如果有）
        // 2. 调用 ChatClient
        return chatClient.prompt()
            .user(message)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .stream()
            .content()
            .map(text -> new AgentEvent("text", text));
    }
}
```

### Advisor 链执行顺序

```
① MessageChatMemoryAdvisor — 从 JDBC 加载历史
② PermissionFilterAdvisor — 过滤工具
③ ChatClient — LLM 推理，决定调用哪些工具
④ ConfirmationAdvisor — 拦截写操作
⑤ 工具执行 → 结果返回 LLM
⑥ MessageChatMemoryAdvisor — 存入 JDBC
```

## 8. System Prompt

```
你是产业链管理系统的 AI 操作助手。你可以通过调用工具帮助用户完成企业管理、
产业链操作、智能挂链等任务。

## 行为规则
- 查询操作直接执行，写操作系统会自动要求用户确认
- 执行写操作前，先向用户说明你准备做什么
- 如果用户描述模糊，先用搜索工具确认目标，不要猜测
- 每次操作后告知用户结果
- 拒绝执行超出你工具范围的请求

## 当前用户信息
用户名：{username}
角色：{roles}

## 可用工具
{按类分组的工具描述，由 PermissionFilterAdvisor 动态注入}

## 上下文（如果用户指定了）
当前企业：{enterpriseName}（ID:{enterpriseId}）
当前产业链：{chainName}（ID:{chainId}）
```

## 9. API 设计

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/agent/chat/stream` | SSE 流式对话，入参 `{ sessionId, message, context? }` |
| POST | `/api/agent/confirm` | 确认/取消写操作，入参 `{ sessionId, confirmId, approved }` |
| GET | `/api/agent/tools` | 获取当前用户可用工具列表 |
| GET | `/api/agent/permissions` | 获取当前用户的 AI 工具权限覆盖配置 |
| PUT | `/api/agent/permissions` | 批量更新 AI 工具权限 |
| GET | `/api/agent/permissions/all` | 管理员：查看所有用户的 AI 权限 |

所有接口走现有 JWT 认证 + `@PreAuthorize`。

## 10. 前端改造

### 改造文件

| 文件 | 改动 |
|---|---|
| `src/api/aiChat.ts` 或新增 `src/api/agent.ts` | Agent 相关 API 封装 |
| `src/stores/aiChat.ts` | 消息模型扩展：confirm 消息类型 |
| `src/views/assistant/AiAssistantPanel.vue` | SSE 解析改造 + 确认卡片渲染 |
| `src/views/system/SystemManage.vue` | 新增"AI 助手权限" Tab |

### SSE 事件类型

```typescript
type AgentEvent =
  | { type: 'text'; content: string }
  | { type: 'tool_call'; tool: string; args: any }
  | { type: 'tool_result'; tool: string; result: any }
  | { type: 'confirm'; confirmId: string; tool: string;
      args: any; description: string; riskLevel: 'low' | 'medium' | 'high' }
  | { type: 'error'; message: string }
  | { type: 'done' }
```

### 不改动的部分

- 现有 `/api/matching/ai-chat` 和 `/api/matching/ai-chat/stream` 保留
- 原有"智能问答助手"功能不受影响
- Agent 是新增端点，两套并行

## 11. 错误处理

| 场景 | 处理 |
|---|---|
| 工具执行失败 | Tool 方法内部 try-catch，返回错误字符串给 LLM |
| LLM 单次调用超时 | 60s 超时，前端提示 |
| 整个 Agent 会话超时 | SseEmitter 300s，超时关闭连接 |
| 多轮工具调用循环 | 最大 10 轮，超过强制结束 |
| 确认超时 | 60s 未响应自动取消 |
| 并发会话 | 每用户同时只允许一个 Agent 会话 |
| 权限变更 | 下次对话生效，进行中的会话用快照 |

## 12. 依赖变更

```xml
<!-- 新增 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>

<!-- BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.8</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 13. 实现分期

### Phase 1：后端骨架
- 依赖 + 配置（spring.ai.openai.* → MiMo）
- 6 个 Tool 类（~22 个工具方法）
- ai_tool_permission 表 + AiPermissionService
- PermissionFilterAdvisor + ConfirmationAdvisor
- AgentService + AgentController
- chat_memory 表（Spring AI 自动管理）

### Phase 2：前端改造
- agent.ts API 封装
- useAiChatStore 扩展 confirm 消息类型
- AiAssistantPanel SSE 解析 + 确认卡片

### Phase 3：AI 权限管理 UI
- SystemManage.vue 新增 Tab
- 工具列表 + 开关 + 快捷操作

### Phase 4：联调 + 优化
- 端到端联调
- Prompt 调优
- 边界测试

## 14. 未来扩展

- **MCP Server 暴露**：`@Tool` → `@McpTool`，加 `spring-ai-starter-mcp-server-webmvc`，零成本对外暴露
- **Matching 模块迁移**：后续将自研 LlmClient 迁移到 Spring AI ChatClient
- **动态工具发现**：工具数量增长后引入 tool-search-tool
- **多轮确认合并**：多个写操作合并为一次确认
