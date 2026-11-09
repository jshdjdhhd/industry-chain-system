package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.llm.LlmChatCompletion;
import git.ggwzz.llm.LlmClient;
import git.ggwzz.matching.llm.MatchingLlmProperties;
import git.ggwzz.matching.llm.LlmRerankDecision;
import git.ggwzz.matching.llm.LlmRerankPromptBuilder;
import git.ggwzz.matching.llm.LlmRerankResultParser;
import git.ggwzz.matching.service.MatchingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V3 大模型重排序智能挂链推荐提供者。
 *
 * <p>先复用 V2.1 embedding + 规则召回候选，再调用 MiMo/OpenAI-compatible
 * 大模型进行精排和解释。大模型不可用时自动回退 V2.1 结果。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmRerankMatchingProvider implements MatchingProvider {

    private static final String MODEL_NAME = "LLM_RERANK_V3";
    private static final String FALLBACK_MODEL_NAME = "HYBRID_EMBEDDING_V2";

    private final HybridEmbeddingMatchingProvider hybridEmbeddingMatchingProvider;
    private final LlmClient llmClient;
    private final LlmRerankPromptBuilder promptBuilder;
    private final LlmRerankResultParser resultParser;
    private final MatchingLlmProperties properties;
    private final ObjectMapper objectMapper;
    private final MatchingReasonFormatter reasonFormatter;

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public List<MatchingResult> match(Long enterpriseId, Long chainId) {
        int candidateTopK = Math.max(properties.getCandidateTopK(), properties.getResultTopK());
        List<MatchingResult> candidates = hybridEmbeddingMatchingProvider.matchCandidates(
                enterpriseId, chainId, candidateTopK);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<MatchingResult> recommendableCandidates = filterRecommendableCandidates(candidates);
        if (recommendableCandidates.isEmpty()) {
            log.info("V3 未发现达到推荐阈值的候选，返回低置信候选供人工判断: enterpriseId={}, chainId={}, minConfidence={}",
                    enterpriseId, chainId, properties.getMinResultConfidence());
            return lowConfidenceResults(candidates);
        }
        if (shouldSkipLlmRerank(recommendableCandidates)) {
            log.info("V3 命中高置信快速路径，跳过 LLM 精排: enterpriseId={}, chainId={}, threshold={}",
                    enterpriseId, chainId, properties.getSkipRerankConfidenceThreshold());
            return fastPathResults(recommendableCandidates);
        }

        try {
            LlmChatCompletion completion = llmClient.complete(
                    promptBuilder.build(recommendableCandidates, properties.getResultTopK()));
            Set<Long> allowedNodeIds = recommendableCandidates.stream()
                    .map(MatchingResult::getNodeId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<LlmRerankDecision> decisions = resultParser.parse(
                    completion.content(), allowedNodeIds, properties.getResultTopK());
            if (decisions.isEmpty()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "LLM 未返回可用候选节点");
            }
            return buildRerankedResults(recommendableCandidates, decisions, completion);
        } catch (Exception ex) {
            log.warn("V3 大模型重排序失败，回退 V2.1 推荐: enterpriseId={}, chainId={}, reason={}",
                    enterpriseId, chainId, ex.getClass().getSimpleName());
            log.debug("V3 大模型重排序失败详情", ex);
            return fallbackResults(recommendableCandidates, ex);
        }
    }

    private List<MatchingResult> buildRerankedResults(List<MatchingResult> candidates,
                                                     List<LlmRerankDecision> decisions,
                                                     LlmChatCompletion completion) {
        Map<Long, MatchingResult> candidateMap = candidates.stream()
                .collect(Collectors.toMap(MatchingResult::getNodeId, item -> item,
                        (left, right) -> left, LinkedHashMap::new));
        List<MatchingResult> results = new ArrayList<>();
        Set<Long> usedNodeIds = new LinkedHashSet<>();

        for (LlmRerankDecision decision : decisions) {
            MatchingResult candidate = candidateMap.get(decision.nodeId());
            if (candidate == null || !usedNodeIds.add(decision.nodeId())) {
                continue;
            }
            if (!isRecommendable(decision.confidence())) {
                continue;
            }
            results.add(toLlmResult(candidate, decision, completion, false));
        }

        for (MatchingResult candidate : candidates) {
            if (results.size() >= properties.getResultTopK()) {
                break;
            }
            if (usedNodeIds.add(candidate.getNodeId())) {
                LlmRerankDecision decision = new LlmRerankDecision(
                        candidate.getNodeId(),
                        confidenceAsDouble(candidate),
                        reasonFormatter.formatLocalReason(candidate,
                                "大模型未返回该候选，沿用 V2.1 排名作为补位；建议人工复核后再采纳。"),
                        "大模型未返回该候选，沿用 V2.1 排名作为补位");
                results.add(toLlmResult(candidate, decision, completion, true));
            }
        }

        return results.stream()
                .limit(Math.max(1, properties.getResultTopK()))
                .collect(Collectors.toList());
    }

    private List<MatchingResult> fallbackResults(List<MatchingResult> candidates, Exception ex) {
        return candidates.stream()
                .sorted(Comparator.comparing(MatchingResult::getConfidence,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, properties.getResultTopK()))
                .map(candidate -> toFallbackResult(candidate, ex))
                .collect(Collectors.toList());
    }

    private boolean shouldSkipLlmRerank(List<MatchingResult> candidates) {
        List<MatchingResult> sorted = candidates.stream()
                .sorted(Comparator.comparing(MatchingResult::getConfidence,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        if (sorted.isEmpty()) {
            return false;
        }
        MatchingResult top = sorted.get(0);
        double topConfidence = confidenceAsDouble(top);
        double highConfidenceThreshold = properties.getSkipRerankConfidenceThreshold();
        if (highConfidenceThreshold > 0.0D && highConfidenceThreshold <= 1.0D
                && topConfidence >= highConfidenceThreshold) {
            return true;
        }

        double secondConfidence = sorted.size() > 1 ? confidenceAsDouble(sorted.get(1)) : 0.0D;
        if (topConfidence >= properties.getClearWinnerMinConfidenceThreshold()
                && topConfidence - secondConfidence >= properties.getClearWinnerGapThreshold()) {
            return true;
        }

        JsonNode evidence = parseEvidence(top.getEvidence());
        double ruleConfidence = evidence.path("rule_confidence").asDouble(0.0D);
        double vectorSimilarity = evidence.path("vector_similarity").asDouble(0.0D);
        return ruleConfidence >= properties.getStrongRuleConfidenceThreshold()
                && vectorSimilarity >= properties.getStrongEvidenceMinVectorScore();
    }

    private List<MatchingResult> fastPathResults(List<MatchingResult> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(MatchingResult::getConfidence,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, properties.getResultTopK()))
                .map(this::toSkippedRerankResult)
                .collect(Collectors.toList());
    }

    private List<MatchingResult> lowConfidenceResults(List<MatchingResult> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(MatchingResult::getConfidence,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, properties.getResultTopK()))
                .map(this::toLowConfidenceResult)
                .collect(Collectors.toList());
    }

    private MatchingResult toLlmResult(MatchingResult candidate,
                                      LlmRerankDecision decision,
                                      LlmChatCompletion completion,
                                      boolean notRankedByLlm) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(candidate.getNodeId());
        result.setConfidence(round(decision.confidence()));
        result.setReason(reasonFormatter.formatLlmReason(candidate, decision));
        result.setEvidence(buildLlmEvidence(candidate, decision, completion, notRankedByLlm));
        return result;
    }

    private MatchingResult toFallbackResult(MatchingResult candidate, Exception ex) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(candidate.getNodeId());
        result.setConfidence(candidate.getConfidence());
        result.setEvidence(buildFallbackEvidence(candidate, ex));
        result.setReason(reasonFormatter.formatLocalReason(candidate,
                "大模型复核不可用，本次采用 V2.1 本地召回结果；请结合业务资料人工确认。"));
        return result;
    }

    private MatchingResult toSkippedRerankResult(MatchingResult candidate) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(candidate.getNodeId());
        result.setConfidence(candidate.getConfidence());
        result.setEvidence(buildSkippedRerankEvidence(candidate));
        result.setReason(reasonFormatter.formatLocalReason(candidate,
                "本地召回已经形成明确判断，已跳过大模型精排以提升响应速度。"));
        return result;
    }

    private MatchingResult toLowConfidenceResult(MatchingResult candidate) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(candidate.getNodeId());
        result.setConfidence(candidate.getConfidence());
        result.setEvidence(buildLowConfidenceEvidence(candidate));
        result.setReason(reasonFormatter.formatLocalReason(candidate,
                "当前置信度低于推荐阈值 " + properties.getMinResultConfidence()
                        + "，不建议自动挂链；该候选仅作为人工复核参考，需结合企业主营产品、经营范围和节点定义后再决定。"));
        return result;
    }

    private List<MatchingResult> filterRecommendableCandidates(List<MatchingResult> candidates) {
        return candidates.stream()
                .filter(candidate -> isRecommendable(confidenceAsDouble(candidate)))
                .collect(Collectors.toList());
    }

    private boolean isRecommendable(double confidence) {
        double threshold = properties.getMinResultConfidence();
        if (threshold <= 0.0D) {
            return true;
        }
        return confidence >= Math.min(0.99D, threshold);
    }

    private String buildLlmEvidence(MatchingResult candidate,
                                    LlmRerankDecision decision,
                                    LlmChatCompletion completion,
                                    boolean notRankedByLlm) {
        try {
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("model", MODEL_NAME);
            evidence.put("prompt_version", LlmRerankPromptBuilder.PROMPT_VERSION);
            evidence.put("prompt_profile", LlmRerankPromptBuilder.PROMPT_PROFILE);
            evidence.put("llm_provider", properties.getProvider());
            evidence.put("llm_model", completion.model());
            evidence.put("llm_fallback", false);
            evidence.put("llm_not_ranked", notRankedByLlm);
            evidence.put("llm_confidence", decision.confidence());
            evidence.put("reason", decision.reason());
            evidence.put("risk", decision.risk());
            evidence.put("v21_confidence", confidenceAsDouble(candidate));
            evidence.put("v21_reason", safe(candidate.getReason()));
            evidence.set("v21_evidence", parseEvidence(candidate.getEvidence()));
            copyV21Scores(candidate, evidence);
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception ex) {
            log.warn("构建 V3 evidence 失败", ex);
            return "{\"model\":\"LLM_RERANK_V3\",\"llm_fallback\":false}";
        }
    }

    private String buildFallbackEvidence(MatchingResult candidate, Exception ex) {
        try {
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("model", MODEL_NAME);
            evidence.put("prompt_version", LlmRerankPromptBuilder.PROMPT_VERSION);
            evidence.put("prompt_profile", LlmRerankPromptBuilder.PROMPT_PROFILE);
            evidence.put("llm_provider", properties.getProvider());
            evidence.put("llm_model", properties.getModel());
            evidence.put("llm_fallback", true);
            evidence.put("fallback_model", FALLBACK_MODEL_NAME);
            evidence.put("fallback_reason", ex.getClass().getSimpleName());
            evidence.put("v21_confidence", confidenceAsDouble(candidate));
            evidence.put("v21_reason", safe(candidate.getReason()));
            evidence.set("v21_evidence", parseEvidence(candidate.getEvidence()));
            copyV21Scores(candidate, evidence);
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception buildEx) {
            log.warn("构建 V3 fallback evidence 失败", buildEx);
            return "{\"model\":\"LLM_RERANK_V3\",\"llm_fallback\":true}";
        }
    }

    private String buildSkippedRerankEvidence(MatchingResult candidate) {
        try {
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("model", MODEL_NAME);
            evidence.put("prompt_version", LlmRerankPromptBuilder.PROMPT_VERSION);
            evidence.put("prompt_profile", LlmRerankPromptBuilder.PROMPT_PROFILE);
            evidence.put("llm_provider", properties.getProvider());
            evidence.put("llm_model", properties.getModel());
            evidence.put("llm_fallback", false);
            evidence.put("llm_skipped", true);
            evidence.put("fallback_model", FALLBACK_MODEL_NAME);
            evidence.put("fallback_reason", "HIGH_CONFIDENCE_FAST_PATH");
            evidence.put("skip_rerank_confidence_threshold", properties.getSkipRerankConfidenceThreshold());
            evidence.put("v21_confidence", confidenceAsDouble(candidate));
            evidence.put("v21_reason", safe(candidate.getReason()));
            evidence.set("v21_evidence", parseEvidence(candidate.getEvidence()));
            copyV21Scores(candidate, evidence);
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception buildEx) {
            log.warn("构建 V3 fast-path evidence 失败", buildEx);
            return "{\"model\":\"LLM_RERANK_V3\",\"llm_skipped\":true}";
        }
    }

    private String buildLowConfidenceEvidence(MatchingResult candidate) {
        try {
            ObjectNode evidence = objectMapper.createObjectNode();
            evidence.put("model", MODEL_NAME);
            evidence.put("prompt_version", LlmRerankPromptBuilder.PROMPT_VERSION);
            evidence.put("prompt_profile", LlmRerankPromptBuilder.PROMPT_PROFILE);
            evidence.put("llm_provider", properties.getProvider());
            evidence.put("llm_model", properties.getModel());
            evidence.put("llm_fallback", false);
            evidence.put("llm_skipped", true);
            evidence.put("recommendation_level", "LOW_CONFIDENCE");
            evidence.put("below_min_result_confidence", true);
            evidence.put("min_result_confidence", properties.getMinResultConfidence());
            evidence.put("v21_confidence", confidenceAsDouble(candidate));
            evidence.put("v21_reason", safe(candidate.getReason()));
            evidence.set("v21_evidence", parseEvidence(candidate.getEvidence()));
            copyV21Scores(candidate, evidence);
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception buildEx) {
            log.warn("构建 V3 low-confidence evidence 失败", buildEx);
            return "{\"model\":\"LLM_RERANK_V3\",\"recommendation_level\":\"LOW_CONFIDENCE\"}";
        }
    }

    private void copyV21Scores(MatchingResult candidate, ObjectNode evidence) {
        JsonNode v21 = parseEvidence(candidate.getEvidence());
        evidence.put("vector_similarity", v21.path("vector_similarity").asDouble(0.0D));
        evidence.put("rule_confidence", v21.path("rule_confidence").asDouble(0.0D));
        evidence.put("v21_final_score", v21.path("final_score").asDouble(confidenceAsDouble(candidate)));
    }

    private JsonNode parseEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(evidence);
            return node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ex) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("raw", evidence);
            return wrapper;
        }
    }

    private BigDecimal round(double value) {
        double clamped = Math.min(0.99D, Math.max(0.0D, value));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }

    private double confidenceAsDouble(MatchingResult result) {
        return result.getConfidence() == null ? 0.0D : result.getConfidence().doubleValue();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
