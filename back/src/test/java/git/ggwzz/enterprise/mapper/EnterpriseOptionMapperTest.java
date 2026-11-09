package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class EnterpriseOptionMapperTest {

    @Autowired
    private EnterpriseOptionMapper enterpriseOptionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchByKeywordShouldReturnOnlyIdAndNameForActiveEnterprises() {
        jdbcTemplate.update(
                "UPDATE enterprise SET status = 'ACTIVE' WHERE id IN (1, 2)");

        List<EnterpriseOptionVO> options =
                enterpriseOptionMapper.searchActiveOptions("天齐", 20);

        assertEquals(1, options.size());
        EnterpriseOptionVO option = options.get(0);
        assertEquals(1L, option.getId());
        assertEquals("天齐锂业股份有限公司", option.getName());
    }

    @Test
    void searchByKeywordShouldRespectLimit() {
        jdbcTemplate.update(
                "UPDATE enterprise SET status = 'ACTIVE' WHERE id IN (1, 2, 3)");

        List<EnterpriseOptionVO> options =
                enterpriseOptionMapper.searchActiveOptions("", 2);

        assertTrue(options.size() <= 2);
    }

    @Test
    void searchByKeywordShouldReturnAllActiveWhenKeywordBlank() {
        jdbcTemplate.update(
                "UPDATE enterprise SET status = 'ACTIVE' WHERE id IN (1, 2, 3)");

        List<EnterpriseOptionVO> options =
                enterpriseOptionMapper.searchActiveOptions(null, 20);

        assertTrue(options.size() >= 3);
    }
}
