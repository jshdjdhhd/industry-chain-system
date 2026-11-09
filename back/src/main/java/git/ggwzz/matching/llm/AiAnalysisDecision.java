package git.ggwzz.matching.llm;

public record AiAnalysisDecision(
        Long nodeId,
        double confidence,
        String suggestion,
        String analysis,
        String risk,
        String analysisSummary,
        String suggestedAnswer) {
}
