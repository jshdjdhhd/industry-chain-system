# 单企业人工智能分析设计

## 背景

当前智能挂链单企业流程仍以「推荐」为核心：后端通过本地规则、embedding 和 LLM 重排序组合出候选节点，前端展示为「单企业智能推荐」「生成推荐」「推荐理由」。用户已经将通用 LLM 模块抽取到 `git.ggwzz.llm`，希望单企业能力转向「人工智能分析」：只调用大模型，对企业与产业链节点进行分析，输出建议回答与分析结论。

## 目标

- 将单企业 Tab 从「单企业智能推荐」改为「人工智能分析」。
- 单企业任务只通过 `LlmClient` 调用大模型完成企业与挂链分析。
- 大模型输入包含企业画像和目标产业链节点列表。
- 大模型输出可落入现有 `MatchingResult` 结构：候选节点、置信度、建议回答、分析依据和风险提示。
- 保留现有任务、轮询、采纳、拒绝、人工改选和撤回流程。
- 批量挂链暂时保留现有本地批量引擎，不改为纯 LLM。

## 非目标

- 不改数据库表结构。
- 不重构批量挂链工作台。
- 不移除规则、embedding、批量本地引擎相关代码。
- 不让 LLM 直接写入正式挂链关系，仍需用户采纳或人工确认。

## 方案选择

采用「单企业纯 LLM 分析，批量保留现状」方案。

该方案最贴合「只调用大模型进行企业与挂链分析」的目标，同时控制批量任务的成本和超时风险。单企业链路可以承担一次完整 LLM 分析的延迟；批量链路继续使用已优化过的 `MatchingBatchLocalEngine`。

## 后端设计

### LLM 模块边界

通用 LLM 能力继续由 `git.ggwzz.llm` 提供：

- `LlmClient`
- `LlmMessage`
- `LlmChatCompletion`
- `OpenAiCompatibleLlmClient`
- `LlmProperties`

业务侧不直接拼 HTTP 请求，只通过 `LlmClient.complete(...)` 调用大模型。

### 单企业分析 Provider

新增 `SingleEnterpriseAiAnalysisMatchingProvider`，作为单企业默认 `MatchingProvider`。该 Provider 的 `getModelName()` 返回 `LLM_ANALYSIS_V1`，职责为：

1. 使用 `EnterpriseQueryService.getById(enterpriseId)` 读取企业画像。
2. 使用 `ChainNodeQueryService.listByChainId(chainId)` 读取该产业链下全部节点。
3. 构建「人工智能分析」prompt。
4. 调用 `LlmClient.complete(...)`。
5. 解析 LLM JSON 输出为 `MatchingResult` 列表。

`MatchingService.createTask(...)`、`MatchingService.createTaskAsync(...)` 和 `MatchingTaskWorker.execute(...)` 使用该默认 Provider。`MatchingService.createBatchTasksAsync(...)` 继续显式使用 `HybridEmbeddingMatchingProvider` 和 `MatchingBatchLocalEngine`，避免批量任务切到纯 LLM。

该 Provider 不调用以下本地推荐链路：

- `RuleBasedMatchingProvider.match(...)`
- `HybridEmbeddingMatchingProvider.match(...)`
- `HybridEmbeddingMatchingProvider.matchCandidates(...)`
- embedding 向量服务
- 本地高置信跳过 LLM 逻辑
- LLM 失败后回退本地推荐逻辑

### Prompt 输出约束

LLM 必须返回 JSON 对象，建议结构如下：

```json
{
  "analysis_summary": "对企业主营业务和产业链匹配关系的总体判断",
  "suggested_answer": "给业务人员看的建议回答",
  "results": [
    {
      "node_id": 101,
      "confidence": 0.86,
      "suggestion": "建议挂接到该节点",
      "analysis": "主营产品和节点定义匹配的原因",
      "risk": "需要人工复核的风险点"
    }
  ]
}
```

解析后：

- `node_id` 写入 `MatchingResult.nodeId`。
- `confidence` 写入 `MatchingResult.confidence`，范围限制为 `0.0000` 到 `0.9900`。
- `reason` 使用「建议回答 + 分析依据 + 风险提示」组合成业务可读文本。
- `evidence` 写入结构化 JSON，至少包含：
  - `model`: `LLM_ANALYSIS_V1`
  - `prompt_version`
  - `analysis_summary`
  - `suggested_answer`
  - `analysis`
  - `risk`
  - `llm_provider`
  - `llm_model`

LLM 返回未知节点、重复节点或非法置信度时，解析器过滤或截断；若没有任何可用结果，任务失败，不生成本地推荐。

### 错误处理

- 企业不存在：返回空结果并记录警告，保持现有 Provider 行为。
- 产业链节点为空：返回空结果并记录警告。
- LLM 未配置或调用失败：抛出业务异常，由异步任务标记为 `FAILED`。
- LLM 返回非 JSON 或无有效节点：抛出业务异常，由异步任务标记为 `FAILED`。
- 不做本地推荐回退，避免「人工智能分析」名义下仍输出本地算法结果。

### 现有接口兼容

继续使用现有接口：

- `POST /api/matching/tasks/async`
- `GET /api/matching/tasks/{taskId}`
- `POST /api/matching/results/{resultId}/accept`
- `POST /api/matching/results/{resultId}/reject`
- `POST /api/matching/manual-confirm`

这样前端不需要新增 API，只调整文案和展示语义。

## 前端设计

### 单企业页面文案

`MatchingTask.vue` 的单企业 Tab 调整为：

- Tab 名称：`人工智能分析`
- 页面说明：强调「由大模型分析企业画像与产业链节点，给出挂链建议和分析依据」
- 主按钮：`开始分析`
- 加载文案：`分析中`
- 完成提示：`人工智能分析已生成`
- 失败提示：`人工智能分析失败，请检查 LLM 配置或稍后重试`
- 结果区域标题：`分析与建议`

### 展示内容

继续复用 `MatchingTask.results`：

- 候选节点列表显示 LLM 给出的节点和置信度。
- 详情区突出 `reason` 中的建议回答、分析依据和风险提示。
- `evidence` 可继续作为展开详情或操作日志的结构化证据。

前端不新增独立状态模型，避免和现有任务轮询、采纳、拒绝、人工改选流程重复。

## 数据流

1. 用户在单企业 Tab 选择企业和产业链。
2. 前端调用 `createMatchingTask(...)`，仍请求 `/matching/tasks/async`。
3. 后端创建任务，异步调用单企业 LLM 分析 Provider。
4. Provider 读取企业和节点上下文，调用 `LlmClient`。
5. Parser 将 LLM JSON 转成 `MatchingResult`。
6. Worker 保存结果并将任务置为 `COMPLETED`。
7. 前端轮询任务并展示「分析与建议」。
8. 用户采纳、拒绝或人工改选，沿用现有接口和事务逻辑。

## 测试策略

### 后端单元测试

- Provider 应只调用 `LlmClient.complete(...)`，不调用 `HybridEmbeddingMatchingProvider` 或规则 Provider。
- Prompt builder 应包含企业画像、节点列表、输出 JSON 约束和分析任务说明。
- Parser 应接受合法 JSON，并过滤未知节点、重复节点和非法置信度。
- LLM 调用失败时，Provider 抛出业务异常，不回退本地结果。
- LLM 返回空结果或非法 JSON 时，Provider 抛出业务异常。

### 后端任务测试

- 单企业异步任务成功时保存 `LLM_ANALYSIS_V1` 证据。
- 单企业异步任务失败时任务状态为 `FAILED`，错误信息可读。
- 批量任务仍走 `MatchingBatchLocalEngine`，不调用纯 LLM Provider。

### 前端测试

- API 路径保持 `/matching/tasks/async` 不变。
- 单企业 Tab 展示「人工智能分析」和「开始分析」。
- 分析完成提示、失败提示和操作日志文案不再使用「智能推荐」作为单企业主语。
- 采纳、拒绝、人工改选按钮行为保持不变。

## 验收标准

- 单企业链路在代码层面没有规则、embedding 或本地回退参与分析结果生成。
- 单企业分析结果的 evidence 标识为 `LLM_ANALYSIS_V1`。
- LLM 不可用时单企业任务失败，而不是输出本地推荐结果。
- 批量挂链仍可使用当前批量本地引擎。
- 前端单企业入口和结果区展示为「人工智能分析」语义。
- 后端相关单元测试和前端相关测试通过。
