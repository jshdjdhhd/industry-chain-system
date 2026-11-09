package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.matching.embedding.EmbeddingProperties;
import git.ggwzz.matching.entity.MatchingRecommendationCache;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.mapper.MatchingRecommendationCacheMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchingRecommendationCacheServiceTest {

    private MatchingRecommendationCacheMapper mapper;
    private MatchingRecommendationCacheSchemaManager schemaManager;
    private MatchingRecommendationCacheService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MatchingRecommendationCacheMapper.class);
        schemaManager = mock(MatchingRecommendationCacheSchemaManager.class);
        when(schemaManager.ensureReady()).thenReturn(true);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.getHybrid().setTopK(5);
        properties.getHybrid().setVectorWeight(0.7D);
        properties.getHybrid().setRuleWeight(0.3D);
        service = new MatchingRecommendationCacheService(
                mapper, new ObjectMapper(), properties, schemaManager);
    }

    @Test
    void buildFingerprintShouldChangeWhenNodeProfilesChange() {
        MatchingRecommendationCacheService.CacheFingerprint first = service.buildFingerprint(
                1L, 2L, "LOCAL_QWEN:qwen3-embedding:0.6b",
                "enterprise profile", nodeProfiles("battery", "motor"));
        MatchingRecommendationCacheService.CacheFingerprint changed = service.buildFingerprint(
                1L, 2L, "LOCAL_QWEN:qwen3-embedding:0.6b",
                "enterprise profile", nodeProfiles("battery", "sensor"));

        assertEquals(1L, first.enterpriseId());
        assertEquals(2L, first.chainId());
        assertEquals(64, first.enterpriseTextHash().length());
        assertEquals(64, first.chainNodesHash().length());
        assertEquals(5, first.topK());
        assertEquals(0.7D, first.vectorWeight());
        assertEquals(0.3D, first.ruleWeight());
        assertTrue(!first.chainNodesHash().equals(changed.chainNodesHash()));
    }

    @Test
    void saveAndFindCachedResultsShouldRoundTripIndependentMatchingResults() {
        MatchingRecommendationCacheService.CacheFingerprint fingerprint = service.buildFingerprint(
                1L, 2L, "LOCAL_QWEN:qwen3-embedding:0.6b",
                "enterprise profile", nodeProfiles("battery", "motor"));
        MatchingResult result = new MatchingResult();
        result.setNodeId(101L);
        result.setConfidence(new BigDecimal("0.8765"));
        result.setReason("cached reason");
        result.setEvidence("{\"source\":\"cache-test\"}");
        result.setRankNo(1);

        service.saveSuccess(fingerprint, List.of(result));

        ArgumentCaptor<MatchingRecommendationCache> captor =
                ArgumentCaptor.forClass(MatchingRecommendationCache.class);
        verify(mapper).markActiveDeleted(
                fingerprint.enterpriseId(), fingerprint.chainId(), fingerprint.modelName(),
                fingerprint.enterpriseTextHash(), fingerprint.chainNodesHash(),
                fingerprint.topK(), fingerprint.vectorWeight(), fingerprint.ruleWeight());
        verify(mapper).insert(captor.capture());
        MatchingRecommendationCache stored = captor.getValue();
        when(mapper.selectActiveByFingerprint(
                fingerprint.enterpriseId(), fingerprint.chainId(), fingerprint.modelName(),
                fingerprint.enterpriseTextHash(), fingerprint.chainNodesHash(),
                fingerprint.topK(), fingerprint.vectorWeight(), fingerprint.ruleWeight()))
                .thenReturn(stored);

        Optional<List<MatchingResult>> restored = service.findCachedResults(fingerprint);

        assertTrue(restored.isPresent());
        assertEquals(1, restored.get().size());
        assertNotSame(result, restored.get().get(0));
        assertEquals(101L, restored.get().get(0).getNodeId());
        assertEquals(new BigDecimal("0.8765"), restored.get().get(0).getConfidence());
        assertEquals("cached reason", restored.get().get(0).getReason());
        assertEquals("{\"source\":\"cache-test\"}", restored.get().get(0).getEvidence());
    }

    @Test
    void findCachedResultsShouldTolerateLegacyRowsWithoutRankNo() {
        MatchingRecommendationCacheService.CacheFingerprint fingerprint = service.buildFingerprint(
                1L, 2L, "LOCAL_QWEN:qwen3-embedding:0.6b",
                "enterprise profile", nodeProfiles("battery", "motor"));
        MatchingRecommendationCache stored = new MatchingRecommendationCache();
        stored.setId(99L);
        stored.setResultJson("""
                [
                  {"nodeId":101,"confidence":0.8765,"reason":"cached first","evidence":"{}"},
                  {"nodeId":102,"confidence":0.7654,"reason":"cached second","evidence":"{}"}
                ]
                """);
        when(mapper.selectActiveByFingerprint(
                fingerprint.enterpriseId(), fingerprint.chainId(), fingerprint.modelName(),
                fingerprint.enterpriseTextHash(), fingerprint.chainNodesHash(),
                fingerprint.topK(), fingerprint.vectorWeight(), fingerprint.ruleWeight()))
                .thenReturn(stored);

        Optional<List<MatchingResult>> restored = service.findCachedResults(fingerprint);

        assertTrue(restored.isPresent());
        assertEquals(2, restored.get().size());
        assertEquals(101L, restored.get().get(0).getNodeId());
        assertEquals(102L, restored.get().get(1).getNodeId());
    }

    @Test
    void cacheAccessShouldNoopWhenSchemaUnavailable() {
        when(schemaManager.ensureReady()).thenReturn(false);
        MatchingRecommendationCacheService.CacheFingerprint fingerprint = service.buildFingerprint(
                1L, 2L, "LOCAL_QWEN:qwen3-embedding:0.6b",
                "enterprise profile", nodeProfiles("battery", "motor"));

        Optional<List<MatchingResult>> restored = service.findCachedResults(fingerprint);
        service.saveSuccess(fingerprint, List.of(new MatchingResult()));

        assertTrue(restored.isEmpty());
        verifyNoInteractions(mapper);
    }

    private Map<Long, String> nodeProfiles(String first, String second) {
        Map<Long, String> profiles = new LinkedHashMap<>();
        profiles.put(101L, first);
        profiles.put(102L, second);
        return profiles;
    }
}
