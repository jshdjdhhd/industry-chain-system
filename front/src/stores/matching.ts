import { defineStore } from "pinia";
import type { MatchingTask } from "@/types/domain";

export const useMatchingStore = defineStore("matching", {
  state: () => ({
    currentTask: null as MatchingTask | null,
    recommending: false,
    activeTab: "batch" as "batch" | "single",
    batchTaskIds: [] as number[],
  }),
  actions: {
    setCurrentTask(task: MatchingTask | null) {
      this.currentTask = task;
    },
    setRecommending(recommending: boolean) {
      this.recommending = recommending;
    },
    setActiveTab(tab: "batch" | "single") {
      this.activeTab = tab;
    },
    setBatchTaskIds(ids: number[]) {
      this.batchTaskIds = ids;
    },
  },
});
