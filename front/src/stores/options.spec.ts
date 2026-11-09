import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useOptionsStore } from "./options";
import * as enterpriseApi from "@/api/enterprise";
import * as chainApi from "@/api/chain";

vi.mock("@/api/enterprise", () => ({
  fetchEnterpriseOptions: vi.fn(),
}));
vi.mock("@/api/chain", () => ({
  fetchChainOptions: vi.fn(),
  fetchChainNodeCandidates: vi.fn(),
}));

describe("useOptionsStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.useFakeTimers();
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockReset();
    vi.mocked(chainApi.fetchChainOptions).mockReset();
    vi.mocked(chainApi.fetchChainNodeCandidates).mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("caches enterprise options within TTL", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([
      { id: 1, name: "A" },
    ]);
    const store = useOptionsStore();

    await store.loadEnterpriseOptions();
    await store.loadEnterpriseOptions();

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(1);
    expect(store.enterpriseOptions).toEqual([{ id: 1, name: "A" }]);
  });

  it("caches an empty enterprise option result within TTL", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([]);
    const store = useOptionsStore();

    await store.loadEnterpriseOptions();
    await store.loadEnterpriseOptions();

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(1);
  });

  it("re-fetches enterprise options after TTL expires", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([]);
    const store = useOptionsStore();

    await store.loadEnterpriseOptions();
    vi.advanceTimersByTime(6 * 60_000);
    await store.loadEnterpriseOptions();

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(2);
  });

  it("dedupes concurrent enterprise option requests", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([]);
    const store = useOptionsStore();

    await Promise.all([
      store.loadEnterpriseOptions(),
      store.loadEnterpriseOptions(),
    ]);

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(1);
  });

  it("does not cache keyword search results", async () => {
    vi.mocked(enterpriseApi.fetchEnterpriseOptions).mockResolvedValue([]);
    const store = useOptionsStore();

    await store.searchEnterpriseOptions("宁德");
    await store.searchEnterpriseOptions("宁德");

    expect(enterpriseApi.fetchEnterpriseOptions).toHaveBeenCalledTimes(2);
  });

  it("caches chain options within TTL", async () => {
    vi.mocked(chainApi.fetchChainOptions).mockResolvedValue([
      { id: 1, name: "C" },
    ]);
    const store = useOptionsStore();

    await store.loadChainOptions();
    await store.loadChainOptions();

    expect(chainApi.fetchChainOptions).toHaveBeenCalledTimes(1);
  });

  it("caches node candidates per chain ID", async () => {
    vi.mocked(chainApi.fetchChainNodeCandidates).mockResolvedValue([]);
    const store = useOptionsStore();

    await store.loadNodeCandidates(1);
    await store.loadNodeCandidates(1);
    await store.loadNodeCandidates(2);

    expect(chainApi.fetchChainNodeCandidates).toHaveBeenCalledTimes(2);
  });
});
