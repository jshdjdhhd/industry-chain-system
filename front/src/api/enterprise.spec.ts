import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchEnterpriseOptions,
  fetchEnterpriseProfileAnalysis,
  fetchEnterpriseRadar,
} from "./enterprise";
import { get } from "./http";

vi.mock("./http", () => ({ get: vi.fn() }));
vi.mock("./mode", () => ({ isMockApiEnabled: () => false }));

describe("enterprise options API", () => {
  beforeEach(() => vi.mocked(get).mockReset());

  it("requests /enterprises/options with keyword and limit", async () => {
    vi.mocked(get).mockResolvedValue([{ id: 1, name: "宁德时代" }]);

    const result = await fetchEnterpriseOptions("宁德", 20);

    expect(get).toHaveBeenCalledWith("/enterprises/options", {
      params: { keyword: "宁德", limit: 20 },
    });
    expect(result).toEqual([{ id: 1, name: "宁德时代" }]);
  });

  it("omits keyword param when blank", async () => {
    vi.mocked(get).mockResolvedValue([]);

    await fetchEnterpriseOptions("");

    expect(get).toHaveBeenCalledWith("/enterprises/options", {
      params: { keyword: undefined, limit: 20 },
    });
  });

  it("requests profile analysis with default similarLimit", async () => {
    vi.mocked(get).mockResolvedValue({
      enterpriseId: 1,
      enterpriseName: "宁德时代",
      radar: { metrics: [] },
      similarEnterprises: [],
      generatedAt: "2026-07-07T10:00:00",
    });

    await fetchEnterpriseProfileAnalysis(1);

    expect(get).toHaveBeenCalledWith("/enterprises/1/profile-analysis", {
      params: { similarLimit: 5 },
    });
  });

  it("requests profile analysis with custom similarLimit", async () => {
    vi.mocked(get).mockResolvedValue({
      enterpriseId: 1,
      enterpriseName: "宁德时代",
      radar: { metrics: [] },
      similarEnterprises: [],
      generatedAt: "2026-07-07T10:00:00",
    });

    await fetchEnterpriseProfileAnalysis(1, 10);

    expect(get).toHaveBeenCalledWith("/enterprises/1/profile-analysis", {
      params: { similarLimit: 10 },
    });
  });

  it("requests radar metrics without similarLimit", async () => {
    vi.mocked(get).mockResolvedValue({ metrics: [] });

    await fetchEnterpriseRadar(1);

    expect(get).toHaveBeenCalledWith("/enterprises/1/profile-analysis/radar");
  });
});
