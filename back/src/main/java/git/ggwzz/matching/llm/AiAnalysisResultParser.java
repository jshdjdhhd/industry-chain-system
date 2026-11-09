package git.ggwzz.matching.llm;

import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * AI 分析结果解析器 —— 现在 LLM 返回自然语言分析，不再解析 JSON。
 * 保留类结构以兼容现有调用链。
 */
@Slf4j
@Component
public class AiAnalysisResultParser {

    /**
     * 从 LLM 自然语言输出中提取分析决策。
     * 由于 LLM 现在输出自然语言而非 JSON，这里返回一个包含完整分析文本的单条决策。
     *
     * @param llmOutput      LLM 输出的完整分析文本
     * @param allowedNodeIds 允许的节点 ID（用于过滤，自然语言模式下不再严格过滤）
     * @param topK           最多返回的结果数（自然语言模式下返回 1 条完整分析）
     * @return 包含完整分析文本的决策列表
     */
    public List<AiAnalysisDecision> parse(String llmOutput, Set<Long> allowedNodeIds, int topK) {
        if (llmOutput == null || llmOutput.isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 未返回人工智能分析结果");
        }

        String analysisText = llmOutput.trim();
        log.info("AI analysis output length: {} chars", analysisText.length());

        // 尝试从自然语言中提取推荐的节点 ID（LLM 可能在分析中提到节点名）
        // 但主要以分析文本为主
        Long bestNodeId = extractBestNodeId(analysisText, allowedNodeIds);
        double confidence = bestNodeId != null ? 0.75 : 0.5;

        // 从分析文本中提取摘要（前 200 字符）
        String summary = analysisText.length() > 200
                ? analysisText.substring(0, 200) + "..."
                : analysisText;

        AiAnalysisDecision decision = new AiAnalysisDecision(
                bestNodeId != null ? bestNodeId : (allowedNodeIds != null && !allowedNodeIds.isEmpty() ? allowedNodeIds.iterator().next() : null),
                confidence,
                "AI 分析建议",
                analysisText,
                "请人工复核 AI 分析结果",
                summary,
                analysisText);

        return List.of(decision);
    }

    /**
     * 尝试从分析文本中提取最可能的节点 ID。
     * 简单策略：如果 LLM 提到了某个节点名，就用那个节点。
     */
    private Long extractBestNodeId(String text, Set<Long> allowedNodeIds) {
        // 自然语言模式下，我们无法可靠地从文本中提取 node_id
        // 返回 null，让调用方使用默认策略
        return null;
    }
}
