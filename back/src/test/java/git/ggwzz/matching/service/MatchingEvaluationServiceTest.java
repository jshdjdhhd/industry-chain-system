package git.ggwzz.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.matching.entity.MatchingEvaluationSample;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.mapper.MatchingEvaluationSampleMapper;
import git.ggwzz.matching.service.impl.MatchingEvaluationService;
import git.ggwzz.matching.vo.MatchingEvaluationReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchingEvaluationServiceTest {

    @Test
    void evaluateShouldCalculateRecallTop1AndMrrFromEnabledSamples() {
        MatchingEvaluationSampleMapper sampleMapper = mock(MatchingEvaluationSampleMapper.class);
        MatchingProvider matchingProvider = mock(MatchingProvider.class);
        when(matchingProvider.getModelName()).thenReturn("LLM_RERANK_V3");

        MatchingEvaluationService service = new MatchingEvaluationService(sampleMapper, matchingProvider, 5);

        MatchingEvaluationSample sampleA = sample(10L, 2L, 100L, "中际旭创 -> 光模块");
        MatchingEvaluationSample sampleB = sample(11L, 2L, 200L, "宁德时代 -> 动力电池");
        when(sampleMapper.selectEnabledByChainId(2L)).thenReturn(List.of(sampleA, sampleB));
        when(matchingProvider.match(10L, 2L)).thenReturn(List.of(result(99L), result(100L)));
        when(matchingProvider.match(11L, 2L)).thenReturn(List.of(result(200L), result(201L)));

        MatchingEvaluationReport report = service.evaluate(2L);

        assertEquals("LLM_RERANK_V3", report.getProviderName());
        assertEquals(2, report.getSampleCount());
        assertEquals(new BigDecimal("1.0000"), report.getRecallAtK());
        assertEquals(new BigDecimal("0.5000"), report.getTop1Accuracy());
        assertEquals(new BigDecimal("0.7500"), report.getMeanReciprocalRank());
        assertEquals(0, report.getFallbackCount());
        assertTrue(report.getMisses().isEmpty());
    }

    @Test
    void evaluateAllShouldReturnThreeProviderReportsAndCountFallbacks() {
        MatchingEvaluationSampleMapper sampleMapper = mock(MatchingEvaluationSampleMapper.class);
        MatchingProvider ruleProvider = mock(MatchingProvider.class);
        MatchingProvider hybridProvider = mock(MatchingProvider.class);
        MatchingProvider llmProvider = mock(MatchingProvider.class);
        when(ruleProvider.getModelName()).thenReturn("RULE_BASED");
        when(hybridProvider.getModelName()).thenReturn("HYBRID_EMBEDDING_V2");
        when(llmProvider.getModelName()).thenReturn("LLM_RERANK_V3");

        MatchingEvaluationService service = new MatchingEvaluationService(
                sampleMapper,
                new ObjectMapper(),
                new MatchingEvaluationService.ProviderSpec(ruleProvider, 5),
                new MatchingEvaluationService.ProviderSpec(hybridProvider, 5),
                new MatchingEvaluationService.ProviderSpec(llmProvider, 3)
        );

        MatchingEvaluationSample sample = sample(10L, 2L, 100L, "中际旭创 -> 光模块");
        when(sampleMapper.selectEnabledByChainId(2L)).thenReturn(List.of(sample));
        when(ruleProvider.match(10L, 2L)).thenReturn(List.of(result(100L)));
        when(hybridProvider.match(10L, 2L)).thenReturn(List.of(result(100L)));
        when(llmProvider.match(10L, 2L)).thenReturn(List.of(result(100L, "{\"llm_fallback\":true}")));

        List<MatchingEvaluationReport> reports = service.evaluateAll(2L);

        assertEquals(3, reports.size());
        assertEquals("RULE_BASED", reports.get(0).getProviderName());
        assertEquals("HYBRID_EMBEDDING_V2", reports.get(1).getProviderName());
        assertEquals("LLM_RERANK_V3", reports.get(2).getProviderName());
        assertEquals(1, reports.get(2).getFallbackCount());
        assertEquals(new BigDecimal("1.0000"), reports.get(2).getRecallAtK());
    }

    @Test
    void evaluateShouldRecordMissesWhenExpectedNodeIsNotInTopK() {
        MatchingEvaluationSampleMapper sampleMapper = mock(MatchingEvaluationSampleMapper.class);
        MatchingProvider matchingProvider = mock(MatchingProvider.class);
        when(matchingProvider.getModelName()).thenReturn("RULE_BASED");

        MatchingEvaluationService service = new MatchingEvaluationService(sampleMapper, matchingProvider, 1);

        MatchingEvaluationSample sample = sample(10L, 2L, 100L, "中际旭创 -> 光模块");
        when(sampleMapper.selectEnabledByChainId(2L)).thenReturn(List.of(sample));
        when(matchingProvider.match(10L, 2L)).thenReturn(List.of(result(99L), result(100L)));

        MatchingEvaluationReport report = service.evaluate(2L);

        assertEquals(new BigDecimal("0.0000"), report.getRecallAtK());
        assertEquals(1, report.getMisses().size());
        assertEquals(100L, report.getMisses().get(0).getExpectedNodeId());
    }

    private MatchingEvaluationSample sample(Long enterpriseId, Long chainId, Long expectedNodeId, String name) {
        MatchingEvaluationSample sample = new MatchingEvaluationSample();
        sample.setEnterpriseId(enterpriseId);
        sample.setChainId(chainId);
        sample.setExpectedNodeId(expectedNodeId);
        sample.setSampleName(name);
        return sample;
    }

    private MatchingResult result(Long nodeId) {
        return result(nodeId, "{}");
    }

    private MatchingResult result(Long nodeId, String evidence) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(nodeId);
        result.setConfidence(new BigDecimal("0.9000"));
        result.setEvidence(evidence);
        return result;
    }
}
