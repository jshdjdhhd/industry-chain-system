package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.llm.LlmMessage;
import git.ggwzz.matching.entity.MatchingResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmRerankPromptBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmRerankPromptBuilder builder = new LlmRerankPromptBuilder(objectMapper);

    @Test
    void build_shouldIncludeProjectRubricFewShotsConfidenceCalibrationAndDomainTerms() throws Exception {
        List<LlmMessage> messages = builder.build(List.of(
                candidate(201L, "0.8200", """
                        任务：产业链挂链语义检索
                        企业名称：中际旭创股份有限公司
                        主营产品与技术关键词：高速光模块，数据中心光模块，硅光模块
                        领域扩展词：中际旭创，旭创，光模块，光通信，数据中心互联
                        """, """
                        任务：候选产业链节点语义文档
                        节点路径：光通信产业链 / 中游 / 光模块
                        节点关键词：高速光模块，光通信模块，光器件
                        领域扩展词：光模块，光通信模块，光器件
                        """),
                candidate(202L, "0.6100", "企业名称：中际旭创股份有限公司", """
                        节点路径：新能源汽车产业链 / 中游 / 动力电池
                        节点关键词：动力电池，电芯
                        """)
        ), 5);

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).role());
        assertTrue(messages.get(0).content().contains("产业链上下游环节"));
        assertTrue(messages.get(0).content().contains("置信度必须校准"));

        JsonNode payload = objectMapper.readTree(messages.get(1).content());
        assertEquals("PROJECT_ADAPTED_RUBRIC_FEWSHOT", payload.path("prompt_profile").asText());
        assertTrue(payload.path("project_context").path("objective").asText().contains("企业挂接到最合适的产业链节点"));
        assertTrue(arrayContains(payload.path("scoring_rubric"), "主营产品与核心技术匹配"));
        assertTrue(arrayContains(payload.path("scoring_rubric"), "产业链上下游环节"));
        assertTrue(payload.path("confidence_calibration").path("high").asText().contains("0.85"));
        assertTrue(payload.path("confidence_calibration").path("low").asText().contains("0.60"));
        assertTrue(arrayContains(payload.path("few_shot_examples"), "中际旭创"));
        assertTrue(arrayContains(payload.path("few_shot_examples"), "宁德时代"));
        assertTrue(arrayContains(payload.path("few_shot_examples"), "苹果"));
        assertTrue(arrayContains(payload.path("domain_terms"), "数据中心互联"));
        assertTrue(arrayContains(payload.path("domain_terms"), "光通信模块"));
        assertFalse(arrayContains(payload.path("domain_terms"), "动力电池"));
    }

    private MatchingResult candidate(Long nodeId, String confidence, String enterpriseProfile, String nodeProfile) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(nodeId);
        result.setConfidence(new BigDecimal(confidence));
        result.setReason("V2.1 推荐");
        result.setEvidence("""
                {"model":"HYBRID_EMBEDDING_V2","vector_similarity":0.82,"rule_confidence":0.60,
                "final_score":0.75,"enterprise_profile":%s,"node_profile":%s}
                """.formatted(toJsonString(enterpriseProfile), toJsonString(nodeProfile)));
        return result;
    }

    private String toJsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean arrayContains(JsonNode array, String expected) {
        for (JsonNode item : array) {
            if (item.toString().contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
