package git.ggwzz.chain.mapper;

import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.enums.ChainStageType;
import git.ggwzz.chain.mapper.projection.ChainSummaryCountRow;
import git.ggwzz.chain.mapper.projection.EnterpriseCountSummary;
import git.ggwzz.chain.mapper.projection.NodeEnterpriseCountRow;
import git.ggwzz.chain.mapper.projection.NodeSubtreeEnterpriseCountRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 产业链聚合查询和批量操作 Mapper 集成测试。
 */
@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class ChainPerformanceMapperTest {

    @Autowired
    private ChainMapper chainMapper;

    @Autowired
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Autowired
    private ChainNodeMapper chainNodeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_load_stage_type_from_database() {
        ChainNode stage = chainNodeMapper.selectByChainId(1L).stream()
                .filter(node -> Long.valueOf(100L).equals(node.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(ChainStageType.MIDSTREAM, stage.getStageType());
        assertEquals("/100/", stage.getNodePath());
    }
    @Test
    void selectSummaryCountsByChainIdsShouldCountNodesAndDistinctEnterprises() {
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (2, 103, 'MANUAL', '批量统计测试', 0)");

        Map<Long, ChainSummaryCountRow> counts = chainMapper
                .selectSummaryCountsByChainIds(List.of(1L, 2L))
                .stream()
                .collect(Collectors.toMap(
                        ChainSummaryCountRow::getChainId,
                        row -> row));

        assertEquals(4L, counts.get(1L).getNodeCount());
        assertEquals(3L, counts.get(1L).getEnterpriseCount());
        assertEquals(2L, counts.get(2L).getNodeCount());
        assertEquals(1L, counts.get(2L).getEnterpriseCount());
    }

    @Test
    void selectSummaryCountsByChainIdsShouldExcludeDeletedData() {
        jdbcTemplate.update(
                "UPDATE chain_node SET is_deleted = 1 WHERE id = 101");
        jdbcTemplate.update(
                "UPDATE enterprise_node_rel SET is_deleted = 1 WHERE id = 2");
        jdbcTemplate.update(
                "UPDATE enterprise SET is_deleted = 1 WHERE id = 2");

        ChainSummaryCountRow count = chainMapper
                .selectSummaryCountsByChainIds(List.of(1L))
                .getFirst();

        assertEquals(3L, count.getNodeCount());
        assertEquals(0L, count.getEnterpriseCount());
    }

    @Test
    void selectDirectCountsByChainIdShouldCountDistinctActiveEnterprises() {
        jdbcTemplate.update(
                "UPDATE enterprise_node_rel SET is_deleted = 1 WHERE id = 3");
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (1, 102, 'MANUAL', '有效企业回归', 0)");
        jdbcTemplate.update(
                "INSERT INTO enterprise (id, enterprise_name, status, is_deleted) "
                        + "VALUES (6, '已删除企业', 'ACTIVE', 1)");
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (6, 102, 'MANUAL', '删除企业回归', 0)");

        Map<Long, Long> counts = enterpriseNodeRelMapper
                .selectDirectCountsByChainId(1L)
                .stream()
                .collect(Collectors.toMap(
                        NodeEnterpriseCountRow::getNodeId,
                        NodeEnterpriseCountRow::getDirectEnterpriseCount));

        assertEquals(2, counts.size());
        assertEquals(2L, counts.get(102L));
        assertEquals(1L, counts.get(103L));
        assertFalse(counts.containsKey(101L));
        assertFalse(counts.containsKey(201L));
    }

    @Test
    void selectEnterpriseCountSummaryShouldCountDistinctActiveEnterprises() {
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (1, 100, 'MANUAL', '祖孙节点重复企业', 0)");
        jdbcTemplate.update(
                "INSERT INTO enterprise (id, enterprise_name, status, is_deleted) "
                        + "VALUES (6, '已删除企业', 'ACTIVE', 1)");
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (6, 102, 'MANUAL', '删除企业回归', 0)");

        EnterpriseCountSummary summary =
                enterpriseNodeRelMapper.selectEnterpriseCountSummary(
                        1L, 100L, "/100/%");

        assertEquals(1L, summary.getDirectEnterpriseCount());
        assertEquals(2L, summary.getTotalEnterpriseCount());
    }

    @Test
    void selectSubtreeCountsByChainIdShouldDeduplicateActiveEnterprises() {
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (1, 100, 'MANUAL', '祖孙节点重复企业', 0)");
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (2, 101, 'MANUAL', '兄弟节点重复企业', 0)");
        jdbcTemplate.update(
                "INSERT INTO enterprise (id, enterprise_name, status, is_deleted) "
                        + "VALUES (6, '已删除企业', 'ACTIVE', 1)");
        jdbcTemplate.update(
                "INSERT INTO enterprise_node_rel "
                        + "(enterprise_id, node_id, source, reason, is_deleted) "
                        + "VALUES (6, 102, 'MANUAL', '删除企业回归', 0)");

        Map<Long, Long> counts = enterpriseNodeRelMapper
                .selectSubtreeCountsByChainId(1L)
                .stream()
                .collect(Collectors.toMap(
                        NodeSubtreeEnterpriseCountRow::getNodeId,
                        NodeSubtreeEnterpriseCountRow::getSubtreeEnterpriseCount));

        assertEquals(4, counts.size());
        assertEquals(2L, counts.get(100L));
        assertEquals(2L, counts.get(101L));
        assertEquals(1L, counts.get(102L));
        assertEquals(1L, counts.get(103L));
    }

    @Test
    void selectSubtreeCountsByChainIdShouldReturnZeroForEmptyNode() {
        Map<Long, Long> counts = enterpriseNodeRelMapper
                .selectSubtreeCountsByChainId(2L)
                .stream()
                .collect(Collectors.toMap(
                        NodeSubtreeEnterpriseCountRow::getNodeId,
                        NodeSubtreeEnterpriseCountRow::getSubtreeEnterpriseCount));

        assertEquals(2, counts.size());
        assertEquals(1L, counts.get(201L));
        assertEquals(0L, counts.get(202L));
    }

    @Test
    void logicalDeleteByChainIdShouldOnlyDeleteTargetChainRelations() {
        int affected = enterpriseNodeRelMapper.logicalDeleteByChainId(1L);

        assertEquals(3, affected);
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enterprise_node_rel r "
                        + "JOIN chain_node n ON n.id = r.node_id "
                        + "WHERE n.chain_id = 1 AND r.is_deleted = 1",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM enterprise_node_rel WHERE id = 4",
                Integer.class));
    }

    @Test
    void updateSubtreeShouldRewriteAllPathsAndOnlyRootMetadata() {
        int affected = chainNodeMapper.updateSubtree(
                1L,
                100L,
                103L,
                9,
                "/100/",
                "/100/%",
                "/103/100/",
                1);

        assertEquals(3, affected);
        assertNode(100L, 103L, "/103/100/", 2, 9);
        assertNode(101L, 100L, "/103/100/101/", 3, 1);
        assertNode(102L, 100L, "/103/100/102/", 3, 2);
        assertNode(103L, null, "/103/", 1, 3);
        assertNode(201L, null, "/201/", 1, 1);
    }

    @Test
    void updateSubtreeShouldMoveLeafToRootWithoutChangingSortOrder() {
        int affected = chainNodeMapper.updateSubtree(
                1L,
                101L,
                null,
                null,
                "/100/101/",
                "/100/101/%",
                "/101/",
                -1);

        assertEquals(1, affected);
        assertNode(101L, null, "/101/", 1, 1);
        assertNode(102L, 100L, "/100/102/", 2, 2);
    }

    @Test
    void performanceIndexMigrationShouldCreateExpectedIndexes() {
        ClassPathResource migration = new ClassPathResource(
                "db/migration/V9__chain_query_performance_indexes.sql");
        assertTrue(migration.exists());
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(migration);
        populator.execute(
                Objects.requireNonNull(jdbcTemplate.getDataSource()));

        Set<String> indexNames = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT LOWER(index_name) "
                        + "FROM information_schema.indexes "
                        + "WHERE LOWER(table_name) IN "
                        + "('chain_node', 'enterprise_node_rel')",
                String.class));

        assertTrue(indexNames.contains(
                "idx_chain_node_chain_deleted_path"));
        assertTrue(indexNames.contains(
                "idx_chain_node_chain_deleted_sort"));
        assertTrue(indexNames.contains(
                "idx_enterprise_node_rel_node_deleted"));
    }

    private void assertNode(
            Long id,
            Long expectedParentId,
            String expectedPath,
            int expectedLevel,
            int expectedSortOrder) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT parent_id, node_path, node_level, sort_order "
                        + "FROM chain_node WHERE id = ?",
                id);
        Object parentId = row.get("parent_id");
        Long actualParentId = parentId == null
                ? null
                : ((Number) parentId).longValue();
        assertEquals(expectedParentId, actualParentId);
        assertEquals(expectedPath, row.get("node_path"));
        assertEquals(
                expectedLevel,
                ((Number) row.get("node_level")).intValue());
        assertEquals(
                expectedSortOrder,
                ((Number) row.get("sort_order")).intValue());
    }
}
