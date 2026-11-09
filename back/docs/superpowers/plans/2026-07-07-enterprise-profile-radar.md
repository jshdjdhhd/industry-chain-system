# 企业多维画像与相似企业发现 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在企业详情页新增企业多维画像雷达图、Top-N 相似企业推荐、刷新与数量选择，并在后端提供画像分析、相似度缓存和 ACTIVE 企业向量预热能力。

**架构：** 后端在 `groupproject-back-commit/back` 新增独立画像分析接口，保留企业详情接口的轻量职责；画像分析服务聚合雷达指标、embedding 相似度检索、缓存命中和向量预热。前端在 `groupproject-front/front` 的企业详情页挂载独立 `EnterpriseProfilePanel.vue`，组件自行加载画像状态，不阻塞基础详情。

**技术栈：** Spring Boot、MyBatis Plus、MyBatis XML、Flyway、JUnit 5、MockMvc、Vue 3、TypeScript、Element Plus、Vitest、ECharts 5。

---

## 范围与约束

- 后端只修改 `groupproject-back-commit/back`，不修改 `groupproject-back`。
- 前端只修改 `groupproject-front/front`。
- 数据库必须兼容 MySQL 5.7：不用窗口函数、递归 CTE、`JSON_TABLE`，用户输入只使用 `#{}`。
- Controller 返回 VO，不返回 Entity。
- 画像接口失败不能影响企业详情基础信息展示。
- 计划中的 git commit 步骤只作为人工检查点，AI 代理不得替用户执行 commit、push 或上传。

## 文件结构

### 后端

- 创建：`groupproject-back-commit/back/src/main/resources/db/migration/V22__enterprise_profile_analysis.sql`
  - 新增 `enterprise_similarity_cache` 表。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/entity/EnterpriseSimilarityCache.java`
  - 相似度缓存实体。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/EnterpriseProfileAnalysisMapper.java`
  - 查询当前企业画像、分页候选企业、标签文本、挂链统计。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/EnterpriseSimilarityCacheMapper.java`
  - 查询和 upsert 相似度缓存。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseProfileAnalysisRow.java`
  - 当前企业和候选企业通用画像投影。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseLinkStatRow.java`
  - 企业挂链节点数、最高置信度、人工挂链标记投影。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseSimilarityCacheRow.java`
  - 缓存查询投影。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/TargetHashParam.java`
  - 缓存批量命中查询参数。
- 创建：`groupproject-back-commit/back/src/main/resources/mapper/enterprise/EnterpriseProfileAnalysisMapper.xml`
  - MySQL 5.7 兼容查询 SQL。
- 创建：`groupproject-back-commit/back/src/main/resources/mapper/enterprise/EnterpriseSimilarityCacheMapper.xml`
  - 缓存命中和 upsert SQL。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/dto/VectorWarmupRequest.java`
  - 向量预热请求。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/EnterpriseProfileAnalysisVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/EnterpriseRadarVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/EnterpriseRadarMetricVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/SimilarEnterpriseVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/VectorWarmupResultVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/EnterpriseProfileAnalysisService.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/EnterpriseSimilarityService.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseProfileAnalysisServiceImpl.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseSimilarityServiceImpl.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseRadarMetricCalculator.java`
  - 固定 6 维雷达指标算法。
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseProfileTextFactory.java`
  - 把企业画像投影转成 embedding 输入文本，复用 `EmbeddingProfileTextBuilder` 的口径但不耦合匹配模块内部类。
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/controller/EnterpriseController.java`
  - 注入画像服务，新增查询和预热接口。
- 修改：`groupproject-back-commit/back/docs/API-INTERFACE-REGISTRY.md`
  - 记录新增接口、权限和返回结构。
- 修改：`groupproject-back-commit/back/src/test/resources/schema.sql`
  - 补齐测试库字段和新增缓存表。
- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/mapper/EnterpriseProfileAnalysisMapperTest.java`
- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/mapper/EnterpriseSimilarityCacheMapperTest.java`
- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/service/EnterpriseRadarMetricCalculatorTest.java`
- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/service/EnterpriseProfileAnalysisServiceTest.java`
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/controller/EnterpriseControllerTest.java`

### 前端

- 修改：`groupproject-front/front/package.json`
  - 增加 `echarts` 依赖。
- 修改：`groupproject-front/front/src/types/domain.ts`
  - 新增画像分析类型。
- 修改：`groupproject-front/front/src/api/enterprise.ts`
  - 新增 `fetchEnterpriseProfileAnalysis`。
- 修改：`groupproject-front/front/src/api/mock.ts`
  - 新增 mock 画像分析数据。
- 创建：`groupproject-front/front/src/views/enterprise/EnterpriseProfilePanel.vue`
  - 雷达图、相似企业列表、数量选择、刷新、状态处理。
- 修改：`groupproject-front/front/src/views/enterprise/EnterpriseDetail.vue`
  - 在已挂接节点和操作日志之间挂载画像组件，监听路由参数变化重新加载详情。
- 修改：`groupproject-front/front/src/api/enterprise.spec.ts`
- 创建：`groupproject-front/front/src/views/enterprise/EnterpriseProfilePanel.spec.ts`
- 创建：`groupproject-front/front/src/views/enterprise/EnterpriseDetail.spec.ts`

## 任务 1：数据库迁移与缓存数据访问

**文件：**
- 创建：`groupproject-back-commit/back/src/main/resources/db/migration/V22__enterprise_profile_analysis.sql`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/entity/EnterpriseSimilarityCache.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/EnterpriseSimilarityCacheMapper.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseSimilarityCacheRow.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/TargetHashParam.java`
- 创建：`groupproject-back-commit/back/src/main/resources/mapper/enterprise/EnterpriseSimilarityCacheMapper.xml`
- 修改：`groupproject-back-commit/back/src/test/resources/schema.sql`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/mapper/EnterpriseSimilarityCacheMapperTest.java`

- [ ] **步骤 1：编写缓存 Mapper 失败测试**

在 `EnterpriseSimilarityCacheMapperTest.java` 写入：

```java
package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.mapper.projection.EnterpriseSimilarityCacheRow;
import git.ggwzz.enterprise.mapper.projection.TargetHashParam;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class EnterpriseSimilarityCacheMapperTest {

    @Autowired
    private EnterpriseSimilarityCacheMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void upsertShouldInsertThenUpdateByPairModelAndHashes() {
        mapper.upsertSimilarity(1L, 2L, "local-qwen", "source-hash", "target-hash",
                new BigDecimal("0.81234"), "[\"所属行业相同：新能源汽车\"]");
        mapper.upsertSimilarity(1L, 2L, "local-qwen", "source-hash", "target-hash",
                new BigDecimal("0.92345"), "[\"主营产品关键词重合：电池\"]");

        List<EnterpriseSimilarityCacheRow> rows = mapper.selectMatchedCache(
                1L,
                "local-qwen",
                "source-hash",
                List.of(new TargetHashParam(2L, "target-hash")));

        assertEquals(1, rows.size());
        assertEquals(2L, rows.get(0).getTargetEnterpriseId());
        assertEquals(new BigDecimal("0.92345"), rows.get(0).getSimilarity());
        assertTrue(rows.get(0).getReasons().contains("电池"));
    }

    @Test
    void deletedCacheShouldNotMatch() {
        mapper.upsertSimilarity(1L, 2L, "local-qwen", "source-hash", "target-hash",
                new BigDecimal("0.81234"), null);
        jdbcTemplate.update("UPDATE enterprise_similarity_cache SET is_deleted = 1 WHERE source_enterprise_id = 1");

        List<EnterpriseSimilarityCacheRow> rows = mapper.selectMatchedCache(
                1L,
                "local-qwen",
                "source-hash",
                List.of(new TargetHashParam(2L, "target-hash")));

        assertTrue(rows.isEmpty());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseSimilarityCacheMapperTest test`

预期：FAIL，编译报错包含 `cannot find symbol class EnterpriseSimilarityCacheMapper`。

- [ ] **步骤 3：创建迁移 SQL**

在 `V22__enterprise_profile_analysis.sql` 写入设计规格中的建表语句，使用下面完整内容：

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

- [ ] **步骤 4：创建实体、投影、Mapper 与 XML**

`EnterpriseSimilarityCache.java`：

```java
package git.ggwzz.enterprise.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("enterprise_similarity_cache")
public class EnterpriseSimilarityCache {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceEnterpriseId;
    private Long targetEnterpriseId;
    private String modelName;
    private String sourceTextHash;
    private String targetTextHash;
    private BigDecimal similarity;
    private String reasons;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
```

`EnterpriseSimilarityCacheRow.java`：

```java
package git.ggwzz.enterprise.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnterpriseSimilarityCacheRow {
    private Long targetEnterpriseId;
    private BigDecimal similarity;
    private String reasons;
}
```

`EnterpriseSimilarityCacheMapper.java`：

```java
package git.ggwzz.enterprise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import git.ggwzz.enterprise.entity.EnterpriseSimilarityCache;
import git.ggwzz.enterprise.mapper.projection.EnterpriseSimilarityCacheRow;
import git.ggwzz.enterprise.mapper.projection.TargetHashParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface EnterpriseSimilarityCacheMapper extends BaseMapper<EnterpriseSimilarityCache> {
    List<EnterpriseSimilarityCacheRow> selectMatchedCache(
            @Param("sourceEnterpriseId") Long sourceEnterpriseId,
            @Param("modelName") String modelName,
            @Param("sourceTextHash") String sourceTextHash,
            @Param("targetHashes") List<TargetHashParam> targetHashes);

    int upsertSimilarity(@Param("sourceEnterpriseId") Long sourceEnterpriseId,
                         @Param("targetEnterpriseId") Long targetEnterpriseId,
                         @Param("modelName") String modelName,
                         @Param("sourceTextHash") String sourceTextHash,
                         @Param("targetTextHash") String targetTextHash,
                         @Param("similarity") BigDecimal similarity,
                         @Param("reasons") String reasons);
}
```

`EnterpriseSimilarityCacheMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="git.ggwzz.enterprise.mapper.EnterpriseSimilarityCacheMapper">
    <resultMap id="similarityCacheRowMap"
               type="git.ggwzz.enterprise.mapper.projection.EnterpriseSimilarityCacheRow">
        <result column="target_enterprise_id" property="targetEnterpriseId"/>
        <result column="similarity" property="similarity"/>
        <result column="reasons" property="reasons"/>
    </resultMap>

    <select id="selectMatchedCache" resultMap="similarityCacheRowMap">
        SELECT target_enterprise_id, similarity, reasons
        FROM enterprise_similarity_cache
        WHERE source_enterprise_id = #{sourceEnterpriseId}
          AND model_name = #{modelName}
          AND source_text_hash = #{sourceTextHash}
          AND is_deleted = 0
          AND (
          <foreach item="item" collection="targetHashes" separator=" OR ">
              (target_enterprise_id = #{item.targetEnterpriseId}
               AND target_text_hash = #{item.targetTextHash})
          </foreach>
          )
    </select>

    <insert id="upsertSimilarity">
        INSERT INTO enterprise_similarity_cache
            (source_enterprise_id, target_enterprise_id, model_name, source_text_hash,
             target_text_hash, similarity, reasons, is_deleted)
        VALUES
            (#{sourceEnterpriseId}, #{targetEnterpriseId}, #{modelName}, #{sourceTextHash},
             #{targetTextHash}, #{similarity}, #{reasons}, 0)
        ON DUPLICATE KEY UPDATE
            similarity = VALUES(similarity),
            reasons = VALUES(reasons),
            update_time = CURRENT_TIMESTAMP,
            is_deleted = 0
    </insert>
</mapper>
```

- [ ] **步骤 5：创建安全批量缓存查询参数**

创建 `TargetHashParam.java`，批量查询缓存时只使用 `#{}` 绑定参数：

```java
package git.ggwzz.enterprise.mapper.projection;

public record TargetHashParam(Long targetEnterpriseId, String targetTextHash) {
}
```

- [ ] **步骤 6：同步测试 schema**

在 `schema.sql` 的 `enterprise` 表中增加规格需要的字段：

```sql
legal_person VARCHAR(64),
address VARCHAR(255),
registered_capital VARCHAR(64),
establish_date DATE,
patent_info CLOB,
bid_info CLOB,
project_info CLOB,
contact_person VARCHAR(64),
contact_phone VARCHAR(32),
output_value DECIMAL(18,2),
tax_amount DECIMAL(18,2),
```

在 `schema.sql` 末尾增加 H2 兼容缓存表：

```sql
CREATE TABLE IF NOT EXISTS enterprise_similarity_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_enterprise_id BIGINT NOT NULL,
    target_enterprise_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    source_text_hash CHAR(64) NOT NULL,
    target_text_hash CHAR(64) NOT NULL,
    similarity DECIMAL(6,5) NOT NULL,
    reasons VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_enterprise_similarity_pair_model_hash
    ON enterprise_similarity_cache(source_enterprise_id, target_enterprise_id, model_name, source_text_hash, target_text_hash);
```

- [ ] **步骤 7：运行缓存 Mapper 测试验证通过**

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseSimilarityCacheMapperTest test`

预期：PASS。

- [ ] **步骤 8：人工 commit 检查点**

人工执行：

```bash
git add src/main/resources/db/migration/V22__enterprise_profile_analysis.sql src/main/resources/mapper/enterprise/EnterpriseSimilarityCacheMapper.xml src/main/java/git/ggwzz/enterprise/entity/EnterpriseSimilarityCache.java src/main/java/git/ggwzz/enterprise/mapper/EnterpriseSimilarityCacheMapper.java src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseSimilarityCacheRow.java src/main/java/git/ggwzz/enterprise/mapper/projection/TargetHashParam.java src/test/resources/schema.sql src/test/java/git/ggwzz/enterprise/mapper/EnterpriseSimilarityCacheMapperTest.java
git commit -m "feat: add enterprise similarity cache"
```

## 任务 2：画像查询 Mapper 与基础投影

**文件：**
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/EnterpriseProfileAnalysisMapper.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseProfileAnalysisRow.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseLinkStatRow.java`
- 创建：`groupproject-back-commit/back/src/main/resources/mapper/enterprise/EnterpriseProfileAnalysisMapper.xml`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/mapper/EnterpriseProfileAnalysisMapperTest.java`

- [ ] **步骤 1：编写画像查询 Mapper 失败测试**

测试覆盖：当前企业查询、ACTIVE 候选分页、排除自己、挂链统计、标签文本。

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseProfileAnalysisMapperTest test`

预期：FAIL，缺少 `EnterpriseProfileAnalysisMapper`。

- [ ] **步骤 2：创建投影和 Mapper 接口**

`EnterpriseProfileAnalysisRow.java`：

```java
package git.ggwzz.enterprise.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnterpriseProfileAnalysisRow {
    private Long id;
    private String enterpriseName;
    private String status;
    private String industry;
    private String businessScope;
    private String mainProducts;
    private String coreTech;
    private String keywords;
    private String patentInfo;
    private String bidInfo;
    private BigDecimal outputValue;
    private BigDecimal taxAmount;
}
```

`EnterpriseLinkStatRow.java`：

```java
package git.ggwzz.enterprise.mapper.projection;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class EnterpriseLinkStatRow {
    private Long enterpriseId;
    private Integer linkedNodeCount;
    private BigDecimal maxConfidence;
    private Boolean hasManualLink;
}
```

`EnterpriseProfileAnalysisMapper.java`：

```java
package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EnterpriseProfileAnalysisMapper {
    EnterpriseProfileAnalysisRow selectProfileById(@Param("id") Long id);

    List<EnterpriseProfileAnalysisRow> selectActiveCandidates(
            @Param("excludeEnterpriseId") Long excludeEnterpriseId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    List<String> selectTagTextsByEnterpriseId(@Param("enterpriseId") Long enterpriseId);

    List<EnterpriseLinkStatRow> selectLinkStatsByEnterpriseIds(
            @Param("enterpriseIds") List<Long> enterpriseIds);
}
```

- [ ] **步骤 3：创建 XML 查询**

`EnterpriseProfileAnalysisMapper.xml` 使用 3 表以内 JOIN，标签仍单独查：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="git.ggwzz.enterprise.mapper.EnterpriseProfileAnalysisMapper">
    <resultMap id="profileRowMap"
               type="git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow">
        <id column="id" property="id"/>
        <result column="enterprise_name" property="enterpriseName"/>
        <result column="status" property="status"/>
        <result column="industry" property="industry"/>
        <result column="business_scope" property="businessScope"/>
        <result column="main_products" property="mainProducts"/>
        <result column="core_tech" property="coreTech"/>
        <result column="keywords" property="keywords"/>
        <result column="patent_info" property="patentInfo"/>
        <result column="bid_info" property="bidInfo"/>
        <result column="output_value" property="outputValue"/>
        <result column="tax_amount" property="taxAmount"/>
    </resultMap>

    <resultMap id="linkStatRowMap"
               type="git.ggwzz.enterprise.mapper.projection.EnterpriseLinkStatRow">
        <id column="enterprise_id" property="enterpriseId"/>
        <result column="linked_node_count" property="linkedNodeCount"/>
        <result column="max_confidence" property="maxConfidence"/>
        <result column="has_manual_link" property="hasManualLink"/>
    </resultMap>

    <sql id="profileColumns">
        id, enterprise_name, status, industry, business_scope, main_products,
        core_tech, keywords, patent_info, bid_info, output_value, tax_amount
    </sql>

    <select id="selectProfileById" resultMap="profileRowMap">
        SELECT <include refid="profileColumns"/>
        FROM enterprise
        WHERE id = #{id}
          AND is_deleted = 0
        LIMIT 1
    </select>

    <select id="selectActiveCandidates" resultMap="profileRowMap">
        SELECT <include refid="profileColumns"/>
        FROM enterprise
        WHERE status = 'ACTIVE'
          AND is_deleted = 0
          AND id != #{excludeEnterpriseId}
        ORDER BY id ASC
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="selectTagTextsByEnterpriseId" resultType="java.lang.String">
        SELECT t.tag_name
        FROM enterprise_tag_rel r
        INNER JOIN tag t ON t.id = r.tag_id
        WHERE r.enterprise_id = #{enterpriseId}
          AND t.is_deleted = 0
        ORDER BY r.id
    </select>

    <select id="selectLinkStatsByEnterpriseIds" resultMap="linkStatRowMap">
        SELECT enterprise_id,
               COUNT(DISTINCT node_id) AS linked_node_count,
               MAX(confidence) AS max_confidence,
               MAX(CASE WHEN source = 'MANUAL' THEN 1 ELSE 0 END) AS has_manual_link
        FROM enterprise_node_rel
        WHERE is_deleted = 0
          AND enterprise_id IN
          <foreach item="id" collection="enterpriseIds" open="(" separator="," close=")">
              #{id}
          </foreach>
        GROUP BY enterprise_id
    </select>
</mapper>
```

- [ ] **步骤 4：运行 Mapper 测试验证通过**

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseProfileAnalysisMapperTest test`

预期：PASS。

- [ ] **步骤 5：人工 commit 检查点**

人工执行：

```bash
git add src/main/java/git/ggwzz/enterprise/mapper/EnterpriseProfileAnalysisMapper.java src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseProfileAnalysisRow.java src/main/java/git/ggwzz/enterprise/mapper/projection/EnterpriseLinkStatRow.java src/main/resources/mapper/enterprise/EnterpriseProfileAnalysisMapper.xml src/test/java/git/ggwzz/enterprise/mapper/EnterpriseProfileAnalysisMapperTest.java
git commit -m "feat: add enterprise profile analysis queries"
```

## 任务 3：雷达指标计算器与 VO

**文件：**
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/EnterpriseProfileAnalysisVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/EnterpriseRadarVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/EnterpriseRadarMetricVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/SimilarEnterpriseVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/VectorWarmupResultVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseRadarMetricCalculator.java`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/service/EnterpriseRadarMetricCalculatorTest.java`

- [ ] **步骤 1：编写雷达指标失败测试**

覆盖 6 个指标、空字段 0 分、条目数封顶、关键词去重、挂链置信度、产值税收。

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseRadarMetricCalculatorTest test`

预期：FAIL，缺少 `EnterpriseRadarMetricCalculator`。

- [ ] **步骤 2：创建 VO**

使用 Lombok `@Data`，字段名必须与规格 JSON 对齐：

```java
package git.ggwzz.enterprise.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class EnterpriseProfileAnalysisVO {
    private Long enterpriseId;
    private String enterpriseName;
    private EnterpriseRadarVO radar;
    private List<SimilarEnterpriseVO> similarEnterprises;
    private LocalDateTime generatedAt;
}
```

```java
package git.ggwzz.enterprise.vo;

import lombok.Data;

import java.util.List;

@Data
public class EnterpriseRadarVO {
    private List<EnterpriseRadarMetricVO> metrics;
}
```

```java
package git.ggwzz.enterprise.vo;

import lombok.Data;

@Data
public class EnterpriseRadarMetricVO {
    private String key;
    private String name;
    private Integer value;
    private String rawValue;
    private String description;
}
```

```java
package git.ggwzz.enterprise.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SimilarEnterpriseVO {
    private Long enterpriseId;
    private String enterpriseName;
    private BigDecimal similarity;
    private Integer similarityPercent;
    private String industry;
    private String mainProducts;
    private List<String> reasons;
}
```

```java
package git.ggwzz.enterprise.vo;

import lombok.Data;

@Data
public class VectorWarmupResultVO {
    private int totalCount;
    private int successCount;
    private int skippedCount;
    private int failureCount;
}
```

- [ ] **步骤 3：实现雷达指标计算器**

核心方法签名：

```java
public EnterpriseRadarVO calculate(EnterpriseProfileAnalysisRow row,
                                   List<String> tagTexts,
                                   EnterpriseLinkStatRow linkStat)
```

固定返回 key 顺序：

```java
List.of(
    metric("patentCount", "专利数量", scoreByCount(patentCount, 5), patentCount + " 条",
            "根据企业专利信息条目数计算，5 条及以上记满分"),
    metric("bidFrequency", "招投标频次", scoreByCount(bidCount, 5), bidCount + " 条",
            "根据企业招投标信息条目数计算，5 条及以上记满分"),
    metric("productTechRichness", "产品/技术丰富度", scoreByCount(keywordCount, 10), keywordCount + " 个关键词",
            "根据主营产品、核心技术、关键词和标签去重后计算，10 个及以上记满分"),
    metric("businessScopeBreadth", "经营范围广度", businessScopeScore, businessScopeRaw,
            "根据经营范围文本长度和分句数量综合计分"),
    metric("chainLinkage", "产业链关联度", linkageScore, linkageRaw,
            "根据有效挂接节点数、挂接来源和最高置信度计算"),
    metric("businessScale", "经营规模", scaleScore, scaleRaw,
            "根据产值和税收字段是否存在及数值规模计算")
)
```

计分规则在代码中固定：

- 条目指标：`Math.min(100, count * 20)`。
- 关键词丰富度：`Math.min(100, keywordCount * 10)`。
- 经营范围：空为 0；长度分 `min(60, length / 10)`；分句分 `min(40, segmentCount * 8)`。
- 产业链关联度：节点数分 `min(60, linkedNodeCount * 20)`；最高置信度分 `confidence * 30`；人工挂链额外 `10`，总分封顶 100。
- 经营规模：产值存在 `30`，税收存在 `30`；产值大于等于 1000 万加 `20`；税收大于等于 100 万加 `20`。

- [ ] **步骤 4：运行雷达测试验证通过**

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseRadarMetricCalculatorTest test`

预期：PASS。

- [ ] **步骤 5：人工 commit 检查点**

人工执行：

```bash
git add src/main/java/git/ggwzz/enterprise/vo/EnterpriseProfileAnalysisVO.java src/main/java/git/ggwzz/enterprise/vo/EnterpriseRadarVO.java src/main/java/git/ggwzz/enterprise/vo/EnterpriseRadarMetricVO.java src/main/java/git/ggwzz/enterprise/vo/SimilarEnterpriseVO.java src/main/java/git/ggwzz/enterprise/vo/VectorWarmupResultVO.java src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseRadarMetricCalculator.java src/test/java/git/ggwzz/enterprise/service/EnterpriseRadarMetricCalculatorTest.java
git commit -m "feat: add enterprise radar metrics"
```

## 任务 4：相似企业服务与向量预热

**文件：**
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/EnterpriseSimilarityService.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseSimilarityServiceImpl.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseProfileTextFactory.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/dto/VectorWarmupRequest.java`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/service/EnterpriseSimilarityServiceTest.java`

- [ ] **步骤 1：编写相似企业服务失败测试**

覆盖：只返回 ACTIVE、排除自己、limit 只允许 5/10/20、缓存命中不生成向量、候选向量失败跳过、当前企业向量失败抛业务异常、预热超过 500 返回参数错误。

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseSimilarityServiceTest test`

预期：FAIL，缺少 `EnterpriseSimilarityService`。

- [ ] **步骤 2：创建请求 DTO 和服务接口**

`VectorWarmupRequest.java`：

```java
package git.ggwzz.enterprise.dto;

import lombok.Data;

import java.util.List;

@Data
public class VectorWarmupRequest {
    private List<Long> enterpriseIds;
}
```

`EnterpriseSimilarityService.java`：

```java
package git.ggwzz.enterprise.service;

import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.mapper.projection.EnterpriseProfileAnalysisRow;
import git.ggwzz.enterprise.vo.SimilarEnterpriseVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;

import java.util.List;

public interface EnterpriseSimilarityService {
    List<SimilarEnterpriseVO> findSimilarEnterprises(EnterpriseProfileAnalysisRow source, int similarLimit);

    VectorWarmupResultVO warmupActiveEnterpriseVectors(VectorWarmupRequest request);
}
```

- [ ] **步骤 3：实现画像文本工厂**

`EnterpriseProfileTextFactory` 输出稳定文本，objectType 使用 `"ENTERPRISE_PROFILE"`：

```java
public String buildProfileText(EnterpriseProfileAnalysisRow row, List<String> tags) {
    List<String> lines = new ArrayList<>();
    lines.add("任务：企业画像相似度检索，重点关注所属行业、主营产品、核心技术、经营范围、关键词和企业标签。");
    addLine(lines, "企业名称", row.getEnterpriseName());
    addLine(lines, "所属行业", row.getIndustry());
    addLine(lines, "经营范围", row.getBusinessScope());
    addLine(lines, "主营产品", row.getMainProducts());
    addLine(lines, "核心技术", row.getCoreTech());
    addLine(lines, "关键词", row.getKeywords());
    addLine(lines, "企业标签", String.join("，", tags));
    return String.join("\n", lines);
}
```

- [ ] **步骤 4：实现 Top-N 检索**

实现要点：

- `similarLimit` 只允许 5、10、20，否则抛 `BusinessException(ResultCode.BAD_REQUEST, "相似企业数量只能是 5、10 或 20")`。
- 当前企业非 ACTIVE 抛 `BusinessException(ResultCode.BAD_REQUEST, "仅已入库企业支持画像分析")`。
- 每批候选数量常量 `CANDIDATE_BATCH_SIZE = 100`。
- 每批先构造候选文本和 hash，再调用 `EmbeddingVectorService.getBatchCachedVectors("ENTERPRISE_PROFILE", candidateIds, modelName, idToTextHash)`。
- 未命中缓存的候选使用 `getOrCreateVectorDirect("ENTERPRISE_PROFILE", candidateId, modelName, profileText)`，单个候选失败时记录日志并跳过。
- 使用 `VectorMath.cosineSimilarity(sourceVector, candidateVector)` 计算相似度。
- 缓存写入失败 catch `RuntimeException` 并记录日志，不影响本次返回。
- 内存中维护 `PriorityQueue<SimilarScore>`，只保留 Top-N。

- [ ] **步骤 5：实现相似原因**

原因生成固定规则：

```java
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
if (similarity.compareTo(new BigDecimal("0.85000")) >= 0) {
    reasons.add("经营范围语义相似度高");
}
```

- [ ] **步骤 6：实现向量预热**

预热规则：

- 请求体为 null 或 `enterpriseIds` 为 null/empty：分页预热全部 ACTIVE 企业。
- 指定 ID 数量大于 500：抛 `BusinessException(ResultCode.BAD_REQUEST, "单次最多预热 500 家企业")`。
- 指定 ID 时只处理 ACTIVE 且未删除企业，缺失或非 ACTIVE 计入 skipped。
- 每个企业生成文本后调用 `getOrCreateVectorDirect("ENTERPRISE_PROFILE", id, modelName, profileText)`。
- 返回 `totalCount/successCount/skippedCount/failureCount`。

- [ ] **步骤 7：运行相似企业服务测试验证通过**

运行：`cd groupproject-back-commit/back && mvn -Dtest=EnterpriseSimilarityServiceTest test`

预期：PASS。

- [ ] **步骤 8：人工 commit 检查点**

人工执行：

```bash
git add src/main/java/git/ggwzz/enterprise/dto/VectorWarmupRequest.java src/main/java/git/ggwzz/enterprise/service/EnterpriseSimilarityService.java src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseSimilarityServiceImpl.java src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseProfileTextFactory.java src/test/java/git/ggwzz/enterprise/service/EnterpriseSimilarityServiceTest.java
git commit -m "feat: add enterprise similarity service"
```

## 任务 5：画像分析聚合服务与 Controller 接口

**文件：**
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/EnterpriseProfileAnalysisService.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseProfileAnalysisServiceImpl.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/controller/EnterpriseController.java`
- 修改：`groupproject-back-commit/back/docs/API-INTERFACE-REGISTRY.md`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/service/EnterpriseProfileAnalysisServiceTest.java`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/controller/EnterpriseControllerTest.java`

- [ ] **步骤 1：编写聚合服务与 Controller 失败测试**

覆盖规格 9.1、9.2 中的参数、权限、非 ACTIVE、候选为空、embedding 失败降级。

运行：

```bash
cd groupproject-back-commit/back
mvn -Dtest=EnterpriseProfileAnalysisServiceTest,EnterpriseControllerTest test
```

预期：FAIL，缺少 `EnterpriseProfileAnalysisService` 或 Controller 新接口。

- [ ] **步骤 2：创建聚合服务接口**

```java
package git.ggwzz.enterprise.service;

import git.ggwzz.enterprise.dto.VectorWarmupRequest;
import git.ggwzz.enterprise.vo.EnterpriseProfileAnalysisVO;
import git.ggwzz.enterprise.vo.VectorWarmupResultVO;

public interface EnterpriseProfileAnalysisService {
    EnterpriseProfileAnalysisVO analyze(Long enterpriseId, Integer similarLimit);

    VectorWarmupResultVO warmupVectors(VectorWarmupRequest request);
}
```

- [ ] **步骤 3：实现聚合服务**

实现规则：

- `enterpriseId == null` 抛 `BusinessException(ResultCode.BAD_REQUEST, "企业ID不能为空")`。
- `similarLimit == null` 使用 5。
- limit 非 5/10/20 抛参数错误。
- `selectProfileById` 返回 null 抛 `NOT_FOUND`。
- 当前企业非 ACTIVE 抛 `BAD_REQUEST`。
- 雷达图由 `EnterpriseRadarMetricCalculator` 直接计算。
- 相似企业调用 `EnterpriseSimilarityService.findSimilarEnterprises`。
- 相似企业服务抛当前企业向量错误时记录错误，并返回雷达图和空 `similarEnterprises`，让前端区块仍可展示画像。

- [ ] **步骤 4：修改 Controller**

在 `EnterpriseController` 增加字段：

```java
private final EnterpriseProfileAnalysisService enterpriseProfileAnalysisService;
```

新增方法：

```java
@GetMapping("/{id}/profile-analysis")
@PreAuthorize("hasAuthority('enterprise:enterprise:query')")
public Result<EnterpriseProfileAnalysisVO> profileAnalysis(
        @PathVariable Long id,
        @RequestParam(required = false) Integer similarLimit) {
    return Result.success(enterpriseProfileAnalysisService.analyze(id, similarLimit));
}

@PostMapping("/profile-analysis/vector-warmup")
@PreAuthorize("hasAuthority('enterprise:enterprise:update')")
public Result<VectorWarmupResultVO> warmupProfileVectors(
        @RequestBody(required = false) VectorWarmupRequest request) {
    return Result.success(enterpriseProfileAnalysisService.warmupVectors(request));
}
```

- [ ] **步骤 5：同步 API 注册文档**

在 `API-INTERFACE-REGISTRY.md` 的企业模块加入：

```markdown
### GET /api/enterprises/{id}/profile-analysis

- 权限：`enterprise:enterprise:query`
- 查询参数：`similarLimit`，可选，允许 `5 / 10 / 20`，默认 `5`
- 返回：`EnterpriseProfileAnalysisVO`
- 说明：独立企业画像分析接口，返回 6 维雷达指标和 Top-N 相似企业，不影响基础企业详情接口。

### POST /api/enterprises/profile-analysis/vector-warmup

- 权限：`enterprise:enterprise:update`
- 请求体：`{ "enterpriseIds": [1, 2, 3] }`；请求体为空或列表为空时预热全部 ACTIVE 企业
- 限制：指定 ID 单次最多 500 家
- 返回：`VectorWarmupResultVO`
```

- [ ] **步骤 6：运行后端接口测试验证通过**

运行：

```bash
cd groupproject-back-commit/back
mvn -Dtest=EnterpriseProfileAnalysisServiceTest,EnterpriseControllerTest test
```

预期：PASS。

- [ ] **步骤 7：人工 commit 检查点**

人工执行：

```bash
git add src/main/java/git/ggwzz/enterprise/service/EnterpriseProfileAnalysisService.java src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseProfileAnalysisServiceImpl.java src/main/java/git/ggwzz/enterprise/controller/EnterpriseController.java docs/API-INTERFACE-REGISTRY.md src/test/java/git/ggwzz/enterprise/service/EnterpriseProfileAnalysisServiceTest.java src/test/java/git/ggwzz/enterprise/controller/EnterpriseControllerTest.java
git commit -m "feat: expose enterprise profile analysis api"
```

## 任务 6：前端类型、API、Mock 与依赖

**文件：**
- 修改：`groupproject-front/front/package.json`
- 修改：`groupproject-front/front/src/types/domain.ts`
- 修改：`groupproject-front/front/src/api/enterprise.ts`
- 修改：`groupproject-front/front/src/api/mock.ts`
- 测试：`groupproject-front/front/src/api/enterprise.spec.ts`

- [ ] **步骤 1：安装依赖并编写 API 失败测试**

人工运行：`cd groupproject-front/front && npm install echarts@^5`

在 `enterprise.spec.ts` 增加：

```ts
import { fetchEnterpriseProfileAnalysis } from "./enterprise";

it("requests profile analysis with default similarLimit", async () => {
  vi.mocked(get).mockResolvedValue({
    enterpriseId: 1,
    enterpriseName: "宁德时代",
    radar: { metrics: [] },
    similarEnterprises: [],
    generatedAt: "2026-07-07T10:00:00",
  });

  await fetchEnterpriseProfileAnalysis(1);

  expect(get).toHaveBeenCalledWith("/enterprises/1/profile-analysis", {
    params: { similarLimit: 5 },
  });
});

it("requests profile analysis with custom similarLimit", async () => {
  vi.mocked(get).mockResolvedValue({
    enterpriseId: 1,
    enterpriseName: "宁德时代",
    radar: { metrics: [] },
    similarEnterprises: [],
    generatedAt: "2026-07-07T10:00:00",
  });

  await fetchEnterpriseProfileAnalysis(1, 10);

  expect(get).toHaveBeenCalledWith("/enterprises/1/profile-analysis", {
    params: { similarLimit: 10 },
  });
});
```

运行：`cd groupproject-front/front && npm run test -- src/api/enterprise.spec.ts`

预期：FAIL，缺少导出函数。

- [ ] **步骤 2：新增类型**

在 `domain.ts` 增加：

```ts
export interface EnterpriseRadarMetric {
  key: string;
  name: string;
  value: number;
  rawValue: string;
  description: string;
}

export interface EnterpriseRadar {
  metrics: EnterpriseRadarMetric[];
}

export interface SimilarEnterprise {
  enterpriseId: number;
  enterpriseName: string;
  similarity: number;
  similarityPercent: number;
  industry?: string | null;
  mainProducts?: string | null;
  reasons: string[];
}

export interface EnterpriseProfileAnalysis {
  enterpriseId: number;
  enterpriseName: string;
  radar: EnterpriseRadar;
  similarEnterprises: SimilarEnterprise[];
  generatedAt: string;
}
```

- [ ] **步骤 3：新增 API 函数**

在 `enterprise.ts` 类型导入中加入 `EnterpriseProfileAnalysis`，并新增：

```ts
export function fetchEnterpriseProfileAnalysis(
  id: number,
  similarLimit = 5,
): Promise<EnterpriseProfileAnalysis> {
  if (isMockApiEnabled()) {
    return mockApi.fetchEnterpriseProfileAnalysis(id, similarLimit);
  }
  return get(`/enterprises/${id}/profile-analysis`, {
    params: { similarLimit },
  });
}
```

- [ ] **步骤 4：新增 Mock**

在 `mock.ts` 类型导入加入 `EnterpriseProfileAnalysis`，在 `mockApi` 中新增：

```ts
fetchEnterpriseProfileAnalysis(
  id: number,
  similarLimit = 5,
): Promise<EnterpriseProfileAnalysis> {
  const enterprise = enterprises.find((item) => item.id === id);
  if (!enterprise) return rejectMock("企业不存在");
  const similarEnterprises = enterprises
    .filter((item) => item.id !== id && item.status === "ACTIVE")
    .slice(0, similarLimit)
    .map((item, index) => ({
      enterpriseId: item.id,
      enterpriseName: item.name,
      similarity: Number((0.92 - index * 0.03).toFixed(4)),
      similarityPercent: Math.round((0.92 - index * 0.03) * 100),
      industry: item.industry,
      mainProducts: item.mainProducts,
      reasons: ["所属行业相同：" + (item.industry || "未填写")],
    }));
  return sleep({
    enterpriseId: enterprise.id,
    enterpriseName: enterprise.name,
    radar: {
      metrics: [
        { key: "patentCount", name: "专利数量", value: 60, rawValue: "3 条", description: "根据企业专利信息条目数计算，5 条及以上记满分" },
        { key: "bidFrequency", name: "招投标频次", value: 40, rawValue: "2 条", description: "根据企业招投标信息条目数计算，5 条及以上记满分" },
        { key: "productTechRichness", name: "产品/技术丰富度", value: 80, rawValue: "8 个关键词", description: "根据主营产品、核心技术、关键词和标签去重后计算" },
        { key: "businessScopeBreadth", name: "经营范围广度", value: 75, rawValue: "经营范围较完整", description: "根据经营范围文本长度和分句数量综合计分" },
        { key: "chainLinkage", name: "产业链关联度", value: 70, rawValue: "2 个挂接节点", description: "根据有效挂接节点数、挂接来源和最高置信度计算" },
        { key: "businessScale", name: "经营规模", value: 80, rawValue: "产值/税收数据完整", description: "根据产值和税收字段是否存在及数值规模计算" },
      ],
    },
    similarEnterprises,
    generatedAt: new Date().toISOString(),
  });
},
```

- [ ] **步骤 5：运行 API 测试验证通过**

运行：`cd groupproject-front/front && npm run test -- src/api/enterprise.spec.ts`

预期：PASS。

- [ ] **步骤 6：人工 commit 检查点**

人工执行：

```bash
git add package.json package-lock.json src/types/domain.ts src/api/enterprise.ts src/api/mock.ts src/api/enterprise.spec.ts
git commit -m "feat: add enterprise profile api client"
```

## 任务 7：企业画像前端组件

**文件：**
- 创建：`groupproject-front/front/src/views/enterprise/EnterpriseProfilePanel.vue`
- 创建：`groupproject-front/front/src/views/enterprise/EnterpriseProfilePanel.spec.ts`

- [ ] **步骤 1：编写组件失败测试**

测试覆盖 loading、指标渲染、空状态、limit 选择、刷新去重、跳转、失败保留旧数据、卸载 dispose。

运行：`cd groupproject-front/front && npm run test -- src/views/enterprise/EnterpriseProfilePanel.spec.ts`

预期：FAIL，组件文件不存在。

- [ ] **步骤 2：创建组件脚本**

脚本核心结构：

```vue
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import * as echarts from "echarts/core";
import { RadarChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { fetchEnterpriseProfileAnalysis } from "@/api/enterprise";
import type { EnterpriseProfileAnalysis, SimilarEnterprise } from "@/types/domain";

echarts.use([RadarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

const props = defineProps<{ enterpriseId: number }>();
const router = useRouter();
const loading = ref(false);
const refreshing = ref(false);
const error = ref("");
const similarLimit = ref<5 | 10 | 20>(5);
const data = ref<EnterpriseProfileAnalysis | null>(null);
const chartEl = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let requestId = 0;

const metrics = computed(() => data.value?.radar.metrics ?? []);
const similarEnterprises = computed(() => data.value?.similarEnterprises ?? []);

async function loadProfile(keepOld = false) {
  if (!props.enterpriseId || loading.value || refreshing.value) return;
  const currentRequest = ++requestId;
  error.value = "";
  if (keepOld) refreshing.value = true;
  else loading.value = true;
  try {
    const result = await fetchEnterpriseProfileAnalysis(props.enterpriseId, similarLimit.value);
    if (currentRequest !== requestId) return;
    data.value = result;
    await nextTick();
    renderChart();
  } catch (err) {
    if (currentRequest !== requestId) return;
    error.value = err instanceof Error ? err.message : "企业画像加载失败";
    if (keepOld && data.value) ElMessage.error(error.value);
  } finally {
    if (currentRequest === requestId) {
      loading.value = false;
      refreshing.value = false;
    }
  }
}

function renderChart() {
  if (!chartEl.value || metrics.value.length === 0) return;
  if (!chart) chart = echarts.init(chartEl.value);
  chart.setOption({
    tooltip: {},
    radar: {
      indicator: metrics.value.map((item) => ({ name: item.name, max: 100 })),
    },
    series: [{ type: "radar", data: [{ value: metrics.value.map((item) => item.value), name: "企业画像" }] }],
  });
}

function goEnterprise(row: SimilarEnterprise) {
  router.push(`/enterprises/${row.enterpriseId}`);
}

function handleResize() {
  chart?.resize();
}

watch(() => props.enterpriseId, () => loadProfile(false));
watch(similarLimit, () => loadProfile(true));

onMounted(() => {
  window.addEventListener("resize", handleResize);
  loadProfile(false);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chart?.dispose();
  chart = null;
});
</script>
```

- [ ] **步骤 3：创建组件模板和样式**

模板必须包含：

- 顶部标题 `企业多维画像`。
- `el-select` 选项 `5/10/20`。
- 带 `Refresh` 图标的刷新按钮。
- 左侧图表容器。
- 右侧相似企业列表。
- 空指标文案 `暂无画像数据`。
- 空相似企业文案 `暂无相似企业，可尝试增加数量或刷新画像`。

- [ ] **步骤 4：运行组件测试验证通过**

运行：`cd groupproject-front/front && npm run test -- src/views/enterprise/EnterpriseProfilePanel.spec.ts`

预期：PASS。

- [ ] **步骤 5：人工 commit 检查点**

人工执行：

```bash
git add src/views/enterprise/EnterpriseProfilePanel.vue src/views/enterprise/EnterpriseProfilePanel.spec.ts
git commit -m "feat: add enterprise profile panel"
```

## 任务 8：企业详情页集成与路由参数刷新

**文件：**
- 修改：`groupproject-front/front/src/views/enterprise/EnterpriseDetail.vue`
- 创建：`groupproject-front/front/src/views/enterprise/EnterpriseDetail.spec.ts`

- [ ] **步骤 1：编写详情页失败测试**

覆盖：

- 画像组件挂载在已挂接节点和操作日志之间。
- route params `id` 变化后重新调用 `fetchEnterprise`。
- 画像组件收到新的 `enterpriseId`。

运行：`cd groupproject-front/front && npm run test -- src/views/enterprise/EnterpriseDetail.spec.ts`

预期：FAIL，当前详情页没有画像组件和 route watch。

- [ ] **步骤 2：修改详情页脚本**

导入 `watch` 和画像组件：

```ts
import { computed, onMounted, ref, watch } from "vue";
import EnterpriseProfilePanel from "./EnterpriseProfilePanel.vue";
```

增加路由变化监听：

```ts
watch(enterpriseId, async (id, oldId) => {
  if (!Number.isFinite(id) || id === oldId) return;
  await loadDetail();
});
```

- [ ] **步骤 3：插入画像区块**

在“已挂接节点” section 后、“操作日志” section 前插入：

```vue
<EnterpriseProfilePanel
  v-if="enterprise"
  :enterprise-id="enterpriseId"
/>
```

- [ ] **步骤 4：运行详情页测试验证通过**

运行：`cd groupproject-front/front && npm run test -- src/views/enterprise/EnterpriseDetail.spec.ts`

预期：PASS。

- [ ] **步骤 5：人工 commit 检查点**

人工执行：

```bash
git add src/views/enterprise/EnterpriseDetail.vue src/views/enterprise/EnterpriseDetail.spec.ts
git commit -m "feat: show profile analysis on enterprise detail"
```

## 任务 9：全量验证与文档同步

**文件：**
- 修改：`groupproject-back-commit/back/docs/API-INTERFACE-REGISTRY.md`
- 修改：`groupproject-back-commit/back/src/test/resources/http/chain.http`

- [ ] **步骤 1：补充 HTTP 手工验证样例**

在 `chain.http` 增加：

```http
### 企业画像分析：默认 Top 5
GET {{baseUrl}}/api/enterprises/1/profile-analysis
Authorization: Bearer {{token}}

### 企业画像分析：Top 10
GET {{baseUrl}}/api/enterprises/1/profile-analysis?similarLimit=10
Authorization: Bearer {{token}}

### 企业画像向量预热：指定企业
POST {{baseUrl}}/api/enterprises/profile-analysis/vector-warmup
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "enterpriseIds": [1, 2, 3]
}
```

- [ ] **步骤 2：运行后端重点测试**

运行：

```bash
cd groupproject-back-commit/back
mvn -Dtest=EnterpriseProfileAnalysisMapperTest,EnterpriseSimilarityCacheMapperTest,EnterpriseRadarMetricCalculatorTest,EnterpriseSimilarityServiceTest,EnterpriseProfileAnalysisServiceTest,EnterpriseControllerTest test
```

预期：PASS。

- [ ] **步骤 3：运行后端全量验证**

运行：

```bash
cd groupproject-back-commit/back
mvn test
mvn package
```

预期：两个命令均 PASS。

- [ ] **步骤 4：运行前端重点测试**

运行：

```bash
cd groupproject-front/front
npm run test -- src/api/enterprise.spec.ts src/views/enterprise/EnterpriseProfilePanel.spec.ts src/views/enterprise/EnterpriseDetail.spec.ts
```

预期：PASS。

- [ ] **步骤 5：运行前端全量验证**

运行：

```bash
cd groupproject-front/front
npm run test
npm run build
```

预期：两个命令均 PASS。构建后检查 ECharts chunk 体积，若 Vite 输出 chunk 过大警告，记录警告但不因此改变第一版依赖方案。

- [ ] **步骤 6：人工最终提交检查点**

人工执行：

```bash
git add groupproject-back-commit/back/docs/API-INTERFACE-REGISTRY.md groupproject-back-commit/back/src/test/resources/http/chain.http
git commit -m "docs: document enterprise profile analysis api"
```

## 自检结果

- 规格覆盖：本计划覆盖画像查询接口、向量预热接口、6 维雷达指标、ACTIVE 候选范围、embedding 主排序、相似原因、分页批处理、批量向量缓存读取、相似结果缓存、前端 ECharts 雷达图、Top-N 数量选择、刷新、详情跳转、路由参数刷新、后端与前端测试。
- 占位符扫描：已按 writing-plans 红旗词检查计划正文，没有发现需要替换的空泛占位表达。
- 类型一致性：后端 VO 字段与前端 `EnterpriseProfileAnalysis` 类型对齐；接口路径统一为后端 `/api/enterprises/{id}/profile-analysis`，前端 axios 基础路径下调用 `/enterprises/{id}/profile-analysis`。
