package git.ggwzz.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.matching.llm.MatchingLlmProperties;
import git.ggwzz.matching.llm.LlmRerankPromptBuilder;
import git.ggwzz.matching.llm.LlmRerankResultParser;
import git.ggwzz.matching.service.impl.HybridEmbeddingMatchingProvider;
import git.ggwzz.matching.service.impl.LlmRerankMatchingProvider;
import git.ggwzz.matching.service.impl.MatchingReasonFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LlmRerankMatchingProviderTest {

    @Mock
    private HybridEmbeddingMatchingProvider hybridProvider;

    @Mock
    private LlmClient llmClient;

    private MatchingLlmProperties properties;

    private LlmRerankMatchingProvider provider;

    @BeforeEach
    void setUp() {
        properties = new MatchingLlmProperties();
        provider = new LlmRerankMatchingProvider(
                hybridProvider,
                llmClient,
                new LlmRerankPromptBuilder(new ObjectMapper()),
                new LlmRerankResultParser(new ObjectMapper()),
                properties,
                new ObjectMapper(),
                new MatchingReasonFormatter(new ObjectMapper()));
    }

    @Test
    void getModelName_shouldUseLlmRerankV3() {
        assertEquals("LLM_RERANK_V3", provider.getModelName());
    }

    @Test
    void match_shouldReturnResultsInLlmRankOrder() {
        when(hybridProvider.matchCandidates(100L, 1L, 6)).thenReturn(List.of(
                candidate(101L, "0.7200", "V2.1 推荐动力电池", "动力电池节点画像"),
                candidate(102L, "0.6800", "V2.1 推荐光模块", "光模块节点画像")
        ));
        when(llmClient.complete(anyList())).thenReturn(new LlmChatCompletion("mimo-v2.5-pro-ultraspeed", """
                {"ranked_results":[
                  {"node_id":102,"confidence":0.91,"reason":"企业主营产品与光模块节点更一致","risk":"经营范围中存在少量泛化描述"},
                  {"node_id":101,"confidence":0.63,"reason":"动力电池证据较弱","risk":"主营产品未直接覆盖电池"}
                ]}
                """));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(1, results.size());
        assertEquals(102L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.9100"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("企业主营产品与光模块节点更一致"));
        assertTrue(results.get(0).getEvidence().contains("\"model\":\"LLM_RERANK_V3\""));
        assertTrue(results.get(0).getEvidence().contains("\"prompt_profile\":\"PROJECT_ADAPTED_RUBRIC_FEWSHOT\""));
        assertTrue(results.get(0).getEvidence().contains("\"llm_fallback\":false"));
        assertTrue(results.get(0).getEvidence().contains("\"llm_model\":\"mimo-v2.5-pro-ultraspeed\""));
        assertTrue(results.get(0).getEvidence().contains("\"risk\":\"经营范围中存在少量泛化描述\""));
        assertTrue(results.get(0).getEvidence().contains("\"v21_confidence\":0.68"));
    }

    @Test
    void match_shouldReturnLowConfidenceCandidatesWithNotRecommendedReasonAndSkipLlm() {
        properties.setMinResultConfidence(0.65D);
        when(hybridProvider.matchCandidates(100L, 1L, 6)).thenReturn(List.of(
                candidate(101L, "0.5900", "V2.1 低置信候选", "泛化节点画像")
        ));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.5900"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("不建议自动挂链"));
        assertTrue(results.get(0).getEvidence().contains("\"recommendation_level\":\"LOW_CONFIDENCE\""));
        assertTrue(results.get(0).getEvidence().contains("\"below_min_result_confidence\":true"));
        verifyNoInteractions(llmClient);
    }

    @Test
    void match_shouldSkipLlmForClearLocalWinnerAndUseBusinessReadableReason() {
        properties.setClearWinnerMinConfidenceThreshold(0.70D);
        properties.setClearWinnerGapThreshold(0.08D);
        when(hybridProvider.matchCandidates(100L, 1L, 6)).thenReturn(List.of(
                candidate(101L, "0.7400", "语义相似度 0.6436；规则证据：主营产品命中2个关键词",
                        "节点路径：氢能源与新能源汽车零部件产业链 / 动力电池\n节点名称：动力电池",
                        0.6436D, 0.95D),
                candidate(102L, "0.6100", "V2.1 推荐泛化新能源节点",
                        "节点路径：氢能源与新能源汽车零部件产业链 / 新能源汽车零部件",
                        0.5800D, 0.40D)
        ));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).getNodeId());
        assertTrue(results.get(0).getReason().contains("综合推荐依据"));
        assertTrue(results.get(0).getReason().contains("语义相似度"));
        assertTrue(results.get(0).getReason().contains("规则置信度"));
        assertTrue(results.get(0).getEvidence().contains("\"llm_skipped\":true"));
        verifyNoInteractions(llmClient);
    }

    @Test
    void match_shouldFilterLowConfidenceLlmDecisionsWithoutBackfillingThem() {
        properties.setMinResultConfidence(0.65D);
        when(hybridProvider.matchCandidates(100L, 1L, 6)).thenReturn(List.of(
                candidate(101L, "0.7200", "V2.1 推荐动力电池", "动力电池节点画像"),
                candidate(102L, "0.6800", "V2.1 推荐光模块", "光模块节点画像")
        ));
        when(llmClient.complete(anyList())).thenReturn(new LlmChatCompletion("mimo-v2.5-pro-ultraspeed", """
                {"ranked_results":[
                  {"node_id":101,"confidence":0.52,"reason":"证据不足，不建议推荐","risk":"主营产品未直接覆盖"},
                  {"node_id":102,"confidence":0.72,"reason":"主营产品与光模块节点更一致","risk":"仍需人工核验"}
                ]}
                """));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(1, results.size());
        assertEquals(102L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.7200"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("主营产品与光模块节点更一致"));
    }

    @Test
    void match_shouldSkipLlmWhenV21TopCandidateIsHighConfidence() {
        when(hybridProvider.matchCandidates(100L, 1L, 6)).thenReturn(List.of(
                candidate(101L, "0.9200", "V2.1 高置信推荐动力电池", "动力电池节点画像"),
                candidate(102L, "0.6100", "V2.1 推荐光模块", "光模块节点画像")
        ));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.9200"), results.get(0).getConfidence());
        assertTrue(results.get(0).getEvidence().contains("\"model\":\"LLM_RERANK_V3\""));
        assertTrue(results.get(0).getEvidence().contains("\"llm_skipped\":true"));
        assertTrue(results.get(0).getEvidence().contains("\"fallback_reason\":\"HIGH_CONFIDENCE_FAST_PATH\""));
        verifyNoInteractions(llmClient);
    }

    @Test
    void match_shouldFallbackToV21WhenLlmFails() {
        when(hybridProvider.matchCandidates(100L, 1L, 6)).thenReturn(List.of(
                candidate(101L, "0.7200", "V2.1 推荐动力电池", "动力电池节点画像"),
                candidate(102L, "0.6800", "V2.1 推荐光模块", "光模块节点画像")
        ));
        when(llmClient.complete(anyList()))
                .thenThrow(new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 服务不可用"));

        List<MatchingResult> results = provider.match(100L, 1L);

        assertEquals(2, results.size());
        assertEquals(101L, results.get(0).getNodeId());
        assertEquals(new BigDecimal("0.7200"), results.get(0).getConfidence());
        assertTrue(results.get(0).getReason().contains("综合推荐依据"));
        assertTrue(results.get(0).getReason().contains("大模型复核不可用"));
        assertTrue(results.get(0).getEvidence().contains("\"model\":\"LLM_RERANK_V3\""));
        assertTrue(results.get(0).getEvidence().contains("\"llm_fallback\":true"));
        assertTrue(results.get(0).getEvidence().contains("\"fallback_model\":\"HYBRID_EMBEDDING_V2\""));
    }

    private MatchingResult candidate(Long nodeId, String confidence, String reason, String nodeProfile) {
        return candidate(nodeId, confidence, reason, nodeProfile, 0.71D, 0.60D);
    }

    private MatchingResult candidate(Long nodeId, String confidence, String reason, String nodeProfile,
                                     double vectorSimilarity, double ruleConfidence) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(nodeId);
        result.setConfidence(new BigDecimal(confidence));
        result.setReason(reason);
        String escapedNodeProfile = nodeProfile
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
        result.setEvidence("""
                {"model":"HYBRID_EMBEDDING_V2","vector_similarity":%s,"rule_confidence":%s,
                "final_score":%s,"enterprise_profile":"企业画像","node_profile":"%s",
                "rule_evidence":"{\\"keyword_match\\":[\\"动力电池\\",\\"电芯\\"]}"}
                """.formatted(vectorSimilarity, ruleConfidence, confidence, escapedNodeProfile));
        return result;
    }
}
