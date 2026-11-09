package git.ggwzz.agent.config;

import git.ggwzz.agent.advisor.FilteringToolCallbackProvider;
import git.ggwzz.agent.service.AgentToolRegistry;
import git.ggwzz.agent.service.AiPermissionService;
import git.ggwzz.agent.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模块配置。
 */
@Configuration
public class AgentConfig {

    @Bean
    public ToolCallbackProvider agentToolCallbackProvider(
            EnterpriseTools enterpriseTools,
            ChainTools chainTools,
            MatchingTools matchingTools,
            TagTools tagTools,
            AnalysisTools analysisTools,
            DashboardTools dashboardTools,
            AgentToolRegistry agentToolRegistry,
            AiPermissionService aiPermissionService) {
        ToolCallbackProvider raw = MethodToolCallbackProvider.builder()
                .toolObjects(enterpriseTools, chainTools, matchingTools,
                        tagTools, analysisTools, dashboardTools)
                .build();
        return new FilteringToolCallbackProvider(raw, agentToolRegistry, aiPermissionService);
    }
}
