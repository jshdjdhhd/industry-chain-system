package git.ggwzz.dashboard.service;

import git.ggwzz.chain.entity.Chain;
import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
import git.ggwzz.dashboard.service.impl.DashboardServiceImpl;
import git.ggwzz.dashboard.vo.DashboardOverviewVO;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.operationlog.mapper.SysOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private EnterpriseMapper enterpriseMapper;

    @Mock
    private ChainMapper chainMapper;

    @Mock
    private ChainNodeMapper chainNodeMapper;

    @Mock
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Mock
    private MatchingResultMapper matchingResultMapper;

    @Mock
    private SysOperationLogMapper operationLogMapper;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void getOverviewShouldAssembleAggregationResults() {
        Chain chain = chain(1L, "新能源汽车产业链");
        ChainNode root = node(100L, 1L, null, "中游", 1);
        ChainNode child = node(101L, 1L, 100L, "动力电池", 2);
        List<EnterpriseNodeRel> relations = LongStream.rangeClosed(1, 60)
                .mapToObj(id -> rel(id, 100L))
                .toList();

        when(enterpriseMapper.selectCount(any())).thenReturn(100L);
        when(chainMapper.selectList(any()))
                .thenReturn(List.of(chain))
                .thenReturn(List.of(chain));
        when(chainNodeMapper.selectList(any()))
                .thenReturn(List.of(root, child))
                .thenReturn(List.of(root))
                .thenReturn(List.of(root, child));
        when(enterpriseNodeRelMapper.selectList(any()))
                .thenReturn(relations);
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(4L);
        when(matchingResultMapper.selectDecisionSummary()).thenReturn(Map.of(
                MatchingDecision.PENDING.name(),
                decisionRow(MatchingDecision.PENDING, 1, 0.50),
                MatchingDecision.ACCEPTED.name(),
                decisionRow(MatchingDecision.ACCEPTED, 2, 0.84)));
        when(matchingResultMapper.selectWeeklyRecommendations(any()))
                .thenReturn(List.of(recommendationRow(2, 3, 2)));
        when(matchingResultMapper.selectCount(isNull())).thenReturn(3L);
        when(matchingResultMapper.selectCount(notNull())).thenReturn(2L);
        when(operationLogMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

        DashboardOverviewVO result = dashboardService.getOverview();

        assertNotNull(result);
        assertEquals(4, result.getMetrics().size());
        DashboardOverviewVO.Metric total = result.getMetrics().stream()
                .filter(metric -> "企业总数".equals(metric.getLabel()))
                .findFirst()
                .orElseThrow();
        assertEquals(100L, total.getValue());
        DashboardOverviewVO.Metric linked = result.getMetrics().stream()
                .filter(metric -> "已入库企业".equals(metric.getLabel()))
                .findFirst()
                .orElseThrow();
        assertEquals(60L, linked.getValue());
        assertEquals(2, result.getChainDistribution().size());
        assertEquals(
                "新能源汽车产业链",
                result.getChainDistribution().getFirst().getName());
        assertEquals(
                "未挂接",
                result.getChainDistribution().get(1).getName());
        assertEquals(1, result.getNodeStatistics().size());
        assertEquals(1, result.getMatchingSummary().getPending());
        assertEquals(2, result.getMatchingSummary().getAccepted());
        assertEquals(4, result.getMatchingSummary().getManual());
    }

    @Test
    void getOverviewShouldHandleEmptyAggregationResults() {
        when(enterpriseMapper.selectCount(any())).thenReturn(0L);
        when(chainMapper.selectList(any()))
                .thenReturn(Collections.emptyList());
        when(enterpriseNodeRelMapper.selectList(any()))
                .thenReturn(Collections.emptyList());
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);
        when(matchingResultMapper.selectDecisionSummary())
                .thenReturn(Collections.emptyMap());
        when(matchingResultMapper.selectWeeklyRecommendations(any()))
                .thenReturn(Collections.emptyList());
        when(matchingResultMapper.selectCount(isNull())).thenReturn(0L);
        when(operationLogMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

        DashboardOverviewVO result = dashboardService.getOverview();

        assertNotNull(result);
        assertEquals(4, result.getMetrics().size());
        assertTrue(result.getChainDistribution().isEmpty());
        assertTrue(result.getNodeStatistics().isEmpty());
        assertEquals(0, result.getMatchingSummary().getPending());
        assertEquals(7, result.getWeeklyProgress().size());
    }

    private Chain chain(Long id, String name) {
        Chain chain = new Chain();
        chain.setId(id);
        chain.setChainName(name);
        chain.setDeleted(0);
        return chain;
    }

    private ChainNode node(
            Long id,
            Long chainId,
            Long parentId,
            String name,
            int level) {
        ChainNode node = new ChainNode();
        node.setId(id);
        node.setChainId(chainId);
        node.setParentId(parentId);
        node.setNodeName(name);
        node.setNodeLevel(level);
        node.setDeleted(0);
        return node;
    }

    private EnterpriseNodeRel rel(Long enterpriseId, Long nodeId) {
        EnterpriseNodeRel rel = new EnterpriseNodeRel();
        rel.setEnterpriseId(enterpriseId);
        rel.setNodeId(nodeId);
        rel.setDeleted(0);
        return rel;
    }

    private MatchingDecisionRow decisionRow(
            MatchingDecision decision,
            long count,
            double averageConfidence) {
        MatchingDecisionRow row = new MatchingDecisionRow();
        row.setDecision(decision.name());
        row.setCnt(count);
        row.setAvgConfidence(averageConfidence);
        return row;
    }

    private DayRecommendationRow recommendationRow(
            int dayOfWeek,
            int recommendations,
            int accepted) {
        DayRecommendationRow row = new DayRecommendationRow();
        row.setDayOfWeek(dayOfWeek);
        row.setRecommendations(recommendations);
        row.setAccepted(accepted);
        return row;
    }
}
