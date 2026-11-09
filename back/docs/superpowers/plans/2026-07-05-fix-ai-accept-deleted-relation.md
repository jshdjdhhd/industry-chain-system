# 修复 AI 采纳历史逻辑删除挂链唯一索引冲突 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复“采纳 AI 推荐时，如果企业-节点关系存在已逻辑删除历史记录，会因 `uk_enterprise_node` 唯一索引撞库失败”的问题。

**架构：** 将“恢复已逻辑删除关系”的能力从人工挂链接口抽成企业-节点关系服务内部的共享写入路径，让 AI 采纳和人工挂链都先尝试恢复历史记录，恢复失败时才插入新记录。Mapper 的 `restoreDeletedRelation` 保持单一 SQL 入口，并扩展为保留调用方传入的 `confidence`，以支持 AI 关系恢复。

**技术栈：** Java 21、Spring Boot 3.5.15、MyBatis-Plus 3.5.7、JUnit 5、Mockito、H2 MySQL mode。

---

## 文件结构

- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/EnterpriseNodeRelServiceImpl.java`
  - 职责：企业-节点正式挂链/解绑业务。把 AI 和人工挂链的写入路径收敛到一个私有 helper，避免 AI 路径绕过历史关系恢复逻辑。
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java`
  - 职责：企业-节点关系数据访问。调整 `restoreDeletedRelation` 的 SQL，使恢复 AI 历史关系时能写入 `confidence`，恢复人工关系时仍写入 `NULL`。
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java`
  - 职责：Mockito 隔离 Mapper 的 Service 单测。新增 AI 采纳恢复历史逻辑删除关系的失败用例，并确认不再调用 insert。
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapperTest.java`
  - 职责：H2/MyBatis Mapper 集成测试。新增 AI 恢复时 `confidence/source/reason/confirmed_by/is_deleted` 都正确刷新的测试，并保留人工恢复时 `confidence` 为 `NULL` 的既有语义。

## 任务 1：用 Service 单测复现 AI 采纳恢复历史关系缺口

**文件：**
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java:60-126`

- [ ] **步骤 1：编写失败的 Service 单测**

在 `// ======================== linkByAi ========================` 区域中，放到 `linkByAi_shouldSaveRelation` 后面，新增以下测试：

```java
    @Test
    void linkByAi_shouldRestoreLogicallyDeletedRelation() {
        when(enterpriseNodeRelMapper.selectCount(any())).thenReturn(0L);
        when(enterpriseNodeRelMapper.restoreDeletedRelation(any(EnterpriseNodeRel.class)))
                .thenReturn(1);

        enterpriseNodeRelService.linkByAi(1L, 100L, new BigDecimal("0.9500"),
                "AI推荐理由", 9L);

        ArgumentCaptor<EnterpriseNodeRel> captor =
                ArgumentCaptor.forClass(EnterpriseNodeRel.class);
        verify(enterpriseNodeRelMapper).restoreDeletedRelation(captor.capture());
        verify(enterpriseNodeRelMapper, never()).insert(any(EnterpriseNodeRel.class));

        EnterpriseNodeRel restored = captor.getValue();
        assertEquals(1L, restored.getEnterpriseId());
        assertEquals(100L, restored.getNodeId());
        assertEquals(LinkSource.AI.name(), restored.getSource());
        assertEquals(0, new BigDecimal("0.9500").compareTo(restored.getConfidence()));
        assertEquals("AI推荐理由", restored.getReason());
        assertEquals(9L, restored.getConfirmedBy());
        assertEquals(0, restored.getDeleted());
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.service.EnterpriseNodeRelServiceTest#linkByAi_shouldRestoreLogicallyDeletedRelation test
```

预期：FAIL。失败点应是 Mockito 报 `Wanted but not invoked: enterpriseNodeRelMapper.restoreDeletedRelation(...)`，因为当前 `linkByAi` 直接调用 `insert`。

- [ ] **步骤 3：暂不修改实现，进入任务 2**

此任务只负责锁定 Service 层缺口。不要在本任务里改生产代码。

## 任务 2：用 Mapper 集成测试定义 AI 恢复时的 SQL 语义

**文件：**
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapperTest.java:30-68`

- [ ] **步骤 1：补充 BigDecimal import**

在现有 `import java.sql.Timestamp;` 后添加：

```java
import java.math.BigDecimal;
```

- [ ] **步骤 2：编写失败的 Mapper 集成测试**

在 `restoreDeletedRelationShouldReactivateAndRefreshLinkMetadata` 后新增测试：

```java
    @Test
    void restoreDeletedRelationShouldKeepAiConfidenceWhenSourceIsAi() {
        jdbcTemplate.update(
                "UPDATE enterprise_node_rel SET is_deleted = 1, confidence = NULL WHERE id = 1");

        EnterpriseNodeRel relation = new EnterpriseNodeRel();
        relation.setEnterpriseId(2L);
        relation.setNodeId(102L);
        relation.setSource(LinkSource.AI.name());
        relation.setConfidence(new BigDecimal("0.8765"));
        relation.setReason("AI重新推荐");
        relation.setConfirmedBy(8L);
        relation.setDeleted(0);

        int restored = enterpriseNodeRelMapper.restoreDeletedRelation(relation);

        assertEquals(1, restored);
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM enterprise_node_rel WHERE id = 1",
                Integer.class));
        assertEquals(LinkSource.AI.name(), jdbcTemplate.queryForObject(
                "SELECT source FROM enterprise_node_rel WHERE id = 1",
                String.class));
        assertEquals(0, new BigDecimal("0.8765").compareTo(
                jdbcTemplate.queryForObject(
                        "SELECT confidence FROM enterprise_node_rel WHERE id = 1",
                        BigDecimal.class)));
        assertEquals("AI重新推荐", jdbcTemplate.queryForObject(
                "SELECT reason FROM enterprise_node_rel WHERE id = 1",
                String.class));
        assertEquals(8L, jdbcTemplate.queryForObject(
                "SELECT confirmed_by FROM enterprise_node_rel WHERE id = 1",
                Long.class));
    }
```

- [ ] **步骤 3：运行测试验证失败**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.mapper.EnterpriseNodeRelMapperTest#restoreDeletedRelationShouldKeepAiConfidenceWhenSourceIsAi test
```

预期：FAIL。失败点应是 `confidence` 查询结果为 `null`，因为当前 `restoreDeletedRelation` SQL 固定 `confidence = NULL`。

## 任务 3：实现共享恢复/插入路径并保留 AI confidence

**文件：**
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/EnterpriseNodeRelServiceImpl.java:45-84,172-189`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java:102-113`

- [ ] **步骤 1：修改 Mapper SQL，使用调用方传入的 confidence**

把 `EnterpriseNodeRelMapper.restoreDeletedRelation` 的 `@Update` 从：

```java
    @Update("UPDATE enterprise_node_rel "
            + "SET source = #{rel.source}, confidence = NULL, reason = #{rel.reason}, "
            + "confirmed_by = #{rel.confirmedBy}, update_time = CURRENT_TIMESTAMP, is_deleted = 0 "
            + "WHERE enterprise_id = #{rel.enterpriseId} AND node_id = #{rel.nodeId} "
            + "AND is_deleted = 1")
```

改为：

```java
    @Update("UPDATE enterprise_node_rel "
            + "SET source = #{rel.source}, confidence = #{rel.confidence}, reason = #{rel.reason}, "
            + "confirmed_by = #{rel.confirmedBy}, update_time = CURRENT_TIMESTAMP, is_deleted = 0 "
            + "WHERE enterprise_id = #{rel.enterpriseId} AND node_id = #{rel.nodeId} "
            + "AND is_deleted = 1")
```

说明：人工挂链构造的 `EnterpriseNodeRel` 不设置 `confidence`，因此仍会写入 `NULL`；AI 采纳构造的 `EnterpriseNodeRel` 会携带推荐置信度。

- [ ] **步骤 2：把 `linkByAi` 改为复用共享写入 helper**

把 `EnterpriseNodeRelServiceImpl.linkByAi` 中从 `EnterpriseNodeRel rel = new EnterpriseNodeRel();` 到 `save(rel);` 的代码替换为：

```java
        saveRelation(enterpriseId, nodeId, LinkSource.AI, confidence, reason, operator);
```

保留后面的日志：

```java
        log.info("AI推荐挂链成功: enterpriseId={}, nodeId={}, confidence={}",
                enterpriseId, nodeId, confidence);
```

- [ ] **步骤 3：替换人工保存 helper 为通用 helper**

把现有 `saveManualRelation` 方法整体替换为下面两个方法：

```java
    private void saveManualRelation(Long enterpriseId, Long nodeId, String reason, Long operator) {
        saveRelation(enterpriseId, nodeId, LinkSource.MANUAL, null, reason, operator);
        log.info("人工挂链成功: enterpriseId={}, nodeId={}", enterpriseId, nodeId);
    }

    private void saveRelation(Long enterpriseId,
                              Long nodeId,
                              LinkSource source,
                              BigDecimal confidence,
                              String reason,
                              Long operator) {
        EnterpriseNodeRel rel = new EnterpriseNodeRel();
        rel.setEnterpriseId(enterpriseId);
        rel.setNodeId(nodeId);
        rel.setSource(source.name());
        rel.setConfidence(confidence);
        rel.setReason(reason);
        rel.setConfirmedBy(operator);
        rel.setDeleted(0);

        if (baseMapper.restoreDeletedRelation(rel) > 0) {
            log.info("恢复挂链成功: enterpriseId={}, nodeId={}, source={}",
                    enterpriseId, nodeId, source.name());
            return;
        }

        save(rel);
    }
```

注意：`EnterpriseNodeRelServiceImpl` 已经导入 `java.math.BigDecimal` 和 `git.ggwzz.chain.enums.LinkSource`，无需新增 import。

- [ ] **步骤 4：运行任务 1 和任务 2 的测试验证通过**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.service.EnterpriseNodeRelServiceTest#linkByAi_shouldRestoreLogicallyDeletedRelation,git.ggwzz.chain.mapper.EnterpriseNodeRelMapperTest#restoreDeletedRelationShouldKeepAiConfidenceWhenSourceIsAi test
```

预期：PASS。两个新增测试都通过。

## 任务 4：回归既有人工恢复语义与采纳推荐调用链

**文件：**
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java:267-292`
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapperTest.java:30-68`
- 读取验证：`groupproject-back-commit/back/src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java:417-427`

- [ ] **步骤 1：运行人工恢复既有单测**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.service.EnterpriseNodeRelServiceTest#linkManuallyBatch_shouldRestoreLogicallyDeletedRelation test
```

预期：PASS。该测试确认人工批量挂链仍调用 `restoreDeletedRelation`，并且 `source=MANUAL`、`reason=人工挂链`、`confirmedBy=9`。

- [ ] **步骤 2：运行人工恢复既有 Mapper 集成测试**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.mapper.EnterpriseNodeRelMapperTest#restoreDeletedRelationShouldReactivateAndRefreshLinkMetadata test
```

预期：PASS。该测试确认人工恢复后 `confidence` 仍为 `NULL`，`create_time` 不被重写。

- [ ] **步骤 3：运行采纳推荐既有 Service 测试**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.matching.service.MatchingServiceTest#acceptResultShouldAcceptPendingResult test
```

预期：PASS。该测试确认 `acceptResult` 仍通过 `enterpriseNodeRelService.linkByAi(...)` 写正式挂链关系，接口层调用链不需要改动。

## 任务 5：跑相关测试套件并记录验证结果

**文件：**
- 验证：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java`
- 验证：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapperTest.java`
- 验证：`groupproject-back-commit/back/src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java`

- [ ] **步骤 1：运行企业-节点关系 Service 单测**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.service.EnterpriseNodeRelServiceTest test
```

预期：PASS。重点确认 AI 新恢复测试、人工批量恢复测试、重复挂链冲突测试同时通过。

- [ ] **步骤 2：运行企业-节点关系 Mapper 集成测试**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.mapper.EnterpriseNodeRelMapperTest test
```

预期：PASS。重点确认人工恢复 `confidence` 为 `NULL`，AI 恢复 `confidence` 为推荐值。

- [ ] **步骤 3：运行匹配服务相关测试**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.matching.service.MatchingServiceTest test
```

预期：PASS。确认采纳推荐、人工改选、撤回等调用企业-节点关系服务的流程不受影响。

- [ ] **步骤 4：运行完整后端测试**

运行：

```bash
cd groupproject-back-commit/back && mvn test
```

预期：PASS。若失败，只处理与本次修改相关的失败；与本次修改无关的既有失败要在交付说明中列出，不要扩大修复范围。

## 任务 6：最终检查与提交说明

**文件：**
- 检查：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/EnterpriseNodeRelServiceImpl.java`
- 检查：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java`
- 检查：相关测试文件

- [ ] **步骤 1：检查差异只包含本问题相关修改**

运行：

```bash
cd groupproject-back-commit/back && git diff -- src/main/java/git/ggwzz/chain/service/impl/EnterpriseNodeRelServiceImpl.java src/main/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapper.java src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java src/test/java/git/ggwzz/chain/mapper/EnterpriseNodeRelMapperTest.java
```

预期：diff 只包含：新增 AI 恢复测试、Mapper 恢复 `confidence` 改为参数值、Service 共享恢复/插入 helper。

- [ ] **步骤 2：准备提交信息但不要自动提交**

本项目记忆要求默认不要 `git add/commit/push`。除非用户明确要求提交，否则停留在工作区修改并在最终回复中建议提交信息：

```text
fix: restore deleted enterprise-node relation for AI acceptance
```

- [ ] **步骤 3：交付说明**

最终回复必须包含：

```markdown
已修复 AI 采纳历史逻辑删除挂链时的唯一索引冲突。

修改点：
- `EnterpriseNodeRelServiceImpl`：AI 和人工挂链共用恢复历史关系的写入路径。
- `EnterpriseNodeRelMapper`：恢复历史关系时写入调用方传入的 confidence，人工路径仍为 NULL。
- 测试：新增 AI 恢复历史关系的 Service 单测和 Mapper 集成测试。

验证：
- `mvn -Dtest=git.ggwzz.chain.service.EnterpriseNodeRelServiceTest test`：PASS
- `mvn -Dtest=git.ggwzz.chain.mapper.EnterpriseNodeRelMapperTest test`：PASS
- `mvn -Dtest=git.ggwzz.matching.service.MatchingServiceTest test`：PASS
- `mvn test`：PASS 或列出失败原因
```

## 自检结果

- 规格覆盖度：覆盖了 bug 触发路径（AI 采纳 → `linkByAi` → `enterprise_node_rel` 唯一索引）、修复点（共享恢复路径）和回归点（人工恢复仍清空 confidence）。
- 占位符扫描：未使用“待定/TODO/后续实现”等占位内容；所有代码步骤给出具体代码块。
- 类型一致性：计划中使用的类型均已存在：`EnterpriseNodeRel`、`LinkSource`、`BigDecimal`、`restoreDeletedRelation`、`linkByAi`。
- 范围检查：只修复第三个问题，不涉及 JWT 权限快照或异步取消竞态。
