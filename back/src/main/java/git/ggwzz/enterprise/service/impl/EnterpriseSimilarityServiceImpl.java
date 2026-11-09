package git.ggwzz.enterprise.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.mapper.EnterpriseProfileAnalysisMapper;
import git.ggwzz.enterprise.mapper.EnterpriseSimilarityCacheMapper;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseSimilarityCacheRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseTagTextRow;
import git.ggwzz.enterprise.mapper.projection.TargetHashParam;
import git.ggwzz.enterprise.service.EnterpriseSimilarityService;
import git.ggwzz.enterprise.vo.SimilarEnterpriseVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;
import git.ggwzz.matching.embedding.EmbeddingVectorService;
import git.ggwzz.matching.embedding.TextHash;
import git.ggwzz.matching.embedding.VectorMath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 企业相似度分析服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseSimilarityServiceImpl implements EnterpriseSimilarityService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String OBJECT_TYPE = "ENTERPRISE_PROFILE";
    private static final int CANDIDATE_BATCH_SIZE = 100;
    private static final int MAX_WARMUP_SIZE = 500;
    private static final BigDecimal HIGH_SIMILARITY = new BigDecimal("0.85000");
    private static final BigDecimal RULE_INDUSTRY_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal RULE_PRODUCT_WEIGHT = new BigDecimal("0.25");
    private static final BigDecimal RULE_TECH_WEIGHT = new BigDecimal("0.15");
    private static final BigDecimal RULE_KEYWORD_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal RULE_TAG_WEIGHT = new BigDecimal("0.15");

    private final EnterpriseProfileAnalysisMapper profileAnalysisMapper;
    private final EnterpriseSimilarityCacheMapper similarityCacheMapper;
    private final EmbeddingVectorService embeddingVectorService;
    private final EnterpriseProfileTextFactory profileTextFactory;
    private final ObjectMapper objectMapper;

    @Override
    public List<SimilarEnterpriseVO> findSimilarEnterprises(EnterpriseProfileAnalysisRow source, int similarLimit) {
        validateLimit(similarLimit);
        if (source == null || source.getId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "企业不存在");
        }
        if (!STATUS_ACTIVE.equals(source.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已入库企业支持画像分析");
        }

        String modelName = embeddingVectorService.getCurrentModelName();
        List<String> sourceTags = profileAnalysisMapper.selectTagTextsByEnterpriseId(source.getId());
        String sourceText = profileTextFactory.buildProfileText(source, sourceTags);
        String sourceHash = TextHash.sha256(sourceText);
        float[] sourceVector;
        try {
            sourceVector = embeddingVectorService.getOrCreateVectorDirect(
                    OBJECT_TYPE, source.getId(), modelName, sourceText);
        } catch (RuntimeException ex) {
            log.warn("enterprise source profile vector failed, use rule fallback: sourceId={}, reason={}",
                    source.getId(), ex.getClass().getSimpleName());
            log.debug("enterprise source profile vector fallback detail", ex);
            return findSimilarEnterprisesByRules(source, sourceTags, similarLimit);
        }

        PriorityQueue<SimilarScore> topScores = new PriorityQueue<>(Comparator.comparing(SimilarScore::similarity));
        scanCandidates(source, source.getIndustry(), sourceTags, sourceHash, sourceVector, modelName, similarLimit, topScores);

        return topScores.stream()
                .sorted(Comparator.comparing(SimilarScore::similarity).reversed())
                .map(SimilarScore::vo)
                .toList();
    }

    private void scanCandidates(EnterpriseProfileAnalysisRow source,
                                 String industry,
                                 List<String> sourceTags,
                                 String sourceHash,
                                 float[] sourceVector,
                                 String modelName,
                                 int similarLimit,
                                 PriorityQueue<SimilarScore> topScores) {
        Set<Long> scoredIds = new LinkedHashSet<>();
        int offset = 0;
        while (true) {
            List<EnterpriseProfileAnalysisRow> candidates = profileAnalysisMapper.selectSameIndustryActiveCandidates(
                    industry, source.getId(), offset, CANDIDATE_BATCH_SIZE);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            scoreBatch(source, sourceTags, sourceHash, sourceVector, modelName, candidates, similarLimit, topScores);
            candidates.forEach(candidate -> scoredIds.add(candidate.getId()));
            if (candidates.size() < CANDIDATE_BATCH_SIZE) {
                break;
            }
            offset += CANDIDATE_BATCH_SIZE;
        }
        offset = 0;
        while (topScores.size() < similarLimit) {
            List<EnterpriseProfileAnalysisRow> candidates = profileAnalysisMapper.selectActiveCandidates(
                    source.getId(), offset, CANDIDATE_BATCH_SIZE);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            List<EnterpriseProfileAnalysisRow> fresh = candidates.stream()
                    .filter(candidate -> !scoredIds.contains(candidate.getId()))
                    .toList();
            if (!fresh.isEmpty()) {
                scoreBatch(source, sourceTags, sourceHash, sourceVector, modelName, fresh, similarLimit, topScores);
                fresh.forEach(candidate -> scoredIds.add(candidate.getId()));
            }
            if (candidates.size() < CANDIDATE_BATCH_SIZE) {
                break;
            }
            offset += CANDIDATE_BATCH_SIZE;
        }
    }

    private List<SimilarEnterpriseVO> findSimilarEnterprisesByRules(EnterpriseProfileAnalysisRow source,
                                                                    List<String> sourceTags,
                                                                    int similarLimit) {
        PriorityQueue<SimilarScore> topScores = new PriorityQueue<>(Comparator.comparing(SimilarScore::similarity));
        Set<Long> scoredIds = new LinkedHashSet<>();
        scoreCandidatesByRules(source, sourceTags, similarLimit, topScores, scoredIds,
                (offset) -> profileAnalysisMapper.selectSameIndustryActiveCandidates(
                        source.getIndustry(), source.getId(), offset, CANDIDATE_BATCH_SIZE));
        scoreCandidatesByRules(source, sourceTags, similarLimit, topScores, scoredIds,
                (offset) -> profileAnalysisMapper.selectActiveCandidates(
                        source.getId(), offset, CANDIDATE_BATCH_SIZE));
        return topScores.stream()
                .sorted(Comparator.comparing(SimilarScore::similarity).reversed())
                .map(SimilarScore::vo)
                .toList();
    }

    private void scoreCandidatesByRules(EnterpriseProfileAnalysisRow source,
                                         List<String> sourceTags,
                                         int similarLimit,
                                         PriorityQueue<SimilarScore> topScores,
                                         Set<Long> scoredIds,
                                         java.util.function.IntFunction<List<EnterpriseProfileAnalysisRow>> candidateLoader) {
        int offset = 0;
        while (true) {
            List<EnterpriseProfileAnalysisRow> candidates = candidateLoader.apply(offset);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            List<EnterpriseProfileAnalysisRow> fresh = candidates.stream()
                    .filter(candidate -> !scoredIds.contains(candidate.getId()))
                    .toList();
            if (!fresh.isEmpty()) {
                Map<Long, List<String>> candidateTagsById = loadTagsByCandidates(fresh);
                for (EnterpriseProfileAnalysisRow candidate : fresh) {
                    List<String> candidateTags = candidateTagsById.getOrDefault(candidate.getId(), List.of());
                    BigDecimal similarity = ruleSimilarity(source, candidate, sourceTags, candidateTags);
                    if (similarity.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    offer(topScores, similarLimit,
                            toVo(candidate, similarity, buildReasons(source, candidate, sourceTags, candidateTags, similarity)));
                }
                fresh.forEach(candidate -> scoredIds.add(candidate.getId()));
            }
            if (candidates.size() < CANDIDATE_BATCH_SIZE) {
                break;
            }
            offset += CANDIDATE_BATCH_SIZE;
        }
    }

    @Override
    public VectorWarmupResultVO warmupActiveEnterpriseVectors(VectorWarmupRequest request) {
        List<Long> requestedIds = request == null ? null : request.getEnterpriseIds();
        if (requestedIds != null && requestedIds.size() > MAX_WARMUP_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单次最多预热 500 家企业");
        }

        VectorWarmupResultVO result = new VectorWarmupResultVO();
        String modelName = embeddingVectorService.getCurrentModelName();
        if (requestedIds == null || requestedIds.isEmpty()) {
            warmupAllActive(modelName, result);
        } else {
            result.setTotalCount(requestedIds.size());
            for (Long enterpriseId : requestedIds) {
                EnterpriseProfileAnalysisRow row = enterpriseId == null ? null : profileAnalysisMapper.selectProfileById(enterpriseId);
                warmupOne(row, modelName, result);
            }
        }
        return result;
    }

    private void scoreBatch(EnterpriseProfileAnalysisRow source,
                            List<String> sourceTags,
                            String sourceHash,
                            float[] sourceVector,
                            String modelName,
                            List<EnterpriseProfileAnalysisRow> candidates,
                            int similarLimit,
                            PriorityQueue<SimilarScore> topScores) {
        Map<Long, List<String>> candidateTags = loadTagsByCandidates(candidates);
        Map<Long, String> candidateTexts = new HashMap<>();
        Map<Long, String> candidateHashes = new HashMap<>();
        for (EnterpriseProfileAnalysisRow candidate : candidates) {
            String text = profileTextFactory.buildProfileText(candidate, candidateTags.get(candidate.getId()));
            candidateTexts.put(candidate.getId(), text);
            candidateHashes.put(candidate.getId(), TextHash.sha256(text));
        }

        List<TargetHashParam> targetHashes = candidates.stream()
                .map(candidate -> new TargetHashParam(candidate.getId(), candidateHashes.get(candidate.getId())))
                .toList();
        Map<Long, EnterpriseSimilarityCacheRow> cachedRows = similarityCacheMapper
                .selectMatchedCache(source.getId(), modelName, sourceHash, targetHashes)
                .stream()
                .collect(Collectors.toMap(EnterpriseSimilarityCacheRow::getTargetEnterpriseId,
                        Function.identity(), (left, right) -> left));

        Map<Long, String> uncachedCandidateTexts = new HashMap<>();
        for (EnterpriseProfileAnalysisRow candidate : candidates) {
            if (!cachedRows.containsKey(candidate.getId())) {
                uncachedCandidateTexts.put(candidate.getId(), candidateTexts.get(candidate.getId()));
            }
        }
        Map<Long, float[]> candidateVectors;
        try {
            candidateVectors = embeddingVectorService.getOrCreateVectorsDirectBatch(
                    OBJECT_TYPE,
                    uncachedCandidateTexts,
                    modelName);
        } catch (RuntimeException ex) {
            log.warn("batch candidate profile vector failed, use rule fallback: sourceId={}, candidateCount={}, reason={}",
                    source.getId(), uncachedCandidateTexts.size(), ex.getClass().getSimpleName());
            log.debug("batch candidate profile vector fallback detail", ex);
            candidateVectors = Map.of();
        }

        for (EnterpriseProfileAnalysisRow candidate : candidates) {
            EnterpriseSimilarityCacheRow cache = cachedRows.get(candidate.getId());
            if (cache != null) {
                offer(topScores, similarLimit, toVo(candidate, cache.getSimilarity(), parseReasons(cache.getReasons())));
                continue;
            }
            try {
                float[] candidateVector = candidateVectors.get(candidate.getId());
                if (candidateVector == null) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "候选企业画像向量生成失败");
                }
                BigDecimal similarity = toSimilarity(VectorMath.cosineSimilarity(sourceVector, candidateVector));
                List<String> reasons = buildReasons(source, candidate, sourceTags, candidateTags.get(candidate.getId()), similarity);
                cacheSimilarity(source.getId(), candidate.getId(), modelName, sourceHash,
                        candidateHashes.get(candidate.getId()), similarity, reasons);
                offer(topScores, similarLimit, toVo(candidate, similarity, reasons));
            } catch (RuntimeException ex) {
                log.warn("skip candidate profile vector, use rule fallback: sourceId={}, candidateId={}, reason={}",
                        source.getId(), candidate.getId(), ex.getClass().getSimpleName());
                log.debug("skip candidate profile vector detail", ex);
                BigDecimal fallbackSimilarity = ruleSimilarity(
                        source, candidate, sourceTags, candidateTags.get(candidate.getId()));
                if (fallbackSimilarity.compareTo(BigDecimal.ZERO) > 0) {
                    List<String> fallbackReasons = buildReasons(
                            source, candidate, sourceTags, candidateTags.get(candidate.getId()), fallbackSimilarity);
                    offer(topScores, similarLimit, toVo(candidate, fallbackSimilarity, fallbackReasons));
                }
            }
        }
    }

    private Map<Long, List<String>> loadTagsByCandidates(List<EnterpriseProfileAnalysisRow> candidates) {
        List<Long> candidateIds = candidates.stream()
                .map(EnterpriseProfileAnalysisRow::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        return profileAnalysisMapper.selectTagTextsByEnterpriseIds(candidateIds).stream()
                .collect(Collectors.groupingBy(
                        EnterpriseTagTextRow::getEnterpriseId,
                        HashMap::new,
                        Collectors.mapping(EnterpriseTagTextRow::getTagName, Collectors.toList())));
    }

    private void warmupAllActive(String modelName, VectorWarmupResultVO result) {
        int offset = 0;
        while (true) {
            List<EnterpriseProfileAnalysisRow> rows = profileAnalysisMapper.selectActiveCandidates(
                    -1L, offset, CANDIDATE_BATCH_SIZE);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            result.setTotalCount(result.getTotalCount() + rows.size());
            rows.forEach(row -> warmupOne(row, modelName, result));
            if (rows.size() < CANDIDATE_BATCH_SIZE) {
                break;
            }
            offset += CANDIDATE_BATCH_SIZE;
        }
    }

    private void warmupOne(EnterpriseProfileAnalysisRow row, String modelName, VectorWarmupResultVO result) {
        if (row == null || !STATUS_ACTIVE.equals(row.getStatus())) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }
        try {
            List<String> tags = profileAnalysisMapper.selectTagTextsByEnterpriseId(row.getId());
            String text = profileTextFactory.buildProfileText(row, tags);
            embeddingVectorService.getOrCreateVectorDirect(OBJECT_TYPE, row.getId(), modelName, text);
            result.setSuccessCount(result.getSuccessCount() + 1);
        } catch (RuntimeException ex) {
            log.warn("warmup enterprise profile vector failed: enterpriseId={}, reason={}",
                    row.getId(), ex.getClass().getSimpleName());
            log.debug("warmup enterprise profile vector detail", ex);
            result.setFailureCount(result.getFailureCount() + 1);
        }
    }

    private void cacheSimilarity(Long sourceId, Long targetId, String modelName, String sourceHash,
                                 String targetHash, BigDecimal similarity, List<String> reasons) {
        try {
            similarityCacheMapper.upsertSimilarity(sourceId, targetId, modelName, sourceHash, targetHash,
                    similarity, objectMapper.writeValueAsString(reasons));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("write enterprise similarity cache failed: sourceId={}, targetId={}, reason={}",
                    sourceId, targetId, ex.getClass().getSimpleName());
            log.debug("write enterprise similarity cache detail", ex);
        }
    }

    private void offer(PriorityQueue<SimilarScore> topScores, int similarLimit, SimilarEnterpriseVO vo) {
        topScores.offer(new SimilarScore(vo.getSimilarity(), vo));
        if (topScores.size() > similarLimit) {
            topScores.poll();
        }
    }

    private SimilarEnterpriseVO toVo(EnterpriseProfileAnalysisRow candidate, BigDecimal similarity, List<String> reasons) {
        SimilarEnterpriseVO vo = new SimilarEnterpriseVO();
        vo.setEnterpriseId(candidate.getId());
        vo.setEnterpriseName(candidate.getEnterpriseName());
        vo.setSimilarity(similarity);
        vo.setSimilarityPercent(similarity.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue());
        vo.setIndustry(candidate.getIndustry());
        vo.setMainProducts(candidate.getMainProducts());
        vo.setReasons(reasons);
        return vo;
    }

    private BigDecimal toSimilarity(double similarity) {
        double normalized = Math.max(0.0D, Math.min(1.0D, similarity));
        return BigDecimal.valueOf(normalized).setScale(5, RoundingMode.HALF_UP);
    }

    private BigDecimal ruleSimilarity(EnterpriseProfileAnalysisRow source,
                                      EnterpriseProfileAnalysisRow candidate,
                                      List<String> sourceTags,
                                      List<String> candidateTags) {
        BigDecimal score = BigDecimal.ZERO;
        if (sameNonBlank(source.getIndustry(), candidate.getIndustry())) {
            score = score.add(RULE_INDUSTRY_WEIGHT);
        }
        score = score.add(weightedOverlap(source.getMainProducts(), candidate.getMainProducts(), RULE_PRODUCT_WEIGHT));
        score = score.add(weightedOverlap(source.getCoreTech(), candidate.getCoreTech(), RULE_TECH_WEIGHT));
        score = score.add(weightedOverlap(source.getKeywords(), candidate.getKeywords(), RULE_KEYWORD_WEIGHT));
        score = score.add(weightedTagOverlap(sourceTags, candidateTags, RULE_TAG_WEIGHT));
        return score.min(BigDecimal.ONE).setScale(5, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedOverlap(String left, String right, BigDecimal weight) {
        Set<String> leftSet = splitKeywords(left);
        Set<String> rightSet = splitKeywords(right);
        if (leftSet.isEmpty() || rightSet.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Set<String> intersection = new LinkedHashSet<>(leftSet);
        intersection.retainAll(rightSet);
        if (intersection.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int denominator = Math.max(leftSet.size(), rightSet.size());
        return weight
                .multiply(BigDecimal.valueOf(intersection.size()))
                .divide(BigDecimal.valueOf(denominator), 5, RoundingMode.HALF_UP);
    }

    private BigDecimal weightedTagOverlap(List<String> left, List<String> right, BigDecimal weight) {
        Set<String> leftSet = normalizeList(left);
        Set<String> rightSet = normalizeList(right);
        if (leftSet.isEmpty() || rightSet.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Set<String> intersection = new LinkedHashSet<>(leftSet);
        intersection.retainAll(rightSet);
        if (intersection.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int denominator = Math.max(leftSet.size(), rightSet.size());
        return weight
                .multiply(BigDecimal.valueOf(intersection.size()))
                .divide(BigDecimal.valueOf(denominator), 5, RoundingMode.HALF_UP);
    }

    private List<String> buildReasons(EnterpriseProfileAnalysisRow source,
                                      EnterpriseProfileAnalysisRow candidate,
                                      List<String> sourceTags,
                                      List<String> candidateTags,
                                      BigDecimal similarity) {
        List<String> reasons = new ArrayList<>();
        if (sameNonBlank(source.getIndustry(), candidate.getIndustry())) {
            reasons.add("所属行业相同：" + source.getIndustry().trim());
        }
        Set<String> sharedProductKeywords = intersectKeywords(source.getMainProducts(), candidate.getMainProducts());
        if (!sharedProductKeywords.isEmpty()) {
            reasons.add("主营产品关键词重合：" + String.join("、", sharedProductKeywords));
        }
        Set<String> sharedTags = intersectTags(sourceTags, candidateTags);
        if (!sharedTags.isEmpty()) {
            reasons.add("共享标签：" + String.join("、", sharedTags));
        }
        if (similarity.compareTo(HIGH_SIMILARITY) >= 0) {
            reasons.add("经营范围语义相似度高");
        }
        return reasons;
    }

    private List<String> parseReasons(String reasons) {
        if (reasons == null || reasons.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(reasons, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of(reasons);
        }
    }

    private boolean sameNonBlank(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.trim().equals(right.trim());
    }

    private Set<String> intersectKeywords(String left, String right) {
        Set<String> leftSet = splitKeywords(left);
        Set<String> rightSet = splitKeywords(right);
        leftSet.retainAll(rightSet);
        return leftSet;
    }

    private Set<String> intersectTags(List<String> left, List<String> right) {
        Set<String> leftSet = normalizeList(left);
        Set<String> rightSet = normalizeList(right);
        leftSet.retainAll(rightSet);
        return leftSet;
    }

    private Set<String> splitKeywords(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashSet<>();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String part : text.split("[\\s,，;；、/|]+")) {
            String keyword = part.trim();
            if (!keyword.isEmpty()) {
                result.add(keyword);
            }
        }
        return result;
    }

    private Set<String> normalizeList(List<String> values) {
        if (values == null) {
            return new LinkedHashSet<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateLimit(int similarLimit) {
        if (similarLimit != 5 && similarLimit != 10 && similarLimit != 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "相似企业数量只能是 5、10 或 20");
        }
    }

    private record SimilarScore(BigDecimal similarity, SimilarEnterpriseVO vo) {
    }
}
