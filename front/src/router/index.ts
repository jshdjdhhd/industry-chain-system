import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";
import AdminLayout from "@/layouts/AdminLayout.vue";
import { useAuthStore } from "@/stores/auth";

export const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/auth/LoginView.vue"),
    meta: { title: "登录" },
  },
  {
    path: "/",
    component: AdminLayout,
    redirect: "/dashboard",
    children: [
      {
        path: "/dashboard",
        name: "dashboard",
        component: () => import("@/views/dashboard/DashboardView.vue"),
        meta: { title: "数据看板" },
      },
      {
        path: "/enterprises",
        name: "enterprises",
        component: () => import("@/views/enterprise/EnterpriseList.vue"),
        meta: { title: "企业管理", listTitle: "企业管理" },
      },
      {
        path: "/enterprises/unregistered",
        name: "enterprise-unregistered",
        component: () => import("@/views/enterprise/EnterpriseList.vue"),
        meta: {
          title: "未入库企业",
          listTitle: "未入库企业",
          defaultStatus: "UNREGISTERED",
          lockedStatus: true,
        },
      },
      {
        path: "/enterprises/outbound",
        name: "enterprise-outbound",
        component: () => import("@/views/enterprise/EnterpriseList.vue"),
        meta: {
          title: "出库企业",
          listTitle: "出库企业",
          defaultStatus: "OUTBOUND",
          lockedStatus: true,
        },
      },
      {
        path: "/enterprises/import-records",
        name: "enterprise-import-records",
        component: () =>
          import("@/views/enterprise/EnterpriseImportRecords.vue"),
        meta: { title: "导入记录" },
      },
      {
        path: "/enterprises/new",
        name: "enterprise-create",
        component: () => import("@/views/enterprise/EnterpriseForm.vue"),
        meta: { title: "新增企业" },
      },
      {
        path: "/enterprises/:id",
        name: "enterprise-detail",
        component: () => import("@/views/enterprise/EnterpriseDetail.vue"),
        meta: { title: "企业详情" },
      },
      {
        path: "/enterprises/:id/edit",
        name: "enterprise-edit",
        component: () => import("@/views/enterprise/EnterpriseForm.vue"),
        meta: { title: "编辑企业" },
      },
      {
        path: "/tags",
        name: "tags",
        component: () => import("@/views/enterprise/TagManager.vue"),
        meta: { title: "标签管理" },
      },
      {
        path: "/chains",
        name: "chains",
        component: () => import("@/views/chain/ChainManagement.vue"),
        meta: { title: "产业链管理" },
      },
      {
        path: "/chains/node-search",
        name: "chain-node-search",
        component: () => import("@/views/chain/NodeKeywordSearch.vue"),
        meta: { title: "节点搜索" },
      },
      {
        path: "/chains/node-adjustment",
        name: "chain-node-adjustment",
        component: () => import("@/views/chain/NodeAdjustment.vue"),
        meta: { title: "节点调整" },
      },
      {
        path: "/matching",
        name: "matching",
        component: () => import("@/views/matching/MatchingTask.vue"),
        meta: { title: "智能挂链" },
      },
      {
        path: "/matching/logs",
        name: "matching-logs",
        component: () => import("@/views/matching/MatchingLogs.vue"),
        meta: { title: "操作日志" },
      },
      {
        path: "/analysis/diagnosis",
        name: "chain-diagnosis",
        component: () => import("@/views/analysis/ChainDiagnosis.vue"),
        meta: { title: "产业链健康分析" },
      },
      {
        path: "/analysis/reports",
        name: "analysis-reports",
        component: () => import("@/views/analysis/AnalysisReportList.vue"),
        meta: { title: "报告历史" },
      },
      {
        path: "/analysis/reports/:id(\\d+)",
        name: "analysis-report-detail",
        component: () => import("@/views/analysis/AnalysisReportDetail.vue"),
        meta: { title: "报告详情" },
      },
      {
        path: "/operation-logs",
        redirect: "/system/operation-logs",
        meta: { title: "操作日志" },
      },
      // 系统管理路由
      {
        path: "/system/manage",
        name: "system-manage",
        component: () => import("@/views/system/SystemManage.vue"),
        meta: { title: "系统管理", hideAiAssistant: true },
      },
      {
        path: "/system/operation-logs",
        name: "system-operation-logs",
        component: () => import("@/views/system/SystemOperationLog.vue"),
        meta: { title: "操作日志", hideAiAssistant: true },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  if (to.name !== "login" && !authStore.isLoggedIn) {
    return { name: "login" };
  }
  if (to.name === "login" && authStore.isLoggedIn) {
    return { name: "dashboard" };
  }
  if (authStore.isLoggedIn && !authStore.user) {
    await authStore.fetchUser();
  }
});

export default router;
