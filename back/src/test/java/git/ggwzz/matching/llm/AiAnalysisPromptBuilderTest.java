package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAnalysisPromptBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAnalysisPromptBuilder builder = new AiAnalysisPromptBuilder(objectMapper);

    @Test
    void buildShouldIncludeEnterpriseNodesAndNaturalLanguagePrompt() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(100L);
        enterprise.setName("宁德时代新能源科技股份有限公司");
        enterprise.setMainProducts("动力电池，电芯，储能系统");
        enterprise.setBusinessScope("锂离子电池制造与销售");
        enterprise.setTags("新能源，电池");

        ChainNodeCandidateDTO battery = node(101L, "新能源汽车产业链 / 中游 / 动力电池", "动力电池");
        ChainNodeCandidateDTO motor = node(102L, "新能源汽车产业链 / 中游 / 驱动电机", "驱动电机");

        List<LlmMessage> messages = builder.build(enterprise, List.of(battery, motor), 3);

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertTrue(messages.get(0).content().contains("产业链企业挂链分析专家"));
        assertTrue(messages.get(0).content().contains("自然语言"));

        String userMessage = messages.get(1).content();
        assertEquals("user", messages.get(1).role());
        assertTrue(userMessage.contains("宁德时代新能源科技股份有限公司"));
        assertTrue(userMessage.contains("动力电池，电芯，储能系统"));
        assertTrue(userMessage.contains("锂离子电池制造与销售"));
        assertTrue(userMessage.contains("新能源，电池"));
        assertTrue(userMessage.contains("新能源汽车产业链 / 中游 / 动力电池"));
        assertTrue(userMessage.contains("新能源汽车产业链 / 中游 / 驱动电机"));
        assertTrue(userMessage.contains("动力电池"));
        assertTrue(userMessage.contains("驱动电机"));
        assertTrue(userMessage.contains("3 个节点"));
    }

    private ChainNodeCandidateDTO node(Long id, String path, String keywords) {
        ChainNodeCandidateDTO node = new ChainNodeCandidateDTO();
        node.setNodeId(id);
        node.setChainId(1L);
        node.setNodeName(path.substring(path.lastIndexOf('/') + 1).trim());
        node.setNodePath(path);
        node.setKeywords(keywords);
        node.setLevel(3);
        return node;
    }
}
