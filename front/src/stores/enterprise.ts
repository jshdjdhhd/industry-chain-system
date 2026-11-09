import { defineStore } from "pinia";
import type { EnterpriseListItem } from "@/types/domain";
import type { EnterpriseQuery } from "@/api/enterprise";

export const useEnterpriseStore = defineStore("enterprise", {
  state: () => ({
    selectedEnterprise: null as EnterpriseListItem | null,
    queryCache: {
      pageNum: 1,
      pageSize: 10,
      keyword: "",
      status: "",
    } as EnterpriseQuery,
  }),
  actions: {
    setSelectedEnterprise(enterprise: EnterpriseListItem | null) {
      this.selectedEnterprise = enterprise;
    },
    setQueryCache(query: EnterpriseQuery) {
      this.queryCache = { ...query };
    },
  },
});
