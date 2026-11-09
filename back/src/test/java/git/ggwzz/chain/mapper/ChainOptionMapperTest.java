package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainOptionVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class ChainOptionMapperTest {

    @Autowired
    private ChainOptionMapper chainOptionMapper;

    @Test
    void listOptionsShouldReturnOnlyIdAndNameWithoutStats() {
        List<ChainOptionVO> options = chainOptionMapper.listOptions();

        assertTrue(options.size() >= 2);
        ChainOptionVO first = options.get(0);
        assertEquals(2L, first.getId());
        assertEquals("半导体产业链", first.getName());
    }
}
