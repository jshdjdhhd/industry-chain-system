# Frontend Enterprise UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不影响后端接入的前提下，完成前端关键页面和公共组件的企业级视觉优化。

**Architecture:** 保持现有 Vue 页面结构和 Element Plus 组件体系，通过模板微调、局部样式和全局设计变量完成视觉升级。所有业务数据仍来自现有接口和 store。

**Tech Stack:** Vue 3, TypeScript, Element Plus, CSS, ImageGen assets, Vitest.

---

## File Structure

- Modify: `front/src/styles/index.css`
- Modify: `front/src/layouts/AdminLayout.vue`
- Modify: `front/src/views/auth/LoginView.vue`
- Modify: `front/src/views/dashboard/DashboardView.vue`
- Modify: `front/src/views/enterprise/EnterpriseList.vue`
- Modify: `front/src/components/AppTable.vue`
- Modify: `front/src/components/SearchBar.vue`
- Modify: `front/src/components/StatusTag.vue`
- Add: `front/public/industry-map-bg.png`
- Add: `front/public/login-tech-bg.png`

## Task 1: 全局视觉规范

- [ ] 在 `index.css` 中统一颜色变量、页面背景、卡片、按钮、输入框和表格样式。
- [ ] 保持后台系统信息密度，不做营销落地页风格。
- [ ] 确保移动端文本不溢出。

## Task 2: 布局优化

- [ ] 优化 `AdminLayout.vue` 的侧边栏、顶部栏和内容区层级。
- [ ] 删除未接功能的消息入口和固定红点。
- [ ] 保证导航切换不触发额外假逻辑。

## Task 3: 看板优化

- [ ] 优化指标卡片、分布图、匹配概览、进度图和最近操作表格。
- [ ] 删除明显网格状背景，改为干净浅色运营台样式。
- [ ] 保留真实数据计算逻辑。

## Task 4: 企业列表优化

- [ ] 优化列表页标题、统计区、筛选区和表格视觉层次。
- [ ] 保证批量操作、入库、出库、删除、导入等功能入口不变。
- [ ] 保持选中数量和 total 动态计算。

## Task 5: 公共组件优化

- [ ] 优化 `AppTable.vue` 表格容器和分页区域。
- [ ] 优化 `SearchBar.vue` 筛选布局。
- [ ] 优化 `StatusTag.vue` 状态色和标签样式。

## Task 6: 验证

- [ ] 运行 `npm exec prettier -- --check` 检查改动文件。
- [ ] 运行 `npm test`。
- [ ] 运行 `npm run build`。
- [ ] 打开 `/login`、`/dashboard`、`/enterprises` 检查页面可访问。
