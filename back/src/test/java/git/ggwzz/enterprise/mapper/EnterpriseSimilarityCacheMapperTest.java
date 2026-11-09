package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.mapper.projection.EnterpriseSimilarityCacheRow;
import git.ggwzz.enterprise.mapper.projection.TargetHashParam;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class EnterpriseSimilarityCacheMapperTest {

    @Autowired
    private EnterpriseSimilarityCacheMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void upsertShouldInsertThenUpdateByPairModelAndHashes() {
        mapper.upsertSimilarity(1L, 2L, "local-qwen", "source-hash", "target-hash",
                new BigDecimal("0.81234"), "[\"所属行业相同：新能源汽车\"]");
        mapper.upsertSimilarity(1L, 2L, "local-qwen", "source-hash", "target-hash",
                new BigDecimal("0.92345"), "[\"主营产品关键词重合：电池\"]");

        List<EnterpriseSimilarityCacheRow> rows = mapper.selectMatchedCache(
                1L,
                "local-qwen",
                "source-hash",
                List.of(new TargetHashParam(2L, "target-hash")));

        assertEquals(1, rows.size());
        assertEquals(2L, rows.get(0).getTargetEnterpriseId());
        assertEquals(new BigDecimal("0.92345"), rows.get(0).getSimilarity());
        assertTrue(rows.get(0).getReasons().contains("电池"));
    }

    @Test
    void deletedCacheShouldNotMatch() {
        mapper.upsertSimilarity(1L, 2L, "local-qwen", "source-hash", "target-hash",
                new BigDecimal("0.81234"), null);
        jdbcTemplate.update("UPDATE enterprise_similarity_cache SET is_deleted = 1 WHERE source_enterprise_id = 1");

        List<EnterpriseSimilarityCacheRow> rows = mapper.selectMatchedCache(
                1L,
                "local-qwen",
                "source-hash",
                List.of(new TargetHashParam(2L, "target-hash")));

        assertTrue(rows.isEmpty());
    }
}
