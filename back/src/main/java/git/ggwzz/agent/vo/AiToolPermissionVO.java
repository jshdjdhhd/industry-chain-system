package git.ggwzz.agent.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiToolPermissionVO {
    private Long userId;
    private String username;
    private List<ToolPermissionItem> permissions;

    @Data
    public static class ToolPermissionItem {
        private String toolName;
        private String description;
        private boolean readOnly;
        private String group;
        private boolean enabled;
    }
}
