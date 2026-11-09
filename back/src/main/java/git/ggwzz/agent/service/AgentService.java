package git.ggwzz.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.agent.advisor.ConfirmationAdvisor;
import git.ggwzz.agent.advisor.PermissionFilterAdvisor;
import git.ggwzz.agent.dto.AgentChatRequest;
import git.ggwzz.security.service.SecurityUser;
import git.ggwzz.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import git.ggwzz.agent.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Agent 对话服务。
 *
 * <p>构建 ChatClient，处理 SSE 流式对话，通过 SseEmitter 发送结构化事件。</p>
 */
@Slf4j
@Service
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            你是产业链管理系统的 AI 操作助手。你可以通过调用工具帮助用户完成企业管理、产业链操作、智能挂链等任务。

            ## 行为规则
            - 查询操作直接执行，写操作系统会自动要求用户确认
            - 执行写操作前，先向用户说明你准备做什么
            - 如果用户描述模糊，先用搜索工具确认目标，不要猜测
            - 每次操作后告知用户结果
            - 如果用户请求的操作需要某个工具，但该工具不在你的可用工具列表中，说明该工具的 AI 调用权限已被关闭，请提示用户在对话框上方的「助手权限管理」中调整相关权限
            - 拒绝执行超出你工具范围的请求

            ## 当前用户信息
            用户名：{username}
            角色：{roles}
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public AgentService(ChatClient.Builder builder,
                        ToolCallbackProvider toolCallbackProvider,
                        AiPermissionService aiPermissionService,
                        AgentToolRegistry agentToolRegistry,
                        ChatMemory chatMemory,
                        PermissionFilterAdvisor permissionFilterAdvisor,
                        ConfirmationAdvisor confirmationAdvisor,
                        ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        Advisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(memoryAdvisor)
                .defaultAdvisors(permissionFilterAdvisor)
                .defaultAdvisors(confirmationAdvisor)
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    /**
     * SSE 流式对话。
     *
     * @param request 对话请求
     * @param emitter SSE 发射器
     */
    public void chatStream(AgentChatRequest request, SseEmitter emitter) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();

        // 组装 system prompt：注入用户信息和上下文
        String systemPrompt = buildSystemPrompt();

        String userMessage = message;
        if (request.getContext() != null) {
            AgentChatRequest.AgentContext ctx = request.getContext();
            StringBuilder sb = new StringBuilder(message);
            sb.append("\n\n[上下文信息]");
            if (ctx.getEnterpriseId() != null) {
                sb.append("\n当前企业ID：").append(ctx.getEnterpriseId());
            }
            if (ctx.getChainId() != null) {
                sb.append("\n当前产业链ID：").append(ctx.getChainId());
            }
            userMessage = sb.toString();
        }

        final String finalUserMessage = userMessage;

        // 捕获 SecurityContext，传递到异步线程（ForkJoinPool 不继承 SecurityContext）
        final var securityContext = SecurityContextHolder.getContext();

        // 异步执行，SSE 才能正确建立
        CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                ChatResponse chatResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user(finalUserMessage)
                        .advisors(a -> a
                                .param(ChatMemory.CONVERSATION_ID, sessionId)
                                .param(ConfirmationAdvisor.SSE_EMITTER_KEY, emitter))
                        .call()
                        .chatResponse();

                // 提取 LLM 回复文本并通过 SSE 发送
                String replyText = chatResponse.getResult().getOutput().getText();
                if (replyText != null && !replyText.isEmpty()) {
                    sendEvent(emitter, "text", Map.of("content", replyText));
                }

                sendEvent(emitter, "done", Map.of("status", "completed"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Agent 对话异常: sessionId={}", sessionId, e);
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "未知错误"));
                    emitter.complete();
                } catch (Exception ex) {
                    log.debug("SSE 错误提示发送失败", ex);
                    emitter.completeWithError(ex);
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    /**
     * 构建 system prompt，注入当前用户信息。
     */
    private String buildSystemPrompt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = "未知用户";
        String roles = "无";

        if (auth != null && auth.getPrincipal() instanceof SecurityUser su) {
            username = su.getUsername();
            roles = su.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(", "));
        }

        return SYSTEM_PROMPT
                .replace("{username}", username)
                .replace("{roles}", roles);
    }

    /**
     * 发送 SSE 事件。
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);
        emitter.send(SseEmitter.event().name(eventName).data(json));
    }
}
