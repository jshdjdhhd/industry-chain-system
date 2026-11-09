# 系统管理模块实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将系统管理模块从空壳升级为完整可用的后台管理功能，包含用户管理、角色管理、权限管理、操作日志和个人设置。

**架构：** 后端复用已有的 `auth` 包实体和 Mapper，在新 `system` 包下构建 Service/Controller 层暴露管理接口；前端新建 `views/system/` 目录和 `api/system.ts` 接口层，通过 Element Plus 表格/表单/树组件实现管理界面。

**技术栈：** Spring Boot + MyBatis-Plus + Vue 3 + TypeScript + Element Plus + Pinia

---

## 文件结构

### 后端新建文件

| 文件 | 职责 |
|------|------|
| `groupproject-back/back/src/main/resources/db/migration/V11__system_permissions.sql` | 系统管理权限码种子数据 |
| `git.ggwzz.system.dto.CreateUserRequest` | 新增用户请求体 |
| `git.ggwzz.system.dto.UpdateUserRequest` | 修改用户请求体 |
| `git.ggwzz.system.dto.UserQuery` | 用户列表查询条件 |
| `git.ggwzz.system.dto.CreateRoleRequest` | 新增角色请求体 |
| `git.ggwzz.system.dto.UpdateRoleRequest` | 修改角色请求体 |
| `git.ggwzz.system.dto.AssignRolesRequest` | 给用户分配角色请求体 |
| `git.ggwzz.system.dto.AssignPermissionsRequest` | 给角色分配权限请求体 |
| `git.ggwzz.system.dto.ChangePasswordRequest` | 修改密码请求体 |
| `git.ggwzz.system.dto.ResetPasswordRequest` | 管理员重置密码请求体 |
| `git.ggwzz.system.vo.SysUserVO` | 用户列表/详情 VO |
| `git.ggwzz.system.vo.SysRoleVO` | 角色列表 VO |
| `git.ggwzz.system.vo.SysRoleDetailVO` | 角色详情（含权限列表）VO |
| `git.ggwzz.system.vo.SysPermissionTreeVO` | 权限树 VO |
| `git.ggwzz.system.service.SysUserService` | 用户管理服务接口 |
| `git.ggwzz.system.service.SysRoleService` | 角色管理服务接口 |
| `git.ggwzz.system.service.impl.SysUserServiceImpl` | 用户管理服务实现 |
| `git.ggwzz.system.service.impl.SysRoleServiceImpl` | 角色管理服务实现 |
| `git.ggwzz.system.controller.SysUserController` | 用户管理 REST 接口 |
| `git.ggwzz.system.controller.SysRoleController` | 角色管理 REST 接口 |
| `git.ggwzz.system.controller.SysPermissionController` | 权限树查询 REST 接口 |
| `git.ggwzz.system.controller.UserProfileController` | 个人设置 REST 接口 |

### 后端修改文件

| 文件 | 变更 |
|------|------|
| `SecurityConfig.java` | 放行 `/api/system/permissions/tree`（可选，所有已认证用户可查权限树） |
| `OperationLogQueryController.java` | 增加按操作人/动作/时间范围筛选参数 |

### 前端新建文件

| 文件 | 职责 |
|------|------|
| `src/api/system.ts` | 系统管理 API 接口层 |
| `src/views/system/UserList.vue` | 用户管理页面 |
| `src/views/system/RoleList.vue` | 角色管理页面 |
| `src/views/system/PermissionTree.vue` | 权限树展示页面 |
| `src/views/system/SystemOperationLog.vue` | 操作日志页面 |
| `src/views/system/UserProfile.vue` | 个人设置页面 |

### 前端修改文件

| 文件 | 变更 |
|------|------|
| `src/types/domain.ts` | 新增系统管理相关类型定义 |
| `src/api/mock.ts` | 新增系统管理 mock 数据 |
| `src/layouts/AdminLayout.vue` | 调整系统管理菜单（删除运行概览，新增5个子菜单） |
| `src/router/index.ts` | 新增系统管理路由，旧 `/operation-logs` 重定向 |

---

## 任务 1：SQL 迁移 — 系统管理权限码

**文件：**
- 创建：`groupproject-back/back/src/main/resources/db/migration/V11__system_permissions.sql`

- [ ] **步骤 1：创建权限码迁移文件**

```sql
-- V11__system_permissions.sql
-- 系统管理模块权限码 + admin 角色自动授权

INSERT INTO `sys_permission` (`permission_code`, `permission_name`, `parent_id`, `sort`)
SELECT seed.`code`, seed.`name`, 0, seed.`sort`
FROM (
    SELECT 'system:user:query'        AS `code`, '用户管理-查询'   AS `name`, 37 AS `sort`
    UNION ALL SELECT 'system:user:create',       '用户管理-新增',   38
    UNION ALL SELECT 'system:user:update',       '用户管理-修改',   39
    UNION ALL SELECT 'system:user:delete',       '用户管理-删除',   40
    UNION ALL SELECT 'system:user:reset-pwd',    '用户管理-重置密码', 41
    UNION ALL SELECT 'system:role:query',        '角色管理-查询',   42
    UNION ALL SELECT 'system:role:create',       '角色管理-新增',   43
    UNION ALL SELECT 'system:role:update',       '角色管理-修改',   44
    UNION ALL SELECT 'system:role:delete',       '角色管理-删除',   45
    UNION ALL SELECT 'system:permission:query',  '权限管理-查询',   46
    UNION ALL SELECT 'system:log:query',         '操作日志-查询',   47
) seed
LEFT JOIN `sys_permission` existing ON existing.`permission_code` = seed.`code`
WHERE existing.`id` IS NULL;

-- admin 角色自动拥有全部新增权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id`
FROM `sys_permission` p
LEFT JOIN `sys_role_permission` rp ON rp.`role_id` = 1 AND rp.`permission_id` = p.`id`
WHERE p.`permission_code` LIKE 'system:%'
  AND rp.`id` IS NULL;
```

- [ ] **步骤 2：Commit**

```bash
git add groupproject-back/back/src/main/resources/db/migration/V11__system_permissions.sql
git commit -m "feat(system): add system management permission codes migration"
```

---

## 任务 2：后端 DTO 层

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/CreateUserRequest.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/UpdateUserRequest.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/UserQuery.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/CreateRoleRequest.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/UpdateRoleRequest.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/AssignRolesRequest.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/AssignPermissionsRequest.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/ChangePasswordRequest.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/dto/ResetPasswordRequest.java`

- [ ] **步骤 1：创建 CreateUserRequest**

```java
package git.ggwzz.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度 3-64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度 6-128")
    private String password;

    private String realName;
    private String email;
    private String phone;

    /** 分配的角色 ID 列表 */
    private List<Long> roleIds;
}
```

- [ ] **步骤 2：创建 UpdateUserRequest**

```java
package git.ggwzz.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @Size(min = 1, max = 64, message = "真实姓名长度 1-64")
    private String realName;
    private String email;
    private String phone;
}
```

- [ ] **步骤 3：创建 UserQuery**

```java
package git.ggwzz.system.dto;

import git.ggwzz.common.util.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {
    private String username;
    private Integer status;
}
```

- [ ] **步骤 4：创建 CreateRoleRequest**

```java
package git.ggwzz.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoleRequest {
    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 64, message = "角色编码长度 2-64")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(min = 2, max = 64, message = "角色名称长度 2-64")
    private String roleName;

    /** 初始权限 ID 列表 */
    private List<Long> permissionIds;
}
```

- [ ] **步骤 5：创建 UpdateRoleRequest**

```java
package git.ggwzz.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateRoleRequest {
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
}
```

- [ ] **步骤 6：创建 AssignRolesRequest**

```java
package git.ggwzz.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class AssignRolesRequest {
    private List<Long> roleIds;
}
```

- [ ] **步骤 7：创建 AssignPermissionsRequest**

```java
package git.ggwzz.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class AssignPermissionsRequest {
    private List<Long> permissionIds;
}
```

- [ ] **步骤 8：创建 ChangePasswordRequest**

```java
package git.ggwzz.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "新密码长度 6-128")
    private String newPassword;
}
```

- [ ] **步骤 9：创建 ResetPasswordRequest**

```java
package git.ggwzz.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "新密码长度 6-128")
    private String newPassword;
}
```

- [ ] **步骤 10：Commit**

```bash
git add groupproject-back/back/src/main/java/git/ggwzz/system/dto/
git commit -m "feat(system): add DTO layer for system management"
```

---

## 任务 3：后端 VO 层

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/vo/SysUserVO.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/vo/SysRoleVO.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/vo/SysRoleDetailVO.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/vo/SysPermissionTreeVO.java`

- [ ] **步骤 1：创建 SysUserVO**

```java
package git.ggwzz.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SysUserVO {
    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private Integer status;
    private List<SysRoleVO> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 2：创建 SysRoleVO**

```java
package git.ggwzz.system.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SysRoleVO {
    private Long id;
    private String roleCode;
    private String roleName;
}
```

- [ ] **步骤 3：创建 SysRoleDetailVO**

```java
package git.ggwzz.system.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SysRoleDetailVO {
    private Long id;
    private String roleCode;
    private String roleName;
    private List<SysPermissionTreeVO> permissions;
}
```

- [ ] **步骤 4：创建 SysPermissionTreeVO**

```java
package git.ggwzz.system.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SysPermissionTreeVO {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private Long parentId;
    private Integer sort;
    private List<SysPermissionTreeVO> children;
}
```

- [ ] **步骤 5：Commit**

```bash
git add groupproject-back/back/src/main/java/git/ggwzz/system/vo/
git commit -m "feat(system): add VO layer for system management"
```

---

## 任务 4：后端 SysRoleService

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/service/SysRoleService.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/service/impl/SysRoleServiceImpl.java`

- [ ] **步骤 1：创建 SysRoleService 接口**

```java
package git.ggwzz.system.service;

import git.ggwzz.system.dto.AssignPermissionsRequest;
import git.ggwzz.system.dto.CreateRoleRequest;
import git.ggwzz.system.dto.UpdateRoleRequest;
import git.ggwzz.system.vo.SysPermissionTreeVO;
import git.ggwzz.system.vo.SysRoleDetailVO;
import git.ggwzz.system.vo.SysRoleVO;

import java.util.List;

public interface SysRoleService {

    List<SysRoleVO> listAll();

    SysRoleDetailVO getDetail(Long id);

    void create(CreateRoleRequest request);

    void update(Long id, UpdateRoleRequest request);

    void delete(Long id);

    void assignPermissions(Long id, AssignPermissionsRequest request);

    List<SysPermissionTreeVO> getPermissionTree();
}
```

- [ ] **步骤 2：创建 SysRoleServiceImpl**

```java
package git.ggwzz.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.auth.entity.SysPermission;
import git.ggwzz.auth.entity.SysRole;
import git.ggwzz.auth.entity.SysRolePermission;
import git.ggwzz.auth.entity.SysUserRole;
import git.ggwzz.auth.mapper.SysPermissionMapper;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysRolePermissionMapper;
import git.ggwzz.auth.mapper.SysUserRoleMapper;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.system.dto.AssignPermissionsRequest;
import git.ggwzz.system.dto.CreateRoleRequest;
import git.ggwzz.system.dto.UpdateRoleRequest;
import git.ggwzz.system.service.SysRoleService;
import git.ggwzz.system.vo.SysPermissionTreeVO;
import git.ggwzz.system.vo.SysRoleDetailVO;
import git.ggwzz.system.vo.SysRoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public List<SysRoleVO> listAll() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getDeleted, 0));
        return roles.stream().map(r -> SysRoleVO.builder()
                .id(r.getId())
                .roleCode(r.getRoleCode())
                .roleName(r.getRoleName())
                .build()).toList();
    }

    @Override
    public SysRoleDetailVO getDetail(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        List<SysPermission> permissions = permissionMapper.selectByRoleId(id);
        return SysRoleDetailVO.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .permissions(buildTree(permissions))
                .build();
    }

    @Override
    @Transactional
    public void create(CreateRoleRequest request) {
        Long exists = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, request.getRoleCode())
                        .eq(SysRole::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDeleted(0);
        roleMapper.insert(role);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            for (Long permId : request.getPermissionIds()) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(role.getId());
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
    }

    @Override
    public void update(Long id, UpdateRoleRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        role.setRoleName(request.getRoleName());
        roleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        // 校验是否有用户关联
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (userCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该角色下存在用户，无法删除");
        }
        role.setDeleted(1);
        roleMapper.updateById(role);
        // 清理角色-权限关联
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
    }

    @Override
    @Transactional
    public void assignPermissions(Long id, AssignPermissionsRequest request) {
        SysRole role = roleMapper.selectById(id);
        if (role == null || role.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
        }
        // 先删旧关联
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        // 写入新关联
        if (request.getPermissionIds() != null) {
            for (Long permId : request.getPermissionIds()) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(id);
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
    }

    @Override
    public List<SysPermissionTreeVO> getPermissionTree() {
        List<SysPermission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getDeleted, 0)
                        .orderByAsc(SysPermission::getSort));
        return buildTree(all);
    }

    private List<SysPermissionTreeVO> buildTree(List<SysPermission> permissions) {
        Map<Long, List<SysPermission>> grouped = permissions.stream()
                .filter(p -> p.getParentId() != null && p.getParentId() != 0)
                .collect(Collectors.groupingBy(SysPermission::getParentId));
        return permissions.stream()
                .filter(p -> p.getParentId() == null || p.getParentId() == 0)
                .map(root -> toTreeVO(root, grouped))
                .toList();
    }

    private SysPermissionTreeVO toTreeVO(SysPermission p, Map<Long, List<SysPermission>> grouped) {
        List<SysPermissionTreeVO> children = grouped.getOrDefault(p.getId(), new ArrayList<>())
                .stream().map(c -> toTreeVO(c, grouped)).toList();
        return SysPermissionTreeVO.builder()
                .id(p.getId())
                .permissionCode(p.getPermissionCode())
                .permissionName(p.getPermissionName())
                .parentId(p.getParentId())
                .sort(p.getSort())
                .children(children.isEmpty() ? null : children)
                .build();
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add groupproject-back/back/src/main/java/git/ggwzz/system/service/
git commit -m "feat(system): add SysRoleService with CRUD and permission assignment"
```

---

## 任务 5：后端 SysUserService

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/service/SysUserService.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/service/impl/SysUserServiceImpl.java`

- [ ] **步骤 1：创建 SysUserService 接口**

```java
package git.ggwzz.system.service;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.system.dto.*;
import git.ggwzz.system.vo.SysUserVO;

public interface SysUserService {

    PageResult<SysUserVO> page(UserQuery query);

    SysUserVO getDetail(Long id);

    void create(CreateUserRequest request);

    void update(Long id, UpdateUserRequest request);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    void assignRoles(Long id, AssignRolesRequest request);

    void resetPassword(Long id, ResetPasswordRequest request);

    void changePassword(Long id, ChangePasswordRequest request);
}
```

- [ ] **步骤 2：创建 SysUserServiceImpl**

```java
package git.ggwzz.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import git.ggwzz.auth.entity.SysRole;
import git.ggwzz.auth.entity.SysUser;
import git.ggwzz.auth.entity.SysUserRole;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysUserMapper;
import git.ggwzz.auth.mapper.SysUserRoleMapper;
import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.ResultCode;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.security.util.SecurityUtils;
import git.ggwzz.system.dto.*;
import git.ggwzz.system.service.SysUserService;
import git.ggwzz.system.vo.SysRoleVO;
import git.ggwzz.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<SysUserVO> page(UserQuery query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0)
                .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(page, wrapper);
        List<SysUserVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public SysUserVO getDetail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    @Transactional
    public void create(CreateUserRequest request) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, request.getUsername())
                        .eq(SysUser::getDeleted, 0));
        if (exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1); // 默认正常
        user.setDeleted(0);
        userMapper.insert(user);

        if (request.getRoleIds() != null) {
            for (Long roleId : request.getRoleIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    public void update(Long id, UpdateUserRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (StringUtils.hasText(request.getRealName())) user.setRealName(request.getRealName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 校验是否是最后一个管理员
        if (isLastAdmin(id)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "至少保留一个管理员账号");
        }
        user.setDeleted(1);
        userMapper.updateById(user);
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (status == 2 && isLastAdmin(id)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "不能停用最后一个管理员");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(Long id, AssignRolesRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        if (request.getRoleIds() != null) {
            for (Long roleId : request.getRoleIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(id);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    public void resetPassword(Long id, ResetPasswordRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(Long id, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null || user.getDeleted() == 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    private boolean isLastAdmin(Long userId) {
        // 查找 admin 角色
        SysRole adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, "admin")
                        .eq(SysRole::getDeleted, 0));
        if (adminRole == null) return false;
        // 查找拥有 admin 角色的用户数
        Long adminCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, adminRole.getId()));
        // 如果当前用户是管理员且只剩 1 个管理员
        boolean isCurrentUserAdmin = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, adminRole.getId())) > 0;
        return isCurrentUserAdmin && adminCount <= 1;
    }

    private SysUserVO toVO(SysUser user) {
        List<SysRole> roles = roleMapper.selectByUserId(user.getId());
        List<SysRoleVO> roleVOs = roles.stream().map(r -> SysRoleVO.builder()
                .id(r.getId())
                .roleCode(r.getRoleCode())
                .roleName(r.getRoleName())
                .build()).toList();
        return SysUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .roles(roleVOs)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add groupproject-back/back/src/main/java/git/ggwzz/system/service/
git commit -m "feat(system): add SysUserService with CRUD, role assignment, password management"
```

---

## 任务 6：后端 Controller 层

**文件：**
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/controller/SysUserController.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/controller/SysRoleController.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/controller/SysPermissionController.java`
- 创建：`groupproject-back/back/src/main/java/git/ggwzz/system/controller/UserProfileController.java`

- [ ] **步骤 1：创建 SysUserController**

```java
package git.ggwzz.system.controller;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.security.util.SecurityUtils;
import git.ggwzz.system.dto.*;
import git.ggwzz.system.service.SysUserService;
import git.ggwzz.system.vo.SysUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping
    public Result<PageResult<SysUserVO>> page(@Valid UserQuery query) {
        return Result.success(sysUserService.page(query));
    }

    @GetMapping("/{id}")
    public Result<SysUserVO> detail(@PathVariable Long id) {
        return Result.success(sysUserService.getDetail(id));
    }

    @OperationLog(action = "create_user", targetType = "user", targetId = "#result.data.id")
    @PostMapping
    public Result<SysUserVO> create(@Valid @RequestBody CreateUserRequest request) {
        sysUserService.create(request);
        return Result.success();
    }

    @OperationLog(action = "update_user", targetType = "user", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        sysUserService.update(id, request);
        return Result.success();
    }

    @OperationLog(action = "delete_user", targetType = "user", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return Result.success();
    }

    @OperationLog(action = "update_user_status", targetType = "user", targetId = "#id")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysUserService.updateStatus(id, status);
        return Result.success();
    }

    @OperationLog(action = "assign_user_roles", targetType = "user", targetId = "#id")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest request) {
        sysUserService.assignRoles(id, request);
        return Result.success();
    }

    @OperationLog(action = "reset_password", targetType = "user", targetId = "#id")
    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        sysUserService.resetPassword(id, request);
        return Result.success();
    }
}
```

- [ ] **步骤 2：创建 SysRoleController**

```java
package git.ggwzz.system.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.system.dto.AssignPermissionsRequest;
import git.ggwzz.system.dto.CreateRoleRequest;
import git.ggwzz.system.dto.UpdateRoleRequest;
import git.ggwzz.system.service.SysRoleService;
import git.ggwzz.system.vo.SysRoleDetailVO;
import git.ggwzz.system.vo.SysRoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping
    public Result<List<SysRoleVO>> list() {
        return Result.success(sysRoleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SysRoleDetailVO> detail(@PathVariable Long id) {
        return Result.success(sysRoleService.getDetail(id));
    }

    @OperationLog(action = "create_role", targetType = "role", targetId = "#result.data.id")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateRoleRequest request) {
        sysRoleService.create(request);
        return Result.success();
    }

    @OperationLog(action = "update_role", targetType = "role", targetId = "#id")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        sysRoleService.update(id, request);
        return Result.success();
    }

    @OperationLog(action = "delete_role", targetType = "role", targetId = "#id")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return Result.success();
    }

    @OperationLog(action = "assign_role_permissions", targetType = "role", targetId = "#id")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                           @Valid @RequestBody AssignPermissionsRequest request) {
        sysRoleService.assignPermissions(id, request);
        return Result.success();
    }
}
```

- [ ] **步骤 3：创建 SysPermissionController**

```java
package git.ggwzz.system.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.system.service.SysRoleService;
import git.ggwzz.system.vo.SysPermissionTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysRoleService sysRoleService;

    @GetMapping("/tree")
    public Result<List<SysPermissionTreeVO>> tree() {
        return Result.success(sysRoleService.getPermissionTree());
    }
}
```

- [ ] **步骤 4：创建 UserProfileController**

```java
package git.ggwzz.system.controller;

import git.ggwzz.common.api.Result;
import git.ggwzz.common.log.OperationLog;
import git.ggwzz.security.util.SecurityUtils;
import git.ggwzz.system.dto.ChangePasswordRequest;
import git.ggwzz.system.service.SysUserService;
import git.ggwzz.system.vo.SysUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final SysUserService sysUserService;

    @GetMapping
    public Result<SysUserVO> profile() {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(sysUserService.getDetail(userId));
    }

    @OperationLog(action = "change_password", targetType = "user")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.currentUserId();
        sysUserService.changePassword(userId, request);
        return Result.success();
    }
}
```

- [ ] **步骤 5：Commit**

```bash
git add groupproject-back/back/src/main/java/git/ggwzz/system/controller/
git commit -m "feat(system): add REST controllers for user/role/permission/profile management"
```

---

## 任务 7：后端 — 扩展操作日志查询 + Mapper 方法

**文件：**
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/auth/mapper/SysPermissionMapper.java`（已有 `selectByUserId`，新增 `selectByRoleId`）
- 修改：`groupproject-back/back/src/main/resources/mapper/SysPermissionMapper.xml`（或新建，添加 SQL）
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/operationlog/controller/OperationLogQueryController.java`（增加筛选参数）
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/operationlog/service/OperationLogService.java`（增加筛选方法签名）
- 修改：`groupproject-back/back/src/main/java/git/ggwzz/operationlog/service/impl/OperationLogServiceImpl.java`（实现筛选）

- [ ] **步骤 1：给 SysPermissionMapper 添加 selectByRoleId 方法**

在 `SysPermissionMapper.java` 中新增方法：

```java
/**
 * 按角色 ID 查询其拥有的权限。
 *
 * @param roleId 角色 ID
 * @return 权限列表
 */
List<SysPermission> selectByRoleId(Long roleId);
```

- [ ] **步骤 2：创建或修改 SysPermissionMapper.xml**

检查是否存在 `src/main/resources/mapper/SysPermissionMapper.xml`。若不存在则创建，若存在则追加 `selectByRoleId` SQL：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="git.ggwzz.auth.mapper.SysPermissionMapper">

    <select id="selectByUserId" resultType="git.ggwzz.auth.entity.SysPermission">
        SELECT DISTINCT p.*
        FROM sys_permission p
        INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
        INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
        WHERE ur.user_id = #{userId} AND p.deleted = 0
    </select>

    <select id="selectByRoleId" resultType="git.ggwzz.auth.entity.SysPermission">
        SELECT p.*
        FROM sys_permission p
        INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
        WHERE rp.role_id = #{roleId} AND p.deleted = 0
        ORDER BY p.sort
    </select>

</mapper>
```

- [ ] **步骤 3：扩展操作日志查询接口**

修改 `OperationLogQueryController.java`，增加筛选参数：

```java
package git.ggwzz.operationlog.controller;

import git.ggwzz.common.api.PageResult;
import git.ggwzz.common.api.Result;
import git.ggwzz.operationlog.entity.SysOperationLog;
import git.ggwzz.operationlog.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogQueryController {

    private final OperationLogService operationLogService;

    @GetMapping
    public Result<PageResult<SysOperationLog>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime startTime,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) LocalDateTime endTime) {
        return Result.success(operationLogService.page(
                pageNum, pageSize, operator, action, targetType, startTime, endTime));
    }
}
```

- [ ] **步骤 4：更新 OperationLogService 接口**

修改 `OperationLogService.java`，增加带筛选的方法签名（保留原方法兼容）：

```java
/**
 * 分页查询操作日志（支持筛选）。
 */
PageResult<SysOperationLog> page(int pageNum, int pageSize,
                                  String operator, String action, String targetType,
                                  LocalDateTime startTime, LocalDateTime endTime);
```

- [ ] **步骤 5：更新 OperationLogServiceImpl 实现**

在 `OperationLogServiceImpl.java` 中实现新的 page 方法：

```java
@Override
public PageResult<SysOperationLog> page(int pageNum, int pageSize,
                                          String operator, String action, String targetType,
                                          LocalDateTime startTime, LocalDateTime endTime) {
    Page<SysOperationLog> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
            .like(StringUtils.hasText(operator), SysOperationLog::getOperator, operator)
            .eq(StringUtils.hasText(action), SysOperationLog::getAction, action)
            .eq(StringUtils.hasText(targetType), SysOperationLog::getTargetType, targetType)
            .ge(startTime != null, SysOperationLog::getCreatedAt, startTime)
            .le(endTime != null, SysOperationLog::getCreatedAt, endTime)
            .orderByDesc(SysOperationLog::getCreatedAt);
    Page<SysOperationLog> result = operationLogMapper.selectPage(page, wrapper);
    return new PageResult<>(result.getRecords(), result.getTotal(), pageNum, pageSize);
}
```

- [ ] **步骤 6：Commit**

```bash
git add groupproject-back/back/src/main/java/git/ggwzz/auth/mapper/SysPermissionMapper.java
git add groupproject-back/back/src/main/resources/mapper/
git add groupproject-back/back/src/main/java/git/ggwzz/operationlog/
git commit -m "feat(system): add selectByRoleId to permission mapper, extend operation log query filters"
```

---

## 任务 8：前端 — 类型定义 + API 层

**文件：**
- 修改：`groupproject/front/src/types/domain.ts`（追加系统管理类型）
- 创建：`groupproject/front/src/api/system.ts`
- 修改：`groupproject/front/src/api/operationLog.ts`（增加筛选参数）

- [ ] **步骤 1：在 domain.ts 末尾追加系统管理类型**

```typescript
// ===== 系统管理模块 =====

export interface SysUserVO {
  id: number;
  username: string;
  realName: string;
  email: string;
  phone: string;
  status: number;
  roles: SysRoleVO[];
  createdAt: string;
  updatedAt: string;
}

export interface SysRoleVO {
  id: number;
  roleCode: string;
  roleName: string;
}

export interface SysRoleDetailVO extends SysRoleVO {
  permissions: SysPermissionTreeVO[];
}

export interface SysPermissionTreeVO {
  id: number;
  permissionCode: string;
  permissionName: string;
  parentId: number;
  sort: number;
  children?: SysPermissionTreeVO[];
}

export interface CreateUserRequest {
  username: string;
  password: string;
  realName?: string;
  email?: string;
  phone?: string;
  roleIds?: number[];
}

export interface UpdateUserRequest {
  realName?: string;
  email?: string;
  phone?: string;
}

export interface CreateRoleRequest {
  roleCode: string;
  roleName: string;
  permissionIds?: number[];
}

export interface UpdateRoleRequest {
  roleName: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}
```

- [ ] **步骤 2：创建 system.ts API 层**

```typescript
import { get, post, put, del } from "./http";
import type {
  PageResult,
  SysUserVO,
  SysRoleVO,
  SysRoleDetailVO,
  SysPermissionTreeVO,
  CreateUserRequest,
  UpdateUserRequest,
  CreateRoleRequest,
  UpdateRoleRequest,
  ChangePasswordRequest,
} from "@/types/domain";

// ===== 用户管理 =====

export function fetchUsers(params: {
  pageNum: number;
  pageSize: number;
  username?: string;
  status?: number;
}): Promise<PageResult<SysUserVO>> {
  return get("/system/users", { params });
}

export function fetchUserDetail(id: number): Promise<SysUserVO> {
  return get(`/system/users/${id}`);
}

export function createUser(data: CreateUserRequest): Promise<void> {
  return post("/system/users", data);
}

export function updateUser(id: number, data: UpdateUserRequest): Promise<void> {
  return put(`/system/users/${id}`, data);
}

export function deleteUser(id: number): Promise<void> {
  return del(`/system/users/${id}`);
}

export function updateUserStatus(id: number, status: number): Promise<void> {
  return put(`/system/users/${id}/status`, null, { params: { status } });
}

export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return put(`/system/users/${id}/roles`, { roleIds });
}

export function resetUserPassword(id: number, newPassword: string): Promise<void> {
  return put(`/system/users/${id}/password/reset`, { newPassword });
}

// ===== 角色管理 =====

export function fetchRoles(): Promise<SysRoleVO[]> {
  return get("/system/roles");
}

export function fetchRoleDetail(id: number): Promise<SysRoleDetailVO> {
  return get(`/system/roles/${id}`);
}

export function createRole(data: CreateRoleRequest): Promise<void> {
  return post("/system/roles", data);
}

export function updateRole(id: number, data: UpdateRoleRequest): Promise<void> {
  return put(`/system/roles/${id}`, data);
}

export function deleteRole(id: number): Promise<void> {
  return del(`/system/roles/${id}`);
}

export function assignRolePermissions(id: number, permissionIds: number[]): Promise<void> {
  return put(`/system/roles/${id}/permissions`, { permissionIds });
}

// ===== 权限管理 =====

export function fetchPermissionTree(): Promise<SysPermissionTreeVO[]> {
  return get("/system/permissions/tree");
}

// ===== 个人设置 =====

export function fetchProfile(): Promise<SysUserVO> {
  return get("/system/profile");
}

export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return put("/system/profile/password", data);
}
```

- [ ] **步骤 3：更新 operationLog.ts 增加筛选参数**

```typescript
import { get } from "./http";
import type { PageResult, OperationLog } from "@/types/domain";

export function fetchOperationLogs(params: {
  pageNum: number;
  pageSize: number;
  operator?: string;
  action?: string;
  targetType?: string;
  startTime?: string;
  endTime?: string;
}): Promise<PageResult<OperationLog>> {
  return get("/operation-logs", { params });
}
```

- [ ] **步骤 4：Commit**

```bash
git add groupproject/front/src/types/domain.ts
git add groupproject/front/src/api/system.ts
git add groupproject/front/src/api/operationLog.ts
git commit -m "feat(system): add frontend types and API layer for system management"
```

---

## 任务 9：前端 — Mock 数据

**文件：**
- 修改：`groupproject/front/src/api/mock.ts`（追加系统管理 mock）
- 修改：`groupproject/front/src/api/mode.ts`（如需注册新 mock 函数）

- [ ] **步骤 1：在 mock.ts 中追加系统管理 mock 函数**

在 `mock.ts` 的 mockApi 对象中追加：

```typescript
// 用户管理 mock
fetchUsers: (params: any) => {
  const users = [
    { id: 1, username: "admin", realName: "系统管理员", email: "admin@example.com", phone: "13800000001", status: 1, roles: [{ id: 1, roleCode: "admin", roleName: "超级管理员" }], createdAt: "2026-01-01T00:00:00", updatedAt: "2026-01-01T00:00:00" },
    { id: 2, username: "manager", realName: "产业管理人员", email: "manager@example.com", phone: "13800000002", status: 1, roles: [{ id: 2, roleCode: "manager", roleName: "产业管理人员" }], createdAt: "2026-01-15T00:00:00", updatedAt: "2026-01-15T00:00:00" },
    { id: 3, username: "viewer", realName: "普通查看人员", email: "viewer@example.com", phone: "13800000003", status: 1, roles: [{ id: 3, roleCode: "viewer", roleName: "普通查看人员" }], createdAt: "2026-02-01T00:00:00", updatedAt: "2026-02-01T00:00:00" },
  ];
  const filtered = users.filter(u =>
    (!params.username || u.username.includes(params.username)) &&
    (!params.status || u.status === params.status)
  );
  return { records: filtered, total: filtered.length, pageNum: params.pageNum, pageSize: params.pageSize };
},

// 角色管理 mock
fetchRoles: () => [
  { id: 1, roleCode: "admin", roleName: "超级管理员" },
  { id: 2, roleCode: "manager", roleName: "产业管理人员" },
  { id: 3, roleCode: "viewer", roleName: "普通查看人员" },
],

// 权限树 mock
fetchPermissionTree: () => [
  { id: 1, permissionCode: "enterprise:enterprise:query", permissionName: "企业-查询", parentId: 0, sort: 1 },
  { id: 15, permissionCode: "chain:chain:query", permissionName: "产业链-查询", parentId: 0, sort: 15 },
  { id: 28, permissionCode: "matching:task:query", permissionName: "匹配任务-查询", parentId: 0, sort: 28 },
  { id: 37, permissionCode: "system:user:query", permissionName: "用户管理-查询", parentId: 0, sort: 37 },
  { id: 42, permissionCode: "system:role:query", permissionName: "角色管理-查询", parentId: 0, sort: 42 },
  { id: 46, permissionCode: "system:permission:query", permissionName: "权限管理-查询", parentId: 0, sort: 46 },
],

// 个人设置 mock
fetchProfile: () => ({ id: 1, username: "admin", realName: "系统管理员", email: "admin@example.com", phone: "13800000001", status: 1, roles: [{ id: 1, roleCode: "admin", roleName: "超级管理员" }], createdAt: "2026-01-01T00:00:00", updatedAt: "2026-01-01T00:00:00" }),
```

注意：实际追加时需与 `mock.ts` 现有的 `mockApi` 对象结构对齐。在 `mode.ts` 中检查是否需要注册新的 mock 路由。

- [ ] **步骤 2：Commit**

```bash
git add groupproject/front/src/api/mock.ts
git add groupproject/front/src/api/mode.ts
git commit -m "feat(system): add mock data for system management APIs"
```

---

## 任务 10：前端 — 路由 + 菜单配置

**文件：**
- 修改：`groupproject/front/src/router/index.ts`
- 修改：`groupproject/front/src/layouts/AdminLayout.vue`

- [ ] **步骤 1：在 router/index.ts 中新增系统管理路由**

在 AdminLayout 的 children 数组中追加路由（在现有路由之后）：

```typescript
// 系统管理路由
{
  path: "/system/users",
  name: "system-users",
  component: () => import("@/views/system/UserList.vue"),
  meta: { title: "用户管理" },
},
{
  path: "/system/roles",
  name: "system-roles",
  component: () => import("@/views/system/RoleList.vue"),
  meta: { title: "角色管理" },
},
{
  path: "/system/permissions",
  name: "system-permissions",
  component: () => import("@/views/system/PermissionTree.vue"),
  meta: { title: "权限管理" },
},
{
  path: "/system/operation-logs",
  name: "system-operation-logs",
  component: () => import("@/views/system/SystemOperationLog.vue"),
  meta: { title: "操作日志" },
},
{
  path: "/system/profile",
  name: "system-profile",
  component: () => import("@/views/system/UserProfile.vue"),
  meta: { title: "个人设置" },
},
```

将旧的 `/operation-logs` 路由改为重定向：

```typescript
{
  path: "/operation-logs",
  redirect: "/system/operation-logs",
},
```

- [ ] **步骤 2：修改 AdminLayout.vue 侧边栏菜单**

将 `sideGroups` 中的 `system` 分组从：

```typescript
{
  index: "system",
  label: "系统管理",
  icon: Setting,
  children: [{ path: "/dashboard", label: "运行概览", icon: DataLine }],
},
```

改为：

```typescript
{
  index: "system",
  label: "系统管理",
  icon: Setting,
  children: [
    { path: "/system/users", label: "用户管理", icon: User },
    { path: "/system/roles", label: "角色管理", icon: UserFilled },
    { path: "/system/permissions", label: "权限管理", icon: Lock },
    { path: "/system/operation-logs", label: "操作日志", icon: Document },
    { path: "/system/profile", label: "个人设置", icon: UserSetting },
  ],
},
```

注意：需要在文件顶部 import 中添加缺失的图标（`UserFilled`、`Lock`、`Document`、`UserSetting`）。检查现有 import 语句，从 `@element-plus/icons-vue` 补充。

- [ ] **步骤 3：Commit**

```bash
git add groupproject/front/src/router/index.ts
git add groupproject/front/src/layouts/AdminLayout.vue
git commit -m "feat(system): add system management routes and sidebar menu"
```

---

## 任务 11：前端 — UserList.vue 用户管理页面

**文件：**
- 创建：`groupproject/front/src/views/system/UserList.vue`

- [ ] **步骤 1：创建 UserList.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as systemApi from "@/api/system";
import type { SysUserVO, SysRoleVO, CreateUserRequest, UpdateUserRequest } from "@/types/domain";

const loading = ref(false);
const tableData = ref<SysUserVO[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

// 搜索条件
const searchUsername = ref("");
const searchStatus = ref<number | undefined>();

// 弹窗状态
const dialogVisible = ref(false);
const dialogTitle = ref("新增用户");
const editingId = ref<number | null>(null);
const form = ref<CreateUserRequest & UpdateUserRequest>({
  username: "",
  password: "",
  realName: "",
  email: "",
  phone: "",
});

// 角色分配弹窗
const roleDialogVisible = ref(false);
const roleUserId = ref<number>(0);
const allRoles = ref<SysRoleVO[]>([]);
const selectedRoleIds = ref<number[]>([]);

// 重置密码弹窗
const resetPwdDialogVisible = ref(false);
const resetPwdUserId = ref<number>(0);
const resetPwdUsername = ref("");
const newResetPassword = ref("");

const statusMap: Record<number, { label: string; type: string }> = {
  1: { label: "正常", type: "success" },
  2: { label: "停用", type: "warning" },
  3: { label: "锁定", type: "danger" },
};

async function loadData() {
  loading.value = true;
  try {
    const res = await systemApi.fetchUsers({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      username: searchUsername.value || undefined,
      status: searchStatus.value,
    });
    tableData.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pageNum.value = 1;
  loadData();
}

function handleAdd() {
  dialogTitle.value = "新增用户";
  editingId.value = null;
  form.value = { username: "", password: "", realName: "", email: "", phone: "" };
  dialogVisible.value = true;
}

function handleEdit(row: SysUserVO) {
  dialogTitle.value = "编辑用户";
  editingId.value = row.id;
  form.value = { username: row.username, password: "", realName: row.realName, email: row.email, phone: row.phone };
  dialogVisible.value = true;
}

async function handleSave() {
  if (editingId.value) {
    await systemApi.updateUser(editingId.value, {
      realName: form.value.realName,
      email: form.value.email,
      phone: form.value.phone,
    });
    ElMessage.success("修改成功");
  } else {
    await systemApi.createUser(form.value);
    ElMessage.success("新增成功");
  }
  dialogVisible.value = false;
  loadData();
}

async function handleDelete(row: SysUserVO) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」？`, "确认删除");
  await systemApi.deleteUser(row.id);
  ElMessage.success("删除成功");
  loadData();
}

async function handleStatusChange(row: SysUserVO, status: number) {
  const label = statusMap[status]?.label || "未知";
  await ElMessageBox.confirm(`确定将用户「${row.username}」状态改为「${label}」？`, "确认操作");
  await systemApi.updateUserStatus(row.id, status);
  ElMessage.success("状态更新成功");
  loadData();
}

async function handleAssignRole(row: SysUserVO) {
  roleUserId.value = row.id;
  selectedRoleIds.value = row.roles.map(r => r.id);
  if (allRoles.value.length === 0) {
    allRoles.value = await systemApi.fetchRoles();
  }
  roleDialogVisible.value = true;
}

async function handleSaveRoles() {
  await systemApi.assignUserRoles(roleUserId.value, selectedRoleIds.value);
  ElMessage.success("角色分配成功");
  roleDialogVisible.value = false;
  loadData();
}

function handleResetPassword(row: SysUserVO) {
  resetPwdUserId.value = row.id;
  resetPwdUsername.value = row.username;
  newResetPassword.value = "";
  resetPwdDialogVisible.value = true;
}

async function handleConfirmResetPwd() {
  if (!newResetPassword.value || newResetPassword.value.length < 6) {
    ElMessage.warning("密码长度至少 6 位");
    return;
  }
  await systemApi.resetUserPassword(resetPwdUserId.value, newResetPassword.value);
  ElMessage.success("密码重置成功");
  resetPwdDialogVisible.value = false;
}

onMounted(loadData);
</script>

<template>
  <div class="page">
    <h2 class="page__title">用户管理</h2>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="searchUsername" placeholder="用户名" clearable style="width: 200px" @keyup.enter="handleSearch" />
      <el-select v-model="searchStatus" placeholder="状态" clearable style="width: 120px">
        <el-option label="正常" :value="1" />
        <el-option label="停用" :value="2" />
        <el-option label="锁定" :value="3" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
      <el-button type="primary" @click="handleAdd">新增用户</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="realName" label="真实姓名" width="120" />
      <el-table-column prop="email" label="邮箱" width="200" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(statusMap[row.status]?.type as any) || 'info'" size="small">
            {{ statusMap[row.status]?.label || "未知" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="150">
        <template #default="{ row }">
          <el-tag v-for="r in row.roles" :key="r.id" size="small" style="margin-right: 4px">{{ r.roleName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" @click="handleAssignRole(row)">分配角色</el-button>
          <el-button size="small" @click="handleResetPassword(row)">重置密码</el-button>
          <el-dropdown trigger="click" @command="(cmd: number) => handleStatusChange(row, cmd)">
            <el-button size="small">状态</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="1" :disabled="row.status === 1">正常</el-dropdown-item>
                <el-dropdown-item :command="2" :disabled="row.status === 2">停用</el-dropdown-item>
                <el-dropdown-item :command="3" :disabled="row.status === 3">锁定</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadData"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="密码" v-if="!editingId">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="400px">
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">{{ role.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetPwdDialogVisible" title="重置密码" width="400px">
      <p>为用户「{{ resetPwdUsername }}」设置新密码：</p>
      <el-input v-model="newResetPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
      <template #footer>
        <el-button @click="resetPwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 20px; }
.page__title { margin-bottom: 20px; font-size: 20px; font-weight: 600; }
.search-bar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add groupproject/front/src/views/system/UserList.vue
git commit -m "feat(system): add UserList page with CRUD, role assignment, password reset"
```

---

## 任务 12：前端 — RoleList.vue 角色管理页面

**文件：**
- 创建：`groupproject/front/src/views/system/RoleList.vue`

- [ ] **步骤 1：创建 RoleList.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as systemApi from "@/api/system";
import type { SysRoleVO, SysRoleDetailVO, SysPermissionTreeVO, CreateRoleRequest } from "@/types/domain";

const loading = ref(false);
const tableData = ref<SysRoleVO[]>([]);

// 新增/编辑弹窗
const dialogVisible = ref(false);
const dialogTitle = ref("新增角色");
const editingId = ref<number | null>(null);
const form = ref<CreateRoleRequest>({ roleCode: "", roleName: "", permissionIds: [] });

// 权限分配弹窗
const permDialogVisible = ref(false);
const permRoleId = ref<number>(0);
const permRoleName = ref("");
const permissionTree = ref<SysPermissionTreeVO[]>([]);
const checkedPermissionIds = ref<number[]>([]);

async function loadData() {
  loading.value = true;
  try {
    tableData.value = await systemApi.fetchRoles();
  } finally {
    loading.value = false;
  }
}

function handleAdd() {
  dialogTitle.value = "新增角色";
  editingId.value = null;
  form.value = { roleCode: "", roleName: "", permissionIds: [] };
  dialogVisible.value = true;
}

function handleEdit(row: SysRoleVO) {
  dialogTitle.value = "编辑角色";
  editingId.value = row.id;
  form.value = { roleCode: row.roleCode, roleName: row.roleName };
  dialogVisible.value = true;
}

async function handleSave() {
  if (editingId.value) {
    await systemApi.updateRole(editingId.value, { roleName: form.value.roleName });
    ElMessage.success("修改成功");
  } else {
    await systemApi.createRole(form.value);
    ElMessage.success("新增成功");
  }
  dialogVisible.value = false;
  loadData();
}

async function handleDelete(row: SysRoleVO) {
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」？`, "确认删除");
  await systemApi.deleteRole(row.id);
  ElMessage.success("删除成功");
  loadData();
}

async function handleAssignPermission(row: SysRoleVO) {
  permRoleId.value = row.id;
  permRoleName.value = row.roleName;
  if (permissionTree.value.length === 0) {
    permissionTree.value = await systemApi.fetchPermissionTree();
  }
  const detail = await systemApi.fetchRoleDetail(row.id);
  checkedPermissionIds.value = collectPermissionIds(detail.permissions);
  permDialogVisible.value = true;
}

function collectPermissionIds(nodes: SysPermissionTreeVO[]): number[] {
  const ids: number[] = [];
  for (const node of nodes) {
    ids.push(node.id);
    if (node.children) ids.push(...collectPermissionIds(node.children));
  }
  return ids;
}

function collectAllIds(nodes: SysPermissionTreeVO[]): number[] {
  return collectPermissionIds(nodes);
}

async function handleSavePermissions() {
  await systemApi.assignRolePermissions(permRoleId.value, checkedPermissionIds.value);
  ElMessage.success("权限分配成功");
  permDialogVisible.value = false;
}

onMounted(loadData);
</script>

<template>
  <div class="page">
    <h2 class="page__title">角色管理</h2>

    <div class="search-bar">
      <el-button type="primary" @click="handleAdd">新增角色</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="roleCode" label="角色编码" width="200" />
      <el-table-column prop="roleName" label="角色名称" width="200" />
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" @click="handleAssignPermission(row)">分配权限</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form label-width="80px">
        <el-form-item label="角色编码">
          <el-input v-model="form.roleCode" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="form.roleName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配弹窗 -->
    <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${permRoleName}`" width="500px">
      <el-tree
        :data="permissionTree"
        show-checkbox
        node-key="id"
        :default-checked-keys="checkedPermissionIds"
        :props="{ children: 'children', label: 'permissionName' }"
        @check="(_, { checkedKeys }) => { checkedPermissionIds = checkedKeys as number[]; }"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 20px; }
.page__title { margin-bottom: 20px; font-size: 20px; font-weight: 600; }
.search-bar { margin-bottom: 16px; }
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add groupproject/front/src/views/system/RoleList.vue
git commit -m "feat(system): add RoleList page with CRUD and permission assignment"
```

---

## 任务 13：前端 — PermissionTree.vue 权限管理页面

**文件：**
- 创建：`groupproject/front/src/views/system/PermissionTree.vue`

- [ ] **步骤 1：创建 PermissionTree.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from "vue";
import * as systemApi from "@/api/system";
import type { SysPermissionTreeVO } from "@/types/domain";

const loading = ref(false);
const treeData = ref<SysPermissionTreeVO[]>([]);

async function loadData() {
  loading.value = true;
  try {
    treeData.value = await systemApi.fetchPermissionTree();
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<template>
  <div class="page">
    <h2 class="page__title">权限管理</h2>
    <p class="page__desc">系统权限码由开发维护，此处仅做展示。权限格式：模块:资源:动作</p>

    <el-card v-loading="loading">
      <el-tree
        :data="treeData"
        node-key="id"
        default-expand-all
        :props="{ children: 'children', label: 'permissionName' }"
      >
        <template #default="{ data }">
          <div class="perm-node">
            <span class="perm-name">{{ data.permissionName }}</span>
            <el-tag size="small" type="info">{{ data.permissionCode }}</el-tag>
          </div>
        </template>
      </el-tree>
    </el-card>
  </div>
</template>

<style scoped>
.page { padding: 20px; }
.page__title { margin-bottom: 8px; font-size: 20px; font-weight: 600; }
.page__desc { color: #909399; margin-bottom: 16px; font-size: 14px; }
.perm-node { display: flex; align-items: center; gap: 12px; }
.perm-name { min-width: 120px; }
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add groupproject/front/src/views/system/PermissionTree.vue
git commit -m "feat(system): add PermissionTree read-only page"
```

---

## 任务 14：前端 — SystemOperationLog.vue 操作日志页面

**文件：**
- 创建：`groupproject/front/src/views/system/SystemOperationLog.vue`

- [ ] **步骤 1：创建 SystemOperationLog.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { fetchOperationLogs } from "@/api/operationLog";
import type { OperationLog } from "@/types/domain";

const loading = ref(false);
const tableData = ref<OperationLog[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

const filterOperator = ref("");
const filterAction = ref("");
const filterTargetType = ref("");

async function loadData() {
  loading.value = true;
  try {
    const res = await fetchOperationLogs({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      operator: filterOperator.value || undefined,
      action: filterAction.value || undefined,
      targetType: filterTargetType.value || undefined,
    });
    tableData.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pageNum.value = 1;
  loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="page">
    <h2 class="page__title">操作日志</h2>

    <div class="search-bar">
      <el-input v-model="filterOperator" placeholder="操作人" clearable style="width: 150px" @keyup.enter="handleSearch" />
      <el-input v-model="filterAction" placeholder="操作类型" clearable style="width: 150px" @keyup.enter="handleSearch" />
      <el-select v-model="filterTargetType" placeholder="目标类型" clearable style="width: 150px">
        <el-option label="企业" value="enterprise" />
        <el-option label="标签" value="tag" />
        <el-option label="产业链" value="chain" />
        <el-option label="节点" value="chain_node" />
        <el-option label="匹配" value="matching_result" />
        <el-option label="用户" value="user" />
        <el-option label="角色" value="role" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="action" label="操作类型" width="150" />
      <el-table-column prop="targetType" label="目标类型" width="120" />
      <el-table-column prop="targetId" label="目标ID" width="100" />
      <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadData"
    />
  </div>
</template>

<style scoped>
.page { padding: 20px; }
.page__title { margin-bottom: 20px; font-size: 20px; font-weight: 600; }
.search-bar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add groupproject/front/src/views/system/SystemOperationLog.vue
git commit -m "feat(system): add SystemOperationLog page with filters"
```

---

## 任务 15：前端 — UserProfile.vue 个人设置页面

**文件：**
- 创建：`groupproject/front/src/views/system/UserProfile.vue`

- [ ] **步骤 1：创建 UserProfile.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import * as systemApi from "@/api/system";
import type { SysUserVO, ChangePasswordRequest } from "@/types/domain";

const loading = ref(false);
const profile = ref<SysUserVO | null>(null);

const pwdForm = ref<ChangePasswordRequest>({ oldPassword: "", newPassword: "" });
const confirmPassword = ref("");

async function loadProfile() {
  loading.value = true;
  try {
    profile.value = await systemApi.fetchProfile();
  } finally {
    loading.value = false;
  }
}

async function handleChangePassword() {
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword) {
    ElMessage.warning("请填写旧密码和新密码");
    return;
  }
  if (pwdForm.value.newPassword.length < 6) {
    ElMessage.warning("新密码长度至少 6 位");
    return;
  }
  if (pwdForm.value.newPassword !== confirmPassword.value) {
    ElMessage.warning("两次输入的密码不一致");
    return;
  }
  await systemApi.changePassword(pwdForm.value);
  ElMessage.success("密码修改成功");
  pwdForm.value = { oldPassword: "", newPassword: "" };
  confirmPassword.value = "";
}

onMounted(loadProfile);
</script>

<template>
  <div class="page">
    <h2 class="page__title">个人设置</h2>

    <el-card v-loading="loading" style="max-width: 600px">
      <template v-if="profile">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>
          <el-descriptions-item label="真实姓名">{{ profile.realName }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ profile.email || "-" }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile.phone || "-" }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag v-for="r in profile.roles" :key="r.id" size="small" style="margin-right: 4px">{{ r.roleName }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-card>

    <el-card style="max-width: 600px; margin-top: 20px">
      <template #header>修改密码</template>
      <el-form label-width="80px">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleChangePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page { padding: 20px; }
.page__title { margin-bottom: 20px; font-size: 20px; font-weight: 600; }
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add groupproject/front/src/views/system/UserProfile.vue
git commit -m "feat(system): add UserProfile page with password change"
```

---

## 任务 16：前端 — 更新 operationLog.ts 适配新页面 + 旧页面兼容

**文件：**
- 修改：`groupproject/front/src/views/dashboard/OperationLog.vue`（旧页面重定向或跳转提示）
- 修改：`groupproject/front/src/api/operationLog.ts`（已在任务 8 修改，此处确认兼容）

- [ ] **步骤 1：检查旧 OperationLog.vue 是否需要调整**

由于路由已从 `/operation-logs` 重定向到 `/system/operation-logs`，旧的 `OperationLog.vue` 不再被直接访问。检查 `DashboardView.vue` 中的"查看全部操作"按钮是否仍跳转到 `/operation-logs`：

如果是，改为跳转到 `/system/operation-logs`：

```vue
<!-- DashboardView.vue 中找到类似代码并修改 -->
<el-button @click="$router.push('/system/operation-logs')">查看全部操作</el-button>
```

- [ ] **步骤 2：Commit**

```bash
git add groupproject/front/src/views/dashboard/DashboardView.vue
git commit -m "fix(dashboard): update operation log link to new system route"
```

---

## 任务 17：后端测试 — SysRoleService 单元测试

**文件：**
- 创建：`groupproject-back/back/src/test/java/git/ggwzz/system/service/SysRoleServiceImplTest.java`

- [ ] **步骤 1：创建 SysRoleServiceImplTest**

```java
package git.ggwzz.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import git.ggwzz.auth.entity.SysPermission;
import git.ggwzz.auth.entity.SysRole;
import git.ggwzz.auth.entity.SysRolePermission;
import git.ggwzz.auth.entity.SysUserRole;
import git.ggwzz.auth.mapper.SysPermissionMapper;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysRolePermissionMapper;
import git.ggwzz.auth.mapper.SysUserRoleMapper;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.system.dto.AssignPermissionsRequest;
import git.ggwzz.system.dto.CreateRoleRequest;
import git.ggwzz.system.dto.UpdateRoleRequest;
import git.ggwzz.system.service.impl.SysRoleServiceImpl;
import git.ggwzz.system.vo.SysRoleDetailVO;
import git.ggwzz.system.vo.SysRoleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysRoleServiceImplTest {

    @Mock SysRoleMapper roleMapper;
    @Mock SysPermissionMapper permissionMapper;
    @Mock SysRolePermissionMapper rolePermissionMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @InjectMocks SysRoleServiceImpl service;

    @Test
    void listAll_returnsActiveRoles() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode("admin");
        role.setRoleName("管理员");
        role.setDeleted(0);
        when(roleMapper.selectList(any())).thenReturn(List.of(role));

        List<SysRoleVO> result = service.listAll();
        assertEquals(1, result.size());
        assertEquals("admin", result.get(0).getRoleCode());
    }

    @Test
    void create_duplicateCode_throws() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        CreateRoleRequest req = new CreateRoleRequest();
        req.setRoleCode("admin");
        req.setRoleName("管理员");
        assertThrows(BusinessException.class, () -> service.create(req));
    }

    @Test
    void delete_hasUsers_throws() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setDeleted(0);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(userRoleMapper.selectCount(any())).thenReturn(2L);
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void delete_noUsers_success() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setDeleted(0);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(userRoleMapper.selectCount(any())).thenReturn(0L);
        service.delete(1L);
        verify(roleMapper).updateById(any());
        verify(rolePermissionMapper).delete(any());
    }

    @Test
    void getDetail_notFound_throws() {
        when(roleMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getDetail(999L));
    }
}
```

- [ ] **步骤 2：运行测试验证通过**

运行：`cd groupproject-back/back && mvn test -pl . -Dtest=SysRoleServiceImplTest -DfailIfNoTests=false`
预期：5 tests PASS

- [ ] **步骤 3：Commit**

```bash
git add groupproject-back/back/src/test/java/git/ggwzz/system/service/SysRoleServiceImplTest.java
git commit -m "test(system): add unit tests for SysRoleServiceImpl"
```

---

## 任务 18：后端测试 — SysUserService 单元测试

**文件：**
- 创建：`groupproject-back/back/src/test/java/git/ggwzz/system/service/SysUserServiceImplTest.java`

- [ ] **步骤 1：创建 SysUserServiceImplTest**

```java
package git.ggwzz.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import git.ggwzz.auth.entity.SysRole;
import git.ggwzz.auth.entity.SysUser;
import git.ggwzz.auth.entity.SysUserRole;
import git.ggwzz.auth.mapper.SysRoleMapper;
import git.ggwzz.auth.mapper.SysUserMapper;
import git.ggwzz.auth.mapper.SysUserRoleMapper;
import git.ggwzz.common.exception.BusinessException;
import git.ggwzz.system.dto.*;
import git.ggwzz.system.service.impl.SysUserServiceImpl;
import git.ggwzz.system.vo.SysUserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock SysUserMapper userMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks SysUserServiceImpl service;

    private SysUser mockUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRealName("测试用户");
        user.setEmail("test@example.com");
        user.setPhone("13800000000");
        user.setStatus(1);
        user.setDeleted(0);
        user.setPassword("{bcrypt}$2a$10$encoded");
        return user;
    }

    @Test
    void getDetail_found_returnsVO() {
        when(userMapper.selectById(1L)).thenReturn(mockUser());
        when(roleMapper.selectByUserId(1L)).thenReturn(List.of());
        SysUserVO vo = service.getDetail(1L);
        assertEquals("testuser", vo.getUsername());
    }

    @Test
    void getDetail_notFound_throws() {
        when(userMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getDetail(999L));
    }

    @Test
    void create_duplicateUsername_throws() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("admin");
        req.setPassword("123456");
        assertThrows(BusinessException.class, () -> service.create(req));
    }

    @Test
    void changePassword_wrongOldPassword_throws() {
        when(userMapper.selectById(1L)).thenReturn(mockUser());
        when(passwordEncoder.matches("wrong", "{bcrypt}$2a$10$encoded")).thenReturn(false);
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("wrong");
        req.setNewPassword("new123456");
        assertThrows(BusinessException.class, () -> service.changePassword(1L, req));
    }

    @Test
    void changePassword_correct_success() {
        when(userMapper.selectById(1L)).thenReturn(mockUser());
        when(passwordEncoder.matches("old123", "{bcrypt}$2a$10$encoded")).thenReturn(true);
        when(passwordEncoder.encode("new123456")).thenReturn("{bcrypt}$newencoded");
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("old123");
        req.setNewPassword("new123456");
        assertDoesNotThrow(() -> service.changePassword(1L, req));
        verify(userMapper).updateById(any());
    }
}
```

- [ ] **步骤 2：运行测试验证通过**

运行：`cd groupproject-back/back && mvn test -pl . -Dtest=SysUserServiceImplTest -DfailIfNoTests=false`
预期：5 tests PASS

- [ ] **步骤 3：Commit**

```bash
git add groupproject-back/back/src/test/java/git/ggwzz/system/service/SysUserServiceImplTest.java
git commit -m "test(system): add unit tests for SysUserServiceImpl"
```

---

## 任务 19：前端测试 — router 路由验证

**文件：**
- 修改：`groupproject/front/src/router/index.spec.ts`（追加系统管理路由测试）

- [ ] **步骤 1：在现有路由测试文件中追加测试用例**

```typescript
it("contains system management routes", () => {
  const paths = router.getRoutes().map((r) => r.path);
  expect(paths).toContain("/system/users");
  expect(paths).toContain("/system/roles");
  expect(paths).toContain("/system/permissions");
  expect(paths).toContain("/system/operation-logs");
  expect(paths).toContain("/system/profile");
});

it("redirects old /operation-logs to /system/operation-logs", () => {
  const route = router.getRoutes().find((r) => r.path === "/operation-logs");
  expect(route?.redirect).toBe("/system/operation-logs");
});
```

- [ ] **步骤 2：运行测试验证通过**

运行：`cd groupproject/front && npx vitest run src/router/index.spec.ts`
预期：PASS

- [ ] **步骤 3：Commit**

```bash
git add groupproject/front/src/router/index.spec.ts
git commit -m "test(router): add system management route tests"
```

---

## 任务 20：前端测试 — API mock 契约测试

**文件：**
- 修改：`groupproject/front/src/api/mock-contract.spec.ts`（追加系统管理 mock 契约测试）

- [ ] **步骤 1：追加系统管理 mock 契约测试**

```typescript
describe("system management mock contracts", () => {
  it("fetchUsers returns valid PageResult", async () => {
    const result = await mockApi.fetchUsers({ pageNum: 1, pageSize: 20 });
    expect(result).toHaveProperty("records");
    expect(result).toHaveProperty("total");
    expect(Array.isArray(result.records)).toBe(true);
  });

  it("fetchRoles returns array of roles", async () => {
    const result = await mockApi.fetchRoles();
    expect(Array.isArray(result)).toBe(true);
    result.forEach((role: any) => {
      expect(role).toHaveProperty("id");
      expect(role).toHaveProperty("roleCode");
      expect(role).toHaveProperty("roleName");
    });
  });

  it("fetchPermissionTree returns tree structure", async () => {
    const result = await mockApi.fetchPermissionTree();
    expect(Array.isArray(result)).toBe(true);
    result.forEach((node: any) => {
      expect(node).toHaveProperty("id");
      expect(node).toHaveProperty("permissionCode");
      expect(node).toHaveProperty("permissionName");
    });
  });

  it("fetchProfile returns user object", async () => {
    const result = await mockApi.fetchProfile();
    expect(result).toHaveProperty("id");
    expect(result).toHaveProperty("username");
    expect(result).toHaveProperty("roles");
  });
});
```

- [ ] **步骤 2：运行测试验证通过**

运行：`cd groupproject/front && npx vitest run src/api/mock-contract.spec.ts`
预期：PASS

- [ ] **步骤 3：Commit**

```bash
git add groupproject/front/src/api/mock-contract.spec.ts
git commit -m "test(api): add system management mock contract tests"
```

---

## 计划自检

### 1. 规格覆盖度

| 规格需求 | 对应任务 |
|----------|----------|
| 删除"运行概览"菜单 | 任务 10 |
| 用户管理 CRUD | 任务 2-6（后端）+ 任务 11（前端） |
| 角色管理 CRUD | 任务 2-4, 6（后端）+ 任务 12（前端） |
| 权限管理（只读） | 任务 4, 6（后端）+ 任务 13（前端） |
| 操作日志筛选 | 任务 7（后端）+ 任务 14（前端） |
| 个人设置 | 任务 6（后端）+ 任务 15（前端） |
| 新增权限码 | 任务 1 |
| 路由 + 菜单 | 任务 10 |
| Mock 数据 | 任务 9 |
| 测试 | 任务 17-20 |

**无遗漏。**

### 2. 占位符扫描

- 无 TODO、待定、后续实现
- 所有代码步骤包含完整代码块
- 所有测试包含实际断言

### 3. 类型一致性

- 后端 VO 字段名与前端 domain.ts 类型定义一致
- API 路径与 controller `@RequestMapping` 一致
- mock 数据结构与真实 API 响应结构一致

**自检通过。**
