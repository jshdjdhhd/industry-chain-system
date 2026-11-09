# 修复产业链校验与排序问题 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复产业链更新空白名称、批量排序“假成功”、节点 `sortOrder` 负数三个功能性问题。

**架构：** 在请求 DTO 层补齐 Jakarta Validation，使 Controller 能在入参阶段返回 400；在 Service 层增加同等业务校验，保证绕过 Controller 的调用也不会写入脏数据；对批量排序的数据库更新行数做强校验，避免部分或全部未更新时仍返回成功。所有改动保持现有 Controller → Service → Mapper 分层，不新增表、不改接口路径、不重构无关模块。

**技术栈：** Java 21、Spring Boot 3.5.15、Jakarta Validation、MyBatis-Plus、JUnit 5、Mockito、MockMvc、Maven。

---

## 执行约束

- 后端代码根目录：`groupproject-back-commit/back`。
- 按用户要求：**不执行 `git add`、`git commit`、`git push`**。
- 当前工作区已有其他未提交改动；本计划只触碰下方“文件结构”列出的文件，避免覆盖或修改 `EnterpriseNodeRelMapper.java`、`EnterpriseNodeRelServiceImpl.java` 及其测试等已有无关改动。
- 每个任务按 TDD 执行：先写失败测试，再做最小实现，再运行目标测试。

## 文件结构

- 修改：`back/src/main/java/git/ggwzz/chain/dto/UpdateChainRequest.java`
  - 职责：更新产业链请求 DTO。保留字段长度校验；本次不在 DTO 加 `@NotBlank`，因为该 DTO 语义是“部分更新，null 字段不更新”，空白字符串由 Service 返回统一业务错误。
- 修改：`back/src/main/java/git/ggwzz/chain/dto/CreateNodeRequest.java`
  - 职责：创建节点请求 DTO。给 `sortOrder` 增加 `@Min(0)`，让 Controller 对负数排序返回 400。
- 修改：`back/src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java`
  - 职责：更新节点请求 DTO。给 `sortOrder` 增加 `@Min(0)`，让 Controller 对负数排序返回 400。
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
  - 职责：产业链和节点业务规则。新增/复用小型私有校验方法；在更新产业链、创建节点、更新节点、批量排序中阻断无效数据。
- 修改：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
  - 职责：Service 层单元测试。新增 4 个回归测试，验证空白产业链名称、排序更新 0 行、创建/更新节点负数排序均被拒绝。
- 修改：`back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`
  - 职责：Controller 层 MockMvc 测试。新增 2 个回归测试，验证创建/更新节点传负数 `sortOrder` 时在 Controller 层返回 400，且不进入 Service。

---

### 任务 0：确认工作区边界

**文件：**
- 不修改业务文件

- [ ] **步骤 1：查看工作区状态**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -version
cd ..
git status --short
```

预期：

- `mvn -version` 能输出 Maven 与 Java 版本。
- `git status --short` 可能显示已有未提交改动；记录这些改动，不把它们当成本计划的一部分。

- [ ] **步骤 2：确认只编辑计划列出的 6 个文件**

本计划后续只编辑：

```text
back/src/main/java/git/ggwzz/chain/dto/UpdateChainRequest.java
back/src/main/java/git/ggwzz/chain/dto/CreateNodeRequest.java
back/src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java
back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java
back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java
back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java
```

不要编辑：

```text
back/src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java
back/src/main/java/git/ggwzz/chain/service/impl/EnterpriseNodeRelServiceImpl.java
back/src/test/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapperTest.java
back/src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java
```

这些文件已有工作区改动，避免混入本修复。

---

### 任务 1：拒绝把产业链名称更新为空白字符串

**文件：**
- 修改：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`

- [ ] **步骤 1：编写失败的 Service 测试**

在 `ChainServiceTest` 中，放在现有 `should_return_real_summary_counts_when_update_chain` 测试之后，新增：

```java
    @Test
    void should_reject_blank_chain_name_on_update() {
        Chain chain = chain(1L, "新能源汽车");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainMapper.selectSummaryCountsByChainIds(List.of(1L)))
                .thenReturn(Collections.emptyList());
        UpdateChainRequest request = new UpdateChainRequest();
        request.setChainName("   ");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateChain(1L, request));

        assertEquals(400, ex.getCode());
        assertEquals("产业链名称不能为空", ex.getMessage());
        verify(chainMapper, never()).updateById(any(Chain.class));
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_reject_blank_chain_name_on_update test
```

预期：FAIL。当前实现会接受空白名称，因此该测试应出现类似断言失败：

```text
Expected git.ggwzz.common.exception.BusinessException to be thrown, but nothing was thrown.
```

- [ ] **步骤 3：实现最小业务校验**

在 `ChainServiceImpl.updateChain` 中，`request == null` 校验之后、查询数据库之前，加入 `requireChainNameWhenPresent`：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChainVO updateChain(Long chainId, UpdateChainRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        requireChainNameWhenPresent(request.getChainName());
        Chain chain = chainMapper.selectById(chainId);
        if (chain == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产业链不存在");
        }
        if (request.getChainName() != null) {
            chain.setChainName(request.getChainName().trim());
        }
        if (request.getDescription() != null) {
            chain.setDescription(request.getDescription());
        }
        chainMapper.updateById(chain);
        return toChainVOWithCounts(chain);
    }
```

在 `ChainServiceImpl` 私有校验方法区域加入：

```java
    private void requireChainNameWhenPresent(String chainName) {
        if (chainName != null && chainName.isBlank()) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "产业链名称不能为空");
        }
    }
```

- [ ] **步骤 4：运行目标测试验证通过**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_reject_blank_chain_name_on_update test
```

预期：PASS。

- [ ] **步骤 5：运行相邻更新链路测试**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_return_real_summary_counts_when_update_chain test
```

预期：PASS，证明正常更新产业链名称仍可用。

---

### 任务 2：批量排序更新 0 行时返回业务错误

**文件：**
- 修改：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`

- [ ] **步骤 1：编写失败的 Service 测试**

在 `ChainServiceTest` 中，放在现有 `should_update_sort_order_when_sort_nodes` 测试之后，新增：

```java
    @Test
    void should_throw_not_found_when_sort_node_update_matches_no_row() {
        SortNodeOrderRequest first = new SortNodeOrderRequest();
        first.setNodeId(2L);
        first.setSortOrder(1);
        SortNodeOrderRequest missing = new SortNodeOrderRequest();
        missing.setNodeId(999L);
        missing.setSortOrder(2);

        SortNodesRequest request = new SortNodesRequest();
        request.setParentId(1L);
        request.setNodeOrders(List.of(first, missing));
        when(chainNodeMapper.update(
                any(ChainNode.class),
                any(LambdaUpdateWrapper.class)))
                .thenReturn(1, 0);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.sortNodes(request));

        assertEquals(404, ex.getCode());
        assertEquals("排序节点不存在或不属于指定父节点", ex.getMessage());
        verify(chainNodeMapper, times(2))
                .update(any(ChainNode.class), any(LambdaUpdateWrapper.class));
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_throw_not_found_when_sort_node_update_matches_no_row test
```

预期：FAIL。当前实现不检查 `update(...)` 返回值，因此应出现类似断言失败：

```text
Expected git.ggwzz.common.exception.BusinessException to be thrown, but nothing was thrown.
```

- [ ] **步骤 3：实现最小行数校验**

替换 `ChainServiceImpl.sortNodes` 循环内的最后一行：

```java
            int affected = chainNodeMapper.update(update, wrapper);
            if (affected != 1) {
                throw new BusinessException(
                        ResultCode.NOT_FOUND,
                        "排序节点不存在或不属于指定父节点");
            }
```

该代码应替换原来的：

```java
            chainNodeMapper.update(update, wrapper);
```

完整循环应保持如下结构：

```java
        for (SortNodeOrderRequest order : request.getNodeOrders()) {
            ChainNode update = new ChainNode();
            update.setSortOrder(order.getSortOrder());
            LambdaUpdateWrapper<ChainNode> wrapper = new LambdaUpdateWrapper<ChainNode>()
                    .eq(ChainNode::getId, order.getNodeId());
            if (request.getParentId() == null) {
                wrapper.isNull(ChainNode::getParentId);
            } else {
                wrapper.eq(ChainNode::getParentId, request.getParentId());
            }
            int affected = chainNodeMapper.update(update, wrapper);
            if (affected != 1) {
                throw new BusinessException(
                        ResultCode.NOT_FOUND,
                        "排序节点不存在或不属于指定父节点");
            }
        }
```

- [ ] **步骤 4：运行目标测试验证通过**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_throw_not_found_when_sort_node_update_matches_no_row test
```

预期：PASS。

- [ ] **步骤 5：运行正常排序测试验证未破坏原流程**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_update_sort_order_when_sort_nodes test
```

预期：PASS。

---

### 任务 3：拒绝创建和更新节点时的负数 sortOrder

**文件：**
- 修改：`back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java`
- 修改：`back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java`
- 修改：`back/src/main/java/git/ggwzz/chain/dto/CreateNodeRequest.java`
- 修改：`back/src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java`
- 修改：`back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`

- [ ] **步骤 1：编写失败的 Controller 测试**

在 `ChainControllerTest` 中，放在现有 `createChainWithBlankNameShouldReturn400` 测试之后，新增：

```java
    @Test
    @WithMockUser(authorities = "chain:node:create")
    void createNodeWithNegativeSortOrderShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/chains/1/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nodeName": "测试节点",
                                  "sortOrder": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(chainService, never()).createNode(any());
    }
```

在 `ChainControllerTest` 中，放在现有 `updateNodeWithoutParentChangeShouldRequireOnlyUpdatePermission` 测试之前，新增：

```java
    @Test
    @WithMockUser(authorities = "chain:node:update")
    void updateNodeWithNegativeSortOrderShouldReturn400() throws Exception {
        mockMvc.perform(put("/api/chains/nodes/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sortOrder": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(chainService, never()).updateNode(eq(3L), any());
    }
```

- [ ] **步骤 2：编写失败的 Service 测试**

在 `ChainServiceTest` 中，放在现有 `should_reject_blank_node_name_on_update` 测试之后，新增：

```java
    @Test
    void should_reject_negative_sort_order_when_create_node() {
        Chain chain = chain(1L, "新能源汽车");
        when(chainMapper.selectById(1L)).thenReturn(chain);
        when(chainNodeMapper.insert(any(ChainNode.class))).thenAnswer(invocation -> {
            ChainNode node = invocation.getArgument(0);
            node.setId(10L);
            return 1;
        });
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);
        CreateNodeRequest request = new CreateNodeRequest();
        request.setChainId(1L);
        request.setNodeName("测试节点");
        request.setSortOrder(-1);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.createNode(request));

        assertEquals(400, ex.getCode());
        assertEquals("排序值不能小于 0", ex.getMessage());
        verify(chainNodeMapper, never()).insert(any(ChainNode.class));
    }

    @Test
    void should_reject_negative_sort_order_when_update_node() {
        ChainNode node = movableNode(3L, 1L, null, 1, "/3/");
        when(chainNodeMapper.selectById(3L)).thenReturn(node);
        when(chainNodeMapper.updateById(any(ChainNode.class))).thenReturn(1);
        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setSortOrder(-1);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> chainService.updateNode(3L, request));

        assertEquals(400, ex.getCode());
        assertEquals("排序值不能小于 0", ex.getMessage());
        verify(chainNodeMapper, never()).updateById(any(ChainNode.class));
    }
```

- [ ] **步骤 3：运行测试验证失败**

运行 Controller 负数排序测试：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainControllerTest#createNodeWithNegativeSortOrderShouldReturn400,ChainControllerTest#updateNodeWithNegativeSortOrderShouldReturn400 test
```

预期：FAIL。当前 DTO 没有 `@Min(0)`，请求会进入 Controller 方法，返回不会是 400。

运行 Service 负数排序测试：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_reject_negative_sort_order_when_create_node,ChainServiceTest#should_reject_negative_sort_order_when_update_node test
```

预期：FAIL。当前 Service 没有负数排序校验，测试应出现“期望抛 BusinessException 但未抛出”的断言失败。

- [ ] **步骤 4：给创建节点 DTO 增加 Controller 层校验**

修改 `CreateNodeRequest.java` import：

```java
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
```

替换 `sortOrder` 字段声明：

```java
    /** 同级排序 */
    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;
```

- [ ] **步骤 5：给更新节点 DTO 增加 Controller 层校验**

修改 `UpdateNodeRequest.java` import：

```java
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
```

替换 `sortOrder` 字段声明：

```java
    /** 同级排序 */
    @Min(value = 0, message = "排序值不能小于 0")
    private Integer sortOrder;
```

- [ ] **步骤 6：给 Service 层增加同等业务校验**

在 `ChainServiceImpl.createNode` 中，节点名称校验之后、查询产业链之前，加入：

```java
        requireSortOrderWhenPresent(request.getSortOrder());
```

目标片段：

```java
        if (request.getNodeName() == null || request.getNodeName().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节点名称不能为空");
        }
        requireSortOrderWhenPresent(request.getSortOrder());
        Chain chain = chainMapper.selectById(request.getChainId());
```

在 `ChainServiceImpl.updateNode` 中，`requireNodeNameWhenPresent(...)` 之后、查询节点之前，加入：

```java
        requireSortOrderWhenPresent(request.getSortOrder());
```

目标片段：

```java
        requireNodeId(nodeId);
        requireNodeNameWhenPresent(request.getNodeName());
        requireSortOrderWhenPresent(request.getSortOrder());
        ChainNode node = chainNodeMapper.selectById(nodeId);
```

在 `ChainServiceImpl` 私有校验方法区域加入：

```java
    private void requireSortOrderWhenPresent(Integer sortOrder) {
        if (sortOrder != null && sortOrder < 0) {
            throw new BusinessException(
                    ResultCode.BAD_REQUEST,
                    "排序值不能小于 0");
        }
    }
```

- [ ] **步骤 7：运行 Controller 目标测试验证通过**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainControllerTest#createNodeWithNegativeSortOrderShouldReturn400,ChainControllerTest#updateNodeWithNegativeSortOrderShouldReturn400 test
```

预期：PASS。

- [ ] **步骤 8：运行 Service 目标测试验证通过**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_reject_negative_sort_order_when_create_node,ChainServiceTest#should_reject_negative_sort_order_when_update_node test
```

预期：PASS。

- [ ] **步骤 9：运行相邻正常流程测试**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest#should_allow_rename_without_changing_stage_type,ChainServiceTest#should_move_node_to_root_without_changing_sort_order,ChainControllerTest#updateNodeWithoutParentChangeShouldRequireOnlyUpdatePermission test
```

预期：PASS，证明正常更新节点、移动节点和 Controller 授权路径未被负数排序校验破坏。

---

### 任务 4：回归验证与工作区核对

**文件：**
- 不新增业务文件
- 检查任务 1–3 修改过的 6 个文件

- [ ] **步骤 1：运行产业链 Service 与 Controller 回归测试**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn -Dtest=ChainServiceTest,ChainControllerTest test
```

预期：PASS。若失败，读取 Maven 输出中的第一个失败测试名和断言信息，先修复当前任务引入的问题，不修改无关模块。

- [ ] **步骤 2：运行全量测试**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn test
```

预期：PASS。若由于工作区已有无关改动导致失败，记录失败测试和报错，区分“本计划修改文件导致”还是“既有改动导致”。不要隐瞒失败。

- [ ] **步骤 3：运行打包验证**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit/back
mvn package
```

预期：exit 0。若失败，读取完整 Maven 摘要，修复本计划相关编译或测试问题。

- [ ] **步骤 4：核对工作区文件范围，不提交**

运行：

```bash
cd /c/Users/1/Desktop/hfut/2026_1/java/back/groupproject-back-commit
git status --short
```

预期：本计划新增或修改的文件只包含：

```text
back/src/main/java/git/ggwzz/chain/dto/UpdateChainRequest.java
back/src/main/java/git/ggwzz/chain/dto/CreateNodeRequest.java
back/src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java
back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java
back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java
back/src/test/java/git/ggwzz/chain/controller/ChainControllerTest.java
```

若 `git status --short` 还显示计划开始前已有的无关文件，保留它们，不回滚、不覆盖。按用户要求，不运行 `git add`、`git commit`、`git push`。

---

## 自检

- 规格覆盖度：
  - 问题 1“更新产业链空白名称”：任务 1 覆盖 Service 校验与回归测试。
  - 问题 2“批量排序未命中仍返回成功”：任务 2 覆盖 `update(...)` 行数校验与回归测试。
  - 问题 3“创建/更新节点负数 sortOrder”：任务 3 覆盖 DTO 校验、Service 校验、Controller 与 Service 回归测试。
- 占位符扫描：计划中每个代码改动都给出了明确文件、方法、代码块、运行命令和预期结果。
- 类型一致性：测试和实现均使用现有类型 `BusinessException`、`ResultCode`、`CreateNodeRequest`、`UpdateNodeRequest`、`SortNodesRequest`、`SortNodeOrderRequest`、`ChainNode`、`LambdaUpdateWrapper`，方法名与现有 `ChainServiceImpl` 保持一致。
- Git 约束：所有任务均明确不执行提交或推送。
