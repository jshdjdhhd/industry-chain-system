# 前端智能挂链工作台设计

> 本文件由 Codex 按 superpowers:brainstorming 规格文档形态生成，用于记录前端智能挂链工作台的 Spec Coding 需求。

## 背景

前端已有 `src/views/matching/MatchingTask.vue`、`BatchWorkbench.vue`、`matchingViewModel.ts` 与 `src/api/matching.ts`，承担单企业挂链分析、批量挂链任务、候选节点展示、采纳、拒绝、人工改选、撤回和重试等操作。该能力是企业与产业链节点建立关系的核心入口，需要在 UI、状态流转和 API 适配上保持稳定。

## 目标

- 批量工作台作为默认入口，支持批量选择企业与产业链并提交异步任务。
- 单企业模式支持选择企业、选择产业链、加载节点树、提交分析任务并轮询结果。
- 结果区展示候选节点、置信度、风险提示、分析原因、证据和处理状态。
- 支持采纳、拒绝、人工改选、取消、撤回、重试和刷新状态。
- 任务等待超时时给出前端可恢复提示，不误判后端任务失败。
- Mock 模式和真实 API 模式都能复用同一页面状态模型。

## 非目标

- 不在前端实现挂链推荐算法。
- 不新增与后端不一致的任务状态。
- 不绕过后端权限和业务校验。
- 不在本规格中改造批量任务的后端执行策略。

## 用户流程

1. 用户进入智能挂链页面，默认看到批量工作台。
2. 用户可切换到单企业模式，搜索并选择企业，再选择目标产业链。
3. 页面加载该产业链节点树，用户提交分析任务。
4. 前端通过 `/api/matching/tasks/async` 创建任务，通过 `/api/matching/tasks/{taskId}` 轮询。
5. 任务完成后展示候选节点列表和分析详情。
6. 用户可采纳候选、拒绝候选或人工确认其他节点。
7. 任务运行中可取消，完成后可撤回，失败后可重试。

## 组件设计

- `MatchingTask.vue`：页面容器，负责表单、任务轮询、结果展示和操作按钮。
- `BatchWorkbench.vue`：批量任务工作区，负责批量提交和批量任务摘要展示。
- `matchingViewModel.ts`：封装状态判断、候选节点扁平化、风险提示和操作日志构建。
- `src/api/matching.ts`：封装任务创建、查询、采纳、拒绝、人工确认、取消、撤回、重试和 AI 对话接口。
- `src/stores/matching.ts`：保存当前任务和页面级状态。
- `src/stores/options.ts`：提供企业与产业链下拉选项。

## 数据与接口

- `createMatchingTask` 请求包含 `enterpriseId` 与 `chainId`。
- `fetchMatchingTask` 返回 `MatchingTask`，包含任务状态、候选结果和错误信息。
- `MatchingResult` 至少包含 `resultId`、`nodeId`、`nodePath`、`confidence`、`reason`、`evidence`、`decision`。
- 前端只展示后端返回的业务状态，不自行生成正式挂链关系。

## 错误处理

- 企业或产业链未选择时阻止提交并提示用户。
- 节点树加载失败时保留当前选择并提示刷新。
- 轮询超过前端等待时间时显示可刷新状态，不覆盖后端任务状态。
- 采纳、拒绝、人工改选失败时保留当前任务结果并展示错误消息。
- 低置信候选需要显示风险提示，引导用户人工复核。

## 测试策略

- `matchingViewModel.spec.ts` 覆盖状态判断、候选选择、风险提示和操作日志文本。
- `matching.spec.ts` 覆盖 API 请求路径、参数和响应映射。
- `MatchingTask.spec.ts` 覆盖关键文案、按钮状态、提交前校验和轮询状态。
- Mock 模式测试覆盖本地数据路径，真实 API 模式测试覆盖 axios 封装调用。

## 验收标准

- 单企业任务可以从选择企业到展示候选结果形成完整闭环。
- 批量工作台仍为默认 Tab。
- 任务运行、完成、失败、取消、撤回、重试状态都有明确 UI 表达。
- 候选节点详情展示置信度、分析原因和证据。
- 前端测试通过：`npm test -- src/api/matching.spec.ts src/views/matching/matchingViewModel.spec.ts`。
- 构建通过：`npm run build`。
