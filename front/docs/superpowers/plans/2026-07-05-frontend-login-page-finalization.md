# Frontend Login Page Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将登录页收敛为单个居中玻璃登录卡片，并保持真实登录功能。

**Architecture:** 只修改 `LoginView.vue` 的模板和样式，复用现有 auth store、router、Element Plus 输入框和登录按钮。背景资产保持项目已有 `login-tech-bg.png`。

**Tech Stack:** Vue 3, TypeScript, Element Plus, CSS.

---

## File Structure

- Modify: `front/src/views/auth/LoginView.vue`
- Keep: `front/public/login-tech-bg.png`

## Task 1: 删除旧卡片结构

- [ ] 删除左右分栏结构。
- [ ] 删除右侧白色登录卡片。
- [ ] 删除左侧模块标签、说明段落等非必要内容。
- [ ] 保留登录页背景。

## Task 2: 建立居中玻璃卡片

- [ ] 将 `login-shell` 改为单个居中容器。
- [ ] 设置半透明背景、blur、边框和阴影。
- [ ] 控制卡片宽度，保证桌面和移动端都可用。

## Task 3: 保留真实登录表单

- [ ] 保留用户名输入框。
- [ ] 保留密码输入框和 `show-password`。
- [ ] 保留登录按钮和 loading 状态。
- [ ] 保留 `handleLogin`，不修改 auth store 和 router。

## Task 4: 删除假功能

- [ ] 不添加“记住我”。
- [ ] 不添加“忘记密码”。
- [ ] 不添加注册、短信登录、第三方登录等未接后端功能。

## Task 5: 验证

- [ ] 运行 `npm exec prettier -- --write src/views/auth/LoginView.vue`。
- [ ] 访问 `/login`，确认返回 200。
- [ ] 检查页面只出现真实登录相关控件。
