package git.ggwzz.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 健康分析报告 LLM 响应解析器。
 *
 * <p>从大模型返回的 JSON 字符串中提取 {@code markdown} 字段。由于通用 LLM 客户端
 * 使用 {@code response_format=json_object}，正常情况下响应为一个 JSON 对象；
 * 但若模型在 JSON 外包了 markdown code fence 或多余文本，先用括号配平方式提取
 * 首个完整 JSON 对象再解析，提升容错能力。</p>
 *
 * <p>解析失败（非 JSON、缺少 markdown 字段、正文为空、入参为空）一律抛
 * {@link IllegalStateException}，由 {@link AnalysisReportWorker} 统一落日志后置 FAILED
 * 并写 error_message，本类不再单独输出 warn 日志（避免同一失败记两次）。</p>
 */
@Slf4j
@Service
public class AnalysisReportResultParser {

    private final ObjectMapper objectMapper;

    public AnalysisReportResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 LLM 响应内容中解析 markdown 正文。
     *
     * @param content LLM 响应原文，可能为 null
     * @return markdown 正文
     * @throws IllegalStateException 当响应无法解析、缺少 markdown 字段或正文为空时
     */
    public String parseMarkdown(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM 响应解析失败：响应为空");
        }
        String json = extractJsonObject(content);
        if (json == null) {
            throw new IllegalStateException("LLM 响应解析失败：未找到 JSON 对象");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("LLM 响应解析失败：JSON 格式错误");
        }
        JsonNode markdownNode = root.get("markdown");
        if (markdownNode == null || markdownNode.isNull()) {
            throw new IllegalStateException("LLM 响应解析失败：markdown 字段缺失");
        }
        String markdown = markdownNode.asText();
        if (markdown.isBlank()) {
            throw new IllegalStateException("LLM 响应解析失败：markdown 字段为空");
        }
        return markdown;
    }

    /**
     * 从可能包含 code fence 或多余文本的响应中提取首个完整 JSON 对象。
     *
     * <p>先剥离外层 markdown code fence；再用括号配平方式定位首个 {@code {} 开始、
     * 括号配平的完整 JSON 对象（字符串内的括号不计入配平）。相比贪婪正则
     * {@code \{.*\}}，括号配平只取首个闭合的 JSON 对象，不会因 JSON 后附含
     * {@code }} 的自然语言误提取到末尾大括号。</p>
     *
     * @param content LLM 响应原文
     * @return JSON 对象字符串，未找到返回 null
     */
    private String extractJsonObject(String content) {
        String text = stripOuterMarkdownFence(content.trim());
        int start = text.indexOf('{');
        while (start >= 0) {
            int end = findMatchingJsonEnd(text, start);
            if (end > start) {
                return text.substring(start, end + 1);
            }
            start = text.indexOf('{', start + 1);
        }
        return null;
    }

    private String stripOuterMarkdownFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineBreak = trimmed.indexOf('\n');
        if (firstLineBreak < 0) {
            return trimmed;
        }
        // 查找闭合的 ```，支持没有闭合的情况（LLM 输出被截断）
        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence > firstLineBreak) {
            return trimmed.substring(firstLineBreak + 1, lastFence).trim();
        }
        // 没有闭合的 ```，去掉开头的 ```json 行，返回剩余内容
        return trimmed.substring(firstLineBreak + 1).trim();
    }

    /**
     * 从 start 位置的 {@code {} 开始，按括号配平找到对应的闭合 {@code }}，
     * 字符串内的括号不计入配平。返回闭合 {@code }} 的索引，未找到返回 -1。
     */
    private int findMatchingJsonEnd(String text, int start) {
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
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
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
