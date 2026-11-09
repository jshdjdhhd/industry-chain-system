# Frontend Foundation, Layout, And Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立前端基础工程、后台布局、路由体系和公共规范。

**Architecture:** 使用 Vue 3 + TypeScript + Vite 作为应用基础，Element Plus 提供基础 UI，Pinia 管理登录与应用状态，Vue Router 统一页面访问入口。公共组件和全局样式服务于所有业务页面。

**Tech Stack:** Vue 3, TypeScript, Vite, Vue Router, Pinia, Element Plus, Axios, Vitest.

---

## File Structure

- Modify: `front/src/main.ts`
- Modify: `front/src/router/index.ts`
- Modify: `front/src/layouts/AdminLayout.vue`
- Modify: `front/src/styles/index.css`
- Modify: `front/src/components/AppTable.vue`
- Modify: `front/src/components/SearchBar.vue`
- Modify: `front/src/components/StatusTag.vue`
- Modify: `front/src/views/auth/LoginView.vue`
- Modify: `front/src/views/dashboard/DashboardView.vue`

## Task 1: 工程初始化

- [ ] 创建 Vue 3 + TypeScript + Vite 工程结构。
- [ ] 安装并配置 Element Plus、Vue Router、Pinia、Axios、Vitest。
- [ ] 在 `main.ts` 中挂载全局插件和样式入口。
- [ ] 运行 `npm run build` 确认基础构建通过。

## Task 2: 后台布局

- [ ] 在 `AdminLayout.vue` 中实现侧边栏、顶部栏和内容区。
- [ ] 设计菜单结构，承载看板、企业、标签、产业链、智能挂链、日志等入口。
- [ ] 保证窄屏下布局不发生明显错位。
- [ ] 编写或维护布局相关测试。

## Task 3: 路由体系

- [ ] 在 `router/index.ts` 中配置登录页和后台子路由。
- [ ] 增加登录态守卫，未登录访问业务页面时跳转登录页。
- [ ] 保证已登录访问登录页时进入看板。
- [ ] 运行 `npm test -- src/router/index.spec.ts` 验证路由规则。

## Task 4: 公共组件与规范

- [ ] 实现 `AppTable.vue`，统一表格分页和加载状态。
- [ ] 实现 `SearchBar.vue`，统一筛选区布局。
- [ ] 实现 `StatusTag.vue`，统一状态展示。
- [ ] 在 `styles/index.css` 中沉淀页面标题、表单、按钮、卡片等基础规范。

## Task 5: 验证

- [ ] 运行 `npm test`。
- [ ] 运行 `npm run build`。
- [ ] 打开 `/login`、`/dashboard`、`/enterprises` 检查基础页面可访问。
