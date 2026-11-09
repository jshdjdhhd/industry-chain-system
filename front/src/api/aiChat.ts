import { post } from "@/api/http";
import type {
  AiChatRequestPayload,
  AiChatResponse,
  AiChatStreamCallbacks,
  AiChatStreamHandle,
} from "@/types/domain";

/**
 * 普通 AI 聊天。
 */
export function aiChat(data: AiChatRequestPayload): Promise<AiChatResponse> {
  return post("/matching/ai-chat", data, { timeout: 60_000 });
}

/**
 * 通过 POST SSE 接口发起流式 AI 聊天。
 */
export function aiChatStream(
  data: AiChatRequestPayload,
  callbacks: AiChatStreamCallbacks,
): AiChatStreamHandle {
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
    callbacks.onError(
      error instanceof Error ? error.message : "AI 流式请求失败",
    );
  };

  void fetch("/api/matching/ai-chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      if (!response.body) {
        throw new Error("响应流不可用");
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (!finished) {
        const result = await reader.read();
        if (result.done) {
          // 流自然结束，处理剩余 buffer 后完成
          buffer += decoder.decode();
          if (buffer.trim()) {
            processSseEvents(buffer, callbacks, finishOnce);
          }
          finishOnce();
          return;
        }

        buffer += decoder.decode(result.value, { stream: true });
        // SSE 事件以 \n\n 分隔，只处理已完整的事件
        const lastEventEnd = buffer.lastIndexOf("\n\n");
        if (lastEventEnd < 0) continue;

        const completeEvents = buffer.slice(0, lastEventEnd + 2);
        buffer = buffer.slice(lastEventEnd + 2);
        processSseEvents(completeEvents, callbacks, finishOnce);
      }
    })
    .catch(reportError);

  return {
    abort: () => {
      finished = true;
      controller.abort();
    },
  };
}

/**
 * 按 SSE 事件（\n\n 分隔）解析文本。
 *
 * 同一事件内可能有多行 data:，按 SSE 规范应使用 \n 连接后作为一个 chunk 回调，
 * 避免多行内容（如 markdown 换行）的换行符丢失。
 */
function processSseEvents(
  text: string,
  callbacks: AiChatStreamCallbacks,
  finish: () => void,
) {
  // 规范化换行：\r\n -> \n, \r -> \n
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const events = normalized.split("\n\n");
  for (const event of events) {
    if (!event.trim()) continue;

    const dataLines: string[] = [];
    for (const rawLine of event.split("\n")) {
      if (rawLine.startsWith("data:")) {
        dataLines.push(rawLine.slice(5));
      }
    }
    if (dataLines.length === 0) continue;

    const content = dataLines.join("\n");
    if (content === "[DONE]") {
      finish();
      return;
    }
    if (content) {
      callbacks.onChunk(content);
    }
  }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}
