package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.embedding.EmbeddingProfileTextBuilder;
import git.ggwzz.matching.embedding.EmbeddingProperties;
import git.ggwzz.matching.embedding.EmbeddingVectorService;
import git.ggwzz.matching.embedding.VectorMath;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.service.MatchingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V2 混合智能挂链推荐提供者。
 */
@Slf4j
@Component
public class HybridEmbeddingMatchingProvider implements MatchingProvider {

    private static final String MODEL_NAME = "HYBRID_EMBEDDING_V2";
    private static final String ENTERPRISE_OBJECT = "ENTERPRISE";
    private static final String CHAIN_NODE_OBJECT = "CHAIN_NODE";

    private final EnterpriseQueryService enterpriseQueryService;
    private final ChainNodeQueryService chainNodeQueryService;
    private final EmbeddingProfileTextBuilder profileTextBuilder;
    private final EmbeddingVectorService embeddingVectorService;
    private final RuleBasedMatchingProvider ruleBasedMatchingProvider;
    private final EmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;
    private final MatchingReasonFormatter reasonFormatter;

    @Autowired
    public HybridEmbeddingMatchingProvider(EnterpriseQueryService enterpriseQueryService,
                                           ChainNodeQueryService chainNodeQueryService,
                                           EmbeddingProfileTextBuilder profileTextBuilder,
                                           EmbeddingVectorService embeddingVectorService,
                                           RuleBasedMatchingProvider ruleBasedMatchingProvider,
                                           EmbeddingProperties embeddingProperties,
                                           ObjectMapper objectMapper,
                                           MatchingReasonFormatter reasonFormatter) {
        this.enterpriseQueryService = enterpriseQueryService;
        this.chainNodeQueryService = chainNodeQueryService;
        this.profileTextBuilder = profileTextBuilder;
        this.embeddingVectorService = embeddingVectorService;
        this.ruleBasedMatchingProvider = ruleBasedMatchingProvider;
        this.embeddingProperties = embeddingProperties;
        this.objectMapper = objectMapper;
        this.reasonFormatter = reasonFormatter;
    }

    public HybridEmbeddingMatchingProvider(EnterpriseQueryService enterpriseQueryService,
                                           ChainNodeQueryService chainNodeQueryService,
                                           EmbeddingProfileTextBuilder profileTextBuilder,
                                           EmbeddingVectorService embeddingVectorService,
                                           RuleBasedMatchingProvider ruleBasedMatchingProvider) {
        this(enterpriseQueryService,
                chainNodeQueryService,
                profileTextBuilder,
                embeddingVectorService,
                ruleBasedMatchingProvider,
                new EmbeddingProperties(),
                new ObjectMapper(),
                new MatchingReasonFormatter(new ObjectMapper()));
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public List<MatchingResult> match(Long enterpriseId, Long chainId) {
        return matchCandidates(enterpriseId, chainId, embeddingProperties.getHybrid().getTopK());
    }

    public List<MatchingResult> matchCandidates(Long enterpriseId, Long chainId, int topK) {
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = enterpriseQueryService.getById(enterpriseId);
        if (enterprise == null) {
            log.warn("enterprise not found: enterpriseId={}", enterpriseId);
            return List.of();
        }

        List<ChainNodeCandidateDTO> nodes = chainNodeQueryService.listByChainId(chainId);
        if (nodes == null || nodes.isEmpty()) {
            log.warn("chain nodes not found: chainId={}", chainId);
            return List.of();
        }

        String enterpriseProfile = profileTextBuilder.buildEnterpriseProfile(enterprise);
        Map<Long, MatchingResult> ruleResults = ruleBasedMatchingProvider.match(enterpriseId, chainId)
                .stream()
                .collect(Collectors.toMap(MatchingResult::getNodeId, result -> result,
                        (left, right) -> left, LinkedHashMap::new));

        List<MatchingResult> ruleFastPath = ruleOnlyFastPathResults(ruleResults, nodes, enterpriseProfile, topK);
        if (!ruleFastPath.isEmpty()) {
            log.info("rule fast path hit, skip embedding: enterpriseId={}, chainId={}", enterpriseId, chainId);
            return ruleFastPath;
        }

        try {
            float[] enterpriseVector = embeddingVectorService.getOrCreateVector(
                    ENTERPRISE_OBJECT, enterpriseId, enterpriseProfile);
            List<ScoredNode> scoredNodes = new ArrayList<>();
            for (ChainNodeCandidateDTO node : nodes) {
                String nodeProfile = profileTextBuilder.buildNodeProfile(node);
                float[] nodeVector = embeddingVectorService.getOrCreateVector(
                        CHAIN_NODE_OBJECT, node.getNodeId(), nodeProfile);
                double vectorScore = Math.max(0.0D, VectorMath.cosineSimilarity(enterpriseVector, nodeVector));
                MatchingResult ruleResult = ruleResults.get(node.getNodeId());
                double ruleScore = ruleResult == null || ruleResult.getConfidence() == null
                        ? 0.0D : ruleResult.getConfidence().doubleValue();
                double finalScore = calculateFinalScore(vectorScore, ruleScore);

                scoredNodes.add(new ScoredNode(node, nodeProfile, vectorScore, ruleScore, finalScore, ruleResult));
            }

            return scoredNodes.stream()
                    .sorted(Comparator.comparing(ScoredNode::finalScore).reversed())
                    .limit(Math.max(1, topK))
                    .map(scored -> toMatchingResult(scored, enterpriseProfile))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("embedding fallback to rules: enterpriseId={}, chainId={}, reason={}",
                    enterpriseId, chainId, ex.getClass().getSimpleName());
            log.debug("embedding fallback detail", ex);
            return fallbackRuleResults(ruleResults, topK, ex);
        }
    }

    private double calculateFinalScore(double vectorScore, double ruleScore) {
        double score = embeddingProperties.getHybrid().getVectorWeight() * vectorScore
                + embeddingProperties.getHybrid().getRuleWeight() * ruleScore;
        return Math.min(0.99D, Math.max(0.0D, score));
    }

    private MatchingResult toMatchingResult(ScoredNode scored, String enterpriseProfile) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(scored.node().getNodeId());
        result.setConfidence(BigDecimal.valueOf(scored.finalScore()).setScale(4, RoundingMode.HALF_UP));
        result.setReason(buildReason(scored));
        result.setEvidence(buildEvidence(scored, enterpriseProfile));
        return result;
    }

    private String buildReason(ScoredNode scored) {
        MatchingResult temp = new MatchingResult();
        temp.setNodeId(scored.node().getNodeId());
        temp.setConfidence(BigDecimal.valueOf(scored.finalScore()).setScale(4, RoundingMode.HALF_UP));
        temp.setEvidence(buildEvidence(scored, ""));
        return reasonFormatter.formatLocalReason(temp, "");
    }

    private String buildEvidence(ScoredNode scored, String enterpriseProfile) {
        try {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("model", MODEL_NAME);
            evidence.put("vector_similarity", round(scored.vectorScore()));
            evidence.put("rule_confidence", round(scored.ruleScore()));
            evidence.put("final_score", round(scored.finalScore()));
            evidence.put("below_vector_threshold",
                    scored.vectorScore() < embeddingProperties.getHybrid().getMinVectorScore());
            evidence.put("enterprise_profile", enterpriseProfile);
            evidence.put("node_profile", scored.nodeProfile());
            if (scored.ruleResult() != null) {
                evidence.put("rule_evidence", scored.ruleResult().getEvidence());
            }
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception ex) {
            log.warn("build hybrid evidence failed", ex);
            return "{\"model\":\"HYBRID_EMBEDDING_V2\"}";
        }
    }

    private List<MatchingResult> fallbackRuleResults(Map<Long, MatchingResult> ruleResults, int topK, Exception ex) {
        return ruleResults.values().stream()
                .sorted(Comparator.comparing(MatchingResult::getConfidence,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(1, topK))
                .map(ruleResult -> toRuleFallbackResult(ruleResult, ex))
                .collect(Collectors.toList());
    }

    private MatchingResult toRuleFallbackResult(MatchingResult ruleResult, Exception ex) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(ruleResult.getNodeId());
        result.setConfidence(ruleResult.getConfidence());
        result.setEvidence(buildRuleFallbackEvidence(ruleResult, ex));
        result.setReason(reasonFormatter.formatLocalReason(result,
                "embedding 服务不可用，本次已回退到关键词规则结果；建议人工复核后再采纳。"));
        return result;
    }

    private List<MatchingResult> ruleOnlyFastPathResults(Map<Long, MatchingResult> ruleResults,
                                                         List<ChainNodeCandidateDTO> nodes,
                                                         String enterpriseProfile,
                                                         int topK) {
        if (ruleResults.isEmpty()) {
            return List.of();
        }
        Map<Long, ChainNodeCandidateDTO> nodeMap = nodes.stream()
                .collect(Collectors.toMap(ChainNodeCandidateDTO::getNodeId,
                        node -> node, (left, right) -> left, LinkedHashMap::new));
        List<MatchingResult> sorted = ruleResults.values().stream()
                .sorted(Comparator.comparing(MatchingResult::getConfidence,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        double topConfidence = confidenceAsDouble(sorted.get(0));
        double secondConfidence = sorted.size() > 1 ? confidenceAsDouble(sorted.get(1)) : 0.0D;
        if (topConfidence < embeddingProperties.getHybrid().getRuleOnlyFastPathConfidence()
                || topConfidence - secondConfidence < embeddingProperties.getHybrid().getRuleOnlyFastPathGap()) {
            return List.of();
        }
        return sorted.stream()
                .limit(Math.max(1, topK))
                .map(ruleResult -> toRuleFastPathResult(
                        ruleResult, nodeMap.get(ruleResult.getNodeId()), enterpriseProfile))
                .collect(Collectors.toList());
    }

    private MatchingResult toRuleFastPathResult(MatchingResult ruleResult,
                                                ChainNodeCandidateDTO node,
                                                String enterpriseProfile) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(ruleResult.getNodeId());
        result.setConfidence(ruleResult.getConfidence());
        result.setEvidence(buildRuleFastPathEvidence(ruleResult, node, enterpriseProfile));
        result.setReason(reasonFormatter.formatLocalReason(result,
                "本地规则已形成高置信判断，已跳过 embedding 计算和大模型精排。"));
        return result;
    }

    private String buildRuleFastPathEvidence(MatchingResult ruleResult,
                                             ChainNodeCandidateDTO node,
                                             String enterpriseProfile) {
        try {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("model", MODEL_NAME);
            evidence.put("embedding_skipped", true);
            evidence.put("skip_reason", "RULE_HIGH_CONFIDENCE_FAST_PATH");
            evidence.put("rule_confidence", ruleResult.getConfidence());
            evidence.put("final_score", ruleResult.getConfidence());
            evidence.put("enterprise_profile", enterpriseProfile);
            if (node != null) {
                evidence.put("node_profile", profileTextBuilder.buildNodeProfile(node));
            }
            evidence.put("rule_evidence", ruleResult.getEvidence());
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception ex) {
            log.warn("build fast-path evidence failed", ex);
            return "{\"model\":\"HYBRID_EMBEDDING_V2\",\"embedding_skipped\":true}";
        }
    }

    private String buildRuleFallbackEvidence(MatchingResult ruleResult, Exception ex) {
        try {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("model", MODEL_NAME);
            evidence.put("embedding_fallback", true);
            evidence.put("fallback_model", "RULE_BASED");
            evidence.put("fallback_reason", ex.getClass().getSimpleName());
            evidence.put("rule_confidence", ruleResult.getConfidence());
            evidence.put("final_score", ruleResult.getConfidence());
            evidence.put("rule_evidence", ruleResult.getEvidence());
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception buildEx) {
            log.warn("build fallback evidence failed", buildEx);
            return "{\"model\":\"HYBRID_EMBEDDING_V2\",\"embedding_fallback\":true}";
        }
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private double confidenceAsDouble(MatchingResult result) {
        return result.getConfidence() == null ? 0.0D : result.getConfidence().doubleValue();
    }

    private record ScoredNode(
            ChainNodeCandidateDTO node,
            String nodeProfile,
            double vectorScore,
            double ruleScore,
            double finalScore,
            MatchingResult ruleResult) {
    }
}
