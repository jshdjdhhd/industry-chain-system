# 前端产业链节点调整设计

> 本文件由 Codex 按 superpowers:brainstorming 规格文档形态生成，用于记录产业链节点移动与排序的前端 Spec Coding 需求。

## 背景

产业链节点需要支持树形维护。前端已有 `src/views/chain/NodeAdjustment.vue` 和 `src/domain/nodeTreeRules.ts`，用于展示节点树、计算可选父节点、阻止非法移动、预览同级顺序并提交保存。该能力影响产业链层级结构和企业挂链归属，必须在前端明确约束。

## 目标

- 支持选择产业链并加载节点树。
- 支持选中节点后展示当前路径、原父节点、原位置和影响范围。
- 支持移动普通节点及其子树到新的父节点或根层级。
- 支持在同级节点中上移、下移并一次性保存完整顺序。
- 阻止阶段节点移动或删除，只允许改名和排序。
- 阻止节点移动到自身或自己的子节点下。
- 移动和排序过程中禁用冲突操作，避免重复提交。

## 非目标

- 不在前端直接修改数据库。
- 不支持跨产业链移动节点。
- 不在本规格中实现节点新增、删除和改名页面。
- 不绕过后端对层级和权限的校验。

## 交互设计

- 页面顶部提供产业链下拉和刷新按钮。
- 左侧展示节点树，点击节点后右侧展示移动和排序操作区。
- 节点移动区展示当前节点上下文，用户选择目标父节点和目标位置后点击“预览并移动”。
- 同级排序区展示当前同级节点列表，用户通过上移、下移调整顺序，点击“保存排序”提交。
- 当前选中节点在同级列表中高亮。
- 无产业链、无节点或未选中节点时显示空状态。

## 规则设计

- `flattenNodeTree` 将树形节点转为带路径、父节点、层级和位置的扁平列表。
- `collectDescendantIds` 和 `getDescendantNodeIds` 用于识别子孙节点。
- `getValidParentOptions` 排除自身和子孙节点，生成合法目标父节点列表。
- `getSiblingNodes` 获取同级节点列表。
- `moveSibling` 和 `insertSibling` 用于前端排序预览。
- `buildSortRequest` 将同级草稿转成后端保存所需的 `nodeOrders`。
- `moveNodeInTree` 在 Mock 或本地预览中维护 parentId、levelNo 和 sortNo。

## 接口设计

- `fetchChains` 获取产业链列表。
- `fetchNodeTree(chainId)` 获取目标产业链节点树。
- `moveNode(id, payload)` 提交节点移动，payload 包含目标父节点和排序位置。
- `sortNodes(chainId, payload)` 提交同级完整排序，payload 包含父节点和 nodeOrders。

## 错误处理

- 未选择节点时不允许移动或排序。
- 阶段节点禁用移动入口并显示提示。
- 目标父节点非法时不生成可选项。
- 保存失败时保留当前草稿并提示用户。
- 刷新后清理失效选中状态，避免操作不存在的节点。

## 测试策略

- `nodeTreeRules.spec.ts` 覆盖树扁平化、子孙节点收集、合法父节点过滤、同级移动、插入排序、排序请求生成和非法移动报错。
- `NodeAdjustment.spec.ts` 覆盖页面空状态、节点选择、阶段节点禁用、移动按钮、保存排序按钮和刷新行为。
- `chain.spec.ts` 覆盖节点移动与排序 API 的请求路径和参数。

## 验收标准

- 普通节点可以移动到合法父节点并保持子树层级。
- 节点不能移动到自身或子孙节点下。
- 阶段节点移动入口被禁用并展示明确提示。
- 同级排序保存的 `nodeOrders` 与页面草稿顺序一致。
- 相关测试通过：`npm test -- src/domain/nodeTreeRules.spec.ts src/views/chain/NodeAdjustment.spec.ts src/api/chain.spec.ts`。
