package git.ggwzz.agent.advisor;

import git.ggwzz.agent.service.AgentToolRegistry;
import git.ggwzz.agent.service.AiPermissionService;
import git.ggwzz.security.service.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限过滤 Advisor。
 *
 * <p>两层过滤：先检查 RBAC 权限，再检查 AI 覆盖层（ai_tool_permission 表）。
 * 过滤掉用户无权或被禁用的工具，使 LLM 无法调用它们。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionFilterAdvisor implements BaseAdvisor {

    private final AgentToolRegistry agentToolRegistry;
    private final AiPermissionService aiPermissionService;
    private final ToolCallbackProvider toolCallbackProvider;

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        log.info("PermissionFilterAdvisor.before() called");

        // 1. 从 SecurityContext 获取用户权限集
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.debug("未认证请求，跳过权限过滤");
            return chatClientRequest;
        }

        Set<String> userPermissions = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // 2. 获取 AI 覆盖层
        Long userId = null;
        if (auth.getPrincipal() instanceof SecurityUser su) {
            userId = su.getUserId();
        }
        Map<String, Boolean> overrides = (userId != null)
                ? aiPermissionService.getUserOverrides(userId)
                : Collections.emptyMap();

        // 3. 从 ToolCallbackProvider 获取全部工具（不依赖 prompt options，因为 defaultToolCallbacks 在 advisor 之后才合并）
        List<ToolCallback> allCallbacks = Arrays.asList(toolCallbackProvider.getToolCallbacks());
        if (allCallbacks.isEmpty()) {
            return chatClientRequest;
        }

        List<ToolCallback> filtered = allCallbacks.stream()
                .filter(cb -> isToolAllowed(cb.getToolDefinition().name(), userPermissions, overrides))
                .collect(Collectors.toList());

        if (filtered.size() < allCallbacks.size()) {
            log.debug("权限过滤: {} -> {} 个工具（移除 {} 个）",
                    allCallbacks.size(), filtered.size(), allCallbacks.size() - filtered.size());
        }

        // 4. 将过滤后的工具列表设置到 prompt options 中
        ToolCallingChatOptions tcOptions;
        if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions existing) {
            tcOptions = existing;
        } else {
            tcOptions = ToolCallingChatOptions.builder().build();
        }
        tcOptions.setToolCallbacks(filtered);

        ChatClientRequest filteredRequest = chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().mutate()
                        .chatOptions(tcOptions)
                        .build())
                .build();

        return filteredRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    /**
     * 判断工具是否对当前用户可用。
     *
     * @param toolName        工具名
     * @param userPermissions 用户 RBAC 权限集
     * @param overrides       AI 覆盖层（toolName -> enabled）
     * @return true 表示允许使用
     */
    private boolean isToolAllowed(String toolName, Set<String> userPermissions, Map<String, Boolean> overrides) {
        AgentToolRegistry.ToolMeta meta = agentToolRegistry.get(toolName);
        if (meta == null) {
            // 未注册的工具放行（由其他机制控制）
            return true;
        }

        // 第一层：RBAC 权限
        String requiredPermission = meta.getPermission();
        if (requiredPermission != null && !userPermissions.contains(requiredPermission)) {
            log.debug("工具 [{}] 被 RBAC 权限过滤，需要: {}", toolName, requiredPermission);
            return false;
        }

        // 第二层：AI 覆盖层
        if (Boolean.FALSE.equals(overrides.get(toolName))) {
            log.debug("工具 [{}] 被 AI 覆盖层禁用", toolName);
            return false;
        }

        return true;
    }
}
