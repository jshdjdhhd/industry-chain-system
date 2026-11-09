<script setup lang="ts">
import { computed, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  CollectionTag,
  Connection,
  DataLine,
  Document,
  Expand,
  Files,
  Fold,
  Grid,
  House,
  Key,
  OfficeBuilding,
  Rank,
  Setting,
  Share,
  Search,
  TrendCharts,
  UserFilled,
} from "@element-plus/icons-vue";
import { useAppStore } from "@/stores/app";
import { useAuthStore } from "@/stores/auth";
import { useAiChatStore } from "@/stores/aiChat";
import AiAssistantPanel from "@/views/assistant/AiAssistantPanel.vue";

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const authStore = useAuthStore();
const aiChatStore = useAiChatStore();
const showAiAssistant = computed(() => !route.meta.hideAiAssistant);

async function handleLogout() {
  try {
    await authStore.logout();
  } finally {
    aiChatStore.clearOnLogout();
    router.push("/login");
  }
}

const topNavItems = [
  { path: "/enterprises", label: "企业管理", icon: OfficeBuilding },
  { path: "/chains", label: "产业链管理", icon: Grid },
  { path: "/matching", label: "智能挂链", icon: Connection },
  { path: "/dashboard", label: "数据看板", icon: DataLine },
];

const sideGroups = [
  {
    index: "workspace",
    label: "工作台",
    icon: House,
    children: [{ path: "/dashboard", label: "数据看板", icon: DataLine }],
  },
  {
    index: "enterprise",
    label: "企业管理",
    icon: OfficeBuilding,
    children: [
      { path: "/enterprises", label: "企业列表", icon: Files },
      { path: "/enterprises/unregistered", label: "未入库企业", icon: Files },
      { path: "/enterprises/outbound", label: "出库企业", icon: Files },
      { path: "/enterprises/import-records", label: "导入记录", icon: Files },
      { path: "/tags", label: "标签管理", icon: CollectionTag },
    ],
  },
  {
    index: "chain",
    label: "产业链管理",
    icon: Grid,
    children: [
      { path: "/chains", label: "产业链树", icon: Share },
      {
        path: "/chains/node-search",
        label: "节点搜索",
        icon: Search,
      },
      {
        path: "/chains/node-adjustment",
        label: "节点调整",
        icon: Rank,
      },
    ],
  },
  {
    index: "matching",
    label: "智能挂链",
    icon: Connection,
    children: [
      { path: "/matching", label: "智能推荐", icon: Connection },
      { path: "/matching/logs", label: "操作日志", icon: DataLine },
    ],
  },
  {
    index: "analysis",
    label: "健康分析",
    icon: TrendCharts,
    children: [
      {
        path: "/analysis/diagnosis",
        label: "可视化诊断",
        icon: TrendCharts,
      },
      {
        path: "/analysis/reports",
        label: "报告历史",
        icon: Files,
      },
    ],
  },
  {
    index: "system",
    label: "系统管理",
    icon: Setting,
    children: [
      { path: "/system/manage", label: "系统管理", icon: Key },
      { path: "/system/operation-logs", label: "操作日志", icon: Document },
    ],
  },
];

const activeMenu = computed(() => {
  if (route.path.startsWith("/enterprises/unregistered")) {
    return "/enterprises/unregistered";
  }
  if (route.path.startsWith("/enterprises/outbound")) {
    return "/enterprises/outbound";
  }
  if (route.path.startsWith("/enterprises/import-records")) {
    return "/enterprises/import-records";
  }
  if (route.path.startsWith("/enterprises")) return "/enterprises";
  if (route.path.startsWith("/tags")) return "/tags";
  if (route.path.startsWith("/chains/node-search")) {
    return "/chains/node-search";
  }
  if (route.path.startsWith("/chains/node-adjustment")) {
    return "/chains/node-adjustment";
  }
  if (route.path.startsWith("/chains")) return "/chains";
  if (route.path.startsWith("/matching/logs")) return "/matching/logs";
  if (route.path.startsWith("/matching")) return "/matching";
  if (route.path.startsWith("/analysis/diagnosis")) {
    return "/analysis/diagnosis";
  }
  if (route.path.startsWith("/analysis/reports")) {
    return "/analysis/reports";
  }
  if (route.path.startsWith("/analysis")) return "/analysis/diagnosis";
  if (route.path.startsWith("/system/manage")) return "/system/manage";
  if (route.path.startsWith("/system/operation-logs"))
    return "/system/operation-logs";
  return route.path;
});

const activeTopNav = computed(() => {
  if (route.path.startsWith("/enterprises") || route.path.startsWith("/tags")) {
    return "/enterprises";
  }
  if (route.path.startsWith("/chains")) return "/chains";
  if (route.path.startsWith("/matching")) return "/matching";
  return "/dashboard";
});

const defaultOpeneds = computed(() =>
  appStore.sidebarCollapsed
    ? []
    : ["workspace", "enterprise", "chain", "matching", "analysis", "system"],
);

watchEffect(() => {
  const title =
    typeof route.meta.title === "string" ? route.meta.title : "数据看板";
  appStore.setPageTitle(title);
  document.title = `${title} - 产业链管理系统`;
});

function navigate(path: string) {
  if (route.path !== path) router.push(path);
}
</script>

<template>
  <el-container class="admin-layout">
    <el-header class="admin-layout__topbar" height="60px">
      <div class="admin-layout__brand">
        <span class="admin-layout__brand-mark">
          <el-icon><Connection /></el-icon>
        </span>
        <span class="admin-layout__brand-copy">
          <span class="admin-layout__brand-title">产业链管理系统</span>
          <small>企业、节点、挂链协同管理</small>
        </span>
      </div>

      <el-button
        class="admin-layout__collapse"
        :icon="appStore.sidebarCollapsed ? Expand : Fold"
        text
        title="切换侧栏"
        @click="appStore.toggleSidebar()"
      />

      <nav class="admin-layout__topnav" aria-label="主导航">
        <button
          v-for="item in topNavItems"
          :key="item.path"
          class="admin-layout__topnav-item"
          :class="{ 'is-active': activeTopNav === item.path }"
          type="button"
          @click="navigate(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="admin-layout__actions">
        <el-dropdown trigger="click" @command="handleLogout">
          <span class="admin-layout__avatar">
            <el-icon><UserFilled /></el-icon>
            <span class="admin-layout__username">{{ authStore.userName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="admin-layout__body">
      <el-aside
        class="admin-layout__aside"
        :width="appStore.sidebarCollapsed ? '72px' : '196px'"
      >
        <el-menu
          class="admin-layout__menu"
          :collapse="appStore.sidebarCollapsed"
          :default-active="activeMenu"
          :default-openeds="defaultOpeneds"
          :unique-opened="false"
        >
          <template v-for="group in sideGroups" :key="group.index">
            <el-sub-menu
              v-if="group.children.length > 1 || !appStore.sidebarCollapsed"
              :index="group.index"
            >
              <template #title>
                <el-icon><component :is="group.icon" /></el-icon>
                <span>{{ group.label }}</span>
              </template>
              <el-menu-item
                v-for="item in group.children"
                :key="`${group.index}-${item.path}-${item.label}`"
                :index="item.path"
                @click="navigate(item.path)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <template #title>{{ item.label }}</template>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item
              v-else
              :index="group.children[0].path"
              @click="navigate(group.children[0].path)"
            >
              <el-icon><component :is="group.children[0].icon" /></el-icon>
              <template #title>{{ group.children[0].label }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="admin-layout__crumbbar" height="44px">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ appStore.pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </el-header>

        <el-main class="admin-layout__main">
          <RouterView />
        </el-main>
      </el-container>
    </el-container>
    <AiAssistantPanel v-if="showAiAssistant" />
  </el-container>
</template>

<style scoped>
.admin-layout {
  background:
    linear-gradient(135deg, rgb(247 251 255 / 96%), rgb(232 243 252 / 92%)),
    url("/industry-map-bg.png") center / cover fixed;
  min-height: 100vh;
}

.admin-layout__topbar {
  align-items: center;
  backdrop-filter: blur(20px);
  background:
    linear-gradient(
      90deg,
      rgb(9 38 86 / 94%) 0%,
      rgb(12 73 151 / 90%) 55%,
      rgb(8 191 211 / 82%) 100%
    ),
    radial-gradient(circle at 78% 0, rgb(255 255 255 / 20%), transparent 32%);
  border-bottom: 1px solid rgb(255 255 255 / 22%);
  box-shadow: 0 14px 40px rgb(13 60 126 / 20%);
  color: #ffffff;
  display: flex;
  gap: 10px;
  padding: 0 16px 0 20px;
  position: sticky;
  top: 0;
  z-index: 20;
}

.admin-layout__brand {
  align-items: center;
  display: flex;
  flex: 0 0 196px;
  gap: 10px;
  min-width: 0;
}

.admin-layout__brand-mark {
  align-items: center;
  background:
    linear-gradient(135deg, rgb(255 255 255 / 24%), rgb(255 255 255 / 8%)),
    linear-gradient(135deg, #1f6fff, #08bfd3);
  border: 1px solid rgb(255 255 255 / 32%);
  border-radius: 8px;
  box-shadow: 0 0 24px rgb(8 191 211 / 28%);
  display: inline-flex;
  flex: 0 0 auto;
  font-size: 20px;
  height: 36px;
  justify-content: center;
  width: 36px;
}

.admin-layout__brand-copy {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.admin-layout__brand-title {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-layout__brand-copy small {
  color: rgb(229 244 255 / 78%);
  font-size: 11px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-layout__collapse {
  color: #ffffff;
  font-size: 20px;
}

.admin-layout__topnav {
  align-items: center;
  display: flex;
  flex: 1;
  gap: 8px;
  min-width: 0;
}

.admin-layout__topnav-item {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #e8f1ff;
  cursor: pointer;
  display: inline-flex;
  font-weight: 700;
  gap: 8px;
  height: 44px;
  padding: 0 18px;
  white-space: nowrap;
}

.admin-layout__topnav-item:hover,
.admin-layout__topnav-item.is-active {
  background: rgb(255 255 255 / 16%);
  border-color: rgb(255 255 255 / 22%);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 18%);
  color: #ffffff;
}

.admin-layout__topnav-item .el-icon {
  font-size: 18px;
}

.admin-layout__actions {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

.admin-layout__actions :deep(.el-button) {
  color: #ffffff;
}

.admin-layout__avatar {
  align-items: center;
  background: rgb(255 255 255 / 16%);
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 16px;
  color: #ffffff;
  cursor: pointer;
  display: inline-flex;
  gap: 6px;
  height: 32px;
  padding: 0 12px 0 8px;
}

.admin-layout__username {
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
}

.admin-layout__body {
  min-height: calc(100vh - 60px);
}

.admin-layout__aside {
  backdrop-filter: blur(20px);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 90%), rgb(246 251 255 / 78%)),
    radial-gradient(circle at 0 0, rgb(31 111 255 / 8%), transparent 28%);
  border-right: 1px solid rgb(216 229 245 / 82%);
  box-shadow: 10px 0 34px rgb(20 68 130 / 8%);
  transition: width 180ms ease;
}

.admin-layout__menu {
  border-right: 0;
  padding: 12px 8px;
}

.admin-layout__menu :deep(.el-sub-menu__title),
.admin-layout__menu :deep(.el-menu-item) {
  border-radius: 6px;
  color: #243a58;
  font-weight: 700;
  height: 44px;
  line-height: 44px;
  margin: 4px 0;
}

.admin-layout__menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(
    90deg,
    rgb(31 111 255 / 13%),
    rgb(8 191 211 / 10%)
  );
  box-shadow: inset 3px 0 0 #1f6fff;
  color: #0b56d8;
}

.admin-layout__menu :deep(.el-sub-menu .el-menu-item) {
  font-weight: 600;
  margin-left: 4px;
  min-width: 0;
}

.admin-layout__crumbbar {
  align-items: center;
  backdrop-filter: blur(14px);
  background: rgb(255 255 255 / 72%);
  border-bottom: 1px solid rgb(216 229 245 / 78%);
  display: flex;
  padding: 0 20px;
}

.admin-layout__main {
  background:
    linear-gradient(135deg, rgb(246 250 255 / 88%), rgb(235 246 252 / 82%)),
    url("/industry-map-bg.png") center / cover fixed;
  min-width: 0;
  padding: 18px 20px 24px;
}

@media (max-width: 980px) {
  .admin-layout__brand {
    flex-basis: auto;
  }

  .admin-layout__brand-title {
    font-size: 18px;
  }

  .admin-layout__topnav {
    display: none;
  }

  .admin-layout__aside {
    display: none;
  }

  .admin-layout__main {
    overflow-x: hidden;
    padding: 14px 12px 20px;
  }
}
</style>
