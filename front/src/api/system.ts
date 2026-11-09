import { get, post, put, remove } from "./http";
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
  return remove(`/system/users/${id}`);
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
  return remove(`/system/roles/${id}`);
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
