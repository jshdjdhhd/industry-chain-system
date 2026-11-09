package git.ggwzz.agent.controller;

import git.ggwzz.agent.advisor.ConfirmationAdvisor;
import git.ggwzz.agent.dto.AiToolPermissionRequest;
import git.ggwzz.agent.dto.AgentChatRequest;
import git.ggwzz.agent.dto.AgentConfirmRequest;
import git.ggwzz.agent.entity.AiToolPermission;
import git.ggwzz.agent.service.AgentService;
import git.ggwzz.agent.service.AgentToolRegistry;
import git.ggwzz.agent.service.AiPermissionService;
import git.ggwzz.agent.vo.AgentToolVO;
import git.ggwzz.agent.vo.AiToolPermissionVO;
import git.ggwzz.common.api.Result;
import git.ggwzz.security.service.SecurityUser;
import git.ggwzz.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 对话控制器。
 *
 * <p>提供 SSE 流式对话、写操作确认、工具权限管理等端点。</p>
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final ConfirmationAdvisor confirmationAdvisor;
    private final AgentToolRegistry agentToolRegistry;
    private final AiPermissionService aiPermissionService;

    /**
     * SSE 流式对话端点。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter chatStream(@Valid @RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时
        agentService.chatStream(request, emitter);
        return emitter;
    }

    /**
     * 写操作确认端点。
     */
    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> confirm(@Valid @RequestBody AgentConfirmRequest request) {
        confirmationAdvisor.confirm(request.getConfirmId(), Boolean.TRUE.equals(request.getApproved()));
        return Result.success();
    }

    /**
     * 获取当前用户可用工具列表。
     */
    @GetMapping("/tools")
    @PreAuthorize("isAuthenticated()")
    public Result<List<AgentToolVO>> getTools() {
        Long userId = SecurityUtils.currentUserId();
        Map<String, Boolean> overrides = (userId != null)
                ? aiPermissionService.getUserOverrides(userId)
                : Map.of();

        Set<String> userPermissions = getUserPermissions();

        List<AgentToolVO> tools = new ArrayList<>();
        for (Map.Entry<String, AgentToolRegistry.ToolMeta> entry : agentToolRegistry.getAll().entrySet()) {
            AgentToolRegistry.ToolMeta meta = entry.getValue();

            AgentToolVO vo = new AgentToolVO();
            vo.setToolName(meta.getToolName());
            vo.setReadOnly(meta.isReadOnly());
            vo.setGroup(meta.getGroup());

            // 判断 enabled：RBAC 权限 + AI 覆盖层
            boolean rbacAllowed = meta.getPermission() == null || userPermissions.contains(meta.getPermission());
            Boolean override = overrides.get(meta.getToolName());
            boolean aiAllowed = override == null || override;
            vo.setEnabled(rbacAllowed && aiAllowed);

            tools.add(vo);
        }

        return Result.success(tools);
    }

    /**
     * 获取当前用户的 AI 权限覆盖配置。
     */
    @GetMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    public Result<AiToolPermissionVO> getPermissions() {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            return Result.failure(401, "未登录");
        }

        Map<String, Boolean> overrides = aiPermissionService.getUserOverrides(userId);
        Set<String> userPermissions = getUserPermissions();

        AiToolPermissionVO vo = new AiToolPermissionVO();
        vo.setUserId(userId);
        vo.setUsername(SecurityUtils.currentUsername());

        List<AiToolPermissionVO.ToolPermissionItem> items = new ArrayList<>();
        for (Map.Entry<String, AgentToolRegistry.ToolMeta> entry : agentToolRegistry.getAll().entrySet()) {
            AgentToolRegistry.ToolMeta meta = entry.getValue();

            AiToolPermissionVO.ToolPermissionItem item = new AiToolPermissionVO.ToolPermissionItem();
            item.setToolName(meta.getToolName());
            item.setReadOnly(meta.isReadOnly());
            item.setGroup(meta.getGroup());

            // RBAC 基础权限
            boolean rbacAllowed = meta.getPermission() == null || userPermissions.contains(meta.getPermission());
            // AI 覆盖层
            Boolean override = overrides.get(meta.getToolName());
            item.setEnabled(rbacAllowed && (override == null || override));

            items.add(item);
        }
        vo.setPermissions(items);

        return Result.success(vo);
    }

    /**
     * 批量更新当前用户的 AI 权限覆盖配置。
     */
    @PutMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updatePermissions(@Valid @RequestBody AiToolPermissionRequest request) {
        Long userId = SecurityUtils.currentUserId();
        if (userId == null) {
            return Result.failure(401, "未登录");
        }

        List<AiToolPermission> permissions = request.getPermissions().stream()
                .map(item -> {
                    AiToolPermission perm = new AiToolPermission();
                    perm.setToolName(item.getToolName());
                    perm.setEnabled(item.getEnabled());
                    return perm;
                })
                .collect(Collectors.toList());

        aiPermissionService.updateUserOverrides(userId, permissions);
        return Result.success();
    }

    /**
     * 管理员查看全部用户的 AI 权限覆盖配置。
     */
    @GetMapping("/permissions/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<AiToolPermission>> getAllPermissions() {
        return Result.success(aiPermissionService.listAll());
    }

    /**
     * 获取当前用户的 RBAC 权限集合。
     */
    private Set<String> getUserPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
