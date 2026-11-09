# 企业多维画像与相似企业发现开发计划

日期：2026-07-07

## 1. 背景与目标

当前系统已有企业管理、产业链挂接、智能挂链、embedding 向量缓存等基础能力。企业详情页目前展示基础信息、企业标签、已挂接节点和操作日志，但缺少面向分析场景的多维画像展示，也缺少基于企业画像的相似企业发现入口。

本次功能目标是在现有代码基础上实现：

1. 在企业详情页新增单个企业雷达图，展示企业多维画像。
2. 基于企业画像 embedding 向量，推荐 Top-N 相似企业。
3. 相似企业支持点击跳转企业详情。
4. 支持刷新画像与选择相似企业数量。
5. 将性能优化纳入第一版：候选分页批处理、批量向量缓存读取、ACTIVE 企业向量预热、相似结果缓存、可替换向量检索服务边界。

## 2. 已确认范围

### 2.1 本次实现

- 企业详情页同屏展示“企业多维画像”区块。
- 雷达图展示单个企业画像，指标采用“数据可落地”口径。
- 相似企业候选范围限定为 `ACTIVE` 且未删除企业，并排除当前企业。
- 相似企业排序以 embedding 向量余弦相似度为主，规则解释为辅。
- 相似企业列表支持点击跳转企业详情。
- 画像区块支持刷新按钮。
- 支持相似企业数量选择器，第一版选项为 `5 / 10 / 20`。
- 实现相似结果缓存表和向量预热能力，提高运行速度。
- 补充后端和前端测试，包含空请求、非法参数、空数据、缓存失败、向量失败等边界条件。

### 2.2 暂不实现

- 双企业雷达图对比。
- 独立企业画像报告页。
- 独立向量数据库或 ANN 检索服务部署。
- 大模型主观打分生成雷达图指标。

独立向量数据库和 ANN 检索属于未来演进方向，第一版保持在 MySQL 5.7 与现有 `ai_embedding_vector` 缓存体系内落地。

## 3. 总体方案

采用“画像分析服务 + 企业详情页同屏展示 + 向量主导相似企业”的方案。

后端新增独立企业画像分析接口，不把雷达图和相似企业塞进现有企业详情接口。这样可以避免基础详情被慢计算拖慢，也为画像结果独立刷新和异步任务化升级保留接口边界。

前端在企业详情页新增业务组件 `EnterpriseProfilePanel.vue`，该组件独立请求画像分析接口并管理自己的 loading、error、empty 状态，不影响企业详情页基础信息加载。

## 4. 后端设计

### 4.1 接口设计

新增画像分析查询接口：

```http
GET /api/enterprises/{id}/profile-analysis?similarLimit=5
```

权限沿用企业查询权限：

```java
@PreAuthorize("hasAuthority('enterprise:enterprise:query')")
```

返回统一结构 `{ code, message, data }`，其中 `data` 为 `EnterpriseProfileAnalysisVO`。

返回结构：

```json
{
  "enterpriseId": 1,
  "enterpriseName": "安徽某某科技有限公司",
  "radar": {
    "metrics": [
      {
        "key": "patentCount",
        "name": "专利数量",
        "value": 60,
        "rawValue": "3 条",
        "description": "根据企业专利信息条目数计算，5 条及以上记满分"
      }
    ]
  },
  "similarEnterprises": [
    {
      "enterpriseId": 2,
      "enterpriseName": "安徽某某材料有限公司",
      "similarity": 0.9234,
      "similarityPercent": 92,
      "industry": "新能源汽车",
      "mainProducts": "动力电池、电控系统",
      "reasons": [
        "所属行业相同：新能源汽车",
        "主营产品关键词重合：电池、电控"
      ]
    }
  ],
  "generatedAt": "2026-07-07T10:00:00"
}
```

新增向量预热接口：

```http
POST /api/enterprises/profile-analysis/vector-warmup
```

权限：

```java
@PreAuthorize("hasAuthority('enterprise:enterprise:update')")
```

原因：预热会生成向量并写入缓存，虽然不修改企业业务字段，但属于维护性写操作。

预热请求支持企业 ID 列表参数；请求体为空或企业 ID 列表为空时预热全部 ACTIVE 企业。单次预热最多处理 500 家企业；超过 500 个 ID 返回参数错误，全量预热按分页批处理执行并在响应中返回本次处理总数、成功数、跳过数和失败数。

### 4.2 VO 设计

新增 VO 放在 `git.ggwzz.enterprise.vo`：

- `EnterpriseProfileAnalysisVO`
- `EnterpriseRadarVO`
- `EnterpriseRadarMetricVO`
- `SimilarEnterpriseVO`
- `VectorWarmupResultVO`

Entity 禁止直接作为接口响应，Controller 只返回 VO。

### 4.3 Service 设计

新增服务：

- `EnterpriseProfileAnalysisService`
- `EnterpriseProfileAnalysisServiceImpl`
- `EnterpriseSimilarityService`
- `EnterpriseSimilarityServiceImpl`

职责划分：

1. `EnterpriseController` 只接收参数、做校验、调用 Service、返回 `Result`。
2. `EnterpriseProfileAnalysisService` 聚合雷达图指标和相似企业结果。
3. `EnterpriseRadarMetricCalculator` 负责雷达图指标计算。
4. `EnterpriseSimilarityService` 负责相似企业 Top-N 检索、缓存命中、向量生成、相似度计算和原因解释。
5. Mapper 只做数据访问，不写业务判断。

复用现有能力：

- `EmbeddingProfileTextBuilder`
- `EmbeddingVectorService`
- `EmbeddingVectorService.getBatchCachedVectors(...)`
- `EmbeddingVectorService.getOrCreateVectorDirect(...)`
- `VectorMath.cosineSimilarity(...)`
- `TextHash.sha256(...)`

### 4.4 Mapper / 查询设计

新增或扩展 Mapper 方法，读取：

- 当前企业画像字段。
- ACTIVE 候选企业分页列表。
- 企业标签文本。
- 企业有效挂链关系、挂链置信度。
- 相似度缓存命中结果。
- 相似度缓存 upsert。

SQL 必须兼容 MySQL 5.7：

- 不使用窗口函数。
- 不使用递归 CTE。
- 不使用 `JSON_TABLE`。
- 参数使用 `#{}`，禁止 `${}` 拼接用户输入。
- 不使用 `SELECT *`。
- 单条 SQL 原则不超过 3 个 JOIN。

## 5. 雷达图指标算法

第一版固定 6 个维度，全部归一化到 `0-100`。

| 维度 | 数据来源 | 计算口径 |
| --- | --- | --- |
| 专利数量 | `enterprise.patent_info` | 对专利文本做条目计数，按换行、顿号、逗号、分号等切分；0 条为 0 分，5 条及以上满分。 |
| 招投标频次 | `enterprise.bid_info` | 对招投标文本做条目计数；0 条为 0 分，5 条及以上满分。 |
| 产品/技术丰富度 | `main_products`、`core_tech`、`keywords`、标签 | 统计去重关键词数量；0 个为 0 分，10 个及以上满分。 |
| 经营范围广度 | `business_scope` | 根据经营范围文本长度和关键词/分句数量综合计分；字段为空为 0。 |
| 产业链关联度 | `enterprise_node_rel` | 根据有效挂接节点数和挂接置信度计分；有人工或 AI 挂接且置信度高得分更高。 |
| 经营规模 | `output_value`、`tax_amount` | 综合产值和税收是否存在及数值规模；两者都有且数值较高得分更高。 |

每个指标返回：

- `key`：稳定字段名。
- `name`：中文显示名。
- `value`：`0-100` 雷达图值。
- `rawValue`：原始值展示，如“3 条”“8 个关键词”“暂无数据”。
- `description`：指标口径说明。

缺失数据不报错，指标得分为 0，并返回可理解的 `rawValue`。

## 6. 相似企业算法与性能优化

### 6.1 候选范围

候选企业必须满足：

- `status = 'ACTIVE'`
- `is_deleted = 0`
- `id != 当前企业 id`

### 6.2 主排序口径

主排序使用 embedding 余弦相似度。

规则解释只用于生成 `reasons`，不作为第一版主排序权重。解释包括：

- 所属行业相同。
- 共享标签。
- 主营产品关键词重合。
- 经营范围语义相似度高。

### 6.3 性能优化

第一版实现以下优化。

#### 6.3.1 分页批处理候选企业

不一次性加载所有 ACTIVE 企业。后端按批读取候选，例如每批 100 或 200 条。每批内计算相似度，只维护内存 Top-N 小结果集。

#### 6.3.2 批量读取已缓存向量

每批候选先构造画像文本与 text hash，再调用 `EmbeddingVectorService.getBatchCachedVectors(...)` 批量读取已缓存向量。缓存命中直接计算余弦相似度，未命中再按需生成向量。

#### 6.3.3 预计算 ACTIVE 企业画像向量

提供向量预热能力，用于提前生成 ACTIVE 企业画像向量。画像分析接口也会按需补齐缺失向量。预热接口必须限制单次处理量并返回成功、跳过、失败数量。

#### 6.3.4 相似结果缓存

新增 `enterprise_similarity_cache` 表。当 `source + target + model + sourceHash + targetHash` 一致时可复用缓存。企业画像字段变化会导致 hash 变化，从而自然失效。

缓存写入失败不影响本次返回，只记录日志。

#### 6.3.5 向量检索抽象边界

`EnterpriseSimilarityService` 只暴露“查相似企业 Top-N”的业务接口。当前实现为 MySQL + 批处理 + 向量缓存；未来可替换为独立向量数据库或 ANN 检索，不改 Controller 和前端 API。

### 6.4 失败处理

- 当前企业向量生成失败：返回明确业务错误。
- 单个候选企业向量生成失败：跳过候选并记录日志。
- 候选列表为空：返回空相似企业列表。
- 缓存写入失败：不影响本次返回。
- embedding 服务不可用时：雷达图仍可展示，相似企业返回空列表或错误提示，避免整个画像区块不可用。

## 7. 数据库设计

迁移文件版本使用当前仓库下一号版本。按当前已存在的 `V21__analysis_report.sql`，本功能新增迁移文件为：

```text
back/src/main/resources/db/migration/V22__enterprise_profile_analysis.sql
```

新增表：

```sql
CREATE TABLE IF NOT EXISTS `enterprise_similarity_cache` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '缓存ID',
    `source_enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '源企业ID',
    `target_enterprise_id` BIGINT UNSIGNED NOT NULL COMMENT '目标企业ID',
    `model_name` VARCHAR(100) NOT NULL COMMENT 'embedding模型名称',
    `source_text_hash` CHAR(64) NOT NULL COMMENT '源企业画像文本SHA-256',
    `target_text_hash` CHAR(64) NOT NULL COMMENT '目标企业画像文本SHA-256',
    `similarity` DECIMAL(6,5) NOT NULL COMMENT '相似度',
    `reasons` VARCHAR(1000) NULL COMMENT '相似原因JSON',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_enterprise_similarity_pair_model_hash`
        (`source_enterprise_id`, `target_enterprise_id`, `model_name`, `source_text_hash`, `target_text_hash`),
    KEY `idx_enterprise_similarity_source_model`
        (`source_enterprise_id`, `model_name`, `source_text_hash`, `is_deleted`, `similarity`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '企业相似度缓存表';
```

## 8. 前端设计

### 8.1 API 层

在 `front/src/api/enterprise.ts` 新增：

```ts
export function fetchEnterpriseProfileAnalysis(
    id: number,
    similarLimit = 5,
): Promise<EnterpriseProfileAnalysis>
```

真实接口：

```http
GET /enterprises/{id}/profile-analysis?similarLimit=5
```

Mock 模式补充 `mockApi.fetchEnterpriseProfileAnalysis()`。

### 8.2 类型定义

在 `front/src/types/domain.ts` 新增：

- `EnterpriseRadarMetric`
- `EnterpriseRadar`
- `SimilarEnterprise`
- `EnterpriseProfileAnalysis`

### 8.3 图表依赖

前端新增依赖：

```json
"echarts": "^5.x"
```

组件内按需导入，不全局注册。

### 8.4 组件设计

新增业务组件：

```text
front/src/views/enterprise/EnterpriseProfilePanel.vue
```

该组件放在 `views/enterprise`，因为它包含企业画像业务逻辑，不是通用组件。

职责：

- 接收当前 `enterpriseId`。
- 调用 `fetchEnterpriseProfileAnalysis()`。
- 管理自己的 loading、error、empty 状态。
- 左侧渲染 ECharts Radar。
- 右侧渲染相似企业 Top-N。
- 提供相似企业数量选择器。
- 提供刷新画像按钮。
- 点击相似企业跳转详情页。

### 8.5 页面集成

修改 `EnterpriseDetail.vue`，在“已挂接节点”和“操作日志”之间插入 `EnterpriseProfilePanel`。

同时修复路由参数变化场景：用户从当前企业详情页点击相似企业后，路由变为 `/enterprises/{targetId}`，详情页需要重新加载当前企业详情，画像组件也要重新加载目标企业画像。

### 8.6 交互设计

画像区块顶部工具栏：

- 相似企业数量选择器：`5 / 10 / 20`，默认 5。
- 刷新画像按钮：重新请求当前数量下的画像数据。

相似企业列表每条展示：

- 企业名称。
- 相似度百分比或进度条。
- 行业。
- 主营产品摘要。
- 相似原因。
- 查看详情入口。

点击企业名称、卡片或查看详情按钮跳转：

```ts
router.push(`/enterprises/${row.enterpriseId}`)
```

### 8.7 状态处理

- 画像加载中：区块内显示 loading，不影响基础详情。
- 雷达图数据为空：显示“暂无画像数据”。
- 相似企业为空：显示“暂无相似企业，可尝试增加数量或刷新画像”。
- 刷新失败且已有旧数据：保留旧数据并提示错误。
- 企业非 ACTIVE 或后端返回业务错误：画像区块显示错误说明，基础详情仍展示。
- 组件卸载时释放 ECharts 实例和 resize 监听。

## 9. 测试计划

### 9.1 后端 Service 测试

新增 `EnterpriseProfileAnalysisServiceTest`，覆盖：

- 正常企业返回 6 个雷达指标。
- 空字段企业指标为 0 且不报错。
- `enterpriseId` 为空时返回参数错误。
- `similarLimit` 为空时使用默认值 5。
- `similarLimit = 0`、`-1`、`21` 时返回参数错误。
- 当前企业不存在时返回 `NOT_FOUND`。
- 当前企业不是 `ACTIVE` 时返回业务错误。
- 相似企业只返回 ACTIVE 企业且排除自己。
- 候选企业列表为空时返回空相似企业列表。
- 候选企业含空画像字段时不影响其他候选。
- 缓存命中时不重复生成向量。
- 缓存写入失败时仍返回本次计算结果。
- 单个候选向量生成失败时跳过该候选。
- 当前企业向量生成失败时返回明确业务错误。

### 9.2 后端 Controller 测试

新增或扩展 `EnterpriseControllerTest`，覆盖：

- 不传 `similarLimit` 时使用默认 5。
- `similarLimit = 0`、`-1`、`21` 返回 400。
- 路径参数非法时返回统一错误。
- 未登录访问返回 401。
- 无 `enterprise:enterprise:query` 权限返回 403。
- 有权限访问返回统一成功响应。
- 预热接口空 request body。
- 预热接口空企业 ID 列表。
- 预热接口超大企业 ID 列表。
- 预热接口无权限访问。

### 9.3 Mapper / SQL 测试

覆盖：

- 候选企业分页查询。
- 相似缓存 upsert 与查询。
- 查询缓存时源企业 ID 为空或候选列表为空。
- upsert 时 `reasons` 为空允许写入。
- 同一 `source + target + model + hash` 重复写入时更新相似度和原因。
- 已逻辑删除缓存不参与命中。
- SQL 兼容 MySQL 5.7。

### 9.4 前端 API 测试

扩展 `enterprise.ts` API 测试：

- `fetchEnterpriseProfileAnalysis(id)` 默认传 `similarLimit=5`。
- `fetchEnterpriseProfileAnalysis(id, 10)` 正确传查询参数。
- mock 模式返回稳定结构。
- 后端错误透传给组件处理。

### 9.5 前端组件测试

新增 `EnterpriseProfilePanel.spec.ts`，覆盖：

- 加载时显示 loading。
- 雷达指标和相似企业列表渲染。
- `enterpriseId` 为空或无效时不请求接口，并显示空状态或错误提示。
- `similarLimit` 默认值为 5。
- 选择器只允许 `5 / 10 / 20`。
- 切换数量重新请求。
- 点击刷新重新请求。
- 快速连续点击刷新时不会并发发起多次请求。
- 接口返回 `metrics: []` 时显示“暂无画像数据”。
- 接口返回 `similarEnterprises: []` 时显示“暂无相似企业”。
- 接口失败且已有旧数据时保留旧数据并提示错误。
- 点击相似企业跳转 `/enterprises/{id}`。
- 路由参数变化时重新加载画像。
- 组件卸载时释放图表实例和 resize 监听。

### 9.6 企业详情页测试

扩展企业详情页相关测试：

- 画像区块被正确挂载。
- 路由 `enterpriseId` 变化后重新加载企业详情。
- 画像组件收到新 ID 后重新加载。

## 10. 验证命令

后端：

```bash
cd back
mvn test
mvn package
```

前端：

```bash
cd front
npm run test
npm run build
```

## 11. 实施顺序

1. 后端新增数据库迁移和相似缓存实体/Mapper。
2. 后端新增画像分析 VO、Service、指标计算器、相似企业服务。
3. 后端新增画像查询接口和向量预热接口。
4. 后端补充 Service、Controller、Mapper 测试。
5. 前端新增类型和 API 函数，补充 mock。
6. 前端新增 `EnterpriseProfilePanel.vue`。
7. 前端集成到 `EnterpriseDetail.vue`，处理路由参数变化。
8. 前端补充组件、API、详情页测试。
9. 执行后端与前端验证命令。

## 12. 风险与约束

- embedding 服务不可用会影响相似企业发现，但不应影响雷达图展示。
- 第一版不引入独立向量数据库，超大数据量下相似检索仍有性能上限；已通过分页批处理、向量缓存和相似结果缓存降低成本。
- 新增 `echarts` 会增加前端包体积，需要在构建后关注 chunk 体积。
- 企业字段质量会直接影响雷达图指标和相似企业效果，空字段应以明确空状态展示。
- 后端实现必须遵守 MySQL 5.7 兼容要求和项目分层规则。
