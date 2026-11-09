import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { aiChatStream } from "@/api/aiChat";
import { agentChatStream, agentConfirm } from "@/api/agent";
import { useAuthStore } from "@/stores/auth";
import type {
  AiChatHistoryItem,
  AiChatMessage,
  AiChatConfirmMessage,
  AiChatRequestPayload,
  AiChatSession,
  AiChatSessionMessage,
  AiChatStreamCallbacks,
  AiChatStreamHandle,
} from "@/types/domain";
import type {
  AgentChatRequestPayload,
  AgentConfirmEvent,
  AgentStreamCallbacks,
  AgentStreamHandle,
} from "@/types/agent";

export type ConfirmMessage = AiChatConfirmMessage;

/** 消息联合类型（会话内存储时需断言使用） */
type ChatMessage = AiChatMessage | ConfirmMessage;
type ContextOption = { id: number; name: string };

const STORAGE_PREFIX = "industry-chain:ai-chat:v1:";
const MAX_SESSIONS = 20;
const MAX_MESSAGES = 50;

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

function storageKey(userId: number): string {
  return `${STORAGE_PREFIX}${userId}`;
}

export const useAiChatStore = defineStore("aiChat", () => {
  const panelOpen = ref(false);
  const sessions = ref<AiChatSession[]>([]);
  const currentSessionId = ref<string | null>(null);
  const currentUserId = ref<number | null>(null);
  const streaming = ref(false);
  const storageError = ref<string | null>(null);
  const streamHandle = ref<AiChatStreamHandle | null>(null);

  const agentMode = ref(false);
  const agentStreamHandle = ref<AgentStreamHandle | null>(null);
  const confirmTimers = new Map<string, ReturnType<typeof setTimeout>>();
  let idleTimer: ReturnType<typeof setTimeout> | null = null;
  const IDLE_TIMEOUT_MS = 60_000;

  const currentSession = computed(
    () =>
      sessions.value.find((session) => session.id === currentSessionId.value) ||
      null,
  );

  const canSend = computed(() => {
    const session = currentSession.value;
    if (!session || streaming.value) return false;
    return true;
  });

  function loadFromStorage(userId: number) {
    if (currentUserId.value !== userId) {
      abortStream();
      sessions.value = [];
      currentSessionId.value = null;
      currentUserId.value = userId;
    }
    storageError.value = null;

    try {
      const raw = localStorage.getItem(storageKey(userId));
      if (!raw) {
        sessions.value = [];
        ensureDefaultSession();
        return;
      }

      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        throw new Error("聊天记录格式错误");
      }
      sessions.value = (parsed as AiChatSession[])
        .slice(0, MAX_SESSIONS)
        .map(normalizeStoredSession);
      currentSessionId.value = sessions.value[0]?.id || null;
      ensureDefaultSession();
    } catch {
      localStorage.removeItem(storageKey(userId));
      storageError.value = "聊天记录已损坏，已清空当前用户的历史记录";
      sessions.value = [];
      currentSessionId.value = null;
      ensureDefaultSession();
    }
  }

  function saveToStorage() {
    if (currentUserId.value === null) return;
    try {
      const data = sessions.value.map((session) => ({
        ...session,
        messages: session.messages.slice(-MAX_MESSAGES),
      }));
      localStorage.setItem(
        storageKey(currentUserId.value),
        JSON.stringify(data),
      );
    } catch (error) {
      if (
        error instanceof DOMException &&
        error.name === "QuotaExceededError"
      ) {
        storageError.value = "存储空间已满，请删除旧会话后再试";
      } else {
        storageError.value = "聊天记录保存失败";
      }
    }
  }

  function ensureDefaultSession() {
    if (sessions.value.length === 0) {
      createSession();
      return;
    }
    if (
      !currentSessionId.value ||
      !sessions.value.some((session) => session.id === currentSessionId.value)
    ) {
      currentSessionId.value = sessions.value[0].id;
    }
  }

  function createSession(): string {
    if (streaming.value) {
      return currentSessionId.value || "";
    }
    if (sessions.value.length >= MAX_SESSIONS) {
      sessions.value.pop();
    }

    const now = Date.now();
    const session: AiChatSession = {
      id: generateId(),
      title: "新对话",
      enterpriseId: null,
      enterpriseName: null,
      enterpriseIds: [],
      enterpriseNames: [],
      chainId: null,
      chainName: null,
      chainIds: [],
      chainNames: [],
      messages: [],
      createdAt: now,
      updatedAt: now,
      streaming: false,
      failedRequest: null,
    };
    sessions.value.unshift(session);
    currentSessionId.value = session.id;
    saveToStorage();
    return session.id;
  }

  function switchSession(id: string) {
    if (
      streaming.value ||
      !sessions.value.some((session) => session.id === id)
    ) {
      return;
    }
    currentSessionId.value = id;
  }

  function deleteSession(id: string) {
    if (streaming.value) return;
    const index = sessions.value.findIndex((session) => session.id === id);
    if (index < 0) return;

    sessions.value.splice(index, 1);
    if (currentSessionId.value === id) {
      currentSessionId.value = sessions.value[0]?.id || null;
    }
    ensureDefaultSession();
    saveToStorage();
  }

  function clearAllSessions() {
    if (streaming.value) return;
    sessions.value = [];
    currentSessionId.value = null;
    ensureDefaultSession();
    saveToStorage();
  }

  function setEnterprises(selected: ContextOption[]) {
    const session = currentSession.value;
    if (!session || streaming.value) return;
    const normalized = normalizeOptions(selected);
    session.enterpriseIds = normalized.map((item) => item.id);
    session.enterpriseNames = normalized.map((item) => item.name);
    session.enterpriseId = session.enterpriseIds[0] ?? null;
    session.enterpriseName = session.enterpriseNames[0] ?? null;
    session.updatedAt = Date.now();
    saveToStorage();
  }

  function setEnterprise(id: number | null, name: string | null) {
    setEnterprises(id === null ? [] : [{ id, name: name ?? "" }]);
  }

  function setChains(selected: ContextOption[]) {
    const session = currentSession.value;
    if (!session || streaming.value) return;
    const normalized = normalizeOptions(selected);
    session.chainIds = normalized.map((item) => item.id);
    session.chainNames = normalized.map((item) => item.name);
    session.chainId = session.chainIds[0] ?? null;
    session.chainName = session.chainNames[0] ?? null;
    session.updatedAt = Date.now();
    saveToStorage();
  }

  function setChain(id: number | null, name: string | null) {
    setChains(id === null ? [] : [{ id, name: name ?? "" }]);
  }

  function buildHistory(session: AiChatSession): AiChatHistoryItem[] {
    return session.messages
      .filter(
        (message) =>
          isChatMessage(message) &&
          message.status === "completed" && message.content.length > 0,
      )
      .slice(-MAX_MESSAGES)
      .map((message) => ({
        role: message.role,
        content: message.content,
      }));
  }

  function sendMessage(text: string) {
    const session = currentSession.value;
    const normalizedText = text.trim();
    if (!session || !normalizedText || !canSend.value) return;

    const payload: AiChatRequestPayload = {
      enterpriseId: session.enterpriseId,
      chainId: session.chainId,
      enterpriseIds: session.enterpriseIds,
      chainIds: session.chainIds,
      message: normalizedText,
      history: buildHistory(session),
    };

    session.messages.push(createMessage("user", normalizedText, "completed"));
    if (session.messages.length === 1) {
      session.title = normalizedText.slice(0, 30);
    }

    const assistantMessage = createMessage("assistant", "", "streaming");
    session.messages.push(assistantMessage);
    startStream(session, assistantMessage, payload);
  }

  function sendMessageViaAgent(text: string) {
    const session = currentSession.value;
    const normalizedText = text.trim();
    if (!session || !normalizedText || !canSend.value || streaming.value) return;

    const payload: AgentChatRequestPayload = {
      sessionId: session.id,
      message: normalizedText,
      context: {
        enterpriseId: session.enterpriseIds[0] ?? undefined,
        chainId: session.chainIds[0] ?? undefined,
      },
    };

    session.messages.push(createMessage("user", normalizedText, "completed"));
    if (session.messages.length === 1) {
      session.title = normalizedText.slice(0, 30);
    }

    const assistantMessage = createMessage("assistant", "", "streaming");
    session.messages.push(assistantMessage);

    session.streaming = true;
    session.updatedAt = Date.now();
    streaming.value = true;
    saveToStorage();
    resetIdleTimer();

    const sessionId = session.id;
    const messageId = assistantMessage.id;

    const callbacks: AgentStreamCallbacks = {
      onText: (content) => {
        resetIdleTimer();
        const target = findStreamTarget(sessionId, messageId);
        if (target) {
          target.message.content += content;
        }
      },
      onToolCall: (tool, _args) => {
        resetIdleTimer();
        const s = sessions.value.find((item) => item.id === sessionId);
        if (!s) return;
        const toolMsg = createMessage("assistant", "", "completed");
        toolMsg.content = `[调用工具: ${tool}]`;
        (s.messages as ChatMessage[]).push(toolMsg);
      },
      onToolResult: (_tool, _result) => {
        resetIdleTimer();
      },
      onConfirm: (event: AgentConfirmEvent) => {
        resetIdleTimer();
        const s = sessions.value.find((item) => item.id === sessionId);
        if (!s) return;
        const confirmMsg: ConfirmMessage = {
          id: generateId(),
          role: "assistant",
          type: "confirm",
          confirmId: event.confirmId,
          tool: event.tool,
          args: event.args,
          description: event.description,
          riskLevel: event.riskLevel,
          status: "pending",
          createdAt: Date.now(),
        };
        (s.messages as ChatMessage[]).push(confirmMsg);
        startConfirmTimeout(confirmMsg.confirmId, sessionId);
      },
      onDone: () => {
        const target = findStreamTarget(sessionId, messageId);
        if (target) {
          target.message.status = "completed";
          target.session.streaming = false;
          target.session.updatedAt = Date.now();
        }
        finishAgentStream();
      },
      onError: (error) => {
        const target = findStreamTarget(sessionId, messageId);
        if (target) {
          target.message.status = "failed";
          target.message.content = `Agent 回复失败：${error}`;
          target.session.streaming = false;
          target.session.updatedAt = Date.now();
        }
        finishAgentStream();
      },
    };
    agentStreamHandle.value = agentChatStream(payload, callbacks);
  }

  function startConfirmTimeout(confirmId: string, sessionId: string) {
    const timer = setTimeout(() => {
      confirmTimers.delete(confirmId);
      const s = sessions.value.find((item) => item.id === sessionId);
      if (!s) return;
      const msg = (s.messages as ChatMessage[]).find(
        (m): m is ConfirmMessage =>
          m.role === "assistant" &&
          "type" in m &&
          m.type === "confirm" &&
          m.confirmId === confirmId &&
          m.status === "pending",
      );
      if (msg) {
        msg.status = "timeout";
        saveToStorage();
      }
    }, 60_000);
    confirmTimers.set(confirmId, timer);
  }

  function confirmAction(confirmId: string, approved: boolean) {
    const session = currentSession.value;
    if (!session) return;

    const msg = (session.messages as ChatMessage[]).find(
      (m): m is ConfirmMessage =>
        m.role === "assistant" &&
        "type" in m &&
        m.type === "confirm" &&
        m.confirmId === confirmId &&
        m.status === "pending",
    );
    if (!msg) return;

    // 清除超时定时器
    const timer = confirmTimers.get(confirmId);
    if (timer) {
      clearTimeout(timer);
      confirmTimers.delete(confirmId);
    }

    msg.status = approved ? "confirmed" : "rejected";
    saveToStorage();

    agentConfirm({
      sessionId: session.id,
      confirmId,
      approved,
    }).catch(() => {
      // 网络失败不影响本地状态展示
    });
  }

  function finishAgentStream() {
    clearIdleTimer();
    streaming.value = false;
    agentStreamHandle.value = null;
    saveToStorage();
  }

  function retryLastMessage() {
    const session = currentSession.value;
    const failedRequest = session?.failedRequest;
    if (!session || !failedRequest || streaming.value) return;

    const lastMessage = session.messages.at(-1);
    if (
      lastMessage &&
      isChatMessage(lastMessage) &&
      lastMessage.role === "assistant" &&
      lastMessage.status === "failed"
    ) {
      session.messages.pop();
    }

    const assistantMessage = createMessage("assistant", "", "streaming");
    session.messages.push(assistantMessage);
    startStream(session, assistantMessage, failedRequest);
  }

  function startStream(
    session: AiChatSession,
    assistantMessage: AiChatMessage,
    payload: AiChatRequestPayload,
  ) {
    session.streaming = true;
    session.failedRequest = null;
    session.updatedAt = Date.now();
    streaming.value = true;
    saveToStorage();
    resetIdleTimer();

    const sessionId = session.id;
    const messageId = assistantMessage.id;
    const callbacks: AiChatStreamCallbacks = {
      onChunk: (chunk) => {
        resetIdleTimer();
        const target = findStreamTarget(sessionId, messageId);
        if (target) {
          target.message.content += chunk;
        }
      },
      onDone: () => {
        const target = findStreamTarget(sessionId, messageId);
        if (target) {
          target.message.status = "completed";
          target.session.streaming = false;
          target.session.updatedAt = Date.now();
        }
        finishStream();
      },
      onError: (error) => {
        const target = findStreamTarget(sessionId, messageId);
        if (target) {
          target.message.status = "failed";
          target.message.content = `AI 回复失败：${error}`;
          target.session.streaming = false;
          target.session.failedRequest = payload;
          target.session.updatedAt = Date.now();
        }
        finishStream();
      },
    };
    streamHandle.value = aiChatStream(payload, callbacks);
  }

  function findStreamTarget(sessionId: string, messageId: string) {
    const session = sessions.value.find((item) => item.id === sessionId);
    const message = session?.messages.find((item) => item.id === messageId);
    return session && message && isChatMessage(message) ? { session, message } : null;
  }

  function finishStream() {
    clearIdleTimer();
    streaming.value = false;
    streamHandle.value = null;
    saveToStorage();
  }

  function abortStream() {
    clearIdleTimer();
    streamHandle.value?.abort();
    streamHandle.value = null;
    agentStreamHandle.value?.abort();
    agentStreamHandle.value = null;
    streaming.value = false;
    for (const session of sessions.value) {
      session.streaming = false;
    }
    for (const timer of confirmTimers.values()) {
      clearTimeout(timer);
    }
    confirmTimers.clear();
  }

  /**
   * 停止当前流式传输，并将最后一条流式消息标记为 failed。
   * @param reason - "stopped"（用户手动停止）或 "timeout"（空闲超时）
   */
  function stopStream(reason: "stopped" | "timeout" = "stopped") {
    const session = currentSession.value;
    if (!session || !streaming.value) return;

    const lastMsg = session.messages.at(-1);
    if (
      lastMsg &&
      isChatMessage(lastMsg) &&
      lastMsg.role === "assistant" &&
      lastMsg.status === "streaming"
    ) {
      lastMsg.status = "failed";
      lastMsg.content =
        reason === "timeout"
          ? "AI 响应超时，请重试"
          : lastMsg.content || "已停止响应";
    }

    abortStream();
    saveToStorage();
  }

  function resetIdleTimer() {
    clearIdleTimer();
    idleTimer = setTimeout(() => {
      stopStream("timeout");
    }, IDLE_TIMEOUT_MS);
  }

  function clearIdleTimer() {
    if (idleTimer !== null) {
      clearTimeout(idleTimer);
      idleTimer = null;
    }
  }

  function openPanel() {
    const userId = useAuthStore().user?.userId;
    if (userId === undefined) return;
    if (currentUserId.value !== userId || sessions.value.length === 0) {
      loadFromStorage(userId);
    }
    panelOpen.value = true;
  }

  function closePanel() {
    panelOpen.value = false;
  }

  function togglePanel() {
    if (panelOpen.value) {
      closePanel();
    } else {
      openPanel();
    }
  }

  function clearOnLogout() {
    abortStream();
    panelOpen.value = false;
    sessions.value = [];
    currentSessionId.value = null;
    currentUserId.value = null;
    storageError.value = null;
  }

  function dismissStorageError() {
    storageError.value = null;
  }

  return {
    panelOpen,
    sessions,
    currentSessionId,
    currentUserId,
    streaming,
    storageError,
    currentSession,
    canSend,
    agentMode,
    loadFromStorage,
    saveToStorage,
    createSession,
    switchSession,
    deleteSession,
    clearAllSessions,
    setEnterprise,
    setEnterprises,
    setChain,
    setChains,
    sendMessage,
    sendMessageViaAgent,
    confirmAction,
    stopStream,
    retryLastMessage,
    openPanel,
    closePanel,
    togglePanel,
    clearOnLogout,
    dismissStorageError,
  };
});

function createMessage(
  role: AiChatMessage["role"],
  content: string,
  status: AiChatMessage["status"],
): AiChatMessage {
  return {
    id: generateId(),
    role,
    content,
    status,
    createdAt: Date.now(),
  };
}

function isChatMessage(message: AiChatSessionMessage): message is AiChatMessage {
  return !("type" in message);
}

function normalizeStoredSession(session: AiChatSession): AiChatSession {
  const enterpriseIds = normalizeStoredIds(session.enterpriseIds, session.enterpriseId);
  const enterpriseNames = normalizeStoredNames(
    session.enterpriseNames,
    session.enterpriseName,
    enterpriseIds.length,
  );
  const chainIds = normalizeStoredIds(session.chainIds, session.chainId);
  const chainNames = normalizeStoredNames(
    session.chainNames,
    session.chainName,
    chainIds.length,
  );

  return {
    ...session,
    enterpriseIds,
    enterpriseNames,
    enterpriseId: enterpriseIds[0] ?? null,
    enterpriseName: enterpriseNames[0] ?? null,
    chainIds,
    chainNames,
    chainId: chainIds[0] ?? null,
    chainName: chainNames[0] ?? null,
    messages: Array.isArray(session.messages)
      ? session.messages.slice(-MAX_MESSAGES).map((message) =>
          isChatMessage(message) && message.status === "streaming"
            ? {
                ...message,
                status: "failed" as const,
                content: message.content || "上次回复已中断，请重试",
              }
            : message,
        )
      : [],
    streaming: false,
  };
}

function normalizeOptions(options: ContextOption[]): ContextOption[] {
  const seen = new Set<number>();
  const result: ContextOption[] = [];
  for (const option of options) {
    if (!Number.isInteger(option.id) || option.id <= 0 || seen.has(option.id)) {
      continue;
    }
    seen.add(option.id);
    result.push({ id: option.id, name: option.name });
  }
  return result;
}

function normalizeStoredIds(ids: number[] | undefined, fallback: number | null): number[] {
  const source = Array.isArray(ids) && ids.length > 0 ? ids : fallback === null ? [] : [fallback];
  return [...new Set(source.filter((id) => Number.isInteger(id) && id > 0))];
}

function normalizeStoredNames(
  names: string[] | undefined,
  fallback: string | null,
  expectedLength: number,
): string[] {
  const source = Array.isArray(names) && names.length > 0 ? names : fallback === null ? [] : [fallback];
  return Array.from({ length: expectedLength }, (_, index) => source[index] ?? "");
}
