package git.ggwzz.chain.mapper;

import git.ggwzz.chain.entity.EnterpriseNodeRel;
import git.ggwzz.chain.enums.LinkSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link EnterpriseNodeRelMapper} 数据访问测试。
 */
@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class EnterpriseNodeRelMapperTest {

    @Autowired
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void restoreDeletedRelationShouldReactivateAndRefreshLinkMetadata() {
        Timestamp originalCreateTime = jdbcTemplate.queryForObject(
                "SELECT create_time FROM enterprise_node_rel WHERE id = 1",
                Timestamp.class);
        jdbcTemplate.update(
                "UPDATE enterprise_node_rel SET is_deleted = 1 WHERE id = 1");

        EnterpriseNodeRel relation = new EnterpriseNodeRel();
        relation.setEnterpriseId(2L);
        relation.setNodeId(102L);
        relation.setSource(LinkSource.MANUAL.name());
        relation.setReason("重新人工挂接");
        relation.setConfirmedBy(9L);
        relation.setDeleted(0);

        int restored = enterpriseNodeRelMapper.restoreDeletedRelation(relation);

        assertEquals(1, restored);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM enterprise_node_rel WHERE id = 1",
                Integer.class));
        assertEquals(LinkSource.MANUAL.name(), jdbcTemplate.queryForObject(
                "SELECT source FROM enterprise_node_rel WHERE id = 1",
                String.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT confidence FROM enterprise_node_rel WHERE id = 1",
                BigDecimal.class));
        assertEquals("重新人工挂接", jdbcTemplate.queryForObject(
                "SELECT reason FROM enterprise_node_rel WHERE id = 1",
                String.class));
        assertEquals(9L, jdbcTemplate.queryForObject(
                "SELECT confirmed_by FROM enterprise_node_rel WHERE id = 1",
                Long.class));
        assertEquals(originalCreateTime, jdbcTemplate.queryForObject(
                "SELECT create_time FROM enterprise_node_rel WHERE id = 1",
                Timestamp.class));
    }

    @Test
    void restoreDeletedRelationShouldKeepAiConfidenceWhenSourceIsAi() {
        jdbcTemplate.update(
                "UPDATE enterprise_node_rel SET is_deleted = 1, confidence = NULL WHERE id = 1");

        EnterpriseNodeRel relation = new EnterpriseNodeRel();
        relation.setEnterpriseId(2L);
        relation.setNodeId(102L);
        relation.setSource(LinkSource.AI.name());
        relation.setConfidence(new BigDecimal("0.8765"));
        relation.setReason("AI重新推荐");
        relation.setConfirmedBy(8L);
        relation.setDeleted(0);

        int restored = enterpriseNodeRelMapper.restoreDeletedRelation(relation);

        assertEquals(1, restored);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM enterprise_node_rel WHERE id = 1",
                Integer.class));
        assertEquals(LinkSource.AI.name(), jdbcTemplate.queryForObject(
                "SELECT source FROM enterprise_node_rel WHERE id = 1",
                String.class));
        assertEquals(0, new BigDecimal("0.8765").compareTo(
                jdbcTemplate.queryForObject(
                        "SELECT confidence FROM enterprise_node_rel WHERE id = 1",
                        BigDecimal.class)));
        assertEquals("AI重新推荐", jdbcTemplate.queryForObject(
                "SELECT reason FROM enterprise_node_rel WHERE id = 1",
                String.class));
        assertEquals(8L, jdbcTemplate.queryForObject(
                "SELECT confirmed_by FROM enterprise_node_rel WHERE id = 1",
                Long.class));
    }
}
