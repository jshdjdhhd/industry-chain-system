import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { aiChatStream } from "@/api/aiChat";
import type {
  AiChatRequestPayload,
  AiChatStreamCallbacks,
} from "@/types/domain";

function createMockResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
  return new Response(stream, { status });
}

const payload: AiChatRequestPayload = {
  enterpriseId: null,
  chainId: null,
  message: "测试",
  history: [],
};

describe("aiChatStream", () => {
  beforeEach(() => {
    localStorage.setItem("token", "test-token");
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it("解析跨网络分片的 SSE data 事件", async () => {
    const chunks: string[] = [];
    let doneCount = 0;
    const callbacks: AiChatStreamCallbacks = {
      onChunk: (text) => chunks.push(text),
      onDone: () => {
        doneCount += 1;
      },
      onError: vi.fn(),
    };
    vi.mocked(fetch).mockResolvedValue(
      createMockResponse(["data:你", "好\n\ndata:世界\n", "\ndata:[DONE]\n\n"]),
    );

    aiChatStream(payload, callbacks);

    await vi.waitFor(() => expect(doneCount).toBe(1));
    expect(chunks).toEqual(["你好", "世界"]);
    expect(callbacks.onError).not.toHaveBeenCalled();
  });

  it("流结束与 DONE 共享幂等完成状态", async () => {
    const onDone = vi.fn();
    vi.mocked(fetch).mockResolvedValue(createMockResponse(["data:[DONE]\n\n"]));

    aiChatStream(payload, {
      onChunk: vi.fn(),
      onDone,
      onError: vi.fn(),
    });

    await vi.waitFor(() => expect(onDone).toHaveBeenCalledTimes(1));
  });

  it("流自然结束时完成，即使没有 DONE 事件", async () => {
    const onDone = vi.fn();
    const onChunk = vi.fn();
    vi.mocked(fetch).mockResolvedValue(createMockResponse(["data:回复\n\n"]));

    aiChatStream(payload, {
      onChunk,
      onDone,
      onError: vi.fn(),
    });

    await vi.waitFor(() => expect(onDone).toHaveBeenCalledTimes(1));
    expect(onChunk).toHaveBeenCalledWith("回复");
  });

  it("请求携带 Bearer token 与 JSON 请求体", async () => {
    vi.mocked(fetch).mockResolvedValue(createMockResponse([]));

    aiChatStream(payload, {
      onChunk: vi.fn(),
      onDone: vi.fn(),
      onError: vi.fn(),
    });

    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    expect(fetch).toHaveBeenCalledWith(
      "/api/matching/ai-chat/stream",
      expect.objectContaining({
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer test-token",
        },
        body: JSON.stringify(payload),
      }),
    );
  });

  it("HTTP 错误时回调 onError", async () => {
    const onError = vi.fn();
    vi.mocked(fetch).mockResolvedValue(createMockResponse([], 500));

    aiChatStream(payload, {
      onChunk: vi.fn(),
      onDone: vi.fn(),
      onError,
    });

    await vi.waitFor(() => expect(onError).toHaveBeenCalledWith("HTTP 500"));
  });

  it("abort 取消请求且不报告错误", async () => {
    const onError = vi.fn();
    vi.mocked(fetch).mockImplementation((_url, init) => {
      return new Promise((_resolve, reject) => {
        init?.signal?.addEventListener("abort", () => {
          reject(new DOMException("Aborted", "AbortError"));
        });
      });
    });

    const handle = aiChatStream(payload, {
      onChunk: vi.fn(),
      onDone: vi.fn(),
      onError,
    });
    handle.abort();
    await Promise.resolve();
    await Promise.resolve();

    expect(onError).not.toHaveBeenCalled();
  });
});
