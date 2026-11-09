package git.ggwzz.matching.llm;

/**
 * 大模型重排序后的单个节点判断。
 */
public record LlmRerankDecision(Long nodeId, double confidence, String reason, String risk) {
}
