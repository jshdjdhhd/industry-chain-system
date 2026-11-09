import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import EnterpriseProfilePanel from "./EnterpriseProfilePanel.vue";
import {
  fetchEnterpriseProfileAnalysis,
  fetchEnterpriseRadar,
} from "@/api/enterprise";

const push = vi.fn();
const resize = vi.fn();
const dispose = vi.fn();
const setOption = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("element-plus", () => ({
  ElMessage: { error: vi.fn() },
}));

vi.mock("@element-plus/icons-vue", () => ({ Refresh: {} }));

vi.mock("echarts/core", () => ({
  use: vi.fn(),
  init: vi.fn(() => ({ setOption, resize, dispose })),
}));

vi.mock("echarts/charts", () => ({ RadarChart: {} }));
vi.mock("echarts/components", () => ({
  GridComponent: {},
  LegendComponent: {},
  TooltipComponent: {},
}));
vi.mock("echarts/renderers", () => ({ CanvasRenderer: {} }));
vi.mock("@/api/enterprise", () => ({
  fetchEnterpriseProfileAnalysis: vi.fn(),
  fetchEnterpriseRadar: vi.fn(),
}));

const radar = {
  metrics: [
    {
      key: "patentCount",
      name: "专利数量",
      value: 80,
      rawValue: "4 条",
      description: "根据企业专利信息条目数计算",
    },
  ],
};

const analysis = {
  enterpriseId: 1,
  enterpriseName: "宁德时代",
  radar: { metrics: [] },
  similarEnterprises: [
    {
      enterpriseId: 2,
      enterpriseName: "比亚迪",
      similarity: 0.91,
      similarityPercent: 91,
      industry: "整车制造",
      mainProducts: "新能源汽车",
      reasons: ["共享标签：新能源"],
    },
  ],
  generatedAt: "2026-07-07T10:00:00",
};

function mountPanel() {
  return mount(EnterpriseProfilePanel, {
    props: { enterpriseId: 1 },
    global: {
      directives: {
        loading: {},
      },
      stubs: {
        "el-select": {
          props: ["modelValue"],
          emits: ["update:modelValue"],
          template: `<select data-test="limit" :value="modelValue" @change="$emit('update:modelValue', Number($event.target.value))"><slot /></select>`,
        },
        "el-option": {
          props: ["label", "value"],
          template: `<option :value="value">{{ label }}</option>`,
        },
        "el-button": {
          template: `<button data-test="refresh" @click="$emit('click')"><slot /></button>`,
        },
        "el-alert": {
          props: ["title"],
          template: `<div role="alert">{{ title }}</div>`,
        },
      },
    },
  });
}

describe("EnterpriseProfilePanel", () => {
  beforeEach(() => {
    vi.mocked(fetchEnterpriseRadar).mockReset();
    vi.mocked(fetchEnterpriseProfileAnalysis).mockReset();
    vi.mocked(fetchEnterpriseRadar).mockResolvedValue(structuredClone(radar));
    vi.mocked(fetchEnterpriseProfileAnalysis).mockResolvedValue(
      structuredClone(analysis),
    );
    push.mockReset();
    resize.mockReset();
    dispose.mockReset();
    setOption.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("loads radar and similar enterprises on mount", async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(fetchEnterpriseRadar).toHaveBeenCalledWith(1);
    expect(fetchEnterpriseProfileAnalysis).toHaveBeenCalledWith(1, 5);
    expect(wrapper.text()).toContain("专利数量");
    expect(wrapper.text()).toContain("比亚迪");
    expect(setOption).toHaveBeenCalled();
  });

  it("renders radar before similar enterprises arrive", async () => {
    vi.mocked(fetchEnterpriseProfileAnalysis).mockReturnValue(
      new Promise(() => undefined),
    );
    const wrapper = mountPanel();
    await flushPromises();

    expect(fetchEnterpriseRadar).toHaveBeenCalledWith(1);
    expect(wrapper.text()).toContain("专利数量");
    expect(setOption).toHaveBeenCalled();
    expect(wrapper.text()).toContain("相似企业加载中");
    expect(wrapper.text()).not.toContain("比亚迪");
  });

  it("renders empty states when no metrics or similar enterprises", async () => {
    vi.mocked(fetchEnterpriseRadar).mockResolvedValue({ metrics: [] });
    vi.mocked(fetchEnterpriseProfileAnalysis).mockResolvedValue({
      ...analysis,
      similarEnterprises: [],
    });

    const wrapper = mountPanel();
    await flushPromises();

    expect(wrapper.text()).toContain("暂无画像数据");
    expect(wrapper.text()).toContain("暂无相似企业");
  });

  it("reloads similar enterprises with selected limit, not radar", async () => {
    const wrapper = mountPanel();
    await flushPromises();

    await wrapper.find('[data-test="limit"]').setValue("10");
    await flushPromises();

    expect(fetchEnterpriseRadar).toHaveBeenCalledTimes(1);
    expect(fetchEnterpriseProfileAnalysis).toHaveBeenLastCalledWith(1, 10);
  });

  it("deduplicates refresh while loading", async () => {
    let resolveSimilar: (value: typeof analysis) => void = () => undefined;
    vi.mocked(fetchEnterpriseProfileAnalysis).mockReturnValue(
      new Promise((resolve) => {
        resolveSimilar = resolve;
      }),
    );
    const wrapper = mountPanel();

    await wrapper.find('[data-test="refresh"]').trigger("click");
    resolveSimilar(structuredClone(analysis));
    await flushPromises();

    expect(fetchEnterpriseProfileAnalysis).toHaveBeenCalledTimes(1);
    expect(fetchEnterpriseRadar).toHaveBeenCalledTimes(1);
  });

  it("navigates to similar enterprise detail", async () => {
    const wrapper = mountPanel();
    await flushPromises();

    await wrapper.find(".similar-item").trigger("click");

    expect(push).toHaveBeenCalledWith("/enterprises/2");
  });

  it("keeps old similar enterprises on refresh failure", async () => {
    vi.mocked(fetchEnterpriseProfileAnalysis)
      .mockResolvedValueOnce(structuredClone(analysis))
      .mockRejectedValueOnce(new Error("刷新失败"));
    const wrapper = mountPanel();
    await flushPromises();

    await wrapper.find('[data-test="refresh"]').trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("比亚迪");
  });

  it("disposes chart on unmount", async () => {
    const wrapper = mountPanel();
    await flushPromises();

    wrapper.unmount();

    expect(dispose).toHaveBeenCalled();
  });
});
