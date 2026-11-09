package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.llm.LlmRerankDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将算法证据转成面向业务人员可读的推荐理由。
 */
@Component
@RequiredArgsConstructor
public class MatchingReasonFormatter {

    private static final int REASON_LIMIT = 950;

    private final ObjectMapper objectMapper;

    public String formatLocalReason(MatchingResult candidate, String decisionNote) {
        JsonNode evidence = parseEvidence(candidate.getEvidence());
        double confidence = confidence(candidate);
        double vectorSimilarity = numeric(evidence, "vector_similarity", -1.0D);
        double ruleConfidence = numeric(evidence, "rule_confidence", -1.0D);
        String nodePath = firstNonBlank(
                profileValue(evidence.path("node_profile").asText(""), "节点路径"),
                "候选节点");
        List<String> keywords = keywordMatches(evidence);

        StringBuilder reason = new StringBuilder("综合推荐依据：");
        reason.append("企业画像与候选节点“").append(nodePath).append("”存在业务关联");
        if (!keywords.isEmpty()) {
            reason.append("，命中关键词“").append(String.join("、", keywords)).append("”");
        }
        if (vectorSimilarity >= 0.0D) {
            reason.append("；语义相似度 ").append(format(vectorSimilarity, 4));
        }
        if (ruleConfidence >= 0.0D) {
            reason.append("，规则置信度 ").append(format(ruleConfidence, 2));
        }
        reason.append("，综合置信度 ").append(format(confidence, 2))
                .append("，属于").append(confidenceBand(confidence)).append("。");
        if (decisionNote != null && !decisionNote.isBlank()) {
            reason.append(decisionNote.trim());
        } else if (confidence < 0.75D) {
            reason.append("建议人工重点核验主营产品是否直接落在该节点，而不是仅存在泛化关联。");
        }
        return limit(reason.toString());
    }

    public String formatLlmReason(MatchingResult candidate, LlmRerankDecision decision) {
        String llmReason = decision.reason() == null ? "" : decision.reason().trim();
        String localReason = formatLocalReason(candidate, "");
        StringBuilder reason = new StringBuilder();
        if (!llmReason.isBlank()) {
            reason.append("大模型复核结论：").append(stripTrailingPunctuation(llmReason)).append("。");
        }
        reason.append(localReason);
        String risk = decision.risk() == null ? "" : decision.risk().trim();
        if (!risk.isBlank() && !"无".equals(risk) && !"无明显风险".equals(risk)) {
            reason.append("风险提示：").append(stripTrailingPunctuation(risk)).append("。");
        }
        return limit(reason.toString());
    }

    private JsonNode parseEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(evidence);
            return node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> keywordMatches(JsonNode evidence) {
        Set<String> keywords = new LinkedHashSet<>();
        collectKeywords(evidence.path("rule_evidence"), keywords);
        collectKeywords(evidence.path("v21_evidence").path("rule_evidence"), keywords);
        return new ArrayList<>(keywords).stream().limit(8).toList();
    }

    private void collectKeywords(JsonNode ruleEvidence, Set<String> keywords) {
        if (ruleEvidence == null || ruleEvidence.isMissingNode() || ruleEvidence.isNull()) {
            return;
        }
        JsonNode ruleNode = ruleEvidence;
        if (ruleEvidence.isTextual()) {
            try {
                ruleNode = objectMapper.readTree(ruleEvidence.asText());
            } catch (Exception ex) {
                return;
            }
        }
        JsonNode keywordMatch = ruleNode.path("keyword_match");
        if (!keywordMatch.isArray()) {
            return;
        }
        for (JsonNode keyword : keywordMatch) {
            String value = keyword.asText("").trim();
            if (!value.isBlank()) {
                keywords.add(value);
            }
        }
    }

    private String profileValue(String profile, String label) {
        if (profile == null || profile.isBlank()) {
            return "";
        }
        String prefix = label + "：";
        for (String line : profile.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private double numeric(JsonNode node, String fieldName, double fallback) {
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? value.asDouble() : fallback;
    }

    private double confidence(MatchingResult result) {
        return result.getConfidence() == null ? 0.0D : result.getConfidence().doubleValue();
    }

    private String confidenceBand(double confidence) {
        if (confidence >= 0.85D) {
            return "高置信推荐";
        }
        if (confidence >= 0.65D) {
            return "中等置信推荐，需要人工确认后入链";
        }
        return "低置信候选，不建议自动推荐";
    }

    private String format(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String stripTrailingPunctuation(String value) {
        return value.replaceAll("[。；;,.，\\s]+$", "");
    }

    private String limit(String value) {
        if (value == null || value.length() <= REASON_LIMIT) {
            return value;
        }
        return value.substring(0, REASON_LIMIT - 3) + "...";
    }
}
