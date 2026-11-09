import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cancelMatchingTask,
  createBatchMatchingTasks,
  createMatchingTask,
  fetchMatchingTask,
  manualConfirmMatching,
  revokeMatchingTask,
  retryMatchingTask,
} from "./matching";
import { get, post } from "./http";

vi.mock("./http", () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

describe("matching API", () => {
  beforeEach(() => {
    vi.stubEnv("VITE_API_MOCK", "false");
    vi.mocked(get).mockReset();
    vi.mocked(post).mockReset();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("submits matching tasks through the async endpoint", async () => {
    vi.mocked(post).mockResolvedValue({
      taskId: 1,
      enterpriseId: 100,
      chainId: 1,
      status: "PROCESSING",
      results: [],
    });

    await createMatchingTask({ enterpriseId: 100, chainId: 1 });

    expect(post).toHaveBeenCalledWith(
      "/matching/tasks/async",
      { enterpriseId: 100, chainId: 1 },
      { timeout: 15_000 },
    );
  });

  it("submits batch matching tasks through the batch async endpoint", async () => {
    vi.mocked(post).mockResolvedValue({
      enterpriseCount: 2,
      chainCount: 1,
      totalPairs: 2,
      submittedCount: 2,
      tasks: [],
    });

    await createBatchMatchingTasks({
      allEnterprises: true,
      allChains: false,
      chainIds: [1],
    });

    expect(post).toHaveBeenCalledWith(
      "/matching/tasks/batch-async",
      { allEnterprises: true, allChains: false, chainIds: [1] },
      { timeout: 20_000 },
    );
  });

  it("keeps task polling on the task detail endpoint", async () => {
    vi.mocked(get).mockResolvedValue({
      taskId: 1,
      enterpriseId: 100,
      chainId: 1,
      status: "COMPLETED",
      results: [],
    });

    await fetchMatchingTask(1);

    expect(get).toHaveBeenCalledWith("/matching/tasks/1", { timeout: 30_000 });
  });

  it("cancels a processing task through the dedicated endpoint", async () => {
    vi.mocked(post).mockResolvedValue({
      taskId: 1,
      enterpriseId: 100,
      chainId: 1,
      status: "CANCELLED",
      results: [],
    });

    await cancelMatchingTask(1);

    expect(post).toHaveBeenCalledWith("/matching/tasks/1/cancel");
  });

  it("revokes a matching task through the revoke endpoint", async () => {
    vi.mocked(post).mockResolvedValue({
      taskId: 1,
      enterpriseId: 100,
      chainId: 1,
      status: "CANCELLED",
      results: [],
    });

    await revokeMatchingTask(1);

    expect(post).toHaveBeenCalledWith("/matching/tasks/1/revoke");
  });

  it("retries a failed task through the dedicated endpoint", async () => {
    vi.mocked(post).mockResolvedValue({
      taskId: 2,
      enterpriseId: 100,
      chainId: 1,
      status: "PROCESSING",
      results: [],
    });

    await retryMatchingTask(1);

    expect(post).toHaveBeenCalledWith("/matching/tasks/1/retry");
  });

  it("sends task context when manually overriding a recommendation", async () => {
    vi.mocked(post).mockResolvedValue({
      enterpriseId: 100,
      nodeId: 203,
      source: "MANUAL",
      linked: true,
    });

    await manualConfirmMatching({
      enterpriseId: 100,
      nodeId: 203,
      taskId: 1,
      sourceResultId: 10,
      reason: "人工确认更适合电驱系统节点",
    });

    expect(post).toHaveBeenCalledWith("/matching/manual-confirm", {
      enterpriseId: 100,
      nodeId: 203,
      taskId: 1,
      sourceResultId: 10,
      reason: "人工确认更适合电驱系统节点",
    });
  });
});
