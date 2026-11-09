# Frontend Foundation, Layout, And Routing Design

> 补录说明：本文按项目实际开发内容整理为 spec 形态，用于归档前端基础负责人工作范围。

## 背景

产业链管理系统需要一个稳定的前端基础工程，承载登录、看板、企业管理、产业链管理、智能挂链、日志等业务模块。前端基础负责人需要先建立统一工程结构、页面布局、路由入口和开发规范，保障后续模块能在同一框架下持续集成。

## 目标

- 搭建 Vue 3 + TypeScript + Vite 前端工程。
- 建立后台管理系统的基础布局，包括侧边栏、顶部栏、内容区和登录入口。
- 配置核心业务页面路由，支持页面间稳定跳转。
- 引入 Element Plus、Pinia、Axios、Vitest 等基础能力。
- 沉淀公共样式和通用组件，降低后续页面重复开发成本。

## 设计范围

- `src/main.ts`：应用入口。
- `src/router/index.ts`：路由配置与登录守卫。
- `src/layouts/AdminLayout.vue`：后台管理框架布局。
- `src/styles/index.css`：全局样式、页面容器、组件基础样式。
- `src/components/*`：表格、搜索栏、状态标签等公共组件。
- `src/views/auth/LoginView.vue`：登录页。
- `src/views/dashboard/DashboardView.vue`：数据看板入口。

## 验收标准

- 前端项目可本地启动和构建。
- 登录页、看板页、企业管理等核心路由可访问。
- 页面布局统一，导航和内容区结构稳定。
- 公共组件可在多个页面复用。
- 基础测试和构建命令可执行。
