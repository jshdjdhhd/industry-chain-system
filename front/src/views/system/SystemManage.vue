<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search, Key, UserFilled, Lock, User } from "@element-plus/icons-vue";
import * as systemApi from "@/api/system";
import type {
  SysUserVO,
  SysRoleVO,
  SysPermissionTreeVO,
  CreateRoleRequest,
  ChangePasswordRequest,
} from "@/types/domain";

// ========== Tab 切换 ==========
const activeTab = ref("users");
const tabList = [
  { key: "users", label: "用户管理", icon: User, desc: "管理系统用户、分配角色和重置密码" },
  { key: "roles", label: "角色管理", icon: UserFilled, desc: "维护角色信息、分配权限" },
  { key: "permissions", label: "权限管理", icon: Lock, desc: "查看系统权限码，由开发维护" },
  { key: "profile", label: "个人设置", icon: Key, desc: "查看个人信息、修改密码" },
];

const currentTabDesc = computed(() => tabList.find(t => t.key === activeTab.value)?.desc || "");

// ========== 用户管理 ==========
const usersLoading = ref(false);
const usersTableData = ref<SysUserVO[]>([]);
const usersTotal = ref(0);
const usersPageNum = ref(1);
const usersPageSize = ref(10);
const searchUsername = ref("");
const searchStatus = ref<number | undefined>();

const userDialogVisible = ref(false);
const userDialogTitle = ref("新增用户");
const editingUserId = ref<number | null>(null);
const userForm = ref<any>({ username: "", password: "", realName: "", email: "", phone: "" });

const roleDialogVisible = ref(false);
const roleUserId = ref<number>(0);
const allRoles = ref<SysRoleVO[]>([]);
const selectedRoleIds = ref<number[]>([]);

const resetPwdDialogVisible = ref(false);
const resetPwdUserId = ref<number>(0);
const resetPwdUsername = ref("");
const newResetPassword = ref("");

const statusMap: Record<number, { label: string; type: string }> = {
  1: { label: "正常", type: "success" },
  2: { label: "停用", type: "warning" },
  3: { label: "锁定", type: "danger" },
};

async function loadUsers() {
  usersLoading.value = true;
  try {
    const res = await systemApi.fetchUsers({
      pageNum: usersPageNum.value,
      pageSize: usersPageSize.value,
      username: searchUsername.value || undefined,
      status: searchStatus.value,
    });
    usersTableData.value = res.records;
    usersTotal.value = res.total;
  } finally {
    usersLoading.value = false;
  }
}

function handleUserSearch() {
  usersPageNum.value = 1;
  loadUsers();
}

function resetUserQuery() {
  searchUsername.value = "";
  searchStatus.value = undefined;
  usersPageNum.value = 1;
  loadUsers();
}

function handleUserAdd() {
  userDialogTitle.value = "新增用户";
  editingUserId.value = null;
  userForm.value = { username: "", password: "", realName: "", email: "", phone: "" };
  userDialogVisible.value = true;
}

function handleUserEdit(row: SysUserVO) {
  userDialogTitle.value = "编辑用户";
  editingUserId.value = row.id;
  userForm.value = { username: row.username, password: "", realName: row.realName, email: row.email, phone: row.phone };
  userDialogVisible.value = true;
}

async function handleUserSave() {
  if (editingUserId.value) {
    await systemApi.updateUser(editingUserId.value, {
      realName: userForm.value.realName,
      email: userForm.value.email,
      phone: userForm.value.phone,
    });
    ElMessage.success("修改成功");
  } else {
    await systemApi.createUser(userForm.value);
    ElMessage.success("新增成功");
  }
  userDialogVisible.value = false;
  loadUsers();
}

async function handleUserDelete(row: SysUserVO) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」？`, "确认删除", { type: "warning" });
  await systemApi.deleteUser(row.id);
  ElMessage.success("删除成功");
  loadUsers();
}

async function handleStatusChange(row: SysUserVO, status: number) {
  const label = statusMap[status]?.label || "未知";
  await ElMessageBox.confirm(`确定将「${row.username}」状态改为「${label}」？`, "确认操作");
  await systemApi.updateUserStatus(row.id, status);
  ElMessage.success("状态更新成功");
  loadUsers();
}

async function handleAssignRole(row: SysUserVO) {
  roleUserId.value = row.id;
  selectedRoleIds.value = row.roles.map((r) => r.id);
  if (allRoles.value.length === 0) {
    allRoles.value = await systemApi.fetchRoles();
  }
  roleDialogVisible.value = true;
}

async function handleSaveRoles() {
  await systemApi.assignUserRoles(roleUserId.value, selectedRoleIds.value);
  ElMessage.success("角色分配成功");
  roleDialogVisible.value = false;
  loadUsers();
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

// ========== 角色管理 ==========
const rolesLoading = ref(false);
const rolesTableData = ref<SysRoleVO[]>([]);

const roleEditDialogVisible = ref(false);
const roleEditDialogTitle = ref("新增角色");
const editingRoleId = ref<number | null>(null);
const roleForm = ref<CreateRoleRequest>({ roleCode: "", roleName: "", permissionIds: [] });

const permDialogVisible = ref(false);
const permRoleId = ref<number>(0);
const permRoleName = ref("");
const permissionTree = ref<SysPermissionTreeVO[]>([]);
const checkedPermissionIds = ref<number[]>([]);

function handlePermissionCheck(
  _node: unknown,
  data: { checkedKeys: Array<string | number> },
) {
  checkedPermissionIds.value = data.checkedKeys.map(Number);
}

async function loadRoles() {
  rolesLoading.value = true;
  try {
    rolesTableData.value = await systemApi.fetchRoles();
  } finally {
    rolesLoading.value = false;
  }
}

function handleRoleAdd() {
  roleEditDialogTitle.value = "新增角色";
  editingRoleId.value = null;
  roleForm.value = { roleCode: "", roleName: "", permissionIds: [] };
  roleEditDialogVisible.value = true;
}

function handleRoleEdit(row: SysRoleVO) {
  roleEditDialogTitle.value = "编辑角色";
  editingRoleId.value = row.id;
  roleForm.value = { roleCode: row.roleCode, roleName: row.roleName };
  roleEditDialogVisible.value = true;
}

async function handleRoleSave() {
  if (editingRoleId.value) {
    await systemApi.updateRole(editingRoleId.value, { roleName: roleForm.value.roleName });
    ElMessage.success("修改成功");
  } else {
    await systemApi.createRole(roleForm.value);
    ElMessage.success("新增成功");
  }
  roleEditDialogVisible.value = false;
  loadRoles();
}

async function handleRoleDelete(row: SysRoleVO) {
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」？`, "确认删除", { type: "warning" });
  await systemApi.deleteRole(row.id);
  ElMessage.success("删除成功");
  loadRoles();
}

function collectIds(nodes: SysPermissionTreeVO[]): number[] {
  const ids: number[] = [];
  for (const n of nodes) {
    ids.push(n.id);
    if (n.children) ids.push(...collectIds(n.children));
  }
  return ids;
}

async function handleAssignPermission(row: SysRoleVO) {
  permRoleId.value = row.id;
  permRoleName.value = row.roleName;
  if (permissionTree.value.length === 0) {
    permissionTree.value = await systemApi.fetchPermissionTree();
  }
  const detail = await systemApi.fetchRoleDetail(row.id);
  checkedPermissionIds.value = collectIds(detail.permissions);
  permDialogVisible.value = true;
}

async function handleSavePermissions() {
  await systemApi.assignRolePermissions(permRoleId.value, checkedPermissionIds.value);
  ElMessage.success("权限分配成功");
  permDialogVisible.value = false;
}

// ========== 权限管理 ==========
const permsLoading = ref(false);
const permTreeData = ref<SysPermissionTreeVO[]>([]);

async function loadPermissions() {
  permsLoading.value = true;
  try {
    permTreeData.value = await systemApi.fetchPermissionTree();
  } finally {
    permsLoading.value = false;
  }
}

// ========== 个人设置 ==========
const profileLoading = ref(false);
const profile = ref<SysUserVO | null>(null);

const pwdForm = ref<ChangePasswordRequest>({ oldPassword: "", newPassword: "" });
const confirmPassword = ref("");

async function loadProfile() {
  profileLoading.value = true;
  try {
    profile.value = await systemApi.fetchProfile();
  } finally {
    profileLoading.value = false;
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

// ========== Tab 切换加载 ==========
function handleTabChange(tab: string) {
  activeTab.value = tab;
  if (tab === "users") loadUsers();
  else if (tab === "roles") loadRoles();
  else if (tab === "permissions") loadPermissions();
  else if (tab === "profile") loadProfile();
}

// ========== 初始化 ==========
onMounted(() => {
  loadUsers();
});
</script>

<template>
  <div class="page sys-manage">
    <!-- 页面头部 -->
    <div class="page__header">
      <div>
        <h2 class="page__title">系统管理</h2>
        <p class="page__description">{{ currentTabDesc }}</p>
      </div>
    </div>

    <!-- Tab 导航栏 -->
    <nav class="sys-manage__tabs" role="tablist">
      <button
        v-for="tab in tabList"
        :key="tab.key"
        class="sys-manage__tab"
        :class="{ 'is-active': activeTab === tab.key }"
        type="button"
        role="tab"
        @click="handleTabChange(tab.key)"
      >
        <el-icon><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
      </button>
    </nav>

    <!-- 用户管理 -->
    <template v-if="activeTab === 'users'">
      <section class="sys-manage__summary">
        <div>
          <span>用户总数</span>
          <strong>{{ usersTotal }}</strong>
        </div>
        <div>
          <span>当前页</span>
          <strong>{{ usersTableData.length }}</strong>
        </div>
      </section>

      <section class="sys-manage__search">
        <el-form :model="{}" class="sys-manage__search-form" label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="searchUsername" placeholder="搜索用户名" clearable @keyup.enter="handleUserSearch" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchStatus" placeholder="全部状态" clearable>
              <el-option label="正常" :value="1" />
              <el-option label="停用" :value="2" />
              <el-option label="锁定" :value="3" />
            </el-select>
          </el-form-item>
          <div class="sys-manage__search-actions">
            <el-button :icon="Search" type="primary" @click="handleUserSearch">查询</el-button>
            <el-button @click="resetUserQuery">重置</el-button>
          </div>
        </el-form>
      </section>

      <section class="sys-manage__toolbar">
        <el-button :icon="Plus" type="primary" @click="handleUserAdd">新增用户</el-button>
      </section>

      <section class="sys-manage__table">
        <el-table :data="usersTableData" v-loading="usersLoading" border row-key="id">
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column prop="realName" label="真实姓名" min-width="110" />
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
          <el-table-column prop="phone" label="手机号" min-width="130" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="(statusMap[row.status]?.type as any)" size="small" effect="plain">
                {{ statusMap[row.status]?.label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="150">
            <template #default="{ row }">
              <div class="tag-cell">
                <el-tag v-for="r in row.roles" :key="r.id" size="small" effect="plain">{{ r.roleName }}</el-tag>
                <span v-if="!row.roles?.length" class="empty-inline">-</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="280">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleUserEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="handleAssignRole(row)">分配角色</el-button>
              <el-button link type="warning" @click="handleResetPassword(row)">重置密码</el-button>
              <el-dropdown trigger="click" @command="(cmd: number) => handleStatusChange(row, cmd)">
                <el-button link type="info">状态</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :command="1" :disabled="row.status === 1">正常</el-dropdown-item>
                    <el-dropdown-item :command="2" :disabled="row.status === 2">停用</el-dropdown-item>
                    <el-dropdown-item :command="3" :disabled="row.status === 3">锁定</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button link type="danger" @click="handleUserDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="sys-manage__footer">
          <span class="sys-manage__total">共 {{ usersTotal }} 条</span>
          <el-pagination
            v-model:current-page="usersPageNum"
            v-model:page-size="usersPageSize"
            :total="usersTotal"
            background
            layout="prev, pager, next, sizes"
            :page-sizes="[10, 20, 50]"
            @current-change="loadUsers"
            @size-change="usersPageNum = 1; loadUsers()"
          />
        </div>
      </section>
    </template>

    <!-- 角色管理 -->
    <template v-if="activeTab === 'roles'">
      <section class="sys-manage__toolbar">
        <el-button :icon="Plus" type="primary" @click="handleRoleAdd">新增角色</el-button>
      </section>

      <section class="sys-manage__table">
        <el-table :data="rolesTableData" v-loading="rolesLoading" border row-key="id">
          <el-table-column prop="roleCode" label="角色编码" min-width="180" />
          <el-table-column prop="roleName" label="角色名称" min-width="180" />
          <el-table-column fixed="right" label="操作" width="220">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleRoleEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="handleAssignPermission(row)">分配权限</el-button>
              <el-button link type="danger" @click="handleRoleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <!-- 权限管理 -->
    <template v-if="activeTab === 'permissions'">
      <section class="sys-manage__table" v-loading="permsLoading">
        <div class="sys-manage__perm-tree">
          <el-tree
            :data="permTreeData"
            node-key="id"
            default-expand-all
            :props="{ children: 'children', label: 'permissionName' }"
          >
            <template #default="{ data }">
              <div class="perm-node">
                <span class="perm-name">{{ data.permissionName }}</span>
                <el-tag size="small" type="info" effect="plain">{{ data.permissionCode }}</el-tag>
              </div>
            </template>
          </el-tree>
        </div>
      </section>
    </template>

    <!-- 个人设置 -->
    <template v-if="activeTab === 'profile'">
      <section class="sys-manage__profile" v-loading="profileLoading">
        <div class="sys-manage__profile-card">
          <h3 class="sys-manage__profile-title">基本信息</h3>
          <template v-if="profile">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>
              <el-descriptions-item label="真实姓名">{{ profile.realName }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ profile.email || "-" }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ profile.phone || "-" }}</el-descriptions-item>
              <el-descriptions-item label="角色">
                <div class="tag-cell">
                  <el-tag v-for="r in profile.roles" :key="r.id" size="small" effect="plain">{{ r.roleName }}</el-tag>
                </div>
              </el-descriptions-item>
            </el-descriptions>
          </template>
        </div>

        <div class="sys-manage__profile-card">
          <h3 class="sys-manage__profile-title">修改密码</h3>
          <el-form label-width="80px" style="max-width: 400px">
            <el-form-item label="旧密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="confirmPassword" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleChangePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </div>
      </section>
    </template>

    <!-- 用户编辑对话框 -->
    <el-dialog v-model="userDialogVisible" :title="userDialogTitle" width="500px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="!!editingUserId" /></el-form-item>
        <el-form-item label="密码" v-if="!editingUserId"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="真实姓名"><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="userForm.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUserSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 角色分配对话框 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="400px" destroy-on-close>
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id">{{ role.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="resetPwdDialogVisible" title="重置密码" width="400px" destroy-on-close>
      <p style="margin-bottom: 16px; color: #606266">为用户「{{ resetPwdUsername }}」设置新密码：</p>
      <el-input v-model="newResetPassword" type="password" show-password placeholder="至少6位" />
      <template #footer>
        <el-button @click="resetPwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmResetPwd">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 角色编辑对话框 -->
    <el-dialog v-model="roleEditDialogVisible" :title="roleEditDialogTitle" width="500px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" :disabled="!!editingRoleId" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleEditDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRoleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配对话框 -->
    <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${permRoleName}`" width="500px" destroy-on-close>
      <el-tree
        :data="permissionTree"
        show-checkbox
        node-key="id"
        :default-checked-keys="checkedPermissionIds"
        :props="{ children: 'children', label: 'permissionName' }"
        @check="handlePermissionCheck"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sys-manage {
  position: relative;
}

/* ========== 页面头部 ========== */
.page__header {
  margin-bottom: 20px;
}

.page__title {
  font-size: 22px;
  font-weight: 800;
  color: #102033;
  margin-bottom: 4px;
}

.page__description {
  color: #63738b;
  font-size: 14px;
}

/* ========== Tab 导航 ========== */
.sys-manage__tabs {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(135deg, rgb(255 255 255 / 92%), rgb(244 250 255 / 80%)),
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 10%), transparent 32%);
  border: 1px solid rgb(216 229 245 / 94%);
  border-radius: 8px;
  box-shadow: 0 16px 42px rgb(16 64 120 / 8%);
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  overflow: hidden;
  padding: 6px;
  position: relative;
}

.sys-manage__tabs::before {
  background: linear-gradient(90deg, #1f6fff, #08bfd3);
  content: "";
  height: 2px;
  left: 6px;
  opacity: 0.7;
  position: absolute;
  right: 6px;
  top: 0;
}

.sys-manage__tab {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #536275;
  cursor: pointer;
  display: inline-flex;
  font-size: 14px;
  font-weight: 700;
  gap: 8px;
  padding: 10px 20px;
  transition: all 200ms ease;
  white-space: nowrap;
}

.sys-manage__tab:hover {
  background: rgb(31 111 255 / 6%);
  color: #0b56d8;
}

.sys-manage__tab.is-active {
  background: linear-gradient(135deg, rgb(31 111 255 / 12%), rgb(8 191 211 / 10%));
  border-color: rgb(31 111 255 / 20%);
  box-shadow: inset 3px 0 0 #1f6fff;
  color: #0b56d8;
}

.sys-manage__tab .el-icon {
  font-size: 16px;
}

/* ========== 统计卡片 ========== */
.sys-manage__summary {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 20px;
}

.sys-manage__summary div {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(135deg, rgb(255 255 255 / 90%), rgb(246 251 255 / 74%)),
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 12%), transparent 34%);
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 16px 42px rgb(16 64 120 / 8%);
  display: grid;
  gap: 6px;
  min-height: 80px;
  overflow: hidden;
  padding: 16px 18px;
  position: relative;
}

.sys-manage__summary div::before {
  background: linear-gradient(180deg, #1f6fff, #08bfd3);
  content: "";
  inset: 16px auto 16px 0;
  position: absolute;
  width: 3px;
}

.sys-manage__summary span {
  color: #63738b;
  font-size: 13px;
  font-weight: 700;
}

.sys-manage__summary strong {
  color: #102033;
  font-size: 28px;
  font-weight: 850;
  line-height: 1;
}

/* ========== 搜索栏 ========== */
.sys-manage__search {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(135deg, rgb(255 255 255 / 92%), rgb(244 250 255 / 80%)),
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 10%), transparent 32%);
  border: 1px solid rgb(216 229 245 / 94%);
  border-radius: 8px;
  box-shadow: 0 16px 42px rgb(16 64 120 / 8%);
  margin-bottom: 20px;
  overflow: hidden;
  padding: 18px;
  position: relative;
}

.sys-manage__search::before {
  background: linear-gradient(90deg, #1f6fff, #08bfd3);
  content: "";
  height: 2px;
  left: 18px;
  opacity: 0.7;
  position: absolute;
  right: 18px;
  top: 0;
}

.sys-manage__search-form {
  align-items: end;
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
}

.sys-manage__search-form :deep(.el-form-item__label) {
  color: #405674;
  font-size: 13px;
  font-weight: 800;
}

.sys-manage__search-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.sys-manage__search-form :deep(.el-input),
.sys-manage__search-form :deep(.el-select) {
  width: 100%;
}

.sys-manage__search-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

/* ========== 工具栏 ========== */
.sys-manage__toolbar {
  margin-bottom: 16px;
}

/* ========== 表格区域 ========== */
.sys-manage__table {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 90%), rgb(247 251 255 / 82%)),
    rgb(255 255 255 / 86%);
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 18px 50px rgb(16 64 120 / 9%);
  overflow: hidden;
}

.sys-manage__table :deep(.el-table) {
  background: transparent;
}

.sys-manage__table :deep(.el-table__inner-wrapper::before) {
  background: #dbe9f8;
}

.sys-manage__table :deep(.el-table__header-wrapper th) {
  background: linear-gradient(180deg, #f6faff, #edf6ff);
}

.sys-manage__table :deep(.el-table__row) {
  transition: background 180ms ease, transform 180ms ease;
}

.sys-manage__table :deep(.el-table__row:hover) {
  transform: translateX(2px);
}

.sys-manage__footer {
  align-items: center;
  background: rgb(248 251 255 / 72%);
  border-top: 1px solid #e6edf7;
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
}

.sys-manage__total {
  color: #536275;
  font-size: 14px;
}

/* ========== 权限树 ========== */
.sys-manage__perm-tree {
  padding: 18px;
}

.perm-node {
  display: flex;
  align-items: center;
  gap: 12px;
}

.perm-name {
  min-width: 120px;
  font-weight: 600;
}

/* ========== 个人设置 ========== */
.sys-manage__profile {
  display: grid;
  gap: 20px;
}

.sys-manage__profile-card {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(135deg, rgb(255 255 255 / 90%), rgb(246 251 255 / 74%)),
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 12%), transparent 34%);
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 16px 42px rgb(16 64 120 / 8%);
  overflow: hidden;
  padding: 20px;
  position: relative;
}

.sys-manage__profile-card::before {
  background: linear-gradient(90deg, #1f6fff, #08bfd3);
  content: "";
  height: 2px;
  left: 20px;
  opacity: 0.7;
  position: absolute;
  right: 20px;
  top: 0;
}

.sys-manage__profile-title {
  color: #102033;
  font-size: 16px;
  font-weight: 800;
  margin-bottom: 16px;
}

/* ========== 通用 ========== */
.tag-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.empty-inline {
  color: #667085;
  font-size: 13px;
}

@media (max-width: 720px) {
  .sys-manage__summary {
    grid-template-columns: 1fr;
  }

  .sys-manage__tabs {
    overflow-x: auto;
  }

  .sys-manage__tab {
    padding: 8px 14px;
    font-size: 13px;
  }
}
</style>
