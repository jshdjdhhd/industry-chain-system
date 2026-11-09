# 产业链节点树规则 Module 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将产业链节点树的 node_path、移动、排序、路径展示和前端预览 / Mock 规则集中到更 deep 的 module，减少后端、前端页面和 Mock adapter 中重复理解同一套树规则。

**架构：** 后端新增 `ChainNodeTreeRules` 作为纯规则 module：caller 传入当前节点、目标父节点 loader 和路径节点集合，module 返回可执行的 move plan 或展示路径。前端新增 `src/domain/nodeTreeRules.ts`，由 `NodeAdjustment` 页面 helper 和 `mock.ts` 共用，页面只表达用户意图，Mock adapter 不再复制移动、祖先、同级排序规则。

**技术栈：** JDK 21、Spring Boot 3.5.15、MyBatis-Plus 3.5.7、JUnit 5、Mockito、Vue 3、TypeScript、Pinia、Vitest、Vite

---

## 执行约束

- 用户偏好：默认不要执行 `git add`、`git commit`、`git push`。本计划中的「检查点」只列出建议检查的文件，不执行提交。
- 后端修改只发生在 `groupproject-back-commit/back`。
- 前端修改只发生在 `groupproject-front/front`。
- 不改变数据库结构，不修改 MySQL 5.7 的 `parent_id + node_path` 决策。
- 不改变现有 HTTP contract；前端页面和后端 Controller 路径保持不变。
- 不引入新的 TypeScript 或 Java 依赖。
- 继续使用 codebase-design vocabulary：module、interface、implementation、seam、adapter、depth、leverage、locality。

## 文件结构

### 后端

- 创建 `groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainNodeTreeRules.java`  
  职责：集中产业链节点树规则 implementation。它的 interface 是少量方法：`planMove(...)`、`requireOrdinaryNode(...)`、`parseNodePath(...)`、`buildDisplayPath(...)`。它不直接依赖 Mapper；需要加载父节点或祖先时由 caller 传入 `Function<Long, ChainNode>`，保持测试 surface 小。

- 创建 `groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/impl/ChainNodeTreeRulesTest.java`  
  职责：直接穿过新 module 的 interface 测试 node_path canonical 校验、移动计划、祖先环检测、展示路径还原。

- 修改 `groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`  
  职责：保留产业链管理 orchestration；将阶段节点保护、canonical path 校验、目标父节点合法性和 move plan 计算委托给 `ChainNodeTreeRules`。

- 修改 `groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainNodeQueryServiceImpl.java`  
  职责：继续负责节点查询 orchestration；将 node_path 解析和展示路径拼接委托给 `ChainNodeTreeRules`。

- 修改 `groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`  
  职责：保留 service-level behavior tests，验证 refactor 后移动、删除、更新、排序外部行为不变。

- 修改 `groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/ChainNodeQueryServiceImplTest.java`  
  职责：保留 query service behavior tests，验证 path display behavior 不变。

### 前端

- 创建 `groupproject-front/front/src/domain/nodeTreeRules.ts`  
  职责：集中前端节点树规则 implementation。页面使用它做 flatten、有效父节点、同级排序和子树计数；Mock adapter 使用它做移动、祖先校验、同级组定位和层级更新。

- 创建 `groupproject-front/front/src/domain/nodeTreeRules.spec.ts`  
  职责：直接穿过前端树规则 module 的 interface 测试页面推导规则和 Mock mutation 规则。

- 修改 `groupproject-front/front/src/views/chain/nodeAdjustmentViewModel.ts`  
  职责：变为页面兼容 re-export，避免一次性修改页面 import；长期 callers 应优先从 `@/domain/nodeTreeRules` 引用。

- 修改 `groupproject-front/front/src/api/mock.ts`  
  职责：删除重复的树移动 helper，改为使用 `@/domain/nodeTreeRules`。Mock 继续是 adapter，只保留 Mock 数据、延迟和错误包装。

- 修改 / 运行 `groupproject-front/front/src/views/chain/nodeAdjustmentViewModel.spec.ts`、`groupproject-front/front/src/views/chain/NodeAdjustment.spec.ts`、`groupproject-front/front/src/api/chain.spec.ts`  
  职责：确认页面行为、Mock 行为和现有 chain API contract 不变。

---

## 任务 1：创建后端 `ChainNodeTreeRules` module

**文件：**

- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/impl/ChainNodeTreeRulesTest.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainNodeTreeRules.java`

- [ ] **步骤 1：编写失败的后端规则 module 测试**

创建 `groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/impl/ChainNodeTreeRulesTest.java`：

```java
package git.ggwzz.chain.service.impl;

import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.chain.enums.ChainStageType;
import git.ggwzz.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChainNodeTreeRulesTest {

    private final ChainNodeTreeRules rules = new ChainNodeTreeRules();

    @Test
    void planMove_shouldBuildBulkUpdateArgumentsAndApplyResultToNode() {
        ChainNode node = node(3L, 1L, 2L, 2, "/2/3/");
        ChainNode targetParent = node(9L, 1L, null, 1, "/9/");

        ChainNodeTreeRules.MovePlan plan = rules.planMove(
                node,
                9L,
                4,
                id -> id.equals(9L) ? targetParent : null,
                "阶段节点无法修改父节点");

        assertEquals(1L, plan.chainId());
        assertEquals(3L, plan.nodeId());
        assertEquals(9L, plan.newParentId());
        assertEquals(4, plan.sortOrder());
        assertEquals("/2/3/", plan.oldPath());
        assertEquals("/2/3/%", plan.oldPathPattern());
        assertEquals("/9/3/", plan.newPath());
        assertEquals(0, plan.levelOffset());
        assertEquals(2, plan.newLevel());

        plan.applyTo(node);
        assertEquals(9L, node.getParentId());
        assertEquals("/9/3/", node.getNodePath());
        assertEquals(2, node.getNodeLevel());
        assertEquals(4, node.getSortOrder());
    }

    @Test
    void planMove_shouldRejectStageNodeBeforeLoadingTargetParent() {
        ChainNode stage = node(2L, 1L, null, 1, "/2/");
        stage.setStageType(ChainStageType.UPSTREAM);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rules.planMove(
                        stage,
                        9L,
                        null,
                        id -> {
                            throw new AssertionError("target parent must not be loaded for stage nodes");
                        },
                        "阶段节点无法修改父节点"));

        assertEquals(400, exception.getCode());
        assertEquals("阶段节点无法修改父节点", exception.getMessage());
    }

    @Test
    void planMove_shouldRejectNonCanonicalSourcePath() {
        ChainNode node = node(3L, 1L, null, 1, "/");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rules.planMove(
                        node,
                        null,
                        null,
                        id -> null,
                        "阶段节点无法修改父节点"));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
    }

    @Test
    void planMove_shouldRejectTargetAncestorCycle() {
        ChainNode node = node(3L, 1L, null, 1, "/3/");
        ChainNode target = node(5L, 1L, 6L, 2, "/6/5/");
        ChainNode cyclicAncestor = node(6L, 1L, 5L, 2, "/5/6/");
        Map<Long, ChainNode> nodes = Map.of(5L, target, 6L, cyclicAncestor);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> rules.planMove(
                        node,
                        5L,
                        null,
                        nodes::get,
                        "阶段节点无法修改父节点"));

        assertEquals(500, exception.getCode());
        assertEquals("节点层级数据异常，请联系管理员", exception.getMessage());
    }

    @Test
    void buildDisplayPath_shouldJoinChainAndPathNodesInMaterializedPathOrder() {
        ChainNode current = node(102L, 1L, 101L, 2, "/100/101/102/");
        current.setNodeName("Battery Manufacturing");
        ChainNode root = node(100L, 1L, null, 1, "/100/");
        root.setNodeName("New Energy Vehicle");
        ChainNode parent = node(101L, 1L, 100L, 2, "/100/101/");
        parent.setNodeName("Midstream");

        String result = rules.buildDisplayPath(
                current,
                "新能源汽车产业链",
                List.of(current, parent, root));

        assertEquals("新能源汽车产业链 / New Energy Vehicle / Midstream / Battery Manufacturing", result);
    }

    @Test
    void buildDisplayPath_shouldFallbackToCurrentNodeNameWhenPathIsBlankAndChainMissing() {
        ChainNode current = node(102L, 1L, null, 1, " ");
        current.setNodeName("Battery Manufacturing");

        String result = rules.buildDisplayPath(current, null, List.of(current));

        assertEquals("Battery Manufacturing", result);
    }

    @Test
    void parseNodePath_shouldSkipBlankAndUnparseableSegments() {
        assertEquals(List.of(101L, 102L), rules.parseNodePath("/101/not-a-number/102//"));
        assertEquals(List.of(), rules.parseNodePath("  "));
        assertEquals(List.of(), rules.parseNodePath(null));
    }

    @Test
    void planMove_shouldKeepSortOrderWhenSortOrderIsNull() {
        ChainNode node = node(3L, 1L, 2L, 2, "/2/3/");
        node.setSortOrder(7);

        ChainNodeTreeRules.MovePlan plan = rules.planMove(
                node,
                null,
                null,
                Function.identity(),
                "阶段节点无法修改父节点");

        plan.applyTo(node);
        assertNull(node.getParentId());
        assertEquals("/3/", node.getNodePath());
        assertEquals(1, node.getNodeLevel());
        assertEquals(7, node.getSortOrder());
    }

    private ChainNode node(Long id, Long chainId, Long parentId, Integer level, String path) {
        ChainNode node = new ChainNode();
        node.setId(id);
        node.setChainId(chainId);
        node.setParentId(parentId);
        node.setNodeName("node-" + id);
        node.setNodeLevel(level);
        node.setNodePath(path);
        return node;
    }
}
```

- [ ] **步骤 2：运行测试验证红灯**

工作目录：`groupproject-back-commit/back`

```bash
mvn "-Dtest=ChainNodeTreeRulesTest" test
```

预期：FAIL，编译错误包含 `cannot find symbol: class ChainNodeTreeRules`。

- [ ] **步骤 3：编写最少后端规则 module implementation**

创建 `groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainNodeTreeRules.java`：

```java
package git.ggwzz.chain.service.impl;

import git.ggwzz.chain.entity.ChainNode;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产业链节点树规则 module。
 *
 * <p>集中 node_path canonical 校验、节点移动计划、祖先链校验和展示路径拼接。
 * 该 module 不直接访问数据库；caller 通过函数参数提供节点加载能力。</p>
 */
@Slf4j
public class ChainNodeTreeRules {

    private static final String PATH_SEPARATOR = " / ";

    public MovePlan planMove(ChainNode node,
                             Long newParentId,
                             Integer sortOrder,
                             Function<Long, ChainNode> nodeLoader,
                             String stageErrorMessage) {
        requireOrdinaryNode(node, stageErrorMessage);
        if (Objects.equals(node.getId(), newParentId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节点不能移动到自身下");
        }

        String oldPath = requireCanonicalNodePath(node);
        int oldLevel = node.getNodeLevel();
        String parentPath = "/";
        int newLevel = 1;
        if (newParentId != null) {
            ChainNode parent = nodeLoader.apply(newParentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父节点不存在");
            }
            if (!Objects.equals(node.getChainId(), parent.getChainId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不能跨产业链移动节点");
            }
            validateMoveTarget(node, parent, nodeLoader);
            parentPath = requireCanonicalNodePath(parent);
            newLevel = parent.getNodeLevel() + 1;
        }

        String newPath = parentPath + node.getId() + "/";
        int levelOffset = newLevel - oldLevel;
        return new MovePlan(
                node.getChainId(),
                node.getId(),
                newParentId,
                sortOrder,
                oldPath,
                oldPath + "%",
                newPath,
                levelOffset,
                newLevel);
    }

    public void requireOrdinaryNode(ChainNode node, String message) {
        if (node.getStageType() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
    }

    public String requireCanonicalNodePath(ChainNode node) {
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
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
        }
        return nodePath;
    }

    public List<Long> parseNodePath(String nodePath) {
        List<Long> result = new ArrayList<>();
        if (nodePath == null || nodePath.isBlank()) {
            return result;
        }
        String[] parts = nodePath.split("/");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                result.add(Long.valueOf(part));
            } catch (NumberFormatException ignored) {
                // Ignore invalid path segments and keep the parseable portion.
            }
        }
        return result;
    }

    public String buildDisplayPath(ChainNode current, String chainName, Collection<ChainNode> pathNodes) {
        List<String> names = new ArrayList<>();
        if (chainName != null && !chainName.isBlank()) {
            names.add(chainName);
        }

        List<Long> pathIds = parseNodePath(current.getNodePath());
        if (pathIds.isEmpty() && current.getId() != null) {
            pathIds.add(current.getId());
        }
        Map<Long, String> nodeNames = pathNodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(
                        ChainNode::getId,
                        ChainNode::getNodeName,
                        (left, right) -> left));
        for (Long pathId : pathIds) {
            String nodeName = nodeNames.get(pathId);
            if (nodeName != null && !nodeName.isBlank()) {
                names.add(nodeName);
            }
        }

        if (names.isEmpty()) {
            return current.getNodeName();
        }
        return String.join(PATH_SEPARATOR, names);
    }

    private void validateMoveTarget(ChainNode node,
                                    ChainNode targetParent,
                                    Function<Long, ChainNode> nodeLoader) {
        Set<Long> visited = new HashSet<>();
        ChainNode current = targetParent;
        while (current != null) {
            requireCanonicalNodePath(current);
            if (!Objects.equals(node.getChainId(), current.getChainId())) {
                throwCorruptTree(node.getChainId(), current.getId(), "产业链节点祖先链跨产业链");
            }
            if (!visited.add(current.getId())) {
                log.error("产业链节点祖先链存在环: chainId={}, nodeId={}", node.getChainId(), current.getId());
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
            }
            if (Objects.equals(node.getId(), current.getId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "节点不能移动到自己的子节点下");
            }
            Long childId = current.getId();
            Long parentId = current.getParentId();
            if (parentId == null) {
                return;
            }
            current = nodeLoader.apply(parentId);
            if (current == null) {
                log.error(
                        "产业链节点祖先不存在: chainId={}, childId={}, missingParentId={}",
                        node.getChainId(),
                        childId,
                        parentId);
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
            }
        }
    }

    private void throwCorruptTree(Long chainId, Long nodeId, String reason) {
        log.error("{}: chainId={}, nodeId={}", reason, chainId, nodeId);
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "节点层级数据异常，请联系管理员");
    }

    public record MovePlan(
            Long chainId,
            Long nodeId,
            Long newParentId,
            Integer sortOrder,
            String oldPath,
            String oldPathPattern,
            String newPath,
            Integer levelOffset,
            Integer newLevel) {

        public void applyTo(ChainNode node) {
            node.setParentId(newParentId);
            node.setNodePath(newPath);
            node.setNodeLevel(newLevel);
            if (sortOrder != null) {
                node.setSortOrder(sortOrder);
            }
        }
    }
}
```

- [ ] **步骤 4：运行后端规则 module 测试验证通过**

工作目录：`groupproject-back-commit/back`

```bash
mvn "-Dtest=ChainNodeTreeRulesTest" test
```

预期：PASS，`Tests run: 8, Failures: 0, Errors: 0`。

- [ ] **步骤 5：检查点（不提交）**

检查文件：

```bash
git diff -- src/main/java/git/ggwzz/chain/service/impl/ChainNodeTreeRules.java src/test/java/git/ggwzz/chain/service/impl/ChainNodeTreeRulesTest.java
```

预期：只包含本任务新增的 2 个文件。

---

## 任务 2：让 `ChainServiceImpl` 使用后端树规则 module

**文件：**

- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`

- [ ] **步骤 1：运行现有 service characterization tests**

工作目录：`groupproject-back-commit/back`

```bash
mvn "-Dtest=ChainServiceTest" test
```

预期：PASS。若此处失败，先记录失败输出并停止本任务，因为 refactor 前 behavior 已经不稳定。

- [ ] **步骤 2：在 `ChainServiceImpl` 添加规则 module 字段**

在 mapper 字段之后加入：

```java
    private final ChainNodeTreeRules chainNodeTreeRules = new ChainNodeTreeRules();
```

修改后的字段区应形如：

```java
    private final ChainMapper chainMapper;
    private final ChainNodeMapper chainNodeMapper;
    private final EnterpriseNodeRelMapper enterpriseNodeRelMapper;
    private final ChainOptionMapper chainOptionMapper;
    private final ChainNodeCandidateMapper chainNodeCandidateMapper;
    private final ChainNodeTreeRules chainNodeTreeRules = new ChainNodeTreeRules();
```

同时删除不再使用的 import：

```java
import java.util.HashSet;
```

保留 `java.util.Set`，因为 `createDefaultStageNodes` 仍使用 `Set.of(...)`。

- [ ] **步骤 3：替换 `moveNodeInternal` 的 implementation**

将 `moveNodeInternal(...)` 整个方法替换为：

```java
    private void moveNodeInternal(
            ChainNode node,
            Long newParentId,
            Integer sortOrder,
            String stageErrorMessage) {
        ChainNodeTreeRules.MovePlan plan = chainNodeTreeRules.planMove(
                node,
                newParentId,
                sortOrder,
                chainNodeMapper::selectById,
                stageErrorMessage);
        int affected = chainNodeMapper.updateSubtree(
                plan.chainId(),
                plan.nodeId(),
                plan.newParentId(),
                plan.sortOrder(),
                plan.oldPath(),
                plan.oldPathPattern(),
                plan.newPath(),
                plan.levelOffset());
        if (affected == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "节点不存在");
        }

        plan.applyTo(node);
    }
```

删除以下私有方法，它们的 implementation 已进入 `ChainNodeTreeRules`：

```java
    private void requireOrdinaryNode(ChainNode node, String message) { ... }

    private String requireCanonicalNodePath(ChainNode node) { ... }

    private void validateMoveTarget(ChainNode node, ChainNode targetParent) { ... }
```

- [ ] **步骤 4：更新 `deleteNode` 的阶段节点保护调用**

在 `deleteNode(Long nodeId)` 中，将：

```java
        requireOrdinaryNode(node, "上下游阶段节点不允许删除");
```

替换为：

```java
        chainNodeTreeRules.requireOrdinaryNode(node, "上下游阶段节点不允许删除");
```

- [ ] **步骤 5：运行 service tests 验证 behavior 不变**

工作目录：`groupproject-back-commit/back`

```bash
mvn "-Dtest=ChainServiceTest,ChainNodeTreeRulesTest" test
```

预期：PASS。重点确认这些现有测试仍然通过：

- `should_move_node_and_rewrite_descendant_paths`
- `should_reject_move_when_node_is_stage`
- `should_reject_noncanonical_source_path_before_bulk_move`
- `should_reject_move_when_target_ancestor_is_missing`
- `should_reject_move_when_target_ancestor_chain_has_cycle`
- `should_reject_delete_stage_before_child_check`

- [ ] **步骤 6：检查点（不提交）**

检查文件：

```bash
git diff -- src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java src/main/java/git/ggwzz/chain/service/impl/ChainNodeTreeRules.java src/test/java/git/ggwzz/chain/service/impl/ChainNodeTreeRulesTest.java
```

预期：`ChainServiceImpl` 中移动规则 private implementation 减少，`moveNodeInternal` 只 orchestration mapper update 和 plan application。

---

## 任务 3：让 `ChainNodeQueryServiceImpl` 使用后端树规则 module

**文件：**

- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainNodeQueryServiceImpl.java`
- 测试：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/ChainNodeQueryServiceImplTest.java`

- [ ] **步骤 1：运行现有 query service tests**

工作目录：`groupproject-back-commit/back`

```bash
mvn "-Dtest=ChainNodeQueryServiceImplTest" test
```

预期：PASS。若失败，记录失败输出并停止本任务。

- [ ] **步骤 2：添加规则 module 字段并删除局部常量**

在 `ChainNodeQueryServiceImpl` 的 mapper 字段后加入：

```java
    private final ChainNodeTreeRules chainNodeTreeRules = new ChainNodeTreeRules();
```

删除：

```java
    private static final String PATH_SEPARATOR = " / ";
```

修改后的字段区应形如：

```java
    private final ChainNodeMapper chainNodeMapper;
    private final ChainMapper chainMapper;
    private final ChainNodeTreeRules chainNodeTreeRules = new ChainNodeTreeRules();
```

- [ ] **步骤 3：替换 `buildNodePath` 中的路径解析和拼接**

将 `buildNodePath(Long nodeId)` 替换为：

```java
    @Override
    public String buildNodePath(Long nodeId) {
        if (nodeId == null) {
            return null;
        }
        ChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            return null;
        }

        Chain chain = chainMapper.selectById(node.getChainId());
        String chainName = chain == null ? null : chain.getChainName();
        List<Long> pathIds = chainNodeTreeRules.parseNodePath(node.getNodePath());
        if (pathIds.isEmpty()) {
            pathIds.add(node.getId());
        }
        Map<Long, ChainNode> nodeMap = loadNodesById(pathIds);
        return chainNodeTreeRules.buildDisplayPath(node, chainName, nodeMap.values());
    }
```

- [ ] **步骤 4：删除重复的 `parseNodePath` 私有方法**

从 `ChainNodeQueryServiceImpl` 删除整个方法：

```java
    private List<Long> parseNodePath(String nodePath) { ... }
```

保留 `loadNodesById(Collection<Long> nodeIds)`，因为 query service 仍负责 Mapper 访问。

- [ ] **步骤 5：运行 query service 和规则 module tests**

工作目录：`groupproject-back-commit/back`

```bash
mvn "-Dtest=ChainNodeQueryServiceImplTest,ChainNodeTreeRulesTest" test
```

预期：PASS。重点确认这些现有 path behavior 仍然通过：

- `buildNodePath_shouldJoinChainAndNodesInPathOrder`
- `buildNodePath_shouldFallbackToCurrentNodeWhenNodePathIsBlank`
- `buildNodePath_shouldSkipUnparseablePathSegments`
- `buildNodePath_shouldReturnNodePathWhenChainDoesNotExist`
- `buildNodePath_shouldSkipMissingNodesInPath`

- [ ] **步骤 6：检查点（不提交）**

检查文件：

```bash
git diff -- src/main/java/git/ggwzz/chain/service/impl/ChainNodeQueryServiceImpl.java src/main/java/git/ggwzz/chain/service/impl/ChainNodeTreeRules.java src/test/java/git/ggwzz/chain/service/impl/ChainNodeTreeRulesTest.java
```

预期：`ChainNodeQueryServiceImpl` 不再维护自己的 path parsing / joining implementation。

---

## 任务 4：创建前端 `nodeTreeRules` domain module

**文件：**

- 创建：`groupproject-front/front/src/domain/nodeTreeRules.spec.ts`
- 创建：`groupproject-front/front/src/domain/nodeTreeRules.ts`

- [ ] **步骤 1：编写失败的前端树规则 module 测试**

创建 `groupproject-front/front/src/domain/nodeTreeRules.spec.ts`：

```typescript
import { describe, expect, it } from "vitest";
import type { ChainNode } from "@/types/domain";
import {
  buildSortRequest,
  collectDescendantIds,
  countSubtreeNodes,
  findNodeLocation,
  flattenNodeTree,
  getSiblingGroup,
  getSiblingNodes,
  getValidParentOptions,
  insertSibling,
  moveNodeInTree,
  moveSibling,
} from "./nodeTreeRules";

const tree: ChainNode[] = [
  {
    id: 1,
    name: "根节点 A",
    children: [
      {
        id: 2,
        name: "子节点 A1",
        levelNo: 2,
        parentId: 1,
        children: [{ id: 3, name: "孙节点 A11", levelNo: 3, parentId: 2, children: [] }],
      },
      { id: 4, name: "子节点 A2", levelNo: 2, parentId: 1, children: [] },
    ],
  },
  { id: 5, name: "根节点 B", levelNo: 1, parentId: null, children: [] },
];

function cloneTree() {
  return structuredClone(tree);
}

describe("nodeTreeRules", () => {
  it("derives parent, position, level and display path from tree nesting", () => {
    expect(
      flattenNodeTree(tree).map(({ id, parentId, position, level, pathName }) => ({
        id,
        parentId,
        position,
        level,
        pathName,
      })),
    ).toEqual([
      { id: 1, parentId: null, position: 0, level: 1, pathName: "根节点 A" },
      { id: 2, parentId: 1, position: 0, level: 2, pathName: "根节点 A / 子节点 A1" },
      { id: 3, parentId: 2, position: 0, level: 3, pathName: "根节点 A / 子节点 A1 / 孙节点 A11" },
      { id: 4, parentId: 1, position: 1, level: 2, pathName: "根节点 A / 子节点 A2" },
      { id: 5, parentId: null, position: 1, level: 1, pathName: "根节点 B" },
    ]);
  });

  it("collects descendants and excludes the selected subtree from parent options", () => {
    expect(collectDescendantIds(tree, 1)).toEqual([2, 3, 4]);
    expect(getValidParentOptions(tree, 1).map((entry) => entry.id)).toEqual([5]);
    expect(getValidParentOptions(tree, 2).map((entry) => entry.id)).toEqual([1, 4, 5]);
  });

  it("builds sibling orders without crossing first or last boundaries", () => {
    const siblings = getSiblingNodes(tree, 1);
    expect(moveSibling(siblings, 2, "up").map((node) => node.id)).toEqual([2, 4]);
    expect(moveSibling(siblings, 2, "down").map((node) => node.id)).toEqual([4, 2]);
    expect(insertSibling(siblings, 4, 0).map((node) => node.id)).toEqual([4, 2]);
    expect(buildSortRequest(1, insertSibling(siblings, 4, 0))).toEqual({
      parentId: 1,
      nodeOrders: [
        { nodeId: 4, sortOrder: 0 },
        { nodeId: 2, sortOrder: 1 },
      ],
    });
  });

  it("locates nodes and sibling groups in a chain-indexed tree map", () => {
    const nodeTrees: Record<number, ChainNode[]> = { 7: cloneTree() };
    expect(findNodeLocation(nodeTrees, 3)).toMatchObject({ chainId: 7, parentId: 2 });
    expect(getSiblingGroup(nodeTrees, 7, null)?.map((node) => node.id)).toEqual([1, 5]);
    expect(getSiblingGroup(nodeTrees, 7, 1)?.map((node) => node.id)).toEqual([2, 4]);
  });

  it("moves a subtree, updates level numbers and normalizes sibling order for mock adapter", () => {
    const nodeTrees: Record<number, ChainNode[]> = { 7: cloneTree() };

    const moved = moveNodeInTree(nodeTrees, 2, 5, 0);

    expect(moved.parentId).toBe(5);
    expect(moved.levelNo).toBe(2);
    expect(moved.children?.[0].parentId).toBe(2);
    expect(moved.children?.[0].levelNo).toBe(3);
    expect(getSiblingGroup(nodeTrees, 7, 1)?.map((node) => node.id)).toEqual([4]);
    expect(getSiblingGroup(nodeTrees, 7, 5)?.map((node) => node.id)).toEqual([2]);
    expect(getSiblingGroup(nodeTrees, 7, 5)?.[0].sortNo).toBe(0);
  });

  it("rejects moving a node into its own descendant", () => {
    const nodeTrees: Record<number, ChainNode[]> = { 7: cloneTree() };

    expect(() => moveNodeInTree(nodeTrees, 2, 3, 0)).toThrow("节点不能移动到自己的子节点下");
  });

  it("counts the selected node and every node in its subtree", () => {
    expect(countSubtreeNodes(tree[0])).toBe(4);
    expect(countSubtreeNodes(tree[1])).toBe(1);
  });
});
```

- [ ] **步骤 2：运行前端规则 module 测试验证红灯**

工作目录：`groupproject-front/front`

```bash
npm test -- src/domain/nodeTreeRules.spec.ts
```

预期：FAIL，错误包含 `Failed to resolve import "./nodeTreeRules"` 或 `Cannot find module './nodeTreeRules'`。

- [ ] **步骤 3：编写前端树规则 module implementation**

创建 `groupproject-front/front/src/domain/nodeTreeRules.ts`：

```typescript
import type { ChainNode, SortNodesRequest } from "@/types/domain";

export interface FlattenedNode {
  id: number;
  name: string;
  parentId: number | null;
  position: number;
  level: number;
  pathName: string;
  node: ChainNode;
}

export interface NodeLocation {
  chainId: number;
  node: ChainNode;
  parentId: number | null;
  siblings: ChainNode[];
}

export function flattenNodeTree(nodes: ChainNode[]): FlattenedNode[] {
  const flattened: FlattenedNode[] = [];

  function visit(
    siblings: ChainNode[],
    parentId: number | null,
    parentPath: string[],
    level: number,
  ) {
    siblings.forEach((node, position) => {
      const path = [...parentPath, node.name];
      flattened.push({
        id: node.id,
        name: node.name,
        parentId,
        position,
        level,
        pathName: path.join(" / "),
        node,
      });
      visit(node.children ?? [], node.id, path, level + 1);
    });
  }

  visit(nodes, null, [], 1);
  return flattened;
}

export function collectDescendantIds(nodes: ChainNode[], nodeId: number): number[] {
  const selected = flattenNodeTree(nodes).find((entry) => entry.id === nodeId);
  if (!selected) return [];
  return getDescendantNodeIds(selected.node);
}

export function getDescendantNodeIds(node: ChainNode): number[] {
  return (node.children ?? []).flatMap((child) => [
    child.id,
    ...getDescendantNodeIds(child),
  ]);
}

export function getValidParentOptions(nodes: ChainNode[], nodeId: number): FlattenedNode[] {
  const excludedIds = new Set([nodeId, ...collectDescendantIds(nodes, nodeId)]);
  return flattenNodeTree(nodes).filter((entry) => !excludedIds.has(entry.id));
}

export function getSiblingNodes(nodes: ChainNode[], parentId: number | null): ChainNode[] {
  if (parentId === null) return [...nodes];
  const parent = flattenNodeTree(nodes).find((entry) => entry.id === parentId);
  return [...(parent?.node.children ?? [])];
}

export function moveSibling(
  siblings: ChainNode[],
  nodeId: number,
  direction: "up" | "down",
): ChainNode[] {
  const currentIndex = siblings.findIndex((node) => node.id === nodeId);
  const targetIndex = currentIndex + (direction === "up" ? -1 : 1);
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= siblings.length) {
    return [...siblings];
  }

  const reordered = [...siblings];
  [reordered[currentIndex], reordered[targetIndex]] = [
    reordered[targetIndex],
    reordered[currentIndex],
  ];
  return reordered;
}

export function insertSibling(
  siblings: ChainNode[],
  nodeId: number,
  targetPosition: number,
): ChainNode[] {
  const currentIndex = siblings.findIndex((node) => node.id === nodeId);
  if (currentIndex < 0) return [...siblings];

  const reordered = [...siblings];
  const [node] = reordered.splice(currentIndex, 1);
  const insertionIndex = Math.min(Math.max(targetPosition, 0), reordered.length);
  reordered.splice(insertionIndex, 0, node);
  return reordered;
}

export function buildSortRequest(
  parentId: number | null,
  siblings: ChainNode[],
): SortNodesRequest {
  return {
    parentId,
    nodeOrders: siblings.map((node, sortOrder) => ({
      nodeId: node.id,
      sortOrder,
    })),
  };
}

export function countSubtreeNodes(node: ChainNode): number {
  return (
    1 +
    (node.children ?? []).reduce(
      (count, child) => count + countSubtreeNodes(child),
      0,
    )
  );
}

export function findNodeLocation(
  nodeTrees: Record<number, ChainNode[]>,
  nodeId: number,
): NodeLocation | undefined {
  for (const [chainId, roots] of Object.entries(nodeTrees)) {
    const location = findNodeInSiblings(Number(chainId), roots, nodeId, null);
    if (location) return location;
  }
  return undefined;
}

export function getSiblingGroup(
  nodeTrees: Record<number, ChainNode[]>,
  chainId: number,
  parentId: number | null,
): ChainNode[] | undefined {
  if (parentId === null) return nodeTrees[chainId];
  const parent = findNodeLocation(nodeTrees, parentId);
  if (!parent || parent.chainId !== chainId) return undefined;
  parent.node.children ??= [];
  return parent.node.children;
}

export function moveNodeInTree(
  nodeTrees: Record<number, ChainNode[]>,
  id: number,
  newParentId: number | null,
  sortOrder?: number,
): ChainNode {
  const location = findNodeLocation(nodeTrees, id);
  if (!location) throw new Error("节点不存在");
  if (location.node.stageType) {
    throw new Error("阶段节点无法修改父节点");
  }
  if (newParentId === id) {
    throw new Error("节点不能移动到自身下");
  }

  const descendantIds = new Set(getDescendantNodeIds(location.node));
  if (newParentId !== null && descendantIds.has(newParentId)) {
    throw new Error("节点不能移动到自己的子节点下");
  }

  const newParent =
    newParentId === null ? undefined : findNodeLocation(nodeTrees, newParentId);
  if (newParentId !== null && !newParent) {
    throw new Error("父节点不存在");
  }
  if (newParent && newParent.chainId !== location.chainId) {
    throw new Error("不能跨产业链移动节点");
  }

  const oldIndex = location.siblings.findIndex((node) => node.id === id);
  location.siblings.splice(oldIndex, 1);
  normalizeSiblingOrder(location.siblings);

  const targetSiblings = getSiblingGroup(nodeTrees, location.chainId, newParentId);
  if (!targetSiblings) {
    throw new Error("父节点不存在");
  }
  const insertionIndex = Math.min(
    Math.max(sortOrder ?? targetSiblings.length, 0),
    targetSiblings.length,
  );
  location.node.parentId = newParentId;
  updateSubtreeLevel(
    location.node,
    newParent ? (newParent.node.levelNo ?? 1) + 1 : 1,
  );
  targetSiblings.splice(insertionIndex, 0, location.node);
  normalizeSiblingOrder(targetSiblings);
  return location.node;
}

export function normalizeSiblingOrder(siblings: ChainNode[]) {
  siblings.forEach((node, index) => {
    node.sortNo = index;
  });
}

export function updateSubtreeLevel(node: ChainNode, levelNo: number) {
  node.levelNo = levelNo;
  for (const child of node.children ?? []) {
    child.parentId = node.id;
    updateSubtreeLevel(child, levelNo + 1);
  }
}

function findNodeInSiblings(
  chainId: number,
  siblings: ChainNode[],
  nodeId: number,
  parentId: number | null,
): NodeLocation | undefined {
  for (const node of siblings) {
    if (node.id === nodeId) {
      return { chainId, node, parentId, siblings };
    }
    const childLocation = findNodeInSiblings(
      chainId,
      node.children ?? [],
      nodeId,
      node.id,
    );
    if (childLocation) return childLocation;
  }
  return undefined;
}
```

- [ ] **步骤 4：运行前端规则 module 测试验证通过**

工作目录：`groupproject-front/front`

```bash
npm test -- src/domain/nodeTreeRules.spec.ts
```

预期：PASS，Vitest 输出 `1 passed` test file。

- [ ] **步骤 5：检查点（不提交）**

检查文件：

```bash
git diff -- src/domain/nodeTreeRules.ts src/domain/nodeTreeRules.spec.ts
```

预期：只包含本任务新增的 2 个文件。

---

## 任务 5：让 `NodeAdjustment` 页面 helper 使用前端树规则 module

**文件：**

- 修改：`groupproject-front/front/src/views/chain/nodeAdjustmentViewModel.ts`
- 测试：`groupproject-front/front/src/views/chain/nodeAdjustmentViewModel.spec.ts`
- 测试：`groupproject-front/front/src/views/chain/NodeAdjustment.spec.ts`

- [ ] **步骤 1：将页面 helper 改为兼容 re-export**

将 `groupproject-front/front/src/views/chain/nodeAdjustmentViewModel.ts` 全文件替换为：

```typescript
export {
  buildSortRequest,
  collectDescendantIds,
  countSubtreeNodes,
  flattenNodeTree,
  getSiblingNodes,
  getValidParentOptions,
  insertSibling,
  moveSibling,
} from "@/domain/nodeTreeRules";

export type { FlattenedNode } from "@/domain/nodeTreeRules";
```

说明：`NodeAdjustment.vue` 暂时不改 import，避免一次性触碰页面。这个 re-export 是 page-specific compatibility seam；真正的树规则 implementation 已集中到 `@/domain/nodeTreeRules`。

- [ ] **步骤 2：运行页面 helper 和页面行为测试**

工作目录：`groupproject-front/front`

```bash
npm test -- src/views/chain/nodeAdjustmentViewModel.spec.ts src/views/chain/NodeAdjustment.spec.ts src/domain/nodeTreeRules.spec.ts
```

预期：PASS，3 个 test files 通过。

- [ ] **步骤 3：检查点（不提交）**

检查文件：

```bash
git diff -- src/views/chain/nodeAdjustmentViewModel.ts src/domain/nodeTreeRules.ts src/domain/nodeTreeRules.spec.ts
```

预期：`nodeAdjustmentViewModel.ts` 不再持有树规则 implementation，只 re-export。

---

## 任务 6：让前端 Mock adapter 使用前端树规则 module

**文件：**

- 修改：`groupproject-front/front/src/api/mock.ts`
- 测试：`groupproject-front/front/src/api/chain.spec.ts`
- 测试：`groupproject-front/front/src/views/chain/NodeAdjustment.spec.ts`
- 测试：`groupproject-front/front/src/domain/nodeTreeRules.spec.ts`

- [ ] **步骤 1：在 `mock.ts` 添加树规则 imports**

在 `mock.ts` 顶部的 import 区加入：

```typescript
import {
  findNodeLocation,
  getDescendantNodeIds,
  getSiblingGroup,
  moveNodeInTree,
  normalizeSiblingOrder,
} from "@/domain/nodeTreeRules";
```

- [ ] **步骤 2：删除 `mock.ts` 中重复的树 mutation helpers**

从 `mock.ts` 删除这些本地定义：

```typescript
function getDescendantNodeIds(node: ChainNode): number[] { ... }

interface NodeLocation { ... }

function findNodeInSiblings(...) { ... }

function findNodeLocation(nodeId: number): NodeLocation | undefined { ... }

function getSiblingGroup(chainId: number, parentId: number | null): ChainNode[] | undefined { ... }

function updateSubtreeLevel(node: ChainNode, levelNo: number) { ... }

function normalizeSiblingOrder(siblings: ChainNode[]) { ... }

function moveNodeInTree(id: number, newParentId: number | null, sortOrder?: number): ChainNode { ... }
```

保留 `flattenNodes(...)`、`removeNodeById(...)`、`withComputedNodeCounts(...)` 等 Mock 数据辅助，因为它们不是移动合法性规则的主 implementation。

- [ ] **步骤 3：更新 `mock.ts` 中的 call sites**

将这些调用逐个替换：

```typescript
const location = findNodeLocation(parentId);
```

替换为：

```typescript
const location = findNodeLocation(nodeTrees, parentId);
```

将：

```typescript
const node = data.parentChangeRequested
  ? moveNodeInTree(id, data.parentId ?? null, data.sortNo)
  : findNodeLocation(id)?.node;
```

替换为：

```typescript
const node = data.parentChangeRequested
  ? moveNodeInTree(nodeTrees, id, data.parentId ?? null, data.sortNo)
  : findNodeLocation(nodeTrees, id)?.node;
```

将：

```typescript
return sleep(moveNodeInTree(id, data.newParentId, data.sortOrder));
```

替换为：

```typescript
return sleep(moveNodeInTree(nodeTrees, id, data.newParentId, data.sortOrder));
```

将：

```typescript
const firstLocation = findNodeLocation(firstOrder.nodeId);
```

替换为：

```typescript
const firstLocation = findNodeLocation(nodeTrees, firstOrder.nodeId);
```

将：

```typescript
const siblings = getSiblingGroup(firstLocation.chainId, data.parentId);
```

替换为：

```typescript
const siblings = getSiblingGroup(nodeTrees, firstLocation.chainId, data.parentId);
```

将所有剩余的：

```typescript
findNodeLocation(
```

检查并改成传入 `nodeTrees` 的形式：

```typescript
findNodeLocation(nodeTrees,
```

- [ ] **步骤 4：运行 Mock 和页面相关测试**

工作目录：`groupproject-front/front`

```bash
npm test -- src/api/chain.spec.ts src/views/chain/NodeAdjustment.spec.ts src/domain/nodeTreeRules.spec.ts
```

预期：PASS，3 个 test files 通过。

- [ ] **步骤 5：检查点（不提交）**

检查文件：

```bash
git diff -- src/api/mock.ts src/domain/nodeTreeRules.ts src/domain/nodeTreeRules.spec.ts src/views/chain/nodeAdjustmentViewModel.ts
```

预期：`mock.ts` 不再复制移动、祖先、同级组定位和层级更新规则；它只调用 `@/domain/nodeTreeRules`。

---

## 任务 7：联合验证与 architecture smoke check

**文件：**

- 验证：后端 `ChainNodeTreeRules`、`ChainServiceImpl`、`ChainNodeQueryServiceImpl`
- 验证：前端 `nodeTreeRules`、`nodeAdjustmentViewModel`、`mock.ts`

- [ ] **步骤 1：运行后端聚焦测试**

工作目录：`groupproject-back-commit/back`

```bash
mvn "-Dtest=ChainNodeTreeRulesTest,ChainServiceTest,ChainNodeQueryServiceImplTest" test
```

预期：PASS。

- [ ] **步骤 2：运行前端聚焦测试**

工作目录：`groupproject-front/front`

```bash
npm test -- src/domain/nodeTreeRules.spec.ts src/views/chain/nodeAdjustmentViewModel.spec.ts src/views/chain/NodeAdjustment.spec.ts src/api/chain.spec.ts
```

预期：PASS。

- [ ] **步骤 3：运行后端完整测试**

工作目录：`groupproject-back-commit/back`

```bash
mvn test
```

预期：PASS。若失败，记录首个失败 test class、test method 和完整错误摘要，不声称完成。

- [ ] **步骤 4：运行前端完整测试**

工作目录：`groupproject-front/front`

```bash
npm test
```

预期：PASS。若失败，记录首个失败 spec 和错误摘要，不声称完成。

- [ ] **步骤 5：运行前端构建**

工作目录：`groupproject-front/front`

```bash
npm run build
```

预期：PASS，`vue-tsc -b && vite build` 完成，无 TypeScript 错误。

- [ ] **步骤 6：运行后端打包**

工作目录：`groupproject-back-commit/back`

```bash
mvn package
```

预期：PASS，生成 target artifact。若环境中缺少外部配置导致 package 失败，记录失败输出中的配置项名称和 profile。

- [ ] **步骤 7：最终 diff 检查（不提交）**

在主工作目录 `C:/Users/1/Desktop/hfut/2026_1/java/back` 运行：

```bash
git diff --stat
```

预期修改集中在本计划列出的文件。若出现 `node_modules`、`.idea/httpRequests`、构建产物或临时文件，停止并移除这些非计划产物。

---

## 自检结果

### 规格覆盖度

- 后端 node_path canonical 校验：任务 1、任务 2 覆盖。
- 后端移动合法性：任务 1、任务 2 覆盖。
- 后端展示路径拼接：任务 1、任务 3 覆盖。
- 前端页面推导规则：任务 4、任务 5 覆盖。
- 前端 Mock adapter 重复移动规则：任务 4、任务 6 覆盖。
- 联合验证：任务 7 覆盖。

### 占位符扫描

计划中没有使用「待定」、未定义类型、未定义方法、模糊错误处理或「类似上一步」描述。新增类型和方法均在创建文件任务中定义。

### 类型一致性

- 后端统一使用 `ChainNodeTreeRules.MovePlan`，字段名与 `ChainNodeMapper.updateSubtree(...)` 参数一一对应。
- 前端统一使用 `NodeLocation`、`FlattenedNode`、`moveNodeInTree(nodeTrees, ...)`，`mock.ts` call sites 均传入 `nodeTrees`。
- `nodeAdjustmentViewModel.ts` re-export 的函数名与现有 `NodeAdjustment.vue` imports 保持一致。

## 执行完成判定

只有在以下命令全部通过后，才能声称 Candidate 3 的编码工作完成：

```bash
# groupproject-back-commit/back
mvn "-Dtest=ChainNodeTreeRulesTest,ChainServiceTest,ChainNodeQueryServiceImplTest" test
mvn test
mvn package

# groupproject-front/front
npm test -- src/domain/nodeTreeRules.spec.ts src/views/chain/nodeAdjustmentViewModel.spec.ts src/views/chain/NodeAdjustment.spec.ts src/api/chain.spec.ts
npm test
npm run build
```

如果任何命令失败，最终汇报必须包含失败命令、失败输出摘要和未完成原因。
