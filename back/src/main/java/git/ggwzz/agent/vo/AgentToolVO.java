package git.ggwzz.agent.vo;

import lombok.Data;

@Data
public class AgentToolVO {
    private String toolName;
    private String description;
    private boolean readOnly;
    private String group;
    private boolean enabled;
}
