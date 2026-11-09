import { defineStore } from "pinia";
import type { ChainNode, IndustryChain } from "@/types/domain";

export const useChainStore = defineStore("chain", {
  state: () => ({
    selectedChain: null as IndustryChain | null,
    selectedNode: null as ChainNode | null,
    nodeTree: [] as ChainNode[],
  }),
  actions: {
    setSelectedChain(chain: IndustryChain | null) {
      this.selectedChain = chain;
    },
    setSelectedNode(node: ChainNode | null) {
      this.selectedNode = node;
    },
    setNodeTree(nodes: ChainNode[]) {
      this.nodeTree = nodes;
    },
  },
});
