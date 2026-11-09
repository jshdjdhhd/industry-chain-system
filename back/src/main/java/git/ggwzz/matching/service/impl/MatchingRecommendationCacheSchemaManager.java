package git.ggwzz.matching.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Ensures the optional recommendation cache table exists before cache access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingRecommendationCacheSchemaManager {

    private static final String PROBE_SQL =
            "SELECT COUNT(1) FROM matching_recommendation_cache WHERE 1 = 0";

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS matching_recommendation_cache (
                id BIGINT NOT NULL AUTO_INCREMENT,
                enterprise_id BIGINT NOT NULL,
                chain_id BIGINT NOT NULL,
                model_name VARCHAR(100) NOT NULL,
                enterprise_text_hash CHAR(64) NOT NULL,
                chain_nodes_hash CHAR(64) NOT NULL,
                top_k INT NOT NULL,
                vector_weight DECIMAL(6,4) NOT NULL,
                rule_weight DECIMAL(6,4) NOT NULL,
                result_json LONGTEXT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                computed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                expire_at DATETIME NULL,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                is_deleted TINYINT NOT NULL DEFAULT 0,
                PRIMARY KEY (id),
                KEY idx_matching_recommendation_fingerprint (
                    enterprise_id, chain_id, model_name, enterprise_text_hash, chain_nodes_hash,
                    top_k, vector_weight, rule_weight, status, is_deleted
                ),
                KEY idx_matching_recommendation_computed (computed_at)
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private volatile boolean ready;
    private volatile boolean unavailable;

    public boolean ensureReady() {
        if (ready) {
            return true;
        }
        if (unavailable) {
            return false;
        }
        synchronized (this) {
            if (ready) {
                return true;
            }
            if (unavailable) {
                return false;
            }
            try {
                jdbcTemplate.queryForObject(PROBE_SQL, Integer.class);
                ready = true;
                return true;
            } catch (DataAccessException probeEx) {
                return createTableOrDisable(probeEx);
            }
        }
    }

    private boolean createTableOrDisable(DataAccessException probeEx) {
        try {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
            ready = true;
            log.info("matching recommendation cache table is ready");
            return true;
        } catch (DataAccessException createEx) {
            unavailable = true;
            log.warn("matching recommendation cache disabled: {}", rootMessage(createEx, probeEx));
            return false;
        }
    }

    private String rootMessage(Exception primary, Exception fallback) {
        Throwable cursor = primary;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? null : cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = fallback == null ? null : fallback.getMessage();
        }
        return message == null || message.isBlank() ? "schema unavailable" : message;
    }
}
