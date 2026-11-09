package git.ggwzz.agent.advisor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.agent.service.AgentToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 写操作确认 Advisor。
 *
 * <p>拦截所有 readOnly=false 的工具调用。在执行前通过 SSE 发送 confirm 事件给前端，
 * 等待用户确认后才放行。用户拒绝或超时则返回取消消息给 LLM。</p>
 *
 * <p>用法：在 ChatClient 调用时，将 SseEmitter 放入 adviseContext：</p>
 * <pre>
 * chatClient.prompt()
 *     .advisors(a -> a.param("sseEmitter", emitter))
 *     .user(message)
 *     .call()
 * </pre>
 *
 * <p>AgentController 通过 {@link #confirm(String, boolean)} 方法反馈用户确认结果。</p>
 */
@Slf4j
@Component
public class ConfirmationAdvisor implements BaseAdvisor {

    /** adviseContext 中 SseEmitter 的键名 */
    public static final String SSE_EMITTER_KEY = "sseEmitter";

    private static final long CONFIRM_TIMEOUT_SECONDS = 60;

    private final AgentToolRegistry agentToolRegistry;
    private final ObjectMapper objectMapper;

    /** 确认队列：confirmId -> 等待中的 Future */
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingConfirmations = new ConcurrentHashMap<>();

    public ConfirmationAdvisor(AgentToolRegistry agentToolRegistry, ObjectMapper objectMapper) {
        this.agentToolRegistry = agentToolRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        // 在 PermissionFilterAdvisor 之后执行，确保只处理已通过权限过滤的工具
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 从 adviseContext 获取 SseEmitter
        Object emitterObj = chatClientRequest.context().get(SSE_EMITTER_KEY);
        if (!(emitterObj instanceof SseEmitter sseEmitter)) {
            log.debug("未找到 SseEmitter，跳过写操作确认拦截");
            return chatClientRequest;
        }

        if (!(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions tcOptions)) {
            return chatClientRequest;
        }

        List<ToolCallback> allCallbacks = tcOptions.getToolCallbacks();
        if (allCallbacks == null || allCallbacks.isEmpty()) {
            return chatClientRequest;
        }

        // 将写操作的工具包装为带确认逻辑的代理
        List<ToolCallback> wrapped = allCallbacks.stream()
                .map(cb -> wrapWithConfirmation(cb, sseEmitter))
                .collect(Collectors.toList());

        tcOptions.setToolCallbacks(wrapped);
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    // ========================= 外部调用接口 =========================

    /**
     * 确认或拒绝写操作。由 AgentController 的 REST 端点调用。
     *
     * @param confirmId 确认 ID（由 SSE 事件返回给前端）
     * @param approved  true=放行，false=拒绝
     */
    public void confirm(String confirmId, boolean approved) {
        CompletableFuture<Boolean> future = pendingConfirmations.remove(confirmId);
        if (future != null) {
            future.complete(approved);
            log.debug("确认结果已处理: confirmId={}, approved={}", confirmId, approved);
        } else {
            log.warn("未找到待确认的请求（可能已超时）: confirmId={}", confirmId);
        }
    }

    /**
     * 查询当前等待确认的数量（用于监控）。
     */
    public int pendingCount() {
        return pendingConfirmations.size();
    }

    // ========================= 内部实现 =========================

    private ToolCallback wrapWithConfirmation(ToolCallback original, SseEmitter sseEmitter) {
        return new ConfirmingToolCallback(original, sseEmitter);
    }

    /**
     * 包装 ToolCallback，在写操作执行前插入确认流程。
     */
    private class ConfirmingToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final SseEmitter sseEmitter;

        ConfirmingToolCallback(ToolCallback delegate, SseEmitter sseEmitter) {
            this.delegate = delegate;
            this.sseEmitter = sseEmitter;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String functionInput) {
            return interceptIfNeeded(functionInput, null);
        }

        @Override
        public String call(String functionInput, ToolContext toolContext) {
            return interceptIfNeeded(functionInput, toolContext);
        }

        private String interceptIfNeeded(String functionInput, ToolContext toolContext) {
            String toolName = delegate.getToolDefinition().name();
            AgentToolRegistry.ToolMeta meta = agentToolRegistry.get(toolName);

            // 只读操作直接放行
            if (meta == null || meta.isReadOnly()) {
                return (toolContext != null) ? delegate.call(functionInput, toolContext) : delegate.call(functionInput);
            }

            // 写操作：需要确认
            return executeWithConfirmation(toolName, functionInput, toolContext, meta);
        }

        private String executeWithConfirmation(String toolName, String functionInput,
                                               ToolContext toolContext, AgentToolRegistry.ToolMeta meta) {
            String confirmId = UUID.randomUUID().toString();
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            pendingConfirmations.put(confirmId, future);

            try {
                // 1. 构建并发送 SSE confirm 事件
                Map<String, Object> event = buildConfirmEvent(confirmId, toolName, functionInput, meta);
                String eventData = objectMapper.writeValueAsString(event);
                sseEmitter.send(SseEmitter.event().name("confirm").data(eventData));
                log.info("已发送写操作确认请求: confirmId={}, tool={}", confirmId, toolName);

                // 2. 阻塞等待用户确认
                Boolean approved = future.get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (Boolean.TRUE.equals(approved)) {
                    log.info("用户确认执行: tool={}", toolName);
                    return (toolContext != null) ? delegate.call(functionInput, toolContext) : delegate.call(functionInput);
                } else {
                    log.info("用户拒绝执行: tool={}", toolName);
                    return "用户取消了该操作";
                }

            } catch (TimeoutException e) {
                pendingConfirmations.remove(confirmId);
                log.warn("确认超时（{}s）: tool={}, confirmId={}", CONFIRM_TIMEOUT_SECONDS, toolName, confirmId);
                return "用户确认超时，操作已取消";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pendingConfirmations.remove(confirmId);
                log.warn("确认等待被中断: tool={}", toolName);
                return "操作被中断";
            } catch (Exception e) {
                pendingConfirmations.remove(confirmId);
                log.error("确认流程异常: tool={}, confirmId={}", toolName, confirmId, e);
                return "确认流程异常，操作已取消: " + e.getMessage();
            }
        }
    }

    // ========================= 事件构建 =========================

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildConfirmEvent(String confirmId, String toolName,
                                                   String functionInput, AgentToolRegistry.ToolMeta meta) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("confirmId", confirmId);
        event.put("tool", toolName);

        // 解析参数
        Map<String, Object> args = Collections.emptyMap();
        try {
            if (functionInput != null && !functionInput.isBlank()) {
                args = objectMapper.readValue(functionInput, Map.class);
            }
        } catch (JsonProcessingException e) {
            log.debug("解析工具参数 JSON 失败: {}", e.getMessage());
        }
        event.put("args", args);

        // 可读描述
        event.put("description", buildDescription(toolName, args));

        // 风险等级
        event.put("riskLevel", determineRiskLevel(toolName));

        return event;
    }

    private String buildDescription(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "deleteEnterprise" -> "删除企业（ID=" + args.getOrDefault("enterpriseId", "?") + "）";
            case "outboundEnterprise" -> "将企业（ID=" + args.getOrDefault("enterpriseId", "?") + "）从在库变更为出库";
            case "inboundEnterprise" -> "将企业（ID=" + args.getOrDefault("enterpriseId", "?") + "）入库";
            case "createEnterprise" -> "创建新企业「" + args.getOrDefault("name", "?") + "」";
            case "createNode" -> "创建产业链节点「" + args.getOrDefault("nodeName", "?") + "」";
            case "linkEnterpriseToNode" -> "将企业（ID=" + args.getOrDefault("enterpriseId", "?")
                    + "）挂链到节点（ID=" + args.getOrDefault("nodeId", "?") + "）";
            case "unlinkEnterpriseFromNode" -> "将企业（ID=" + args.getOrDefault("enterpriseId", "?")
                    + "）从节点（ID=" + args.getOrDefault("nodeId", "?") + "）解除挂链";
            case "runSmartMatching" -> "运行智能挂链推荐（企业ID=" + args.getOrDefault("enterpriseId", "?")
                    + "，产业链ID=" + args.getOrDefault("chainId", "?") + "）";
            case "acceptMatchingResult" -> "采纳挂链推荐结果（ID=" + args.getOrDefault("resultId", "?") + "）";
            case "rejectMatchingResult" -> "拒绝挂链推荐结果（ID=" + args.getOrDefault("resultId", "?") + "）";
            case "createTag" -> "创建标签「" + args.getOrDefault("name", "?") + "」";
            case "bindTag" -> "为企业（ID=" + args.getOrDefault("enterpriseId", "?")
                    + "）绑定标签（ID=" + args.getOrDefault("tagId", "?") + "）";
            case "unbindTag" -> "为企业（ID=" + args.getOrDefault("enterpriseId", "?")
                    + "）解绑标签（ID=" + args.getOrDefault("tagId", "?") + "）";
            case "generateReport" -> "生成产业链健康分析报告（产业链ID=" + args.getOrDefault("chainId", "?") + "）";
            default -> "执行写操作: " + toolName;
        };
    }

    /**
     * 判断风险等级。
     * <ul>
     *   <li>delete 类操作 → high</li>
     *   <li>outbound / unlink / reject 类操作 → medium</li>
     *   <li>其他写操作 → low</li>
     * </ul>
     */
    private String determineRiskLevel(String toolName) {
        String lower = toolName.toLowerCase();
        if (lower.contains("delete")) {
            return "high";
        }
        if (lower.contains("outbound") || lower.contains("unlink") || lower.contains("reject")) {
            return "medium";
        }
        return "low";
    }
}
