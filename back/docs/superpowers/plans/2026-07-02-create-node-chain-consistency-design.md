# 创建节点产业链一致性校验设计

## 1. 背景

创建节点接口为 `POST /api/chains/{chainId}/nodes`。当前实现会校验目标产业链和父节点是否存在，但父节点存在后直接继承其层级与路径，没有确认父节点是否属于目标产业链。因此，调用方可以在产业链 B 下创建节点，却传入产业链 A 的节点作为父节点，形成以下不一致数据：

- 新节点的 `chain_id` 属于产业链 B；
- 新节点的 `parent_id` 指向产业链 A 的节点；
- 新节点的 `node_path` 继承产业链 A 的父节点路径。

此外，`ChainController.createNode()` 目前仅在请求体 `chainId` 为空时才写入路径参数。虽然接口契约规定 `chainId` 由路径提供，但请求 DTO 仍可接收同名字段，调用方理论上可以通过请求体覆盖路径中的目标产业链。

## 2. 目标与范围

### 2.1 目标

- URL 路径中的 `chainId` 是创建节点时唯一权威的产业链 ID。
- 创建子节点时，父节点必须属于目标产业链。
- 校验失败时返回 HTTP 400 和明确的业务错误信息。
- 校验失败后不得插入节点或回填 `node_path`。
- 通过 Service 单元测试和 `chain.http` 真实接口流程覆盖跨产业链场景。

### 2.2 范围

修改范围限定在 `back`：

- `ChainController`
- `ChainServiceImpl`
- `ChainServiceTest`
- `docs/API-INTERFACE-REGISTRY.md`
- `src/test/resources/http/chain.http`

不修改前端、数据库表结构、Mapper 接口、请求或响应字段。

## 3. 方案

采用 Controller 固定路径参数、Service 执行业务校验的双层方案。

### 3.1 Controller 参数边界

`ChainController.createNode()` 无条件执行：

```java
request.setChainId(chainId);
```

不再根据请求体中的 `chainId` 是否为空决定是否覆盖。这样，即使请求体携带了不同的 `chainId`，Service 接收到的也始终是 URL 路径中的值。

### 3.2 Service 一致性校验

`ChainServiceImpl.createNode()` 保持现有处理顺序：

1. 校验请求对象不为空。
2. 根据目标 `chainId` 校验产业链存在。
3. 当 `parentId` 非空时查询父节点。
4. 校验父节点存在。
5. 比较父节点 `chainId` 与目标 `chainId`。
6. 通过校验后计算层级、路径并写入节点。

比较使用 `Objects.equals()`，避免包装类型使用 `==`，也能安全处理异常空值。父节点不属于目标产业链时抛出：

```java
throw new BusinessException(
        ResultCode.BAD_REQUEST,
        "父节点不属于当前产业链");
```

该校验必须位于读取父节点之后、读取父节点层级和路径之前，确保非法请求不会进入节点构造和数据库写入阶段。

## 4. 数据流与错误语义

### 4.1 正常创建根节点

`parentId` 为空，不执行父节点一致性校验，继续按当前逻辑创建目标产业链的一级节点。

### 4.2 正常创建子节点

父节点存在且 `parent.chainId == request.chainId`，继续继承父节点路径并将层级加一。

### 4.3 父节点不存在

保持当前 HTTP 400 语义和“父节点不存在”提示，不改变既有接口行为。

### 4.4 父节点属于其他产业链

返回 HTTP 400，响应消息为“父节点不属于当前产业链”。不得调用 `chainNodeMapper.insert()` 或 `chainNodeMapper.updateById()`。

### 4.5 请求体携带不同的 chainId

Controller 使用路径参数覆盖请求体值。后续校验和创建均以路径参数为准，不新增请求字段，也不引入新的响应格式。

## 5. 测试设计

### 5.1 `ChainServiceTest`

新增 `should_throw_bad_request_when_parent_belongs_to_another_chain`：

- 目标产业链 ID 为 2；
- 父节点存在，但其 `chainId` 为 1；
- 调用 `createNode()`；
- 断言异常代码为 400；
- 断言异常信息为“父节点不属于当前产业链”；
- 验证 `insert()` 和 `updateById()` 均未执行。

补强现有 `should_set_level_when_create_child_node`：

- 显式设置父节点 `chainId=1`；
- 保持现有层级、路径、关键词和写入次数断言；
- 证明新增校验不会破坏合法创建流程。

现有根节点、父节点不存在、产业链不存在等测试继续作为回归覆盖。

### 5.2 `chain.http`

在现有测试数据流程中增加一个临时产业链 B，并执行以下场景：

1. 在产业链 A 下创建父节点并保存 ID。
2. 请求在产业链 B 下创建子节点，`parentId` 使用产业链 A 的父节点 ID。
3. 断言 HTTP 状态为 400。
4. 断言响应 `code` 为 400。
5. 断言响应 `message` 为“父节点不属于当前产业链”。
6. 按依赖顺序删除临时产业链 B，避免遗留测试数据。

该流程依赖 IDEA HTTP Client 的 `client.global` 状态，应逐条执行，不使用 Run All。

## 6. 文档同步

在 `docs/API-INTERFACE-REGISTRY.md` 的节点接口说明中补充：

- `chainId` 以 URL 路径参数为准；
- 创建子节点时，父节点必须属于同一产业链；
- 不一致时返回 400。

接口路径、DTO、VO 和前端契约未发生变化，因此不需要新增接口或修改前端类型。

## 7. 验收标准

- 合法的根节点和同产业链子节点仍可成功创建。
- 跨产业链父节点请求稳定返回 400。
- 错误消息明确为“父节点不属于当前产业链”。
- 非法请求不会执行节点插入或路径回填。
- 请求体无法覆盖 URL 路径中的 `chainId`。
- `ChainServiceTest`、后端完整测试和 Maven 打包通过。
- `chain.http` 跨产业链场景及清理流程执行通过。
- `git diff --check` 无格式错误。
- 所有修改仅位于 `back`。

## 8. 非目标

- 不调整产业链、节点或关系表结构。
- 不新增数据库约束或 Mapper 查询。
- 不修改节点移动、排序、删除或统计逻辑。
- 不执行 Git 提交、推送或其他上传操作。
