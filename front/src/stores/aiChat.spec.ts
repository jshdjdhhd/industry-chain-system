import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { aiChatStream } from "@/api/aiChat";
import { useAiChatStore } from "@/stores/aiChat";
import { useAuthStore } from "@/stores/auth";
import type { AiChatStreamCallbacks } from "@/types/domain";

vi.mock("@/api/aiChat", () => ({
  aiChatStream: vi.fn(() => ({ abort: vi.fn() })),
}));

function signIn(userId: number) {
  const auth = useAuthStore();
  auth.user = {
    userId,
    username: `user-${userId}`,
    realName: `用户 ${userId}`,
    roles: [],
    permissions: [],
  };
  auth.token = "test-token";
}

function latestCallbacks(): AiChatStreamCallbacks {
  const calls = vi.mocked(aiChatStream).mock.calls;
  return calls[calls.length - 1][1];
}

describe("useAiChatStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("打开面板时创建默认会话", () => {
    signIn(1);
    const store = useAiChatStore();

    store.openPanel();

    expect(store.panelOpen).toBe(true);
    expect(store.sessions).toHaveLength(1);
    expect(store.currentSessionId).toBe(store.sessions[0].id);
  });

  it("支持创建、切换和删除多个会话", () => {
    signIn(1);
    const store = useAiChatStore();
    store.openPanel();
    const firstId = store.currentSessionId!;

    const secondId = store.createSession();
    expect(store.sessions).toHaveLength(2);
    expect(store.currentSessionId).toBe(secondId);

    store.switchSession(firstId);
    expect(store.currentSessionId).toBe(firstId);

    store.deleteSession(firstId);
    expect(store.sessions.some((session) => session.id === firstId)).toBe(
      false,
    );
    expect(store.currentSessionId).toBe(secondId);
  });

  it("超过 20 个会话时删除最旧会话", () => {
    signIn(1);
    const store = useAiChatStore();
    store.openPanel();
    const oldestId = store.currentSessionId!;

    for (let index = 0; index < 20; index += 1) {
      store.createSession();
    }

    expect(store.sessions).toHaveLength(20);
    expect(store.sessions.some((session) => session.id === oldestId)).toBe(
      false,
    );
  });

  it("上下文必须同时为空或同时有值才能发送", () => {
    signIn(1);
    const store = useAiChatStore();
    store.openPanel();
    expect(store.canSend).toBe(true);

    store.setEnterprise(1, "测试企业");
    expect(store.canSend).toBe(false);
    store.sendMessage("不应发送");
    expect(aiChatStream).not.toHaveBeenCalled();

    store.setChain(2, "测试产业链");
    expect(store.canSend).toBe(true);
    store.sendMessage("分析挂链风险");
    expect(aiChatStream).toHaveBeenCalledTimes(1);
  });

  it("流式回调更新当前请求对应的消息并持久化", () => {
    signIn(1);
    const store = useAiChatStore();
    store.openPanel();

    store.sendMessage("什么是产业链？");
    const callbacks = latestCallbacks();
    callbacks.onChunk("产业链");
    callbacks.onChunk("是……");
    callbacks.onDone();

    expect(store.streaming).toBe(false);
    expect(store.currentSession?.messages.at(-1)).toMatchObject({
      role: "assistant",
      content: "产业链是……",
      status: "completed",
    });

    const persisted = localStorage.getItem("industry-chain:ai-chat:v1:1");
    expect(persisted).toContain("产业链是");
  });

  it("失败请求可重试且不重复添加用户消息", () => {
    signIn(1);
    const store = useAiChatStore();
    store.openPanel();

    store.sendMessage("测试失败");
    latestCallbacks().onError("网络错误");
    const messageCount = store.currentSession!.messages.length;

    store.retryLastMessage();

    expect(aiChatStream).toHaveBeenCalledTimes(2);
    expect(store.currentSession!.messages).toHaveLength(messageCount);
    expect(store.currentSession?.messages.at(-1)?.status).toBe("streaming");
  });

  it("流式生成中禁止切换、删除或清空会话", () => {
    signIn(1);
    const store = useAiChatStore();
    store.openPanel();
    const firstId = store.currentSessionId!;
    const secondId = store.createSession();
    store.sendMessage("生成中");

    store.switchSession(firstId);
    store.deleteSession(secondId);
    store.clearAllSessions();

    expect(store.currentSessionId).toBe(secondId);
    expect(store.sessions).toHaveLength(2);
  });

  it("不同用户只读取各自的本地记录", () => {
    signIn(1);
    const store = useAiChatStore();
    store.openPanel();
    store.setEnterprise(1, "用户一企业");
    store.setChain(1, "用户一产业链");
    store.clearOnLogout();

    signIn(2);
    store.openPanel();

    expect(store.currentUserId).toBe(2);
    expect(store.currentSession?.enterpriseId).toBeNull();
    expect(store.currentSession?.chainId).toBeNull();
  });

  it("损坏的当前用户记录会被隔离清理", () => {
    signIn(1);
    localStorage.setItem("industry-chain:ai-chat:v1:1", "{invalid json");
    const store = useAiChatStore();

    store.openPanel();

    expect(store.storageError).toContain("已损坏");
    expect(store.sessions).toHaveLength(1);
    expect(localStorage.getItem("industry-chain:ai-chat:v1:1")).not.toBe(
      "{invalid json",
    );
  });
});
