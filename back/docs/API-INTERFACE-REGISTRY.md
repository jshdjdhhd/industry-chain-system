# 接口登记表

> 用途：团队协作时登记已实现接口，新增接口前先查本文件，避免路径重复和语义冲突。  
> 范围：扫描 `src/main/java/git/ggwzz/**/controller/*.java` 后整理。  
> 统一响应：除特殊文件接口外，均返回 `{ "code": 200, "message": "success", "data": ... }`。  
> 鉴权：除 `POST /api/auth/login` 与 `GET /api/health` 外，当前 `/api/**` 默认需要 `Authorization: Bearer <token>`。

## 1. Auth 鉴权接口

| 方法 | 路径 | 认证 | 请求参数 | 响应 data | 说明 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/auth/login` | 否 | Body: `LoginRequest` | `LoginVO` | 用户登录，返回 JWT、用户信息和权限集合 | 已实现 |
| POST | `/api/auth/logout` | 是 | 无 | `null` | 用户登出；无状态模式下由前端清除 token，后端记录操作日志 | 已实现 |
| GET | `/api/auth/me` | 是 | 无 | `CurrentUserVO` | 获取当前登录用户、角色和权限 | 已实现 |
| GET | `/api/health` | 否 | 无 | `{ "status": "up" }` | 健康检查 | 已实现 |

### LoginRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `String` | 是 | 用户名 |
| `password` | `String` | 是 | 密码 |

## 2. Chain 产业链接口

### 2.1 产业链管理

| 方法 | 路径 | 认证 | 请求参数 | 响应 data | 说明 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/chains` | 是 | Body: `CreateChainRequest` | `ChainVO` | 创建产业链并在同一事务中初始化上游、中游、下游三个阶段节点；成功时 `nodeCount=3` | 已实现 |
| GET | `/api/chains` | 是 | Query: `pageNum=1`, `pageSize=10` | `PageResult<ChainVO>` | 分页查询产业链；每条记录始终包含 `nodeCount` 和 `enterpriseCount` | 已实现 |
| GET | `/api/chains/options` | 是 | 无 | `List<ChainOptionVO>` | 查询产业链轻量选项，仅返回 `id`、`name`，不计算节点数或企业数 | 已实现 |
| GET | `/api/chains/{chainId}` | 是 | Path: `chainId` | `ChainVO` | 查询产业链详情 | 已实现 |
| PUT | `/api/chains/{chainId}` | 是 | Path: `chainId`; Body: `UpdateChainRequest` | `ChainVO` | 更新产业链，`null` 字段不更新 | 已实现 |
| DELETE | `/api/chains/{chainId}` | 是 | Path: `chainId` | `null` | 逻辑删除产业链，并逻辑删除其节点和挂链关系 | 已实现 |

### ChainVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 产业链 ID |
| `chainName` | `String` | 产业链名称 |
| `description` | `String` | 产业链描述 |
| `createTime` | `LocalDateTime` | 创建时间 |
| `nodeCount` | `Long` | 未删除节点数；新建产业链初始化完成后为 `3` |
| `enterpriseCount` | `Long` | 有效节点下、有效挂接关系关联的未删除企业的产业链内去重数；无企业时为 `0` |

### CreateChainRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chainName` | `String` | 是 | 产业链名称，最多 128 字符 |
| `description` | `String` | 否 | 产业链描述，最多 500 字符 |

### UpdateChainRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chainName` | `String` | 否 | 产业链名称，最多 128 字符 |
| `description` | `String` | 否 | 产业链描述，最多 500 字符 |

## 3. Chain Node 节点接口

| 方法 | 路径 | 认证 | 请求参数 | 响应 data | 说明 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/chains/{chainId}/nodes` | 是 | Path: `chainId`; Body: `CreateNodeRequest` | `NodeVO` | 创建产业链节点，`chainId` 以 URL 路径参数为准，自动维护 `parent_id`、`node_path`、`node_level` | 已实现 |
| GET | `/api/chains/{chainId}/tree` | 是 | Path: `chainId` | `TreeNodeVO` | 查询产业链树，叶子节点 `children=[]` | 已实现 |
| GET | `/api/chains/{chainId}/node-options` | 是 | Path: `chainId` | `List<ChainNodeCandidateVO>` | 查询轻量节点候选，不执行企业数或子树统计 | 已实现 |
| GET | `/api/chains/{chainId}/nodes` | 是 | Path: `chainId`; Query: `parentId`, `pageNum=1`, `pageSize=20` | `PageResult<NodeVO>` | 分页查询节点，可按父节点过滤 | 已实现 |
| PUT | `/api/chains/nodes/{nodeId}` | 是 | Path: `nodeId`; Body: `UpdateNodeRequest` | `NodeVO` | 更新节点，`null` 字段不更新 | 已实现 |
| PUT | `/api/chains/nodes/{nodeId}/move` | 是 | Path: `nodeId`; Body: `MoveNodeRequest` | `NodeVO` | 移动节点，级联重写子树 `node_path` 与 `node_level` | 已实现 |
| PUT | `/api/chains/nodes/sort` | 是 | Body: `SortNodesRequest` | `null` | 批量更新同级节点 `sort_order` | 已实现 |
| DELETE | `/api/chains/nodes/{nodeId}` | 是 | Path: `nodeId` | `null` | 删除叶子节点；有子节点时返回 400 | 已实现 |
| GET | `/api/chains/nodes/search` | 是 | Query: `keyword`, `pageNum=1`, `pageSize=20` | `PageResult<NodeVO>` | 按节点名称右模糊搜索 | 已实现 |
| GET | `/api/chains/nodes/search-by-keyword` | 是 | Query: `keyword`（非空白）, `chainId`（可选）, `pageNum=1`, `pageSize=20` | `PageResult<NodeVO>` | 按节点知识库 `keywords` 字段全模糊搜索；`chainId` 不传则跨产业链。与 `/nodes/search`（按名称）区分 | 已实现 |
| GET | `/api/chains/nodes/{nodeId}/enterprise-count` | 是 | Path: `nodeId` | `EnterpriseCountVO` | 查询节点直接挂链企业数和含下级总企业数 | 已实现 |

节点阶段响应契约：

- `NodeVO.stageType` 与 `TreeNodeVO.stageType` 可取 `UPSTREAM`、`MIDSTREAM`、
  `DOWNSTREAM`；`null` 表示普通节点。
- 固定阶段节点允许改名和同级排序，但阶段身份不会因改名改变。
- 移动固定阶段节点返回 `400 / 阶段节点无法修改父节点`。
- 单独删除固定阶段节点返回 `400 / 上下游阶段节点不允许删除`。
- 删除整条产业链时，三个阶段节点仍随产业链一起逻辑删除。
节点创建约束：

- `chainId` 以 URL 路径参数为准，请求体中的同名字段会被无条件覆盖。
- 创建子节点时，父节点必须属于同一产业链；不一致时返回
  `400 / 父节点不属于当前产业链`，且不执行节点插入或路径回填。

节点移动约束：

- 环路判断以同一产业链内的 `parent_id` 祖先链为准。
- `node_path` 必须符合 `/<祖先ID>/.../<自身ID>/`；路径异常返回
  `500 / 节点层级数据异常，请联系管理员`，且不执行子树批量更新。

### CreateNodeRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chainId` | `Long` | 否 | 以 URL 路径参数为准，Controller 无条件覆盖请求体中的同名字段 |
| `parentId` | `Long` | 否 | 父节点 ID，`null` 表示一级节点 |
| `nodeName` | `String` | 是 | 节点名称，最多 128 字符 |
| `keywords` | `String` | 否 | 节点关键词，逗号分隔，最多 500 字符；Service 层清洗（去空格、去空项、去重），空白清洗为 `null` |
| `description` | `String` | 否 | 节点描述，最多 500 字符 |
| `sortOrder` | `Integer` | 否 | 同级排序 |

### UpdateNodeRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nodeName` | `String` | 否 | 节点名称，最多 128 字符 |
| `keywords` | `String` | 否 | 节点关键词，逗号分隔，最多 500 字符；`null` 不更新，非空时经 Service 层清洗 |
| `description` | `String` | 否 | 节点描述，最多 500 字符；`null` 不更新 |
| `sortOrder` | `Integer` | 否 | 同级排序 |
| `parentChangeRequested` | `boolean` | 否 | 是否修改父节点，默认 `false` |
| `parentId` | `Long` | 否 | 目标父节点；请求修改且为 `null` 时移动为一级节点 |

更新规则：

- `parentChangeRequested=false` 时忽略 `parentId`，兼容原有部分更新。
- `parentChangeRequested=true` 时，属性和整棵子树移动在同一事务中完成。
- 修改父节点同时需要 `chain:node:update` 和 `chain:node:move`。
- 阶段节点修改父节点返回 `400 / 阶段节点无法修改父节点`。
- `TreeNodeVO` 返回 `parentId` 和 `sortOrder`。

### MoveNodeRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `newParentId` | `Long` | 否 | 新父节点 ID，`null` 表示移动为一级节点 |
| `sortOrder` | `Integer` | 否 | 移动后的同级排序，不能小于 0 |

移动约束：

- 节点不存在返回 404。
- 新父节点不存在返回 400。
- 不能移动到自身下。
- 不能移动到自己的子孙节点下。
- 不能跨产业链移动节点。

### SortNodesRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `parentId` | `Long` | 否 | 父节点 ID，`null` 表示一级节点 |
| `nodeOrders` | `List<SortNodeOrderRequest>` | 是 | 待排序节点列表，不能为空 |

### SortNodeOrderRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nodeId` | `Long` | 是 | 节点 ID |
| `sortOrder` | `Integer` | 是 | 同级排序，不能小于 0 |

## 4. Node Enterprise 企业挂链接口

| 方法 | 路径 | 认证 | 请求参数 | 响应 data | 说明 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/nodes/{id}/enterprises` | 是 | Path: `id`; Body: `LinkEnterpriseRequest` | `null` | 人工挂链企业到有效节点；企业不存在返回 404，企业非 ACTIVE 返回 400，重复挂链返回 409 | 已实现 |
| POST | `/api/nodes/{id}/enterprises/batch` | 是 | Path: `id`; Body: `BatchLinkEnterpriseRequest` | `BatchLinkEnterpriseVO` | 批量人工挂链企业；节点无效时整体返回 404，非法企业进入 `FAILED` 明细且不中断批次 | 已实现 |
| DELETE | `/api/nodes/{id}/enterprises/{enterpriseId}` | 是 | Path: `id`, `enterpriseId` | `null` | 从有效节点解绑企业；节点 404 优先于关系不存在 404 | 已实现 |
| GET | `/api/nodes/{id}/enterprises` | 是 | Path: `id` | `List<NodeEnterpriseVO>` | 查询有效节点下未删除挂链关系及真实企业名称；有效空节点返回 200 和空列表，无效节点返回 404 | 已实现 |

节点错误语义：

- 上述四个接口在节点不存在或已逻辑删除时统一返回 HTTP 404，响应体为
  `{ "code": 404, "message": "节点不存在或已删除", "data": null }`。
- 批量挂接的节点是整个批次共享资源；节点无效时请求整体失败，不返回 `BatchLinkEnterpriseVO`，
  也不为每个企业生成 `FAILED` 明细。
- 单个挂接中的企业不存在或已删除时返回 HTTP 404 和 `企业不存在`；企业状态不是 `ACTIVE`
  时返回 HTTP 400 和 `企业状态不可挂接`。
- 批量挂接逐项执行相同的企业校验；非法企业返回 `FAILED` 明细和中文原因，其余合法企业继续处理。
- 查询有效但没有挂接关系的节点仍返回 HTTP 200，`data` 为 `[]`。
- 解绑时先校验节点；仅当节点有效但关系不存在时返回 `该企业未挂链到此节点`。

### LinkEnterpriseRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `enterpriseId` | `Long` | 是 | 企业 ID |

### BatchLinkEnterpriseRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `enterpriseIds` | `List<Long>` | 是 | 待挂接企业 ID 列表，不能为空，元素不能为 `null` |

### BatchLinkEnterpriseVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `Integer` | 请求体中的企业 ID 总数量，包含重复项 |
| `linkedCount` | `Integer` | 成功新增挂接数量 |
| `skippedCount` | `Integer` | 已绑定或请求内重复导致跳过的数量 |
| `failedCount` | `Integer` | 单项处理失败数量 |
| `items` | `List<BatchLinkEnterpriseItemVO>` | 逐条处理明细 |

### BatchLinkEnterpriseItemVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `enterpriseId` | `Long` | 企业 ID |
| `status` | `String` | 处理状态：`LINKED` / `ALREADY_LINKED` / `DUPLICATE_IN_REQUEST` / `FAILED` |
| `message` | `String` | 中文展示消息 |

### NodeEnterpriseVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `enterpriseId` | `Long` | 企业 ID |
| `enterpriseName` | `String` | 企业真实名称，由企业表关联查询 |
| `source` | `String` | 挂接来源：`MANUAL` / `AI` / `IMPORT` |
| `createTime` | `LocalDateTime` | 挂接创建时间，格式为 `yyyy-MM-dd HH:mm:ss` |

## 5. Matching 智能挂链接口

| 方法 | 路径 | 认证 | 请求参数 | 响应 data | 说明 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/api/matching/tasks` | 是 | Body: `CreateMatchingTaskRequest` | `MatchingTaskVO` | 创建智能挂链推荐任务，并生成推荐结果 | 已实现 |
| GET | `/api/matching/tasks/{taskId}` | 是 | Path: `taskId` | `MatchingTaskVO` | 查询推荐任务详情和推荐结果 | 已实现 |
| POST | `/api/matching/results/{resultId}/accept` | 是 | Path: `resultId` | `MatchingDecisionVO` | 采纳推荐结果并创建正式挂链关系；挂接企业不存在返回 404，非 ACTIVE 返回 400 | 已实现 |
| POST | `/api/matching/results/{resultId}/reject` | 是 | Path: `resultId`; Body: `RejectMatchingResultRequest` | `MatchingDecisionVO` | 拒绝推荐结果并记录原因 | 已实现 |
| POST | `/api/matching/manual-confirm` | 是 | Body: `ManualConfirmRequest` | `ManualConfirmVO` | 不依赖推荐结果人工挂链；企业不存在返回 404，非 ACTIVE 返回 400 | 已实现 |
| POST | `/api/matching/ai-chat` | 是 | Body: `AiChatRequest` | `AiChatResponse` | AI 通用问答或企业与产业链上下文问答 | 已实现 |
| POST | `/api/matching/ai-chat/stream` | 是 | Body: `AiChatRequest` | `text/event-stream` | AI 流式通用问答或上下文问答 | 已实现 |

### CreateMatchingTaskRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `enterpriseId` | `Long` | 是 | 企业 ID |
| `chainId` | `Long` | 是 | 产业链 ID |

### RejectMatchingResultRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `rejectReason` | `String` | 是 | 拒绝原因 |

### ManualConfirmRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `enterpriseId` | `Long` | 是 | 企业 ID |
| `nodeId` | `Long` | 是 | 节点 ID |
| `reason` | `String` | 否 | 人工确认原因 |

### AiChatRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `enterpriseId` | `Long` | 否 | 企业 ID；上下文模式必填且必须为正整数 |
| `chainId` | `Long` | 否 | 产业链 ID；上下文模式必填且必须为正整数 |
| `message` | `String` | 是 | 当前消息，非空且不超过 4000 字符 |
| `history` | `List<ChatMessage>` | 否 | 历史消息，最多 50 条；角色仅允许 `user` 或 `assistant`，内容非空且不超过 4000 字符 |

`enterpriseId` 与 `chainId` 必须同时为空或同时提供。两者同时为空时使用通用问答模式；同时提供时使用企业与产业链上下文问答模式。

### AI 聊天响应

- `POST /api/matching/ai-chat` 返回统一响应 `Result<AiChatResponse>`，其中 `data` 包含 `reply` 和 `model`。
- `POST /api/matching/ai-chat/stream` 请求体同上，响应类型为 `text/event-stream`。
- 流式接口按 SSE `data:` 事件持续返回文本分片，服务端完成响应后关闭事件流。
- 两个接口均要求 `matching:task:create` 权限。
## 6. Operation Log 操作日志维护接口

> 仅在 `local` / `test` / `unittest` 环境启用，用于 IDEA HTTP Client 联调后清理测试操作日志。

| 方法 | 路径 | 认证 | 请求参数 | 响应 data | 说明 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| DELETE | `/api/operation-logs/maintenance/chain-http` | 是 | Body: `ChainHttpOperationLogCleanupRequest` | `Integer` | 清理本次 `chain.http` 测试产生的操作日志，返回删除行数 | 已实现 |

### ChainHttpOperationLogCleanupRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `startedAt` | `LocalDateTime` | 是 | 本次 `chain.http` 测试开始时间 |
| `endedAt` | `LocalDateTime` | 是 | 本次 `chain.http` 测试结束时间 |
| `chainId` | `Long` | 是 | 本次测试创建的产业链 ID |
| `nodeIds` | `List<Long>` | 是 | 本次测试创建的节点 ID 列表，不能为空，元素不能为 `null` |
| `enterpriseIds` | `List<Long>` | 是 | 本次测试使用的企业 ID 列表，不能为空，元素不能为 `null` |

## 7. Enterprise 企业接口

| 方法 | 路径 | 认证 | 请求参数 | 响应 data | 说明 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/api/enterprises/options` | 是 | Query: `keyword`（可选）、`limit=20`（最大 100） | `List<EnterpriseOptionVO>` | 查询 ACTIVE 企业轻量选项，仅返回 `id`、`name`；关键词按企业名称前缀匹配 | 已实现 |
| GET | `/api/enterprises/{id}/profile-analysis` | 是 | Path: `id`; Query: `similarLimit`（可选，允许 `5 / 10 / 20`，默认 `5`） | `EnterpriseProfileAnalysisVO` | 独立企业画像分析接口，返回 6 维雷达指标和 Top-N 相似企业，不影响基础企业详情接口 | 已实现 |
| GET | `/api/enterprises/{id}/profile-analysis/radar` | 是 | Path: `id` | `EnterpriseRadarVO` | 企业雷达画像快速接口，仅返回 6 维雷达指标，不计算相似企业，用于前端首屏快速渲染雷达图 | 已实现 |
| POST | `/api/enterprises/profile-analysis/vector-warmup` | 是 | Body: `{ "enterpriseIds": [1, 2, 3] }`；请求体为空或列表为空时预热全部 ACTIVE 企业 | `VectorWarmupResultVO` | 企业画像向量预热；指定 ID 单次最多 500 家 | 已实现 |

### EnterpriseProfileAnalysisVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `enterpriseId` | `Long` | 企业 ID |
| `enterpriseName` | `String` | 企业名称 |
| `radar` | `EnterpriseRadarVO` | 6 维画像雷达指标 |
| `similarEnterprises` | `List<SimilarEnterpriseVO>` | Top-N 相似企业 |
| `generatedAt` | `LocalDateTime` | 结果生成时间 |

### VectorWarmupResultVO

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | `int` | 本次预热目标总数 |
| `successCount` | `int` | 成功生成或命中向量数量 |
| `skippedCount` | `int` | 缺失、非 ACTIVE 或无需处理数量 |
| `failureCount` | `int` | 向量生成失败数量 |

## 8. 当前未提供 Controller 接口的模块

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| `tag` | 未提供 Controller 接口 | 当前为包结构骨架 |
| `operationlog` | 仅提供本地/测试维护接口 | 当前提供操作日志落库服务，以及 `chain.http` 测试日志清理接口 |

## 9. 新增接口协作规则

1. 新增接口前先查本文件，确认路径和业务语义不重复。
2. 新增接口时先在对应模块表格中登记，状态可写“设计中”或“已实现”。
3. 实现完成后同步更新请求参数、响应类型和状态。
4. 如果接口路径、HTTP 方法或请求字段变更，必须同步更新本文件和相关 `.http` 测试脚本。
5. 如使用 smart-doc 生成正式接口文档，本文件仍作为人工协作登记表保留。
