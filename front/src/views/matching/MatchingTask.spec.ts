import { describe, expect, it, vi } from "vitest";

vi.mock("@/api/enterprise", () => ({
  fetchEnterprise: vi.fn(),
  fetchEnterprises: vi
    .fn()
    .mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 200 }),
}));
vi.mock("@/api/chain", () => ({
  fetchChains: vi.fn().mockResolvedValue([]),
  fetchNodeTree: vi.fn().mockResolvedValue([]),
  unlinkEnterpriseFromNode: vi.fn(),
}));
vi.mock("@/api/matching", () => ({
  acceptMatchingResult: vi.fn(),
  cancelMatchingTask: vi.fn(),
  createMatchingTask: vi.fn(),
  fetchInitialMatchingTask: vi.fn(),
  fetchMatchingTask: vi.fn(),
  manualConfirmMatching: vi.fn(),
  rejectMatchingResult: vi.fn(),
  revokeMatchingTask: vi.fn(),
  retryMatchingTask: vi.fn(),
}));
vi.mock("@/api/mode", () => ({ isMockApiEnabled: () => false }));
vi.mock("@/stores/matching", () => ({
  useMatchingStore: () => ({
    setCurrentTask: vi.fn(),
    setRecommending: vi.fn(),
  }),
}));

describe("MatchingTask", () => {
  it("contains AI analysis copy in the component source", async () => {
    // Import the raw SFC source to verify text changes without mounting
    const fs = await import("node:fs");
    const path = await import("node:path");
    const vuePath = path.resolve(__dirname, "./MatchingTask.vue");
    const source = fs.readFileSync(vuePath, "utf-8");

    // Should contain new AI analysis copy
    expect(source).toContain("人工智能分析");
    expect(source).toContain("开始分析");
    expect(source).toContain("分析与建议");
    expect(source).toContain("分析任务已提交");
    expect(source).toContain("人工智能分析已生成");
    expect(source).toContain("AI 分析");

    // Should contain AI analysis UI elements
    expect(source).toContain("matching-panel");
    expect(source).toContain("ai-suggestion-card");
    expect(source).toContain("confidence-ring");
    expect(source).toContain("ai-skeleton");

    // Should have operation logs link (not inline table)
    expect(source).toContain("查看操作日志");
    expect(source).toContain("/matching/logs");

    // 内嵌聊天已迁移为全局助手
    expect(source).not.toContain("与 AI 对话");
    expect(source).not.toContain("aiChatStream");
    expect(source).not.toContain("sendChatMessage");
    expect(source).not.toContain("chatMessages");

    // Should NOT contain old recommendation copy
    expect(source).not.toContain("单企业智能推荐");
    expect(source).not.toContain("生成推荐");
    expect(source).not.toContain("推荐理由");
    expect(source).not.toContain("智能挂链推荐已生成");
    expect(source).not.toContain("推荐任务已提交");

    // 下拉选项使用共享轻量缓存，默认批量 tab 不再加载管理列表和完整节点树
    expect(source).toContain("useOptionsStore");
    expect(source).toContain("loadSingleTabOptions");
    expect(source).toContain("searchEnterpriseOptions");
    expect(source).toContain("window.setTimeout");
    expect(source).not.toContain("fetchEnterprises");
    expect(source).not.toContain("fetchChains");
    expect(source).not.toContain("fetchNodeTree");

    // 真实接口链路不再在智能挂链页自动注入 mock 示例任务。
    expect(source).not.toContain("isMockApiEnabled");
    expect(source).not.toContain("fetchMatchingTask(9001)");
  });
});
