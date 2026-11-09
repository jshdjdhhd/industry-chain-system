package git.ggwzz.agent.dto;

import lombok.Data;

@Data
public class AgentConfirmRequest {
    private String sessionId;
    private String confirmId;
    private Boolean approved;
}
