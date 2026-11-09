package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class ChainNodeCandidateMapperTest {

    @Autowired
    private ChainNodeCandidateMapper chainNodeCandidateMapper;

    @Test
    void listByChainIdShouldReturnCandidatesWithoutEnterpriseStats() {
        List<ChainNodeCandidateVO> candidates =
                chainNodeCandidateMapper.listByChainId(1L);

        assertFalse(candidates.isEmpty());
        ChainNodeCandidateVO first = candidates.get(0);
        assertEquals(1L, first.getChainId());
        assertNotNull(first.getName());
        assertNotNull(first.getNodePath());
    }
}
