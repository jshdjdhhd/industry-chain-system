# 智能挂链前后端及数据库改动说明

## 1. 改动范围

本次改动聚焦智能挂链后端模块和真实数据库适配。

实际改动范围：

- 后端：有改动。
- 数据库：有改动。
- 测试：有改动。
- 前端：没有改动。

说明：我没有修改前端页面、组件、路由或请求代码。前端侧只需要知道后端现在返回的数据更完整，并且节点新增了 `keywords` 字段能力。

## 2. 后端改动概览

本次后端主要完成了智能挂链本地规则算法的真实库闭环。

核心能力包括：

- 企业特征从真实数据库读取。
- 产业链节点从真实数据库读取。
- 节点关键词从 `chain_node.keywords` 读取。
- 本地规则算法生成推荐结果。
- 推荐结果包含置信度、理由、证据。
- 采纳推荐后写入正式挂链关系。
- 置信度按 0 到 1 小数保存。
- 支持节点路径展示。
- 测试覆盖真实查询、规则算法、采纳写入等关键流程。

## 3. 智能挂链算法相关改动

主要文件：

```text
groupproject-back/back/src/main/java/git/ggwzz/matching/service/impl/RuleBasedMatchingProvider.java
```

改动说明：

- 完成本地规则匹配算法。
- 企业名称、主营产品、经营范围、标签分来源加权评分。
- 节点名称、节点关键词、节点描述共同参与匹配。
- 支持关键词包含关系命中。
- 推荐结果按置信度降序排列。
- 最多返回前 5 条推荐。
- 使用 Jackson 生成合法 JSON 证据。

评分规则：

```text
企业名称命中数 * 3
主营产品命中数 * 4
经营范围命中数 * 2
标签命中数 * 2
```

置信度：

```text
confidence = min(0.95, score / 10)
```

## 4. 企业查询改动

新增文件：

```text
groupproject-back/back/src/main/java/git/ggwzz/enterprise/mapper/EnterpriseMapper.java
```

修改文件：

```text
groupproject-back/back/src/main/java/git/ggwzz/enterprise/service/impl/EnterpriseQueryServiceImpl.java
```

改动说明：

- 原先企业查询没有真实数据库实现。
- 新增 MyBatis Mapper，从真实 `enterprise` 表读取智能挂链所需字段。
- 企业经营范围优先读取 `business_scope`，为空时回退到 `industry`。
- 企业主营特征组合读取 `main_products`、`keywords`、`core_tech`。
- 企业标签按真实库结构从 `enterprise_tag_rel + tag` 查询。

真实库适配点：

```text
enterprise_tag 表不存在
真实结构是 enterprise_tag_rel + tag
```

因此后端查询已按真实结构调整。

## 5. 产业链节点查询改动

修改文件：

```text
groupproject-back/back/src/main/java/git/ggwzz/chain/service/ChainNodeQueryService.java
groupproject-back/back/src/main/java/git/ggwzz/chain/service/impl/ChainNodeQueryServiceImpl.java
groupproject-back/back/src/main/java/git/ggwzz/chain/mapper/ChainNodeMapper.java
groupproject-back/back/src/main/java/git/ggwzz/chain/entity/ChainNode.java
```

改动说明：

- 原先节点查询是空实现或骨架逻辑。
- 现在通过 `ChainNodeMapper.selectByChainId` 查询真实 `chain_node`。
- `ChainNode` 实体新增 `keywords` 字段。
- `ChainNodeMapper` 查询列新增 `keywords`。
- `ChainNodeQueryServiceImpl` 将 `node.getKeywords()` 写入算法输入。
- 新增 `buildNodePath(nodeId)`，用于返回完整节点路径。

节点路径示例：

```text
新能源汽车产业链 / 中游 / 电池制造
```

## 6. 节点关键词维护能力

修改文件：

```text
groupproject-back/back/src/main/java/git/ggwzz/chain/dto/CreateNodeRequest.java
groupproject-back/back/src/main/java/git/ggwzz/chain/dto/UpdateNodeRequest.java
groupproject-back/back/src/main/java/git/ggwzz/chain/vo/NodeVO.java
groupproject-back/back/src/main/java/git/ggwzz/chain/service/impl/ChainServiceImpl.java
```

改动说明：

- 创建节点请求新增 `keywords`。
- 更新节点请求新增 `keywords`。
- 节点返回 VO 新增 `keywords`。
- 创建和更新节点时会保存关键词。
- 查询节点时会返回关键词。

前端当前不需要修改也能继续使用原接口。后续如果前端要做节点关键词维护，只需要在请求体和展示中接入 `keywords` 字段。

## 7. 推荐结果与正式挂链改动

修改文件：

```text
groupproject-back/back/src/main/java/git/ggwzz/matching/service/impl/MatchingServiceImpl.java
groupproject-back/back/src/main/java/git/ggwzz/chain/service/EnterpriseNodeRelService.java
groupproject-back/back/src/main/java/git/ggwzz/chain/service/impl/EnterpriseNodeRelServiceImpl.java
```

改动说明：

- 推荐结果 VO 增加节点路径组装。
- 采纳推荐后调用正式挂链服务。
- `linkByAi` 的 `confidence` 参数从 `Long` 改为 `BigDecimal`。
- 修复置信度保存语义错误。

修复前风险：

```text
0.9500 可能被转换成 95
```

修复后：

```text
enterprise_node_rel.confidence = 0.9500
```

## 8. 数据库改动

新增数据库升级脚本：

```text
groupproject-back/back/src/main/resources/db/migration/V4__chain_node_keywords.sql
```

核心改动：

```sql
ALTER TABLE `chain_node`
    ADD COLUMN `keywords` VARCHAR(500) NULL COMMENT '节点关键词，多个关键词用逗号分隔' AFTER `node_name`;
```

并对现有节点回填关键词：

```text
氢气制取：氢气制取,电解水制氢,工业副产氢,绿氢,电解槽
氢气储运：氢气储运,高压储氢,液氢,氢气运输,加氢站
燃料电池：燃料电池,燃料电池电堆,氢燃料电池,质子交换膜,核心零部件
动力电池：动力电池,锂离子电池,电芯,模组,磷酸铁锂,三元锂
电驱系统：电驱系统,驱动电机,电控,新能源动力总成
新能源整车：新能源整车,新能源汽车,纯电动,插电混动,整车制造
充换电设施：充换电设施,充电桩,充电站,换电站,智能充电
```

服务器真实库也已执行过该结构升级和关键词回填。

## 9. 演示数据脚本改动

修改文件：

```text
groupproject-back/back/src/main/resources/db/data/V2__demo_business.sql
```

改动说明：

- `enterprise` 演示建表补充真实库中的业务字段：
  - `business_scope`
  - `main_products`
  - `core_tech`
- 补充 `tag` 表和 `enterprise_tag_rel` 表，和服务器真实库结构保持一致。
- 保留原 `enterprise_tag` 演示数据兼容历史脚本，但智能挂链实际查询已切到真实结构。

## 10. 测试改动

新增测试：

```text
groupproject-back/back/src/test/java/git/ggwzz/matching/service/MatchingQueryServiceIntegrationTest.java
```

修改测试：

```text
groupproject-back/back/src/test/java/git/ggwzz/matching/service/RuleBasedMatchingProviderTest.java
groupproject-back/back/src/test/java/git/ggwzz/matching/service/MatchingServiceTest.java
groupproject-back/back/src/test/java/git/ggwzz/chain/service/EnterpriseNodeRelServiceTest.java
groupproject-back/back/src/test/java/git/ggwzz/chain/service/ChainServiceTest.java
groupproject-back/back/src/test/resources/schema.sql
groupproject-back/back/src/test/resources/data.sql
```

测试覆盖：

- 企业特征从数据库读取。
- 企业标签从 `enterprise_tag_rel + tag` 读取。
- 节点关键词从 `chain_node.keywords` 读取。
- 节点路径构建。
- 关键词包含关系命中。
- 推荐结果排序和数量限制。
- 采纳推荐后写正式挂链。
- AI 置信度按 `BigDecimal` 小数保存。

## 11. 前端影响说明

本次没有修改前端代码。

对前端的潜在影响：

- 节点接口现在可以返回 `keywords` 字段。
- 创建或更新节点接口现在可以接收 `keywords` 字段。
- 智能挂链推荐结果中的 `nodePath` 更完整。
- 现有前端如果不传 `keywords`，接口仍然可以正常工作。

如果后续前端要配合增强，可以考虑：

- 在节点编辑弹窗中增加“节点关键词”输入框。
- 在节点详情中展示关键词。
- 在智能挂链结果中展示命中的关键词证据。

但这些不是本次后端完成智能挂链本地算法的必要条件。

## 12. 远程库联调结果

已连接服务器真实 MySQL 库做 smoke 验证。

验证数据：

```text
企业：宁德时代新能源科技股份有限公司
enterpriseId = 4
产业链 ID = 1
```

验证结果：

```text
匹配节点：202 动力电池
置信度：0.9500
理由：主营产品命中5个关键词；经营范围命中1个关键词
证据：动力电池、电芯、模组、磷酸铁锂、三元锂
```

说明：

- 后端可以连接真实服务器数据库。
- 企业查询 Mapper 可用。
- 节点关键词字段可用。
- 本地规则算法可用。
- 推荐链路可跑通。

## 13. 当前状态

当前智能挂链本地规则算法版已完成。

已完成：

- 真实库企业查询。
- 真实库节点查询。
- 节点关键词字段。
- 本地规则匹配。
- 推荐理由与证据。
- 推荐采纳写正式挂链。
- 远程库 smoke 验证。
- 本地全量测试。

验证结果：

```text
mvn test
Tests run: 87
Failures: 0
Errors: 0
Skipped: 0
```

## 14. 队友需要注意

如果队友拉取后端代码，需要确认数据库已包含：

```text
chain_node.keywords
```

如果目标库没有该字段，需要执行：

```text
src/main/resources/db/migration/V4__chain_node_keywords.sql
```

另外，企业标签查询依赖真实表：

```text
enterprise_tag_rel
tag
```

如果某个环境还是旧的 `enterprise_tag` 结构，需要同步数据库结构，或者单独做兼容查询。

## 15. 简短汇报版本

我完成了智能挂链后端本地规则算法，实现了从真实数据库读取企业特征和产业链节点关键词，根据企业名称、主营产品、经营范围、标签等字段进行加权匹配，生成推荐节点、置信度、推荐理由和 JSON 证据。推荐结果需要人工采纳后才写入正式挂链表。开发过程中同步补充了 `chain_node.keywords` 字段，并适配服务器真实库的 `enterprise_tag_rel + tag` 标签结构。最终使用远程真实库验证宁德时代能匹配到动力电池节点，后端全量测试 87 个全部通过。
