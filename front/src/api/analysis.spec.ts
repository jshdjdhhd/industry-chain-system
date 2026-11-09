import { beforeEach, describe, expect, it, vi } from "vitest";
import { downloadBlob, get, post } from "./http";
import {
  createAnalysisReport,
  downloadAnalysisReport,
  fetchAnalysisReportDetail,
  fetchAnalysisReports,
  fetchDiagnosisTree,
  mapBackendDiagnosisTree,
  retryAnalysisReport,
} from "./analysis";

vi.mock("./http", () => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  remove: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock("./mode", () => ({
  isMockApiEnabled: () => false,
}));

describe("analysis API backend contract adapters", () => {
  it("maps backend diagnosis tree nodes recursively", () => {
    expect(
      mapBackendDiagnosisTree({
        nodeId: 0,
        nodeName: "新能源汽车产业链",
        nodeLevel: 0,
        directEnterpriseCount: 0,
        subtreeEnterpriseCount: 20,
        healthScore: 82,
        healthLevel: "HEALTHY",
        healthLabel: "健康",
        weakReasons: [],
        children: [
          {
            nodeId: 10,
            nodeName: "动力电池",
            nodePath: "/10/",
            nodeLevel: 1,
            keywords: "battery,lithium",
            description: "电池制造",
            directEnterpriseCount: 3,
            subtreeEnterpriseCount: 8,
            healthScore: 70,
            healthLevel: "NORMAL",
            healthLabel: "一般",
            weakReasons: ["产能不足"],
            children: [
              {
                nodeId: 11,
                nodeName: "隔膜",
                nodeLevel: 2,
                directEnterpriseCount: 1,
                subtreeEnterpriseCount: 1,
                healthLevel: "WEAK",
                children: [],
              },
            ],
          },
        ],
      }),
    ).toEqual({
      nodeId: 0,
      nodeName: "新能源汽车产业链",
      nodePath: null,
      nodeLevel: 0,
      keywords: null,
      description: null,
      directEnterpriseCount: 0,
      subtreeEnterpriseCount: 20,
      healthScore: 82,
      healthLevel: "HEALTHY",
      healthLabel: "健康",
      weakReasons: [],
      children: [
        {
          nodeId: 10,
          nodeName: "动力电池",
          nodePath: "/10/",
          nodeLevel: 1,
          keywords: "battery,lithium",
          description: "电池制造",
          directEnterpriseCount: 3,
          subtreeEnterpriseCount: 8,
          healthScore: 70,
          healthLevel: "NORMAL",
          healthLabel: "一般",
          weakReasons: ["产能不足"],
          children: [
            {
              nodeId: 11,
              nodeName: "隔膜",
              nodePath: null,
              nodeLevel: 2,
              keywords: null,
              description: null,
              directEnterpriseCount: 1,
              subtreeEnterpriseCount: 1,
              healthScore: null,
              healthLevel: "WEAK",
              healthLabel: null,
              weakReasons: [],
              children: [],
            },
          ],
        },
      ],
    });
  });

  it("requests the diagnosis tree endpoint with the chain id", async () => {
    vi.mocked(get).mockResolvedValue({
      nodeId: 1,
      nodeName: "集成电路产业链",
      nodeLevel: 0,
      directEnterpriseCount: 0,
      subtreeEnterpriseCount: 0,
      children: [],
    });

    await expect(fetchDiagnosisTree(1)).resolves.toMatchObject({
      nodeId: 1,
      nodeName: "集成电路产业链",
    });

    expect(get).toHaveBeenCalledWith("/analysis/chains/1/diagnosis-tree");
  });
});

describe("analysis report API backend contract", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("posts the create report request to the reports endpoint", async () => {
    const task = {
      id: 100,
      chainId: 1,
      reportType: "HEALTH_ANALYSIS",
      scopeType: "ALL_CHAIN",
      status: "PROCESSING",
    };
    vi.mocked(post).mockResolvedValue(task);

    await expect(
      createAnalysisReport({
        chainId: 1,
        reportType: "HEALTH_ANALYSIS",
        scopeType: "ALL_CHAIN",
        nodeIds: [],
      }),
    ).resolves.toEqual(task);

    expect(post).toHaveBeenCalledWith("/analysis/reports", {
      chainId: 1,
      reportType: "HEALTH_ANALYSIS",
      scopeType: "ALL_CHAIN",
      nodeIds: [],
    });
  });

  it("fetches reports with pagination and filters", async () => {
    const page = {
      records: [
        { id: 100, chainId: 1, status: "COMPLETED" },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 10,
    };
    vi.mocked(get).mockResolvedValue(page);

    await expect(
      fetchAnalysisReports({ pageNum: 1, pageSize: 10, chainId: 1, status: "COMPLETED" }),
    ).resolves.toEqual(page);

    expect(get).toHaveBeenCalledWith("/analysis/reports", {
      params: {
        pageNum: 1,
        pageSize: 10,
        chainId: 1,
        status: "COMPLETED",
      },
    });
  });

  it("fetches report detail by id", async () => {
    const detail = {
      id: 100,
      chainId: 1,
      status: "COMPLETED",
      contentHtml: "<h1>report</h1>",
    };
    vi.mocked(get).mockResolvedValue(detail);

    await expect(fetchAnalysisReportDetail(100)).resolves.toEqual(detail);
    expect(get).toHaveBeenCalledWith("/analysis/reports/100");
  });

  it("retries a failed report by id", async () => {
    const task = { id: 101, chainId: 1, status: "PROCESSING", retryFromTaskId: 100 };
    vi.mocked(post).mockResolvedValue(task);

    await expect(retryAnalysisReport(100)).resolves.toEqual(task);
    expect(post).toHaveBeenCalledWith("/analysis/reports/100/retry");
  });

  it("downloads a report blob bypassing the response interceptor", async () => {
    const blob = new Blob(["pdf"]);
    vi.mocked(downloadBlob).mockResolvedValue(blob);

    await expect(downloadAnalysisReport(100, "pdf")).resolves.toBe(blob);
    expect(downloadBlob).toHaveBeenCalledWith(
      "/analysis/reports/100/download",
      { params: { format: "pdf" } },
    );
  });

  it("downloads markdown format with the md param", async () => {
    const blob = new Blob(["md"]);
    vi.mocked(downloadBlob).mockResolvedValue(blob);

    await downloadAnalysisReport(100, "md");
    expect(downloadBlob).toHaveBeenCalledWith(
      "/analysis/reports/100/download",
      { params: { format: "md" } },
    );
  });
});
