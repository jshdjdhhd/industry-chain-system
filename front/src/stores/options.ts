import { ref } from "vue";
import { defineStore } from "pinia";
import { fetchEnterpriseOptions } from "@/api/enterprise";
import { fetchChainNodeCandidates, fetchChainOptions } from "@/api/chain";
import type {
  ChainNodeCandidate,
  ChainOption,
  EnterpriseOption,
} from "@/types/domain";

const TTL_MS = 5 * 60_000;

interface NodeCandidateCacheEntry {
  loadedAt: number;
  items: ChainNodeCandidate[];
}

export const useOptionsStore = defineStore("options", () => {
  const enterpriseOptions = ref<EnterpriseOption[]>([]);
  const chainOptions = ref<ChainOption[]>([]);
  const nodeCandidates = ref<Record<number, NodeCandidateCacheEntry>>({});

  const enterpriseLoadedAt = ref(0);
  const chainLoadedAt = ref(0);

  let enterprisePromise: Promise<EnterpriseOption[]> | null = null;
  let chainPromise: Promise<ChainOption[]> | null = null;
  const nodePromises: Partial<Record<number, Promise<ChainNodeCandidate[]>>> =
    {};

  async function loadEnterpriseOptions(): Promise<EnterpriseOption[]> {
    if (
      enterpriseLoadedAt.value > 0 &&
      Date.now() - enterpriseLoadedAt.value < TTL_MS
    ) {
      return enterpriseOptions.value;
    }
    if (enterprisePromise) return enterprisePromise;

    enterprisePromise = fetchEnterpriseOptions("", 30)
      .then((items) => {
        enterpriseOptions.value = items;
        enterpriseLoadedAt.value = Date.now();
        return items;
      })
      .finally(() => {
        enterprisePromise = null;
      });
    return enterprisePromise;
  }

  async function searchEnterpriseOptions(
    keyword: string,
  ): Promise<EnterpriseOption[]> {
    return fetchEnterpriseOptions(keyword, 30);
  }

  async function loadChainOptions(): Promise<ChainOption[]> {
    if (chainLoadedAt.value > 0 && Date.now() - chainLoadedAt.value < TTL_MS) {
      return chainOptions.value;
    }
    if (chainPromise) return chainPromise;

    chainPromise = fetchChainOptions()
      .then((items) => {
        chainOptions.value = items;
        chainLoadedAt.value = Date.now();
        return items;
      })
      .finally(() => {
        chainPromise = null;
      });
    return chainPromise;
  }

  async function loadNodeCandidates(
    chainId: number,
  ): Promise<ChainNodeCandidate[]> {
    const cached = nodeCandidates.value[chainId];
    if (cached && Date.now() - cached.loadedAt < TTL_MS) {
      return cached.items;
    }
    if (nodePromises[chainId]) return nodePromises[chainId];

    nodePromises[chainId] = fetchChainNodeCandidates(chainId)
      .then((items) => {
        nodeCandidates.value = {
          ...nodeCandidates.value,
          [chainId]: { loadedAt: Date.now(), items },
        };
        return items;
      })
      .finally(() => {
        delete nodePromises[chainId];
      });
    return nodePromises[chainId];
  }

  return {
    enterpriseOptions,
    chainOptions,
    nodeCandidates,
    loadEnterpriseOptions,
    searchEnterpriseOptions,
    loadChainOptions,
    loadNodeCandidates,
  };
});
