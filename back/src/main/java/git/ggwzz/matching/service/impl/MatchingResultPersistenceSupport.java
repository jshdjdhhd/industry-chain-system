package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.matching.entity.MatchingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能挂链结果持久化前的字段压缩与前端证据清洗。
 */
@Component
@RequiredArgsConstructor
public class MatchingResultPersistenceSupport {

    private static final int REASON_COLUMN_LIMIT = 1000;
    private static final int EVIDENCE_COLUMN_LIMIT = 1000;

    private final ObjectMapper objectMapper;

    /**
     * 远程联调库的 matching_result.reason/evidence 是 varchar(1000)，写入前压缩字段。
     */
    public void prepareForRemotePersistence(MatchingResult result) {
        result.setReason(limitText(result.getReason(), REASON_COLUMN_LIMIT));
        result.setEvidence(compactEvidence(result.getEvidence()));
    }

    /**
     * 清洗 evidence 字段：从大 JSON 中提取关键词匹配、向量分和规则分等可读信息。
     */
    public String cleanEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(evidence);
            StringBuilder sb = new StringBuilder();

            JsonNode ruleEvidence = root.get("rule_evidence");
            if (ruleEvidence != null && ruleEvidence.isTextual()) {
                JsonNode ruleObj = objectMapper.readTree(ruleEvidence.asText());
                JsonNode keywordMatch = ruleObj.get("keyword_match");
                if (keywordMatch != null && keywordMatch.isArray() && keywordMatch.size() > 0) {
                    sb.append("关键词命中：");
                    for (int i = 0; i < keywordMatch.size(); i++) {
                        if (i > 0) {
                            sb.append("、");
                        }
                        sb.append(keywordMatch.get(i).asText());
                    }
                }
            }

            JsonNode vectorSimilarity = root.get("vector_similarity");
            if (vectorSimilarity != null && vectorSimilarity.isNumber()) {
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append("语义相似度 ").append(String.format("%.4f", vectorSimilarity.asDouble()));
            }

            JsonNode ruleConfidence = root.get("rule_confidence");
            if (ruleConfidence != null && ruleConfidence.isNumber() && ruleConfidence.asDouble() > 0) {
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append("规则置信度 ").append(String.format("%.2f", ruleConfidence.asDouble()));
            }

            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String compactEvidence(String evidence) {
        if (evidence == null || evidence.isBlank() || evidence.length() <= EVIDENCE_COLUMN_LIMIT) {
            return evidence;
        }
        try {
            JsonNode root = objectMapper.readTree(evidence);
            if (!root.isObject()) {
                return limitText(evidence, EVIDENCE_COLUMN_LIMIT);
            }
            ObjectNode compact = objectMapper.createObjectNode();
            copyField(root, compact, "model");
            copyField(root, compact, "prompt_version");
            copyField(root, compact, "prompt_profile");
            copyField(root, compact, "llm_provider");
            copyField(root, compact, "llm_model");
            copyField(root, compact, "llm_fallback");
            copyField(root, compact, "llm_not_ranked");
            copyField(root, compact, "fallback_model");
            copyField(root, compact, "fallback_reason");
            copyField(root, compact, "llm_confidence");
            copyField(root, compact, "recommendation_level");
            copyField(root, compact, "below_min_result_confidence");
            copyField(root, compact, "min_result_confidence");
            copyField(root, compact, "below_vector_threshold");
            copyField(root, compact, "vector_similarity");
            copyField(root, compact, "rule_confidence");
            copyField(root, compact, "v21_final_score");
            copyField(root, compact, "v21_confidence");
            copyTextField(root, compact, "risk", 160);
            copyTextField(root, compact, "reason", 240);
            copyTextField(root, compact, "v21_reason", 240);

            String json = objectMapper.writeValueAsString(compact);
            if (json.length() <= EVIDENCE_COLUMN_LIMIT) {
                return json;
            }
            compact.remove(List.of("reason", "v21_reason", "risk", "prompt_version"));
            json = objectMapper.writeValueAsString(compact);
            return json.length() <= EVIDENCE_COLUMN_LIMIT
                    ? json
                    : limitText(evidence, EVIDENCE_COLUMN_LIMIT);
        } catch (Exception ex) {
            return limitText(evidence, EVIDENCE_COLUMN_LIMIT);
        }
    }

    private void copyField(JsonNode source, ObjectNode target, String fieldName) {
        JsonNode value = source.get(fieldName);
        if (value != null && !value.isNull()) {
            target.set(fieldName, value);
        }
    }

    private void copyTextField(JsonNode source, ObjectNode target, String fieldName, int maxLength) {
        JsonNode value = source.get(fieldName);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            target.put(fieldName, limitText(value.asText(), maxLength));
        }
    }

    private String limitText(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        if (limit <= 3) {
            return value.substring(0, limit);
        }
        return value.substring(0, limit - 3) + "...";
    }
}
