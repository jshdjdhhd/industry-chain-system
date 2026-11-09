package git.ggwzz.matching.service;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RuleBasedMatchingProviderTest {

    @Mock
    private EnterpriseQueryService enterpriseQueryService;

    @Mock
    private ChainNodeQueryService chainNodeQueryService;

    @InjectMocks
    private RuleBasedMatchingProvider ruleBasedMatchingProvider;

    private RuleBasedMatchingProvider.EnterpriseInfo enterprise;
    private ChainNodeCandidateDTO node1;
    private ChainNodeCandidateDTO node2;

    @BeforeEach
    void setUp() {
        enterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        enterprise.setId(1L);
        enterprise.setName("宁德时代新能源科技");
        enterprise.setBusinessScope("锂电池、动力电池、储能电池");
        enterprise.setMainProducts("动力电池、电芯、电池模组");
        enterprise.setTags("新能源、电池制造、锂电池");

        node1 = node(101L, "电池制造", "电池制造,动力电池,电芯,电池模组", "新能源产业链 / 中游 / 电池制造");
        node2 = node(102L, "锂矿开采", "锂矿,锂辉石,碳酸锂", "新能源产业链 / 上游 / 锂矿开采");
    }

    @Test
    void matchShouldReturnResultsWithCorrectOrdering() {
        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(Arrays.asList(node1, node2));

        List<MatchingResult> results = ruleBasedMatchingProvider.match(1L, 1L);

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(101L, results.get(0).getNodeId());
        assertEquals(0, results.get(0).getConfidence().compareTo(new BigDecimal("0.9500")));
        assertTrue(results.get(0).getReason().contains("主营产品命中"));
    }

    @Test
    void matchWithPreloadedInputsShouldNotQueryServices() {
        List<MatchingResult> results = ruleBasedMatchingProvider.match(enterprise, Arrays.asList(node1, node2));

        assertNotNull(results);
        assertFalse(results.isEmpty());
        assertEquals(101L, results.get(0).getNodeId());
        verifyNoInteractions(enterpriseQueryService, chainNodeQueryService);
    }

    @Test
    void matchShouldReturnEmptyListWhenNoHits() {
        ChainNodeCandidateDTO noMatchNode = node(104L, "软件开发", "软件,编程,开发", "数字经济产业链 / 中游 / 软件开发");
        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(Collections.singletonList(noMatchNode));

        List<MatchingResult> results = ruleBasedMatchingProvider.match(1L, 1L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void matchShouldReturnAtMostFiveResults() {
        List<ChainNodeCandidateDTO> nodes = Arrays.asList(
                node(1L, "电池制造", "动力电池,电芯", "新能源产业链 / 中游 / 电池制造"),
                node(2L, "锂电池", "锂电池,锂离子", "新能源产业链 / 中游 / 锂电池"),
                node(3L, "储能电池", "储能,电池", "新能源产业链 / 中游 / 储能电池"),
                node(4L, "动力电池", "动力电池,电芯", "新能源产业链 / 中游 / 动力电池"),
                node(5L, "电池模组", "电池模组,模组", "新能源产业链 / 中游 / 电池模组"),
                node(6L, "电池材料", "正极,负极,电解液", "新能源产业链 / 上游 / 电池材料")
        );

        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(nodes);

        List<MatchingResult> results = ruleBasedMatchingProvider.match(1L, 1L);

        assertNotNull(results);
        assertTrue(results.size() <= 5);
    }

    @Test
    void matchShouldReturnEmptyListWhenEnterpriseNotFound() {
        when(enterpriseQueryService.getById(999L)).thenReturn(null);

        List<MatchingResult> results = ruleBasedMatchingProvider.match(999L, 1L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void matchShouldReturnEmptyListWhenNoNodes() {
        when(enterpriseQueryService.getById(1L)).thenReturn(enterprise);
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(Collections.emptyList());

        List<MatchingResult> results = ruleBasedMatchingProvider.match(1L, 1L);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void matchShouldHitWhenKeywordIsContainedInNodePath() {
        RuleBasedMatchingProvider.EnterpriseInfo fuelCellEnterprise = new RuleBasedMatchingProvider.EnterpriseInfo();
        fuelCellEnterprise.setId(2L);
        fuelCellEnterprise.setName("示例氢能科技有限公司");
        fuelCellEnterprise.setMainProducts("燃料电池电堆");

        ChainNodeCandidateDTO fuelCellNode = node(
                201L,
                "燃料电池系统",
                "燃料电池系统,双极板",
                "氢能产业链 / 中游 / 燃料电池电堆制造");

        when(enterpriseQueryService.getById(2L)).thenReturn(fuelCellEnterprise);
        when(chainNodeQueryService.listByChainId(1L)).thenReturn(Collections.singletonList(fuelCellNode));

        List<MatchingResult> results = ruleBasedMatchingProvider.match(2L, 1L);

        assertEquals(1, results.size());
        assertEquals(201L, results.get(0).getNodeId());
        assertTrue(results.get(0).getEvidence().contains("燃料电池电堆"));
    }

    private ChainNodeCandidateDTO node(Long id, String name, String keywords, String path) {
        ChainNodeCandidateDTO dto = new ChainNodeCandidateDTO();
        dto.setNodeId(id);
        dto.setChainId(1L);
        dto.setNodeName(name);
        dto.setKeywords(keywords);
        dto.setNodePath(path);
        dto.setLevel(3);
        return dto;
    }
}
