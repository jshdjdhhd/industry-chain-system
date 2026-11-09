import { mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { beforeEach, describe, expect, it } from "vitest";
import AdminLayout from "./AdminLayout.vue";
import AiAssistantPanel from "@/views/assistant/AiAssistantPanel.vue";
import { routes } from "@/router";

describe("AdminLayout", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("does not expose temporary mock or fake user state in the shell", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });
    router.push("/dashboard");
    await router.isReady();

    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: {
          RouterView: true,
        },
      },
    });

    expect(wrapper.text()).not.toContain("前端 Mock 数据");
    expect(wrapper.text()).not.toContain("12");
    expect(wrapper.text()).not.toContain("管理员");
    expect(wrapper.text()).toContain("节点搜索");
    expect(wrapper.text()).toContain("节点调整");
  });

  it("highlights node adjustment when its route is active", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });
    router.push("/chains/node-adjustment");
    await router.isReady();

    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: {
          RouterView: true,
        },
      },
    });

    expect(
      wrapper.findComponent({ name: "ElMenu" }).props("defaultActive"),
    ).toBe("/chains/node-adjustment");
  });

  it("highlights outbound enterprises when its route is active", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });
    router.push("/enterprises/outbound");
    await router.isReady();

    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: {
          RouterView: true,
        },
      },
    });

    expect(
      wrapper.findComponent({ name: "ElMenu" }).props("defaultActive"),
    ).toBe("/enterprises/outbound");
  });

  it("普通业务路由挂载 AI 助手", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });
    router.push("/dashboard");
    await router.isReady();

    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: { RouterView: true },
      },
    });

    expect(wrapper.findComponent(AiAssistantPanel).exists()).toBe(true);
  });

  it("系统管理路由不挂载 AI 助手", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    });
    router.push("/system/manage");
    await router.isReady();

    const wrapper = mount(AdminLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: { RouterView: true },
      },
    });

    expect(wrapper.findComponent(AiAssistantPanel).exists()).toBe(false);
  });
});
