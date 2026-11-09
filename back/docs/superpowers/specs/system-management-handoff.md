# 系统管理模块 — Agent 交接文档

## 你是谁

你是接手「产业链管理系统 — 系统管理模块」的下一个 Agent。上一个 Agent 已经完成了后端全部代码和前端基础框架，但 UI 需要重构。

## 项目背景

这是一个 Vue 3 + Spring Boot 的教学项目，用于管理企业、产业链和智能挂链推荐。系统管理模块是从零搭建的，之前只有一个空壳菜单。

## 已完成的工作

### 后端（全部完成，无需修改）

后端代码在 `groupproject-back/back/src/main/java/git/ggwzz/system/` 下：

```
system/
├── controller/   SysUserController, SysRoleController, SysPermissionController, UserProfileController
├── dto/          9 个请求体（CreateUser/UpdateUser/UserQuery/CreateRole/UpdateRole/AssignRoles/AssignPermissions/ChangePassword/ResetPassword）
├── service/      SysUserService + SysRoleService（接口 + 实现）
└── vo/           SysUserVO, SysRoleVO, SysRoleDetailVO, SysPermissionTreeVO
```

额外修改：
- `SysPermissionMapper.java` 新增了 `selectByRoleId()` 方法
- `SysPermissionMapper.xml` 在 `src/main/resources/mapper/` 下
- `V11__system_permissions.sql` 在 `src/main/resources/db/migration/` 下

后端 API 路径：
- `/api/system/users` — 用户 CRUD + 状态/角色/密码管理
- `/api/system/roles` — 角色 CRUD + 权限分配
- `/api/system/permissions/tree` — 权限树（只读）
- `/api/system/profile` — 个人设置 + 修改密码

### 前端（基础完成，UI 需要重构）

已完成的：
- `src/api/system.ts` — 完整 API 层（已验证可用）
- `src/types/domain.ts` — 已追加系统管理类型定义
- `src/api/operationLog.ts` — 已扩展筛选参数
- `src/layouts/AdminLayout.vue` — 已更新侧边栏菜单（图标用 Key 不用 UserSetting，后者不存在）
- `src/router/index.ts` — 已添加系统管理路由

当前页面状态（需要你重构）：
- `src/views/system/UserList.vue` — 用户管理（独立页面）
- `src/views/system/RoleList.vue` — 角色管理（独立页面）
- `src/views/system/PermissionTree.vue` — 权限树（独立页面）
- `src/views/system/UserProfile.vue` — 个人设置（独立页面）
- `src/views/system/SystemOperationLog.vue` — 操作日志（独立页面，这个保持独立）

## 你需要做的事

### 核心任务：合并为统一系统管理页面

用户要求把用户管理、角色管理、权限管理、个人设置合并到一个页面 `SystemManage.vue` 中，用 `el-tabs` 切换，而不是 4 个独立页面。

```
┌──────────────────────────────────────────────────────────┐
│  系统管理                                                 │
├──────────────────────────────────────────────────────────┤
│  [用户管理] [角色管理] [权限管理] [个人设置]               │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  （Tab 内容区域，每个 Tab 用 el-card 包裹）                │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

操作日志（SystemOperationLog.vue）保持独立页面不变。

### 具体步骤

1. 读取现有的 4 个页面文件（UserList.vue、RoleList.vue、PermissionTree.vue、UserProfile.vue）
2. 创建 `SystemManage.vue`，用 el-tabs 合并 4 个页面的功能
3. 删除旧的 4 个独立页面文件
4. 更新 `router/index.ts`：删除 4 个旧路由，添加 `/system/manage` 路由
5. 更新 `AdminLayout.vue`：system 分组改为 2 个子菜单（系统管理 + 操作日志）
6. 运行 `npx vite build` 验证构建通过

### UI 设计要求

- 使用 Element Plus 的 `el-card` 包裹每个 Tab 内容
- 表格使用 `stripe` 斑马纹
- 搜索栏和按钮间距 `gap: 12px`
- 整体风格与项目已有的企业管理页面（EnterpriseList.vue）保持一致
- 变量名冲突时加前缀（如 `usersLoading`、`rolesLoading`）

## 已踩的坑（避免重复）

1. **`UserSetting` 图标不存在** — Element Plus 没有这个图标，用 `Key` 代替
2. **`http.ts` 的删除函数叫 `remove` 不叫 `del`** — `import { remove } from "./http"`
3. **`unlinkEnterpriseFromNode` 在 `chain.ts` 里不在 `matching.ts`** — MatchingLogs.vue 之前导入路径写错了，已修复
4. **子 Agent 无法执行文件写入** — 如果你也是 Agent，直接用 Write/Edit 工具，不要 spawn 子 Agent

## 关键文件路径

```
C:\Users\18443\Desktop\大项目开发\
├── docs/superpowers/
│   ├── specs/2026-07-03-system-management-design.md    # 设计规格
│   └── plans/2026-07-03-system-management.md           # 实现计划
├── groupproject-back/back/src/main/java/git/ggwzz/
│   ├── system/           # 系统管理后端代码（已完成）
│   ├── auth/             # 已有的用户/角色/权限实体和 Mapper
│   └── operationlog/     # 已有的操作日志模块
└── groupproject/front/src/
    ├── api/system.ts     # 系统管理 API（已完成）
    ├── views/system/     # 系统管理页面（需要重构）
    ├── router/index.ts   # 路由配置
    └── layouts/AdminLayout.vue  # 侧边栏菜单
```
