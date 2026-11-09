package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.llm.AiAnalysisDecision;
import git.ggwzz.matching.llm.AiAnalysisPromptBuilder;
import git.ggwzz.matching.llm.AiAnalysisResultParser;
import git.ggwzz.matching.llm.MatchingLlmProperties;
import git.ggwzz.matching.service.MatchingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class SingleEnterpriseAiAnalysisMatchingProvider implements MatchingProvider {

    private static final String MODEL_NAME = "LLM_ANALYSIS_V1";

    private final EnterpriseQueryService enterpriseQueryService;
    private final ChainNodeQueryService chainNodeQueryService;
    private final LlmClient llmClient;
    private final AiAnalysisPromptBuilder promptBuilder;
    private final AiAnalysisResultParser resultParser;
    private final MatchingLlmProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public List<MatchingResult> match(Long enterpriseId, Long chainId) {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = enterpriseQueryService.getById(enterpriseId);
        if (enterprise == null) {
            log.warn("enterprise not found for AI analysis: enterpriseId={}", enterpriseId);
            return List.of();
        }
        List<ChainNodeCandidateDTO> nodes = chainNodeQueryService.listByChainId(chainId);
        if (nodes == null || nodes.isEmpty()) {
            log.warn("chain nodes not found for AI analysis: chainId={}", chainId);
            return List.of();
        }
        LlmChatCompletion completion = llmClient.complete(
                promptBuilder.build(enterprise, nodes, properties.getAnalysisTopK()));
        Set<Long> allowedNodeIds = nodes.stream()
                .map(ChainNodeCandidateDTO::getNodeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return resultParser.parse(completion.content(), allowedNodeIds, properties.getAnalysisTopK()).stream()
                .map(decision -> toMatchingResult(decision, completion))
                .toList();
    }

    private MatchingResult toMatchingResult(AiAnalysisDecision decision, LlmChatCompletion completion) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(decision.nodeId());
        result.setConfidence(BigDecimal.valueOf(decision.confidence()).setScale(4, RoundingMode.HALF_UP));
        result.setReason(buildReason(decision));
        result.setEvidence(buildEvidence(decision, completion));
        return result;
    }

    private String buildReason(AiAnalysisDecision decision) {
        // 直接返回 LLM 的完整分析文本
        String analysis = decision.analysis();
        if (analysis != null && !analysis.isBlank()) {
            return analysis;
        }
        // 降级处理
        StringBuilder reason = new StringBuilder();
        appendSentence(reason, "建议回答", decision.suggestedAnswer());
        appendSentence(reason, "节点建议", decision.suggestion());
        appendSentence(reason, "分析依据", decision.analysis());
        appendSentence(reason, "风险提示", decision.risk());
        return reason.toString();
    }

    private void appendSentence(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" ");
        }
        builder.append(label).append("：").append(stripTrailingPunctuation(value)).append("。");
    }

    private String buildEvidence(AiAnalysisDecision decision, LlmChatCompletion completion) {
        try {
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("model", MODEL_NAME);
            evidence.put("prompt_version", AiAnalysisPromptBuilder.PROMPT_VERSION);
            evidence.put("analysis_summary", safe(decision.analysisSummary()));
            evidence.put("suggested_answer", safe(decision.suggestedAnswer()));
            evidence.put("suggestion", safe(decision.suggestion()));
            evidence.put("analysis", safe(decision.analysis()));
            evidence.put("risk", safe(decision.risk()));
            evidence.put("llm_provider", properties.getProvider());
            evidence.put("llm_model", completion.model());
            evidence.put("llm_fallback", false);
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception ex) {
            log.warn("build AI analysis evidence failed", ex);
            return "{\"model\":\"LLM_ANALYSIS_V1\",\"llm_fallback\":false}";
        }
    }

    private String stripTrailingPunctuation(String value) {
        return value.replaceAll("[。；;,.，\\s]+$", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
