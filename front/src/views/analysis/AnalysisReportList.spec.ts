import { defineComponent, h, inject, provide, computed, type InjectionKey, type PropType, type Ref } from "vue";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AnalysisReportList from "./AnalysisReportList.vue";
import type { AnalysisReportTask } from "@/types/domain";

const analysisApiMocks = vi.hoisted(() => ({
  fetchAnalysisReports: vi.fn(),
}));

const chainApiMocks = vi.hoisted(() => ({
  fetchChains: vi.fn(),
}));

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}));

vi.mock("@/api/analysis", () => analysisApiMocks);
vi.mock("@/api/chain", () => chainApiMocks);
vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return {
    ...actual,
    useRouter: () => routerMocks,
  };
});

const ElButtonStub = defineComponent({
  name: "ElButton",
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h("button", attrs, slots.default?.());
  },
});

const ElTagStub = defineComponent({
  name: "ElTag",
  props: { type: String },
  setup(_, { slots }) {
    return () => h("span", slots.default?.());
  },
});

const ElAlertStub = defineComponent({
  name: "ElAlert",
  props: { title: String, type: String, closable: Boolean },
  setup(props) {
    return () => h("div", { class: "el-alert-stub" }, props.title);
  },
});

const ElSelectStub = defineComponent({
  name: "ElSelect",
  inheritAttrs: false,
  emits: ["change", "update:modelValue"],
  setup(_, { slots }) {
    return () => h("div", { class: "el-select-stub" }, slots.default?.());
  },
});

const ElOptionStub = defineComponent({
  name: "ElOption",
  props: { label: String, value: [String, Number] as PropType<string | number> },
  setup() {
    return () => h("div", { class: "el-option-stub" });
  },
});

const ElFormItemStub = defineComponent({
  name: "ElFormItem",
  setup(_, { slots }) {
    return () => h("div", slots.default?.());
  },
});

const tableRowKey: InjectionKey<Readonly<Ref<AnalysisReportTask | undefined>>> =
  Symbol("reportTableRow");

const ElTableStub = defineComponent({
  name: "ElTable",
  props: {
    data: {
      type: Array as PropType<AnalysisReportTask[]>,
      default: () => [],
    },
  },
  setup(props, { slots }) {
    provide(tableRowKey, computed(() => props.data[0]));
    return () => h("div", { class: "el-table-stub" }, slots.default?.());
  },
});

const ElTableColumnStub = defineComponent({
  name: "ElTableColumn",
  props: { prop: String, label: String },
  setup(props, { slots }) {
    const row = inject(tableRowKey);
    return () => {
      if (!row?.value) return h("div");
      if (slots.default) return h("div", slots.default({ row: row.value }));
      const value = props.prop ? (row.value as unknown as Record<string, unknown>)[props.prop] : undefined;
      return h("div", value != null ? String(value) : "");
    };
  },
});

const ElPaginationStub = defineComponent({
  name: "ElPagination",
  emits: ["current-change", "size-change"],
  template: "<div class='el-pagination-stub' />",
});

const AppTableStub = defineComponent({
  name: "AppTable",
  props: {
    data: Array,
    loading: Boolean,
    total: Number,
    page: Number,
    pageSize: Number,
  },
  emits: ["update:page", "update:pageSize"],
  setup(props, { slots }) {
    return () =>
      h("div", { class: "app-table-stub" }, [
        h(ElTableStub, { data: (props.data ?? []) as AnalysisReportTask[] }, slots.default),
        h(ElPaginationStub),
      ]);
  },
});

const SearchBarStub = defineComponent({
  name: "SearchBar",
  setup(_, { slots }) {
    return () => h("div", { class: "search-bar-stub" }, [slots.default?.(), slots.actions?.()]);
  },
});

function mountList() {
  return shallowMount(AnalysisReportList, {
    global: {
      directives: {
        loading: { beforeMount() {}, updated() {} },
      },
      stubs: {
        ElButton: ElButtonStub,
        ElTag: ElTagStub,
        ElAlert: ElAlertStub,
        ElSelect: ElSelectStub,
        ElOption: ElOptionStub,
        ElFormItem: ElFormItemStub,
        ElTable: ElTableStub,
        ElTableColumn: ElTableColumnStub,
        ElPagination: ElPaginationStub,
        AppTable: AppTableStub,
        SearchBar: SearchBarStub,
      },
    },
  });
}

const sampleReport: AnalysisReportTask = {
  id: 5001,
  chainId: 1,
  chainName: "新能源汽车产业链",
  reportType: "HEALTH_ANALYSIS",
  scopeType: "ALL_CHAIN",
  status: "COMPLETED",
  createdAt: "2026-07-01 10:00:00",
  completedAt: "2026-07-01 10:01:30",
};

describe("AnalysisReportList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    chainApiMocks.fetchChains.mockResolvedValue([
      { id: 1, name: "新能源汽车产业链" },
      { id: 2, name: "集成电路产业链" },
    ]);
    analysisApiMocks.fetchAnalysisReports.mockResolvedValue({
      records: [sampleReport],
      total: 1,
      pageNum: 1,
      pageSize: 10,
    });
  });

  it("loads chains and reports on mount", async () => {
    const wrapper = mountList();
    await flushPromises();

    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(1);
    expect(analysisApiMocks.fetchAnalysisReports).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      chainId: undefined,
      status: undefined,
    });
    expect(wrapper.text()).toContain("新能源汽车产业链");
    expect(wrapper.text()).toContain("已完成");
  });

  it("renders an empty state when no reports exist", async () => {
    analysisApiMocks.fetchAnalysisReports.mockResolvedValue({
      records: [],
      total: 0,
      pageNum: 1,
      pageSize: 10,
    });
    const wrapper = mountList();
    await flushPromises();

    expect(wrapper.text()).toContain("报告历史");
    expect(wrapper.find(".el-table-stub").exists()).toBe(true);
  });

  it("shows an error alert when loading reports fails", async () => {
    analysisApiMocks.fetchAnalysisReports.mockRejectedValue(new Error("后端不可用"));
    const wrapper = mountList();
    await flushPromises();

    expect(wrapper.find(".el-alert-stub").exists()).toBe(true);
    expect(wrapper.text()).toContain("后端不可用");
  });

  it("navigates to detail when clicking the view button", async () => {
    const wrapper = mountList();
    await flushPromises();

    const detailButton = wrapper.findAll("button").find((btn) =>
      btn.text().includes("查看详情"),
    );
    if (!detailButton) throw new Error("查看详情 button not found");
    await detailButton.trigger("click");

    expect(routerMocks.push).toHaveBeenCalledWith("/analysis/reports/5001");
  });

  it("filters by status on search", async () => {
    const wrapper = mountList();
    await flushPromises();

    const searchButton = wrapper.findAll("button").find((btn) =>
      btn.text().includes("查询"),
    );
    if (!searchButton) throw new Error("查询 button not found");
    await searchButton.trigger("click");
    await flushPromises();

    expect(analysisApiMocks.fetchAnalysisReports).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      chainId: undefined,
      status: undefined,
    });
  });
});
