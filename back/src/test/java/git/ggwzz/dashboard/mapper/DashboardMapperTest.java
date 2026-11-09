package git.ggwzz.dashboard.mapper;

import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.dashboard.mapper.projection.ChainEnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.EnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
import git.ggwzz.dashboard.mapper.projection.NodeSubtreeCountRow;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class DashboardMapperTest {

    @Autowired
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Autowired
    private ChainNodeMapper chainNodeMapper;

    @Autowired
    private MatchingResultMapper matchingResultMapper;

    @Test
    void selectEnterpriseStatsShouldReturnTotalAndLinkedCounts() {
        EnterpriseCountRow stats = enterpriseNodeRelMapper.selectEnterpriseStats();

        assertEquals(4L, stats.getTotal());
        assertEquals(4L, stats.getLinked());
    }

    @Test
    void selectEnterpriseCountByChainShouldGroupByChain() {
        var rows = enterpriseNodeRelMapper.selectEnterpriseCountByChain();

        assertEquals(2, rows.size());
        ChainEnterpriseCountRow chain1 = rows.stream()
                .filter(row -> row.getChainId() == 1L)
                .findFirst()
                .orElseThrow();
        assertEquals(3L, chain1.getEnterpriseCount());

        boolean hasChain2 = rows.stream()
                .anyMatch(row -> row.getChainId() == 2L && row.getEnterpriseCount() == 1L);
        assertTrue(hasChain2);
    }

    @Test
    void selectRootNodeSubtreeCountsShouldUseStoredPathPrefix() {
        var rows = chainNodeMapper.selectRootNodeSubtreeCounts();

        assertEquals(4, rows.size());
        NodeSubtreeCountRow node100 = rows.stream()
                .filter(row -> row.getNodeId() == 100L)
                .findFirst()
                .orElseThrow();
        assertEquals(2L, node100.getEnterpriseCount());

        NodeSubtreeCountRow node103 = rows.stream()
                .filter(row -> row.getNodeId() == 103L)
                .findFirst()
                .orElseThrow();
        assertEquals(1L, node103.getEnterpriseCount());
    }

    @Test
    void selectDecisionSummaryShouldGroupByDecision() {
        var summary = matchingResultMapper.selectDecisionSummary();

        assertEquals(3, summary.size());
        MatchingDecisionRow accepted = summary.get("ACCEPTED");
        assertEquals(2L, accepted.getCnt());
        assertEquals(0.915, accepted.getAvgConfidence(), 0.01);
        assertEquals(1L, summary.get("PENDING").getCnt());
        assertEquals(1L, summary.get("REJECTED").getCnt());
    }

    @Test
    void selectWeeklyRecommendationsShouldGroupByDayOfWeek() {
        var weekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay();

        var rows = matchingResultMapper.selectWeeklyRecommendations(weekStart);

        int recommendations = rows.stream()
                .mapToInt(DayRecommendationRow::getRecommendations)
                .sum();
        assertTrue(recommendations >= 4);
    }
}
