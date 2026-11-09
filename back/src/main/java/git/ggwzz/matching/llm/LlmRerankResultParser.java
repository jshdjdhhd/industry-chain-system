package git.ggwzz.matching.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析大模型重排序 JSON 输出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRerankResultParser {

    private final ObjectMapper objectMapper;

    public List<LlmRerankDecision> parse(String content, Set<Long> allowedNodeIds, int topK) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 未返回重排序结果");
        }
        try {
            String cleaned = extractJsonPayload(content);
            log.debug("LLM 解析输入前300字符: {}", cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned);
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode results = resolveResultsArray(root);
            if (results == null || !results.isArray()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 重排序结果格式不正确");
            }
            return parseResults(results, allowedNodeIds, topK);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 重排序结果不是合法 JSON");
        }
    }

    private List<LlmRerankDecision> parseResults(JsonNode results, Set<Long> allowedNodeIds, int topK) {
        int limit = topK <= 0 ? Integer.MAX_VALUE : topK;
        Set<Long> seen = new LinkedHashSet<>();
        List<LlmRerankDecision> decisions = new ArrayList<>();
        for (JsonNode item : results) {
            Long nodeId = readNodeId(item);
            if (nodeId == null || !allowedNodeIds.contains(nodeId) || !seen.add(nodeId)) {
                continue;
            }
            decisions.add(new LlmRerankDecision(
                    nodeId,
                    clampConfidence(item.path("confidence").asDouble(0.0D)),
                    item.path("reason").asText(""),
                    item.path("risk").asText("")));
            if (decisions.size() >= limit) {
                break;
            }
        }
        return decisions;
    }

    private JsonNode resolveResultsArray(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        if (root.has("ranked_results")) {
            return root.get("ranked_results");
        }
        if (root.has("results")) {
            return root.get("results");
        }
        return null;
    }

    private Long readNodeId(JsonNode item) {
        JsonNode snake = item.get("node_id");
        if (snake != null && snake.canConvertToLong()) {
            return snake.asLong();
        }
        JsonNode camel = item.get("nodeId");
        if (camel != null && camel.canConvertToLong()) {
            return camel.asLong();
        }
        return null;
    }

    private double clampConfidence(double confidence) {
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            return 0.0D;
        }
        return Math.min(0.99D, Math.max(0.0D, confidence));
    }

    private String extractJsonPayload(String content) {
        String trimmed = stripOuterMarkdownFence(content.trim());
        String rankedResultsObject = extractJsonObjectContaining(trimmed, "\"ranked_results\"");
        if (rankedResultsObject != null) {
            return rankedResultsObject;
        }
        String resultsObject = extractJsonObjectContaining(trimmed, "\"results\"");
        if (resultsObject != null) {
            return resultsObject;
        }
        String array = extractFirstJsonArray(trimmed);
        return array == null ? trimmed : array;
    }

    private String stripOuterMarkdownFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineBreak = content.indexOf('\n');
        int lastFence = content.lastIndexOf("```");
        if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
            return content.substring(firstLineBreak + 1, lastFence).trim();
        }
        return content;
    }

    private String extractJsonObjectContaining(String text, String key) {
        int keyIndex = text.indexOf(key);
        while (keyIndex >= 0) {
            int start = text.lastIndexOf('{', keyIndex);
            while (start >= 0) {
                int end = findMatchingJsonEnd(text, start, '{', '}');
                if (end > keyIndex) {
                    return text.substring(start, end + 1);
                }
                start = text.lastIndexOf('{', start - 1);
            }
            keyIndex = text.indexOf(key, keyIndex + key.length());
        }
        return null;
    }

    private String extractFirstJsonArray(String text) {
        int start = text.indexOf('[');
        while (start >= 0) {
            int end = findMatchingJsonEnd(text, start, '[', ']');
            if (end > start) {
                return text.substring(start, end + 1);
            }
            start = text.indexOf('[', start + 1);
        }
        return null;
    }

    private int findMatchingJsonEnd(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
                if (depth < 0) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
