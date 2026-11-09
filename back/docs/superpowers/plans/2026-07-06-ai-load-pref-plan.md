# 智能挂链与 AI 助手选项加载性能优化 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 解决“智能挂链”和“AI 聊天助手”读取企业列表、产业链列表慢的问题，把“下拉框选项”与“管理列表”解耦，前端共享选项缓存并懒加载，后端新增轻量 options 接口并消除企业列表 N+1 标签查询。

**架构：** 后端新增 `EnterpriseOptionVO` / `ChainOptionVO` / `ChainNodeCandidateVO` 三组轻量投影 VO 与对应 Controller/Service/Mapper 方法，复用现有 `EnterpriseController` / `ChainController`，复用现有 Service 与 Mapper，但走只查询必要列的命名 SQL。前端新增 `useOptionsStore`（Composition API 风格的 Pinia store）统一缓存企业、产业链、节点候选选项，带 TTL 与 in-flight 去重；`MatchingTask.vue` 改为切到“人工智能分析”tab 时才懒加载，且企业远程搜索加防抖；`AiAssistantPanel.vue` 改为复用 `useOptionsStore`。

**技术栈：** 后端 Java 21、Spring Boot 3.5.15、MyBatis-Plus 3.5.7、MySQL 5.7；前端 Vue 3、TypeScript、Vite 5、Pinia、Element Plus、Vitest。

---

## 文件结构

### 后端（`groupproject-back-commit/back`）

- 创建：`src/main/java/git/ggwzz/enterprise/vo/EnterpriseOptionVO.java`
  - 职责：企业选项投影 VO，只含 `id` / `name`。
- 创建：`src/main/java/git/ggwzz/enterprise/mapper/EnterpriseOptionMapper.java`
  - 职责：企业选项查询 Mapper，命名 `@Select` 只查 `id, enterprise_name`。
- 创建：`src/test/java/git/ggwzz/enterprise/mapper/EnterpriseOptionMapperTest.java`
  - 职责：H2/MyBatis Mapper 集成测试，验证按 keyword 前缀过滤与 limit。
- 修改：`src/main/java/git/ggwzz/enterprise/service/EnterpriseService.java`
  - 职责：新增 `listOptions(keyword, limit)` 方法签名。
- 修改：`src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseServiceImpl.java`
  - 职责：实现 `listOptions`，调用 `EnterpriseOptionMapper`；并把 `list` 中逐条 `getTagsByEnterpriseId` 改成批量加载，消除 N+1。
- 修改：`src/main/java/git/ggwzz/enterprise/controller/EnterpriseController.java`
  - 职责：新增 `GET /api/enterprises/options`。
- 创建：`src/main/java/git/ggwzz/chain/vo/ChainOptionVO.java`
  - 职责：产业链选项投影 VO，只含 `id` / `name`。
- 创建：`src/main/java/git/ggwzz/chain/vo/ChainNodeCandidateVO.java`
  - 职责：节点候选投影 VO，含 `id` / `name` / `nodePath` / `keywords` / `description`，不含企业数统计。
- 创建：`src/main/java/git/ggwzz/chain/mapper/ChainOptionMapper.java`
  - 职责：产业链选项查询 Mapper，只查 `id, chain_name`。
- 创建：`src/main/java/git/ggwzz/chain/mapper/ChainNodeCandidateMapper.java`
  - 职责：节点候选查询 Mapper，按 `chainId` 查全部未删除节点，只查必要列。
- 创建：`src/test/java/git/ggwzz/chain/mapper/ChainOptionMapperTest.java`
  - 职责：产业链选项 Mapper 集成测试。
- 创建：`src/test/java/git/ggwzz/chain/mapper/ChainNodeCandidateMapperTest.java`
  - 职责：节点候选 Mapper 集成测试。
- 修改：`src/main/java/git/ggwzz/chain/service/ChainService.java`
  - 职责：新增 `listChainOptions()` 与 `listNodeCandidates(chainId)` 方法签名。
- 修改：`src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
  - 职责：实现两个新方法，分别调用对应 Mapper，不触发节点数/企业数统计。
- 修改：`src/main/java/git/ggwzz/chain/controller/ChainController.java`
  - 职责：新增 `GET /api/chains/options` 与 `GET /api/chains/{chainId}/node-options`。
- 创建：`src/main/resources/db/migration/V20__options_query_indexes.sql`
  - 职责：为选项查询路径补复合索引（企业、节点、关系表）。

### 前端（`groupproject-front/front`）

- 修改：`src/types/domain.ts`
  - 职责：新增 `EnterpriseOption` / `ChainOption` / `ChainNodeCandidate` 类型。
- 修改：`src/api/enterprise.ts`
  - 职责：新增 `fetchEnterpriseOptions(keyword, limit)`。
- 修改：`src/api/chain.ts`
  - 职责：新增 `fetchChainOptions()` 与 `fetchChainNodeCandidates(chainId)`。
- 创建：`src/stores/options.ts`
  - 职责：Composition API 风格 Pinia store，缓存企业/产业链/节点候选，TTL + in-flight 去重。
- 创建：`src/stores/options.spec.ts`
  - 职责：store 单测，验证 TTL 命中、in-flight 去重、keyword 不缓存。
- 修改：`src/views/matching/MatchingTask.vue`
  - 职责：onMounted 不再默认加载企业/产业链/节点树；切到“人工智能分析”tab 时通过 `useOptionsStore` 懒加载；企业远程搜索加防抖。
- 修改：`src/views/assistant/AiAssistantPanel.vue`
  - 职责：移除局部 `enterprises` / `chains` / `optionsLoaded`，改用 `useOptionsStore`。
- 创建：`src/api/enterprise.spec.ts`
  - 职责：验证 `fetchEnterpriseOptions` 调用 `/enterprises/options`。
- 修改：`src/api/chain.spec.ts`
  - 职责：验证 `fetchChainOptions` 与 `fetchChainNodeCandidates` 的 URL 与参数映射。

---

## 任务 1：后端企业选项 VO 与 Mapper

**文件：**
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/vo/EnterpriseOptionVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/mapper/EnterpriseOptionMapper.java`
- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/mapper/EnterpriseOptionMapperTest.java`

- [ ] **步骤 1：编写失败的 Mapper 集成测试**

创建 `EnterpriseOptionMapperTest.java`：

```java
package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class EnterpriseOptionMapperTest {

    @Autowired
    private EnterpriseOptionMapper enterpriseOptionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchByKeywordShouldReturnOnlyIdAndNameForActiveEnterprises() {
        jdbcTemplate.update(
                "UPDATE enterprise SET status = 'ACTIVE' WHERE id IN (1, 2)");

        List<EnterpriseOptionVO> options =
                enterpriseOptionMapper.searchActiveOptions("天齐", 20);

        assertEquals(1, options.size());
        EnterpriseOptionVO option = options.get(0);
        assertEquals(1L, option.getId());
        assertEquals("天齐锂业股份有限公司", option.getName());
    }

    @Test
    void searchByKeywordShouldRespectLimit() {
        jdbcTemplate.update(
                "UPDATE enterprise SET status = 'ACTIVE' WHERE id IN (1, 2, 3)");

        List<EnterpriseOptionVO> options =
                enterpriseOptionMapper.searchActiveOptions("", 2);

        assertTrue(options.size() <= 2);
    }

    @Test
    void searchByKeywordShouldReturnAllActiveWhenKeywordBlank() {
        jdbcTemplate.update(
                "UPDATE enterprise SET status = 'ACTIVE' WHERE id IN (1, 2, 3)");

        List<EnterpriseOptionVO> options =
                enterpriseOptionMapper.searchActiveOptions(null, 20);

        assertTrue(options.size() >= 3);
    }
}
```

> 注：测试数据来自 `src/test/resources/data.sql`，其中 `id=1` 为“天齐锂业股份有限公司”，初始状态已是 `ACTIVE`；`id=2` 为宁德时代、`id=3` 为比亚迪，初始也是 `ACTIVE`。UPDATE 语句作为幂等保护，避免未来 data.sql 变更后测试误判。

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.enterprise.mapper.EnterpriseOptionMapperTest test
```

预期：FAIL，编译失败，`EnterpriseOptionMapper` / `EnterpriseOptionVO` 不存在。

- [ ] **步骤 3：创建 EnterpriseOptionVO**

创建 `EnterpriseOptionVO.java`：

```java
package git.ggwzz.enterprise.vo;

import lombok.Data;

/**
 * 企业选项投影 VO，只含下拉框需要的 id 与名称。
 *
 * <p>与企业列表 VO 区分：不返回经营范围、主营产品、标签等大字段，
 * 专用于智能挂链、AI 助手等下拉选项场景，避免加载完整企业实体。</p>
 */
@Data
public class EnterpriseOptionVO {

    /** 企业 ID */
    private Long id;

    /** 企业名称 */
    private String name;
}
```

- [ ] **步骤 4：创建 EnterpriseOptionMapper**

创建 `EnterpriseOptionMapper.java`：

```java
package git.ggwzz.enterprise.mapper;

import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 企业选项查询 Mapper。
 *
 * <p>只查询 id 与 enterprise_name，按名称前缀过滤，避免加载企业实体的大文本字段。</p>
 */
@Mapper
public interface EnterpriseOptionMapper {

    /**
     * 按名称前缀搜索 ACTIVE 企业选项。
     *
     * <p>keyword 为空时返回全部 ACTIVE 企业，受 limit 限制。</p>
     *
     * @param keyword 关键词，仅匹配企业名称前缀；可为 null/空
     * @param limit   最大返回条数
     * @return 企业选项列表
     */
    @Select("<script>"
            + "SELECT id, enterprise_name AS name FROM enterprise "
            + "WHERE is_deleted = 0 AND status = 'ACTIVE' "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND enterprise_name LIKE CONCAT(#{keyword}, '%') "
            + "</if>"
            + "ORDER BY create_time DESC, id DESC "
            + "LIMIT #{limit}"
            + "</script>")
    List<EnterpriseOptionVO> searchActiveOptions(
            @Param("keyword") String keyword,
            @Param("limit") int limit);
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.enterprise.mapper.EnterpriseOptionMapperTest test
```

预期：PASS。

- [ ] **步骤 6：暂不提交，进入任务 2**

本任务锁定 Mapper 层 SQL 语义，不在此任务暴露 Controller。

## 任务 2：后端企业选项 Service 与 Controller

**文件：**
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/EnterpriseService.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseServiceImpl.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/controller/EnterpriseController.java`

- [ ] **步骤 1：在 Service 接口新增方法签名**

在 `EnterpriseService.java` 接口中追加：

```java
    /**
     * 查询 ACTIVE 企业选项（仅 id 与名称），用于下拉框。
     *
     * @param keyword 关键词，仅匹配企业名称前缀；为空时返回全部 ACTIVE
     * @param limit   最大返回条数
     * @return 企业选项列表
     */
    List<EnterpriseOptionVO> listOptions(String keyword, int limit);
```

并在文件顶部补 import：

```java
import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import java.util.List;
```

- [ ] **步骤 2：在 EnterpriseServiceImpl 注入 Mapper 并实现**

在 `EnterpriseServiceImpl.java` 的字段区追加：

```java
    private final EnterpriseOptionMapper enterpriseOptionMapper;
```

顶部补 import：

```java
import git.ggwzz.enterprise.mapper.EnterpriseOptionMapper;
import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
```

在类内追加方法：

```java
    @Override
    public List<EnterpriseOptionVO> listOptions(String keyword, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return enterpriseOptionMapper.searchActiveOptions(safeKeyword, safeLimit);
    }
```

- [ ] **步骤 3：在 EnterpriseController 暴露 GET /api/enterprises/options**

在 `EnterpriseController.java` 追加方法：

```java
    /**
     * 企业下拉选项（仅 ACTIVE，仅 id 与名称）。
     *
     * <p>供智能挂链、AI 助手等下拉框使用，避免加载完整企业列表 VO。</p>
     *
     * @param keyword 关键词，仅匹配企业名称前缀；可选
     * @param limit   最大返回条数，默认 20，最大 100
     * @return 企业选项列表
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('enterprise:enterprise:query')")
    public Result<List<EnterpriseOptionVO>> options(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(enterpriseService.listOptions(keyword, limit));
    }
```

顶部补 import：

```java
import git.ggwzz.enterprise.vo.EnterpriseOptionVO;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
```

- [ ] **步骤 4：编译并跑企业相关测试**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.enterprise.* test
```

预期：PASS，编译通过，企业单测与 Mapper 测试通过。

## 任务 3：后端消除企业列表 N+1 标签查询

**文件：**
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseServiceImpl.java`
- 修改：`groupproject-back-commit/back/src/test/java/git/ggwzz/enterprise/service/EnterpriseServiceTest.java`

- [ ] **步骤 1：编写失败的 Service 单测**

在 `EnterpriseServiceTest.java` 中新增用例，验证列表查询期间 `tagMapper` 只被批量调用一次而非每条一次：

```java
    @Test
    void list_shouldBatchLoadTagsInsteadOfPerEnterpriseQueries() {
        when(enterpriseMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<Enterprise> page = invocation.getArgument(0);
            Enterprise e1 = new Enterprise();
            e1.setId(1L);
            e1.setStatus("ACTIVE");
            Enterprise e2 = new Enterprise();
            e2.setId(2L);
            e2.setStatus("ACTIVE");
            page.setRecords(java.util.List.of(e1, e2));
            page.setTotal(2);
            return page;
        });
        when(enterpriseTagRelMapper.selectList(any())).thenReturn(java.util.List.of());
        when(tagMapper.selectBatchIds(any())).thenReturn(java.util.List.of());

        enterpriseService.list(new EnterpriseQueryRequest());

        // 批量加载只调用一次 tagMapper.selectBatchIds，而非每条企业一次
        verify(tagMapper, times(1)).selectBatchIds(any());
        verify(enterpriseTagRelMapper, times(1)).selectList(any());
    }
}
```

> 注：先 Read `EnterpriseServiceTest.java` 确认其现有 mock 与构造方式（`@ExtendWith(MockitoExtension.class)` 还是手写 `new EnterpriseServiceImpl(...)`），按现有模式对齐字段注入与 stub。若该测试类用 `@Mock` 注入 `EnterpriseMapper` / `EnterpriseTagRelMapper` / `TagMapper`，则直接复用。

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.enterprise.service.EnterpriseServiceTest#list_shouldBatchLoadTagsInsteadOfPerEnterpriseQueries test
```

预期：FAIL，`tagMapper` 被调用多次而非 1 次（现有 `list` 走 `getTagsByEnterpriseId` 逐条查）。

- [ ] **步骤 3：实现批量加载标签**

在 `EnterpriseServiceImpl.java` 新增私有方法：

```java
    /**
     * 批量查询多企业的标签，避免列表接口逐条查询形成 N+1。
     *
     * @param enterpriseIds 企业 ID 集合
     * @return enterpriseId -> 标签列表
     */
    private Map<Long, List<TagVO>> loadTagsByEnterpriseIds(List<Long> enterpriseIds) {
        if (enterpriseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EnterpriseTagRel> rels = enterpriseTagRelMapper.selectList(
                new LambdaQueryWrapper<EnterpriseTagRel>()
                        .in(EnterpriseTagRel::getEnterpriseId, enterpriseIds));
        if (rels.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> tagIds = rels.stream()
                .map(EnterpriseTagRel::getTagId)
                .collect(Collectors.toSet());
        Map<Long, Tag> tagMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));
        Map<Long, List<TagVO>> result = new HashMap<>();
        for (EnterpriseTagRel rel : rels) {
            Tag tag = tagMap.get(rel.getTagId());
            if (tag != null) {
                result.computeIfAbsent(rel.getEnterpriseId(), k -> new ArrayList<>())
                        .add(toTagVO(tag));
            }
        }
        return result;
    }
```

并新增 `toTagVO`：

```java
    private TagVO toTagVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getTagName());
        vo.setType(tag.getTagType());
        vo.setColor(tag.getColor());
        return vo;
    }
```

> 若 `TagVO` 字段名与上述不一致，先 Read `tag/vo/TagVO.java` 按实际定义对齐。

修改 `list` 方法的 VO 构造段，把：

```java
        List<EnterpriseVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
```

改为：

```java
        List<Long> enterpriseIds = result.getRecords().stream()
                .map(Enterprise::getId)
                .collect(Collectors.toList());
        Map<Long, List<TagVO>> tagMap = loadTagsByEnterpriseIds(enterpriseIds);
        List<EnterpriseVO> voList = result.getRecords().stream()
                .map(e -> toVO(e, tagMap.getOrDefault(e.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
```

把 `toVO(Enterprise e)` 改为 `toVO(Enterprise e, List<TagVO> tags)`：

```java
    private EnterpriseVO toVO(Enterprise e, List<TagVO> tags) {
        EnterpriseVO vo = new EnterpriseVO();
        vo.setId(e.getId());
        vo.setName(e.getEnterpriseName());
        vo.setCreditCode(e.getUnifiedCode());
        vo.setLegalPerson(e.getLegalPerson());
        vo.setIndustry(e.getIndustry());
        vo.setAddress(e.getAddress());
        vo.setBusinessScope(e.getBusinessScope());
        vo.setMainProducts(e.getMainProducts());
        vo.setOutputValue(e.getOutputValue());
        vo.setTaxAmount(e.getTaxAmount());
        vo.setStatus(e.getStatus());
        vo.setTags(tags);
        vo.setCreatedAt(e.getCreateTime());
        vo.setUpdatedAt(e.getUpdateTime());
        return vo;
    }
```

`toDetailVO` 仍可调用 `getTagsByEnterpriseId`，单条详情无 N+1 问题，保持不变。

补 import：

```java
import git.ggwzz.tag.entity.Tag;
import java.util.ArrayList;
import java.util.HashMap;
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.enterprise.service.EnterpriseServiceTest test
```

预期：PASS。

- [ ] **步骤 5：运行企业相关全量测试回归**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.enterprise.* test
```

预期：PASS。

## 任务 4：后端产业链选项 VO 与 Mapper

**文件：**
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/vo/ChainOptionVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/mapper/ChainOptionMapper.java`
- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/mapper/ChainOptionMapperTest.java`

- [ ] **步骤 1：编写失败的 Mapper 集成测试**

```java
package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainOptionVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class ChainOptionMapperTest {

    @Autowired
    private ChainOptionMapper chainOptionMapper;

    @Test
    void listOptionsShouldReturnOnlyIdAndNameWithoutStats() {
        List<ChainOptionVO> options = chainOptionMapper.listOptions();

        assertTrue(options.size() >= 2);
        ChainOptionVO first = options.get(0);
        assertEquals(2L, first.getId());
        assertEquals("半导体产业链", first.getName());
    }
}
```

> 注：`data.sql` 中 `id=1` 为“锂电池产业链”、`id=2` 为“半导体产业链”；`listOptions` 按 `id DESC` 排序，首项为 `id=2`。

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.mapper.ChainOptionMapperTest test
```

预期：FAIL，编译失败。

- [ ] **步骤 3：创建 ChainOptionVO**

```java
package git.ggwzz.chain.vo;

import lombok.Data;

/**
 * 产业链选项投影 VO，只含 id 与名称。
 *
 * <p>不触发节点数、企业数统计，专用于下拉框。</p>
 */
@Data
public class ChainOptionVO {

    /** 产业链 ID */
    private Long id;

    /** 产业链名称 */
    private String name;
}
```

- [ ] **步骤 4：创建 ChainOptionMapper**

```java
package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainOptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产业链选项查询 Mapper，只查 id 与 chain_name，不统计节点/企业数。
 */
@Mapper
public interface ChainOptionMapper {

    /**
     * 查询全部未删除产业链选项，按 id 倒序。
     *
     * @return 产业链选项列表
     */
    @Select("SELECT id, chain_name AS name FROM chain "
            + "WHERE is_deleted = 0 ORDER BY id DESC")
    List<ChainOptionVO> listOptions();
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.mapper.ChainOptionMapperTest test
```

预期：PASS。

## 任务 5：后端节点候选 VO 与 Mapper

**文件：**
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/vo/ChainNodeCandidateVO.java`
- 创建：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/mapper/ChainNodeCandidateMapper.java`
- 创建：`groupproject-back-commit/back/src/test/java/git/ggwzz/chain/mapper/ChainNodeCandidateMapperTest.java`

- [ ] **步骤 1：编写失败的 Mapper 集成测试**

```java
package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("unittest")
@Transactional
class ChainNodeCandidateMapperTest {

    @Autowired
    private ChainNodeCandidateMapper chainNodeCandidateMapper;

    @Test
    void listByChainIdShouldReturnCandidatesWithoutEnterpriseStats() {
        List<ChainNodeCandidateVO> candidates =
                chainNodeCandidateMapper.listByChainId(1L);

        assertFalse(candidates.isEmpty());
        ChainNodeCandidateVO first = candidates.get(0);
        assertEquals(1L, first.getChainId());
        assertNotNull(first.getName());
        assertNotNull(first.getNodePath());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.mapper.ChainNodeCandidateMapperTest test
```

预期：FAIL，编译失败。

- [ ] **步骤 3：创建 ChainNodeCandidateVO**

```java
package git.ggwzz.chain.vo;

import lombok.Data;

/**
 * 产业链节点候选投影 VO。
 *
 * <p>只含智能挂链候选节点需要的字段，不含直接企业数、子树企业数等统计，
 * 避免触发子树聚合 SQL。</p>
 */
@Data
public class ChainNodeCandidateVO {

    /** 节点 ID */
    private Long id;

    /** 所属产业链 ID */
    private Long chainId;

    /** 节点名称 */
    private String name;

    /** 节点路径，如 /5/6/7/ */
    private String nodePath;

    /** 关键词，逗号分隔 */
    private String keywords;

    /** 节点定义/描述 */
    private String description;
}
```

- [ ] **步骤 4：创建 ChainNodeCandidateMapper**

```java
package git.ggwzz.chain.mapper;

import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产业链节点候选查询 Mapper。
 *
 * <p>按 chainId 查询全部未删除节点，只查必要列，不计算企业数统计。</p>
 */
@Mapper
public interface ChainNodeCandidateMapper {

    /**
     * 查询某产业链下全部未删除节点候选，按 sort_order、id 排序。
     *
     * @param chainId 产业链 ID
     * @return 节点候选列表
     */
    @Select("SELECT id, chain_id, node_name AS name, node_path, keywords, description "
            + "FROM chain_node WHERE chain_id = #{chainId} AND is_deleted = 0 "
            + "ORDER BY sort_order, id")
    List<ChainNodeCandidateVO> listByChainId(@Param("chainId") Long chainId);
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.mapper.ChainNodeCandidateMapperTest test
```

预期：PASS。

## 任务 6：后端产业链选项与节点候选 Service、Controller

**文件：**
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/ChainService.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java`
- 修改：`groupproject-back-commit/back/src/main/java/git/ggwzz/chain/controller/ChainController.java`

- [ ] **步骤 1：在 ChainService 接口新增方法签名**

追加：

```java
    /**
     * 查询全部产业链选项（仅 id 与名称），用于下拉框。
     *
     * @return 产业链选项列表
     */
    List<ChainOptionVO> listChainOptions();

    /**
     * 查询某产业链下全部节点候选（不含企业数统计），用于智能挂链候选范围。
     *
     * @param chainId 产业链 ID
     * @return 节点候选列表
     */
    List<ChainNodeCandidateVO> listNodeCandidates(Long chainId);
```

补 import：

```java
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import java.util.List;
```

- [ ] **步骤 2：在 ChainServiceImpl 注入 Mapper 并实现**

字段区追加：

```java
    private final ChainOptionMapper chainOptionMapper;
    private final ChainNodeCandidateMapper chainNodeCandidateMapper;
```

补 import：

```java
import git.ggwzz.chain.mapper.ChainOptionMapper;
import git.ggwzz.chain.mapper.ChainNodeCandidateMapper;
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.ChainNodeCandidateVO;
```

追加方法：

```java
    @Override
    public List<ChainOptionVO> listChainOptions() {
        return chainOptionMapper.listOptions();
    }

    @Override
    public List<ChainNodeCandidateVO> listNodeCandidates(Long chainId) {
        if (chainId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产业链ID不能为空");
        }
        return chainNodeCandidateMapper.listByChainId(chainId);
    }
```

- [ ] **步骤 3：在 ChainController 暴露两个 GET 接口**

追加：

```java
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('chain:chain:query')")
    public Result<List<ChainOptionVO>> listChainOptions() {
        return Result.success(chainService.listChainOptions());
    }

    @GetMapping("/{chainId}/node-options")
    @PreAuthorize("hasAuthority('chain:node:query')")
    public Result<List<ChainNodeCandidateVO>> listNodeCandidates(
            @PathVariable Long chainId) {
        return Result.success(chainService.listNodeCandidates(chainId));
    }
```

补 import：

```java
import git.ggwzz.chain.vo.ChainOptionVO;
import git.ggwzz.chain.vo.ChainNodeCandidateVO;
import java.util.List;
```

- [ ] **步骤 4：编译并跑产业链相关测试**

运行：

```bash
cd groupproject-back-commit/back && mvn -Dtest=git.ggwzz.chain.* test
```

预期：PASS。

## 任务 7：后端补选项查询索引迁移

**文件：**
- 创建：`groupproject-back-commit/back/src/main/resources/db/migration/V20__options_query_indexes.sql`
- 修改：`groupproject-back-commit/back/src/test/resources/schema.sql`

- [ ] **步骤 1：创建迁移脚本**

```sql
-- =====================================================================
-- 选项查询性能索引
-- 版本: V20
-- 说明: 为智能挂链、AI 助手的下拉选项查询路径补复合索引。
--       禁止外键；逻辑删除沿用 is_deleted；MySQL 5.7 兼容。
-- =====================================================================

-- 企业：WHERE status='ACTIVE' AND is_deleted=0 ORDER BY create_time DESC, id DESC
ALTER TABLE `enterprise`
    ADD KEY `idx_enterprise_status_deleted_create_time`
        (`status`, `is_deleted`, `create_time`, `id`);

-- 企业：按名称前缀搜索
ALTER TABLE `enterprise`
    ADD KEY `idx_enterprise_status_deleted_name`
        (`status`, `is_deleted`, `enterprise_name`, `id`);

-- 节点：WHERE chain_id=? AND is_deleted=0 ORDER BY sort_order, id
ALTER TABLE `chain_node`
    ADD KEY `idx_chain_node_chain_deleted_sort`
        (`chain_id`, `is_deleted`, `sort_order`, `id`);

-- 节点：子树统计 node_path 前缀
ALTER TABLE `chain_node`
    ADD KEY `idx_chain_node_chain_deleted_path`
        (`chain_id`, `is_deleted`, `node_path`);

-- 关系：按节点统计活跃企业
ALTER TABLE `enterprise_node_rel`
    ADD KEY `idx_rel_node_deleted_enterprise`
        (`node_id`, `is_deleted`, `enterprise_id`);

-- 关系：按企业查挂链
ALTER TABLE `enterprise_node_rel`
    ADD KEY `idx_rel_enterprise_deleted_node`
        (`enterprise_id`, `is_deleted`, `node_id`);
```

> 注：先 Read `db/migration` 目录确认 V20 未被占用；若已有 V20，递增到下一个可用版本号。

- [ ] **步骤 2：同步更新 H2 测试 schema**

修改 `src/test/resources/schema.sql`，在对应表 DDL 后追加同名索引（H2 兼容）：

```sql
CREATE INDEX IF NOT EXISTS idx_enterprise_status_deleted_create_time
    ON enterprise(status, is_deleted, create_time, id);
CREATE INDEX IF NOT EXISTS idx_enterprise_status_deleted_name
    ON enterprise(status, is_deleted, enterprise_name, id);
CREATE INDEX IF NOT EXISTS idx_chain_node_chain_deleted_sort
    ON chain_node(chain_id, is_deleted, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_chain_node_chain_deleted_path
    ON chain_node(chain_id, is_deleted, node_path);
CREATE INDEX IF NOT EXISTS idx_rel_node_deleted_enterprise
    ON enterprise_node_rel(node_id, is_deleted, enterprise_id);
CREATE INDEX IF NOT EXISTS idx_rel_enterprise_deleted_node
    ON enterprise_node_rel(enterprise_id, is_deleted, node_id);
```

- [ ] **步骤 3：跑后端全量测试确认 H2 不报错**

运行：

```bash
cd groupproject-back-commit/back && mvn test
```

预期：PASS（既有测试不受索引影响）。

## 任务 8：前端类型与 API 封装

**文件：**
- 修改：`groupproject-front/front/src/types/domain.ts`
- 修改：`groupproject-front/front/src/api/enterprise.ts`
- 修改：`groupproject-front/front/src/api/chain.ts`
- 修改：`groupproject-front/front/src/api/mock.ts`
- 创建：`groupproject-front/front/src/api/enterprise.spec.ts`
- 修改：`groupproject-front/front/src/api/chain.spec.ts`

- [ ] **步骤 1：在 domain.ts 新增类型**

在 `src/types/domain.ts` 追加：

```ts
/** 企业下拉选项，仅 id 与名称 */
export interface EnterpriseOption {
  id: number;
  name: string;
}

/** 产业链下拉选项，仅 id 与名称 */
export interface ChainOption {
  id: number;
  name: string;
}

/** 智能挂链节点候选，不含企业数统计 */
export interface ChainNodeCandidate {
  id: number;
  chainId: number;
  name: string;
  nodePath: string;
  keywords: string | null;
  description: string | null;
}
```

- [ ] **步骤 2：在 mock.ts 新增占位实现**

在 `src/api/mock.ts` 追加（mock 模式不报错，返回空数组）：

```ts
export function fetchEnterpriseOptions(_keyword: string, _limit: number): Promise<EnterpriseOption[]> {
  return Promise.resolve([]);
}

export function fetchChainOptions(): Promise<ChainOption[]> {
  return Promise.resolve([]);
}

export function fetchChainNodeCandidates(_chainId: number): Promise<ChainNodeCandidate[]> {
  return Promise.resolve([]);
}
```

并在 mock.ts 顶部按需补 import：

```ts
import type { EnterpriseOption, ChainOption, ChainNodeCandidate } from "@/types/domain";
```

- [ ] **步骤 3：在 enterprise.ts 新增 fetchEnterpriseOptions**

在 `src/api/enterprise.ts` 追加：

```ts
export function fetchEnterpriseOptions(
  keyword: string,
  limit = 20,
): Promise<EnterpriseOption[]> {
  if (isMockApiEnabled()) return mockApi.fetchEnterpriseOptions(keyword, limit);
  return get("/enterprises/options", {
    params: { keyword: keyword || undefined, limit },
  });
}
```

并在文件顶部 import 补 `EnterpriseOption`：

```ts
import type {
  EnterpriseDetail,
  EnterpriseImportRecord,
  EnterpriseListItem,
  EnterpriseOption,
  EnterprisePayload,
  PageQuery,
  PageResult,
} from "@/types/domain";
```

- [ ] **步骤 4：在 chain.ts 新增两个 API**

在 `src/api/chain.ts` 追加：

```ts
export function fetchChainOptions(): Promise<ChainOption[]> {
  if (isMockApiEnabled()) return mockApi.fetchChainOptions();
  return get<ChainOption[]>("/chains/options");
}

export function fetchChainNodeCandidates(
  chainId: number,
): Promise<ChainNodeCandidate[]> {
  if (isMockApiEnabled()) return mockApi.fetchChainNodeCandidates(chainId);
  return get<ChainNodeCandidate[]>(`/chains/${chainId}/node-options`);
}
```

补 import：

```ts
import type {
  BatchLinkEnterpriseResult,
  ChainNode,
  ChainNodeCandidate,
  ChainNodePayload,
  ChainOption,
  ChainStageType,
  ChainPayload,
  IndustryChain,
  MoveNodeRequest,
  NodeEnterpriseResult,
  PageResult,
  SortNodesRequest,
  UpdateChainNodePayload,
} from "@/types/domain";
```

- [ ] **步骤 5：编写 enterprise API 单测**

`src/api/enterprise.spec.ts`：

```ts
import { describe, expect, it, vi, beforeEach } from "vitest";
import { fetchEnterpriseOptions } from "./enterprise";
import { get } from "./http";

vi.mock("./http", () => ({ get: vi.fn() }));
vi.mock("./mode", () => ({ isMockApiEnabled: () => false }));

describe("enterprise options API", () => {
  beforeEach(() => vi.mocked(get).mockReset());

  it("requests /enterprises/options with keyword and limit", async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1, name: "宁德时代" }]);

    const result = await fetchEnterpriseOptions("宁德", 20);

    expect(get).toHaveBeenCalledWith("/enterprises/options", {
      params: { keyword: "宁德", limit: 20 },
    });
    expect(result).toEqual([{ id: 1, name: "宁德时代" }]);
  });

  it("omits keyword param when blank", async () => {
    vi.mocked(get).mockResolvedValue([]);
    await fetchEnterpriseOptions("");
    expect(get).toHaveBeenCalledWith("/enterprises/options", {
      params: { keyword: undefined, limit: 20 },
    });
  });
});
```

- [ ] **步骤 6：编写 chain API 单测**

在 `src/api/chain.spec.ts` 追加：

```ts
import { fetchChainOptions, fetchChainNodeCandidates } from "./chain";

describe("chain options API", () => {
  beforeEach(() => {
    vi.mocked(get).mockReset();
  });

  it("fetches chain options from /chains/options", async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1, name: "锂电池产业链" }]);
    const result = await fetchChainOptions();
    expect(get).toHaveBeenCalledWith("/chains/options");
    expect(result).toEqual([{ id: 1, name: "锂电池产业链" }]);
  });

  it("fetches node candidates from /chains/{id}/node-options", async () => {
    vi.mocked(get).mockResolvedValue([]);
    await fetchChainNodeCandidates(1);
    expect(get).toHaveBeenCalledWith("/chains/1/node-options");
  });
});
```

> 注：现有 `chain.spec.ts` 已 `vi.mock("./http")` 并暴露 `get`，直接复用。若其顶部 `beforeEach` 未 reset `get`，在新增 describe 内自行 reset。

- [ ] **步骤 7：运行前端 API 单测**

运行：

```bash
cd groupproject-front/front && npm run test -- src/api/enterprise.spec.ts src/api/chain.spec.ts
```

预期：PASS。

## 任务 9：前端 useOptionsStore

**文件：**
- 创建：`groupproject-front/front/src/stores/options.ts`
- 创建：`groupproject-front/front/src/stores/options.spec.ts`

- [ ] **步骤 1：编写失败的 store 单测**

`src/stores/options.spec.ts`：

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useOptionsStore } from "./options";
import * as enterpriseApi from "@/api/enterprise";
import * as chainApi from "@/api/chain";

vi.mock("@/api/enterprise", () => ({
  fetchEnterpriseOptions: vi.fn(),
}));
vi.mock("@/api/chain", () => ({
  fetchChainOptions: vi.fn(),
  fetchChainNodeCandidates: vi.fn(),
}));

describe("useOptionsStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockReset();
    vi.mocked(chainApi.fetchChainOptions).mockReset();
    vi.mocked(chainApi.fetchChainNodeCandidates).mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("caches enterprise options within TTL", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([
      { id: 1, name: "A" },
    ]);
    const store = useOptionsStore();

    await store.loadEnterpriseOptions();
    await store.loadEnterpriseOptions();

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(1);
    expect(store.enterpriseOptions).toEqual([{ id: 1, name: "A" }]);
  });

  it("re-fetches enterprise options after TTL expires", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([]);
    const store = useOptionsStore();

    await store.loadEnterpriseOptions();
    vi.advanceTimersByTime(6 * 60_000);
    await store.loadEnterpriseOptions();

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(2);
  });

  it("dedupes concurrent enterprise option requests", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([]);
    const store = useOptionsStore();

    await Promise.all([store.loadEnterpriseOptions(), store.loadEnterpriseOptions()]);

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(1);
  });

  it("does not cache keyword search results", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([]);
    const store = useOptionsStore();

    await store.searchEnterpriseOptions("宁德");
    await store.searchEnterpriseOptions("宁德");

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(2);
  });

  it("caches chain options within TTL", async () => {
    vi.mocked(chainApi.fetchChainOptions).mockResolvedValue([
      { id: 1, name: "C" },
    ]);
    const store = useOptionsStore();

    await store.loadChainOptions();
    await store.loadChainOptions();

    expect(chainApi.fetchChainOptions).toHaveBeenCalledTimes(1);
  });

  it("caches node candidates per chainId", async () => {
    vi.mocked(chainApi.fetchChainNodeCandidates).mockResolvedValue([]);
    const store = useOptionsStore();

    await store.loadNodeCandidates(1);
    await store.loadNodeCandidates(1);
    await store.loadNodeCandidates(2);

    expect(chainApi.fetchChainNodeCandidates).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
cd groupproject-front/front && npm run test -- src/stores/options.spec.ts
```

预期：FAIL，`useOptionsStore` 不存在。

- [ ] **步骤 3：实现 useOptionsStore**

`src/stores/options.ts`：

```ts
import { ref } from "vue";
import { defineStore } from "pinia";
import { fetchEnterpriseOptions } from "@/api/enterprise";
import { fetchChainOptions, fetchChainNodeCandidates } from "@/api/chain";
import type {
  ChainNodeCandidate,
  ChainOption,
  EnterpriseOption,
} from "@/types/domain";

const TTL_MS = 5 * 60_000;

interface NodeCandidateCacheEntry {
  loadedAt: number;
  items: ChainNodeCandidate[];
}

export const useOptionsStore = defineStore("options", () => {
  const enterpriseOptions = ref<EnterpriseOption[]>([]);
  const chainOptions = ref<ChainOption[]>([]);
  const nodeCandidates = ref<Record<number, NodeCandidateCacheEntry>>({});

  const enterpriseLoadedAt = ref(0);
  const chainLoadedAt = ref(0);

  let enterprisePromise: Promise<EnterpriseOption[]> | null = null;
  let chainPromise: Promise<ChainOption[]> | null = null;
  const nodePromise: Record<number, Promise<ChainNodeCandidate[]>> = {};

  async function loadEnterpriseOptions(): Promise<EnterpriseOption[]> {
    if (
      Date.now() - enterpriseLoadedAt.value < TTL_MS &&
      enterpriseOptions.value.length
    ) {
      return enterpriseOptions.value;
    }
    if (enterprisePromise) {
      return enterprisePromise;
    }
    enterprisePromise = fetchEnterpriseOptions("", 30)
      .then((items) => {
        enterpriseOptions.value = items;
        enterpriseLoadedAt.value = Date.now();
        return items;
      })
      .finally(() => {
        enterprisePromise = null;
      });
    return enterprisePromise;
  }

  async function searchEnterpriseOptions(
    keyword: string,
  ): Promise<EnterpriseOption[]> {
    return fetchEnterpriseOptions(keyword, 30);
  }

  async function loadChainOptions(): Promise<ChainOption[]> {
    if (
      Date.now() - chainLoadedAt.value < TTL_MS &&
      chainOptions.value.length
    ) {
      return chainOptions.value;
    }
    if (chainPromise) {
      return chainPromise;
    }
    chainPromise = fetchChainOptions()
      .then((items) => {
        chainOptions.value = items;
        chainLoadedAt.value = Date.now();
        return items;
      })
      .finally(() => {
        chainPromise = null;
      });
    return chainPromise;
  }

  async function loadNodeCandidates(
    chainId: number,
  ): Promise<ChainNodeCandidate[]> {
    const cached = nodeCandidates.value[chainId];
    if (cached && Date.now() - cached.loadedAt < TTL_MS) {
      return cached.items;
    }
    if (nodePromise[chainId]) {
      return nodePromise[chainId];
    }
    nodePromise[chainId] = fetchChainNodeCandidates(chainId)
      .then((items) => {
        nodeCandidates.value = {
          ...nodeCandidates.value,
          [chainId]: { loadedAt: Date.now(), items },
        };
        return items;
      })
      .finally(() => {
        delete nodePromise[chainId];
      });
    return nodePromise[chainId];
  }

  return {
    enterpriseOptions,
    chainOptions,
    nodeCandidates,
    loadEnterpriseOptions,
    searchEnterpriseOptions,
    loadChainOptions,
    loadNodeCandidates,
  };
});
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
cd groupproject-front/front && npm run test -- src/stores/options.spec.ts
```

预期：PASS。

## 任务 10：前端 MatchingTask 懒加载与防抖

**文件：**
- 修改：`groupproject-front/front/src/views/matching/MatchingTask.vue`
- 修改：`groupproject-front/front/src/views/matching/MatchingTask.spec.ts`（若存在且因数据源变更失败）

- [ ] **步骤 1：替换企业/产业链数据源为 options store**

在 `<script setup>` 顶部引入：

```ts
import { useOptionsStore } from "@/stores/options";
import { fetchEnterprise } from "@/api/enterprise";
```

删除原有局部 `enterprises` / `chains` / `enterpriseLoading` / `chainLoading` / `activeEnterpriseTotal` 与 `loadEnterprises` / `loadChains`，改为：

```ts
const optionsStore = useOptionsStore();
const enterprises = computed(() => optionsStore.enterpriseOptions);
const chains = computed(() => optionsStore.chainOptions);
```

> 若 `BatchWorkbench` 依赖 `enterprises`，保持其 props 不变，把 `enterprises` 计算结果传入；先 Read `BatchWorkbench.vue` 确认 props。

- [ ] **步骤 2：onMounted 只做最小初始化，去掉默认加载企业/产业链/节点树**

把原 `onMounted` 替换为：

```ts
onMounted(async () => {
  // 默认 tab 是“批量挂链工作台”，不预加载单企业分析需要的选项与节点树。
  if (activeTab.value === "single") {
    await loadSingleTabOptions();
  }
});

watch(
  () => activeTab.value,
  async (tab) => {
    if (tab === "single") {
      await loadSingleTabOptions();
    }
  },
);

async function loadSingleTabOptions() {
  await Promise.allSettled([
    optionsStore.loadEnterpriseOptions(),
    optionsStore.loadChainOptions(),
  ]);
  if (form.chainId) {
    await loadNodes(true).catch(() => {
      ElMessage.warning("节点列表加载失败，请刷新重试");
    });
  }
}
```

- [ ] **步骤 3：企业远程搜索改用 store 的 search 方法并加防抖**

把企业下拉 `:remote-method` 指向新函数：

```ts
const enterpriseSearchKeyword = ref("");
const enterprisesSearchResult = ref<EnterpriseOption[]>([]);
let searchTimer: number | null = null;

async function searchEnterprises(keyword: string) {
  enterpriseSearchKeyword.value = keyword;
  if (searchTimer) {
    window.clearTimeout(searchTimer);
  }
  const trimmed = keyword.trim();
  if (trimmed.length > 0 && trimmed.length < 2) {
    return; // 小于 2 字符不发请求
  }
  searchTimer = window.setTimeout(async () => {
    try {
      enterprisesSearchResult.value =
        await optionsStore.searchEnterpriseOptions(trimmed);
    } catch {
      enterprisesSearchResult.value = [];
    }
  }, 300);
}
```

模板里 `el-select` 的 `:remote-method` 改为 `searchEnterprises`，`el-option` 数据源改为：

```vue
<el-option
  v-for="enterprise in (enterpriseSearchKeyword.trim() ? enterprisesSearchResult : enterprises)"
  :key="enterprise.id"
  :label="enterprise.name"
  :value="enterprise.id"
/>
```

> 若 `form.enterpriseId` 当前选中企业不在选项里，保留对 `fetchEnterprise(id)` 的兜底加载（`loadSelectedEnterpriseDetail` 已有）。

- [ ] **步骤 4：节点树改用候选接口**

把 `loadNodes` 改为：

```ts
async function loadNodes(silent = false) {
  if (!form.chainId) {
    nodes.value = [];
    return;
  }
  nodes.value = (await optionsStore.loadNodeCandidates(form.chainId)).map(
    (candidate) => ({
      id: candidate.id,
      chainId: candidate.chainId,
      parentId: null,
      name: candidate.name,
      stageType: null,
      levelNo: null,
      keywords: candidate.keywords,
      definition: candidate.description,
      nodePath: candidate.nodePath,
      sortNo: null,
      children: [],
    }),
  );
  if (!silent) {
    ElMessage.success(`已读取 ${nodeCount.value} 个产业链节点`);
  }
}
```

> `flattenChainNodes` 与 `displayNodes` 依赖的 `ChainNode` 字段保持兼容；`children: []` 保证其可被展平。

- [ ] **步骤 5：跑前端单测**

运行：

```bash
cd groupproject-front/front && npm run test
```

预期：PASS（如 `MatchingTask.spec.ts` 因数据源变更失败，按新数据源调整 mock；不要绕过测试）。

- [ ] **步骤 6：浏览器手测**

启动前后端，访问 `/matching`，观察 Network：

- 进入页面默认 tab 不发 `/enterprises`、`/chains`、`/chains/{id}/tree`。
- 切到“人工智能分析”才发 `/enterprises/options`、`/chains/options`、`/chains/{id}/node-options`。
- 企业输入框连续输入时不会每次都发请求，至少 300ms 防抖且小于 2 字符不发。

## 任务 11：前端 AiAssistantPanel 复用 options store

**文件：**
- 修改：`groupproject-front/front/src/views/assistant/AiAssistantPanel.vue`
- 修改：`groupproject-front/front/src/views/assistant/AiAssistantPanel.spec.ts`（若因局部 state 移除失败）

- [ ] **步骤 1：替换局部 enterprises/chains 为 store**

在 `<script setup>` 顶部引入：

```ts
import { useOptionsStore } from "@/stores/options";
```

删除局部：

```ts
const enterprises = ref<EnterpriseListItem[]>([]);
const chains = ref<IndustryChain[]>([]);
const optionsLoaded = ref(false);
```

以及 `loadOptions` 函数、`watch(store.panelOpen)` 中的 `loadOptions()` 调用、`onMounted` 中的 `loadOptions()` 调用。

改为：

```ts
const optionsStore = useOptionsStore();
const enterprises = computed(() => optionsStore.enterpriseOptions);
const chains = computed(() => optionsStore.chainOptions);

watch(
  () => store.panelOpen,
  (open) => {
    if (open) {
      void optionsStore.loadEnterpriseOptions();
      void optionsStore.loadChainOptions();
    }
  },
);

onMounted(() => {
  if (store.panelOpen) {
    void optionsStore.loadEnterpriseOptions();
    void optionsStore.loadChainOptions();
  }
});
```

- [ ] **步骤 2：模板类型对齐**

模板中 `el-option` 的 `:label` 仍读 `enterprise.name` / `chain.name`，与新类型字段一致，无需改模板。删除对 `EnterpriseListItem` / `IndustryChain` 的 import（若不再使用）以避免 `noUnusedLocals` 报错。

- [ ] **步骤 3：跑前端单测**

运行：

```bash
cd groupproject-front/front && npm run test -- src/views/assistant
```

预期：PASS（如 `AiAssistantPanel.spec.ts` 因局部 state 移除失败，按 store mock 调整）。

- [ ] **步骤 4：浏览器手测**

打开 AI 助手面板，确认企业与产业链下拉能加载，且若智能挂链页已加载过则不再重复请求（Network 只有一组 `/enterprises/options`、`/chains/options`）。

## 任务 12：回归与验证

**文件：**
- 验证：后端全量测试
- 验证：前端全量测试
- 验证：浏览器 Network 与数据库 EXPLAIN

- [ ] **步骤 1：后端全量测试**

运行：

```bash
cd groupproject-back-commit/back && mvn test
```

预期：PASS。

- [ ] **步骤 2：前端全量测试**

运行：

```bash
cd groupproject-front/front && npm run test
```

预期：PASS。

- [ ] **步骤 3：浏览器 Network 对比**

分别打开 `/matching`（默认批量 tab）、切到“人工智能分析”、打开 AI 助手，记录以下接口的耗时与调用次数：

- `GET /api/enterprises/options`
- `GET /api/chains/options`
- `GET /api/chains/{id}/node-options`

期望：

- 进入 `/matching` 默认 tab：以上三个接口 0 次。
- 切到“人工智能分析”：各 1 次。
- 打开 AI 助手：0 次额外请求（命中 store 缓存）。

- [ ] **步骤 4：数据库 EXPLAIN（可选但建议）**

对以下 SQL 跑 `EXPLAIN`，确认走新索引：

```sql
EXPLAIN SELECT id, enterprise_name AS name FROM enterprise
WHERE is_deleted = 0 AND status = 'ACTIVE'
ORDER BY create_time DESC, id DESC LIMIT 30;

EXPLAIN SELECT id, chain_name AS name FROM chain
WHERE is_deleted = 0 ORDER BY id DESC;

EXPLAIN SELECT id, chain_id, node_name, node_path, keywords, description
FROM chain_node WHERE chain_id = 1 AND is_deleted = 0
ORDER BY sort_order, id;
```

期望：`type` 为 `ref` 或 `range`，`key` 命中新复合索引，`Extra` 不出现 `Using filesort`（或仅在数据量小时不影响）。

- [ ] **步骤 5：交付说明**

最终回复包含：

```markdown
已优化智能挂链与 AI 助手的企业/产业链选项加载。

修改点：
- 后端：新增 /api/enterprises/options、/api/chains/options、/api/chains/{id}/node-options 三个轻量接口，企业列表消除 N+1 标签查询，补选项查询索引。
- 前端：新增 useOptionsStore 统一缓存选项；MatchingTask 改为切到“人工智能分析”才懒加载，企业远程搜索加防抖；AiAssistantPanel 复用 store。

验证：
- mvn test：PASS
- npm run test：PASS
- Network：默认 tab 不发选项请求；切 tab 与打开助手仅各发一次。
```

## 自检结果

- 规格覆盖度：覆盖了根因分析的 5 类原因——前端一次性加载过多（任务 10 懒加载）、默认 tab 预加载（任务 10 onMounted 改造）、企业列表 VO 过重 + N+1（任务 1、3）、产业链统计过重（任务 4、6）、节点树统计过重（任务 5、6）；并覆盖索引（任务 7）与共享缓存（任务 9）。
- 占位符扫描：未使用“待定/TODO/后续实现”；所有代码步骤给出具体代码块；mock 占位明确说明用途。
- 类型一致性：`EnterpriseOptionVO` / `ChainOptionVO` / `ChainNodeCandidateVO` 字段名在后端 Mapper SQL 别名、前端 `domain.ts` 类型、store 与组件使用中保持一致（`id` / `name` / `nodePath` / `keywords` / `description` / `chainId`）。
- 范围检查：只优化选项加载路径，不改 AI 采纳恢复逻辑、不改企业管理页统计口径；管理页仍走原 `/chains` 与 `getChainTree`。
