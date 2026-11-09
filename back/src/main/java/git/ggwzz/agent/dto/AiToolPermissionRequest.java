package git.ggwzz.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiToolPermissionRequest {
    private Long userId;
    private List<PermissionItem> permissions;

    @Data
    public static class PermissionItem {
        private String toolName;
        private Boolean enabled;
    }
}
