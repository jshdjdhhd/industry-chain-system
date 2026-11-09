# 子树企业统计口径统一修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 统一节点详情、节点树和产业链总数的统计口径，使其只计算去重后的有效企业。

**架构：** 保留现有接口契约，把直接数、节点子树数和链级总数分别交给批量聚合 SQL 计算。`ChainServiceImpl` 只装配树结构和统计结果，不再递归累加直接数来推导子树数。

**技术栈：** JDK 21、Spring Boot 3.5.15、MyBatis-Plus 3.5.7、MyBatis XML、MySQL 5.7、JUnit 5、Mockito、H2、Maven。

**设计规格：** `docs/superpowers/specs/2026-07-03-subtree-enterprise-count-statistics-design.md`

**执行约束：** 所有修改仅在 `back` 中进行。不得执行 `git add`、`git commit`、`git push` 或其他 Git 上传操作。

---

## 文件结构

- 修改 `back/src/test/java/git/ggwzz/chain/mapper/ChainPerformanceMapperTest.java`
  - 覆盖重复挂接、已删除企业、已解绑关系和空节点。
- 修改 `back/src/main/resources/mapper/chain/EnterpriseNodeRelMapper.xml`
  - 统一直接数和节点详情统计，新增节点子树批量统计。
- 修改 `back/src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java`
  - 声明新查询并统一 JavaDoc。
- 创建 `back/src/main/java/git/ggwzz/chain/mapper/projection/NodeSubtreeEnterpriseCountRow.java`
  - 承载节点 ID 和子树去重有效企业数。
- 修改 `back/src/main/java/git/ggwzz/chain/mapper/projection/NodeEnterpriseCountRow.java`
- 修改 `back/src/main/java/git/ggwzz/chain/mapper/projection/EnterpriseCountSummary.java`
  - 将“关系数”注释统一为“去重有效企业数”。
- 修改 `back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
  - 锁定树接口消费直接数、子树数和链级总数的方式。
- 修改 `back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
  - 使用三个独立聚合结果组装树。

不修改 Controller、VO 字段、前端、数据库迁移、仪表盘统计 SQL、
`API-INTERFACE-REGISTRY.md` 和 `chain.http`。

## 任务 1：先复现现有 Mapper 的错误口径

**文件：**
- 修改：`back/src/test/java/git/ggwzz/chain/mapper/ChainPerformanceMapperTest.java:83-119`

- [ ] **步骤 1：扩展直接数测试**

将现有直接数测试替换为：

```java
@Test
void selectDirectCountsByChainIdShouldCountDistinctActiveEnterprises() {
    jdbcTemplate.update(
            "UPDATE enterprise_node_rel SET is_deleted = 1 WHERE id = 3");
    jdbcTemplate.update(
            "INSERT INTO enterprise_node_rel "
                    + "(enterprise_id, node_id, source, reason, is_deleted) "
                    + "VALUES (1, 102, 'MANUAL', '有效企业回归', 0)");
    jdbcTemplate.update(
            "INSERT INTO enterprise (id, enterprise_name, status, is_deleted) "
                    + "VALUES (6, '已删除企业', 'ACTIVE', 1)");
    jdbcTemplate.update(
            "INSERT INTO enterprise_node_rel "
                    + "(enterprise_id, node_id, source, reason, is_deleted) "
                    + "VALUES (6, 102, 'MANUAL', '删除企业回归', 0)");

    Map<Long, Long> counts = enterpriseNodeRelMapper
            .selectDirectCountsByChainId(1L)
            .stream()
            .collect(Collectors.toMap(
                    NodeEnterpriseCountRow::getNodeId,
                    NodeEnterpriseCountRow::getDirectEnterpriseCount));

    assertEquals(2, counts.size());
    assertEquals(2L, counts.get(102L));
    assertEquals(1L, counts.get(103L));
    assertFalse(counts.containsKey(101L));
    assertFalse(counts.containsKey(201L));
}
```

- [ ] **步骤 2：扩展节点详情测试**

将现有节点详情测试替换为：

```java
@Test
void selectEnterpriseCountSummaryShouldCountDistinctActiveEnterprises() {
    jdbcTemplate.update(
            "INSERT INTO enterprise_node_rel "
                    + "(enterprise_id, node_id, source, reason, is_deleted) "
                    + "VALUES (1, 100, 'MANUAL', '祖孙节点重复企业', 0)");
    jdbcTemplate.update(
            "INSERT INTO enterprise (id, enterprise_name, status, is_deleted) "
                    + "VALUES (6, '已删除企业', 'ACTIVE', 1)");
    jdbcTemplate.update(
            "INSERT INTO enterprise_node_rel "
                    + "(enterprise_id, node_id, source, reason, is_deleted) "
                    + "VALUES (6, 102, 'MANUAL', '删除企业回归', 0)");

    EnterpriseCountSummary summary =
            enterpriseNodeRelMapper.selectEnterpriseCountSummary(
                    1L, 100L, "/100/%");

    assertEquals(1L, summary.getDirectEnterpriseCount());
    assertEquals(2L, summary.getTotalEnterpriseCount());
}
```

- [ ] **步骤 3：运行测试确认红灯**

```powershell
mvn "-Dtest=ChainPerformanceMapperTest#selectDirectCountsByChainIdShouldCountDistinctActiveEnterprises+selectEnterpriseCountSummaryShouldCountDistinctActiveEnterprises" test
```

预期：`BUILD FAILURE`。当前实现分别返回节点 `102 = 3` 和
`totalEnterpriseCount = 4`。

## 任务 2：定义节点子树批量统计契约

**文件：**
- 创建：`back/src/main/java/git/ggwzz/chain/mapper/projection/NodeSubtreeEnterpriseCountRow.java`
- 修改：`back/src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java:63-83`
- 修改：`back/src/main/resources/mapper/chain/EnterpriseNodeRelMapper.xml:6-26`
- 修改：`back/src/test/java/git/ggwzz/chain/mapper/ChainPerformanceMapperTest.java`

- [ ] **步骤 1：创建投影**

```java
package git.ggwzz.chain.mapper.projection;

import lombok.Data;

/**
 * 节点子树去重有效企业数投影。
 */
@Data
public class NodeSubtreeEnterpriseCountRow {

    /** 节点 ID */
    private Long nodeId;

    /** 当前节点及全部后代节点中的去重有效企业数 */
    private Long subtreeEnterpriseCount;
}
```

- [ ] **步骤 2：声明 Mapper 方法**

在 `EnterpriseNodeRelMapper.java` 导入投影，并在直接数查询后增加：

```java
/**
 * 按节点统计产业链下的子树去重有效企业数。
 *
 * @param chainId 产业链 ID
 * @return 产业链内所有有效节点及其子树去重有效企业数
 */
List<NodeSubtreeEnterpriseCountRow> selectSubtreeCountsByChainId(
        @Param("chainId") Long chainId);
```

- [ ] **步骤 3：增加 XML 结果映射**

```xml
<resultMap id="nodeSubtreeEnterpriseCountRowMap"
           type="git.ggwzz.chain.mapper.projection.NodeSubtreeEnterpriseCountRow">
    <id column="node_id" property="nodeId"/>
    <result column="subtree_enterprise_count"
            property="subtreeEnterpriseCount"/>
</resultMap>
```

- [ ] **步骤 4：增加重复企业子树测试**

导入 `NodeSubtreeEnterpriseCountRow`，增加：

```java
@Test
void selectSubtreeCountsByChainIdShouldDeduplicateActiveEnterprises() {
    jdbcTemplate.update(
            "INSERT INTO enterprise_node_rel "
                    + "(enterprise_id, node_id, source, reason, is_deleted) "
                    + "VALUES (1, 100, 'MANUAL', '祖孙节点重复企业', 0)");
    jdbcTemplate.update(
            "INSERT INTO enterprise_node_rel "
                    + "(enterprise_id, node_id, source, reason, is_deleted) "
                    + "VALUES (2, 101, 'MANUAL', '兄弟节点重复企业', 0)");
    jdbcTemplate.update(
            "INSERT INTO enterprise (id, enterprise_name, status, is_deleted) "
                    + "VALUES (6, '已删除企业', 'ACTIVE', 1)");
    jdbcTemplate.update(
            "INSERT INTO enterprise_node_rel "
                    + "(enterprise_id, node_id, source, reason, is_deleted) "
                    + "VALUES (6, 102, 'MANUAL', '删除企业回归', 0)");

    Map<Long, Long> counts = enterpriseNodeRelMapper
            .selectSubtreeCountsByChainId(1L)
            .stream()
            .collect(Collectors.toMap(
                    NodeSubtreeEnterpriseCountRow::getNodeId,
                    NodeSubtreeEnterpriseCountRow::getSubtreeEnterpriseCount));

    assertEquals(4, counts.size());
    assertEquals(2L, counts.get(100L));
    assertEquals(2L, counts.get(101L));
    assertEquals(1L, counts.get(102L));
    assertEquals(1L, counts.get(103L));
}
```

- [ ] **步骤 5：增加空节点返回零测试**

```java
@Test
void selectSubtreeCountsByChainIdShouldReturnZeroForEmptyNode() {
    Map<Long, Long> counts = enterpriseNodeRelMapper
            .selectSubtreeCountsByChainId(2L)
            .stream()
            .collect(Collectors.toMap(
                    NodeSubtreeEnterpriseCountRow::getNodeId,
                    NodeSubtreeEnterpriseCountRow::getSubtreeEnterpriseCount));

    assertEquals(2, counts.size());
    assertEquals(1L, counts.get(201L));
    assertEquals(0L, counts.get(202L));
}
```

- [ ] **步骤 6：运行新测试确认缺少 SQL**

```powershell
mvn "-Dtest=ChainPerformanceMapperTest#selectSubtreeCountsByChainIdShouldDeduplicateActiveEnterprises+selectSubtreeCountsByChainIdShouldReturnZeroForEmptyNode" test
```

预期：`BUILD FAILURE`，MyBatis 报告：

```text
Invalid bound statement: git.ggwzz.chain.mapper.EnterpriseNodeRelMapper.selectSubtreeCountsByChainId
```

## 任务 3：统一有效企业统计 SQL

**文件：**
- 修改：`back/src/main/resources/mapper/chain/EnterpriseNodeRelMapper.xml:47-75`

- [ ] **步骤 1：改写直接数查询**

```xml
<select id="selectDirectCountsByChainId"
        resultMap="nodeEnterpriseCountRowMap">
    SELECT r.node_id,
           COUNT(DISTINCT CASE
               WHEN e.id IS NOT NULL THEN r.enterprise_id
               ELSE NULL
           END) AS direct_enterprise_count
    FROM enterprise_node_rel r
    INNER JOIN chain_node n ON n.id = r.node_id
    LEFT JOIN enterprise e
           ON e.id = r.enterprise_id
          AND e.is_deleted = 0
    WHERE n.chain_id = #{chainId}
      AND n.is_deleted = 0
      AND r.is_deleted = 0
    GROUP BY r.node_id
</select>
```

- [ ] **步骤 2：改写节点详情查询**

```xml
<select id="selectEnterpriseCountSummary"
        resultMap="enterpriseCountSummaryMap">
    SELECT COUNT(DISTINCT CASE
               WHEN n.id = #{nodeId} AND e.id IS NOT NULL
               THEN r.enterprise_id
               ELSE NULL
           END) AS direct_enterprise_count,
           COUNT(DISTINCT CASE
               WHEN e.id IS NOT NULL THEN r.enterprise_id
               ELSE NULL
           END) AS total_enterprise_count
    FROM chain_node n
    LEFT JOIN enterprise_node_rel r
           ON r.node_id = n.id
          AND r.is_deleted = 0
    LEFT JOIN enterprise e
           ON e.id = r.enterprise_id
          AND e.is_deleted = 0
    WHERE n.chain_id = #{chainId}
      AND n.is_deleted = 0
      AND n.node_path LIKE #{nodePathPattern}
</select>
```

- [ ] **步骤 3：实现子树批量查询**

```xml
<select id="selectSubtreeCountsByChainId"
        resultMap="nodeSubtreeEnterpriseCountRowMap">
    SELECT ancestor.id AS node_id,
           COUNT(DISTINCT CASE
               WHEN e.id IS NOT NULL THEN r.enterprise_id
               ELSE NULL
           END) AS subtree_enterprise_count
    FROM chain_node ancestor
    LEFT JOIN chain_node descendant
           ON descendant.chain_id = ancestor.chain_id
          AND descendant.node_path LIKE CONCAT(ancestor.node_path, '%')
          AND descendant.is_deleted = 0
    LEFT JOIN enterprise_node_rel r
           ON r.node_id = descendant.id
          AND r.is_deleted = 0
    LEFT JOIN enterprise e
           ON e.id = r.enterprise_id
          AND e.is_deleted = 0
    WHERE ancestor.chain_id = #{chainId}
      AND ancestor.is_deleted = 0
    GROUP BY ancestor.id
</select>
```

- [ ] **步骤 4：运行 Mapper 测试确认绿灯**

```powershell
mvn "-Dtest=ChainPerformanceMapperTest" test
```

预期：`BUILD SUCCESS`，四个新增统计回归全部通过。

## 任务 4：用失败测试锁定 Service 组树语义

**文件：**
- 修改：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java:251-365`

- [ ] **步骤 1：增加投影辅助方法**

导入 `NodeSubtreeEnterpriseCountRow`，在 `countRow` 后增加：

```java
private NodeSubtreeEnterpriseCountRow subtreeCountRow(
        Long nodeId,
        Long subtreeCount) {
    NodeSubtreeEnterpriseCountRow row =
            new NodeSubtreeEnterpriseCountRow();
    row.setNodeId(nodeId);
    row.setSubtreeEnterpriseCount(subtreeCount);
    return row;
}
```

- [ ] **步骤 2：给现有树测试补充聚合结果**

在 `should_build_tree_when_get_chain_tree` 中增加：

```java
when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
        .thenReturn(List.of(
                subtreeCountRow(2L, 3L),
                subtreeCountRow(3L, 2L)));
when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
        .thenReturn(List.of(summaryCountRow(1L, 2L, 3L)));
```

在断言末尾增加：

```java
assertEquals(3, root.getSubtreeEnterpriseCount());
verify(enterpriseNodeRelMapper).selectSubtreeCountsByChainId(1L);
verify(chainMapper).selectSummaryCountsByChainIds(List.of(1L));
```

- [ ] **步骤 3：增加跨一级节点去重测试**

```java
@Test
void shouldUseDistinctChainCountForVirtualRoot() {
    Chain chain = chain(1L, "新能源汽车");
    ChainNode first = rootNode(100L, "中游");
    ChainNode second = rootNode(103L, "整车制造");
    when(chainMapper.selectById(1L)).thenReturn(chain);
    when(chainNodeMapper.selectByChainId(1L))
            .thenReturn(List.of(first, second));
    when(enterpriseNodeRelMapper.selectDirectCountsByChainId(1L))
            .thenReturn(List.of(
                    countRow(100L, 1L),
                    countRow(103L, 1L)));
    when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
            .thenReturn(List.of(
                    subtreeCountRow(100L, 1L),
                    subtreeCountRow(103L, 1L)));
    when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
            .thenReturn(List.of(summaryCountRow(1L, 2L, 1L)));

    TreeNodeVO root = chainService.getChainTree(1L);

    assertEquals(1, root.getSubtreeEnterpriseCount());
    assertEquals(1, root.getChildren().get(0).getSubtreeEnterpriseCount());
    assertEquals(1, root.getChildren().get(1).getSubtreeEnterpriseCount());
}
```

- [ ] **步骤 4：锁定固定查询次数**

在 `should_query_enterprise_counts_once_for_many_tree_nodes` 和
`should_map_keywords_and_description_in_tree_nodes` 中增加：

```java
when(enterpriseNodeRelMapper.selectSubtreeCountsByChainId(1L))
        .thenReturn(Collections.emptyList());
when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
        .thenReturn(Collections.emptyList());
```

在大规模树测试末尾增加：

```java
verify(enterpriseNodeRelMapper, times(1))
        .selectSubtreeCountsByChainId(1L);
verify(chainMapper, times(1))
        .selectSummaryCountsByChainIds(List.of(1L));
assertEquals(0, tree.getSubtreeEnterpriseCount());
```

- [ ] **步骤 5：运行 Service 测试确认红灯**

```powershell
mvn "-Dtest=ChainServiceTest" test
```

预期：`BUILD FAILURE`。跨一级节点测试当前返回 `2`，且当前实现没有调用新的子树聚合与链级聚合。

## 任务 5：让 Service 消费三个独立聚合结果

**文件：**
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java:191-262`

- [ ] **步骤 1：加载节点子树统计映射**

导入 `NodeSubtreeEnterpriseCountRow`，在直接数映射后增加：

```java
Map<Long, Long> subtreeCountMap = new HashMap<>();
for (NodeSubtreeEnterpriseCountRow row
        : enterpriseNodeRelMapper.selectSubtreeCountsByChainId(chainId)) {
    subtreeCountMap.put(
            row.getNodeId(),
            row.getSubtreeEnterpriseCount());
}
```

- [ ] **步骤 2：让虚拟根节点使用链级总数**

将一级节点子树求和逻辑替换为：

```java
List<TreeNodeVO> children = buildChildren(
        roots, childrenMap, directCountMap, subtreeCountMap);
ChainSummaryCountRow chainSummary =
        loadChainSummaryCountMap(List.of(chainId)).get(chainId);
long rootSubtree = chainSummary == null
        ? 0L
        : chainSummary.getEnterpriseCount();
root.setSubtreeEnterpriseCount(Math.toIntExact(rootSubtree));
root.setChildren(children);
```

- [ ] **步骤 3：让普通节点读取子树聚合映射**

将 `buildChildren` 签名改为：

```java
private List<TreeNodeVO> buildChildren(
        List<ChainNode> nodes,
        Map<Long, List<ChainNode>> childrenMap,
        Map<Long, Long> directCountMap,
        Map<Long, Long> subtreeCountMap) {
```

递归调用同步传入 `subtreeCountMap`，将递归加法替换为：

```java
long directCount = directCountMap.getOrDefault(node.getId(), 0L);
long subtreeCount = subtreeCountMap.getOrDefault(node.getId(), 0L);
vo.setDirectEnterpriseCount(Math.toIntExact(directCount));
vo.setSubtreeEnterpriseCount(Math.toIntExact(subtreeCount));
```

- [ ] **步骤 4：运行 Service 测试确认绿灯**

```powershell
mvn "-Dtest=ChainServiceTest" test
```

预期：`BUILD SUCCESS`。虚拟根节点、普通节点和查询次数断言全部通过。

## 任务 6：收口代码语义

**文件：**
- 修改：`back/src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java:63-93`
- 修改：`back/src/main/java/git/ggwzz/chain/mapper/projection/NodeEnterpriseCountRow.java:5-15`
- 修改：`back/src/main/java/git/ggwzz/chain/mapper/projection/EnterpriseCountSummary.java:5-15`
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java:196-258`

- [ ] **步骤 1：统一直接数 JavaDoc**

```java
/**
 * 按节点统计产业链下直接挂接的去重有效企业数。
 *
 * @param chainId 产业链 ID
 * @return 有有效挂接企业的节点及其直接去重有效企业数
 */
```

- [ ] **步骤 2：统一节点详情 JavaDoc**

```java
/**
 * 统计节点直接挂接的去重有效企业数和子树去重有效企业总数。
 *
 * @param chainId 产业链 ID
 * @param nodeId 当前节点 ID
 * @param nodePathPattern 当前节点路径右模糊模式
 * @return 直接去重有效企业数和子树去重有效企业总数
 */
```

- [ ] **步骤 3：统一投影注释**

`NodeEnterpriseCountRow` 的字段使用：

```java
/** 节点直接挂接的去重有效企业数 */
private Long directEnterpriseCount;
```

`EnterpriseCountSummary` 的字段使用：

```java
/** 当前节点直接挂接的去重有效企业数 */
private Long directEnterpriseCount;

/** 当前节点及全部后代节点中的去重有效企业数 */
private Long totalEnterpriseCount;
```

- [ ] **步骤 4：修正组树注释**

```java
// 直接数和子树数均由批量聚合 SQL 返回，组树阶段只负责映射结果。
```

## 任务 7：完整验证与范围审计

**文件：**
- 验证：本计划列出的全部生产代码、测试和文档文件

- [ ] **步骤 1：运行定向测试集**

```powershell
mvn "-Dtest=ChainPerformanceMapperTest,ChainServiceTest" test
```

预期：`BUILD SUCCESS`。

- [ ] **步骤 2：运行全量测试**

```powershell
mvn test
```

预期：`BUILD SUCCESS`，没有其他模块回归。

- [ ] **步骤 3：检查格式和范围**

```powershell
git diff --check
git status --short
git diff --stat
```

预期：`git diff --check` 无输出；业务代码变更仅包含“文件结构”列出的后端文件。设计规格和最终计划文档允许作为新增文件出现，不得出现前端、仪表盘、迁移或接口契约文件变更。

- [ ] **步骤 4：停止在可交付状态**

记录定向测试和全量测试的通过数量并报告实际变更文件。不要执行任何 Git 提交或上传操作。
