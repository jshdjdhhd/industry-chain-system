package git.ggwzz.enterprise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.mapper.EnterpriseProfileAnalysisMapper;
import git.ggwzz.enterprise.mapper.EnterpriseSimilarityCacheMapper;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseSimilarityCacheRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseTagTextRow;
import git.ggwzz.enterprise.service.impl.EnterpriseProfileTextFactory;
import git.ggwzz.enterprise.service.impl.EnterpriseSimilarityServiceImpl;
import git.ggwzz.enterprise.vo.SimilarEnterpriseVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;
import git.ggwzz.matching.embedding.EmbeddingVectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnterpriseSimilarityServiceTest {

    @Mock
    private EnterpriseProfileAnalysisMapper profileAnalysisMapper;

    @Mock
    private EnterpriseSimilarityCacheMapper similarityCacheMapper;

    @Mock
    private EmbeddingVectorService embeddingVectorService;

    private EnterpriseSimilarityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EnterpriseSimilarityServiceImpl(
                profileAnalysisMapper,
                similarityCacheMapper,
                embeddingVectorService,
                new EnterpriseProfileTextFactory(),
                new ObjectMapper());
    }

    @Test
    void findSimilarEnterprisesShouldOnlyAllowExpectedLimits() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findSimilarEnterprises(activeRow(1L, "源企业"), 6));

        assertEquals(400, ex.getCode());
        assertEquals("相似企业数量只能是 5、10 或 20", ex.getMessage());
    }

    @Test
    void findSimilarEnterprisesShouldRejectNonActiveSource() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        source.setStatus("UNREGISTERED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findSimilarEnterprises(source, 5));

        assertEquals(400, ex.getCode());
        assertEquals("仅已入库企业支持画像分析", ex.getMessage());
    }

    @Test
    void findSimilarEnterprisesShouldReturnActiveCandidatesAndExcludeSource() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        EnterpriseProfileAnalysisRow candidate = activeRow(2L, "候选企业");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of(tagRow(2L, "新能源")));
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F, 0.0F});
        when(profileAnalysisMapper.selectActiveCandidates(1L, 0, 100)).thenReturn(List.of(candidate));
        when(similarityCacheMapper.selectMatchedCache(eq(1L), eq("local-qwen"), any(), anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorsDirectBatch(eq("ENTERPRISE_PROFILE"), anyMap(), eq("local-qwen")))
                .thenReturn(Map.of(2L, new float[]{0.9F, 0.1F}));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getEnterpriseId());
        assertEquals("候选企业", result.get(0).getEnterpriseName());
        assertTrue(result.get(0).getReasons().contains("所属行业相同：动力电池"));
        verify(profileAnalysisMapper).selectActiveCandidates(1L, 0, 100);
    }

    @Test
    void findSimilarEnterprisesShouldBatchGenerateMissingCandidateVectors() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        EnterpriseProfileAnalysisRow first = activeRow(2L, "候选企业1");
        EnterpriseProfileAnalysisRow second = activeRow(3L, "候选企业2");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of(tagRow(2L, "新能源"), tagRow(3L, "新能源")));
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F, 0.0F});
        when(profileAnalysisMapper.selectActiveCandidates(1L, 0, 100)).thenReturn(List.of(first, second));
        when(similarityCacheMapper.selectMatchedCache(eq(1L), eq("local-qwen"), any(), anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorsDirectBatch(eq("ENTERPRISE_PROFILE"), anyMap(), eq("local-qwen")))
                .thenReturn(Map.of(
                        2L, new float[]{0.9F, 0.1F},
                        3L, new float[]{0.8F, 0.2F}
                ));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(2, result.size());
        verify(embeddingVectorService).getOrCreateVectorsDirectBatch(eq("ENTERPRISE_PROFILE"), anyMap(), eq("local-qwen"));
        verify(embeddingVectorService, never())
                .getBatchCachedVectors(eq("ENTERPRISE_PROFILE"), anyList(), eq("local-qwen"), anyMap());
        verify(embeddingVectorService, never())
                .getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(2L), eq("local-qwen"), any());
        verify(embeddingVectorService, never())
                .getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(3L), eq("local-qwen"), any());
    }

    @Test
    void findSimilarEnterprisesShouldFetchCandidateTagsInSingleBatchQuery() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        EnterpriseProfileAnalysisRow first = activeRow(2L, "候选企业1");
        EnterpriseProfileAnalysisRow second = activeRow(3L, "候选企业2");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of(tagRow(2L, "新能源"), tagRow(3L, "新能源")));
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F, 0.0F});
        when(profileAnalysisMapper.selectActiveCandidates(1L, 0, 100)).thenReturn(List.of(first, second));
        when(similarityCacheMapper.selectMatchedCache(eq(1L), eq("local-qwen"), any(), anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorsDirectBatch(eq("ENTERPRISE_PROFILE"), anyMap(), eq("local-qwen")))
                .thenReturn(Map.of(
                        2L, new float[]{0.9F, 0.1F},
                        3L, new float[]{0.8F, 0.2F}
                ));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(2, result.size());
        verify(profileAnalysisMapper).selectTagTextsByEnterpriseIds(anyList());
        verify(profileAnalysisMapper, never()).selectTagTextsByEnterpriseId(2L);
        verify(profileAnalysisMapper, never()).selectTagTextsByEnterpriseId(3L);
    }
    @Test
    void findSimilarEnterprisesShouldScanSameIndustryCandidatesBeforeCrossIndustry() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        source.setIndustry("动力电池");
        EnterpriseProfileAnalysisRow sameIndustry = activeRow(2L, "同行业候选");
        sameIndustry.setIndustry("动力电池");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(profileAnalysisMapper.selectSameIndustryActiveCandidates(eq("动力电池"), eq(1L), eq(0), anyInt()))
                .thenReturn(List.of(sameIndustry));
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of(tagRow(2L, "新能源")));
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F, 0.0F});
        when(similarityCacheMapper.selectMatchedCache(eq(1L), eq("local-qwen"), any(), anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorsDirectBatch(eq("ENTERPRISE_PROFILE"), anyMap(), eq("local-qwen")))
                .thenReturn(Map.of(2L, new float[]{0.9F, 0.1F}));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getEnterpriseId());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(profileAnalysisMapper);
        order.verify(profileAnalysisMapper)
                .selectSameIndustryActiveCandidates(eq("动力电池"), eq(1L), eq(0), anyInt());
        order.verify(profileAnalysisMapper)
                .selectActiveCandidates(eq(1L), eq(0), anyInt());
    }
    @Test
    void cacheHitShouldNotGenerateCandidateVector() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        EnterpriseProfileAnalysisRow candidate = activeRow(2L, "候选企业");
        EnterpriseSimilarityCacheRow cache = new EnterpriseSimilarityCacheRow();
        cache.setTargetEnterpriseId(2L);
        cache.setSimilarity(new BigDecimal("0.92345"));
        cache.setReasons("[\"所属行业相同：动力电池\"]");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of());
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F, 0.0F});
        when(profileAnalysisMapper.selectActiveCandidates(1L, 0, 100)).thenReturn(List.of(candidate));
        when(similarityCacheMapper.selectMatchedCache(eq(1L), eq("local-qwen"), any(), anyList()))
                .thenReturn(List.of(cache));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(new BigDecimal("0.92345"), result.get(0).getSimilarity());
        verify(embeddingVectorService, never())
                .getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(2L), eq("local-qwen"), any());
    }

    @Test
    void candidateVectorFailureShouldFallBackToRuleSimilarity() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        EnterpriseProfileAnalysisRow failedCandidate = activeRow(2L, "失败候选");
        EnterpriseProfileAnalysisRow goodCandidate = activeRow(3L, "成功候选");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of());
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F, 0.0F});
        when(profileAnalysisMapper.selectActiveCandidates(1L, 0, 100)).thenReturn(List.of(failedCandidate, goodCandidate));
        when(similarityCacheMapper.selectMatchedCache(eq(1L), eq("local-qwen"), any(), anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorsDirectBatch(eq("ENTERPRISE_PROFILE"), anyMap(), eq("local-qwen")))
                .thenReturn(Map.of(3L, new float[]{1.0F, 0.0F}));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(2, result.size());
        assertEquals(3L, result.get(0).getEnterpriseId());
        assertEquals(2L, result.get(1).getEnterpriseId());
        assertTrue(result.get(1).getReasons().contains("所属行业相同：动力电池"));
    }

    @Test
    void candidateBatchVectorFailureShouldFallBackToRuleSimilarity() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        EnterpriseProfileAnalysisRow matchingCandidate = activeRow(2L, "匹配候选");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of(tagRow(2L, "新能源")));
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F, 0.0F});
        when(profileAnalysisMapper.selectActiveCandidates(1L, 0, 100)).thenReturn(List.of(matchingCandidate));
        when(similarityCacheMapper.selectMatchedCache(eq(1L), eq("local-qwen"), any(), anyList()))
                .thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorsDirectBatch(eq("ENTERPRISE_PROFILE"), anyMap(), eq("local-qwen")))
                .thenThrow(new IllegalStateException("batch embedding failed"));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getEnterpriseId());
        assertTrue(result.get(0).getReasons().contains("所属行业相同：动力电池"));
    }

    @Test
    void sourceVectorFailureShouldFallBackToRuleSimilarity() {
        EnterpriseProfileAnalysisRow source = activeRow(1L, "源企业");
        EnterpriseProfileAnalysisRow matchingCandidate = activeRow(2L, "匹配候选");
        EnterpriseProfileAnalysisRow unrelatedCandidate = activeRow(3L, "无关候选");
        unrelatedCandidate.setIndustry("芯片设计");
        unrelatedCandidate.setBusinessScope("芯片设计与流片服务");
        unrelatedCandidate.setMainProducts("ASIC,SoC");
        unrelatedCandidate.setCoreTech("集成电路设计");
        unrelatedCandidate.setKeywords("半导体");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenThrow(new IllegalStateException("source embedding failed"));
        when(profileAnalysisMapper.selectActiveCandidates(1L, 0, 100))
                .thenReturn(List.of(matchingCandidate, unrelatedCandidate));
        when(profileAnalysisMapper.selectTagTextsByEnterpriseIds(anyList()))
                .thenReturn(List.of(tagRow(2L, "新能源"), tagRow(3L, "半导体")));

        List<SimilarEnterpriseVO> result = service.findSimilarEnterprises(source, 5);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getEnterpriseId());
        assertEquals("匹配候选", result.get(0).getEnterpriseName());
        assertTrue(result.get(0).getSimilarity().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.get(0).getReasons().contains("所属行业相同：动力电池"));
        assertTrue(result.get(0).getReasons().contains("共享标签：新能源"));
    }

    @Test
    void warmupShouldRejectMoreThanFiveHundredSpecifiedIds() {
        VectorWarmupRequest request = new VectorWarmupRequest();
        request.setEnterpriseIds(LongStream.rangeClosed(1, 501).boxed().toList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.warmupActiveEnterpriseVectors(request));

        assertEquals(400, ex.getCode());
        assertEquals("单次最多预热 500 家企业", ex.getMessage());
    }

    @Test
    void warmupShouldSkipMissingOrNonActiveAndCountFailures() {
        VectorWarmupRequest request = new VectorWarmupRequest();
        request.setEnterpriseIds(List.of(1L, 2L, 3L, 4L));
        EnterpriseProfileAnalysisRow inactive = activeRow(2L, "未入库企业");
        inactive.setStatus("UNREGISTERED");
        when(embeddingVectorService.getCurrentModelName()).thenReturn("local-qwen");
        when(profileAnalysisMapper.selectProfileById(1L)).thenReturn(activeRow(1L, "成功企业"));
        when(profileAnalysisMapper.selectProfileById(2L)).thenReturn(inactive);
        when(profileAnalysisMapper.selectProfileById(3L)).thenReturn(null);
        when(profileAnalysisMapper.selectProfileById(4L)).thenReturn(activeRow(4L, "失败企业"));
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of());
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(4L)).thenReturn(List.of());
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(1L), eq("local-qwen"), any()))
                .thenReturn(new float[]{1.0F});
        when(embeddingVectorService.getOrCreateVectorDirect(eq("ENTERPRISE_PROFILE"), eq(4L), eq("local-qwen"), any()))
                .thenThrow(new IllegalStateException("failed"));

        VectorWarmupResultVO result = service.warmupActiveEnterpriseVectors(request);

        assertEquals(4, result.getTotalCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(2, result.getSkippedCount());
        assertEquals(1, result.getFailureCount());
    }

    private EnterpriseProfileAnalysisRow activeRow(Long id, String name) {
        EnterpriseProfileAnalysisRow row = new EnterpriseProfileAnalysisRow();
        row.setId(id);
        row.setEnterpriseName(name);
        row.setStatus("ACTIVE");
        row.setIndustry("动力电池");
        row.setBusinessScope("动力电池研发、生产和销售");
        row.setMainProducts("动力电池,电芯");
        row.setCoreTech("锂离子电池");
        row.setKeywords("新能源");
        return row;
    }

    private EnterpriseTagTextRow tagRow(Long enterpriseId, String tagName) {
        EnterpriseTagTextRow row = new EnterpriseTagTextRow();
        row.setEnterpriseId(enterpriseId);
        row.setTagName(tagName);
        return row;
    }
}
