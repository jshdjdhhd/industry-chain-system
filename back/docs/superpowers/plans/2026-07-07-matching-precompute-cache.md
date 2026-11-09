# 智能挂链预热缓存实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将智能挂链预热计算结果存入数据库，批量挂链时已计算过的企业和产业链直接复用缓存，新增数据继续真实计算。

**架构：** 后端新增推荐结果缓存表与服务，缓存键由企业、产业链、模型、画像 hash、节点 hash、topK 和权重组成。批量引擎先查缓存，命中则复制结果并完成任务，未命中才生成 embedding 和匹配结果，并回写缓存。Embedding 客户端增加批量请求能力，减少 Ollama 单条 HTTP 开销。

**技术栈：** Spring Boot、MyBatis-Plus、MySQL 5.7 Flyway 风格 SQL、JUnit 5、Vue 3/TypeScript。

---

### 任务 1：后端推荐缓存模型与 Mapper

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/matching/entity/MatchingRecommendationCache.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/matching/mapper/MatchingRecommendationCacheMapper.java`
- 创建：`groupproject-back/back/src/main/resources/db/migration/V23__matching_recommendation_cache.sql`
- 测试：`groupproject-back/back/src/test/java/git/ggwzz/matching/mapper/MatchingRecommendationCacheMapperTest.java`

- [ ] **步骤 1：编写失败的 Mapper 测试**

```java
@Test
void selectsLatestActiveCacheByFingerprint() {
    MatchingRecommendationCache cache = fixture("hash-a", "chain-hash-a");
    mapper.insert(cache);
    MatchingRecommendationCache found = mapper.selectActiveByFingerprint(
            cache.getEnterpriseId(), cache.getChainId(), cache.getModelName(),
            cache.getEnterpriseTextHash(), cache.getChainNodesHash(),
            cache.getTopK(), cache.getVectorWeight(), cache.getRuleWeight());
    assertThat(found.getResultJson()).contains("nodeId");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=MatchingRecommendationCacheMapperTest test`
预期：编译失败，缓存实体和 Mapper 不存在。

- [ ] **步骤 3：添加实体、Mapper 和迁移**

实现缓存表字段：`enterprise_id`、`chain_id`、`model_name`、`enterprise_text_hash`、`chain_nodes_hash`、`top_k`、`vector_weight`、`rule_weight`、`result_json`、`status`、`computed_at`、`expire_at`、`is_deleted`，并添加查询索引。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -Dtest=MatchingRecommendationCacheMapperTest test`
预期：PASS。

### 任务 2：后端批量 embedding

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/embedding/EmbeddingClient.java`
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/embedding/LocalQwenEmbeddingClient.java`
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/embedding/EmbeddingVectorService.java`
- 测试：`groupproject-back/back/src/test/java/git/ggwzz/matching/embedding/LocalQwenEmbeddingClientTest.java`
- 测试：`groupproject-back/back/src/test/java/git/ggwzz/matching/embedding/EmbeddingVectorServiceTest.java`

- [ ] **步骤 1：编写失败测试**

```java
@Test
void embedBatchSendsArrayInputAndParsesMultipleVectors() {
    List<float[]> vectors = client.embedBatch(List.of("enterprise", "node"));
    assertThat(vectors).hasSize(2);
    assertThat(recordedRequestBody).contains("\"input\":[");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=LocalQwenEmbeddingClientTest,EmbeddingVectorServiceTest test`
预期：编译失败或行为失败，因为 `embedBatch` 尚未实现。

- [ ] **步骤 3：实现批量 embedding**

`EmbeddingClient` 增加默认 `embedBatch`；`LocalQwenEmbeddingClient` 用 `input` 数组请求；`EmbeddingVectorService` 新增批量获取或创建向量方法，批量保存新向量。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -Dtest=LocalQwenEmbeddingClientTest,EmbeddingVectorServiceTest test`
预期：PASS。

### 任务 3：批量挂链缓存命中与回写

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/matching/service/impl/MatchingRecommendationCacheService.java`
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/matching/service/impl/MatchingBatchLocalEngine.java`
- 测试：`groupproject-back/back/src/test/java/git/ggwzz/matching/service/impl/MatchingRecommendationCacheServiceTest.java`

- [ ] **步骤 1：编写失败测试**

```java
@Test
void cacheKeyChangesWhenEnterpriseOrChainProfileChanges() {
    CacheLookup lookup = cacheService.buildLookup(enterpriseId, chainId, modelName, enterpriseProfile, nodeProfiles);
    assertThat(lookup.chainNodesHash()).hasSize(64);
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -Dtest=MatchingRecommendationCacheServiceTest test`
预期：编译失败，因为服务不存在。

- [ ] **步骤 3：实现缓存服务和批量引擎接入**

批量引擎每个任务先尝试缓存；命中则复制缓存结果并 `markTaskCompleted`；未命中走现有匹配，完成后 `upsert` 缓存。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -Dtest=MatchingRecommendationCacheServiceTest test`
预期：PASS。

### 任务 4：演示环境日志降噪

**文件：**
- 修改：`groupproject-back/back/src/main/resources/application-dev.yml`
- 修改：`groupproject-back/back/src/main/resources/application-local.yml`

- [ ] **步骤 1：改配置**

将 `mybatis-plus.configuration.log-impl` 改为 `org.apache.ibatis.logging.nologging.NoLoggingImpl`，将 `git.ggwzz` 开发日志级别改为 `INFO`。

- [ ] **步骤 2：运行配置检查**

运行：`mvn -DskipTests compile`
预期：BUILD SUCCESS。

### 任务 5：前端必要适配

**文件：**
- 仅在后端响应新增缓存命中统计时修改：`groupproject/front/src/types/domain.ts`、`groupproject/front/src/api/matching.ts`、`groupproject/front/src/views/matching/BatchWorkbench.vue`

- [ ] **步骤 1：检查是否需要前端改动**

如果后端保持现有 `BatchMatchingTaskVO` 和 `BatchQueryVO` 结构，则不改前端。

- [ ] **步骤 2：运行前端类型检查**

运行：`npm run type-check` 或项目已有等价命令。
预期：PASS。

### 任务 6：最终验证与提交

**文件：**
- 后端仓库：仅 stage 本次智能挂链相关文件。
- 前端仓库：仅在确有前端改动时 stage 本次文件。

- [ ] **步骤 1：运行后端验证**

运行：`mvn test` 或至少运行相关测试和 `mvn -DskipTests compile`。

- [ ] **步骤 2：运行前端验证**

运行：`npm run type-check`，若有相关测试则运行。

- [ ] **步骤 3：审查 diff**

运行：`git diff --cached`，确认未包含用户既有无关改动。

- [ ] **步骤 4：提交**

后端：`git commit -m "feat: add matching recommendation cache"`
前端：如果有前端改动，`git commit -m "feat: show matching cache progress"`。
