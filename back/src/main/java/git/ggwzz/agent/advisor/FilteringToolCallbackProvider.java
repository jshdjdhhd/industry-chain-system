package git.ggwzz.agent.advisor;

import git.ggwzz.agent.service.AgentToolRegistry;
import git.ggwzz.agent.service.AiPermissionService;
import git.ggwzz.security.service.SecurityUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 过滤型 ToolCallbackProvider。
 *
 * <p>包装原始 ToolCallbackProvider，在每次获取工具时根据当前用户的 RBAC 权限和 AI 覆盖层进行过滤。
 * 被禁用的工具对 LLM 完全不可见。</p>
 */
@Slf4j
public class FilteringToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;
    private final AgentToolRegistry agentToolRegistry;
    private final AiPermissionService aiPermissionService;

    public FilteringToolCallbackProvider(ToolCallbackProvider delegate,
                                         AgentToolRegistry agentToolRegistry,
                                         AiPermissionService aiPermissionService) {
        this.delegate = delegate;
        this.agentToolRegistry = agentToolRegistry;
        this.aiPermissionService = aiPermissionService;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        ToolCallback[] all = delegate.getToolCallbacks();
        log.info("FilteringToolCallbackProvider.getToolCallbacks() called, all tools count: {}", all.length);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return all;
        }

        Set<String> userPermissions = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        Long userId = null;
        if (auth.getPrincipal() instanceof SecurityUser su) {
            userId = su.getUserId();
        }
        Map<String, Boolean> overrides = (userId != null)
                ? aiPermissionService.getUserOverrides(userId)
                : Collections.emptyMap();

        ToolCallback[] filtered = Arrays.stream(all)
                .filter(cb -> isToolAllowed(cb.getToolDefinition().name(), userPermissions, overrides))
                .toArray(ToolCallback[]::new);

        log.info("Filtered tools: {}", filtered.length);

        if (filtered.length < all.length) {
            log.debug("权限过滤: {} -> {} 个工具（移除 {} 个）",
                    all.length, filtered.length, all.length - filtered.length);
        }

        return filtered;
    }

    private boolean isToolAllowed(String toolName, Set<String> userPermissions, Map<String, Boolean> overrides) {
        AgentToolRegistry.ToolMeta meta = agentToolRegistry.get(toolName);
        if (meta == null) {
            return true;
        }

        // 第一层：RBAC 权限
        String requiredPermission = meta.getPermission();
        if (requiredPermission != null && !userPermissions.contains(requiredPermission)) {
            return false;
        }

        // 第二层：AI 覆盖层
        if (Boolean.FALSE.equals(overrides.get(toolName))) {
            return false;
        }

        return true;
    }
}
