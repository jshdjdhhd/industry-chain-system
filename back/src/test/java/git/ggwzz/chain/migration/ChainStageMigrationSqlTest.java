package git.ggwzz.chain.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainStageMigrationSqlTest {

    @Test
    void should_require_unique_demo_node_before_stage_backfill()
            throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(
                        "db/migration/V13__backfill_chain_stages.sql")) {
            assertNotNull(input);
            String sql = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");

            assertTrue(sql.contains(
                    ") unique_mapped_child\n"
                            + "  ON unique_mapped_child.candidate_id"
                            + " = child.id"));
            assertEquals(
                    2,
                    countOccurrences(sql, "HAVING COUNT(*) = 1"));
        }
    }

    private int countOccurrences(String text, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }
}
