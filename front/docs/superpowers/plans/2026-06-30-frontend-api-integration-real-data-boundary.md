# Frontend API Integration And Real Data Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 明确前端真实数据边界，完成接口联调、mock 清理和异常处理。

**Architecture:** 通过 `src/api` 统一封装请求，通过 Pinia 保存必要状态，通过页面组件消费真实接口返回。Mock 仅用于测试和开发模式，不进入正式 UI 表达。

**Tech Stack:** Vue 3, TypeScript, Axios, Pinia, Element Plus, Vitest.

---

## File Structure

- Review: `front/src/api/http.ts`
- Review: `front/src/api/mode.ts`
- Modify: `front/src/layouts/AdminLayout.vue`
- Modify: `front/src/views/dashboard/DashboardView.vue`
- Modify: `front/src/views/enterprise/EnterpriseList.vue`
- Modify: `front/src/components/AppTable.vue`
- Modify: `front/src/components/SearchBar.vue`
- Modify: `front/src/components/StatusTag.vue`

## Task 1: API 模式确认

- [ ] 检查 `VITE_API_MOCK` 配置，确认 mock 与 backend 模式切换逻辑。
- [ ] 检查 `http.ts` 的错误提示和 401 处理。
- [ ] 保证接口失败时能提示用户，而不是由页面伪造成功状态。

## Task 2: UI Mock 文案清理

- [ ] 搜索“Mock”“演示”“示例”等文案。
- [ ] 删除状态栏中的“前端 Mock 数据”提示。
- [ ] 删除未接功能的消息按钮和固定红点数量。
- [ ] 保留必要的空状态和接口错误状态。

## Task 3: 动态数据计算

- [ ] 看板企业总数从接口返回的分布数据动态计算。
- [ ] 企业列表统计从分页 total、选中行和当前筛选状态计算。
- [ ] 不在 UI 中写死“初始企业数量”或伪造业务统计。

## Task 4: 页面切换联调

- [ ] 检查企业管理、标签管理等页面之间切换是否触发异常请求。
- [ ] 对 502 等后端不可用错误保持统一提示。
- [ ] 不用前端吞错或假数据掩盖后端未连接问题。

## Task 5: 验证

- [ ] 运行 `npm test`。
- [ ] 运行 `npm run build`。
- [ ] 打开 `/dashboard`、`/enterprises`、`/tags` 检查页面可访问。
