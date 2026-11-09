import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia, type Pinia } from "pinia";
import AiAssistantPanel from "@/views/assistant/AiAssistantPanel.vue";
import { useAiChatStore } from "@/stores/aiChat";
import { useAuthStore } from "@/stores/auth";
import { fetchEnterpriseOptions } from "@/api/enterprise";
import { fetchChainOptions } from "@/api/chain";

vi.mock("@/api/enterprise", () => ({
  fetchEnterpriseOptions: vi.fn().mockResolvedValue([]),
}));

vi.mock("@/api/chain", () => ({
  fetchChainOptions: vi.fn().mockResolvedValue([]),
  fetchChainNodeCandidates: vi.fn().mockResolvedValue([]),
}));

vi.mock("@/api/aiChat", () => ({
  aiChatStream: vi.fn(() => ({ abort: vi.fn() })),
}));

let pinia: Pinia;

function mountPanel() {
  return mount(AiAssistantPanel, {
    global: {
      plugins: [pinia, ElementPlus],
      stubs: {
        transition: false,
      },
    },
  });
}

describe("AiAssistantPanel", () => {
  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
    localStorage.clear();
    const auth = useAuthStore();
    auth.user = {
      userId: 1,
      username: "test",
      realName: "测试用户",
      roles: [],
      permissions: [],
    };
    vi.mocked(fetchEnterpriseOptions).mockClear();
    vi.mocked(fetchChainOptions).mockClear();
  });

  it("面板关闭时只显示悬浮按钮", async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(wrapper.find(".ai-fab").exists()).toBe(true);
    expect(wrapper.find(".ai-panel").exists()).toBe(false);
  });

  it("点击悬浮按钮后显示完整面板结构", async () => {
    const wrapper = mountPanel();
    await wrapper.find(".ai-fab").trigger("click");
    await flushPromises();

    expect(wrapper.find(".ai-panel__header").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__toolbar").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__context").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__messages").exists()).toBe(true);
    expect(wrapper.find(".ai-panel__input").exists()).toBe(true);
    expect(fetchEnterpriseOptions).toHaveBeenCalledTimes(1);
    expect(fetchChainOptions).toHaveBeenCalledTimes(1);
  });

  it("会话选择器可切换已创建的会话", async () => {
    const store = useAiChatStore();
    store.openPanel();
    const firstId = store.currentSessionId!;
    store.createSession();
    const wrapper = mountPanel();
    await flushPromises();

    const selector = wrapper.find('[data-testid="session-selector"]');
    expect(selector.exists()).toBe(true);
    store.switchSession(firstId);

    expect(store.currentSessionId).toBe(firstId);
  });

  it("新建对话按钮创建新会话", async () => {
    const store = useAiChatStore();
    store.openPanel();
    const wrapper = mountPanel();
    await flushPromises();
    const before = store.sessions.length;

    await wrapper.find('[data-testid="new-session-button"]').trigger("click");

    expect(store.sessions).toHaveLength(before + 1);
  });

  it("只选择一个上下文时显示提示并禁用发送", async () => {
    const store = useAiChatStore();
    store.openPanel();
    store.setEnterprise(1, "测试企业");
    const wrapper = mountPanel();
    await flushPromises();

    expect(wrapper.find(".ai-panel__hint").text()).toContain(
      "请同时选择企业和产业链",
    );
    const sendButton = wrapper.find('[data-testid="send-button"]');
    expect(sendButton.attributes("disabled")).toBeDefined();
  });

  it("Markdown 输出经过清洗", async () => {
    localStorage.setItem(
      "industry-chain:ai-chat:v1:1",
      JSON.stringify([
        {
          id: "session-1",
          title: "安全测试",
          enterpriseId: null,
          enterpriseName: null,
          chainId: null,
          chainName: null,
          messages: [
            {
              id: "message-1",
              role: "assistant",
              content:
                '<img src="x" onerror="alert(1)"><script>alert(2)</script>',
              status: "completed",
              createdAt: 1,
            },
          ],
          createdAt: 1,
          updatedAt: 1,
          streaming: false,
          failedRequest: null,
        },
      ]),
    );
    const store = useAiChatStore();
    store.openPanel();
    const wrapper = mountPanel();
    await flushPromises();

    const html = wrapper.find(".ai-msg__content").html();
    expect(html).not.toContain("onerror");
    expect(html).not.toContain("<script");
  });
});
