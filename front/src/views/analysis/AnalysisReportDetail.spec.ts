import { defineComponent, h } from "vue";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AnalysisReportDetail from "./AnalysisReportDetail.vue";
import type { AnalysisReportDetail as ReportDetail } from "@/types/domain";

const analysisApiMocks = vi.hoisted(() => ({
  fetchAnalysisReportDetail: vi.fn(),
  retryAnalysisReport: vi.fn(),
  downloadAnalysisReport: vi.fn(),
}));

const elementPlusMocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}));

const routeMocks = vi.hoisted(() => ({
  params: { id: "100" },
}));

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}));

vi.mock("@/api/analysis", () => analysisApiMocks);
vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return {
    ...actual,
    useRoute: () => routeMocks,
    useRouter: () => routerMocks,
  };
});
vi.mock("element-plus", () => ({
  ElMessage: {
    success: elementPlusMocks.success,
    error: elementPlusMocks.error,
  },
  ElMessageBox: {
    confirm: elementPlusMocks.confirm,
  },
}));

const ElButtonStub = defineComponent({
  name: "ElButton",
  inheritAttrs: false,
  props: { loading: Boolean },
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

const ElIconStub = defineComponent({
  name: "ElIcon",
  setup(_, { slots }) {
    return () => h("span", slots.default?.());
  },
});

const ElEmptyStub = defineComponent({
  name: "ElEmpty",
  props: { description: String, imageSize: Number },
  setup(props) {
    return () => h("div", { class: "el-empty-stub" }, props.description);
  },
});

function mountDetail() {
  return shallowMount(AnalysisReportDetail, {
    global: {
      directives: {
        loading: { beforeMount() {}, updated() {} },
      },
      stubs: {
        ElButton: ElButtonStub,
        ElTag: ElTagStub,
        ElAlert: ElAlertStub,
        ElIcon: ElIconStub,
        ElEmpty: ElEmptyStub,
      },
    },
  });
}

const completedDetail: ReportDetail = {
  id: 100,
  chainId: 1,
  chainName: "新能源汽车产业链",
  reportType: "HEALTH_ANALYSIS",
  scopeType: "ALL_CHAIN",
  status: "COMPLETED",
  title: "健康分析报告",
  contentHtml: "<h1>健康分析报告</h1><p>整体健康度 82 分。</p>",
  contentMarkdown: "# 健康分析报告",
  createdAt: "2026-07-01 10:00:00",
  completedAt: "2026-07-01 10:01:30",
};

const failedDetail: ReportDetail = {
  id: 101,
  chainId: 1,
  chainName: "新能源汽车产业链",
  reportType: "HEALTH_ANALYSIS",
  scopeType: "SELECTED_NODES",
  status: "FAILED",
  errorMessage: "大模型调用超时",
  createdAt: "2026-07-02 09:20:00",
};

const processingDetail: ReportDetail = {
  id: 102,
  chainId: 1,
  chainName: "新能源汽车产业链",
  reportType: "HEALTH_ANALYSIS",
  scopeType: "ALL_CHAIN",
  status: "PROCESSING",
  createdAt: "2026-07-03 08:00:00",
};

describe("AnalysisReportDetail", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders the HTML preview in an isolated iframe for a completed report", async () => {
    analysisApiMocks.fetchAnalysisReportDetail.mockResolvedValue(completedDetail);
    const wrapper = mountDetail();
    await flushPromises();

    const iframe = wrapper.find(".analysis-report-detail__iframe");
    expect(iframe.exists()).toBe(true);
    expect(iframe.attributes("srcdoc")).toBe(completedDetail.contentHtml);
    expect(iframe.attributes("sandbox")).toBe("");
    expect(wrapper.text()).toContain("已完成");
  });

  it("shows the failure reason and retry button for a failed report", async () => {
    analysisApiMocks.fetchAnalysisReportDetail.mockResolvedValue(failedDetail);
    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.find(".el-alert-stub").exists()).toBe(true);
    expect(wrapper.text()).toContain("大模型调用超时");
    const retryButton = wrapper.findAll("button").find((btn) =>
      btn.text().includes("重试任务"),
    );
    expect(retryButton).toBeDefined();
  });

  it("retries and navigates to the new task after confirmation", async () => {
    analysisApiMocks.fetchAnalysisReportDetail.mockResolvedValue(failedDetail);
    analysisApiMocks.retryAnalysisReport.mockResolvedValue({
      id: 200,
      chainId: 1,
      status: "PROCESSING",
    });
    elementPlusMocks.confirm.mockResolvedValue(undefined);
    const wrapper = mountDetail();
    await flushPromises();

    const retryButton = wrapper.findAll("button").find((btn) =>
      btn.text().includes("重试任务"),
    );
    if (!retryButton) throw new Error("重试按钮未找到");
    await retryButton.trigger("click");
    await flushPromises();

    expect(analysisApiMocks.retryAnalysisReport).toHaveBeenCalledWith(100);
    expect(routerMocks.push).toHaveBeenCalledWith("/analysis/reports/200");
  });

  it("polls while status is PROCESSING and stops on COMPLETED", async () => {
    analysisApiMocks.fetchAnalysisReportDetail
      .mockResolvedValueOnce(processingDetail)
      .mockResolvedValueOnce(completedDetail);
    const wrapper = mountDetail();
    await flushPromises();

    expect(analysisApiMocks.fetchAnalysisReportDetail).toHaveBeenCalledTimes(1);

    // advance 2s → triggers first poll
    vi.advanceTimersByTime(2000);
    await flushPromises();

    expect(analysisApiMocks.fetchAnalysisReportDetail).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("已完成");

    // advance again → should not poll further (terminal status)
    vi.advanceTimersByTime(4000);
    await flushPromises();
    expect(analysisApiMocks.fetchAnalysisReportDetail).toHaveBeenCalledTimes(2);
  });

  it("stops polling when the component is unmounted", async () => {
    analysisApiMocks.fetchAnalysisReportDetail.mockResolvedValue(processingDetail);
    const wrapper = mountDetail();
    await flushPromises();

    vi.advanceTimersByTime(2000);
    await flushPromises();
    expect(analysisApiMocks.fetchAnalysisReportDetail).toHaveBeenCalledTimes(2);

    wrapper.unmount();

    vi.advanceTimersByTime(6000);
    await flushPromises();
    // no further calls after unmount
    expect(analysisApiMocks.fetchAnalysisReportDetail).toHaveBeenCalledTimes(2);
  });

  it("shows an error alert when loading the detail fails", async () => {
    analysisApiMocks.fetchAnalysisReportDetail.mockRejectedValue(
      new Error("报告不存在"),
    );
    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.find(".el-alert-stub").exists()).toBe(true);
    expect(wrapper.text()).toContain("报告不存在");
  });

  it("stops polling after exceeding the 5 minute max duration", async () => {
    analysisApiMocks.fetchAnalysisReportDetail.mockResolvedValue(processingDetail);
    mountDetail();
    await flushPromises();

    const callsBeforeTimeout =
      analysisApiMocks.fetchAnalysisReportDetail.mock.calls.length;

    // advance past the 5min max duration + one poll interval, flushing
    // microtasks so the async poll chain runs to completion
    await vi.advanceTimersByTimeAsync(5 * 60 * 1000 + 2000);

    const callsAfterTimeout =
      analysisApiMocks.fetchAnalysisReportDetail.mock.calls.length;
    // additional poll calls during the window are expected, but the chain
    // must stop once the max duration is exceeded
    expect(callsAfterTimeout).toBeGreaterThan(callsBeforeTimeout);

    // advancing further must not trigger any more fetch calls
    await vi.advanceTimersByTimeAsync(10_000);
    expect(
      analysisApiMocks.fetchAnalysisReportDetail.mock.calls.length,
    ).toBe(callsAfterTimeout);
  });
});
