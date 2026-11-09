import { defineStore } from "pinia";

export const useAppStore = defineStore("app", {
  state: () => ({
    sidebarCollapsed: false,
    globalLoading: false,
    pageTitle: "企业管理",
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    },
    setSidebarCollapsed(collapsed: boolean) {
      this.sidebarCollapsed = collapsed;
    },
    setGlobalLoading(loading: boolean) {
      this.globalLoading = loading;
    },
    setPageTitle(title: string) {
      this.pageTitle = title;
    },
  },
});
