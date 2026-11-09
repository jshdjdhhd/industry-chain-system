package git.ggwzz.matching.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.matching.embedding.EmbeddingProperties;
import git.ggwzz.matching.embedding.TextHash;
import git.ggwzz.matching.entity.MatchingRecommendationCache;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.mapper.MatchingRecommendationCacheMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 推荐结果缓存服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingRecommendationCacheService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final TypeReference<List<CachedResult>> CACHED_RESULT_LIST =
            new TypeReference<>() {
            };

    private final MatchingRecommendationCacheMapper mapper;
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties embeddingProperties;
    private final MatchingRecommendationCacheSchemaManager schemaManager;

    public CacheFingerprint buildFingerprint(Long enterpriseId,
                                              Long chainId,
                                              String modelName,
                                              String enterpriseProfile,
                                              Map<Long, String> nodeProfiles) {
        String chainSnapshot = nodeProfiles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "\u001F" + nullToEmpty(entry.getValue()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return new CacheFingerprint(
                enterpriseId,
                chainId,
                modelName,
                TextHash.sha256(nullToEmpty(enterpriseProfile)),
                TextHash.sha256(chainSnapshot),
                embeddingProperties.getHybrid().getTopK(),
                embeddingProperties.getHybrid().getVectorWeight(),
                embeddingProperties.getHybrid().getRuleWeight());
    }

    public FastCacheFingerprint buildFastFingerprint(Long enterpriseId,
                                                     Long chainId,
                                                     String modelName,
                                                     String enterpriseProfile) {
        return new FastCacheFingerprint(
                enterpriseId,
                chainId,
                modelName,
                TextHash.sha256(nullToEmpty(enterpriseProfile)),
                embeddingProperties.getHybrid().getTopK(),
                embeddingProperties.getHybrid().getVectorWeight(),
                embeddingProperties.getHybrid().getRuleWeight());
    }

    public Optional<List<MatchingResult>> findCachedResults(CacheFingerprint fingerprint) {
        if (!schemaManager.ensureReady()) {
            return Optional.empty();
        }
        MatchingRecommendationCache cache = mapper.selectActiveByFingerprint(
                fingerprint.enterpriseId(), fingerprint.chainId(), fingerprint.modelName(),
                fingerprint.enterpriseTextHash(), fingerprint.chainNodesHash(),
                fingerprint.topK(), fingerprint.vectorWeight(), fingerprint.ruleWeight());
        return deserializeResults(cache);
    }

    public Map<CacheFingerprint, List<MatchingResult>> findCachedResults(
            Collection<CacheFingerprint> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty() || !schemaManager.ensureReady()) {
            return Map.of();
        }

        Map<BatchCacheKey, List<CacheFingerprint>> fingerprintsByQuery = new LinkedHashMap<>();
        for (CacheFingerprint fingerprint : fingerprints) {
            fingerprintsByQuery.computeIfAbsent(BatchCacheKey.from(fingerprint), ignored -> new ArrayList<>())
                    .add(fingerprint);
        }

        Map<CacheFingerprint, List<MatchingResult>> resultsByFingerprint = new HashMap<>();
        for (Map.Entry<BatchCacheKey, List<CacheFingerprint>> entry : fingerprintsByQuery.entrySet()) {
            BatchCacheKey key = entry.getKey();
            Map<String, CacheFingerprint> requested = new LinkedHashMap<>();
            Set<Long> enterpriseIds = new LinkedHashSet<>();
            Set<String> enterpriseTextHashes = new LinkedHashSet<>();
            for (CacheFingerprint fingerprint : entry.getValue()) {
                requested.put(fingerprintKey(fingerprint), fingerprint);
                enterpriseIds.add(fingerprint.enterpriseId());
                enterpriseTextHashes.add(fingerprint.enterpriseTextHash());
            }

            List<MatchingRecommendationCache> candidates = mapper.selectActiveCandidates(
                    key.chainId(), key.modelName(), key.chainNodesHash(), key.topK(),
                    key.vectorWeight(), key.ruleWeight(), new ArrayList<>(enterpriseIds),
                    new ArrayList<>(enterpriseTextHashes));
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            for (MatchingRecommendationCache cache : candidates) {
                CacheFingerprint fingerprint = requested.get(fingerprintKey(cache));
                if (fingerprint == null || resultsByFingerprint.containsKey(fingerprint)) {
                    continue;
                }
                deserializeResults(cache)
                        .ifPresent(results -> resultsByFingerprint.put(fingerprint, results));
            }
        }
        return resultsByFingerprint;
    }

    public Map<FastCacheFingerprint, List<MatchingResult>> findLatestCachedResults(
            Collection<FastCacheFingerprint> fingerprints) {
        if (fingerprints == null || fingerprints.isEmpty() || !schemaManager.ensureReady()) {
            return Map.of();
        }

        Map<BatchLatestCacheKey, List<FastCacheFingerprint>> fingerprintsByQuery = new LinkedHashMap<>();
        for (FastCacheFingerprint fingerprint : fingerprints) {
            fingerprintsByQuery.computeIfAbsent(BatchLatestCacheKey.from(fingerprint), ignored -> new ArrayList<>())
                    .add(fingerprint);
        }

        Map<FastCacheFingerprint, List<MatchingResult>> resultsByFingerprint = new HashMap<>();
        for (Map.Entry<BatchLatestCacheKey, List<FastCacheFingerprint>> entry : fingerprintsByQuery.entrySet()) {
            BatchLatestCacheKey key = entry.getKey();
            Map<String, FastCacheFingerprint> requested = new LinkedHashMap<>();
            Set<Long> enterpriseIds = new LinkedHashSet<>();
            Set<String> enterpriseTextHashes = new LinkedHashSet<>();
            for (FastCacheFingerprint fingerprint : entry.getValue()) {
                requested.put(fastFingerprintKey(fingerprint), fingerprint);
                enterpriseIds.add(fingerprint.enterpriseId());
                enterpriseTextHashes.add(fingerprint.enterpriseTextHash());
            }

            List<MatchingRecommendationCache> candidates = mapper.selectLatestActiveCandidates(
                    key.chainId(), key.modelName(), key.topK(), key.vectorWeight(), key.ruleWeight(),
                    new ArrayList<>(enterpriseIds), new ArrayList<>(enterpriseTextHashes));
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            for (MatchingRecommendationCache cache : candidates) {
                FastCacheFingerprint fingerprint = requested.get(fastFingerprintKey(cache));
                if (fingerprint == null || resultsByFingerprint.containsKey(fingerprint)) {
                    continue;
                }
                deserializeResults(cache)
                        .ifPresent(results -> resultsByFingerprint.put(fingerprint, results));
            }
        }
        return resultsByFingerprint;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveSuccess(CacheFingerprint fingerprint, List<MatchingResult> results) {
        if (!schemaManager.ensureReady()) {
            return;
        }
        mapper.markActiveDeleted(
                fingerprint.enterpriseId(), fingerprint.chainId(), fingerprint.modelName(),
                fingerprint.enterpriseTextHash(), fingerprint.chainNodesHash(),
                fingerprint.topK(), fingerprint.vectorWeight(), fingerprint.ruleWeight());

        MatchingRecommendationCache cache = new MatchingRecommendationCache();
        cache.setEnterpriseId(fingerprint.enterpriseId());
        cache.setChainId(fingerprint.chainId());
        cache.setModelName(fingerprint.modelName());
        cache.setEnterpriseTextHash(fingerprint.enterpriseTextHash());
        cache.setChainNodesHash(fingerprint.chainNodesHash());
        cache.setTopK(fingerprint.topK());
        cache.setVectorWeight(BigDecimal.valueOf(fingerprint.vectorWeight()).setScale(4, RoundingMode.HALF_UP));
        cache.setRuleWeight(BigDecimal.valueOf(fingerprint.ruleWeight()).setScale(4, RoundingMode.HALF_UP));
        cache.setResultJson(serialize(results));
        cache.setStatus(STATUS_ACTIVE);
        cache.setComputedAt(LocalDateTime.now());
        cache.setDeleted(0);
        mapper.insert(cache);
    }

    private String serialize(List<MatchingResult> results) {
        try {
            List<CachedResult> cachedResults = new ArrayList<>(results.size());
            int fallbackRankNo = 1;
            for (MatchingResult result : results) {
                cachedResults.add(CachedResult.from(result, fallbackRankNo++));
            }
            return objectMapper.writeValueAsString(cachedResults);
        } catch (Exception ex) {
            throw new IllegalStateException("serialize matching recommendation cache failed", ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Optional<List<MatchingResult>> deserializeResults(MatchingRecommendationCache cache) {
        if (cache == null || cache.getResultJson() == null || cache.getResultJson().isBlank()) {
            return Optional.empty();
        }
        try {
            List<CachedResult> cachedResults = objectMapper.readValue(
                    cache.getResultJson(), CACHED_RESULT_LIST);
            return Optional.of(cachedResults.stream()
                    .sorted(Comparator.comparing(
                            CachedResult::rankNo,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(CachedResult::toMatchingResult)
                    .toList());
        } catch (Exception ex) {
            log.warn("failed to parse matching recommendation cache: cacheId={}", cache.getId(), ex);
            return Optional.empty();
        }
    }

    private String fingerprintKey(CacheFingerprint fingerprint) {
        return fingerprint.enterpriseId() + "\u001F"
                + fingerprint.chainId() + "\u001F"
                + fingerprint.modelName() + "\u001F"
                + fingerprint.enterpriseTextHash() + "\u001F"
                + fingerprint.chainNodesHash() + "\u001F"
                + fingerprint.topK() + "\u001F"
                + fingerprint.vectorWeight() + "\u001F"
                + fingerprint.ruleWeight();
    }

    private String fingerprintKey(MatchingRecommendationCache cache) {
        return cache.getEnterpriseId() + "\u001F"
                + cache.getChainId() + "\u001F"
                + cache.getModelName() + "\u001F"
                + cache.getEnterpriseTextHash() + "\u001F"
                + cache.getChainNodesHash() + "\u001F"
                + cache.getTopK() + "\u001F"
                + cache.getVectorWeight().doubleValue() + "\u001F"
                + cache.getRuleWeight().doubleValue();
    }

    private String fastFingerprintKey(FastCacheFingerprint fingerprint) {
        return fingerprint.enterpriseId() + "\u001F"
                + fingerprint.chainId() + "\u001F"
                + fingerprint.modelName() + "\u001F"
                + fingerprint.enterpriseTextHash() + "\u001F"
                + fingerprint.topK() + "\u001F"
                + fingerprint.vectorWeight() + "\u001F"
                + fingerprint.ruleWeight();
    }

    private String fastFingerprintKey(MatchingRecommendationCache cache) {
        return cache.getEnterpriseId() + "\u001F"
                + cache.getChainId() + "\u001F"
                + cache.getModelName() + "\u001F"
                + cache.getEnterpriseTextHash() + "\u001F"
                + cache.getTopK() + "\u001F"
                + cache.getVectorWeight().doubleValue() + "\u001F"
                + cache.getRuleWeight().doubleValue();
    }

    public record CacheFingerprint(
            Long enterpriseId,
            Long chainId,
            String modelName,
            String enterpriseTextHash,
            String chainNodesHash,
            Integer topK,
            Double vectorWeight,
            Double ruleWeight) {
    }

    public record FastCacheFingerprint(
            Long enterpriseId,
            Long chainId,
            String modelName,
            String enterpriseTextHash,
            Integer topK,
            Double vectorWeight,
            Double ruleWeight) {
    }

    private record BatchCacheKey(
            Long chainId,
            String modelName,
            String chainNodesHash,
            Integer topK,
            Double vectorWeight,
            Double ruleWeight) {

        static BatchCacheKey from(CacheFingerprint fingerprint) {
            return new BatchCacheKey(
                    fingerprint.chainId(),
                    fingerprint.modelName(),
                    fingerprint.chainNodesHash(),
                    fingerprint.topK(),
                    fingerprint.vectorWeight(),
                    fingerprint.ruleWeight());
        }
    }

    private record BatchLatestCacheKey(
            Long chainId,
            String modelName,
            Integer topK,
            Double vectorWeight,
            Double ruleWeight) {

        static BatchLatestCacheKey from(FastCacheFingerprint fingerprint) {
            return new BatchLatestCacheKey(
                    fingerprint.chainId(),
                    fingerprint.modelName(),
                    fingerprint.topK(),
                    fingerprint.vectorWeight(),
                    fingerprint.ruleWeight());
        }
    }

    private record CachedResult(
            Long nodeId,
            BigDecimal confidence,
            String reason,
            String evidence,
            Integer rankNo) {

        static CachedResult from(MatchingResult result) {
            return from(result, result.getRankNo() == null ? 1 : result.getRankNo());
        }

        static CachedResult from(MatchingResult result, Integer fallbackRankNo) {
            return new CachedResult(
                    result.getNodeId(),
                    result.getConfidence(),
                    result.getReason(),
                    result.getEvidence(),
                    result.getRankNo() == null ? fallbackRankNo : result.getRankNo());
        }

        MatchingResult toMatchingResult() {
            MatchingResult result = new MatchingResult();
            result.setNodeId(nodeId);
            result.setConfidence(confidence);
            result.setReason(reason);
            result.setEvidence(evidence);
            result.setRankNo(rankNo);
            return result;
        }
    }
}
