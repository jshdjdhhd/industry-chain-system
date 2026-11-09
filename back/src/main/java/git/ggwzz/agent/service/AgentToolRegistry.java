package git.ggwzz.agent.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class AgentToolRegistry {

    private final Map<String, ToolMeta> registry = new LinkedHashMap<>();

    public AgentToolRegistry() {
        register("searchEnterprises", "enterprise:enterprise:query", true, "企业管理");
        register("getEnterpriseDetail", "enterprise:enterprise:query", true, "企业管理");
        register("createEnterprise", "enterprise:enterprise:create", false, "企业管理");
        register("inboundEnterprise", "enterprise:enterprise:inbound", false, "企业管理");
        register("outboundEnterprise", "enterprise:enterprise:outbound", false, "企业管理");
        register("deleteEnterprise", "enterprise:enterprise:delete", false, "企业管理");

        register("listChains", "chain:chain:query", true, "产业链管理");
        register("getChainNodeTree", "chain:node:query", true, "产业链管理");
        register("searchNodes", "chain:node:query", true, "产业链管理");
        register("createNode", "chain:node:create", false, "产业链管理");
        register("linkEnterpriseToNode", "chain:node:link-enterprise", false, "产业链管理");
        register("unlinkEnterpriseFromNode", "chain:node:unlink-enterprise", false, "产业链管理");

        register("runSmartMatching", "matching:task:create", false, "智能挂链");
        register("acceptMatchingResult", "matching:result:accept", false, "智能挂链");
        register("rejectMatchingResult", "matching:result:reject", false, "智能挂链");

        register("listTags", "enterprise:tag:query", true, "标签管理");
        register("createTag", "enterprise:tag:create", false, "标签管理");
        register("bindTag", "enterprise:tag:bind", false, "标签管理");
        register("unbindTag", "enterprise:tag:unbind", false, "标签管理");

        register("generateReport", "analysis:report:create", false, "健康分析");
        register("getDiagnosis", "analysis:diagnosis:query", true, "健康分析");
        register("getReport", "analysis:report:query", true, "健康分析");

        register("getOverview", null, true, "数据看板");
    }

    private void register(String toolName, String permission, boolean readOnly, String group) {
        registry.put(toolName, new ToolMeta(toolName, permission, readOnly, group));
    }

    public ToolMeta get(String toolName) { return registry.get(toolName); }
    public Map<String, ToolMeta> getAll() { return registry; }
    public Set<String> getAllToolNames() { return registry.keySet(); }

    @Data
    @AllArgsConstructor
    public static class ToolMeta {
        private String toolName;
        private String permission;
        private boolean readOnly;
        private String group;
    }
}
