# Frontend Enterprise UI Polish Design

> 补录说明：本文按项目实际 UI 美化过程整理，用于归档前端视觉优化工作。

## 背景

前端基础功能完成后，系统需要从教学式页面升级为更完整的企业级后台界面。视觉优化需在不影响后端接口、不修改核心数据结构和路由逻辑的前提下完成。

## 目标

- 提升后台整体视觉质感和一致性。
- 优化登录页、数据看板、企业列表等关键页面。
- 强化公共表格、搜索区、状态标签等组件表现。
- 控制页面信息密度，使其符合企业级管理系统使用场景。
- 保证所有样式优化不破坏既有功能和接口联调。

## 非目标

- 不改 `src/api/`、`src/types/`、`src/stores/`、`src/router/` 的业务合同。
- 不新增未接后端的假功能。
- 不用参考图内容伪装完整产品能力。

## 设计方向

采用冷静、克制的企业级蓝白视觉体系，强调清晰层级、稳定布局和可扫描性。登录页可适度强化科技感，看板和列表页以业务可读性为优先。

## 涉及范围

- `src/styles/index.css`
- `src/layouts/AdminLayout.vue`
- `src/views/auth/LoginView.vue`
- `src/views/dashboard/DashboardView.vue`
- `src/views/enterprise/EnterpriseList.vue`
- `src/components/AppTable.vue`
- `src/components/SearchBar.vue`
- `src/components/StatusTag.vue`
- `public/industry-map-bg.png`
- `public/login-tech-bg.png`

## 验收标准

- 登录页、看板页、企业列表整体风格统一。
- dashboard 不使用明显网格状背景样式。
- 企业列表统计和筛选区视觉清晰，不遮挡内容。
- 公共组件样式统一，按钮、输入框、表格状态一致。
- 构建和测试通过。
