import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchChains, fetchNodeTree } from "./chain";
import { fetchDashboardOverview } from "./dashboard";
import { fetchEnterpriseImportRecords, fetchEnterprises } from "./enterprise";
import { fetchMatchingTask } from "./matching";
import { fetchTags } from "./tag";

describe("front-end mock API contract", () => {
  beforeEach(() => {
    vi.stubEnv("VITE_API_MOCK", "true");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("starts without seeded business data", async () => {
    const [enterprises, importRecords, tags, chains, nodes, matchingTask] =
      await Promise.all([
        fetchEnterprises({ pageNum: 1, pageSize: 10 }),
        fetchEnterpriseImportRecords({ pageNum: 1, pageSize: 10 }),
        fetchTags(),
        fetchChains(),
        fetchNodeTree(1),
        fetchMatchingTask(9001),
      ]);

    expect(enterprises).toMatchObject({ total: 0, records: [], pageNum: 1 });
    expect(importRecords).toMatchObject({ total: 0, records: [], pageNum: 1 });
    expect(tags).toEqual([]);
    expect(chains).toEqual([]);
    expect(nodes).toEqual([]);
    expect(matchingTask.results).toEqual([]);
  });

  it("keeps dashboard numbers derived from the current empty mock dataset", async () => {
    const dashboard = await fetchDashboardOverview();
    const metricValue = (label: string) =>
      dashboard.metrics.find((metric) => metric.label === label)?.value;

    expect(metricValue("企业总数")).toBe(0);
    expect(metricValue("已入库企业")).toBe(0);
    expect(metricValue("待智能挂链")).toBe(0);
    expect(metricValue("推荐采纳率")).toBe("0%");
    expect(dashboard.chainDistribution).toEqual([]);
    expect(dashboard.nodeStatistics).toEqual([]);
    expect(dashboard.weeklyProgress).toEqual([]);
    expect(dashboard.recentOperations).toEqual([]);
    expect(dashboard.matchingSummary).toEqual({
      pending: 0,
      accepted: 0,
      rejected: 0,
      manual: 0,
      averageConfidence: 0,
      weeklyRecommendations: 0,
    });
  });
});
