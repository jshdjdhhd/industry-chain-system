# DashboardServiceImpl 全表扫描优化 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 `DashboardServiceImpl.getOverview()` 中 4 次全表加载到内存做聚合的操作，替换为 6 条数据库聚合查询，消除 O(N) 内存占用风险。

**架构：** 在 Mapper 层新增 6 个聚合方法（3 个 Mapper 各 1-2 个），Service 层重构为"查询聚合结果 → 纯 Java 拼装 VO"模式。不引入新依赖，不改变 API 接口。

**技术栈：** Spring Boot 3.5 + MyBatis-Plus 3.5 + H2（测试） + MySQL（生产）


---

## 文件结构

### 新增文件
| 文件 | 职责 |
|------|------|
| `src/main/java/git/ggwzz/dashboard/mapper/projection/EnterpriseCountRow.java` | 企业统计投影（total + linked） |
| `src/main/java/git/ggwzz/dashboard/mapper/projection/ChainEnterpriseCountRow.java` | 产业链企业分布投影 |
| `src/main/java/git/ggwzz/dashboard/mapper/projection/NodeSubtreeCountRow.java` | 根节点子树企业统计投影 |
| `src/main/java/git/ggwzz/dashboard/mapper/projection/MatchingDecisionRow.java` | 匹配决策汇总投影 |
| `src/main/java/git/ggwzz/dashboard/mapper/projection/DayRecommendationRow.java` | 本周每日推荐统计投影 |
| `src/test/java/git/ggwzz/dashboard/mapper/DashboardMapperTest.java` | Mapper 聚合方法集成测试 |
| `src/test/java/git/ggwzz/dashboard/service/DashboardServiceImplTest.java` | Service 重构后单元测试 |

### 修改文件
| 文件 | 变更 |
|------|------|
| `src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java` | +2 方法 |
| `src/main/resources/mapper/chain/EnterpriseNodeRelMapper.xml` | +2 条 SQL |
| `src/main/java/git/ggwzz/chain/mapper/ChainNodeMapper.java` | +1 方法 |
| `src/main/resources/mapper/chain/ChainNodeMapper.xml` | +1 条 SQL |
| `src/main/java/git/ggwzz/matching/mapper/MatchingResultMapper.java` | +2 方法 |
| `src/main/java/git/ggwzz/dashboard/service/impl/DashboardServiceImpl.java` | 重构全部私有方法 |

---

## 任务 1：创建投影类（5 个文件）

**文件：**
- 创建：`src/main/java/git/ggwzz/dashboard/mapper/projection/EnterpriseCountRow.java`
- 创建：`src/main/java/git/ggwzz/dashboard/mapper/projection/ChainEnterpriseCountRow.java`
- 创建：`src/main/java/git/ggwzz/dashboard/mapper/projection/NodeSubtreeCountRow.java`
- 创建：`src/main/java/git/ggwzz/dashboard/mapper/projection/MatchingDecisionRow.java`
- 创建：`src/main/java/git/ggwzz/dashboard/mapper/projection/DayRecommendationRow.java`

- [ ] **步骤 1：创建 5 个投影类**

这些是纯数据载体，MyBatis 通过 setter 注入聚合结果。

```java
// EnterpriseCountRow.java
package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

@Data
public class EnterpriseCountRow {
    private long total;
    private long linked;
}
```

```java
// ChainEnterpriseCountRow.java
package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

@Data
public class ChainEnterpriseCountRow {
    private Long chainId;
    private String chainName;
    private long enterpriseCount;
}
```

```java
// NodeSubtreeCountRow.java
package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

@Data
public class NodeSubtreeCountRow {
    private Long nodeId;
    private String nodeName;
    private long enterpriseCount;
}
```

```java
// MatchingDecisionRow.java
package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

@Data
public class MatchingDecisionRow {
    private String decision;
    private long cnt;
    private double avgConfidence;
}
```

```java
// DayRecommendationRow.java
package git.ggwzz.dashboard.mapper.projection;

import lombok.Data;

@Data
public class DayRecommendationRow {
    private int dayOfWeek;
    private int recommendations;
    private int accepted;
}
```

- [ ] **步骤 2：Commit**

```bash
git add src/main/java/git/ggwzz/dashboard/mapper/projection/
git commit -m "feat(dashboard): add projection classes for SQL aggregation"
```

---

## 任务 2：EnterpriseNodeRelMapper 新增 2 个聚合方法

**文件：**
- 修改：`src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java`
- 修改：`src/main/resources/mapper/chain/EnterpriseNodeRelMapper.xml`
- 创建：`src/test/java/git/ggwzz/dashboard/mapper/DashboardMapperTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
// DashboardMapperTest.java
package git.ggwzz.dashboard.mapper;

import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.dashboard.mapper.projection.EnterpriseCountRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class DashboardMapperTest {

    @Autowired
    private EnterpriseNodeRelMapper enterpriseNodeRelMapper;

    @Test
    void selectEnterpriseStatsShouldReturnTotalAndLinkedCounts() {
        // data.sql 中有 4 家企业（id 1,2,3,5），其中 3 家有挂链关系（id 1,2,3）
        // id=5 也有挂链（id=4），所以 total=4, linked=4
        EnterpriseCountRow stats = enterpriseNodeRelMapper.selectEnterpriseStats();
        assertEquals(4L, stats.getTotal());
        assertEquals(4L, stats.getLinked());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd back && mvn test -pl . -Dtest=DashboardMapperTest#selectEnterpriseStatsShouldReturnTotalAndLinkedCounts -DfailIfNoTests=false`
预期：编译失败，`selectEnterpriseStats()` 方法不存在

- [ ] **步骤 3：实现 Mapper 方法和 SQL**

在 `EnterpriseNodeRelMapper.java` 中添加（在文件末尾 `}` 之前）：

```java
/**
 * 企业统计汇总：活跃企业总数和已挂链企业数。
 *
 * @return 包含 total 和 linked 的投影行
 */
@Select("SELECT "
        + "(SELECT COUNT(*) FROM enterprise WHERE status = 'ACTIVE' AND is_deleted = 0) AS total, "
        + "COUNT(DISTINCT enterprise_id) AS linked "
        + "FROM enterprise_node_rel WHERE is_deleted = 0")
EnterpriseCountRow selectEnterpriseStats();

/**
 * 按产业链统计企业分布。
 *
 * @return 每条产业链的企业数列表
 */
List<ChainEnterpriseCountRow> selectEnterpriseCountByChain();
```

在文件顶部添加 import：
```java
import git.ggwzz.dashboard.mapper.projection.ChainEnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.EnterpriseCountRow;
```

在 `EnterpriseNodeRelMapper.xml` 的 `</mapper>` 之前添加：

```xml
<resultMap id="chainEnterpriseCountRowMap"
           type="git.ggwzz.dashboard.mapper.projection.ChainEnterpriseCountRow">
    <result column="chain_id" property="chainId"/>
    <result column="chain_name" property="chainName"/>
    <result column="enterprise_count" property="enterpriseCount"/>
</resultMap>

<select id="selectEnterpriseCountByChain"
        resultMap="chainEnterpriseCountRowMap">
    SELECT c.id AS chain_id,
           c.chain_name,
           COUNT(DISTINCT r.enterprise_id) AS enterprise_count
    FROM chain c
    LEFT JOIN chain_node n
           ON n.chain_id = c.id AND n.is_deleted = 0
    LEFT JOIN enterprise_node_rel r
           ON r.node_id = n.id AND r.is_deleted = 0
    LEFT JOIN enterprise e
           ON e.id = r.enterprise_id AND e.is_deleted = 0
    WHERE c.is_deleted = 0
    GROUP BY c.id, c.chain_name
</select>
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd back && mvn test -pl . -Dtest=DashboardMapperTest#selectEnterpriseStatsShouldReturnTotalAndLinkedCounts -DfailIfNoTests=false`
预期：PASS

- [ ] **步骤 5：补充 selectEnterpriseCountByChain 测试并运行**

在 `DashboardMapperTest.java` 中添加：

```java
@Test
void selectEnterpriseCountByChainShouldGroupByChain() {
    // data.sql 中有 2 条产业链，chain_id=1 有 3 家企业挂链，chain_id=2 有 1 家
    var rows = enterpriseNodeRelMapper.selectEnterpriseCountByChain();
    assertEquals(2, rows.size());
    // 验证 chain_id=1 的企业数
    var chain1 = rows.stream().filter(r -> r.getChainId() == 1L).findFirst();
    assertTrue(chain1.isPresent());
    assertEquals(3L, chain1.get().getEnterpriseCount());
    // 验证 chain_id=2 的企业数
    var chain2 = rows.stream().filter(r -> r.getChainId() == 2L).findFirst();
    assertTrue(chain2.isPresent());
    assertEquals(1L, chain2.get().getEnterpriseCount());
}
```

运行：`cd back && mvn test -pl . -Dtest=DashboardMapperTest -DfailIfNoTests=false`
预期：全部 PASS

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java \
        src/main/resources/mapper/chain/EnterpriseNodeRelMapper.xml \
        src/test/java/git/ggwzz/dashboard/mapper/DashboardMapperTest.java
git commit -m "feat(dashboard): add enterprise stats and chain distribution SQL aggregation"
```

---

## 任务 3：ChainNodeMapper 新增根节点子树统计

**文件：**
- 修改：`src/main/java/git/ggwzz/chain/mapper/ChainNodeMapper.java`
- 修改：`src/main/resources/mapper/chain/ChainNodeMapper.xml`

- [ ] **步骤 1：编写失败的测试**

在 `DashboardMapperTest.java` 中添加：

```java
@Autowired
private ChainNodeMapper chainNodeMapper;

@Test
void selectRootNodeSubtreeCountsShouldCountDescendantsByPathPrefix() {
    // data.sql: root node 100 (path=/100/) has children 101,102; root 103 has no children
    // root 201 has no children; root 202 has no children
    // enterprise links: node 100->none, 101->1, 102->1, 103->1, 201->1, 202->0
    // So root 100 subtree = enterprises at 101 + 102 = 2 distinct (enterprise 1 at 101, enterprise 2 at 102)
    // Root 103 subtree = 1 (enterprise 3 at 103)
    // Root 201 subtree = 1 (enterprise 5 at 201)
    // Root 202 subtree = 0
    var rows = chainNodeMapper.selectRootNodeSubtreeCounts();
    // 应返回所有未删除的根节点（parent_id IS NULL）
    var node100 = rows.stream().filter(r -> r.getNodeId() == 100L).findFirst();
    assertTrue(node100.isPresent());
    assertEquals(2L, node100.get().getEnterpriseCount());

    var node103 = rows.stream().filter(r -> r.getNodeId() == 103L).findFirst();
    assertTrue(node103.isPresent());
    assertEquals(1L, node103.get().getEnterpriseCount());
}
```

需要添加 import：
```java
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.dashboard.mapper.projection.NodeSubtreeCountRow;
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd back && mvn test -pl . -Dtest=DashboardMapperTest#selectRootNodeSubtreeCountsShouldCountDescendantsByPathPrefix -DfailIfNoTests=false`
预期：编译失败，`selectRootNodeSubtreeCounts()` 方法不存在

- [ ] **步骤 3：实现 Mapper 方法和 SQL**

在 `ChainNodeMapper.java` 中添加（在文件末尾 `}` 之前）：

```java
/**
 * 统计所有根节点（parent_id IS NULL）子树内的企业数。
 * 使用 node_path 前缀匹配代替递归查询。
 *
 * @return 根节点列表及其子树企业数
 */
List<NodeSubtreeCountRow> selectRootNodeSubtreeCounts();
```

添加 import：
```java
import git.ggwzz.dashboard.mapper.projection.NodeSubtreeCountRow;
```

在 `ChainNodeMapper.xml` 的 `</mapper>` 之前添加：

```xml
<resultMap id="nodeSubtreeCountRowMap"
           type="git.ggwzz.dashboard.mapper.projection.NodeSubtreeCountRow">
    <result column="node_id" property="nodeId"/>
    <result column="node_name" property="nodeName"/>
    <result column="enterprise_count" property="enterpriseCount"/>
</resultMap>

<select id="selectRootNodeSubtreeCounts"
        resultMap="nodeSubtreeCountRowMap">
    SELECT n_root.id AS node_id,
           n_root.node_name,
           COUNT(DISTINCT r.enterprise_id) AS enterprise_count
    FROM chain_node n_root
    LEFT JOIN chain_node n
           ON n.node_path LIKE CONCAT(n_root.node_path, n_root.id, '/%')
          AND n.chain_id = n_root.chain_id
          AND n.is_deleted = 0
    LEFT JOIN enterprise_node_rel r
           ON (r.node_id = n_root.id OR r.node_id = n.id)
          AND r.is_deleted = 0
    LEFT JOIN enterprise e
           ON e.id = r.enterprise_id
          AND e.is_deleted = 0
    WHERE n_root.parent_id IS NULL
      AND n_root.is_deleted = 0
    GROUP BY n_root.id, n_root.node_name
</select>
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd back && mvn test -pl . -Dtest=DashboardMapperTest#selectRootNodeSubtreeCountsShouldCountDescendantsByPathPrefix -DfailIfNoTests=false`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/git/ggwzz/chain/mapper/ChainNodeMapper.java \
        src/main/resources/mapper/chain/ChainNodeMapper.xml \
        src/test/java/git/ggwzz/dashboard/mapper/DashboardMapperTest.java
git commit -m "feat(dashboard): add root node subtree enterprise count SQL aggregation"
```

---

## 任务 4：MatchingResultMapper 新增 2 个聚合方法

**文件：**
- 修改：`src/main/java/git/ggwzz/matching/mapper/MatchingResultMapper.java`
- 创建：`src/main/resources/mapper/matching/MatchingResultMapper.xml`（新增 XML 文件）

- [ ] **步骤 1：编写失败的测试**

在 `DashboardMapperTest.java` 中添加：

```java
@Autowired
private MatchingResultMapper matchingResultMapper;

@Test
void selectDecisionSummaryShouldGroupByDecision() {
    // data.sql: 4 results - 2 ACCEPTED, 1 PENDING, 1 REJECTED
    var summary = matchingResultMapper.selectDecisionSummary();
    assertEquals(3, summary.size());
    assertEquals(2L, summary.get("ACCEPTED").getCnt());
    assertEquals(1L, summary.get("PENDING").getCnt());
    assertEquals(1L, summary.get("REJECTED").getCnt());
    // 平均置信度：ACCEPTED 的两条为 0.95 和 0.88，avg = 0.915
    assertEquals(0.915, summary.get("ACCEPTED").getAvgConfidence(), 0.01);
}

@Test
void selectWeeklyRecommendationsShouldGroupByDayOfWeek() {
    // data.sql 中 matching_result 的 created_at 是 DEFAULT CURRENT_TIMESTAMP
    // 即测试运行当天。DAYOFWEEK 取决于今天是星期几。
    // 只要不抛异常且返回 >= 1 条记录即可。
    java.time.LocalDateTime weekStart = java.time.LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay();
    var rows = matchingResultMapper.selectWeeklyRecommendations(weekStart);
    assertTrue(rows.size() >= 1);
    // recommendations 应该 >= 1（当天至少有 4 条记录）
    int totalRecommendations = rows.stream().mapToInt(DayRecommendationRow::getRecommendations).sum();
    assertTrue(totalRecommendations >= 1);
}
```

需要添加 import：
```java
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd back && mvn test -pl . -Dtest=DashboardMapperTest#selectDecisionSummaryShouldGroupByDecision -DfailIfNoTests=false`
预期：编译失败，`selectDecisionSummary()` 和 `selectWeeklyRecommendations()` 方法不存在

- [ ] **步骤 3：实现 Mapper 方法和 XML SQL**

在 `MatchingResultMapper.java` 中添加：

```java
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
```

在文件末尾 `}` 之前添加：

```java
/**
 * 匹配决策汇总：按 decision 分组统计数量和平均置信度。
 *
 * @return decision -> MatchingDecisionRow 映射
 */
@MapKey("decision")
Map<String, MatchingDecisionRow> selectDecisionSummary();

/**
 * 本周每日推荐和采纳统计。
 *
 * @param weekStart 本周一 00:00:00
 * @return 按 DAYOFWEEK 分组的每日统计
 */
List<DayRecommendationRow> selectWeeklyRecommendations(
        @Param("weekStart") LocalDateTime weekStart);
```

创建 `src/main/resources/mapper/matching/MatchingResultMapper.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="git.ggwzz.matching.mapper.MatchingResultMapper">

    <resultMap id="matchingDecisionRowMap"
               type="git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow">
        <result column="decision" property="decision"/>
        <result column="cnt" property="cnt"/>
        <result column="avg_confidence" property="avgConfidence"/>
    </resultMap>

    <resultMap id="dayRecommendationRowMap"
               type="git.ggwzz.dashboard.mapper.projection.DayRecommendationRow">
        <result column="day_of_week" property="dayOfWeek"/>
        <result column="recommendations" property="recommendations"/>
        <result column="accepted" property="accepted"/>
    </resultMap>

    <select id="selectDecisionSummary"
            resultMap="matchingDecisionRowMap">
        SELECT decision,
               COUNT(*) AS cnt,
               AVG(COALESCE(confidence, 0)) AS avg_confidence
        FROM matching_result
        GROUP BY decision
    </select>

    <select id="selectWeeklyRecommendations"
            resultMap="dayRecommendationRowMap">
        SELECT DAYOFWEEK(created_at) AS day_of_week,
               COUNT(*) AS recommendations,
               SUM(CASE WHEN decision = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted
        FROM matching_result
        WHERE created_at >= #{weekStart}
        GROUP BY DAYOFWEEK(created_at)
    </select>

</mapper>
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd back && mvn test -pl . -Dtest=DashboardMapperTest -DfailIfNoTests=false`
预期：全部 PASS

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/git/ggwzz/matching/mapper/MatchingResultMapper.java \
        src/main/resources/mapper/matching/MatchingResultMapper.xml \
        src/test/java/git/ggwzz/dashboard/mapper/DashboardMapperTest.java
git commit -m "feat(dashboard): add matching decision and weekly recommendation SQL aggregation"
```

---

## 任务 5：重构 DashboardServiceImpl

**文件：**
- 修改：`src/main/java/git/ggwzz/dashboard/service/impl/DashboardServiceImpl.java`
- 创建：`src/test/java/git/ggwzz/dashboard/service/DashboardServiceImplTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
// DashboardServiceImplTest.java
package git.ggwzz.dashboard.service;

import git.ggwzz.chain.mapper.ChainMapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.chain.mapper.projection.EnterpriseCountSummary;
import git.ggwzz.dashboard.mapper.projection.ChainEnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.EnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
import git.ggwzz.dashboard.mapper.projection.NodeSubtreeCountRow;
import git.ggwzz.dashboard.service.impl.DashboardServiceImpl;
import git.ggwzz.dashboard.vo.DashboardOverviewVO;
import git.ggwzz.enterprise.mapper.EnterpriseMapper;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.mapper.SysOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private EnterpriseMapper enterpriseMapper;
    @Mock private ChainMapper chainMapper;
    @Mock private ChainNodeMapper chainNodeMapper;
    @Mock private EnterpriseNodeRelMapper enterpriseNodeRelMapper;
    @Mock private MatchingResultMapper matchingResultMapper;
    @Mock private SysOperationLogMapper operationLogMapper;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void getOverviewShouldAssembleFromAggregationResults() {
        // 模拟聚合查询返回
        EnterpriseCountRow enterpriseStats = new EnterpriseCountRow();
        enterpriseStats.setTotal(100);
        enterpriseStats.setLinked(60);
        when(enterpriseNodeRelMapper.selectEnterpriseStats()).thenReturn(enterpriseStats);

        ChainEnterpriseCountRow chainRow = new ChainEnterpriseCountRow();
        chainRow.setChainId(1L);
        chainRow.setChainName("新能源汽车产业链");
        chainRow.setEnterpriseCount(30);
        when(enterpriseNodeRelMapper.selectEnterpriseCountByChain())
                .thenReturn(List.of(chainRow));

        NodeSubtreeCountRow nodeRow = new NodeSubtreeCountRow();
        nodeRow.setNodeId(100L);
        nodeRow.setNodeName("中游");
        nodeRow.setEnterpriseCount(15);
        when(chainNodeMapper.selectRootNodeSubtreeCounts())
                .thenReturn(List.of(nodeRow));

        MatchingDecisionRow accepted = new MatchingDecisionRow();
        accepted.setDecision("ACCEPTED");
        accepted.setCnt(20);
        accepted.setAvgConfidence(0.85);
        MatchingDecisionRow pending = new MatchingDecisionRow();
        pending.setDecision("PENDING");
        pending.setCnt(10);
        pending.setAvgConfidence(0.5);
        when(matchingResultMapper.selectDecisionSummary())
                .thenReturn(Map.of("ACCEPTED", accepted, "PENDING", pending));

        when(matchingResultMapper.selectWeeklyRecommendations(any()))
                .thenReturn(Collections.emptyList());

        when(operationLogMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

        // 执行
        DashboardOverviewVO result = dashboardService.getOverview();

        // 验证
        assertNotNull(result);
        assertNotNull(result.getMetrics());
        assertEquals(4, result.getMetrics().size());
        // 企业总数
        DashboardOverviewVO.Metric totalMetric = result.getMetrics().stream()
                .filter(m -> "企业总数".equals(m.getLabel())).findFirst().orElseThrow();
        assertEquals(100L, totalMetric.getValue());
        // 已入库企业 = linked = 60
        DashboardOverviewVO.Metric linkedMetric = result.getMetrics().stream()
                .filter(m -> "已入库企业".equals(m.getLabel())).findFirst().orElseThrow();
        assertEquals(60L, linkedMetric.getValue());
        // 产业链分布
        assertNotNull(result.getChainDistribution());
        assertEquals(1, result.getChainDistribution().size());
        assertEquals("新能源汽车产业链", result.getChainDistribution().get(0).getName());
        // 节点统计
        assertNotNull(result.getNodeStatistics());
        assertEquals(1, result.getNodeStatistics().size());
        // 匹配汇总
        assertNotNull(result.getMatchingSummary());
        assertEquals(10, result.getMatchingSummary().getPending());
        assertEquals(20, result.getMatchingSummary().getAccepted());
    }

    @Test
    void getOverviewShouldHandleEmptyDataGracefully() {
        when(enterpriseNodeRelMapper.selectEnterpriseStats()).thenReturn(new EnterpriseCountRow());
        when(enterpriseNodeRelMapper.selectEnterpriseCountByChain()).thenReturn(Collections.emptyList());
        when(chainNodeMapper.selectRootNodeSubtreeCounts()).thenReturn(Collections.emptyList());
        when(matchingResultMapper.selectDecisionSummary()).thenReturn(Collections.emptyMap());
        when(matchingResultMapper.selectWeeklyRecommendations(any())).thenReturn(Collections.emptyList());
        when(operationLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        DashboardOverviewVO result = dashboardService.getOverview();

        assertNotNull(result);
        assertEquals(4, result.getMetrics().size());
        assertTrue(result.getChainDistribution().isEmpty());
        assertTrue(result.getNodeStatistics().isEmpty());
        assertEquals(0, result.getMatchingSummary().getPending());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`cd back && mvn test -pl . -Dtest=DashboardServiceImplTest -DfailIfNoTests=false`
预期：测试失败，因为当前 `DashboardServiceImpl` 仍然使用旧的全表查询方式，mock 的新方法不会被调用

- [ ] **步骤 3：重构 DashboardServiceImpl**

用以下内容**完整替换** `DashboardServiceImpl.java`：

```java
package git.ggwzz.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.chain.mapper.ChainNodeMapper;
import git.ggwzz.chain.mapper.EnterpriseNodeRelMapper;
import git.ggwzz.dashboard.mapper.projection.ChainEnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.DayRecommendationRow;
import git.ggwzz.dashboard.mapper.projection.EnterpriseCountRow;
import git.ggwzz.dashboard.mapper.projection.MatchingDecisionRow;
import git.ggwzz.dashboard.mapper.projection.NodeSubtreeCountRow;
import git.ggwzz.dashboard.service.DashboardService;
import git.ggwzz.dashboard.vo.DashboardOverviewVO;
import git.ggwzz.matching.mapper.MatchingResultMapper;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String[] TONES = {"blue", "green", "amber", "purple"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EnterpriseNodeRelMapper enterpriseNodeRelMapper;
    private final ChainNodeMapper chainNodeMapper;
    private final MatchingResultMapper matchingResultMapper;
    private final SysOperationLogMapper operationLogMapper;

    @Override
    public DashboardOverviewVO getOverview() {
        // 6 个数据库聚合查询，替代原来的全表加载 + Java 聚合
        EnterpriseCountRow enterpriseStats = enterpriseNodeRelMapper.selectEnterpriseStats();
        List<ChainEnterpriseCountRow> chainDistribution = enterpriseNodeRelMapper.selectEnterpriseCountByChain();
        List<NodeSubtreeCountRow> nodeSubtreeCounts = chainNodeMapper.selectRootNodeSubtreeCounts();
        Map<String, MatchingDecisionRow> decisionSummary = matchingResultMapper.selectDecisionSummary();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<DayRecommendationRow> weekRecommendations =
                matchingResultMapper.selectWeeklyRecommendations(weekStart.atStartOfDay());

        List<SysOperationLog> recentLogs = operationLogMapper.selectList(
                new LambdaQueryWrapper<SysOperationLog>()
                        .orderByDesc(SysOperationLog::getId)
                        .last("LIMIT 10"));

        // 纯 Java 拼装 VO，无数据库交互
        DashboardOverviewVO vo = new DashboardOverviewVO();
        vo.setMetrics(buildMetrics(enterpriseStats, decisionSummary));
        vo.setChainDistribution(buildChainDistribution(chainDistribution, enterpriseStats.getTotal()));
        vo.setNodeStatistics(buildNodeStatistics(nodeSubtreeCounts));
        vo.setMatchingSummary(buildMatchingSummary(decisionSummary, weekRecommendations));
        vo.setWeeklyProgress(buildWeeklyProgress(weekStart, weekRecommendations));
        vo.setRecentOperations(buildRecentOperations(recentLogs));
        return vo;
    }

    private List<DashboardOverviewVO.Metric> buildMetrics(
            EnterpriseCountRow stats, Map<String, MatchingDecisionRow> decisionSummary) {
        long total = stats.getTotal();
        long linked = stats.getLinked();
        int pending = (int) decisionSummary.getOrDefault("PENDING", new MatchingDecisionRow()).getCnt();

        List<DashboardOverviewVO.Metric> metrics = new ArrayList<>();
        metrics.add(createMetric("企业总数", total, "家", "blue"));
        metrics.add(createMetric("已入库企业", linked, "家", "green"));
        metrics.add(createMetric("待智能挂链", pending, "家", "amber"));

        DashboardOverviewVO.Metric adoptionMetric = new DashboardOverviewVO.Metric();
        adoptionMetric.setLabel("推荐采纳率");
        long totalResults = decisionSummary.values().stream().mapToLong(MatchingDecisionRow::getCnt).sum();
        long accepted = decisionSummary.getOrDefault("ACCEPTED", new MatchingDecisionRow()).getCnt();
        adoptionMetric.setValue(totalResults > 0 ? Math.round(accepted * 100.0 / totalResults) + "%" : "0%");
        adoptionMetric.setTone("purple");
        metrics.add(adoptionMetric);

        return metrics;
    }

    private DashboardOverviewVO.Metric createMetric(String label, long value, String unit, String tone) {
        DashboardOverviewVO.Metric m = new DashboardOverviewVO.Metric();
        m.setLabel(label);
        m.setValue(value);
        m.setUnit(unit);
        m.setTone(tone);
        return m;
    }

    private List<DashboardOverviewVO.ChainDistribution> buildChainDistribution(
            List<ChainEnterpriseCountRow> rows, long totalEnterprises) {
        if (rows.isEmpty()) return Collections.emptyList();

        long linkedTotal = rows.stream().mapToLong(ChainEnterpriseCountRow::getEnterpriseCount).sum();
        long unlinked = totalEnterprises - linkedTotal;

        List<DashboardOverviewVO.ChainDistribution> result = new ArrayList<>();
        for (ChainEnterpriseCountRow row : rows) {
            if (row.getEnterpriseCount() > 0) {
                DashboardOverviewVO.ChainDistribution dist = new DashboardOverviewVO.ChainDistribution();
                dist.setName(row.getChainName());
                dist.setValue((int) row.getEnterpriseCount());
                result.add(dist);
            }
        }

        if (unlinked > 0) {
            DashboardOverviewVO.ChainDistribution dist = new DashboardOverviewVO.ChainDistribution();
            dist.setName("未挂接");
            dist.setValue((int) unlinked);
            result.add(dist);
        }

        long total = result.stream().mapToLong(DashboardOverviewVO.ChainDistribution::getValue).sum();
        for (DashboardOverviewVO.ChainDistribution dist : result) {
            double pct = total > 0 ? (dist.getValue() * 100.0 / total) : 0;
            dist.setPercent(String.format("%.0f%%", pct));
        }

        return result;
    }

    private List<DashboardOverviewVO.NodeStatistic> buildNodeStatistics(List<NodeSubtreeCountRow> rows) {
        String[] colors = {"#2F6BFF", "#00A896", "#F59E0B", "#7C3AED"};
        List<DashboardOverviewVO.NodeStatistic> result = new ArrayList<>();
        int index = 0;
        for (NodeSubtreeCountRow row : rows) {
            if (row.getEnterpriseCount() > 0) {
                DashboardOverviewVO.NodeStatistic stat = new DashboardOverviewVO.NodeStatistic();
                stat.setName(row.getNodeName());
                stat.setValue((int) row.getEnterpriseCount());
                stat.setTone(colors[index % colors.length]);
                result.add(stat);
                index++;
            }
        }
        return result;
    }

    private DashboardOverviewVO.MatchingSummary buildMatchingSummary(
            Map<String, MatchingDecisionRow> decisionSummary,
            List<DayRecommendationRow> weekRecommendations) {
        int pending = (int) decisionSummary.getOrDefault("PENDING", new MatchingDecisionRow()).getCnt();
        int accepted = (int) decisionSummary.getOrDefault("ACCEPTED", new MatchingDecisionRow()).getCnt();
        int rejected = (int) decisionSummary.getOrDefault("REJECTED", new MatchingDecisionRow()).getCnt();

        long manual = enterpriseNodeRelMapper.selectCount(
                new LambdaQueryWrapper<git.ggwzz.chain.entity.EnterpriseNodeRel>()
                        .eq(git.ggwzz.chain.entity.EnterpriseNodeRel::getSource, "MANUAL")
                        .eq(git.ggwzz.chain.entity.EnterpriseNodeRel::getDeleted, 0));

        long totalResults = decisionSummary.values().stream().mapToLong(MatchingDecisionRow::getCnt).sum();
        double avgConfidence = 0;
        if (totalResults > 0) {
            double weightedSum = decisionSummary.values().stream()
                    .mapToDouble(r -> r.getAvgConfidence() * r.getCnt())
                    .sum();
            avgConfidence = weightedSum / totalResults;
        }

        int weekly = weekRecommendations.stream().mapToInt(DayRecommendationRow::getRecommendations).sum();

        DashboardOverviewVO.MatchingSummary summary = new DashboardOverviewVO.MatchingSummary();
        summary.setPending(pending);
        summary.setAccepted(accepted);
        summary.setRejected(rejected);
        summary.setManual((int) manual);
        summary.setAverageConfidence(avgConfidence);
        summary.setWeeklyRecommendations(weekly);
        return summary;
    }

    private List<DashboardOverviewVO.WeeklyProgress> buildWeeklyProgress(
            LocalDate weekStart, List<DayRecommendationRow> weekRecommendations) {
        // DAYOFWEEK: 1=Sunday, 2=Monday, ..., 7=Saturday
        // Java DayOfWeek: 1=Monday, ..., 7=Sunday
        Map<Integer, DayRecommendationRow> byDay = weekRecommendations.stream()
                .collect(Collectors.toMap(DayRecommendationRow::getDayOfWeek, r -> r));

        List<DashboardOverviewVO.WeeklyProgress> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            // Java Monday=1 → DAYOFWEEK Monday=2, Sunday=1
            int dayOfWeekSql = date.getDayOfWeek().getValue() % 7 + 1;

            DashboardOverviewVO.WeeklyProgress wp = new DashboardOverviewVO.WeeklyProgress();
            wp.setDate(date.format(DATE_FMT));
            DayRecommendationRow dayRow = byDay.get(dayOfWeekSql);
            wp.setInbound(0);
            wp.setNodes(0);
            wp.setRecommendations(dayRow != null ? dayRow.getRecommendations() : 0);
            wp.setAccepted(dayRow != null ? dayRow.getAccepted() : 0);
            result.add(wp);
        }
        return result;
    }

    private List<DashboardOverviewVO.RecentOperation> buildRecentOperations(List<SysOperationLog> logs) {
        return logs.stream().map(log -> {
            DashboardOverviewVO.RecentOperation op = new DashboardOverviewVO.RecentOperation();
            op.setId(log.getId());
            op.setOperator(log.getOperator());
            op.setAction(log.getAction());
            op.setTargetType(log.getTargetType());
            op.setTargetId(log.getTargetId());
            op.setCreatedAt(log.getCreatedAt() != null
                    ? log.getCreatedAt().format(DATETIME_FMT) : "");
            op.setTargetName(log.getTargetType());
            String detail = log.getDetail();
            if (detail != null && detail.contains("\"result\":\"FAIL\"")) {
                op.setResult("失败");
            } else {
                op.setResult("成功");
            }
            return op;
        }).collect(Collectors.toList());
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd back && mvn test -pl . -Dtest=DashboardServiceImplTest -DfailIfNoTests=false`
预期：全部 PASS

- [ ] **步骤 5：运行全量测试确认无回归**

运行：`cd back && mvn test`
预期：全部 PASS（包括已有的 DashboardMapperTest、ChainServiceTest 等）

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/git/ggwzz/dashboard/service/impl/DashboardServiceImpl.java \
        src/test/java/git/ggwzz/dashboard/service/DashboardServiceImplTest.java
git commit -m "refactor(dashboard): replace full-table scans with SQL aggregation queries"
```

---

## 验证清单

完成所有任务后，确认以下事项：

- [ ] `mvn test` 全部通过
- [ ] `DashboardMapperTest` 覆盖了所有 6 个新 Mapper 方法
- [ ] `DashboardServiceImplTest` 覆盖了正常数据和空数据两种场景
- [ ] API 响应结构（`DashboardOverviewVO`）未改变
- [ ] Controller 层未做任何修改
- [ ] 无全表 `selectList(null)` 调用残留
