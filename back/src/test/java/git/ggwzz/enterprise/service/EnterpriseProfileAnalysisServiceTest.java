package git.ggwzz.enterprise.service;

import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.mapper.EnterpriseProfileAnalysisMapper;
import git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.service.impl.EnterpriseProfileAnalysisServiceImpl;
import git.ggwzz.enterprise.service.impl.EnterpriseRadarMetricCalculator;
import git.ggwzz.enterprise.vo.EnterpriseProfileAnalysisVO;
import git.ggwzz.enterprise.vo.EnterpriseRadarVO;
import git.ggwzz.enterprise.vo.SimilarEnterpriseVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnterpriseProfileAnalysisServiceTest {

    @Mock
    private EnterpriseProfileAnalysisMapper profileAnalysisMapper;

    @Mock
    private EnterpriseSimilarityService enterpriseSimilarityService;

    private EnterpriseProfileAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EnterpriseProfileAnalysisServiceImpl(
                profileAnalysisMapper,
                enterpriseSimilarityService,
                new EnterpriseRadarMetricCalculator());
    }

    @Test
    void analyzeShouldRejectNullEnterpriseId() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.analyze(null, 5));

        assertEquals(400, ex.getCode());
        assertEquals("企业ID不能为空", ex.getMessage());
    }

    @Test
    void analyzeShouldRejectInvalidLimit() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.analyze(1L, 6));

        assertEquals(400, ex.getCode());
        assertEquals("相似企业数量只能是 5、10 或 20", ex.getMessage());
    }

    @Test
    void analyzeShouldReturnNotFoundWhenProfileMissing() {
        when(profileAnalysisMapper.selectProfileById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.analyze(99L, 5));

        assertEquals(404, ex.getCode());
        assertEquals("企业不存在", ex.getMessage());
    }

    @Test
    void analyzeShouldRejectNonActiveEnterprise() {
        EnterpriseProfileAnalysisRow row = activeRow();
        row.setStatus("UNREGISTERED");
        when(profileAnalysisMapper.selectProfileById(1L)).thenReturn(row);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.analyze(1L, 5));

        assertEquals(400, ex.getCode());
        assertEquals("仅已入库企业支持画像分析", ex.getMessage());
    }

    @Test
    void analyzeShouldAggregateRadarAndSimilarEnterprisesWithDefaultLimit() {
        EnterpriseProfileAnalysisRow row = activeRow();
        EnterpriseLinkStatRow linkStat = new EnterpriseLinkStatRow();
        linkStat.setEnterpriseId(1L);
        linkStat.setLinkedNodeCount(1);
        linkStat.setMaxConfidence(new BigDecimal("0.9000"));
        when(profileAnalysisMapper.selectProfileById(1L)).thenReturn(row);
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(profileAnalysisMapper.selectLinkStatsByEnterpriseIds(List.of(1L))).thenReturn(List.of(linkStat));
        SimilarEnterpriseVO similar = new SimilarEnterpriseVO();
        similar.setEnterpriseId(2L);
        similar.setEnterpriseName("相似企业");
        when(enterpriseSimilarityService.findSimilarEnterprises(row, 5)).thenReturn(List.of(similar));

        EnterpriseProfileAnalysisVO result = service.analyze(1L, null);

        assertEquals(1L, result.getEnterpriseId());
        assertEquals("源企业", result.getEnterpriseName());
        assertEquals(6, result.getRadar().getMetrics().size());
        assertEquals(1, result.getSimilarEnterprises().size());
        assertNotNull(result.getGeneratedAt());
        verify(enterpriseSimilarityService).findSimilarEnterprises(row, 5);
    }

    @Test
    void analyzeShouldDegradeWhenSimilarityServiceFails() {
        EnterpriseProfileAnalysisRow row = activeRow();
        when(profileAnalysisMapper.selectProfileById(1L)).thenReturn(row);
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of());
        when(profileAnalysisMapper.selectLinkStatsByEnterpriseIds(List.of(1L))).thenReturn(List.of());
        when(enterpriseSimilarityService.findSimilarEnterprises(row, 10))
                .thenThrow(new IllegalStateException("embedding failed"));

        EnterpriseProfileAnalysisVO result = service.analyze(1L, 10);

        assertEquals(6, result.getRadar().getMetrics().size());
        assertTrue(result.getSimilarEnterprises().isEmpty());
    }

    @Test
    void analyzeRadarShouldReturnMetricsWithoutInvokingSimilarityService() {
        EnterpriseProfileAnalysisRow row = activeRow();
        EnterpriseLinkStatRow linkStat = new EnterpriseLinkStatRow();
        linkStat.setEnterpriseId(1L);
        linkStat.setLinkedNodeCount(1);
        when(profileAnalysisMapper.selectProfileById(1L)).thenReturn(row);
        when(profileAnalysisMapper.selectTagTextsByEnterpriseId(1L)).thenReturn(List.of("新能源"));
        when(profileAnalysisMapper.selectLinkStatsByEnterpriseIds(List.of(1L))).thenReturn(List.of(linkStat));

        EnterpriseRadarVO result = service.analyzeRadar(1L);

        assertEquals(6, result.getMetrics().size());
        verifyNoInteractions(enterpriseSimilarityService);
    }

    @Test
    void warmupVectorsShouldDelegateToSimilarityService() {
        VectorWarmupRequest request = new VectorWarmupRequest();
        VectorWarmupResultVO expected = new VectorWarmupResultVO();
        expected.setSuccessCount(1);
        when(enterpriseSimilarityService.warmupActiveEnterpriseVectors(request)).thenReturn(expected);

        assertEquals(expected, service.warmupVectors(request));
    }

    private EnterpriseProfileAnalysisRow activeRow() {
        EnterpriseProfileAnalysisRow row = new EnterpriseProfileAnalysisRow();
        row.setId(1L);
        row.setEnterpriseName("源企业");
        row.setStatus("ACTIVE");
        row.setPatentInfo("专利1");
        row.setIndustry("动力电池");
        row.setMainProducts("动力电池");
        return row;
    }
}
