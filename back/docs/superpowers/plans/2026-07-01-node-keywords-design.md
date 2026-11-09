# 节点知识库字段 — 设计规格

> 日期：2026-07-01
> 模块：产业链管理（后端）
> 范围：全链路（数据层 + 查询扩展 + 校验 + 智能挂链联动说明）

## 0. 实施校准（2026-07-01 实际落地）

> 本节记录实际代码与本规格的差异，**以实际代码为准**。差异源于规格沿用旧命名 `IndustryChainNode` / `definition`，而当前 `chain` 模块已统一为 `ChainNode` / `description`，且部分字段在更早迭代中已落地。

| 规格项 | 实际代码 | 处理 |
| --- | --- | --- |
| 实体 `IndustryChainNode` | `git.ggwzz.chain.entity.ChainNode`（表 `chain_node`） | 命名沿用实际代码 |
| 字段 `definition` | `description`（TEXT，V2 建表已有） | 不新增 `definition` 列，复用 `description` |
| 字段 `keywords` | `keywords`（VARCHAR(500)，`V4__chain_node_keywords.sql` 已加） | 已存在，无需新增 |
| DDL `V2_add_node_knowledge_fields.sql` | `keywords`/`description` 列均已存在 | **不新增迁移**，避免重号与重复 `ADD COLUMN` |
| Service `IndustryChainService` | `ChainService` / `ChainServiceImpl` | 命名沿用实际代码 |
| Controller `IndustryChainController` | `ChainController`（路径 `/api/chains`） | 新接口置于本控制器 |
| Mapper `IndustryChainNodeMapper.selectByChainId` | `ChainNodeMapper.selectByChainId` 已含 `keywords, description` 两列 | 无需改动 |
| `@PreAuthorize('chain:node:query')` | 当前 `ChainController` 全部接口均未启用方法级 `@PreAuthorize`（依赖 `/api/**` 默认认证） | 新接口同样不加 `@PreAuthorize`，保持与同控制器现有接口一致，避免引入鉴权不一致 |
| `keyword` 参数校验 | Controller 加 `@Validated` + `@NotBlank`，Service 再做空白兜底（`BAD_REQUEST`） | 双层校验，空白关键词返回 400 |

新增/修改文件清单（实际）：

| 文件 | 改动 |
| --- | --- |
| `NodeVO.java` | 新增 `description` 字段及映射 |
| `TreeNodeVO.java` | 新增 `keywords`、`description` 字段及映射 |
| `ChainService.java` | 新增 `searchByKeywords` 抽象方法 |
| `ChainServiceImpl.java` | 新增 `normalizeKeywords` 并接入 `createNode`/`updateNode`；`toNodeVO`/`buildChildren` 补充 `keywords`/`description` 映射；新增 `searchByKeywords` 实现 |
| `ChainController.java` | 类加 `@Validated`；新增 `GET /api/chains/nodes/search-by-keyword` |
| `ChainServiceTest.java` | 新增关键词清洗、`description` 映射、`searchByKeywords` 单测 |
| `ChainControllerTest.java` | 新增关键词搜索接口 MockMvc 测试 |
| `docs/API-INTERFACE-REGISTRY.md` | 登记新接口与 `keywords` 字段 |
| `src/test/resources/http/chain.http` | 新增关键词搜索联调脚本 |

---

## 1. 背景

文档要求"构建标准化产业节点标签知识库"和"形成节点关键词词库"，但现有 `IndustryChainNode` 实体仅含 `name`/`level`/`sortOrder`/`nodePath`，缺少 keywords 和 definition 字段。前端 `ChainNode` 类型已预留 `keywords` 和 `definition`，后端需补齐。

## 2. 数据模型

### 2.1 DDL 变更

新增迁移脚本 `V2_add_node_knowledge_fields.sql`：

```sql
ALTER TABLE `industry_chain_node`
    ADD COLUMN `keywords`   VARCHAR(500)  DEFAULT NULL COMMENT '节点关键词，逗号分隔' AFTER `node_path`,
    ADD COLUMN `definition` VARCHAR(1000) DEFAULT NULL COMMENT '节点定义/描述'        AFTER `keywords`;
```

### 2.2 Entity 变更

`IndustryChainNode` 新增两个字段：

```java
/** 节点关键词，逗号分隔，如 "电芯,原材料,正极材料" */
private String keywords;

/** 节点定义/描述 */
private String definition;
```

均为可选字段，允许 NULL。

## 3. DTO / VO 变更

### 3.1 CreateNodeRequest

```java
@Size(max = 500, message = "关键词不能超过500字符")
private String keywords;

@Size(max = 1000, message = "节点定义不能超过1000字符")
private String definition;
```

### 3.2 UpdateNodeRequest

```java
@Size(max = 500, message = "关键词不能超过500字符")
private String keywords;

@Size(max = 1000, message = "节点定义不能超过1000字符")
private String definition;
```

### 3.3 NodeVO

```java
private String keywords;
private String definition;
```

### 3.4 TreeNodeVO

```java
private String keywords;
private String definition;
```

## 4. Mapper 变更

### 4.1 IndustryChainNodeMapper

**修改 `selectByChainId`** — SQL 加上 `keywords, definition` 两列。

> 按关键词搜索节点的查询通过 Service 层 MyBatis-Plus `LambdaQueryWrapper` 实现（与现有 `searchNodes` 模式一致），无需在 Mapper 中新增原生 SQL 方法。

## 5. Service 层变更

### 5.1 IndustryChainService 接口

新增方法：

```java
PageResult<NodeVO> searchByKeywords(String keyword, Long chainId, int pageNum, int pageSize);
```

### 5.2 IndustryChainServiceImpl

**createNode** — 透传新字段：
```java
node.setKeywords(normalizeKeywords(request.getKeywords()));
node.setDefinition(request.getDefinition());
```

**updateNode** — 部分更新（null 不覆盖）：
```java
if (request.getKeywords() != null) {
    node.setKeywords(normalizeKeywords(request.getKeywords()));
}
if (request.getDefinition() != null) {
    node.setDefinition(request.getDefinition());
}
```

**toNodeVO** — 补充映射：
```java
vo.setKeywords(n.getKeywords());
vo.setDefinition(n.getDefinition());
```

**getChainTree** 中的 TreeNodeVO 组装 — 补充映射：
```java
tn.setKeywords(n.getKeywords());
tn.setDefinition(n.getDefinition());
```

**新增 searchByKeywords**：
```java
public PageResult<NodeVO> searchByKeywords(String keyword, Long chainId, int pageNum, int pageSize) {
    if (keyword == null || keyword.isBlank()) {
        throw new BusinessException(ResultCode.BAD_REQUEST, "搜索关键词不能为空");
    }
    Page<IndustryChainNode> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<IndustryChainNode> wrapper = new LambdaQueryWrapper<IndustryChainNode>()
            .like(IndustryChainNode::getKeywords, keyword);
    if (chainId != null) {
        wrapper.eq(IndustryChainNode::getChainId, chainId);
    }
    wrapper.orderByAsc(IndustryChainNode::getLevel);
    Page<IndustryChainNode> result = nodeMapper.selectPage(page, wrapper);
    List<NodeVO> records = result.getRecords().stream()
            .map(this::toNodeVO)
            .collect(Collectors.toList());
    return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
}
```

### 5.3 关键词清洗方法

```java
private String normalizeKeywords(String keywords) {
    if (keywords == null || keywords.isBlank()) {
        return null;
    }
    return Arrays.stream(keywords.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.joining(","));
}
```

效果：`" 电芯 , 原材料,, 正极材料, 电芯 "` → `"电芯,原材料,正极材料"`

## 6. Controller 层变更

现有接口无需改动（DTO 新字段自动反序列化）。

**新增搜索接口：**

```java
@PreAuthorize("hasAuthority('chain:node:query')")
@GetMapping("/node/search-by-keyword")
public Result<PageResult<NodeVO>> searchByKeyword(
        @RequestParam @NotBlank String keyword,
        @RequestParam(required = false) Long chainId,
        @RequestParam(defaultValue = "1") int pageNum,
        @RequestParam(defaultValue = "20") int pageSize) {
    return Result.success(chainService.searchByKeywords(keyword, chainId, pageNum, pageSize));
}
```

路径：`GET /api/industry-chain/node/search-by-keyword?keyword=电芯`

与现有 `/node/search`（按名称）区分：`/node/search-by-keyword`（按知识库关键词）。

## 7. 校验规则汇总

| 层 | 字段 | 规则 |
|----|------|------|
| DTO | keywords | `@Size(max = 500)`，允许 null |
| DTO | definition | `@Size(max = 1000)`，允许 null |
| Service | keywords | `normalizeKeywords()` 清洗：去空格、去空项、去重 |
| Service | searchByKeywords 的 keyword 参数 | 非空非空白，否则抛 BAD_REQUEST |

## 8. 智能挂链联动（本次不改动）

后续迭代中，智能挂链 Service 构建 LLM prompt 时，可从 `IndustryChainNode` 读取 `keywords` 和 `definition` 注入上下文，提升匹配精准度。本次开发不涉及智能挂链代码变更。

## 9. 改动文件清单

| 文件 | 改动类型 |
|------|----------|
| `sql/V2_add_node_knowledge_fields.sql` | 新增 |
| `IndustryChainNode.java` | 修改（加字段） |
| `CreateNodeRequest.java` | 修改（加字段） |
| `UpdateNodeRequest.java` | 修改（加字段） |
| `NodeVO.java` | 修改（加字段） |
| `TreeNodeVO.java` | 修改（加字段） |
| `IndustryChainNodeMapper.java` | 修改（selectByChainId 加列） |
| `IndustryChainService.java` | 修改（加抽象方法） |
| `IndustryChainServiceImpl.java` | 修改（透传 + 清洗 + 新方法） |
| `IndustryChainController.java` | 修改（加搜索接口） |
