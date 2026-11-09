# 产业链上下游阶段层级实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 实现“产业链 → 上游/中游/下游 → 细分节点”结构，新建产业链时自动创建三个允许改名和排序、但禁止移动和单独删除的阶段节点。

**架构：** `chain_node.stage_type` 保存稳定阶段身份，普通节点为 `NULL`。产业链和三个阶段节点在同一事务中创建；后端负责最终保护，前端和 Mock 提前禁用非法操作。只迁移项目中能确定归属的演示数据，不猜测未知历史数据。

**技术栈：** Java 21、Spring Boot 3.5、MyBatis-Plus、MySQL 5.7、JUnit 5、Mockito、Vue 3、TypeScript、Element Plus、Vitest。

**Git 约束：** AI 不执行 `git add`、`git commit`、`git push` 或创建 PR。每个任务结束只运行测试、`git diff --check` 和只读状态检查。

---

## 文件清单

### 后端

- 创建：`back/src/main/java/git/ggwzz/chain/enums/ChainStageType.java`
- 创建：`back/src/main/resources/db/migration/V11__chain_stage_hierarchy.sql`
- 修改：`back/src/main/java/git/ggwzz/chain/entity/ChainNode.java`
- 修改：`back/src/main/java/git/ggwzz/chain/vo/NodeVO.java`
- 修改：`back/src/main/java/git/ggwzz/chain/vo/TreeNodeVO.java`
- 修改：`back/src/main/java/git/ggwzz/chain/mapper/ChainNodeMapper.java`
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
- 修改：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
- 修改：`back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`
- 修改：`back/src/test/java/git/ggwzz/chain/mapper/ChainPerformanceMapperTest.java`
- 修改：`back/src/test/resources/schema.sql`
- 修改：`back/src/test/resources/data.sql`
- 修改：`back/src/test/resources/init-data.sql`
- 修改：`back/scripts/seed_data.sql`
- 修改：`back/scripts/seed_complete.sql`
- 修改：`back/src/test/resources/http/chain.http`
- 修改：`back/docs/API-INTERFACE-REGISTRY.md`

### 前端

- 修改：`front/src/types/domain.ts`
- 修改：`front/src/api/chain.ts`
- 修改：`front/src/api/chain.spec.ts`
- 修改：`front/src/api/mock.ts`
- 修改：`front/src/views/chain/ChainManagement.vue`
- 修改：`front/src/views/chain/ChainManagement.spec.ts`
- 修改：`front/src/views/chain/NodeAdjustment.vue`
- 修改：`front/src/views/chain/NodeAdjustment.spec.ts`

---

### 任务 1：建立阶段类型和后端只读响应契约

**文件：**

- 创建：`back/src/main/java/git/ggwzz/chain/enums/ChainStageType.java`
- 修改：`back/src/main/java/git/ggwzz/chain/entity/ChainNode.java`
- 修改：`back/src/main/java/git/ggwzz/chain/vo/NodeVO.java`
- 修改：`back/src/main/java/git/ggwzz/chain/vo/TreeNodeVO.java`
- 修改：`back/src/main/java/git/ggwzz/chain/mapper/ChainNodeMapper.java`
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
- 测试：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`

- [ ] **步骤 1：先写失败测试**

在 `should_build_tree_when_get_chain_tree` 中加入：

```java
up.setStageType(ChainStageType.UPSTREAM);
// 获取 upNode 后
assertEquals(ChainStageType.UPSTREAM, upNode.getStageType());
```

增加普通节点兼容测试：

```java
@Test
void should_expose_null_stage_type_for_ordinary_node() {
    Chain chain = chain(1L, "新能源汽车");
    when(chainMapper.selectById(1L)).thenReturn(chain);
    when(chainNodeMapper.insert(any(ChainNode.class)))
            .thenAnswer(invocation -> {
                ChainNode node = invocation.getArgument(0);
                node.setId(10L);
                return 1;
            });
    CreateNodeRequest request = new CreateNodeRequest();
    request.setChainId(1L);
    request.setNodeName("普通节点");

    NodeVO result = chainService.createNode(request);

    assertNull(result.getStageType());
}
```

- [ ] **步骤 2：运行并确认红灯**

```powershell
cd back
mvn "-Dtest=ChainServiceTest#should_build_tree_when_get_chain_tree+should_expose_null_stage_type_for_ordinary_node" test
```

预期：编译失败，提示 `ChainStageType` 或 `stageType` 方法不存在。

- [ ] **步骤 3：创建枚举**

```java
package git.ggwzz.chain.enums;

/** 产业链固定阶段。 */
public enum ChainStageType {
    UPSTREAM("上游", 1),
    MIDSTREAM("中游", 2),
    DOWNSTREAM("下游", 3);

    private final String defaultNodeName;
    private final int defaultSortOrder;

    ChainStageType(String defaultNodeName, int defaultSortOrder) {
        this.defaultNodeName = defaultNodeName;
        this.defaultSortOrder = defaultSortOrder;
    }

    public String getDefaultNodeName() {
        return defaultNodeName;
    }

    public int getDefaultSortOrder() {
        return defaultSortOrder;
    }
}
```

- [ ] **步骤 4：增加 Entity/VO 字段**

在 `ChainNode`、`NodeVO`、`TreeNodeVO` 中增加：

```java
import git.ggwzz.chain.enums.ChainStageType;

/** 产业链阶段，null 表示普通节点 */
private ChainStageType stageType;
```

不要给 `CreateNodeRequest`、`UpdateNodeRequest` 增加该字段。

- [ ] **步骤 5：补齐自定义 SQL 和映射**

`ChainNodeMapper.selectByChainId` 的列清单加入 `stage_type`：

```java
@Select("SELECT id, chain_id, parent_id, node_name, keywords, node_level, description, "
        + "node_path, sort_order, stage_type, create_time, update_time, is_deleted "
        + "FROM chain_node WHERE chain_id = #{chainId} AND is_deleted = 0 "
        + "ORDER BY sort_order, id")
List<ChainNode> selectByChainId(@Param("chainId") Long chainId);
```

`ChainServiceImpl.buildChildren()` 和 `toNodeVO()` 均增加：

```java
vo.setStageType(node.getStageType());
```

- [ ] **步骤 6：运行绿灯验证**

```powershell
mvn "-Dtest=ChainServiceTest#should_build_tree_when_get_chain_tree+should_expose_null_stage_type_for_ordinary_node" test
```

预期：2 个测试通过。

---

### 任务 2：新建产业链时事务内创建三个阶段节点

**文件：**

- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
- 测试：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`

- [ ] **步骤 1：改写创建产业链测试**

```java
@Test
void should_create_three_stage_nodes_when_create_chain() {
    when(chainMapper.insert(any(Chain.class))).thenAnswer(invocation -> {
        Chain chain = invocation.getArgument(0);
        chain.setId(1L);
        return 1;
    });
    long[] nextId = {10L};
    when(chainNodeMapper.insert(any(ChainNode.class)))
            .thenAnswer(invocation -> {
                ChainNode node = invocation.getArgument(0);
                node.setId(nextId[0]++);
                return 1;
            });
    when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);

    CreateChainRequest request = new CreateChainRequest();
    request.setChainName("新能源汽车");
    ChainVO result = chainService.createChain(request);

    assertEquals(3L, result.getNodeCount());
    ArgumentCaptor<ChainNode> captor =
            ArgumentCaptor.forClass(ChainNode.class);
    verify(chainNodeMapper, times(3)).insert(captor.capture());
    assertEquals(
            List.of(
                    ChainStageType.UPSTREAM,
                    ChainStageType.MIDSTREAM,
                    ChainStageType.DOWNSTREAM),
            captor.getAllValues().stream()
                    .map(ChainNode::getStageType)
                    .toList());
    assertEquals(
            List.of("/10/", "/11/", "/12/"),
            captor.getAllValues().stream()
                    .map(ChainNode::getNodePath)
                    .toList());
}
```

增加 `ArgumentCaptor` 和 `ChainStageType` 导入。

- [ ] **步骤 2：增加空值及失败测试**

```java
@Test
void should_reject_blank_chain_name_in_service() {
    CreateChainRequest request = new CreateChainRequest();
    request.setChainName("   ");
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.createChain(request));
    assertEquals(400, ex.getCode());
    assertEquals("产业链名称不能为空", ex.getMessage());
    verify(chainMapper, never()).insert(any(Chain.class));
}

@Test
void should_fail_when_chain_id_is_not_generated() {
    when(chainMapper.insert(any(Chain.class))).thenReturn(1);
    CreateChainRequest request = new CreateChainRequest();
    request.setChainName("测试链");
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.createChain(request));
    assertEquals(500, ex.getCode());
    assertEquals("产业链创建失败", ex.getMessage());
    verify(chainNodeMapper, never()).insert(any(ChainNode.class));
}

@Test
void should_fail_when_stage_insert_returns_zero() {
    when(chainMapper.insert(any(Chain.class))).thenAnswer(invocation -> {
        Chain chain = invocation.getArgument(0);
        chain.setId(1L);
        return 1;
    });
    when(chainNodeMapper.insert(any(ChainNode.class))).thenReturn(0);
    CreateChainRequest request = new CreateChainRequest();
    request.setChainName("测试链");
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.createChain(request));
    assertEquals("产业链阶段节点初始化失败", ex.getMessage());
}
```

- [ ] **步骤 3：运行并确认红灯**

```powershell
mvn "-Dtest=ChainServiceTest#should_create_three_stage_nodes_when_create_chain+should_reject_blank_chain_name_in_service+should_fail_when_chain_id_is_not_generated+should_fail_when_stage_insert_returns_zero" test
```

预期：当前实现未创建阶段节点，测试失败。

- [ ] **步骤 4：实现事务初始化**

保持 `createChain()` 的 `@Transactional(rollbackFor = Exception.class)`，改为：

```java
public ChainVO createChain(CreateChainRequest request) {
    if (request == null) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
    }
    if (request.getChainName() == null
            || request.getChainName().isBlank()) {
        throw new BusinessException(
                ResultCode.BAD_REQUEST,
                "产业链名称不能为空");
    }
    Chain chain = new Chain();
    chain.setChainName(request.getChainName().trim());
    chain.setDescription(request.getDescription());
    chain.setDeleted(0);
    if (chainMapper.insert(chain) != 1 || chain.getId() == null) {
        throw new BusinessException(
                ResultCode.INTERNAL_ERROR,
                "产业链创建失败");
    }
    createDefaultStageNodes(chain.getId());
    ChainVO result = toChainVO(chain);
    result.setNodeCount((long) ChainStageType.values().length);
    return result;
}
```

增加：

```java
private void createDefaultStageNodes(Long chainId) {
    if (Set.of(ChainStageType.values()).size() != 3) {
        throw new BusinessException(
                ResultCode.INTERNAL_ERROR,
                "产业链阶段节点初始化失败");
    }
    for (ChainStageType type : ChainStageType.values()) {
        ChainNode stage = new ChainNode();
        stage.setChainId(chainId);
        stage.setParentId(null);
        stage.setNodeName(type.getDefaultNodeName());
        stage.setNodeLevel(1);
        stage.setNodePath("/");
        stage.setSortOrder(type.getDefaultSortOrder());
        stage.setStageType(type);
        stage.setDeleted(0);
        if (chainNodeMapper.insert(stage) != 1 || stage.getId() == null) {
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "产业链阶段节点初始化失败");
        }
        stage.setNodePath("/" + stage.getId() + "/");
        if (chainNodeMapper.updateById(stage) != 1) {
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "产业链阶段节点初始化失败");
        }
    }
}
```

异常必须继续抛出，禁止捕获后返回成功，确保 Spring 事务回滚。

- [ ] **步骤 5：运行整个 Service 测试**

```powershell
mvn -Dtest=ChainServiceTest test
```

预期：全部通过；旧测试中创建产业链的 `nodeCount` 期望从 `0` 改为 `3`。

---

### 任务 3：保护阶段节点并补齐 Service 边界判断

**文件：**

- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
- 测试：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
- 测试：`back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`

- [ ] **步骤 1：写移动、删除、改名测试**

```java
@Test
void should_reject_move_when_node_is_stage() {
    ChainNode stage = rootNode(2L, "供应端");
    stage.setStageType(ChainStageType.UPSTREAM);
    when(chainNodeMapper.selectById(2L)).thenReturn(stage);
    MoveNodeRequest request = new MoveNodeRequest();
    request.setNewParentId(null);
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.moveNode(2L, request));
    assertEquals(400, ex.getCode());
    assertEquals("上下游阶段节点不允许移动", ex.getMessage());
    verify(chainNodeMapper, never()).updateSubtree(
            anyLong(), anyLong(), any(), any(),
            any(), any(), any(), any());
}

@Test
void should_reject_delete_stage_before_child_check() {
    ChainNode stage = rootNode(2L, "供应端");
    stage.setStageType(ChainStageType.UPSTREAM);
    when(chainNodeMapper.selectById(2L)).thenReturn(stage);
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.deleteNode(2L));
    assertEquals("上下游阶段节点不允许删除", ex.getMessage());
    verify(chainNodeMapper, never()).countChildren(anyLong());
}

@Test
void should_allow_rename_without_changing_stage_type() {
    ChainNode stage = rootNode(2L, "上游");
    stage.setStageType(ChainStageType.UPSTREAM);
    when(chainNodeMapper.selectById(2L)).thenReturn(stage);
    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setNodeName("原材料端");
    NodeVO result = chainService.updateNode(2L, request);
    assertEquals("原材料端", result.getNodeName());
    assertEquals(ChainStageType.UPSTREAM, result.getStageType());
}
```

- [ ] **步骤 2：写空 ID、空白名称、空排序测试**

```java
@Test
void should_reject_null_node_id() {
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.deleteNode(null));
    assertEquals(400, ex.getCode());
    assertEquals("节点ID不能为空", ex.getMessage());
    verify(chainNodeMapper, never()).selectById(any());
}

@Test
void should_reject_blank_node_name_on_update() {
    UpdateNodeRequest request = new UpdateNodeRequest();
    request.setNodeName("   ");
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.updateNode(2L, request));
    assertEquals("节点名称不能为空", ex.getMessage());
    verify(chainNodeMapper, never()).selectById(any());
}

@Test
void should_reject_empty_node_orders_in_service() {
    SortNodesRequest request = new SortNodesRequest();
    request.setNodeOrders(List.of());
    BusinessException ex = assertThrows(
            BusinessException.class,
            () -> chainService.sortNodes(request));
    assertEquals("排序节点不能为空", ex.getMessage());
}
```

- [ ] **步骤 3：运行并确认红灯**

```powershell
mvn "-Dtest=ChainServiceTest#should_reject_move_when_node_is_stage+should_reject_delete_stage_before_child_check+should_allow_rename_without_changing_stage_type+should_reject_null_node_id+should_reject_blank_node_name_on_update+should_reject_empty_node_orders_in_service" test
```

- [ ] **步骤 4：实现防御方法和调用顺序**

在 `ChainServiceImpl` 增加：

```java
private void requireNodeId(Long nodeId) {
    if (nodeId == null) {
        throw new BusinessException(
                ResultCode.BAD_REQUEST,
                "节点ID不能为空");
    }
}

private void requireOrdinaryNode(ChainNode node, String message) {
    if (node.getStageType() != null) {
        throw new BusinessException(ResultCode.BAD_REQUEST, message);
    }
}

private void requireNodeNameWhenPresent(String nodeName) {
    if (nodeName != null && nodeName.isBlank()) {
        throw new BusinessException(
                ResultCode.BAD_REQUEST,
                "节点名称不能为空");
    }
}
```

调用规则：

```java
// updateNode：查询数据库前
requireNodeId(nodeId);
requireNodeNameWhenPresent(request.getNodeName());

// moveNode：查到节点后、校验 node_path 前
requireOrdinaryNode(node, "上下游阶段节点不允许移动");

// deleteNode：查到节点后、countChildren 前
requireOrdinaryNode(node, "上下游阶段节点不允许删除");
```

`sortNodes()` 在空请求判断后增加：

```java
if (request.getNodeOrders() == null
        || request.getNodeOrders().isEmpty()
        || request.getNodeOrders().stream().anyMatch(Objects::isNull)) {
    throw new BusinessException(
            ResultCode.BAD_REQUEST,
            "排序节点不能为空");
}
```

`createNode()` 防御检查 `chainId` 和空白 `nodeName`；`updateNode()` 的 `stageType` 不进入请求 DTO，因此改名不会清空阶段身份。

- [ ] **步骤 5：Controller 增加空白名称测试**

```java
@Test
@WithMockUser(authorities = "chain:chain:create")
void createChainWithBlankNameShouldReturn400() throws Exception {
    mockMvc.perform(post("/api/chains")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"chainName":"   "}
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    verify(chainService, never())
            .createChain(any(CreateChainRequest.class));
}
```

- [ ] **步骤 6：运行后端定向测试**

```powershell
mvn "-Dtest=ChainServiceTest,ChainControllerTest" test
```

预期：阶段移动/删除被拒绝，改名和同级排序仍通过；所有空值测试通过。

---

### 任务 4：新增 V11 迁移并重构确定性种子数据

**文件：**

- 创建：`back/src/main/resources/db/migration/V11__chain_stage_hierarchy.sql`
- 修改：`back/src/test/resources/schema.sql`
- 修改：`back/src/test/resources/data.sql`
- 修改：`back/src/test/resources/init-data.sql`
- 修改：`back/scripts/seed_data.sql`
- 修改：`back/scripts/seed_complete.sql`
- 测试：`back/src/test/java/git/ggwzz/chain/mapper/ChainPerformanceMapperTest.java`

- [ ] **步骤 1：扩展测试 Schema 和数据**

`chain_node` 增加：

```sql
stage_type VARCHAR(16),
```

建表后增加：

```sql
CREATE UNIQUE INDEX uk_chain_node_stage
    ON chain_node(chain_id, stage_type, is_deleted);
```

测试数据使用真实父子层级：

```sql
INSERT INTO chain_node
    (id, chain_id, parent_id, node_name, keywords, node_level,
     description, node_path, sort_order, stage_type)
VALUES
    (100, 1, NULL, '中游', '中游,核心制造', 1,
     '核心制造阶段', '/100/', 1, 'MIDSTREAM'),
    (101, 1, 100, '动力电池', '动力电池,电芯', 2,
     '动力电池制造', '/100/101/', 1, NULL);
```

保留原细分节点 ID，企业挂接关系继续指向原 ID。

- [ ] **步骤 2：写 Mapper 集成测试**

```java
@Test
void should_load_stage_type_from_database() {
    ChainNode stage = chainNodeMapper.selectByChainId(1L).stream()
            .filter(node -> Long.valueOf(100L).equals(node.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals(ChainStageType.MIDSTREAM, stage.getStageType());
    assertEquals("/100/", stage.getNodePath());
}
```

运行：

```powershell
mvn "-Dtest=ChainPerformanceMapperTest#should_load_stage_type_from_database" test
```

- [ ] **步骤 3：编写 V11 迁移**

核心 SQL：

```sql
ALTER TABLE `chain_node`
    ADD COLUMN `stage_type` VARCHAR(16) NULL
        COMMENT '产业链阶段：UPSTREAM/MIDSTREAM/DOWNSTREAM，NULL 为普通节点'
        AFTER `sort_order`,
    ADD UNIQUE KEY `uk_chain_node_stage`
        (`chain_id`, `stage_type`, `is_deleted`);

INSERT INTO `chain_node`
    (`chain_id`, `parent_id`, `node_name`, `keywords`, `node_level`,
     `description`, `node_path`, `sort_order`, `stage_type`)
SELECT c.id, NULL, s.node_name, s.keywords, 1,
       s.description, '/', s.sort_order, s.stage_type
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
) s
WHERE c.id IN (1, 2)
  AND c.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM `chain_node` n
      WHERE n.chain_id = c.id
        AND n.stage_type = s.stage_type
        AND n.is_deleted = 0
  );

UPDATE `chain_node`
SET node_path = CONCAT('/', id, '/'),
    node_level = 1,
    parent_id = NULL
WHERE stage_type IS NOT NULL
  AND is_deleted = 0;

UPDATE `chain_node` child
JOIN `chain_node` stage
  ON stage.chain_id = child.chain_id
 AND stage.stage_type = CASE
     WHEN child.id IN (101, 102) THEN 'UPSTREAM'
     WHEN child.id IN (201, 202, 203) THEN 'MIDSTREAM'
     WHEN child.id IN (301, 302) THEN 'DOWNSTREAM'
 END
 AND stage.is_deleted = 0
SET child.parent_id = stage.id,
    child.node_level = 2,
    child.node_path = CONCAT(stage.node_path, child.id, '/'),
    child.update_time = CURRENT_TIMESTAMP
WHERE child.chain_id = 1
  AND child.id IN (101, 102, 201, 202, 203, 301, 302)
  AND child.is_deleted = 0;
```

迁移不按关键词处理未知节点。

- [ ] **步骤 4：重构手工种子脚本**

`seed_data.sql` 和 `seed_complete.sql` 使用统一顺序：

1. 按产业链名称/ID查询链。
2. `INSERT ... SELECT ... WHERE NOT EXISTS` 创建三个阶段节点。
3. 回填阶段路径为 `/<阶段ID>/`。
4. 按规格映射创建二级细分节点。
5. 回填细分路径为 `<阶段路径><自身ID>/`。

细分节点映射必须严格使用已批准规格：

```text
半导体：
UPSTREAM 芯片设计、半导体设备、半导体材料、EDA/IP
MIDSTREAM 晶圆制造、封装测试、功率半导体
DOWNSTREAM 暂无

光通信：
UPSTREAM 光芯片、光器件
MIDSTREAM 光模块、光纤光缆
DOWNSTREAM 光通信设备、光通信测试仪器

人工智能：
UPSTREAM AI芯片/算力、AI基础软件
MIDSTREAM 大模型
DOWNSTREAM AI应用、智能机器人、自动驾驶
```

- [ ] **步骤 5：运行数据库相关测试**

```powershell
mvn "-Dtest=ChainPerformanceMapperTest,ChainServiceTest,DashboardMapperTest" test
```

预期：测试数据初始化成功，阶段枚举、路径、统计均通过。

- [ ] **步骤 6：检查 MySQL 5.7 兼容性**

```powershell
rg -n "WITH RECURSIVE|ROW_NUMBER|JSON_TABLE" `
  src/main/resources/db/migration/V11__chain_stage_hierarchy.sql
```

预期：无输出。

---

### 任务 5：同步接口文档和手工验收

**文件：**

- 修改：`back/docs/API-INTERFACE-REGISTRY.md`
- 修改：`back/src/test/resources/http/chain.http`
- 测试：`back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`

- [ ] **步骤 1：验证响应序列化**

在 Controller 测试的 `NodeVO` 中设置：

```java
node.setStageType(ChainStageType.UPSTREAM);
```

断言：

```java
.andExpect(jsonPath("$.data.records[0].stageType")
        .value("UPSTREAM"));
```

- [ ] **步骤 2：更新接口注册表**

明确写入：

```text
POST /api/chains 成功后自动创建三个阶段节点，nodeCount=3。
NodeVO/TreeNodeVO.stageType：
UPSTREAM、MIDSTREAM、DOWNSTREAM；null 表示普通节点。
阶段移动：400 / 上下游阶段节点不允许移动。
阶段单独删除：400 / 上下游阶段节点不允许删除。
删除整条产业链仍会逻辑删除阶段节点。
```

- [ ] **步骤 3：扩展 chain.http**

创建产业链后查询树：

```http
GET http://localhost:8080/api/chains/{{chainId}}/tree
Authorization: Bearer {{token}}

> {%
    client.test("新链包含三个阶段节点", function () {
        const nodes = response.body.data.children;
        client.assert(nodes.length === 3);
        client.assert(nodes[0].stageType === "UPSTREAM");
        client.assert(nodes[1].stageType === "MIDSTREAM");
        client.assert(nodes[2].stageType === "DOWNSTREAM");
    });
    client.global.set("upstreamStageId", response.body.data.children[0].id);
%}
```

增加移动、单独删除该阶段节点的请求，均断言 400 和固定消息。清理时只删除整条测试产业链。

- [ ] **步骤 4：运行后端定向回归**

```powershell
mvn "-Dtest=ChainServiceTest,ChainControllerTest,ChainPerformanceMapperTest" test
```

---

### 任务 6：扩展前端类型、真实 API 和 Mock

**文件：**

- 修改：`front/src/types/domain.ts`
- 修改：`front/src/api/chain.ts`
- 修改：`front/src/api/chain.spec.ts`
- 修改：`front/src/api/mock.ts`

- [ ] **步骤 1：先写 API/Mock 失败测试**

真实响应测试增加：

```typescript
expect(mappedNode.stageType).toBe("UPSTREAM");
expect(mappedTree[0].stageType).toBe("UPSTREAM");
```

Mock 增加：

```typescript
it("creates and protects three stage nodes", async () => {
  const chain = await createChain({ name: "测试链" });
  const tree = await fetchNodeTree(chain.id);
  expect(tree.map((node) => node.stageType)).toEqual([
    "UPSTREAM",
    "MIDSTREAM",
    "DOWNSTREAM",
  ]);
  await expect(
    moveNode(tree[0].id, { newParentId: null }),
  ).rejects.toThrow("上下游阶段节点不允许移动");
  await expect(deleteNode(tree[0].id)).rejects.toThrow(
    "上下游阶段节点不允许删除",
  );
});
```

- [ ] **步骤 2：运行红灯**

```powershell
cd front
& 'C:\Users\1\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' `
  '.\node_modules\vitest\vitest.mjs' run src/api/chain.spec.ts
```

- [ ] **步骤 3：增加类型和真实映射**

`domain.ts`：

```typescript
export type ChainStageType =
  | "UPSTREAM"
  | "MIDSTREAM"
  | "DOWNSTREAM";

export interface ChainNode {
  // 原字段保持
  stageType?: ChainStageType | null;
}
```

`BackendTreeNodeVO`、`BackendNodeVO` 增加同字段；`mapBackendNode()`、`mapBackendTreeNode()` 增加：

```typescript
stageType: node.stageType ?? null,
```

- [ ] **步骤 4：实现 Mock 阶段工厂**

```typescript
const DEFAULT_CHAIN_STAGES = [
  { stageType: "UPSTREAM", name: "上游", sortNo: 1 },
  { stageType: "MIDSTREAM", name: "中游", sortNo: 2 },
  { stageType: "DOWNSTREAM", name: "下游", sortNo: 3 },
] as const;

function createDefaultStageNodes(chainId: number): ChainNode[] {
  return DEFAULT_CHAIN_STAGES.map((stage) => {
    const id = nextNodeId++;
    return {
      id,
      chainId,
      parentId: null,
      name: stage.name,
      stageType: stage.stageType,
      levelNo: 1,
      nodePath: `/${id}/`,
      sortNo: stage.sortNo,
      status: "ENABLED",
      directEnterpriseCount: 0,
      subtreeEnterpriseCount: 0,
      children: [],
    };
  });
}
```

`createChain()`：

```typescript
nodeTrees[chain.id] = createDefaultStageNodes(chain.id);
chain.nodeCount = 3;
```

`moveNode()` 在资源存在后增加：

```typescript
if (location.node.stageType) {
  return rejectMock("上下游阶段节点不允许移动");
}
```

`deleteNode()` 在子节点判断前增加：

```typescript
if (node.stageType) {
  return rejectMock("上下游阶段节点不允许删除");
}
```

`sortNodes()` 不限制阶段节点。

- [ ] **步骤 5：运行 API 测试**

```powershell
& 'C:\Users\1\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' `
  '.\node_modules\vitest\vitest.mjs' run src/api/chain.spec.ts
```

预期：旧响应缺少 `stageType` 时映射为 `null`；Mock 创建和保护测试通过。

---

### 任务 7：实现前端阶段标签、删除保护和移动保护

**文件：**

- 修改：`front/src/views/chain/ChainManagement.vue`
- 修改：`front/src/views/chain/ChainManagement.spec.ts`
- 修改：`front/src/views/chain/NodeAdjustment.vue`
- 修改：`front/src/views/chain/NodeAdjustment.spec.ts`

- [ ] **步骤 1：先写产业链树失败测试**

测试树加入：

```typescript
{
  id: 1,
  name: "原材料端",
  stageType: "UPSTREAM",
  children: [],
}
```

断言：

```typescript
expect(wrapper.text()).toContain("上游阶段");
expect(
  wrapper.get('[data-test="delete-node-1"]').attributes("disabled"),
).toBeDefined();
```

- [ ] **步骤 2：实现标签和删除禁用**

`ChainManagement.vue`：

```typescript
const STAGE_LABELS: Record<ChainStageType, string> = {
  UPSTREAM: "上游阶段",
  MIDSTREAM: "中游阶段",
  DOWNSTREAM: "下游阶段",
};

function getStageLabel(node: ChainNode): string {
  return node.stageType ? STAGE_LABELS[node.stageType] : "普通节点";
}
```

`removeNode()` 第一段增加：

```typescript
if (node.stageType) {
  ElMessage.warning("上下游阶段节点不允许删除");
  return;
}
```

表格增加类型列：

```vue
<el-table-column label="节点类型" min-width="108">
  <template #default="{ row }">
    <el-tag v-if="row.stageType" effect="plain">
      {{ getStageLabel(row) }}
    </el-tag>
    <span v-else>普通节点</span>
  </template>
</el-table-column>
```

删除按钮增加：

```vue
:data-test="`delete-node-${row.id}`"
:disabled="Boolean(row.stageType)"
```

Tooltip 对阶段节点显示“上下游阶段节点不允许删除”。

- [ ] **步骤 3：先写节点调整失败测试**

```typescript
it("disables stage movement but keeps sorting", async () => {
  const wrapper = await mountView();
  await selectTreeNode(wrapper, 1);
  expect(
    wrapper.get('[data-test="target-parent-select"]')
      .attributes("disabled"),
  ).toBeDefined();
  expect(
    wrapper.get('[data-test="preview-move"]')
      .attributes("disabled"),
  ).toBeDefined();
  expect(wrapper.text()).toContain(
    "阶段节点可以改名和排序，但不能移动或删除",
  );
});
```

另保留/增加普通节点目标父节点列表包含阶段节点的断言。

- [ ] **步骤 4：实现节点调整保护**

`NodeAdjustment.vue` 增加：

```typescript
const selectedIsStage = computed(
  () => Boolean(selectedEntry.value?.node.stageType),
);
```

`handleMove()` 开头增加：

```typescript
if (selectedIsStage.value) {
  ElMessage.warning("上下游阶段节点不允许移动");
  return;
}
```

移动区域增加：

```vue
<el-alert
  v-if="selectedIsStage"
  :closable="false"
  show-icon
  title="阶段节点可以改名和排序，但不能移动或删除"
  type="info"
/>
```

目标父节点、目标位置、预览按钮的禁用条件加入 `selectedIsStage`。不要修改同级排序按钮和 `saveSort()` 条件。

- [ ] **步骤 5：运行两个页面测试**

```powershell
& 'C:\Users\1\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' `
  '.\node_modules\vitest\vitest.mjs' run `
  src/views/chain/ChainManagement.spec.ts `
  src/views/chain/NodeAdjustment.spec.ts
```

预期：阶段标签、删除禁用、移动禁用、阶段排序和普通节点移动测试全部通过。

---

### 任务 8：全量验证和交付检查

**文件：** 检查以上全部文件，不执行 Git 写操作。

- [ ] **步骤 1：后端完整测试**

```powershell
cd back
mvn test
mvn package
```

预期：两条命令均 `BUILD SUCCESS`，Failures/Errors 为 0。

- [ ] **步骤 2：前端完整测试和构建**

```powershell
cd front
& 'C:\Users\1\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' `
  '.\node_modules\vitest\vitest.mjs' run
$env:PATH = 'C:\Users\1\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;' + $env:PATH
npm run build
```

预期：Vitest 全部通过；`vue-tsc` 和 Vite 构建无错误。

- [ ] **步骤 3：逐项验收**

```text
[ ] 新链自动生成上游、中游、下游
[ ] createChain 响应 nodeCount=3
[ ] 阶段节点允许改名和同级排序
[ ] 阶段节点不能移动和单独删除
[ ] 普通节点可移动到阶段节点下
[ ] 删除整条产业链仍正常
[ ] 已知演示数据完成确定性迁移
[ ] 未知数据未被自动分类
[ ] 空请求、空白名称、空 ID、空排序列表均有稳定响应
[ ] NodeVO、TreeNodeVO、前端和 Mock 均携带 stageType
```

- [ ] **步骤 4：只读检查**

在两个仓库分别运行：

```powershell
git diff --check
git status --short
```

预期：无空白错误；区分本功能文件和原有未跟踪文件。不要执行 `git add`、`git commit` 或 `git push`。

