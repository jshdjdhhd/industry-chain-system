package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseTagTextRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class EnterpriseProfileAnalysisMapperTest {

    @Autowired
    private EnterpriseProfileAnalysisMapper mapper;

    @Test
    void selectProfileByIdShouldReturnProfileFields() {
        EnterpriseProfileAnalysisRow row = mapper.selectProfileById(2L);

        assertNotNull(row);
        assertEquals(2L, row.getId());
        assertEquals("宁德时代新能源科技股份有限公司", row.getEnterpriseName());
        assertEquals("ACTIVE", row.getStatus());
        assertEquals("动力电池", row.getIndustry());
        assertEquals("动力电池,电芯,模组", row.getMainProducts());
        assertEquals("锂离子电池", row.getCoreTech());
    }

    @Test
    void selectActiveCandidatesShouldExcludeSourceAndOnlyReturnActive() {
        List<EnterpriseProfileAnalysisRow> rows = mapper.selectActiveCandidates(2L, 0, 10);

        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().noneMatch(row -> row.getId().equals(2L)));
        assertTrue(rows.stream().allMatch(row -> "ACTIVE".equals(row.getStatus())));
        assertEquals(List.of(1L, 3L, 5L), rows.stream().map(EnterpriseProfileAnalysisRow::getId).toList());
    }

    @Test
    void selectSameIndustryActiveCandidatesShouldOnlyReturnSameIndustryAndExcludeSource() {
        List<EnterpriseProfileAnalysisRow> firstPage = mapper.selectSameIndustryActiveCandidates(
                "动力电池", 2L, 0, 100);

        assertTrue(firstPage.stream().noneMatch(row -> row.getId().equals(2L)));
        assertTrue(firstPage.stream().allMatch(row -> "ACTIVE".equals(row.getStatus())));
        assertTrue(firstPage.stream().allMatch(row -> "动力电池".equals(row.getIndustry())));
    }

    @Test
    void selectTagTextsByEnterpriseIdShouldReturnOrderedTagNames() {
        List<String> tags = mapper.selectTagTextsByEnterpriseId(2L);

        assertEquals(List.of("中游-动力电池", "锂离子电池"), tags);
    }

    @Test
    void selectTagTextsByEnterpriseIdsShouldReturnTagsOnlyForSpecifiedEnterprises() {
        List<EnterpriseTagTextRow> rows = mapper.selectTagTextsByEnterpriseIds(List.of(2L, 3L, 99L));
        Map<Long, List<String>> tagsById = rows.stream()
                .collect(Collectors.groupingBy(EnterpriseTagTextRow::getEnterpriseId,
                        Collectors.mapping(EnterpriseTagTextRow::getTagName, Collectors.toList())));

        assertEquals(2, rows.size());
        assertEquals(List.of("中游-动力电池", "锂离子电池"), tagsById.get(2L));
        assertFalse(tagsById.containsKey(3L));
        assertFalse(tagsById.containsKey(99L));
    }

    @Test
    void selectLinkStatsByEnterpriseIdsShouldAggregateEffectiveRelations() {
        List<EnterpriseLinkStatRow> rows = mapper.selectLinkStatsByEnterpriseIds(List.of(2L, 3L, 99L));
        Map<Long, EnterpriseLinkStatRow> byEnterpriseId = rows.stream()
                .collect(Collectors.toMap(EnterpriseLinkStatRow::getEnterpriseId, row -> row));

        assertEquals(2, rows.size());
        assertEquals(1, byEnterpriseId.get(2L).getLinkedNodeCount());
        assertEquals(0, new BigDecimal("0.9500").compareTo(byEnterpriseId.get(2L).getMaxConfidence()));
        assertFalse(byEnterpriseId.get(2L).getHasManualLink());
        assertEquals(1, byEnterpriseId.get(3L).getLinkedNodeCount());
        assertTrue(byEnterpriseId.get(3L).getHasManualLink());
    }
}
