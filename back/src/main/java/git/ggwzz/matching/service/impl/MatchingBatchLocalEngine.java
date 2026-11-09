package git.ggwzz.matching.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.embedding.EmbeddingProfileTextBuilder;
import git.ggwzz.matching.embedding.EmbeddingProperties;
import git.ggwzz.matching.embedding.EmbeddingVectorService;
import git.ggwzz.matching.embedding.TextHash;
import git.ggwzz.matching.embedding.VectorMath;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.entity.MatchingTask;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.enums.MatchingTaskStatus;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.matching.mapper.MatchingTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批量本地匹配引擎。
 * 按 chainId 分组，节点向量只加载一次，企业向量去重加载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingBatchLocalEngine {

    private static final String MODEL_NAME = "HYBRID_EMBEDDING_V2";
    private static final String ENTERPRISE_OBJECT = "ENTERPRISE";
    private static final String CHAIN_NODE_OBJECT = "CHAIN_NODE";

    private final ChainNodeQueryService chainNodeQueryService;
    private final EnterpriseQueryService enterpriseQueryService;
    private final EmbeddingProfileTextBuilder profileTextBuilder;
    private final EmbeddingVectorService embeddingVectorService;
    private final RuleBasedMatchingProvider ruleBasedMatchingProvider;
    private final MatchingTaskMapper matchingTaskMapper;
    private final MatchingResultMapper matchingResultMapper;
    private final MatchingResultPersistenceSupport persistenceSupport;
    private final EmbeddingProperties embeddingProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final MatchingRecommendationCacheService recommendationCacheService;

    /**
     * 批量执行匹配。按 chainId 分组，每条产业链的节点向量只加载一次。
     * 添加整体超时控制和进度日志。
     */
    public void executeBatch(List<MatchingTask> tasks,
                             List<Long> enterpriseIds,
                             List<Long> chainIds) {
        long batchStart = System.nanoTime();
        log.info("batch execution started: totalTasks={}, enterpriseCount={}, chainCount={}",
                tasks.size(), enterpriseIds.size(), chainIds.size());

        // 按 chainId 分组任务
        Map<Long, List<MatchingTask>> tasksByChain = tasks.stream()
                .collect(Collectors.groupingBy(MatchingTask::getChainId));

        String modelName = embeddingVectorService.getCurrentModelName();
        EnterpriseBatchCache enterpriseBatchCache = prepareEnterpriseBatchCache(enterpriseIds, modelName);

        int completedChains = 0;
        for (Long chainId : chainIds) {
            List<MatchingTask> chainTasks = tasksByChain.get(chainId);
            if (chainTasks == null || chainTasks.isEmpty()) {
                continue;
            }
            try {
                executeChainBatch(chainId, chainTasks, enterpriseBatchCache, modelName);
                completedChains++;
                long elapsedMs = (System.nanoTime() - batchStart) / 1_000_000;
                log.info("batch progress: completedChains={}, totalChains={}, elapsedMs={}",
                        completedChains, chainIds.size(), elapsedMs);
            } catch (Exception ex) {
                log.error("batch chain execution failed: chainId={}", chainId, ex);
                for (MatchingTask task : chainTasks) {
                    markTaskFailed(task.getId(), ex);
                }
            }
        }

        long totalElapsedMs = (System.nanoTime() - batchStart) / 1_000_000;
        log.info("batch execution completed: totalTasks={}, elapsedMs={}ms", tasks.size(), totalElapsedMs);
    }

    /**
     * 在提交事务内优先完成推荐缓存命中的任务，避免演示批量任务全部进入异步队列。
     *
     * @return 未命中缓存、仍需异步 embedding 计算的任务。
     */
    public CachePrecheckOutcome completeCachedTasks(List<MatchingTask> tasks,
                                                    List<Long> enterpriseIds,
                                                    List<Long> chainIds) {
        if (tasks == null || tasks.isEmpty()) {
            return new CachePrecheckOutcome(List.of(), 0);
        }
        long start = System.nanoTime();
        String modelName = embeddingVectorService.getCurrentModelName();
        EnterpriseBatchCache enterpriseBatchCache = prepareEnterpriseBatchCache(enterpriseIds, modelName);
        Map<Long, List<MatchingTask>> tasksByChain = tasks.stream()
                .collect(Collectors.groupingBy(MatchingTask::getChainId));
        Set<Long> cancelledIds = batchQueryCancelledIds(
                tasks.stream().map(MatchingTask::getId).toList());

        List<MatchingTask> remainingTasks = new ArrayList<>();
        List<CachedTaskCompletion> cachedCompletions = new ArrayList<>();
        for (Long chainId : chainIds) {
            List<MatchingTask> chainTasks = tasksByChain.get(chainId);
            if (chainTasks == null || chainTasks.isEmpty()) {
                continue;
            }
            CachePrecheckResult precheckResult = collectLatestCachedTasksForChain(
                    chainId, chainTasks, enterpriseBatchCache, modelName, cancelledIds);
            remainingTasks.addAll(precheckResult.remainingTasks());
            cachedCompletions.addAll(precheckResult.cachedCompletions());
            if (!precheckResult.cachedCompletions().isEmpty()) {
                log.info("recommendation cache hit: chainId={}, hit={}, miss={}",
                        chainId, precheckResult.cachedCompletions().size(),
                        precheckResult.remainingTasks().size());
            }
        }
        if (!cachedCompletions.isEmpty()) {
            markCachedTasksCompleted(cachedCompletions);
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("batch recommendation cache precheck completed: total={}, hit={}, miss={}, elapsedMs={}",
                tasks.size(), cachedCompletions.size(), remainingTasks.size(), elapsedMs);
        return new CachePrecheckOutcome(remainingTasks, cachedCompletions.size());
    }

    private EnterpriseBatchCache prepareEnterpriseBatchCache(List<Long> enterpriseIds, String modelName) {
        long start = System.nanoTime();
        Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> enterpriseInfos = new HashMap<>();
        Map<Long, String> enterpriseProfiles = new HashMap<>();

        Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> enterpriseInfoMap =
                enterpriseQueryService.getByIds(new LinkedHashSet<>(enterpriseIds));
        for (Long enterpriseId : new LinkedHashSet<>(enterpriseIds)) {
            RuleBasedMatchingProvider.EnterpriseInfo enterprise = enterpriseInfoMap.get(enterpriseId);
            if (enterprise == null) {
                continue;
            }
            String profile = profileTextBuilder.buildEnterpriseProfile(enterprise);
            enterpriseInfos.put(enterpriseId, enterprise);
            enterpriseProfiles.put(enterpriseId, profile);
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.info("enterprise profile snapshot ready: enterprises={}, elapsedMs={}",
                enterpriseInfos.size(), elapsedMs);
        return new EnterpriseBatchCache(enterpriseInfos, enterpriseProfiles, new HashMap<>());
    }

    private void executeChainBatch(Long chainId,
                                   List<MatchingTask> chainTasks,
                                   EnterpriseBatchCache enterpriseBatchCache,
                                   String modelName) {
        long chainStart = System.nanoTime();

        // 1. 加载产业链节点（只加载一次）
        List<ChainNodeCandidateDTO> nodes = chainNodeQueryService.listByChainId(chainId);
        if (nodes == null || nodes.isEmpty()) {
            log.warn("chain nodes not found: chainId={}", chainId);
            for (MatchingTask task : chainTasks) {
                markTaskCompleted(task.getId(), List.of(), 0);
            }
            return;
        }

        // 2. 构建节点 profile 和 textHash（只构建一次）
        Map<Long, String> nodeProfiles = buildNodeProfiles(nodes);
        Map<Long, String> nodeTextHashes = nodeProfiles.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> TextHash.sha256(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));

        // 3. 缓存命中任务直接批量完成，避免重复加载向量和计算
        List<MatchingTask> remainingTasks = completeCachedTasksForChain(
                chainId, chainTasks, enterpriseBatchCache, nodeProfiles, modelName);
        if (remainingTasks.isEmpty()) {
            long chainElapsed = (System.nanoTime() - chainStart) / 1_000_000;
            log.info("chain batch done from recommendation cache: chainId={}, tasks={}, elapsedMs={}",
                    chainId, chainTasks.size(), chainElapsed);
            return;
        }

        // 4. 批量加载节点向量（只查一次数据库）
        Map<Long, float[]> nodeVectors = embeddingVectorService.getBatchCachedVectors(
                CHAIN_NODE_OBJECT, new ArrayList<>(nodeProfiles.keySet()), modelName, nodeTextHashes);
        int cachedNodeCount = nodeVectors.size();

        // 5. 对缺失的节点向量批量生成
        Map<Long, String> missingNodeProfiles = nodeProfiles.entrySet().stream()
                .filter(entry -> !nodeVectors.containsKey(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (!missingNodeProfiles.isEmpty()) {
            log.info("generating missing node vectors: chainId={}, missingCount={}",
                    chainId, missingNodeProfiles.size());
            try {
                nodeVectors.putAll(embeddingVectorService.getOrCreateVectorsDirectBatch(
                        CHAIN_NODE_OBJECT, missingNodeProfiles, modelName));
            } catch (Exception ex) {
                log.warn("failed to batch generate node vectors: chainId={}", chainId, ex);
            }
        }

        long vectorLoadMs = (System.nanoTime() - chainStart) / 1_000_000;
        log.info("chain vector snapshot ready: chainId={}, nodes={}, cached={}, generated={}, elapsedMs={}",
                chainId, nodes.size(), cachedNodeCount,
                Math.max(0, nodeVectors.size() - cachedNodeCount), vectorLoadMs);

        prepareEnterpriseVectorsForTasks(remainingTasks, enterpriseBatchCache, modelName);

        // 6. 批量预查取消状态（避免每个任务查一次 DB）
        Set<Long> cancelledIds = batchQueryCancelledIds(
                remainingTasks.stream().map(MatchingTask::getId).toList());

        // 7. 逐企业计算匹配
        int taskIndex = 0;
        for (MatchingTask task : remainingTasks) {
            // 每 10 个任务重新检查一次取消状态
            if (taskIndex > 0 && taskIndex % 10 == 0) {
                cancelledIds = batchQueryCancelledIds(
                        remainingTasks.stream().map(MatchingTask::getId).toList());
            }
            taskIndex++;
            if (cancelledIds.contains(task.getId())) {
                continue;
            }
            try {
                executeSingleInBatch(task, chainId, nodes, nodeProfiles, nodeVectors,
                        enterpriseBatchCache, modelName);
            } catch (Exception ex) {
                log.warn("batch single match failed: taskId={}, enterpriseId={}, chainId={}",
                        task.getId(), task.getEnterpriseId(), chainId, ex);
                markTaskFailed(task.getId(), ex);
            }
        }

        long chainElapsed = (System.nanoTime() - chainStart) / 1_000_000;
        log.info("chain batch done: chainId={}, tasks={}, elapsedMs={}",
                chainId, chainTasks.size(), chainElapsed);
    }

    private void prepareEnterpriseVectorsForTasks(List<MatchingTask> tasks,
                                                  EnterpriseBatchCache enterpriseBatchCache,
                                                  String modelName) {
        Map<Long, String> missingEnterpriseProfiles = tasks.stream()
                .map(MatchingTask::getEnterpriseId)
                .distinct()
                .filter(id -> !enterpriseBatchCache.enterpriseVectors().containsKey(id))
                .filter(id -> enterpriseBatchCache.enterpriseProfiles().containsKey(id))
                .collect(Collectors.toMap(
                        id -> id,
                        id -> enterpriseBatchCache.enterpriseProfiles().get(id),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (missingEnterpriseProfiles.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        try {
            Map<Long, float[]> generatedVectors = embeddingVectorService.getOrCreateVectorsDirectBatch(
                    ENTERPRISE_OBJECT, missingEnterpriseProfiles, modelName);
            enterpriseBatchCache.enterpriseVectors().putAll(generatedVectors);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("enterprise vectors ready for remaining tasks: requested={}, available={}, elapsedMs={}",
                    missingEnterpriseProfiles.size(), generatedVectors.size(), elapsedMs);
        } catch (Exception ex) {
            log.warn("failed to prepare enterprise vectors for remaining tasks, fallback to rules later: count={}",
                    missingEnterpriseProfiles.size(), ex);
        }
    }

    private Map<Long, String> buildNodeProfiles(List<ChainNodeCandidateDTO> nodes) {
        Map<Long, String> nodeProfiles = new LinkedHashMap<>();
        for (ChainNodeCandidateDTO node : nodes) {
            nodeProfiles.put(node.getNodeId(), profileTextBuilder.buildNodeProfile(node));
        }
        return nodeProfiles;
    }

    private List<MatchingTask> completeCachedTasksForChain(Long chainId,
                                                           List<MatchingTask> chainTasks,
                                                           EnterpriseBatchCache enterpriseBatchCache,
                                                           Map<Long, String> nodeProfiles,
                                                           String modelName) {
        Set<Long> cancelledIds = batchQueryCancelledIds(
                chainTasks.stream().map(MatchingTask::getId).toList());
        CachePrecheckResult precheckResult = collectCachedTasksForChain(
                chainId, chainTasks, enterpriseBatchCache, nodeProfiles, modelName, cancelledIds);
        if (!precheckResult.cachedCompletions().isEmpty()) {
            markCachedTasksCompleted(precheckResult.cachedCompletions());
            log.info("recommendation cache hit: chainId={}, hit={}, miss={}",
                    chainId, precheckResult.cachedCompletions().size(),
                    precheckResult.remainingTasks().size());
        }
        return precheckResult.remainingTasks();
    }

    private CachePrecheckResult collectLatestCachedTasksForChain(Long chainId,
                                                                 List<MatchingTask> chainTasks,
                                                                 EnterpriseBatchCache enterpriseBatchCache,
                                                                 String modelName,
                                                                 Set<Long> cancelledIds) {
        List<CachedTaskCompletion> cachedCompletions = new ArrayList<>();
        List<MatchingTask> remainingTasks = new ArrayList<>();
        Map<MatchingRecommendationCacheService.FastCacheFingerprint, List<MatchingTask>> tasksByFingerprint =
                new LinkedHashMap<>();
        for (MatchingTask task : chainTasks) {
            if (cancelledIds.contains(task.getId())) {
                continue;
            }
            String enterpriseProfile = enterpriseBatchCache.enterpriseProfiles().get(task.getEnterpriseId());
            if (enterpriseProfile == null) {
                remainingTasks.add(task);
                continue;
            }
            MatchingRecommendationCacheService.FastCacheFingerprint fingerprint =
                    recommendationCacheService.buildFastFingerprint(
                            task.getEnterpriseId(), chainId, modelName, enterpriseProfile);
            tasksByFingerprint.computeIfAbsent(fingerprint, ignored -> new ArrayList<>())
                    .add(task);
        }

        Map<MatchingRecommendationCacheService.FastCacheFingerprint, List<MatchingResult>> cachedResultsByFingerprint =
                Map.of();
        if (!tasksByFingerprint.isEmpty()) {
            try {
                cachedResultsByFingerprint = recommendationCacheService.findLatestCachedResults(
                        tasksByFingerprint.keySet());
            } catch (Exception ex) {
                log.warn("failed to batch read latest recommendation cache: chainId={}", chainId, ex);
            }
        }

        for (Map.Entry<MatchingRecommendationCacheService.FastCacheFingerprint, List<MatchingTask>> entry
                : tasksByFingerprint.entrySet()) {
            List<MatchingResult> cachedResults = cachedResultsByFingerprint.get(entry.getKey());
            if (cachedResults == null) {
                remainingTasks.addAll(entry.getValue());
                continue;
            }
            for (MatchingTask task : entry.getValue()) {
                cachedCompletions.add(new CachedTaskCompletion(task.getId(), copyResults(cachedResults)));
            }
        }
        return new CachePrecheckResult(remainingTasks, cachedCompletions);
    }

    private CachePrecheckResult collectCachedTasksForChain(Long chainId,
                                                           List<MatchingTask> chainTasks,
                                                           EnterpriseBatchCache enterpriseBatchCache,
                                                           Map<Long, String> nodeProfiles,
                                                           String modelName,
                                                           Set<Long> cancelledIds) {
        List<CachedTaskCompletion> cachedCompletions = new ArrayList<>();
        List<MatchingTask> remainingTasks = new ArrayList<>();
        Map<MatchingRecommendationCacheService.CacheFingerprint, List<MatchingTask>> tasksByFingerprint =
                new LinkedHashMap<>();
        for (MatchingTask task : chainTasks) {
            if (cancelledIds.contains(task.getId())) {
                continue;
            }
            String enterpriseProfile = enterpriseBatchCache.enterpriseProfiles().get(task.getEnterpriseId());
            if (enterpriseProfile == null) {
                remainingTasks.add(task);
                continue;
            }
            MatchingRecommendationCacheService.CacheFingerprint fingerprint =
                    recommendationCacheService.buildFingerprint(
                            task.getEnterpriseId(), chainId, modelName, enterpriseProfile, nodeProfiles);
            tasksByFingerprint.computeIfAbsent(fingerprint, ignored -> new ArrayList<>())
                    .add(task);
        }

        Map<MatchingRecommendationCacheService.CacheFingerprint, List<MatchingResult>> cachedResultsByFingerprint =
                Map.of();
        if (!tasksByFingerprint.isEmpty()) {
            try {
                cachedResultsByFingerprint = recommendationCacheService.findCachedResults(
                        tasksByFingerprint.keySet());
            } catch (Exception ex) {
                log.warn("failed to batch read recommendation cache: chainId={}", chainId, ex);
            }
        }

        for (Map.Entry<MatchingRecommendationCacheService.CacheFingerprint, List<MatchingTask>> entry
                : tasksByFingerprint.entrySet()) {
            List<MatchingResult> cachedResults = cachedResultsByFingerprint.get(entry.getKey());
            if (cachedResults == null) {
                remainingTasks.addAll(entry.getValue());
                continue;
            }
            for (MatchingTask task : entry.getValue()) {
                cachedCompletions.add(new CachedTaskCompletion(task.getId(), copyResults(cachedResults)));
            }
        }
        return new CachePrecheckResult(remainingTasks, cachedCompletions);
    }

    private List<MatchingResult> copyResults(List<MatchingResult> results) {
        List<MatchingResult> copies = new ArrayList<>(results.size());
        for (MatchingResult result : results) {
            MatchingResult copy = new MatchingResult();
            copy.setNodeId(result.getNodeId());
            copy.setConfidence(result.getConfidence());
            copy.setReason(result.getReason());
            copy.setEvidence(result.getEvidence());
            copy.setRankNo(result.getRankNo());
            copy.setDecision(result.getDecision());
            copy.setRejectReason(result.getRejectReason());
            copies.add(copy);
        }
        return copies;
    }

    private void executeSingleInBatch(MatchingTask task,
                                       Long chainId,
                                       List<ChainNodeCandidateDTO> nodes,
                                       Map<Long, String> nodeProfiles,
                                       Map<Long, float[]> nodeVectors,
                                       EnterpriseBatchCache enterpriseBatchCache,
                                       String modelName) {
        long start = System.nanoTime();
        Long enterpriseId = task.getEnterpriseId();
        Long taskId = task.getId();

        log.debug("executing single task: taskId={}, enterpriseId={}, chainId={}", taskId, enterpriseId, chainId);

        RuleBasedMatchingProvider.EnterpriseInfo enterprise = enterpriseBatchCache.enterpriseInfos().get(enterpriseId);
        if (enterprise == null) {
            markTaskFailed(taskId, new RuntimeException("企业不存在: " + enterpriseId));
            return;
        }

        String cachedEnterpriseProfile = enterpriseBatchCache.enterpriseProfiles().get(enterpriseId);
        final String enterpriseProfile = cachedEnterpriseProfile != null
                ? cachedEnterpriseProfile
                : profileTextBuilder.buildEnterpriseProfile(enterprise);

        float[] enterpriseVector = enterpriseBatchCache.enterpriseVectors().get(enterpriseId);
        if (enterpriseVector == null) {
            long vectorElapsed = (System.nanoTime() - start) / 1_000_000;
            log.warn("enterprise vector unavailable after {}ms, fallback to rules: enterpriseId={}",
                    vectorElapsed, enterpriseId);
            List<MatchingResult> ruleResults = ruleBasedMatchingProvider.match(enterprise, nodes);
            int topK = embeddingProperties.getHybrid().getTopK();
            List<MatchingResult> topResults = ruleResults.stream()
                    .sorted(Comparator.comparing(MatchingResult::getConfidence,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .limit(Math.max(1, topK))
                    .toList();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            log.info("task completed with rules only: taskId={}, elapsedMs={}", taskId, elapsed);
            markTaskCompleted(taskId, topResults, elapsed);
            return;
        }

        // 规则匹配
        Map<Long, MatchingResult> ruleResults = ruleBasedMatchingProvider.match(enterprise, nodes)
                .stream()
                .collect(Collectors.toMap(MatchingResult::getNodeId, r -> r,
                        (l, r) -> l, LinkedHashMap::new));

        // 计算混合分数
        List<ScoredNode> scoredNodes = new ArrayList<>();
        for (ChainNodeCandidateDTO node : nodes) {
            float[] nodeVec = nodeVectors.get(node.getNodeId());
            if (nodeVec == null) {
                continue;
            }
            double vectorScore = Math.max(0.0D, VectorMath.cosineSimilarity(enterpriseVector, nodeVec));
            MatchingResult ruleResult = ruleResults.get(node.getNodeId());
            double ruleScore = ruleResult == null || ruleResult.getConfidence() == null
                    ? 0.0D : ruleResult.getConfidence().doubleValue();
            double finalScore = calculateFinalScore(vectorScore, ruleScore);
            scoredNodes.add(new ScoredNode(node, vectorScore, ruleScore, finalScore, ruleResult));
        }

        int topK = embeddingProperties.getHybrid().getTopK();
        List<MatchingResult> results = scoredNodes.stream()
                .sorted(Comparator.comparing(ScoredNode::finalScore).reversed())
                .limit(Math.max(1, topK))
                .map(scored -> toMatchingResult(scored, enterpriseProfile, nodeProfiles))
                .toList();

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        log.info("task completed: taskId={}, resultCount={}, elapsedMs={}", taskId, results.size(), elapsed);
        saveRecommendationCache(enterpriseId, chainId, modelName, enterpriseProfile, nodeProfiles, results);
        markTaskCompleted(taskId, results, elapsed);
    }

    private void saveRecommendationCache(Long enterpriseId,
                                         Long chainId,
                                         String modelName,
                                         String enterpriseProfile,
                                         Map<Long, String> nodeProfiles,
                                         List<MatchingResult> results) {
        try {
            MatchingRecommendationCacheService.CacheFingerprint fingerprint =
                    recommendationCacheService.buildFingerprint(
                            enterpriseId, chainId, modelName, enterpriseProfile, nodeProfiles);
            recommendationCacheService.saveSuccess(fingerprint, results);
        } catch (Exception ex) {
            log.warn("failed to save recommendation cache: enterpriseId={}, chainId={}",
                    enterpriseId, chainId, ex);
        }
    }

    private double calculateFinalScore(double vectorScore, double ruleScore) {
        double score = embeddingProperties.getHybrid().getVectorWeight() * vectorScore
                + embeddingProperties.getHybrid().getRuleWeight() * ruleScore;
        return Math.min(0.99D, Math.max(0.0D, score));
    }

    private MatchingResult toMatchingResult(ScoredNode scored,
                                             String enterpriseProfile,
                                             Map<Long, String> nodeProfiles) {
        MatchingResult result = new MatchingResult();
        result.setNodeId(scored.node().getNodeId());
        result.setConfidence(BigDecimal.valueOf(scored.finalScore()).setScale(4, RoundingMode.HALF_UP));
        result.setReason("混合推荐：向量相似度 " + round(scored.vectorScore())
                + "，规则置信度 " + round(scored.ruleScore())
                + "，综合得分 " + round(scored.finalScore()));
        result.setEvidence(buildEvidence(scored, enterpriseProfile, nodeProfiles));
        return result;
    }

    private String buildEvidence(ScoredNode scored, String enterpriseProfile,
                                  Map<Long, String> nodeProfiles) {
        try {
            Map<String, Object> evidence = new HashMap<>();
            evidence.put("model", MODEL_NAME);
            evidence.put("vector_similarity", round(scored.vectorScore()));
            evidence.put("rule_confidence", round(scored.ruleScore()));
            evidence.put("final_score", round(scored.finalScore()));
            evidence.put("enterprise_profile", enterpriseProfile);
            evidence.put("node_profile", nodeProfiles.get(scored.node().getNodeId()));
            if (scored.ruleResult() != null) {
                evidence.put("rule_evidence", scored.ruleResult().getEvidence());
            }
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception ex) {
            return "{\"model\":\"HYBRID_EMBEDDING_V2\"}";
        }
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    public void markTaskCompleted(Long taskId, List<MatchingResult> results, long elapsedMs) {
        transactionTemplate.executeWithoutResult(status -> {
            // 删除旧结果
            matchingResultMapper.delete(new LambdaQueryWrapper<MatchingResult>()
                    .eq(MatchingResult::getTaskId, taskId));

            // 批量保存新结果（使用 batch insert 提升性能）
            if (!results.isEmpty()) {
                List<MatchingResult> toInsert = new ArrayList<>(results.size());
                int rankNo = 1;
                for (MatchingResult result : results) {
                    result.setTaskId(taskId);
                    result.setRankNo(rankNo++);
                    result.setDecision(MatchingDecision.PENDING.name());
                    persistenceSupport.prepareForRemotePersistence(result);
                    toInsert.add(result);
                }
                // 使用批量插入
                matchingResultMapper.insertBatch(toInsert);
            }

            // 更新任务状态
            MatchingTask update = new MatchingTask();
            update.setId(taskId);
            update.setStatus(MatchingTaskStatus.COMPLETED.name());
            update.setInputSnapshot(MatchingTaskSnapshotSupport.completionSnapshot(
                    null, "BATCH_LOCAL", elapsedMs, results.size()));
            matchingTaskMapper.updateById(update);
        });
    }

    private void markCachedTasksCompleted(List<CachedTaskCompletion> completions) {
        transactionTemplate.executeWithoutResult(status -> {
            List<Long> taskIds = completions.stream()
                    .map(CachedTaskCompletion::taskId)
                    .toList();
            matchingResultMapper.deleteByTaskIds(taskIds);

            List<MatchingResult> toInsert = new ArrayList<>();
            List<MatchingTask> updates = new ArrayList<>();
            for (CachedTaskCompletion completion : completions) {
                int rankNo = 1;
                for (MatchingResult result : completion.results()) {
                    result.setTaskId(completion.taskId());
                    result.setRankNo(rankNo++);
                    result.setDecision(MatchingDecision.PENDING.name());
                    persistenceSupport.prepareForRemotePersistence(result);
                    toInsert.add(result);
                }

                MatchingTask update = new MatchingTask();
                update.setId(completion.taskId());
                update.setStatus(MatchingTaskStatus.COMPLETED.name());
                update.setInputSnapshot(MatchingTaskSnapshotSupport.completionSnapshot(
                        null, "BATCH_CACHE_HIT", 0, completion.results().size()));
                updates.add(update);
            }
            if (!toInsert.isEmpty()) {
                matchingResultMapper.insertBatch(toInsert);
            }
            matchingTaskMapper.updateBatchStatusAndSnapshot(updates);
        });
    }

    private void markTaskFailed(Long taskId, Exception ex) {
        transactionTemplate.executeWithoutResult(status -> {
            matchingResultMapper.delete(new LambdaQueryWrapper<MatchingResult>()
                    .eq(MatchingResult::getTaskId, taskId));

            MatchingTask update = new MatchingTask();
            update.setId(taskId);
            update.setStatus(MatchingTaskStatus.FAILED.name());
            update.setErrorMessage(MatchingTaskSnapshotSupport.limit(
                    ex.getMessage() != null ? ex.getMessage() : "unknown error", 500));
            matchingTaskMapper.updateById(update);
        });
    }

    private boolean isCancelled(Long taskId) {
        MatchingTask task = matchingTaskMapper.selectById(taskId);
        return task != null && MatchingTaskStatus.CANCELLED.name().equals(task.getStatus());
    }

    private Set<Long> batchQueryCancelledIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Set.of();
        }
        List<MatchingTask> tasks = matchingTaskMapper.selectBatchByIds(taskIds);
        if (tasks == null) {
            return Set.of();
        }
        return tasks.stream()
                .filter(t -> MatchingTaskStatus.CANCELLED.name().equals(t.getStatus()))
                .map(MatchingTask::getId)
                .collect(Collectors.toSet());
    }

    public record CachePrecheckOutcome(
            List<MatchingTask> remainingTasks,
            int cachedCount) {
    }

    private record ScoredNode(
            ChainNodeCandidateDTO node,
            double vectorScore,
            double ruleScore,
            double finalScore,
            MatchingResult ruleResult) {
    }

    private record EnterpriseBatchCache(
            Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> enterpriseInfos,
            Map<Long, String> enterpriseProfiles,
            Map<Long, float[]> enterpriseVectors) {
    }

    private record CachedTaskCompletion(
            Long taskId,
            List<MatchingResult> results) {
    }

    private record CachePrecheckResult(
            List<MatchingTask> remainingTasks,
            List<CachedTaskCompletion> cachedCompletions) {
    }
}
