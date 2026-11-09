package git.ggwzz.matching.service;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.service.impl.RuleBasedMatchingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
class MatchingQueryServiceIntegrationTest {

    @Autowired
    private EnterpriseQueryService enterpriseQueryService;

    @Autowired
    private ChainNodeQueryService chainNodeQueryService;

    @Test
    void enterpriseQueryServiceShouldLoadEnterpriseAndTagsFromDatabase() {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = enterpriseQueryService.getById(2L);

        assertNotNull(enterprise);
        assertEquals(2L, enterprise.getId());
        assertEquals("宁德时代新能源科技股份有限公司", enterprise.getName());
        assertEquals("动力电池", enterprise.getBusinessScope());
        assertTrue(enterprise.getMainProducts().contains("动力电池,电芯,模组"));
        assertTrue(enterprise.getTags().contains("中游-动力电池"));
    }

    @Test
    void chainNodeQueryServiceShouldLoadDtoContract() {
        List<ChainNodeCandidateDTO> nodes = chainNodeQueryService.listByChainId(1L);

        assertFalse(nodes.isEmpty());
        ChainNodeCandidateDTO batteryNode = nodes.stream()
                .filter(node -> Long.valueOf(102L).equals(node.getNodeId()))
                .findFirst()
                .orElseThrow();
        assertEquals("电池制造", batteryNode.getNodeName());
        assertEquals("电池,锂电池,动力电池,电芯,模组", batteryNode.getKeywords());
        assertEquals("新能源汽车产业链 / 中游 / 电池制造", chainNodeQueryService.buildNodePath(102L));
    }
}
