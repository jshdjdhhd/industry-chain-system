# 节点移动路径一致性修复实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复部分产业链把同级节点误判为后代节点的问题，修复现有错误 `node_path` 数据，并阻止非法路径触发整条产业链的批量误更新。

**架构：** `parent_id` 是树结构和环路判断的权威数据，`node_path` 是用于子树批量查询和更新的派生字段。后端移动节点前先校验物化路径格式，再沿目标父节点的 `parent_id` 祖先链判断是否形成环；种子脚本和一次性修复脚本统一生成 `/<祖先ID>/.../<自身ID>/` 形式的路径。

**技术栈：** Java 21、Spring Boot 3.5.15、MyBatis-Plus 3.5.7、MySQL 5.7、JUnit 5、Mockito、H2、IDEA HTTP Client。

---

## 一、根因与修复边界

### 已确认根因

- 前端节点树与合法目标父节点列表根据后端返回的嵌套树生成，嵌套树由 `parent_id` 组装。
- 后端 `moveNode()` 当前通过 `parent.nodePath.startsWith(node.nodePath)` 判断目标父节点是否属于源节点子树。
- `scripts/seed_data.sql`、`scripts/seed_complete.sql` 将多条一级节点的 `node_path` 写成相同的 `/`。
- 当源节点和目标同级节点的路径都是 `/` 时，`"/".startsWith("/")` 返回 `true`，后端错误抛出“节点不能移动到自己的子节点下”。
- 不能只删除 `startsWith` 校验：`updateSubtree()` 使用 `node_path LIKE #{oldPathPattern}`；当 `oldPath="/"` 时，`/%` 会匹配同一产业链的全部规范路径。

### 必须保持的树形不变量

对每条未逻辑删除的 `chain_node`：

1. 一级节点：`parent_id IS NULL`、`node_level = 1`、`node_path = CONCAT('/', id, '/')`。
2. 非一级节点：`node_path = CONCAT(parent.node_path, id, '/')`。
3. 非一级节点：`node_level = parent.node_level + 1`。
4. 同一产业链内每个节点的 `node_path` 唯一。
5. 祖先链不能出现孤儿节点、跨产业链父节点或环。

### 本次修改范围

- 修正后端移动节点的路径完整性保护和环路判断。
- 修正两份直接写表的种子脚本。
- 增加 MySQL 5.7 可执行的审计脚本和单轮修复脚本。
- 修复当前数据库中由错误种子脚本产生的路径。
- 增加 Service 单元测试、真实 HTTP 联调步骤和接口登记说明。

### 不纳入本次修改

- 不修改前端页面、前端 API 路径或请求字段。
- 不修改 `PUT /api/chains/nodes/{nodeId}/move` 的成功响应结构。
- 不增加启动时自动修复逻辑，不在正常请求中静默修复整条产业链。
- 不使用 MySQL 8 递归 CTE、存储过程或数据库外键。
- 不执行 `git push`、创建 PR 或其他 Git 上传操作。

## 二、文件变更清单

### 后端代码与测试

- 修改：`src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
  - 校验源节点和目标祖先链的 `node_path`。
  - 使用 `parent_id` 祖先链判断目标父节点是否位于源节点子树。
  - 非法路径返回明确的 500 数据完整性错误，且不得调用 `updateSubtree()`。
- 修改：`src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
  - 覆盖同级移动、真实后代、非法 `/` 路径、孤儿祖先和路径环。

### 数据脚本

- 创建：`scripts/audit_chain_node_paths.sql`
  - 找出根路径错误、父节点缺失、路径拼接错误、层级错误和重复路径。
- 创建：`scripts/repair_chain_node_paths.sql`
  - 每次执行先修复全部一级节点，再向下修复一层非一级节点。
  - 重复执行直到 `repaired_descendants = 0`，兼容任意现有树深度。
- 修改：`scripts/seed_data.sql`
  - 半导体产业链节点插入后，将一级节点路径回填为 `/<自身ID>/`。
- 修改：`scripts/seed_complete.sql`
  - 光通信、人工智能产业链节点插入后，将一级节点路径回填为 `/<自身ID>/`。

### 契约与联调

- 修改：`docs/API-INTERFACE-REGISTRY.md`
  - 记录移动接口的数据完整性前置条件和错误语义。
- 修改：`src/test/resources/http/chain.http`
  - 增加两个规范一级节点之间的移动验证。

## 三、实施任务

### 任务 1：用失败测试锁定移动校验语义

**文件：**

- 修改：`src/test/java/git/ggwzz/chain/service/ChainServiceTest.java:824-938`
- 修改：`src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java:300-357`

- [ ] **步骤 1：增加非法根路径回归测试**

在 `ChainServiceTest` 的“节点移动 / 排序”分组中增加：

```java
@Test
void should_reject_noncanonical_source_path_before_bulk_move() {
    ChainNode node = movableNode(3L, 1L, null, 1, "/");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);

    MoveNodeRequest request = new MoveNodeRequest();
    request.setNewParentId(5L);

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> chainService.moveNode(3L, request));

    assertEquals(500, exception.getCode());
    assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
    verify(chainNodeMapper, never()).updateSubtree(
            anyLong(),
            anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
}
```

补充静态导入：

```java
import static org.mockito.ArgumentMatchers.any;
```

该导入当前文件已经存在，不重复添加。

- [ ] **步骤 2：增加规范同级节点移动测试**

```java
@Test
void should_allow_moving_root_node_under_canonical_sibling() {
    ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
    ChainNode sibling = movableNode(5L, 1L, null, 1, "/5/");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);
    when(chainNodeMapper.selectById(5L)).thenReturn(sibling);
    when(chainNodeMapper.updateSubtree(
            1L, 3L, 5L, 0,
            "/3/", "/3/%", "/5/3/", 1))
            .thenReturn(1);

    MoveNodeRequest request = new MoveNodeRequest();
    request.setNewParentId(5L);
    request.setSortOrder(0);

    NodeVO result = chainService.moveNode(3L, request);

    assertEquals(5L, result.getParentId());
    assertEquals("/5/3/", result.getNodePath());
    assertEquals(2, result.getNodeLevel());
}
```

- [ ] **步骤 3：把现有后代测试改为依据 `parent_id` 祖先链**

将 `should_throw_bad_request_when_move_node_to_descendant` 中的目标节点补充直接父节点：

```java
descendant.setParentId(3L);
```

保留规范路径 `/2/3/` 与 `/2/3/4/`，并增加消息断言：

```java
assertEquals("节点不能移动到自己的子节点下", ex.getMessage());
verify(chainNodeMapper, never()).updateSubtree(
        anyLong(),
        anyLong(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any());
```

- [ ] **步骤 4：增加孤儿祖先测试**

```java
@Test
void should_reject_move_when_target_ancestor_is_missing() {
    ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
    ChainNode target = movableNode(5L, 1L, 99L, 2, "/99/5/");
    when(chainNodeMapper.selectById(3L)).thenReturn(node);
    when(chainNodeMapper.selectById(5L)).thenReturn(target);
    when(chainNodeMapper.selectById(99L)).thenReturn(null);

    MoveNodeRequest request = new MoveNodeRequest();
    request.setNewParentId(5L);

    BusinessException exception = assertThrows(
            BusinessException.class,
            () -> chainService.moveNode(3L, request));

    assertEquals(500, exception.getCode());
    assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
    verify(chainNodeMapper, never()).updateSubtree(
            anyLong(),
            anyLong(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
}
```

- [ ] **步骤 5：运行测试并确认按预期失败**

运行：

```powershell
mvn -Dtest=ChainServiceTest test
```

预期：

- `should_reject_noncanonical_source_path_before_bulk_move` 失败，当前实现错误抛出“节点不能移动到自己的子节点下”或继续执行。
- `should_throw_bad_request_when_move_node_to_descendant` 在改为 `parent_id` 语义后仍依赖旧路径前缀实现。
- `should_reject_move_when_target_ancestor_is_missing` 失败，当前实现不会读取祖先节点。

- [ ] **步骤 6：实现规范路径校验**

在 `ChainServiceImpl` 增加导入：

```java
import java.util.HashSet;
import java.util.Set;
```

在类内增加：

```java
private String requireCanonicalNodePath(ChainNode node) {
    String nodePath = node.getNodePath();
    String expectedSuffix = "/" + node.getId() + "/";
    if (nodePath == null
            || "/".equals(nodePath)
            || !nodePath.startsWith("/")
            || !nodePath.endsWith(expectedSuffix)) {
        log.error(
                "产业链节点路径异常: nodeId={}, chainId={}, parentId={}, nodePath={}",
                node.getId(),
                node.getChainId(),
                node.getParentId(),
                nodePath);
        throw new BusinessException(
                ResultCode.INTERNAL_ERROR,
                "节点层级数据异常，请联系管理员");
    }
    return nodePath;
}
```

- [ ] **步骤 7：实现基于 `parent_id` 的祖先链校验**

在 `ChainServiceImpl` 增加：

```java
private void validateMoveTarget(ChainNode node, ChainNode targetParent) {
    Set<Long> visited = new HashSet<>();
    ChainNode current = targetParent;
    while (current != null) {
        requireCanonicalNodePath(current);
        if (!Objects.equals(node.getChainId(), current.getChainId())) {
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "节点层级数据异常，请联系管理员");
        }
        if (!visited.add(current.getId())) {
            log.error(
                    "产业链节点祖先链存在环: chainId={}, nodeId={}",
                    node.getChainId(),
                    current.getId());
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "节点层级数据异常，请联系管理员");
        }
        if (Objects.equals(node.getId(), current.getId())) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "节点不能移动到自己的子节点下");
        }
        Long childId = current.getId();
        Long parentId = current.getParentId();
        if (parentId == null) {
            return;
        }
        current = chainNodeMapper.selectById(parentId);
        if (current == null) {
            log.error(
                    "产业链节点祖先不存在: chainId={}, childId={}, missingParentId={}",
                    node.getChainId(),
                    childId,
                    parentId);
            throw new BusinessException(
                    ResultCode.INTERNAL_ERROR,
                    "节点层级数据异常，请联系管理员");
        }
    }
}
```

- [ ] **步骤 8：接入 `moveNode()`**

在取得源节点后立即校验路径：

```java
String oldPath = requireCanonicalNodePath(node);
```

删除原来的：

```java
if (parent.getNodePath().startsWith(oldPath)) {
    throw new BusinessException(
            ResultCode.BAD_REQUEST,
            "节点不能移动到自己的子节点下");
}
```

替换为：

```java
validateMoveTarget(node, parent);
parentPath = requireCanonicalNodePath(parent);
```

保留以下保护顺序：

1. 请求和源节点存在性。
2. 禁止移动到自身。
3. 校验源节点规范路径。
4. 目标父节点存在且属于同一产业链。
5. 校验目标祖先链并判断是否形成环。
6. 最后才调用 `updateSubtree()`。

- [ ] **步骤 9：运行定向测试**

运行：

```powershell
mvn -Dtest=ChainServiceTest test
```

预期：`ChainServiceTest` 全部通过，输出 `Failures: 0, Errors: 0`。

- [ ] **步骤 10：提交任务 1**

```powershell
git add -- `
  src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java `
  src/test/java/git/ggwzz/chain/service/ChainServiceTest.java
git commit -m "fix: 修正节点移动环路校验"
```

### 任务 2：修正种子数据并提供路径审计、修复脚本

**文件：**

- 创建：`scripts/audit_chain_node_paths.sql`
- 创建：`scripts/repair_chain_node_paths.sql`
- 修改：`scripts/seed_data.sql:10-17`
- 修改：`scripts/seed_complete.sql:22-37`

- [ ] **步骤 1：创建路径审计脚本**

`scripts/audit_chain_node_paths.sql` 内容：

```sql
-- 查找 parent_id、node_path、node_level 不一致的未删除节点。
SELECT
    child.id,
    child.chain_id,
    child.parent_id,
    child.node_level,
    child.node_path,
    parent.node_level AS parent_level,
    parent.node_path AS parent_path
FROM chain_node child
LEFT JOIN chain_node parent
    ON parent.id = child.parent_id
   AND parent.is_deleted = 0
WHERE child.is_deleted = 0
  AND (
      child.node_path IS NULL
      OR child.node_path = '/'
      OR (
          child.parent_id IS NULL
          AND (
              child.node_level <> 1
              OR child.node_path <> CONCAT('/', child.id, '/')
          )
      )
      OR (
          child.parent_id IS NOT NULL
          AND (
              parent.id IS NULL
              OR parent.chain_id <> child.chain_id
              OR child.node_level <> parent.node_level + 1
              OR child.node_path <> CONCAT(parent.node_path, child.id, '/')
          )
      )
  )
ORDER BY child.chain_id, child.node_level, child.id;

-- 同一产业链内的物化路径必须唯一。
SELECT
    chain_id,
    node_path,
    COUNT(*) AS duplicate_count
FROM chain_node
WHERE is_deleted = 0
GROUP BY chain_id, node_path
HAVING COUNT(*) > 1
ORDER BY chain_id, node_path;
```

- [ ] **步骤 2：在目标数据库执行审计**

通过当前数据库客户端执行：

```sql
SOURCE scripts/audit_chain_node_paths.sql;
```

预期：

- 第一条查询至少返回 `node_path='/'` 的种子节点。
- 第二条查询返回同一产业链中重复使用 `/` 的记录。

记录查询结果中的 `chain_id`、异常行数和最大 `node_level`，修复后用同一脚本对比。

- [ ] **步骤 3：创建单轮路径修复脚本**

`scripts/repair_chain_node_paths.sql` 内容：

```sql
START TRANSACTION;

-- 每次都先保证一级节点路径规范。
UPDATE chain_node
SET node_path = CONCAT('/', id, '/'),
    node_level = 1,
    update_time = CURRENT_TIMESTAMP
WHERE is_deleted = 0
  AND parent_id IS NULL
  AND (
      node_path IS NULL
      OR node_path <> CONCAT('/', id, '/')
      OR node_level <> 1
  );

-- 单次向下修复一层；重复执行即可覆盖任意深度，不使用递归 CTE 或存储过程。
UPDATE chain_node child
JOIN chain_node parent
  ON parent.id = child.parent_id
 AND parent.is_deleted = 0
SET child.node_path = CONCAT(parent.node_path, child.id, '/'),
    child.node_level = parent.node_level + 1,
    child.update_time = CURRENT_TIMESTAMP
WHERE child.is_deleted = 0
  AND parent.chain_id = child.chain_id
  AND parent.node_path <> '/'
  AND (
      child.node_path IS NULL
      OR child.node_path <> CONCAT(parent.node_path, child.id, '/')
      OR child.node_level <> parent.node_level + 1
  );

SELECT ROW_COUNT() AS repaired_descendants;

COMMIT;
```

该脚本不删除节点、不修改 `parent_id`、不修改 `sort_order`。孤儿节点、跨产业链父节点和 `parent_id` 环不会被静默改写，而是继续由审计脚本报告。

- [ ] **步骤 4：修正 `seed_data.sql`**

在半导体节点插入语句之后增加：

```sql
UPDATE chain_node
SET node_path = CONCAT('/', id, '/'),
    node_level = 1
WHERE chain_id = 2
  AND parent_id IS NULL
  AND node_path = '/';
```

- [ ] **步骤 5：修正 `seed_complete.sql`**

在光通信和人工智能节点插入语句之后增加：

```sql
UPDATE chain_node
SET node_path = CONCAT('/', id, '/'),
    node_level = 1
WHERE chain_id IN (3, 4)
  AND parent_id IS NULL
  AND node_path = '/';
```

- [ ] **步骤 6：静态检查种子脚本**

运行：

```powershell
rg -n "\(.*NULL.*'/'" scripts/seed_data.sql scripts/seed_complete.sql
```

允许插入语句暂时使用 `/`，但每个插入块后必须紧邻对应的 `UPDATE ... CONCAT('/', id, '/')` 回填。不得存在没有回填语句保护的节点插入块。

- [ ] **步骤 7：提交任务 2**

```powershell
git add -- `
  scripts/audit_chain_node_paths.sql `
  scripts/repair_chain_node_paths.sql `
  scripts/seed_data.sql `
  scripts/seed_complete.sql
git commit -m "fix: 修复产业链节点种子路径"
```

### 任务 3：修复当前数据库并验证数据不变量

**文件：**

- 使用：`scripts/audit_chain_node_paths.sql`
- 使用：`scripts/repair_chain_node_paths.sql`

- [ ] **步骤 1：备份受影响节点**

在数据库客户端执行：

```sql
CREATE TABLE chain_node_path_backup_20260702 AS
SELECT
    id,
    chain_id,
    parent_id,
    node_level,
    node_path,
    is_deleted,
    update_time
FROM chain_node;
```

执行后验证：

```sql
SELECT COUNT(*) AS backup_count
FROM chain_node_path_backup_20260702;

SELECT COUNT(*) AS source_count
FROM chain_node;
```

预期：`backup_count = source_count`。

- [ ] **步骤 2：重复执行单轮修复脚本**

先取得安全执行上限：

```sql
SELECT COUNT(*) AS active_node_count
FROM chain_node
WHERE is_deleted = 0;
```

每次执行：

```sql
SOURCE scripts/repair_chain_node_paths.sql;
```

读取输出 `repaired_descendants`：

- 大于 `0`：再次执行同一脚本。
- 等于 `0`：停止执行。

正常树每轮至少向下推进一层，因此执行次数不会超过 `active_node_count`。达到该次数后
`repaired_descendants` 仍不为 `0`，说明可能存在 `parent_id` 环；立即停止并用备份恢复：

```sql
UPDATE chain_node node
JOIN chain_node_path_backup_20260702 backup
  ON backup.id = node.id
SET node.node_path = backup.node_path,
    node.node_level = backup.node_level,
    node.update_time = backup.update_time;
```

恢复后暂停任务，不继续执行移动接口验证。

- [ ] **步骤 3：重新执行审计脚本**

```sql
SOURCE scripts/audit_chain_node_paths.sql;
```

预期：两条查询都返回 `0` 行。

若仍返回孤儿节点、跨产业链父节点或环，不修改这些记录；保存查询结果并暂停后续移动验证，由负责人确认正确的 `parent_id`。

- [ ] **步骤 4：核对业务数据未被扩大修改**

执行：

```sql
SELECT
    COUNT(*) AS node_count,
    SUM(parent_id IS NULL) AS root_count,
    SUM(is_deleted = 1) AS deleted_count
FROM chain_node_path_backup_20260702;

SELECT
    COUNT(*) AS node_count,
    SUM(parent_id IS NULL) AS root_count,
    SUM(is_deleted = 1) AS deleted_count
FROM chain_node;

SELECT
    b.id,
    b.parent_id AS old_parent_id,
    n.parent_id AS new_parent_id,
    b.node_path AS old_node_path,
    n.node_path AS new_node_path
FROM chain_node_path_backup_20260702 b
JOIN chain_node n ON n.id = b.id
WHERE NOT (b.parent_id <=> n.parent_id)
ORDER BY b.id;
```

预期：

- 节点总数、一级节点数和逻辑删除数量与修复前一致。
- 第二条查询返回 `0` 行，证明修复没有改变父子关系。

### 任务 4：同步接口说明并增加真实联调回归

**文件：**

- 修改：`docs/API-INTERFACE-REGISTRY.md:69`
- 修改：`src/test/resources/http/chain.http`

- [ ] **步骤 1：更新接口登记表**

在节点移动接口约束中补充：

```markdown
- 环路判断以同一产业链内的 `parent_id` 祖先链为准。
- `node_path` 必须符合 `/<祖先ID>/.../<自身ID>/`；路径异常返回
  `500 / 节点层级数据异常，请联系管理员`，且不执行子树批量更新。
```

- [ ] **步骤 2：在 `chain.http` 创建两个规范一级节点**

增加：

```http
### 创建同级移动回归节点 A
POST http://localhost:8080/api/chains/{{chainId}}/nodes
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "parentId": null,
  "nodeName": "同级移动回归-A",
  "sortOrder": 90
}

> {%
client.global.set("siblingMoveA", response.body.data.id);
%}

### 创建同级移动回归节点 B
POST http://localhost:8080/api/chains/{{chainId}}/nodes
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "parentId": null,
  "nodeName": "同级移动回归-B",
  "sortOrder": 91
}

> {%
client.global.set("siblingMoveB", response.body.data.id);
%}
```

- [ ] **步骤 3：增加同级节点移动回归请求**

```http
### 将一级节点 A 移动到同级节点 B 下
PUT http://localhost:8080/api/chains/nodes/{{siblingMoveA}}/move
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "newParentId": {{siblingMoveB}},
  "sortOrder": 0
}

> {%
client.test("同级节点可以成为新的父节点", function () {
  client.assert(response.status === 200);
  client.assert(response.body.data.parentId === Number(client.global.get("siblingMoveB")));
  client.assert(response.body.data.nodeLevel === 2);
});
%}
```

- [ ] **步骤 4：增加回归节点清理请求**

移动后必须先删除子节点 A，再删除父节点 B：

```http
### 清理同级移动回归节点 A
DELETE http://localhost:8080/api/chains/nodes/{{siblingMoveA}}
Authorization: Bearer {{token}}

### 清理同级移动回归节点 B
DELETE http://localhost:8080/api/chains/nodes/{{siblingMoveB}}
Authorization: Bearer {{token}}
```

- [ ] **步骤 5：按顺序执行真实联调**

执行顺序：

1. 登录并设置 `token`。
2. 创建临时产业链并设置 `chainId`。
3. 创建回归节点 A。
4. 创建回归节点 B。
5. 执行节点移动。
6. 查询产业链树，确认 A 位于 B 的 `children` 中。
7. 删除回归节点 A。
8. 删除回归节点 B。
9. 执行 `chain.http` 其余已有清理步骤。

预期：移动接口返回 `200`，不再出现“节点不能移动到自己的子节点下”。

- [ ] **步骤 6：提交任务 4**

```powershell
git add -- `
  docs/API-INTERFACE-REGISTRY.md `
  src/test/resources/http/chain.http
git commit -m "docs: 补充节点移动完整性验证"
```

### 任务 5：完整验证

**文件：**

- 验证本计划涉及的全部文件。

- [ ] **步骤 1：运行节点移动定向测试**

```powershell
mvn -Dtest=ChainServiceTest test
```

预期：`Failures: 0, Errors: 0`。

- [ ] **步骤 2：运行 Mapper 集成测试**

```powershell
mvn -Dtest=ChainPerformanceMapperTest test
```

预期：`Failures: 0, Errors: 0`，现有 `updateSubtree` 子树更新测试保持通过。

- [ ] **步骤 3：运行完整测试**

```powershell
mvn test
```

预期：`BUILD SUCCESS`，全部测试 `Failures: 0, Errors: 0`。

- [ ] **步骤 4：运行打包**

```powershell
mvn package
```

预期：`BUILD SUCCESS`，生成 `target/industry-chain-1.0.0.jar`。

- [ ] **步骤 5：检查格式和变更边界**

```powershell
git diff --check
git status --short
```

预期：

- `git diff --check` 无输出。
- 只出现本计划列出的文件和执行前已存在的未跟踪文件。
- 不暂存或提交 `logs/`、本地配置、数据库备份数据或无关文档。

- [ ] **步骤 6：确认没有 Git 上传操作**

```powershell
git log --oneline -4
git status -sb
```

仅核对本地提交和分支状态，不执行 `git push`、`gh pr create` 或远端合并。

## 四、完成定义

- 规范路径的同级节点可以移动为父子关系。
- 真实后代节点仍不能成为新父节点。
- `node_path='/'`、孤儿祖先、跨产业链祖先或祖先环不会进入 `updateSubtree()`。
- 审计脚本在修复后的数据库返回 `0` 条异常和 `0` 组重复路径。
- 两份种子脚本不再留下 `/` 形式的持久化节点路径。
- `chain.http` 同级移动回归通过。
- 定向测试、完整测试、打包和 `git diff --check` 全部通过。
- 前端文件保持不变。
- 未执行任何 Git 上传操作。
