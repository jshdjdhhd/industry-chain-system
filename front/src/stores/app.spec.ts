import { setActivePinia, createPinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import { useAppStore } from "./app";

describe("app store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("tracks global layout state", () => {
    const store = useAppStore();

    expect(store.sidebarCollapsed).toBe(false);
    expect(store.pageTitle).toBe("企业管理");

    store.toggleSidebar();
    store.setPageTitle("智能挂链");

    expect(store.sidebarCollapsed).toBe(true);
    expect(store.pageTitle).toBe("智能挂链");
  });
});
