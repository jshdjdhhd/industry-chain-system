package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiAnalysisPromptBuilder {

    public static final String PROMPT_VERSION = "single-enterprise-ai-analysis-v2-2026-07-03";

    private static final String SYSTEM_PROMPT = """
            你是产业链企业挂链分析专家。你擅长分析企业业务与产业链节点的匹配关系。

            你的任务是：
            1. 仔细阅读企业画像（主营产品、经营范围、标签等）
            2. 阅读候选产业链节点列表
            3. 给出专业的分析建议，包括：
               - 该企业最适合挂接到哪个节点，为什么
               - 企业的哪些业务特征与该节点匹配
               - 存在哪些风险或不确定性
               - 给业务人员的操作建议

            请用专业但易懂的中文回答，不要使用 JSON 格式，直接用自然语言表达你的分析。
            """;

    private final ObjectMapper objectMapper;

    public List<LlmMessage> build(RuleBasedMatchingProvider.EnterpriseInfo enterprise,
                                  List<ChainNodeCandidateDTO> nodes,
                                  int resultTopK) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append("请分析以下企业与产业链节点的挂链匹配关系：\n\n");

        userMessage.append("## 企业画像\n");
        userMessage.append("- 企业名称：").append(safe(enterprise.getName())).append("\n");
        userMessage.append("- 主营产品：").append(safe(enterprise.getMainProducts())).append("\n");
        userMessage.append("- 经营范围：").append(safe(enterprise.getBusinessScope())).append("\n");
        userMessage.append("- 标签：").append(safe(enterprise.getTags())).append("\n\n");

        userMessage.append("## 候选产业链节点（共").append(nodes.size()).append("个）\n");
        for (int i = 0; i < nodes.size(); i++) {
            ChainNodeCandidateDTO node = nodes.get(i);
            userMessage.append(i + 1).append(". ").append(safe(node.getNodePath()));
            if (node.getKeywords() != null && !node.getKeywords().isBlank()) {
                userMessage.append(" [关键词：").append(safe(node.getKeywords())).append("]");
            }
            userMessage.append("\n");
        }

        userMessage.append("\n请给出你的专业分析，推荐最匹配的 ").append(Math.max(1, resultTopK)).append(" 个节点。");

        return List.of(
                new LlmMessage("system", SYSTEM_PROMPT),
                new LlmMessage("user", userMessage.toString()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
