import { get, post, put } from "@/api/http";
import type {
  AgentChatRequestPayload,
  AgentConfirmPayload,
  AgentStreamCallbacks,
  AgentStreamHandle,
  AgentTool,
  AiToolPermissionVO,
} from "@/types/agent";

/** Agent SSE 流式对话 */
export function agentChatStream(
  data: AgentChatRequestPayload,
  callbacks: AgentStreamCallbacks,
): AgentStreamHandle {
  const controller = new AbortController();
  const token = localStorage.getItem("token") || "";
  let finished = false;

  const finishOnce = () => {
    if (finished) return;
    finished = true;
    callbacks.onDone();
  };

  const reportError = (error: unknown) => {
    if (finished || isAbortError(error)) return;
    finished = true;
    callbacks.onError(error instanceof Error ? error.message : "Agent 请求失败");
  };

  void fetch("/api/agent/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      if (!response.body) throw new Error("响应流不可用");

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (!finished) {
        const result = await reader.read();
        if (result.done) {
          buffer += decoder.decode();
          if (buffer.trim()) processSseEvents(buffer, callbacks, finishOnce);
          finishOnce();
          return;
        }
        buffer += decoder.decode(result.value, { stream: true });
        const lastEventEnd = buffer.lastIndexOf("\n\n");
        if (lastEventEnd < 0) continue;
        const completeEvents = buffer.slice(0, lastEventEnd + 2);
        buffer = buffer.slice(lastEventEnd + 2);
        processSseEvents(completeEvents, callbacks, finishOnce);
      }
    })
    .catch(reportError);

  return { abort: () => { finished = true; controller.abort(); } };
}

/** 解析 SSE 事件（支持 event: + data: 格式） */
function processSseEvents(
  text: string,
  callbacks: AgentStreamCallbacks,
  finish: () => void,
) {
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const blocks = normalized.split("\n\n");

  for (const block of blocks) {
    if (!block.trim()) continue;
    let eventType = "text";
    const dataLines: string[] = [];

    for (const line of block.split("\n")) {
      if (line.startsWith("event:")) {
        eventType = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5));
      }
    }

    const data = dataLines.join("\n");
    if (!data) continue;

    if (eventType === "done" || data === "[DONE]") {
      finish();
      return;
    }

    try {
      const parsed = JSON.parse(data);
      switch (eventType) {
        case "text":
          callbacks.onText(parsed.content ?? data);
          break;
        case "tool_call":
          callbacks.onToolCall(parsed.tool, parsed.args);
          break;
        case "tool_result":
          callbacks.onToolResult(parsed.tool, parsed.result);
          break;
        case "confirm":
          callbacks.onConfirm(parsed);
          break;
        case "error":
          callbacks.onError(parsed.message ?? data);
          break;
        default:
          callbacks.onText(data);
      }
    } catch {
      // 非 JSON，当作纯文本
      callbacks.onText(data);
    }
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/** 确认/取消写操作 */
export function agentConfirm(data: AgentConfirmPayload): Promise<void> {
  return post("/agent/confirm", data);
}

/** 获取当前用户可用工具列表 */
export function fetchAgentTools(): Promise<AgentTool[]> {
  return get("/agent/tools");
}

/** 获取当前用户的 AI 权限覆盖配置 */
export function fetchAgentPermissions(): Promise<AiToolPermissionVO> {
  return get("/agent/permissions");
}

/** 批量更新用户的 AI 权限 */
export function updateAgentPermissions(
  userId: number,
  permissions: { toolName: string; enabled: boolean }[],
): Promise<void> {
  return put("/agent/permissions", { userId, permissions });
}

/** 获取所有用户的 AI 权限配置（管理员） */
export function fetchAllAgentPermissions(): Promise<AiToolPermissionVO[]> {
  return get("/agent/permissions/all");
}
