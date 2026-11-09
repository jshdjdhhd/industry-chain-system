# 系统管理模块设计规格

## 1. 背景与目标

### 现状问题

- 侧边栏"系统管理"分组仅有一个"运行概览"菜单项，指向 `/dashboard`（数据看板），与工作台分组的"数据看板"重复。
- 系统管理模块无独立页面、路由、API 或 Store，是空壳占位。
- 后端 `auth` 包已有 `SysUser`、`SysRole`、`SysPermission`、`SysUserRole`、`SysRolePermission` 实体和 Mapper，数据库表已建好，但仅用于登录认证，未暴露管理接口。
- 需求文档定义了三种角色（系统管理员/产业管理人员/普通查看人员），但目前仅有 `admin` 一个用户。

### 目标

将系统管理模块从空壳升级为完整可用的后台管理功能，包含用户管理、角色管理、权限管理、操作日志和个人设置。

---

## 2. 功能清单

| 编号 | 子功能 | 路由 | 优先级 |
|------|--------|------|--------|
| S1 | 用户管理 | `/system/users` | P0 |
| S2 | 角色管理 | `/system/roles` | P0 |
| S3 | 权限管理 | `/system/permissions` | P1 |
| S4 | 操作日志 | `/system/operation-logs` | P0 |
| S5 | 个人设置 | `/system/profile` | P1 |

---

## 3. 菜单结构调整

### 删除

- 系统管理分组下的"运行概览"菜单项（`/dashboard`，与工作台重复）。

### 新增

```
系统管理 (system, icon: Setting)
├── 用户管理     → /system/users          icon: User
├── 角色管理     → /system/roles          icon: UserFilled
├── 权限管理     → /system/permissions    icon: Lock
├── 操作日志     → /system/operation-logs icon: Document
└── 个人设置     → /system/profile        icon: UserSetting
```

### 操作日志路由迁移

现有 `/operation-logs` 路由和 `OperationLog.vue` 页面迁移到 `/system/operation-logs`。旧路由做重定向兼容。

---

## 4. 后端设计

### 4.1 包结构

```
git.ggwzz.system/
├── controller/
│   ├── SysUserController        # 用户管理 CRUD
│   ├── SysRoleController        # 角色管理 CRUD
│   ├── SysPermissionController  # 权限树查询（只读）
│   └── UserProfileController    # 个人设置（修改密码）
├── service/
│   ├── SysUserService
│   ├── SysRoleService
│   └── impl/
│       ├── SysUserServiceImpl
│       └── SysRoleServiceImpl
├── dto/
│   ├── CreateUserRequest
│   ├── UpdateUserRequest
│   ├── UserQuery
│   ├── CreateRoleRequest
│   ├── UpdateRoleRequest
│   ├── AssignRoleRequest        # 给用户分配角色
│   ├── AssignPermissionRequest  # 给角色分配权限
│   └── ChangePasswordRequest
└── vo/
    ├── SysUserVO
    ├── SysRoleVO
    ├── SysRoleDetailVO          # 含权限列表的角色详情
    └── SysPermissionTreeVO
```

### 4.2 API 设计

#### 用户管理 `/api/system/users`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/users` | 分页查询用户列表，支持按用户名/状态筛选 |
| GET | `/api/system/users/{id}` | 查询用户详情（含角色列表） |
| POST | `/api/system/users` | 新增用户（密码自动加密） |
| PUT | `/api/system/users/{id}` | 修改用户基本信息 |
| DELETE | `/api/system/users/{id}` | 逻辑删除用户 |
| PUT | `/api/system/users/{id}/status` | 启用/停用/锁定用户 |
| PUT | `/api/system/users/{id}/roles` | 给用户分配角色 |
| PUT | `/api/system/users/{id}/password/reset` | 管理员重置用户密码 |

#### 角色管理 `/api/system/roles`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/roles` | 查询角色列表 |
| GET | `/api/system/roles/{id}` | 查询角色详情（含权限列表） |
| POST | `/api/system/roles` | 新增角色 |
| PUT | `/api/system/roles/{id}` | 修改角色基本信息 |
| DELETE | `/api/system/roles/{id}` | 逻辑删除角色 |
| PUT | `/api/system/roles/{id}/permissions` | 给角色分配权限 |

#### 权限管理 `/api/system/permissions`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/permissions/tree` | 查询权限树（只读） |

#### 个人设置 `/api/system/profile`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/system/profile` | 获取当前用户信息 |
| PUT | `/api/system/profile/password` | 修改当前用户密码（需验证旧密码） |

#### 操作日志 `/api/operation-logs`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/operation-logs` | 分页查询（已有），增加按操作人/动作/时间范围筛选 |

### 4.3 数据复用

- 复用已有实体：`SysUser`、`SysRole`、`SysPermission`、`SysUserRole`、`SysRolePermission`
- 复用已有 Mapper：`SysUserMapper`、`SysRoleMapper`、`SysPermissionMapper`、`SysUserRoleMapper`、`SysRolePermissionMapper`
- 新增 Mapper 方法：按条件分页查询用户、批量操作用户角色/角色权限
- 操作日志复用 `OperationLogService`，扩展查询条件

### 4.4 安全约束

- 所有 `/api/system/**` 接口需要认证
- 用户管理、角色管理接口需要 `system:user:manage` 和 `system:role:manage` 权限（新增权限码）
- 个人设置接口仅限当前用户操作
- 删除管理员账号时需校验至少保留一个管理员
- 角色删除时需校验无用户关联

---

## 5. 前端设计

### 5.1 页面组件

```
src/views/system/
├── UserList.vue              # 用户列表（表格 + 搜索 + 新增/编辑弹窗）
├── RoleList.vue              # 角色列表（表格 + 新增/编辑弹窗 + 权限分配弹窗）
├── PermissionTree.vue        # 权限树（只读展示）
├── SystemOperationLog.vue    # 操作日志（表格 + 筛选）
├── UserProfile.vue           # 个人设置（修改密码表单）
└── components/
    ├── UserFormDialog.vue    # 用户新增/编辑弹窗
    ├── UserRoleDialog.vue    # 用户角色分配弹窗
    ├── RoleFormDialog.vue    # 角色新增/编辑弹窗
    └── RolePermissionDialog.vue  # 角色权限分配弹窗
```

### 5.2 API 层

```typescript
// src/api/system.ts

// 用户管理
fetchUsers(query: UserQuery): Promise<PageResult<SysUserVO>>
fetchUserDetail(id: number): Promise<SysUserVO>
createUser(data: CreateUserRequest): Promise<void>
updateUser(id: number, data: UpdateUserRequest): Promise<void>
deleteUser(id: number): Promise<void>
updateUserStatus(id: number, status: number): Promise<void>
assignUserRoles(id: number, roleIds: number[]): Promise<void>
resetUserPassword(id: number, newPassword: string): Promise<void>

// 角色管理
fetchRoles(): Promise<SysRoleVO[]>
fetchRoleDetail(id: number): Promise<SysRoleDetailVO>
createRole(data: CreateRoleRequest): Promise<void>
updateRole(id: number, data: UpdateRoleRequest): Promise<void>
deleteRole(id: number): Promise<void>
assignRolePermissions(id: number, permissionIds: number[]): Promise<void>

// 权限管理
fetchPermissionTree(): Promise<SysPermissionTreeVO[]>

// 个人设置
fetchProfile(): Promise<SysUserVO>
changePassword(data: ChangePasswordRequest): Promise<void>
```

### 5.3 类型定义

在 `domain.ts` 中新增：

```typescript
export interface SysUserVO {
  id: number;
  username: string;
  realName: string;
  email: string;
  phone: string;
  status: number;
  roles: SysRoleVO[];
  createdAt: string;
}

export interface SysRoleVO {
  id: number;
  roleCode: string;
  roleName: string;
}

export interface SysRoleDetailVO extends SysRoleVO {
  permissions: SysPermissionVO[];
}

export interface SysPermissionVO {
  id: number;
  permissionCode: string;
  permissionName: string;
  parentId: number;
  sort: number;
  children?: SysPermissionVO[];
}

export interface UserQuery extends PageQuery {
  username?: string;
  status?: number;
}
```

---

## 6. 数据库变更

### 新增权限码

在 `sys_permission` 表中新增系统管理相关权限：

```sql
INSERT INTO sys_permission (permission_code, permission_name, parent_id, sort) VALUES
('system:user:query',   '用户管理-查询', 0, 37),
('system:user:create',  '用户管理-新增', 0, 38),
('system:user:update',  '用户管理-修改', 0, 39),
('system:user:delete',  '用户管理-删除', 0, 40),
('system:role:query',   '角色管理-查询', 0, 41),
('system:role:create',  '角色管理-新增', 0, 42),
('system:role:update',  '角色管理-修改', 0, 43),
('system:role:delete',  '角色管理-删除', 0, 44),
('system:permission:query', '权限管理-查询', 0, 45),
('system:log:query',    '操作日志-查询', 0, 46);
```

admin 角色自动拥有全部新增权限。

### 无表结构变更

所有需要的表（`sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`operation_log`）均已存在，无需新建或修改表结构。

---

## 7. 错误处理

| 场景 | 错误码 | 提示信息 |
|------|--------|----------|
| 用户名已存在 | 40900 | 用户名已存在 |
| 角色编码已存在 | 40900 | 角色编码已存在 |
| 删除最后一个管理员 | 40001 | 至少保留一个管理员账号 |
| 删除有用户关联的角色 | 40001 | 该角色下存在用户，无法删除 |
| 旧密码错误 | 40001 | 旧密码不正确 |
| 用户不存在 | 40400 | 用户不存在 |

---

## 8. 不做的事

- 不做菜单管理（菜单硬编码在前端，权限码控制可见性）
- 不做系统配置/参数设置（当前无此需求）
- 不做在线用户监控（无状态 JWT 架构不适用）
- 不做数据备份/恢复（超出教学项目范围）
- 权限管理仅只读展示，不支持管理员自建权限码（权限码由开发维护）
