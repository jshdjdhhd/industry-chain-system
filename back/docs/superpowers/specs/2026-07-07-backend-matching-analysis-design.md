# 后端智能挂链与 AI 分析设计

> 本文件由 Codex 按 superpowers:brainstorming 规格文档形态生成，用于记录后端智能挂链与 AI 分析能力的 Spec Coding 需求。

## 背景

后端已有 `matching`、`llm`、`analysis`、`chain` 与 `enterprise` 相关模块，承担企业与产业链节点的推荐、AI 对话分析、批量任务、采纳拒绝、人工确认和评测能力。前端通过 `/api/matching/**` 与后端协作，后端需要保证任务异步执行、结果可追踪、权限可控和失败可恢复。

## 目标

- 支持创建单企业智能挂链任务和异步任务。
- 支持创建批量智能挂链任务。
- 支持查询任务详情、取消、撤回和重试。
- 支持采纳候选结果、拒绝候选结果和人工确认挂链。
- 支持 AI 挂链分析对话和 SSE 流式对话。
- 支持运行智能挂链评测。
- 所有受保护接口使用 Spring Security 权限控制。
- 关键操作写入操作日志。

## 非目标

- 不让前端直接写入企业挂链关系。
- 不在 Controller 中实现算法或数据库访问。
- 不在响应中泄露外部模型密钥、完整 Token、SQL 或堆栈。
- 不跳过人工采纳直接把推荐结果转为正式挂链关系。

## 接口设计

- `POST /api/matching/tasks`：同步创建单企业任务。
- `POST /api/matching/tasks/async`：异步创建单企业任务。
- `POST /api/matching/tasks/batch-async`：创建批量任务。
- `GET /api/matching/tasks/{taskId}`：查询任务详情。
- `POST /api/matching/tasks/{taskId}/cancel`：取消任务。
- `POST /api/matching/tasks/{taskId}/revoke`：撤回任务。
- `POST /api/matching/tasks/{taskId}/retry`：重试任务。
- `POST /api/matching/results/{resultId}/accept`：采纳候选结果。
- `POST /api/matching/results/{resultId}/reject`：拒绝候选结果。
- `POST /api/matching/manual-confirm`：人工确认挂链。
- `POST /api/matching/ai-chat`：AI 分析对话。
- `POST /api/matching/ai-chat/stream`：SSE 流式 AI 分析。
- `POST /api/matching/evaluations/run`：运行评测。

## 领域模型

- `MatchingTask`：记录企业、产业链、模型名称、任务状态、错误信息和创建人。
- `MatchingResult`：记录候选节点、置信度、分析原因、证据和处理状态。
- `MatchingProvider`：统一匹配提供者接口，隔离规则、embedding、LLM 和 AI 分析策略。
- `MatchingTaskWorker`：执行异步任务并更新状态。
- `AiChatService`：提供挂链上下文下的 AI 对话。
- `MatchingEvaluationService`：对匹配质量进行评测。

## 权限与审计

- 创建任务需要 `matching:task:create` 或 `matching:task:execute`。
- 查询任务需要 `matching:task:query`。
- 取消与撤回需要 `matching:task:cancel`。
- 重试需要 `matching:task:retry`。
- 采纳需要 `matching:result:accept`。
- 拒绝需要 `matching:result:reject`。
- 人工确认需要 `matching:manual-confirm:create`。
- 评测需要 `matching:evaluation:run`。
- 创建、取消、撤回、重试、采纳、拒绝、人工确认、AI 对话和评测操作记录操作日志。

## 错误处理

- 参数错误返回统一业务失败响应。
- 无权限由 Spring Security 返回 403。
- 未认证由认证入口返回 401。
- 外部模型失败时任务应进入失败状态，并保留用户可读错误信息。
- 采纳、拒绝和人工确认必须在 Service 事务中校验任务和结果状态。

## 测试策略

- `MatchingControllerTest` 覆盖接口权限、统一响应和请求校验。
- `MatchingServiceTest` 覆盖任务创建、异步提交、批量提交、取消、撤回、重试。
- Provider 测试覆盖规则、embedding、LLM 和 AI 分析策略。
- `AiChatServiceImplTest` 覆盖 AI 对话上下文和异常处理。
- `MatchingEvaluationServiceTest` 覆盖评测报告生成。

## 验收标准

- 单企业和批量任务都能创建并查询。
- 任务生命周期状态流转明确，失败原因可读。
- 结果采纳、拒绝和人工确认受事务保护。
- AI 对话普通响应和 SSE 流式响应都可用。
- 权限注解覆盖所有受保护接口。
- 相关测试通过：`mvn -Dtest=MatchingControllerTest,MatchingServiceTest,AiChatServiceImplTest,MatchingEvaluationServiceTest test`。
