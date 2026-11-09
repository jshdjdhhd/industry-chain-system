package git.ggwzz.agent.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    private String sessionId;
    private String message;
    private AgentContext context;

    @Data
    public static class AgentContext {
        private Long enterpriseId;
        private Long chainId;
    }
}
