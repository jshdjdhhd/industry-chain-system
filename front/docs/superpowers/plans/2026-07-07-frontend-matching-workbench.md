# 前端智能挂链工作台实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 建立稳定的前端智能挂链工作台，让批量任务和单企业分析任务都能完成提交、轮询、展示和结果处理。

**架构：** 页面层由 `MatchingTask.vue` 和 `BatchWorkbench.vue` 承载交互，`matchingViewModel.ts` 统一状态派生和展示规则，`src/api/matching.ts` 负责后端接口适配，Pinia store 保存跨组件任务状态。

**技术栈：** Vue 3、TypeScript、Vite、Element Plus、Pinia、Axios、Vitest、@vue/test-utils。

---

## 文件结构

- 修改：`front/src/views/matching/MatchingTask.vue`，实现单企业任务表单、轮询、结果和操作按钮。
- 修改：`front/src/views/matching/BatchWorkbench.vue`，实现批量任务入口和摘要。
- 修改：`front/src/views/matching/matchingViewModel.ts`，封装任务状态、候选节点和操作日志。
- 修改：`front/src/api/matching.ts`，封装智能挂链接口。
- 修改：`front/src/stores/matching.ts`，保存当前任务和运行状态。
- 测试：`front/src/api/matching.spec.ts`。
- 测试：`front/src/views/matching/matchingViewModel.spec.ts`。
- 测试：`front/src/views/matching/MatchingTask.spec.ts`。

## 任务 1：API 合同与类型映射

- [ ] **步骤 1：编写 API 测试**

在 `front/src/api/matching.spec.ts` 中断言创建任务使用 `/matching/tasks/async`，查询任务使用 `/matching/tasks/{taskId}`，结果处理接口分别指向 accept、reject、manual-confirm、cancel、revoke、retry。

- [ ] **步骤 2：运行 API 测试确认合同**

```bash
cd front
npm test -- src/api/matching.spec.ts
```

预期：接口路径、请求方法和响应解包全部通过。

- [ ] **步骤 3：校准类型**

在 `front/src/types/domain.ts` 中确认 `MatchingTask`、`MatchingResult`、`MatchingDecision` 覆盖任务状态、节点路径、置信度、证据和处理状态。

## 任务 2：单企业任务页面

- [ ] **步骤 1：编写组件测试**

在 `front/src/views/matching/MatchingTask.spec.ts` 覆盖企业选择、产业链选择、提交按钮禁用状态、任务结果展示和超时提示。

- [ ] **步骤 2：实现页面交互**

在 `MatchingTask.vue` 中维护 `enterpriseId`、`chainId`、`task`、`activeResultId`、`loading`、`taskOperation` 和 `frontendTimeoutTaskId`，提交后按固定间隔轮询任务。

- [ ] **步骤 3：实现结果操作**

将采纳、拒绝、人工改选、取消、撤回、重试分别绑定到 API，操作成功后刷新任务详情并更新 Pinia store。

- [ ] **步骤 4：运行页面测试**

```bash
cd front
npm test -- src/views/matching/MatchingTask.spec.ts
```

预期：关键交互与展示状态通过。

## 任务 3：ViewModel 状态规则

- [ ] **步骤 1：补充状态派生测试**

在 `matchingViewModel.spec.ts` 覆盖 `canCancelTask`、`canRetryTask`、`canDecideResult`、`isLowConfidenceCandidate`、`pickActiveResult`、`buildMatchingOperationLogs`。

- [ ] **步骤 2：实现派生函数**

在 `matchingViewModel.ts` 中集中实现状态判断和展示文本，页面组件只消费函数结果，避免模板中重复业务判断。

- [ ] **步骤 3：运行 ViewModel 测试**

```bash
cd front
npm test -- src/views/matching/matchingViewModel.spec.ts
```

预期：状态规则和操作日志文本全部通过。

## 任务 4：批量工作台保留默认入口

- [ ] **步骤 1：确认默认 Tab**

在 `MatchingTask.vue` 中保持 `activeTab` 初始值为 `batch`。

- [ ] **步骤 2：确认批量组件职责**

在 `BatchWorkbench.vue` 中保留批量企业、产业链选择和批量任务摘要，不复用单企业轮询状态。

- [ ] **步骤 3：运行匹配相关测试**

```bash
cd front
npm test -- src/api/matching.spec.ts src/views/matching/matchingViewModel.spec.ts
```

预期：批量入口不破坏单企业任务合同。

## 任务 5：最终验证

- [ ] **步骤 1：运行前端测试**

```bash
cd front
npm test
```

预期：Vitest 测试全部通过。

- [ ] **步骤 2：运行生产构建**

```bash
cd front
npm run build
```

预期：`vue-tsc -b && vite build` 成功完成。

- [ ] **步骤 3：检查工作区**

```bash
git status --short
```

预期：只包含本需求相关文件变更。
