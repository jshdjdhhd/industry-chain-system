package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.matching.embedding.EmbeddingProperties;
import git.ggwzz.matching.entity.MatchingEvaluationSample;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.llm.MatchingLlmProperties;
import git.ggwzz.matching.mapper.MatchingEvaluationSampleMapper;
import git.ggwzz.matching.service.MatchingProvider;
import git.ggwzz.matching.vo.MatchingEvaluationReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 智能挂链离线评估服务。
 */
@Slf4j
@Service
public class MatchingEvaluationService {

    public record ProviderSpec(MatchingProvider provider, int topK) {
        public ProviderSpec {
            Objects.requireNonNull(provider, "provider");
            topK = Math.max(1, topK);
        }

        public String providerName() {
            String modelName = provider.getModelName();
            return modelName == null || modelName.isBlank() ? provider.getClass().getSimpleName() : modelName;
        }
    }

    private final MatchingEvaluationSampleMapper sampleMapper;
    private final ObjectMapper objectMapper;
    private final ProviderSpec defaultProviderSpec;
    private final List<ProviderSpec> comparisonProviderSpecs;

    @Autowired
    public MatchingEvaluationService(MatchingEvaluationSampleMapper sampleMapper,
                                     RuleBasedMatchingProvider ruleBasedMatchingProvider,
                                     HybridEmbeddingMatchingProvider hybridEmbeddingMatchingProvider,
                                     LlmRerankMatchingProvider llmRerankMatchingProvider,
                                     EmbeddingProperties embeddingProperties,
                                     MatchingLlmProperties llmProperties,
                                     ObjectMapper objectMapper) {
        this(
                sampleMapper,
                objectMapper,
                new ProviderSpec(llmRerankMatchingProvider, llmProperties.getResultTopK()),
                List.of(
                        new ProviderSpec(ruleBasedMatchingProvider, embeddingProperties.getHybrid().getTopK()),
                        new ProviderSpec(hybridEmbeddingMatchingProvider, embeddingProperties.getHybrid().getTopK()),
                        new ProviderSpec(llmRerankMatchingProvider, llmProperties.getResultTopK())
                )
        );
    }

    public MatchingEvaluationService(MatchingEvaluationSampleMapper sampleMapper,
                                     MatchingProvider matchingProvider,
                                     int topK) {
        this(sampleMapper, new ObjectMapper(), new ProviderSpec(matchingProvider, topK));
    }

    public MatchingEvaluationService(MatchingEvaluationSampleMapper sampleMapper,
                                     ObjectMapper objectMapper,
                                     ProviderSpec... providerSpecs) {
        this(sampleMapper,
                objectMapper,
                providerSpecs[0],
                Arrays.asList(providerSpecs));
    }

    private MatchingEvaluationService(MatchingEvaluationSampleMapper sampleMapper,
                                      ObjectMapper objectMapper,
                                      ProviderSpec defaultProviderSpec,
                                      List<ProviderSpec> comparisonProviderSpecs) {
        this.sampleMapper = sampleMapper;
        this.objectMapper = objectMapper;
        this.defaultProviderSpec = defaultProviderSpec;
        this.comparisonProviderSpecs = comparisonProviderSpecs;
    }

    public MatchingEvaluationReport evaluate(Long chainId) {
        return evaluate(chainId, defaultProviderSpec);
    }

    public List<MatchingEvaluationReport> evaluateAll(Long chainId) {
        return comparisonProviderSpecs.stream()
                .map(providerSpec -> evaluate(chainId, providerSpec))
                .toList();
    }

    private MatchingEvaluationReport evaluate(Long chainId, ProviderSpec providerSpec) {
        List<MatchingEvaluationSample> samples = sampleMapper.selectEnabledByChainId(chainId);
        MatchingEvaluationReport report = new MatchingEvaluationReport();
        report.setProviderName(providerSpec.providerName());
        report.setChainId(chainId);
        report.setTopK(providerSpec.topK());
        report.setSampleCount(samples.size());
        report.setFallbackCount(0);

        if (samples.isEmpty()) {
            report.setRecallAtK(decimal(0.0D));
            report.setTop1Accuracy(decimal(0.0D));
            report.setMeanReciprocalRank(decimal(0.0D));
            return report;
        }

        int hitCount = 0;
        int top1Count = 0;
        int fallbackCount = 0;
        double reciprocalRankSum = 0.0D;

        for (MatchingEvaluationSample sample : samples) {
            List<MatchingResult> results = safeMatch(providerSpec, sample);
            if (hasFallback(results, providerSpec.topK())) {
                fallbackCount++;
            }
            int rank = findExpectedRank(results, sample.getExpectedNodeId(), providerSpec.topK());
            if (rank > 0) {
                hitCount++;
                reciprocalRankSum += 1.0D / rank;
                if (rank == 1) {
                    top1Count++;
                }
            } else {
                report.getMisses().add(buildMiss(sample, results, providerSpec.topK()));
            }
        }

        report.setFallbackCount(fallbackCount);
        report.setRecallAtK(decimal((double) hitCount / samples.size()));
        report.setTop1Accuracy(decimal((double) top1Count / samples.size()));
        report.setMeanReciprocalRank(decimal(reciprocalRankSum / samples.size()));
        return report;
    }

    private List<MatchingResult> safeMatch(ProviderSpec providerSpec, MatchingEvaluationSample sample) {
        try {
            List<MatchingResult> results = providerSpec.provider().match(sample.getEnterpriseId(), sample.getChainId());
            return results == null ? List.of() : results;
        } catch (Exception ex) {
            log.warn("evaluation provider failed: provider={}, enterpriseId={}, chainId={}, reason={}",
                    providerSpec.providerName(), sample.getEnterpriseId(), sample.getChainId(),
                    ex.getClass().getSimpleName());
            log.debug("evaluation provider failure detail", ex);
            return List.of();
        }
    }

    private boolean hasFallback(List<MatchingResult> results, int topK) {
        return results.stream()
                .limit(Math.max(1, topK))
                .anyMatch(this::isFallbackResult);
    }

    private boolean isFallbackResult(MatchingResult result) {
        JsonNode evidence = parseEvidence(result == null ? null : result.getEvidence());
        return evidence.path("llm_fallback").asBoolean(false)
                || evidence.path("embedding_fallback").asBoolean(false);
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

    private int findExpectedRank(List<MatchingResult> results, Long expectedNodeId, int limit) {
        int end = Math.min(limit, results.size());
        for (int index = 0; index < end; index++) {
            if (expectedNodeId.equals(results.get(index).getNodeId())) {
                return index + 1;
            }
        }
        return -1;
    }

    private MatchingEvaluationReport.Miss buildMiss(MatchingEvaluationSample sample,
                                                    List<MatchingResult> results,
                                                    int topK) {
        MatchingEvaluationReport.Miss miss = new MatchingEvaluationReport.Miss();
        miss.setEnterpriseId(sample.getEnterpriseId());
        miss.setExpectedNodeId(sample.getExpectedNodeId());
        miss.setSampleName(sample.getSampleName());
        miss.setReturnedNodeIds(results.stream()
                .limit(topK)
                .map(MatchingResult::getNodeId)
                .toList());
        return miss;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
