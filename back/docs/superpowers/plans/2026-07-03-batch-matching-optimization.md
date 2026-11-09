# 批量挂链性能优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把批量挂链从"循环 N 次单次匹配"升级为"按产业链分组批量计算 + JVM 本地缓存 + 专用线程池 + 前端批量工作台"，让 61 企业 × 48 节点从十几分钟降到几十秒。

**架构：** 新增 `MatchingBatchLocalEngine` 按 chainId 分组，节点向量只加载一次；`EmbeddingVectorService` 增加 Caffeine 内存缓存；`AiEmbeddingVectorMapper` 增加批量查询；新增 `AsyncExecutorConfig` 专用线程池；前端新增 `BatchWorkbench.vue` 带轮询进度展示。

**技术栈：** Spring Boot 3.5, Java 21, MyBatis-Plus 3.5.7, Caffeine, Vue 3 Composition API, Element Plus, Pinia

---

## 文件结构

### 后端新增
- `pom.xml` — 添加 Caffeine 依赖
- `matching/config/AsyncExecutorConfig.java` — 专用匹配线程池
- `matching/config/MatchingCacheConfig.java` — Caffeine 缓存配置
- `matching/service/impl/MatchingBatchLocalEngine.java` — 批量本地匹配引擎

### 后端修改
- `matching/mapper/AiEmbeddingVectorMapper.java` — 增加 selectActiveBatch
- `matching/mapper/MatchingResultMapper.java` — 增加 selectByTaskIds
- `matching/mapper/MatchingTaskMapper.java` — 增加 selectByIds
- `matching/embedding/EmbeddingVectorService.java` — 增加 Caffeine 缓存 + 批量接口
- `matching/service/impl/MatchingServiceImpl.java` — 重构 createBatchTasksAsync + batchQueryTasks
- `matching/service/impl/MatchingTaskWorker.java` — @Async 指定线程池

### 前端新增
- `front/src/views/matching/BatchWorkbench.vue` — 批量工作台组件

### 前端修改
- `front/src/api/matching.ts` — fetchBatchTasks 加 mock 分支

---

## 任务 1：添加 Caffeine 依赖

**文件：**
- 修改：`groupproject-back/back/pom.xml:28-106`

- [ ] **步骤 1：在 pom.xml dependencies 中添加 Caffeine**

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 2：新增专用匹配线程池配置

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/matching/config/AsyncExecutorConfig.java`

- [ ] **步骤 1：创建 AsyncExecutorConfig**

```java
package git.ggwzz.matching.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncExecutorConfig {

    @Bean("matchingTaskExecutor")
    public Executor matchingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("matching-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

- [ ] **步骤 2：修改 MatchingTaskWorker 的 @Async 注解指定线程池**

修改 `MatchingTaskWorker.java` 第 37 行和第 42 行：
- `@Async` → `@Async("matchingTaskExecutor")`

- [ ] **步骤 3：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 3：AiEmbeddingVectorMapper 增加批量查询

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/mapper/AiEmbeddingVectorMapper.java`

- [ ] **步骤 1：添加 selectActiveBatch 方法**

在 `AiEmbeddingVectorMapper.java` 接口中添加：

```java
import java.util.List;

@Select("<script>"
        + "SELECT id, object_type, object_id, model_name, dimension, text_hash, vector_data, "
        + "source_text_snapshot, status, create_time, update_time, is_deleted "
        + "FROM ai_embedding_vector "
        + "WHERE object_type = #{objectType} AND model_name = #{modelName} AND is_deleted = 0 "
        + "AND object_id IN "
        + "<foreach item='id' collection='objectIds' open='(' separator=',' close=')'>"
        + "#{id}"
        + "</foreach>"
        + "</script>")
List<AiEmbeddingVector> selectActiveBatch(@Param("objectType") String objectType,
                                          @Param("objectIds") List<Long> objectIds,
                                          @Param("modelName") String modelName);
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 4：EmbeddingVectorService 增加 Caffeine 缓存和批量接口

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/embedding/EmbeddingVectorService.java`

- [ ] **步骤 1：重写 EmbeddingVectorService，增加 Caffeine 缓存和批量方法**

```java
package git.ggwzz.matching.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.matching.entity.AiEmbeddingVector;
import git.ggwzz.matching.mapper.AiEmbeddingVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量生成与缓存服务（带 JVM 本地缓存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingVectorService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AiEmbeddingVectorMapper aiEmbeddingVectorMapper;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingVectorCodec vectorCodec;

    /**
     * L1: decoded vector 缓存。
     * key = objectType:objectId:modelName:textHash, value = float[]
     */
    private final Cache<String, float[]> decodedVectorCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    @Transactional(rollbackFor = Exception.class)
    public float[] getOrCreateVector(String objectType, Long objectId, String profileText) {
        validateRequest(objectType, objectId, profileText);
        String modelName = embeddingClient.modelName();
        String textHash = TextHash.sha256(profileText);

        // 先查 JVM 本地缓存
        String cacheKey = objectType + ":" + objectId + ":" + modelName + ":" + textHash;
        float[] cached = decodedVectorCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 再查数据库
        AiEmbeddingVector active = aiEmbeddingVectorMapper.selectActive(objectType, objectId, modelName);
        if (active != null && textHash.equals(active.getTextHash())) {
            float[] decoded = vectorCodec.decode(active.getVectorData());
            decodedVectorCache.put(cacheKey, decoded);
            return decoded;
        }

        // 缓存未命中，调用 embedding 服务
        float[] vector = embeddingClient.embed(profileText);
        if (vector == null || vector.length == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "embedding 服务返回空向量");
        }

        aiEmbeddingVectorMapper.markActiveDeleted(objectType, objectId, modelName);
        AiEmbeddingVector record = new AiEmbeddingVector();
        record.setObjectType(objectType);
        record.setObjectId(objectId);
        record.setModelName(modelName);
        record.setDimension(vector.length);
        record.setTextHash(textHash);
        record.setVectorData(vectorCodec.encode(vector));
        record.setSourceTextSnapshot(profileText);
        record.setStatus(STATUS_ACTIVE);
        record.setDeleted(0);
        aiEmbeddingVectorMapper.insert(record);

        decodedVectorCache.put(cacheKey, vector);
        return vector;
    }

    /**
     * 批量获取已缓存的向量。只返回数据库中已有且 textHash 匹配的向量。
     * 不会调用 embedding 服务生成新向量。
     *
     * @return objectId → float[] 映射，未命中的 objectId 不包含在结果中
     */
    public Map<Long, float[]> getBatchCachedVectors(String objectType,
                                                     List<Long> objectIds,
                                                     String modelName,
                                                     Map<Long, String> objectIdToTextHash) {
        Map<Long, float[]> result = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();

        // 先从 JVM 缓存取
        for (Long objectId : objectIds) {
            String textHash = objectIdToTextHash.get(objectId);
            if (textHash == null) {
                missedIds.add(objectId);
                continue;
            }
            String cacheKey = objectType + ":" + objectId + ":" + modelName + ":" + textHash;
            float[] cached = decodedVectorCache.getIfPresent(cacheKey);
            if (cached != null) {
                result.put(objectId, cached);
            } else {
                missedIds.add(objectId);
            }
        }

        if (missedIds.isEmpty()) {
            return result;
        }

        // 批量查数据库
        List<AiEmbeddingVector> dbVectors = aiEmbeddingVectorMapper.selectActiveBatch(
                objectType, missedIds, modelName);
        if (dbVectors != null) {
            for (AiEmbeddingVector vec : dbVectors) {
                String expectedHash = objectIdToTextHash.get(vec.getObjectId());
                if (expectedHash != null && expectedHash.equals(vec.getTextHash())) {
                    float[] decoded = vectorCodec.decode(vec.getVectorData());
                    String cacheKey = objectType + ":" + vec.getObjectId() + ":" + modelName + ":" + expectedHash;
                    decodedVectorCache.put(cacheKey, decoded);
                    result.put(vec.getObjectId(), decoded);
                }
            }
        }

        return result;
    }

    /**
     * 生成缺失的向量（小并发）。
     */
    @Transactional(rollbackFor = Exception.class)
    public float[] getOrCreateVectorDirect(String objectType, Long objectId,
                                            String modelName, String profileText) {
        String textHash = TextHash.sha256(profileText);

        String cacheKey = objectType + ":" + objectId + ":" + modelName + ":" + textHash;
        float[] cached = decodedVectorCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        AiEmbeddingVector active = aiEmbeddingVectorMapper.selectActive(objectType, objectId, modelName);
        if (active != null && textHash.equals(active.getTextHash())) {
            float[] decoded = vectorCodec.decode(active.getVectorData());
            decodedVectorCache.put(cacheKey, decoded);
            return decoded;
        }

        float[] vector = embeddingClient.embed(profileText);
        if (vector == null || vector.length == 0) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "embedding 服务返回空向量");
        }

        aiEmbeddingVectorMapper.markActiveDeleted(objectType, objectId, modelName);
        AiEmbeddingVector record = new AiEmbeddingVector();
        record.setObjectType(objectType);
        record.setObjectId(objectId);
        record.setModelName(modelName);
        record.setDimension(vector.length);
        record.setTextHash(textHash);
        record.setVectorData(vectorCodec.encode(vector));
        record.setSourceTextSnapshot(profileText);
        record.setStatus(STATUS_ACTIVE);
        record.setDeleted(0);
        aiEmbeddingVectorMapper.insert(record);

        decodedVectorCache.put(cacheKey, vector);
        return vector;
    }

    private void validateRequest(String objectType, Long objectId, String profileText) {
        if (objectType == null || objectType.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "向量对象类型不能为空");
        }
        if (objectId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "向量对象 ID 不能为空");
        }
        if (profileText == null || profileText.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "画像文本不能为空");
        }
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 5：新增 MatchingBatchLocalEngine 批量匹配引擎

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/matching/service/impl/MatchingBatchLocalEngine.java`

- [ ] **步骤 1：创建 MatchingBatchLocalEngine**

```java
package git.ggwzz.matching.service.impl;

import git.ggwzz.chain.dto.ChainNodeCandidateDTO;
import git.ggwzz.chain.service.ChainNodeQueryService;
import git.ggwzz.enterprise.service.EnterpriseQueryService;
import git.ggwzz.matching.embedding.EmbeddingProfileTextBuilder;
import git.ggwzz.matching.embedding.EmbeddingProperties;
import git.ggwzz.matching.embedding.EmbeddingVectorService;
import git.ggwzz.matching.embedding.VectorMath;
import git.ggwzz.matching.entity.MatchingResult;
import git.ggwzz.matching.enums.MatchingDecision;
import git.ggwzz.matching.enums.MatchingTaskStatus;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.matching.mapper.MatchingTaskMapper;
import git.ggwzz.matching.entity.MatchingTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
    private final HybridEmbeddingMatchingProvider hybridEmbeddingMatchingProvider;
    private final MatchingTaskMapper matchingTaskMapper;
    private final MatchingResultMapper matchingResultMapper;
    private final MatchingResultPersistenceSupport persistenceSupport;
    private final EmbeddingProperties embeddingProperties;

    /**
     * 批量执行匹配。按 chainId 分组，每条产业链的节点向量只加载一次。
     *
     * @param tasks 已创建好的任务列表（已入库）
     * @param enterpriseIds 企业 ID 列表
     * @param chainIds 产业链 ID 列表
     */
    public void executeBatch(List<MatchingTask> tasks,
                             List<Long> enterpriseIds,
                             List<Long> chainIds) {
        // 按 chainId 分组任务
        Map<Long, List<MatchingTask>> tasksByChain = tasks.stream()
                .collect(Collectors.groupingBy(MatchingTask::getChainId));

        for (Long chainId : chainIds) {
            List<MatchingTask> chainTasks = tasksByChain.get(chainId);
            if (chainTasks == null || chainTasks.isEmpty()) {
                continue;
            }
            try {
                executeChainBatch(chainId, chainTasks);
            } catch (Exception ex) {
                log.error("batch chain execution failed: chainId={}", chainId, ex);
                // 标记该产业链下所有任务为失败
                for (MatchingTask task : chainTasks) {
                    markTaskFailed(task.getId(), ex);
                }
            }
        }
    }

    private void executeChainBatch(Long chainId, List<MatchingTask> chainTasks) {
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

        // 2. 构建节点 profile（只构建一次）
        Map<Long, String> nodeProfiles = new LinkedHashMap<>();
        Map<Long, String> nodeTextHashes = new LinkedHashMap<>();
        for (ChainNodeCandidateDTO node : nodes) {
            String profile = profileTextBuilder.buildNodeProfile(node);
            nodeProfiles.put(node.getNodeId(), profile);
            nodeTextHashes.put(node.getNodeId(),
                    git.ggwzz.matching.embedding.TextHash.sha256(profile));
        }

        // 3. 批量加载节点向量（只查一次数据库）
        String modelName = embeddingVectorService.getCurrentModelName();
        Map<Long, float[]> nodeVectors = embeddingVectorService.getBatchCachedVectors(
                CHAIN_NODE_OBJECT, new ArrayList<>(nodeProfiles.keySet()), modelName, nodeTextHashes);

        // 4. 对缺失的节点向量逐个生成
        List<Long> missingNodeIds = nodeProfiles.keySet().stream()
                .filter(id -> !nodeVectors.containsKey(id))
                .toList();
        if (!missingNodeIds.isEmpty()) {
            log.info("generating missing node vectors: chainId={}, missingCount={}", chainId, missingNodeIds.size());
            for (Long nodeId : missingNodeIds) {
                try {
                    float[] vec = embeddingVectorService.getOrCreateVectorDirect(
                            CHAIN_NODE_OBJECT, nodeId, modelName, nodeProfiles.get(nodeId));
                    nodeVectors.put(nodeId, vec);
                } catch (Exception ex) {
                    log.warn("failed to generate node vector: nodeId={}", nodeId, ex);
                }
            }
        }

        long vectorLoadMs = (System.nanoTime() - chainStart) / 1_000_000;
        log.info("chain vector snapshot ready: chainId={}, nodes={}, cached={}, generated={}, elapsedMs={}",
                chainId, nodes.size(), nodeVectors.size() - missingNodeIds.size(),
                missingNodeIds.size(), vectorLoadMs);

        // 5. 逐企业计算匹配
        Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> enterpriseCache = new HashMap<>();
        for (MatchingTask task : chainTasks) {
            if (isCancelled(task.getId())) {
                continue;
            }
            try {
                executeSingleInBatch(task, chainId, nodes, nodeProfiles, nodeVectors,
                        enterpriseCache, modelName);
            } catch (Exception ex) {
                log.warn("batch single match failed: taskId={}, enterpriseId={}, chainId={}",
                        task.getId(), task.getEnterpriseId(), chainId, ex);
                markTaskFailed(task.getId(), ex);
            }
        }

        long chainElapsed = (System.nanoTime() - chainStart) / 1_000_000;
        log.info("chain batch done: chainId={}, tasks={}, elapsedMs={}", chainId, chainTasks.size(), chainElapsed);
    }

    private void executeSingleInBatch(MatchingTask task,
                                       Long chainId,
                                       List<ChainNodeCandidateDTO> nodes,
                                       Map<Long, String> nodeProfiles,
                                       Map<Long, float[]> nodeVectors,
                                       Map<Long, RuleBasedMatchingProvider.EnterpriseInfo> enterpriseCache,
                                       String modelName) {
        long start = System.nanoTime();
        Long enterpriseId = task.getEnterpriseId();

        // 获取企业信息（带内存缓存）
        RuleBasedMatchingProvider.EnterpriseInfo enterprise = enterpriseCache.computeIfAbsent(
                enterpriseId, id -> enterpriseQueryService.getById(id));
        if (enterprise == null) {
            markTaskFailed(task.getId(), new RuntimeException("企业不存在: " + enterpriseId));
            return;
        }

        String enterpriseProfile = profileTextBuilder.buildEnterpriseProfile(enterprise);

        // 获取企业向量
        float[] enterpriseVector;
        try {
            enterpriseVector = embeddingVectorService.getOrCreateVectorDirect(
                    ENTERPRISE_OBJECT, enterpriseId, modelName, enterpriseProfile);
        } catch (Exception ex) {
            log.warn("enterprise vector failed, fallback to rules: enterpriseId={}", enterpriseId, ex);
            List<MatchingResult> ruleResults = ruleBasedMatchingProvider.match(enterpriseId, chainId);
            int topK = embeddingProperties.getHybrid().getTopK();
            List<MatchingResult> topResults = ruleResults.stream()
                    .sorted(Comparator.comparing(MatchingResult::getConfidence,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .limit(Math.max(1, topK))
                    .toList();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            markTaskCompleted(task.getId(), topResults, elapsed);
            return;
        }

        // 规则匹配
        Map<Long, MatchingResult> ruleResults = ruleBasedMatchingProvider.match(enterpriseId, chainId)
                .stream()
                .collect(Collectors.toMap(MatchingResult::getNodeId, r -> r,
                        (l, r) -> l, LinkedHashMap::new));

        // 计算混合分数
        List<ScoredNode> scoredNodes = new ArrayList<>();
        for (ChainNodeCandidateDTO node : nodes) {
            float[] nodeVec = nodeVectors.get(node.getNodeId());
            if (nodeVec == null) {
                continue; // 跳过无向量的节点
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
        markTaskCompleted(task.getId(), results, elapsed);
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
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence);
        } catch (Exception ex) {
            return "{\"model\":\"HYBRID_EMBEDDING_V2\"}";
        }
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private void markTaskCompleted(Long taskId, List<MatchingResult> results, long elapsedMs) {
        // 删除旧结果
        matchingResultMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingResult>()
                        .eq(MatchingResult::getTaskId, taskId));

        // 保存新结果
        int rankNo = 1;
        for (MatchingResult result : results) {
            if (isCancelled(taskId)) {
                return;
            }
            result.setTaskId(taskId);
            result.setRankNo(rankNo++);
            result.setDecision(MatchingDecision.PENDING.name());
            persistenceSupport.prepareForRemotePersistence(result);
            matchingResultMapper.insert(result);
        }

        // 更新任务状态
        MatchingTask update = new MatchingTask();
        update.setId(taskId);
        update.setStatus(MatchingTaskStatus.COMPLETED.name());
        update.setInputSnapshot(MatchingTaskSnapshotSupport.completionSnapshot(
                null, "BATCH_LOCAL", elapsedMs, results.size()));
        matchingTaskMapper.updateById(update);
    }

    private void markTaskFailed(Long taskId, Exception ex) {
        matchingResultMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingResult>()
                        .eq(MatchingResult::getTaskId, taskId));

        MatchingTask update = new MatchingTask();
        update.setId(taskId);
        update.setStatus(MatchingTaskStatus.FAILED.name());
        update.setErrorMessage(ex.getMessage() != null ? ex.getMessage().substring(0, Math.min(500, ex.getMessage().length())) : "unknown error");
        matchingTaskMapper.updateById(update);
    }

    private boolean isCancelled(Long taskId) {
        MatchingTask task = matchingTaskMapper.selectById(taskId);
        return task != null && MatchingTaskStatus.CANCELLED.name().equals(task.getStatus());
    }

    private record ScoredNode(
            ChainNodeCandidateDTO node,
            double vectorScore,
            double ruleScore,
            double finalScore,
            MatchingResult ruleResult) {
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 6：EmbeddingVectorService 增加 getCurrentModelName 方法

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/embedding/EmbeddingVectorService.java`

- [ ] **步骤 1：添加 getCurrentModelName 方法**

```java
public String getCurrentModelName() {
    return embeddingClient.modelName();
}
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 7：重构 MatchingServiceImpl.createBatchTasksAsync

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/service/impl/MatchingServiceImpl.java`

- [ ] **步骤 1：注入 MatchingBatchLocalEngine**

在 `MatchingServiceImpl` 中添加字段注入：

```java
private final MatchingBatchLocalEngine batchLocalEngine;
```

- [ ] **步骤 2：重写 createBatchTasksAsync 方法**

将原来的嵌套循环改为：先批量创建任务，再提交给批量引擎。

```java
@Override
public BatchMatchingTaskVO createBatchTasksAsync(CreateBatchMatchingTaskRequest request, Long operator) {
    BatchScope scope = resolveBatchScope(request);
    long totalPairs = (long) scope.enterpriseIds().size() * scope.chainIds().size();
    if (totalPairs > MAX_BATCH_TASK_PAIRS) {
        throw new BusinessException(ResultCode.BAD_REQUEST,
                "批量智能挂链一次最多提交 " + MAX_BATCH_TASK_PAIRS + " 个组合，请缩小企业或产业链范围");
    }

    // 1. 批量创建任务记录
    List<MatchingTask> tasks = new ArrayList<>((int) totalPairs);
    for (Long enterpriseId : scope.enterpriseIds()) {
        for (Long chainId : scope.chainIds()) {
            CreateMatchingTaskRequest singleRequest = new CreateMatchingTaskRequest();
            singleRequest.setEnterpriseId(enterpriseId);
            singleRequest.setChainId(chainId);
            MatchingTask task = buildTask(singleRequest, operator, MatchingTaskStatus.PROCESSING,
                    "BATCH_LOCAL", hybridEmbeddingMatchingProvider);
            matchingTaskMapper.insert(task);
            tasks.add(task);
        }
    }

    // 2. 提交给批量引擎异步执行
    List<Long> taskIds = tasks.stream().map(MatchingTask::getId).toList();
    List<Long> enterpriseIds = scope.enterpriseIds();
    List<Long> chainIds = scope.chainIds();

    // 异步执行批量匹配
    org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = null;
    try {
        executor = (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)
                org.springframework.context.ApplicationContextProvider
                        .getApplicationContext().getBean("matchingTaskExecutor");
    } catch (Exception ignored) {
    }

    if (executor != null) {
        executor.execute(() -> batchLocalEngine.executeBatch(tasks, enterpriseIds, chainIds));
    } else {
        new Thread(() -> batchLocalEngine.executeBatch(tasks, enterpriseIds, chainIds),
                "batch-matching-fallback").start();
    }

    // 3. 构建响应
    List<MatchingTaskVO> taskVOs = tasks.stream()
            .map(t -> {
                MatchingTaskVO vo = new MatchingTaskVO();
                vo.setTaskId(t.getId());
                vo.setEnterpriseId(t.getEnterpriseId());
                vo.setChainId(t.getChainId());
                vo.setStatus(t.getStatus());
                vo.setResults(List.of());
                return vo;
            })
            .toList();

    BatchMatchingTaskVO vo = new BatchMatchingTaskVO();
    vo.setEnterpriseCount(scope.enterpriseIds().size());
    vo.setChainCount(scope.chainIds().size());
    vo.setTotalPairs((int) totalPairs);
    vo.setSubmittedCount(tasks.size());
    vo.setTasks(taskVOs);
    log.info("batch matching tasks submitted: enterpriseCount={}, chainCount={}, submittedCount={}",
            vo.getEnterpriseCount(), vo.getChainCount(), vo.getSubmittedCount());
    return vo;
}
```

- [ ] **步骤 3：重写 batchQueryTasks 使用批量查询**

```java
@Override
public BatchQueryVO batchQueryTasks(BatchQueryRequest request) {
    if (request == null || request.getTaskIds() == null || request.getTaskIds().isEmpty()) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "任务ID列表不能为空");
    }

    List<Long> taskIds = request.getTaskIds();

    // 批量查询任务
    List<MatchingTask> taskEntities = matchingTaskMapper.selectBatchByIds(taskIds);
    Map<Long, MatchingTask> taskMap = taskEntities.stream()
            .collect(Collectors.toMap(MatchingTask::getId, t -> t, (l, r) -> l));

    // 批量查询结果
    List<MatchingResult> allResults = matchingResultMapper.selectByTaskIds(taskIds);
    Map<Long, List<MatchingResult>> resultsByTaskId = allResults.stream()
            .collect(Collectors.groupingBy(MatchingResult::getTaskId));

    // 组装 VO
    List<MatchingTaskVO> tasks = new ArrayList<>();
    for (Long taskId : taskIds) {
        MatchingTask taskEntity = taskMap.get(taskId);
        if (taskEntity == null) {
            continue;
        }
        List<MatchingResult> results = resultsByTaskId.getOrDefault(taskId, List.of());
        tasks.add(toTaskVO(taskEntity, results));
    }

    BatchQueryVO vo = new BatchQueryVO();
    vo.setTotal(tasks.size());
    vo.setProcessingCount((int) tasks.stream()
            .filter(t -> "PROCESSING".equals(t.getStatus()) || "RETRYING".equals(t.getStatus()))
            .count());
    vo.setCompletedCount((int) tasks.stream()
            .filter(t -> "COMPLETED".equals(t.getStatus()))
            .count());
    vo.setFailedCount((int) tasks.stream()
            .filter(t -> "FAILED".equals(t.getStatus()) || "TIMEOUT".equals(t.getStatus()))
            .count());
    vo.setAcceptedCount((int) tasks.stream()
            .flatMap(t -> t.getResults().stream())
            .filter(r -> "ACCEPTED".equals(r.getDecision()))
            .count());
    vo.setRejectedCount((int) tasks.stream()
            .flatMap(t -> t.getResults().stream())
            .filter(r -> "REJECTED".equals(r.getDecision()))
            .count());
    vo.setTasks(tasks);
    return vo;
}
```

- [ ] **步骤 4：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 8：MatchingTaskMapper 增加 selectBatchByIds

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/mapper/MatchingTaskMapper.java`

- [ ] **步骤 1：添加 selectBatchByIds 方法**

```java
import java.util.List;

@Select("<script>"
        + "SELECT * FROM matching_task WHERE id IN "
        + "<foreach item='id' collection='taskIds' open='(' separator=',' close=')'>"
        + "#{id}"
        + "</foreach>"
        + "</script>")
List<MatchingTask> selectBatchByIds(@Param("taskIds") List<Long> taskIds);
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 9：MatchingResultMapper 增加 selectByTaskIds

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/mapper/MatchingResultMapper.java`

- [ ] **步骤 1：添加 selectByTaskIds 方法**

```java
import java.util.List;

@Select("<script>"
        + "SELECT * FROM matching_result WHERE task_id IN "
        + "<foreach item='tid' collection='taskIds' open='(' separator=',' close=')'>"
        + "#{tid}"
        + "</foreach>"
        + " ORDER BY task_id, rank_no ASC"
        + "</script>")
List<MatchingResult> selectByTaskIds(@Param("taskIds") List<Long> taskIds);
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 10：ApplicationContextProvider 工具类

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/common/config/ApplicationContextProvider.java`

如果已存在则跳过。

- [ ] **步骤 1：创建 ApplicationContextProvider**

```java
package git.ggwzz.common.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return context;
    }
}
```

- [ ] **步骤 2：验证编译**

运行：`cd groupproject-back/back && mvn compile -q`
预期：BUILD SUCCESS

---

## 任务 11：前端 BatchWorkbench.vue 组件

**文件：**
- 创建：`groupproject/front/src/views/matching/BatchWorkbench.vue`

- [ ] **步骤 1：创建 BatchWorkbench.vue**

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { fetchEnterprises } from "@/api/enterprise";
import { fetchChains, fetchNodeTree } from "@/api/chain";
import {
  createBatchMatchingTasks,
  fetchBatchTasks,
} from "@/api/matching";
import { useMatchingStore } from "@/stores/matching";
import {
  buildBatchMatchingRequest,
  flattenChainNodes,
  isTaskRunningStatus,
  matchingTaskStatusTip,
} from "./matchingViewModel";
import type {
  BatchMatchingSelection,
  ChainNode,
  EnterpriseListItem,
  IndustryChain,
  MatchingTask,
} from "@/types/domain";

const emit = defineEmits<{
  "view-single": [enterpriseId: number, chainId: number];
}>();

const matchingStore = useMatchingStore();

const enterprises = ref<EnterpriseListItem[]>([]);
const chains = ref<IndustryChain[]>([]);
const chainNodes = ref<ChainNode[]>([]);
const enterpriseLoading = ref(false);
const chainLoading = ref(false);
const submitting = ref(false);

// 选择状态
const enterpriseScope = ref<"ALL" | "SELECTED">("ALL");
const chainScope = ref<"ALL" | "SELECTED">("ALL");
const selectedEnterpriseIds = ref<number[]>([]);
const selectedChainIds = ref<number[]>([]);

// 批量任务状态
const batchSubmitted = ref(false);
const batchTasks = ref<MatchingTask[]>([]);
const batchTotal = ref(0);
const batchProcessingCount = ref(0);
const batchCompletedCount = ref(0);
const batchFailedCount = ref(0);
const batchAcceptedCount = ref(0);
const batchRejectedCount = ref(0);

// 轮询
let pollTimer: ReturnType<typeof setInterval> | null = null;
const POLL_INTERVAL = 2000;

const flatChainNodes = computed(() => flattenChainNodes(chainNodes.value));

const canSubmit = computed(() => {
  if (enterpriseScope.value === "SELECTED" && selectedEnterpriseIds.value.length === 0) return false;
  if (chainScope.value === "SELECTED" && selectedChainIds.value.length === 0) return false;
  return true;
});

const isRunning = computed(() => batchProcessingCount.value > 0);

const progressPercent = computed(() => {
  if (batchTotal.value === 0) return 0;
  return Math.round(
    ((batchCompletedCount.value + batchFailedCount.value + batchAcceptedCount.value + batchRejectedCount.value) /
      batchTotal.value) *
      100
  );
});

async function loadEnterprises() {
  enterpriseLoading.value = true;
  try {
    const res = await fetchEnterprises({ pageNum: 1, pageSize: 9999 });
    enterprises.value = res.records.filter((e) => e.status === "ACTIVE");
  } catch {
    enterprises.value = [];
  } finally {
    enterpriseLoading.value = false;
  }
}

async function loadChains() {
  chainLoading.value = true;
  try {
    chains.value = await fetchChains();
    if (chains.value.length > 0) {
      chainNodes.value = await fetchNodeTree(chains.value[0].id);
    }
  } catch {
    chains.value = [];
    chainNodes.value = [];
  } finally {
    chainLoading.value = false;
  }
}

async function submitBatch() {
  if (!canSubmit.value || submitting.value) return;
  submitting.value = true;
  try {
    const selection: BatchMatchingSelection = {
      enterpriseScope: enterpriseScope.value,
      chainScope: chainScope.value,
      selectedEnterpriseIds: selectedEnterpriseIds.value,
      selectedChainIds: selectedChainIds.value,
    };
    const request = buildBatchMatchingRequest(selection);
    const result = await createBatchMatchingTasks(request);

    // 保存任务 ID
    const taskIds = result.tasks.map((t) => t.taskId);
    matchingStore.setBatchTaskIds(taskIds);

    // 初始化状态
    batchSubmitted.value = true;
    batchTasks.value = result.tasks;
    batchTotal.value = result.totalPairs;
    batchProcessingCount.value = result.submittedCount;
    batchCompletedCount.value = 0;
    batchFailedCount.value = 0;
    batchAcceptedCount.value = 0;
    batchRejectedCount.value = 0;

    ElMessage.success(`已提交 ${result.submittedCount} 个批量挂链任务`);

    // 开始轮询
    startPolling();
  } catch (err: any) {
    ElMessage.error(err?.message || "批量提交失败");
  } finally {
    submitting.value = false;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(pollBatchStatus, POLL_INTERVAL);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function pollBatchStatus() {
  const taskIds = matchingStore.batchTaskIds;
  if (!taskIds.length) return;

  try {
    const result = await fetchBatchTasks(taskIds);
    batchTasks.value = result.tasks;
    batchTotal.value = result.total;
    batchProcessingCount.value = result.processingCount;
    batchCompletedCount.value = result.completedCount;
    batchFailedCount.value = result.failedCount;
    batchAcceptedCount.value = result.acceptedCount;
    batchRejectedCount.value = result.rejectedCount;

    // 全部完成则停止轮询
    if (result.processingCount === 0) {
      stopPolling();
    }
  } catch {
    // 轮询失败不停止，下次继续
  }
}

function viewSingle(enterpriseId: number, chainId: number) {
  emit("view-single", enterpriseId, chainId);
}

function resetBatch() {
  stopPolling();
  batchSubmitted.value = false;
  batchTasks.value = [];
  batchTotal.value = 0;
  batchProcessingCount.value = 0;
  batchCompletedCount.value = 0;
  batchFailedCount.value = 0;
  batchAcceptedCount.value = 0;
  batchRejectedCount.value = 0;
  matchingStore.setBatchTaskIds([]);
}

onBeforeUnmount(() => {
  stopPolling();
});

// 初始化加载
loadEnterprises();
loadChains();
</script>

<template>
  <div class="batch-workbench">
    <!-- 范围选择区 -->
    <el-card v-if="!batchSubmitted" shadow="never" class="selection-card">
      <template #header>
        <span class="card-title">批量挂链范围选择</span>
      </template>

      <el-form label-width="100px">
        <el-form-item label="企业范围">
          <el-radio-group v-model="enterpriseScope">
            <el-radio value="ALL">全部存续企业</el-radio>
            <el-radio value="SELECTED">指定企业</el-radio>
          </el-radio-group>
          <el-select
            v-if="enterpriseScope === 'SELECTED'"
            v-model="selectedEnterpriseIds"
            multiple
            filterable
            placeholder="选择企业"
            :loading="enterpriseLoading"
            style="width: 100%; margin-top: 8px"
          >
            <el-option
              v-for="e in enterprises"
              :key="e.id"
              :label="e.name"
              :value="e.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="产业链范围">
          <el-radio-group v-model="chainScope">
            <el-radio value="ALL">全部产业链</el-radio>
            <el-radio value="SELECTED">指定产业链</el-radio>
          </el-radio-group>
          <el-select
            v-if="chainScope === 'SELECTED'"
            v-model="selectedChainIds"
            multiple
            filterable
            placeholder="选择产业链"
            :loading="chainLoading"
            style="width: 100%; margin-top: 8px"
          >
            <el-option
              v-for="c in chains"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="submitBatch"
          >
            提交批量挂链
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 批次进度区 -->
    <el-card v-if="batchSubmitted" shadow="never" class="progress-card">
      <template #header>
        <div class="progress-header">
          <span class="card-title">批量挂链进度</span>
          <el-button size="small" @click="resetBatch">新建批次</el-button>
        </div>
      </template>

      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="4">
          <el-statistic title="总任务" :value="batchTotal" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="处理中" :value="batchProcessingCount" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="已完成" :value="batchCompletedCount" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="失败" :value="batchFailedCount" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="已采纳" :value="batchAcceptedCount" />
        </el-col>
        <el-col :span="4">
          <el-statistic title="已拒绝" :value="batchRejectedCount" />
        </el-col>
      </el-row>

      <!-- 进度条 -->
      <el-progress
        :percentage="progressPercent"
        :status="isRunning ? undefined : 'success'"
        :stroke-width="20"
        :text-inside="true"
        style="margin: 16px 0"
      />

      <!-- 任务列表 -->
      <el-table :data="batchTasks" stripe border style="width: 100%" max-height="500">
        <el-table-column prop="taskId" label="任务ID" width="80" />
        <el-table-column prop="enterpriseId" label="企业ID" width="100" />
        <el-table-column prop="chainId" label="产业链ID" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'COMPLETED'
                  ? 'success'
                  : row.status === 'PROCESSING' || row.status === 'RETRYING'
                    ? 'warning'
                    : row.status === 'FAILED' || row.status === 'TIMEOUT'
                      ? 'danger'
                      : 'info'
              "
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="候选数" width="80">
          <template #default="{ row }">
            {{ row.results?.length ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="Top1 节点" min-width="200">
          <template #default="{ row }">
            <span v-if="row.results?.length">
              {{ row.results[0].nodePath }}
              <el-tag size="small" type="info">
                {{ Number(row.results[0].confidence).toFixed(2) }}
              </el-tag>
            </span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="primary"
              link
              size="small"
              @click="viewSingle(row.enterpriseId, row.chainId)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.batch-workbench {
  padding: 0;
}

.selection-card,
.progress-card {
  margin-bottom: 16px;
}

.card-title {
  font-weight: 600;
  font-size: 16px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-row {
  margin-bottom: 8px;
}

.text-muted {
  color: #999;
}
</style>
```

- [ ] **步骤 2：验证前端编译**

运行：`cd groupproject/front && npx vue-tsc --noEmit 2>&1 | head -20`
预期：无类型错误（或只有已存在的无关错误）

---

## 任务 12：前端 fetchBatchTasks 加 mock 分支

**文件：**
- 修改：`groupproject/front/src/api/matching.ts:94-96`

- [ ] **步骤 1：给 fetchBatchTasks 添加 mock 支持**

```ts
export function fetchBatchTasks(taskIds: number[]): Promise<BatchQueryVO> {
  if (isMockApiEnabled()) return mockApi.fetchBatchTasks(taskIds);
  return post("/matching/tasks/batch-query", { taskIds });
}
```

- [ ] **步骤 2：验证前端编译**

运行：`cd goupproject/front && npx vue-tsc --noEmit 2>&1 | head -20`
预期：无类型错误

---

## 任务 13：全量编译验证

- [ ] **步骤 1：后端编译**

运行：`cd groupproject-back/back && mvn compile`
预期：BUILD SUCCESS

- [ ] **步骤 2：前端类型检查**

运行：`cd groupproject/front && npx vue-tsc --noEmit`
预期：无新增类型错误

- [ ] **步骤 3：提交代码**

```bash
cd groupproject-back/back && git add -A && git commit -m "perf: 批量挂链性能优化 - 批量引擎+Caffeine缓存+专用线程池"
cd ../../groupproject/front && git add -A && git commit -m "feat: 新增批量挂链工作台组件 BatchWorkbench.vue"
```
