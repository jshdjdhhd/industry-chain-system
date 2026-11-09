# 产业链阶段层级问题修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复节点编辑时父节点更新失效、阶段节点非法移动提示错误以及已有产业链缺少阶段节点的问题。

**架构：** 扩展现有节点更新接口，用显式的 `parentChangeRequested` 区分“未修改父节点”和“移动为一级节点”，并在一个后端事务中复用既有子树移动逻辑。树接口补充编辑所需字段，前端只发起一次原子更新请求；新的 V13 迁移为所有有效产业链补齐阶段，只自动归类确定性数据。

**技术栈：** JDK 21、Spring Boot 3.5.15、MyBatis-Plus 3.5.7、MySQL 5.7、JUnit 5、Mockito、MockMvc、Vue 3、TypeScript、Element Plus、Vitest

---

## 执行约束

- 后端修改只允许发生在 `groupproject-back-commit/back`。
- 前端修改发生在 `groupproject-front/front`。
- 不修改已经发布的 `V12__chain_stage_hierarchy.sql`。
- 不通过关键词或模糊名称推断未知历史节点的阶段。
- Codex 不执行任何 Git 提交、推送、合并或上传操作。
- 计划中的提交检查点仅供用户自行执行；实现代理到达检查点时只报告建议命令。

## 文件结构

### 后端

- 修改 `src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java`：增加显式父节点变更字段。
- 修改 `src/main/java/git/ggwzz/chain/vo/TreeNodeVO.java`：增加 `parentId` 和 `sortOrder`。
- 修改 `src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`：复用子树移动算法并原子更新。
- 修改 `src/main/java/git/ggwzz/chain/controller/ChainController.java`：增加组合权限校验。
- 修改 `src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`：覆盖树字段、阶段保护和原子移动。
- 修改 `src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`：覆盖请求绑定和组合权限。
- 创建 `src/main/resources/db/migration/V13__backfill_chain_stages.sql`：补齐已有产业链阶段。
- 修改 `docs/API-INTERFACE-REGISTRY.md`：记录新契约和错误信息。
- 修改 `src/test/resources/http/chain.http`：增加原子编辑验收用例。

### 前端

- 修改 `src/types/domain.ts`：增加节点更新载荷类型。
- 修改 `src/api/chain.ts`：映射树字段并提交更新标记。
- 修改 `src/api/chain.spec.ts`：覆盖树字段和更新请求契约。
- 修改 `src/api/mock.ts`：让 Mock 更新复用移动逻辑。
- 修改 `src/views/chain/ChainManagement.vue`：保存原始父节点并限制阶段节点。
- 修改 `src/views/chain/ChainManagement.spec.ts`：覆盖阶段提示、父节点比较及错误流程。

## 任务 1：补齐树接口编辑字段

**文件：**

- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/vo/TreeNodeVO.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`

- [ ] **步骤 1：编写树字段失败测试**

在现有树测试中为父子节点设置并断言字段：

```java
up.setParentId(null);
up.setSortOrder(1);
raw.setParentId(2L);
raw.setSortOrder(7);

TreeNodeVO root = chainService.getChainTree(1L);
TreeNodeVO upNode = root.getChildren().get(0);
TreeNodeVO rawNode = upNode.getChildren().get(0);

assertNull(upNode.getParentId());
assertEquals(1, upNode.getSortOrder());
assertEquals(2L, rawNode.getParentId());
assertEquals(7, rawNode.getSortOrder());
```

- [ ] **步骤 2：运行测试验证失败**

工作目录：`groupproject-back-commit/back`

```powershell
mvn "-Dtest=ChainServiceTest#should_build_tree_when_get_chain_tree" test
```

预期：编译失败，提示 `TreeNodeVO` 缺少父节点或排序访问方法。

- [ ] **步骤 3：扩展 TreeNodeVO**

```java
/** 父节点 ID，一级节点为 null */
private Long parentId;

/** 同级排序 */
private Integer sortOrder;
```

- [ ] **步骤 4：映射树字段**

在 `ChainServiceImpl.buildChildren` 中增加：

```java
vo.setParentId(node.getParentId());
vo.setSortOrder(node.getSortOrder());
```

虚拟产业链根节点不设置这两个字段。

- [ ] **步骤 5：运行树相关测试**

```powershell
mvn "-Dtest=ChainServiceTest#should_build_tree_when_get_chain_tree+should_query_enterprise_counts_once_for_many_tree_nodes+should_map_keywords_and_description_in_tree_nodes" test
```

预期：3 个测试全部通过。

- [ ] **步骤 6：用户自行提交检查点**

```powershell
git add back/src/main/java/git/ggwzz/chain/vo/TreeNodeVO.java `
        back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java `
        back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java
git commit -m "fix: 补齐产业链树节点编辑字段"
```

## 任务 2：用测试定义原子节点更新

**文件：**

- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java`

- [ ] **步骤 1：增加阶段节点保护测试**

```java
@Test
void should_reject_parent_change_when_update_stage_node() {
    ChainNode stage = rootNode(2L, "上游");
    stage.setStageType(ChainStageType.UPSTREAM);
    when(chainNodeMapper.selectById(2L)).thenReturn(stage);

    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setNodeName("供应端");
    request.setParentChangeRequested(true);
    request.setParentId(9L);

    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.updateNode(2L, request));

    assertEquals(400, ex.getCode());
    assertEquals("阶段节点无法修改父节点", ex.getMessage());
    verify(chainNodeMapper, never()).updateSubtree(
            anyLong(), anyLong(), any(), any(),
            any(), any(), any(), any());
    verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
}
```

- [ ] **步骤 2：增加普通节点原子移动测试**

```java
@Test
void should_update_fields_and_parent_in_one_request() {
    ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
    ChainNode target = rootNode(9L, "中游");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);
    when(chainNodeMapper.selectById(9L)).thenReturn(target);
    when(chainNodeMapper.updateSubtree(
            1L, 3L, 9L, 4,
            "/2/3/", "/2/3/%", "/9/3/", 0))
            .thenReturn(2);
    when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setNodeName("动力电池");
    request.setKeywords("动力电池,电芯");
    request.setSortOrder(4);
    request.setParentChangeRequested(true);
    request.setParentId(9L);

    NodeVO result = chainService.updateNode(3L, request);

    assertEquals(9L, result.getParentId());
    assertEquals("/9/3/", result.getNodePath());
    assertEquals("动力电池", result.getNodeName());
    assertEquals("动力电池,电芯", result.getKeywords());
    verify(chainNodeMapper).updateSubtree(
            1L, 3L, 9L, 4,
            "/2/3/", "/2/3/%", "/9/3/", 0);
    verify(chainNodeMapper).updateById(node);
}
```

- [ ] **步骤 3：增加兼容和移动为一级节点测试**

```java
@Test
void should_not_move_when_parent_change_is_not_requested() {
    ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);
    when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setNodeName("改名节点");
    request.setParentId(9L);

    NodeVO result = chainService.updateNode(3L, request);

    assertEquals(2L, result.getParentId());
    verify(chainNodeMapper, never()).updateSubtree(
            anyLong(), anyLong(), any(), any(),
            any(), any(), any(), any());
}

@Test
void should_move_to_root_when_parent_change_requested_with_null_parent() {
    ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);
    when(chainNodeMapper.updateSubtree(
            1L, 3L, null, null,
            "/2/3/", "/2/3/%", "/3/", -1))
            .thenReturn(1);
    when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setParentChangeRequested(true);
    request.setParentId(null);

    NodeVO result = chainService.updateNode(3L, request);

    assertNull(result.getParentId());
    assertEquals(1, result.getNodeLevel());
    assertEquals("/3/", result.getNodePath());
}
```

- [ ] **步骤 4：运行测试验证失败**

```powershell
mvn "-Dtest=ChainServiceTest#should_reject_parent_change_when_update_stage_node+should_update_fields_and_parent_in_one_request+should_not_move_when_parent_change_is_not_requested+should_move_to_root_when_parent_change_requested_with_null_parent" test
```

预期：编译失败，提示 `UpdateNodeRequest` 缺少父节点变更字段。

- [ ] **步骤 5：扩展 UpdateNodeRequest**

```java
/** 是否明确请求修改父节点；缺省为 false，兼容原有更新调用 */
private boolean parentChangeRequested;

/** 目标父节点 ID；请求修改且值为 null 时移动为一级节点 */
private Long parentId;
```

必须使用基本类型 `boolean`，确保字段缺省时为 `false`，权限表达式不会遇到空值。

- [ ] **步骤 6：重新运行新增测试**

```powershell
mvn "-Dtest=ChainServiceTest#should_reject_parent_change_when_update_stage_node+should_update_fields_and_parent_in_one_request+should_not_move_when_parent_change_is_not_requested+should_move_to_root_when_parent_change_requested_with_null_parent" test
```

预期：测试能够编译，但移动相关断言失败。

## 任务 3：复用移动算法并实现事务性更新

**文件：**

- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`

- [ ] **步骤 1：抽取内部移动方法**

```java
private void moveNodeInternal(
        ChainNode node,
        Long newParentId,
        Integer sortOrder,
        String stageErrorMessage) {
    requireOrdinaryNode(node, stageErrorMessage);
    if (Objects.equals(node.getId(), newParentId)) {
        throw new BusinessException(
                ResultCode.BAD_REQUEST,
                "节点不能移动到自身下");
    }

    String oldPath = requireCanonicalNodePath(node);
    int oldLevel = node.getNodeLevel();
    String parentPath = "/";
    int newLevel = 1;
    if (newParentId != null) {
        ChainNode parent = chainNodeMapper.selectById(newParentId);
        if (parent == null) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "父节点不存在");
        }
        if (!Objects.equals(node.getChainId(), parent.getChainId())) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "不能跨产业链移动节点");
        }
        validateMoveTarget(node, parent);
        parentPath = requireCanonicalNodePath(parent);
        newLevel = parent.getNodeLevel() + 1;
    }

    String newPath = parentPath + node.getId() + "/";
    int affected = chainNodeMapper.updateSubtree(
            node.getChainId(),
            node.getId(),
            newParentId,
            sortOrder,
            oldPath,
            oldPath + "%",
            newPath,
            newLevel - oldLevel);
    if (affected == 0) {
        throw new BusinessException(
                ResultCode.NOT_FOUND,
                "节点不存在");
    }

    node.setParentId(newParentId);
    node.setNodePath(newPath);
    node.setNodeLevel(newLevel);
    if (sortOrder != null) {
        node.setSortOrder(sortOrder);
    }
}
```

- [ ] **步骤 2：让独立移动接口复用内部方法**

`moveNode` 查询节点后调用：

```java
moveNodeInternal(
        node,
        request.getNewParentId(),
        request.getSortOrder(),
        "阶段节点无法修改父节点");
return toNodeVO(node);
```

将原移动接口的阶段错误测试期望同步为：

```java
assertEquals("阶段节点无法修改父节点", ex.getMessage());
```

- [ ] **步骤 3：在 updateNode 中接入移动**

查询节点后、写入普通属性前增加：

```java
if (request.isParentChangeRequested()) {
    moveNodeInternal(
            node,
            request.getParentId(),
            request.getSortOrder(),
            "阶段节点无法修改父节点");
}
```

普通属性写入后检查更新结果：

```java
if (chainNodeMapper.updateById(node) != 1) {
    throw new BusinessException(
            ResultCode.INTERNAL_ERROR,
            "节点更新失败");
}
return toNodeVO(node);
```

保留 `updateNode` 的事务注解，不允许通过两个公开 Service 方法拼接操作。

所有既有的 `updateNode` 成功测试都需要显式模拟数据库更新成功：

```java
when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);
```

另增加异常影响行数测试：

```java
@Test
void should_fail_when_node_update_affects_no_rows() {
    ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);
    when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(0);

    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setNodeName("更新失败节点");

    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.updateNode(3L, request));

    assertEquals(500, ex.getCode());
    assertEquals("节点更新失败", ex.getMessage());
}
```

- [ ] **步骤 4：增加移动失败时不更新属性测试**

```java
@Test
void should_not_update_fields_when_atomic_move_fails() {
    ChainNode node = movableNode(3L, 1L, 2L, 2, "/2/3/");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);
    when(chainNodeMapper.updateSubtree(
            1L, 3L, null, null,
            "/2/3/", "/2/3/%", "/3/", -1))
            .thenReturn(0);

    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setNodeName("不应落库");
    request.setParentChangeRequested(true);

    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.updateNode(3L, request));

    assertEquals(404, ex.getCode());
    verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
}
```

- [ ] **步骤 5：运行更新和移动测试**

```powershell
mvn "-Dtest=ChainServiceTest" test
```

预期：整个 `ChainServiceTest` 通过，现有移动边界行为保持不变。

- [ ] **步骤 6：用户自行提交检查点**

```powershell
git add back/src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java `
        back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java `
        back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java
git commit -m "fix: 原子更新节点属性和父节点"
```

## 任务 4：补充组合权限和 Controller 契约

**文件：**

- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/controller/ChainController.java`

- [ ] **步骤 1：增加组合权限测试**

补充 `put` 静态导入，并新增：

```java
@Test
@WithMockUser(authorities = "chain:node:update")
void updateNodeWithoutParentChangeShouldRequireOnlyUpdatePermission()
        throws Exception {
    NodeVO result = new NodeVO();
    result.setId(3L);
    result.setNodeName("动力电池");
    when(chainService.updateNode(eq(3L), any())).thenReturn(result);

    mockMvc.perform(put("/api/chains/nodes/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "nodeName": "动力电池",
                              "parentChangeRequested": false
                            }
                            """))
            .andExpect(status().isOk());
}

@Test
@WithMockUser(authorities = "chain:node:update")
void updateNodeWithParentChangeShouldRequireMovePermission()
        throws Exception {
    mockMvc.perform(put("/api/chains/nodes/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "parentChangeRequested": true,
                              "parentId": 9
                            }
                            """))
            .andExpect(status().isForbidden());

    verify(chainService, never()).updateNode(eq(3L), any());
}

@Test
@WithMockUser(authorities = {
        "chain:node:update",
        "chain:node:move"
})
void updateNodeWithParentChangeShouldAllowCombinedPermissions()
        throws Exception {
    NodeVO result = new NodeVO();
    result.setId(3L);
    result.setParentId(9L);
    when(chainService.updateNode(eq(3L), any())).thenReturn(result);

    mockMvc.perform(put("/api/chains/nodes/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "parentChangeRequested": true,
                              "parentId": 9
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parentId").value(9));
}
```

- [ ] **步骤 1.1：增加树响应字段测试**

补充 `TreeNodeVO` 导入，并新增：

```java
@Test
@WithMockUser(authorities = "chain:node:query")
void getChainTreeShouldExposeParentAndSortOrder() throws Exception {
    TreeNodeVO root = new TreeNodeVO();
    root.setId(1L);
    root.setNodeName("新能源汽车产业链");
    TreeNodeVO child = new TreeNodeVO();
    child.setId(3L);
    child.setParentId(2L);
    child.setSortOrder(7);
    child.setNodeName("动力电池");
    root.setChildren(List.of(child));
    when(chainService.getChainTree(1L)).thenReturn(root);

    mockMvc.perform(get("/api/chains/1/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.children[0].parentId")
                    .value(2))
            .andExpect(jsonPath("$.data.children[0].sortOrder")
                    .value(7));
}
```

- [ ] **步骤 2：运行测试验证权限缺口**

```powershell
mvn "-Dtest=ChainControllerTest#updateNodeWithoutParentChangeShouldRequireOnlyUpdatePermission+updateNodeWithParentChangeShouldRequireMovePermission+updateNodeWithParentChangeShouldAllowCombinedPermissions" test
```

预期：缺少移动权限的请求仍进入 Service 或返回 200。

- [ ] **步骤 3：修改权限表达式**

```java
@PreAuthorize(
        "hasAuthority('chain:node:update')"
                + " and (!#request.parentChangeRequested"
                + " or hasAuthority('chain:node:move'))")
```

- [ ] **步骤 4：运行 Controller 测试**

```powershell
mvn "-Dtest=ChainControllerTest" test
```

预期：全部通过；单独更新权限允许属性编辑，修改父节点需要双权限。

- [ ] **步骤 5：用户自行提交检查点**

```powershell
git add back/src/main/java/git/ggwzz/chain/controller/ChainController.java `
        back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java
git commit -m "fix: 校验节点编辑的移动权限"
```

## 任务 5：创建 V13 历史阶段回填迁移

**文件：**

- 创建：`groupproject-back-commit/back/src/main/resources/db/migration/V13__backfill_chain_stages.sql`

- [ ] **步骤 1：编写迁移 SQL**

```sql
-- 1. 将唯一且名称精确匹配的一级普通节点升级为阶段节点。
UPDATE `chain_node` candidate
JOIN (
    SELECT promoted.candidate_id,
           promoted.chain_id,
           promoted.stage_type,
           promoted.sort_order
    FROM (
        SELECT MIN(n.id) candidate_id,
               n.chain_id,
               CASE n.node_name
                   WHEN '上游' THEN 'UPSTREAM'
                   WHEN '中游' THEN 'MIDSTREAM'
                   WHEN '下游' THEN 'DOWNSTREAM'
               END stage_type,
               CASE n.node_name
                   WHEN '上游' THEN 1
                   WHEN '中游' THEN 2
                   WHEN '下游' THEN 3
               END sort_order
        FROM `chain_node` n
        WHERE n.is_deleted = 0
          AND n.parent_id IS NULL
          AND n.stage_type IS NULL
          AND n.node_name IN ('上游', '中游', '下游')
        GROUP BY n.chain_id, n.node_name
        HAVING COUNT(*) = 1
    ) promoted
) unique_stage
  ON unique_stage.candidate_id = candidate.id
LEFT JOIN `chain_node` existing
  ON existing.chain_id = unique_stage.chain_id
 AND existing.stage_type = unique_stage.stage_type
 AND existing.is_deleted = 0
SET candidate.stage_type = unique_stage.stage_type,
    candidate.parent_id = NULL,
    candidate.node_level = 1,
    candidate.node_path = CONCAT('/', candidate.id, '/'),
    candidate.sort_order = unique_stage.sort_order,
    candidate.update_time = CURRENT_TIMESTAMP
WHERE existing.id IS NULL;

-- 2. 为所有有效产业链补齐缺失阶段。
INSERT INTO `chain_node`
    (`chain_id`, `parent_id`, `node_name`, `keywords`, `node_level`,
     `description`, `node_path`, `sort_order`, `stage_type`)
SELECT c.id, NULL, stage.node_name, stage.keywords, 1,
       stage.description, '/', stage.sort_order, stage.stage_type
FROM `chain` c
JOIN (
    SELECT '上游' node_name, '上游,供应端,原材料' keywords,
           '产业链上游阶段' description, 1 sort_order,
           'UPSTREAM' stage_type
    UNION ALL
    SELECT '中游', '中游,制造端,核心环节',
           '产业链中游阶段', 2, 'MIDSTREAM'
    UNION ALL
    SELECT '下游', '下游,应用端,市场端',
           '产业链下游阶段', 3, 'DOWNSTREAM'
) stage
LEFT JOIN `chain_node` existing
  ON existing.chain_id = c.id
 AND existing.stage_type = stage.stage_type
 AND existing.is_deleted = 0
WHERE c.is_deleted = 0
  AND existing.id IS NULL;

-- 3. 规范有效阶段节点。
UPDATE `chain_node`
SET parent_id = NULL,
    node_level = 1,
    node_path = CONCAT('/', id, '/'),
    sort_order = CASE stage_type
        WHEN 'UPSTREAM' THEN 1
        WHEN 'MIDSTREAM' THEN 2
        WHEN 'DOWNSTREAM' THEN 3
    END,
    update_time = CURRENT_TIMESTAMP
WHERE stage_type IS NOT NULL
  AND is_deleted = 0;

-- 4. 只迁移项目验收数据中能够确定归属的演示节点。
UPDATE `chain_node` descendant
JOIN `chain_node` child
  ON descendant.chain_id = child.chain_id
 AND descendant.node_path LIKE CONCAT(child.node_path, '%')
JOIN (
    SELECT 1 chain_id, '氢气制取' node_name, 'UPSTREAM' stage_type
    UNION ALL SELECT 1, '氢气储运', 'UPSTREAM'
    UNION ALL SELECT 1, '燃料电池', 'MIDSTREAM'
    UNION ALL SELECT 1, '动力电池', 'MIDSTREAM'
    UNION ALL SELECT 1, '电驱系统', 'MIDSTREAM'
    UNION ALL SELECT 1, '新能源整车', 'DOWNSTREAM'
    UNION ALL SELECT 1, '整车制造', 'DOWNSTREAM'
    UNION ALL SELECT 1, '充换电设施', 'DOWNSTREAM'
    UNION ALL SELECT 2, '芯片设计', 'UPSTREAM'
    UNION ALL SELECT 2, '半导体设备', 'UPSTREAM'
    UNION ALL SELECT 2, '半导体材料', 'UPSTREAM'
    UNION ALL SELECT 2, '晶圆代工', 'MIDSTREAM'
    UNION ALL SELECT 2, '晶圆制造', 'MIDSTREAM'
    UNION ALL SELECT 2, '封装测试', 'MIDSTREAM'
    UNION ALL SELECT 2, '功率半导体', 'MIDSTREAM'
) mapping
  ON mapping.chain_id = child.chain_id
 AND mapping.node_name = child.node_name
JOIN `chain_node` stage
  ON stage.chain_id = child.chain_id
 AND stage.stage_type = mapping.stage_type
 AND stage.is_deleted = 0
SET descendant.node_path = CONCAT(
        stage.node_path,
        SUBSTRING(descendant.node_path, 2)
    ),
    descendant.node_level = descendant.node_level + 1,
    descendant.parent_id = CASE
        WHEN descendant.id = child.id THEN stage.id
        ELSE descendant.parent_id
    END,
    descendant.update_time = CURRENT_TIMESTAMP
WHERE child.parent_id IS NULL
  AND child.stage_type IS NULL
  AND child.is_deleted = 0
  AND descendant.is_deleted = 0;
```

- [ ] **步骤 2：静态检查迁移边界**

```powershell
rg -n "LIKE|node_name IN|stage_type|is_deleted|parent_id" `
  src/main/resources/db/migration/V13__backfill_chain_stages.sql
```

预期：只有路径使用 `LIKE`；阶段识别采用精确名称；确定性演示数据使用明确映射。

- [ ] **步骤 3：在一次性 MySQL 5.7 测试库执行**

将生产结构和脱敏数据复制到 `industry_chain_stage_test` 后执行：

```powershell
Get-Content -Raw -Encoding UTF8 `
  src/main/resources/db/migration/V13__backfill_chain_stages.sql |
  mysql.exe --default-character-set=utf8mb4 `
    --host=127.0.0.1 --user=root --password `
    industry_chain_stage_test
```

预期：退出码为 0，无语法错误或唯一键冲突。

- [ ] **步骤 3.1：验证迁移防重复保护**

在同一个一次性数据库再次执行相同的 V13 SQL，然后查询：

```sql
SELECT chain_id, stage_type, COUNT(*) AS active_count
FROM chain_node
WHERE stage_type IS NOT NULL
  AND is_deleted = 0
GROUP BY chain_id, stage_type
HAVING active_count <> 1;
```

预期：第二次执行无唯一键错误，查询返回空结果。

- [ ] **步骤 4：检查迁移不变量**

```powershell
@'
SELECT c.id, c.chain_name,
       COUNT(DISTINCT n.stage_type) AS stage_count
FROM `chain` c
LEFT JOIN chain_node n
  ON n.chain_id = c.id
 AND n.stage_type IS NOT NULL
 AND n.is_deleted = 0
WHERE c.is_deleted = 0
GROUP BY c.id, c.chain_name
HAVING stage_count <> 3;

SELECT id, chain_id, node_name, parent_id, node_level, node_path, stage_type
FROM chain_node
WHERE stage_type IS NOT NULL
  AND is_deleted = 0
  AND (
      parent_id IS NOT NULL
      OR node_level <> 1
      OR node_path <> CONCAT('/', id, '/')
  );
'@ | mysql.exe --default-character-set=utf8mb4 `
  --host=127.0.0.1 --user=root --password `
  industry_chain_stage_test
```

预期：两个查询均返回空结果。

- [ ] **步骤 5：检查未知数据和挂链关系**

迁移前后比较：

```sql
SELECT COUNT(*) FROM enterprise_node_rel WHERE is_deleted = 0;

SELECT id, chain_id, parent_id, node_name, node_path
FROM chain_node
WHERE is_deleted = 0
  AND stage_type IS NULL
  AND chain_id NOT IN (1, 2)
ORDER BY id;
```

预期：挂链关系数量不变；除唯一精确命名的阶段候选外，未知普通节点不被归类。

- [ ] **步骤 6：用户自行提交检查点**

```powershell
git add back/src/main/resources/db/migration/V13__backfill_chain_stages.sql
git commit -m "fix: 回填已有产业链阶段节点"
```

## 任务 6：更新前端类型和真实 API 适配器

**文件：**

- 修改：`groupproject-front/front/src/types/domain.ts`
- 修改：`groupproject-front/front/src/api/chain.ts`
- 修改：`groupproject-front/front/src/api/chain.spec.ts`

- [ ] **步骤 1：扩展树映射失败测试**

在树接口输入和期望中分别加入：

```typescript
parentId: null,
sortOrder: 1,
```

```typescript
parentId: null,
sortNo: 1,
```

对子节点加入 `parentId: 10`、`sortOrder: 7`，并期望
`parentId: 10`、`sortNo: 7`。

- [ ] **步骤 2：增加更新请求契约测试**

```typescript
it("updates node fields and parent atomically", async () => {
  vi.mocked(put).mockResolvedValue({
    id: 12,
    chainId: 1,
    parentId: 20,
    nodeName: "动力电池",
    nodeLevel: 2,
    nodePath: "/20/12/",
    sortOrder: 3,
  });

  await updateNode(12, {
    parentId: 20,
    parentChangeRequested: true,
    name: "动力电池",
    keywords: "动力电池,电芯",
    definition: "电池制造",
    sortNo: 3,
  });

  expect(put).toHaveBeenCalledWith("/chains/nodes/12", {
    parentId: 20,
    parentChangeRequested: true,
    nodeName: "动力电池",
    keywords: "动力电池,电芯",
    description: "电池制造",
    sortOrder: 3,
  });
});
```

- [ ] **步骤 3：运行测试验证失败**

工作目录：`groupproject-front/front`

```powershell
npm run test -- src/api/chain.spec.ts
```

预期：树映射缺少字段，更新载荷类型不接受变更标记。

- [ ] **步骤 4：增加类型**

```typescript
export interface UpdateChainNodePayload extends ChainNodePayload {
  parentChangeRequested: boolean;
}
```

`BackendTreeNodeVO` 增加：

```typescript
parentId?: number | null;
sortOrder?: number;
```

`BackendNodePayload` 增加：

```typescript
parentChangeRequested?: boolean;
```

- [ ] **步骤 5：实现映射和请求**

树映射增加：

```typescript
parentId: node.parentId ?? null,
sortNo: node.sortOrder,
```

新增更新载荷转换：

```typescript
function toBackendUpdateNodePayload(
  data: UpdateChainNodePayload,
): BackendNodePayload {
  return {
    ...toBackendNodePayload(data),
    parentChangeRequested: data.parentChangeRequested,
  };
}
```

更新 API：

```typescript
export function updateNode(
  id: number,
  data: UpdateChainNodePayload,
): Promise<ChainNode> {
  if (isMockApiEnabled()) return mockApi.updateNode(id, data);
  return put<BackendNodeVO>(
    `/chains/nodes/${id}`,
    toBackendUpdateNodePayload(data),
  ).then(mapBackendNode);
}
```

`createNode` 继续使用基础载荷，不发送更新标记。

- [ ] **步骤 6：运行 API 测试**

```powershell
npm run test -- src/api/chain.spec.ts
```

预期：全部通过。

## 任务 7：使 Mock 与真实后端一致

**文件：**

- 修改：`groupproject-front/front/src/api/mock.ts`
- 修改：`groupproject-front/front/src/api/chain.spec.ts`

- [ ] **步骤 1：增加 Mock 原子编辑测试**

```typescript
it("updates mock node fields and parent in one request", async () => {
  const chain = await mockApi.createChain({
    name: "Mock Atomic Update Chain",
  });
  const source = await mockApi.createNode(chain.id, { name: "Source" });
  const target = await mockApi.createNode(chain.id, { name: "Target" });
  const child = await mockApi.createNode(chain.id, {
    parentId: source.id,
    name: "Child",
  });

  await mockApi.updateNode(child.id, {
    parentId: target.id,
    parentChangeRequested: true,
    name: "Moved Child",
    sortNo: 0,
  });

  const tree = await mockApi.fetchNodeTree(chain.id);
  expect(
    tree
      .find((node) => node.id === target.id)
      ?.children?.map((node) => node.id),
  ).toContain(child.id);
});

it("rejects parent edits for mock stage nodes", async () => {
  const chain = await mockApi.createChain({
    name: "Mock Protected Stage Chain",
  });
  const tree = await mockApi.fetchNodeTree(chain.id);

  await expect(
    mockApi.updateNode(tree[0].id, {
      parentId: tree[1].id,
      parentChangeRequested: true,
      name: tree[0].name,
    }),
  ).rejects.toThrow("阶段节点无法修改父节点");
});
```

- [ ] **步骤 2：运行测试验证失败**

```powershell
npm run test -- src/api/chain.spec.ts
```

预期：Mock 更新没有移动树位置，也没有拒绝阶段节点。

- [ ] **步骤 3：抽取同步移动函数**

从现有 `moveNode` 抽取：

```typescript
function moveNodeInTree(
  id: number,
  newParentId: number | null,
  sortOrder?: number,
): ChainNode {
  const location = findNodeLocation(id);
  if (!location) throw new Error("节点不存在");
  if (location.node.stageType) {
    throw new Error("阶段节点无法修改父节点");
  }
  if (newParentId === id) {
    throw new Error("节点不能移动到自身下");
  }

  const descendantIds = new Set(
    getDescendantNodeIds(location.node),
  );
  if (newParentId !== null && descendantIds.has(newParentId)) {
    throw new Error("节点不能移动到自己的子节点下");
  }

  const newParent =
    newParentId === null
      ? undefined
      : findNodeLocation(newParentId);
  if (newParentId !== null && !newParent) {
    throw new Error("父节点不存在");
  }
  if (newParent && newParent.chainId !== location.chainId) {
    throw new Error("不能跨产业链移动节点");
  }

  const oldIndex = location.siblings.findIndex(
    (node) => node.id === id,
  );
  location.siblings.splice(oldIndex, 1);
  normalizeSiblingOrder(location.siblings);

  const targetSiblings =
    newParentId === null
      ? nodeTrees[location.chainId]
      : newParent?.node.children;
  if (!targetSiblings) throw new Error("父节点不存在");

  const targetIndex = Math.min(
    Math.max(sortOrder ?? targetSiblings.length, 0),
    targetSiblings.length,
  );
  location.node.parentId = newParentId;
  updateSubtreeLevel(
    location.node,
    newParentId === null
      ? 1
      : (newParent?.node.levelNo ?? 1) + 1,
  );
  targetSiblings.splice(targetIndex, 0, location.node);
  normalizeSiblingOrder(targetSiblings);
  return location.node;
}
```

- [ ] **步骤 4：让更新和移动复用函数**

```typescript
updateNode(id: number, data: UpdateChainNodePayload) {
  try {
    const node = data.parentChangeRequested
      ? moveNodeInTree(id, data.parentId ?? null, data.sortNo)
      : findNodeLocation(id)?.node;
    if (!node) return rejectMock("节点不存在");

    node.name = data.name;
    node.keywords = data.keywords;
    node.definition = data.definition;
    if (data.sortNo !== undefined) node.sortNo = data.sortNo;
    return sleep(node);
  } catch (error) {
    return rejectMock(
      error instanceof Error ? error.message : "节点更新失败",
    );
  }
},

moveNode(id: number, data: MoveNodeRequest) {
  try {
    return sleep(
      moveNodeInTree(id, data.newParentId, data.sortOrder),
    );
  } catch (error) {
    return rejectMock(
      error instanceof Error ? error.message : "节点移动失败",
    );
  }
},
```

- [ ] **步骤 5：运行 API 和 Mock 测试**

```powershell
npm run test -- src/api/chain.spec.ts src/api/mock-contract.spec.ts
```

预期：全部通过。

- [ ] **步骤 6：用户自行提交检查点**

```powershell
git add front/src/types/domain.ts `
        front/src/api/chain.ts `
        front/src/api/mock.ts `
        front/src/api/chain.spec.ts
git commit -m "fix: 对齐节点原子编辑前端契约"
```

## 任务 8：修复节点编辑窗口

**文件：**

- 修改：`groupproject-front/front/src/views/chain/ChainManagement.vue`
- 修改：`groupproject-front/front/src/views/chain/ChainManagement.spec.ts`

- [ ] **步骤 1：增强输入框测试桩**

```typescript
const ElInputNumberStub = defineComponent({
  inheritAttrs: false,
  props: {
    modelValue: {
      type: Number,
      default: null,
    },
    disabled: Boolean,
  },
  emits: ["update:modelValue"],
  template: `
    <input
      v-bind="$attrs"
      type="number"
      :value="modelValue ?? ''"
      :disabled="disabled"
      @input="$emit(
        'update:modelValue',
        $event.target.value === ''
          ? null
          : Number($event.target.value)
      )"
    />
  `,
});
```

挂载配置将 `ElInputNumber` 指向该桩。

- [ ] **步骤 2：增加阶段节点和普通节点测试**

```typescript
it("should prevent stage parent edits with the expected message", async () => {
  chainApiMocks.fetchNodeTree.mockResolvedValue([{
    id: 10,
    chainId: 1,
    parentId: null,
    name: "上游",
    stageType: "UPSTREAM",
    sortNo: 1,
    children: [],
  }]);

  const wrapper = mountChainManagement();
  await flushPromises();
  await wrapper.get('button[aria-label="编辑"]').trigger("click");

  expect(
    wrapper.get('[data-test="node-parent-id"]')
      .attributes("disabled"),
  ).toBeDefined();
  expect(wrapper.text()).toContain("阶段节点无法修改父节点");
});

it("should request an atomic parent change for ordinary nodes", async () => {
  const node: ChainNode = {
    id: 12,
    chainId: 1,
    parentId: 10,
    name: "动力电池",
    sortNo: 2,
    children: [],
  };
  chainApiMocks.fetchNodeTree.mockResolvedValue([node]);
  chainApiMocks.updateNode.mockResolvedValue({
    ...node,
    parentId: 20,
  });

  const wrapper = mountChainManagement();
  await flushPromises();
  await wrapper.get('button[aria-label="编辑"]').trigger("click");
  await wrapper.get('[data-test="node-parent-id"]').setValue("20");
  await getButtonByText(wrapper, "保存").trigger("click");
  await flushPromises();

  expect(chainApiMocks.updateNode).toHaveBeenCalledWith(
    12,
    expect.objectContaining({
      parentId: 20,
      parentChangeRequested: true,
      sortNo: 2,
    }),
  );
});
```

- [ ] **步骤 3：增加未移动和失败流程测试**

```typescript
it("should not request a move when the parent is unchanged", async () => {
  const node: ChainNode = {
    id: 12,
    chainId: 1,
    parentId: 10,
    name: "动力电池",
    sortNo: 2,
    children: [],
  };
  chainApiMocks.fetchNodeTree.mockResolvedValue([node]);
  chainApiMocks.updateNode.mockResolvedValue(node);

  const wrapper = mountChainManagement();
  await flushPromises();
  await wrapper.get('button[aria-label="编辑"]').trigger("click");
  await getButtonByText(wrapper, "保存").trigger("click");
  await flushPromises();

  expect(chainApiMocks.updateNode).toHaveBeenCalledWith(
    12,
    expect.objectContaining({
      parentId: 10,
      parentChangeRequested: false,
      sortNo: 2,
    }),
  );
});

it("should not show success when the atomic update fails", async () => {
  const node: ChainNode = {
    id: 12,
    chainId: 1,
    parentId: 10,
    name: "动力电池",
    sortNo: 2,
    children: [],
  };
  chainApiMocks.fetchNodeTree.mockResolvedValue([node]);
  chainApiMocks.updateNode.mockRejectedValue(
    new Error("父节点不存在"),
  );

  const wrapper = mountChainManagement();
  await flushPromises();
  await wrapper.get('button[aria-label="编辑"]').trigger("click");
  await wrapper.get('[data-test="node-parent-id"]').setValue("99");
  await getButtonByText(wrapper, "保存").trigger("click");
  await flushPromises();

  expect(elementPlusMocks.success).not.toHaveBeenCalled();
  expect(wrapper.text()).toContain("编辑节点");
});
```

- [ ] **步骤 4：运行组件测试验证失败**

```powershell
npm run test -- src/views/chain/ChainManagement.spec.ts
```

预期：缺少阶段禁用状态、提示和更新请求标记。

- [ ] **步骤 5：记录编辑上下文**

```typescript
const originalParentId = ref<number | null>(null);
const editingNodeStageType = ref<ChainStageType | null>(null);
const editingNodeIsStage = computed(
  () => editingNodeStageType.value !== null,
);
```

`openCreateNode` 重置：

```typescript
originalParentId.value = null;
editingNodeStageType.value = null;
```

`openEditNode` 保存：

```typescript
originalParentId.value = node.parentId ?? null;
editingNodeStageType.value = node.stageType ?? null;
```

- [ ] **步骤 6：生成更新载荷**

编辑分支改为：

```typescript
const parentId = nodeForm.parentId ?? null;
await updateNode(editingNodeId.value, {
  ...nodeForm,
  parentId,
  parentChangeRequested:
    parentId !== originalParentId.value,
});
ElMessage.success("节点已更新");
```

新建分支不增加更新标记。

- [ ] **步骤 7：禁用阶段节点父节点输入**

```vue
<el-input-number
  v-model="nodeForm.parentId"
  data-test="node-parent-id"
  :disabled="Boolean(editingNodeId) && editingNodeIsStage"
  :min="1"
  clearable
/>
<p
  v-if="editingNodeId && editingNodeIsStage"
  class="form-help"
  data-test="stage-parent-help"
>
  阶段节点无法修改父节点
</p>
```

样式：

```css
.form-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
  margin: 6px 0 0;
}
```

- [ ] **步骤 8：运行前端回归测试**

```powershell
npm run test -- `
  src/api/chain.spec.ts `
  src/api/mock-contract.spec.ts `
  src/views/chain/ChainManagement.spec.ts `
  src/views/chain/NodeAdjustment.spec.ts `
  src/views/chain/nodeAdjustmentViewModel.spec.ts
```

预期：全部通过，节点调整页面不受影响。

- [ ] **步骤 9：用户自行提交检查点**

```powershell
git add front/src/views/chain/ChainManagement.vue `
        front/src/views/chain/ChainManagement.spec.ts
git commit -m "fix: 修复节点编辑父节点交互"
```

## 任务 9：同步接口文档和 HTTP 验收

**文件：**

- 修改：`groupproject-back-commit/back/docs/API-INTERFACE-REGISTRY.md`
- 修改：`groupproject-back-commit/back/src/test/resources/http/chain.http`

- [ ] **步骤 1：更新接口注册表**

加入字段：

```markdown
| `parentChangeRequested` | `boolean` | 否 | 是否修改父节点，默认 `false` |
| `parentId` | `Long` | 否 | 目标父节点；请求修改且为 `null` 时移动为一级节点 |
```

加入规则：

```markdown
- `parentChangeRequested=false` 时忽略 `parentId`，兼容原有部分更新。
- `parentChangeRequested=true` 时，属性和整棵子树移动在同一事务中完成。
- 修改父节点同时需要 `chain:node:update` 和 `chain:node:move`。
- 阶段节点修改父节点返回 `400 / 阶段节点无法修改父节点`。
- `TreeNodeVO` 返回 `parentId` 和 `sortOrder`。
```

- [ ] **步骤 2：增加 HTTP 用例**

```http
### 原子修改普通节点属性和父节点
PUT {{baseUrl}}/api/chains/nodes/{{editableNodeId}}
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "nodeName": "编辑后节点",
  "keywords": "编辑,移动",
  "sortOrder": 1,
  "parentChangeRequested": true,
  "parentId": {{midstreamStageId}}
}

> {%
    client.test("节点属性和父节点同时更新", function () {
        client.assert(response.status === 200);
        client.assert(response.body.data.nodeName === "编辑后节点");
        client.assert(response.body.data.parentId
            === client.global.get("midstreamStageId"));
    });
%}

### 阶段节点禁止修改父节点
PUT {{baseUrl}}/api/chains/nodes/{{upstreamStageId}}
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "parentChangeRequested": true,
  "parentId": {{midstreamStageId}}
}

> {%
    client.test("阶段节点父节点修改被拒绝", function () {
        client.assert(response.status === 400);
        client.assert(response.body.message
            === "阶段节点无法修改父节点");
    });
%}
```

- [ ] **步骤 3：检查消息和字段一致**

```powershell
rg -n "上下游阶段节点不允许移动|阶段节点无法修改父节点|parentChangeRequested" `
  src/main/java `
  src/test/java `
  src/test/resources/http/chain.http `
  docs/API-INTERFACE-REGISTRY.md
```

预期：移动保护统一使用新消息，删除保护消息保持原样。

- [ ] **步骤 4：用户自行提交检查点**

```powershell
git add back/docs/API-INTERFACE-REGISTRY.md `
        back/src/test/resources/http/chain.http
git commit -m "docs: 更新节点原子编辑接口说明"
```

## 任务 10：完整验证

**文件：**

- 检查：`groupproject-back-commit/back`
- 检查：`groupproject-front/front`

- [ ] **步骤 1：运行后端核心测试**

工作目录：`groupproject-back-commit/back`

```powershell
mvn "-Dtest=ChainServiceTest,ChainControllerTest,ChainPerformanceMapperTest" test
```

预期：全部通过。

- [ ] **步骤 2：运行后端完整测试和打包**

```powershell
mvn test
mvn package
```

预期：两个命令均显示 `BUILD SUCCESS`。

- [ ] **步骤 3：运行前端完整测试和构建**

工作目录：`groupproject-front/front`

```powershell
npm run test
npm run build
```

预期：所有 Vitest 测试通过，TypeScript 和 Vite 构建成功。

- [ ] **步骤 4：检查格式和变更范围**

在两个仓库分别执行：

```powershell
git diff --check
git status --short
```

预期：格式检查无输出；状态只包含本计划文件和用户原有文件；不包含密钥、日志或构建产物。

- [ ] **步骤 5：执行真实前后端冒烟验收**

1. 新建产业链，确认自动生成三个阶段。
2. 编辑阶段节点名称，确认成功。
3. 阶段节点父节点输入禁用并显示指定说明。
4. 直接调用接口修改阶段父节点，确认返回 400。
5. 编辑普通子节点并更换父节点，确认只出现一次成功消息。
6. 刷新页面，确认节点仍位于新父节点下，排序未被重置。
7. 查看迁移后的已有产业链，确认均显示三个阶段。
8. 确认未知历史节点仍保留原位置，可人工归类。

- [ ] **步骤 6：报告验证结果**

报告必须列出后端测试与打包、前端测试与构建、MySQL 5.7
迁移验证和 8 项冒烟验收结果，并明确未执行 Git 提交或上传。
