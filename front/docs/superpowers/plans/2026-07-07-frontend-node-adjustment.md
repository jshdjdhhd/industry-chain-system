# 前端产业链节点调整实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现产业链节点移动和同级排序页面，保证前端能阻止明显非法操作并向后端提交结构化请求。

**架构：** `NodeAdjustment.vue` 负责页面交互，`nodeTreeRules.ts` 承载树结构计算和排序规则，`src/api/chain.ts` 负责后端接口，组件测试和领域函数测试共同覆盖行为。

**技术栈：** Vue 3、TypeScript、Element Plus、Vitest、@vue/test-utils。

---

## 文件结构

- 修改：`front/src/views/chain/NodeAdjustment.vue`，实现节点调整页面。
- 修改：`front/src/domain/nodeTreeRules.ts`，实现树形规则函数。
- 修改：`front/src/api/chain.ts`，封装节点移动和排序接口。
- 测试：`front/src/domain/nodeTreeRules.spec.ts`。
- 测试：`front/src/views/chain/NodeAdjustment.spec.ts`。
- 测试：`front/src/api/chain.spec.ts`。

## 任务 1：树结构规则函数

- [ ] **步骤 1：编写领域函数测试**

在 `front/src/domain/nodeTreeRules.spec.ts` 中覆盖扁平化、子孙节点收集、合法父节点过滤、同级节点获取、上移下移、插入位置、排序请求生成和非法移动报错。

- [ ] **步骤 2：实现规则函数**

在 `nodeTreeRules.ts` 中实现 `flattenNodeTree`、`getDescendantNodeIds`、`getValidParentOptions`、`getSiblingNodes`、`moveSibling`、`insertSibling`、`buildSortRequest`、`moveNodeInTree`。

- [ ] **步骤 3：运行领域函数测试**

```bash
cd front
npm test -- src/domain/nodeTreeRules.spec.ts
```

预期：所有树结构规则测试通过。

## 任务 2：节点调整页面

- [ ] **步骤 1：编写页面测试**

在 `NodeAdjustment.spec.ts` 中覆盖产业链加载、节点树渲染、选中节点上下文、阶段节点禁用移动、普通节点移动、同级排序保存和放弃草稿。

- [ ] **步骤 2：实现页面状态**

在 `NodeAdjustment.vue` 中维护 `chains`、`nodeTree`、`selectedNodeId`、`targetParentSelection`、`targetPosition`、`siblingDraft`、`sortDirty`、`moving`、`sorting`。

- [ ] **步骤 3：实现移动流程**

选择目标父节点和目标位置后调用 `moveNode`，成功后刷新节点树并提示用户。

- [ ] **步骤 4：实现排序流程**

上移、下移只修改 `siblingDraft`；点击保存时通过 `buildSortRequest` 生成完整同级顺序并调用 `sortNodes`。

- [ ] **步骤 5：运行页面测试**

```bash
cd front
npm test -- src/views/chain/NodeAdjustment.spec.ts
```

预期：页面关键交互测试通过。

## 任务 3：API 合同

- [ ] **步骤 1：补充接口测试**

在 `front/src/api/chain.spec.ts` 中断言节点移动和排序接口的路径、方法和请求体字段。

- [ ] **步骤 2：实现接口封装**

在 `front/src/api/chain.ts` 中通过统一 HTTP 封装暴露 `moveNode` 与 `sortNodes`。

- [ ] **步骤 3：运行接口测试**

```bash
cd front
npm test -- src/api/chain.spec.ts
```

预期：接口合同测试通过。

## 任务 4：最终验证

- [ ] **步骤 1：运行节点调整相关测试**

```bash
cd front
npm test -- src/domain/nodeTreeRules.spec.ts src/views/chain/NodeAdjustment.spec.ts src/api/chain.spec.ts
```

预期：相关测试全部通过。

- [ ] **步骤 2：运行前端全量测试**

```bash
cd front
npm test
```

预期：Vitest 全量测试通过。

- [ ] **步骤 3：运行构建**

```bash
cd front
npm run build
```

预期：类型检查和 Vite 构建成功。
