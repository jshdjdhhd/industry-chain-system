package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.embedding.EmbeddingProfileTextBuilder;
import git.ggwzz.matching.embedding.EmbeddingProperties;
import git.ggwzz.matching.embedding.EmbeddingVectorService;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.entity.MatchingTask;
import git.ggwzz.matching.enums.MatchingTaskStatus;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.matching.mapper.MatchingTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingBatchLocalEngineTest {

    @Mock
    private ChainNodeQueryService chainNodeQueryService;

    @Mock
    private EnterpriseQueryService enterpriseQueryService;

    @Mock
    private EmbeddingVectorService embeddingVectorService;

    @Mock
    private RuleBasedMatchingProvider ruleBasedMatchingProvider;

    @Mock
    private MatchingTaskMapper matchingTaskMapper;

    @Mock
    private MatchingResultMapper matchingResultMapper;

    @Mock
    private MatchingResultPersistenceSupport persistenceSupport;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private MatchingRecommendationCacheService recommendationCacheService;

    private MatchingBatchLocalEngine engine;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Consumer<?> callback = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            Consumer<Object> typedCallback = (Consumer<Object>) callback;
            typedCallback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        engine = new MatchingBatchLocalEngine(
                chainNodeQueryService,
                enterpriseQueryService,
                new EmbeddingProfileTextBuilder(),
                embeddingVectorService,
                ruleBasedMatchingProvider,
                matchingTaskMapper,
                matchingResultMapper,
                persistenceSupport,
                new EmbeddingProperties(),
                new ObjectMapper(),
                transactionTemplate,
                recommendationCacheService);
    }

    @Test
    void executeBatchShouldCompleteCacheHitsWithoutLoadingVectors() {
        MatchingTask task = new MatchingTask();
        task.setId(7L);
        task.setEnterpriseId(1L);
        task.setChainId(2L);
        task.setStatus(MatchingTaskStatus.PROCESSING.name());
        RuleBasedMatchingProvider.EnterpriseInfo enterprise =
                new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(1L);
        enterprise.setName("缓存企业");
        ChainNodeCandidateDTO node = new ChainNodeCandidateDTO();
        node.setNodeId(101L);
        node.setChainId(2L);
        node.setNodeName("动力电池");
        node.setKeywords("动力电池,电芯");
        node.setNodePath("新能源产业链 / 中游 / 动力电池");
        MatchingResult cachedResult = new MatchingResult();
        cachedResult.setNodeId(101L);
        cachedResult.setConfidence(new BigDecimal("0.9000"));
        cachedResult.setReason("缓存推荐");
        cachedResult.setEvidence("{}");
        cachedResult.setRankNo(1);
        MatchingRecommendationCacheService.CacheFingerprint fingerprint =
                new MatchingRecommendationCacheService.CacheFingerprint(
                        1L, 2L, "LOCAL_QWEN:qwen3-embedding:0.6b",
                        "e".repeat(64), "c".repeat(64), 5, 0.7D, 0.3D);

        when(embeddingVectorService.getCurrentModelName())
                .thenReturn("LOCAL_QWEN:qwen3-embedding:0.6b");
        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(2L)).thenReturn(List.of(node));
        when(matchingTaskMapper.selectBatchByIds(List.of(7L))).thenReturn(List.of());
        when(recommendationCacheService.buildFingerprint(
                eq(1L), eq(2L), eq("LOCAL_QWEN:qwen3-embedding:0.6b"),
                anyString(), anyMap())).thenReturn(fingerprint);
        when(recommendationCacheService.findCachedResults(anyCollection()))
                .thenReturn(Map.of(fingerprint, List.of(cachedResult)));

        engine.executeBatch(List.of(task), List.of(1L), List.of(2L));

        verify(embeddingVectorService, never()).getBatchCachedVectors(anyString(), anyList(), anyString(), anyMap());
        verify(embeddingVectorService, never()).getOrCreateVectorsDirectBatch(anyString(), anyMap(), anyString());
        verify(matchingResultMapper).insertBatch(anyList());
        verify(matchingTaskMapper).updateBatchStatusAndSnapshot(anyList());
    }
}
