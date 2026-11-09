import { defineComponent, reactive } from "vue";
import { flushPromises, shallowMount, type VueWrapper } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DiagnosisTreeNode, IndustryChain } from "@/types/domain";
import ChainDiagnosis from "./ChainDiagnosis.vue";

const chainApiMocks = vi.hoisted(() => ({
  fetchChains: vi.fn(),
}));

const analysisApiMocks = vi.hoisted(() => ({
  fetchDiagnosisTree: vi.fn(),
  createAnalysisReport: vi.fn(),
}));

const elementPlusMocks = vi.hoisted(() => ({
  info: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
  confirm: vi.fn(),
}));

const routeMocks = vi.hoisted(() => ({
  query: {} as { chainId?: string },
}));

const resizeObserverMocks = vi.hoisted(() => {
  const observe = vi.fn();
  const unobserve = vi.fn();
  const disconnect = vi.fn();
  const ResizeObserver = vi.fn(() => ({ observe, unobserve, disconnect }));
  return { ResizeObserver, observe, unobserve, disconnect };
});

const g6Mocks = vi.hoisted(() => {
  const handlers: Record<string, ((event: unknown) => void)[]> = {};
  const graphInstance = {
    data: vi.fn(),
    render: vi.fn(),
    fitView: vi.fn(),
    changeSize: vi.fn(),
    changeData: vi.fn(),
    destroy: vi.fn(),
    get: vi.fn((key: string) => (key === "destroyed" ? false : undefined)),
    getNodes: vi.fn(() => []),
    findById: vi.fn(() => null),
    clearItemStates: vi.fn(),
    setItemState: vi.fn(),
    on: vi.fn((event: string, handler: (event: unknown) => void) => {
      handlers[event] = handlers[event] ?? [];
      handlers[event].push(handler);
    }),
  };
  const TreeGraphCtor = vi.fn(() => graphInstance);
  return {
    graphInstance,
    handlers,
    TreeGraphCtor,
    registerNode: vi.fn(),
    Marker: { expand: "expand", collapse: "collapse" },
  };
});

vi.stubGlobal("ResizeObserver", resizeObserverMocks.ResizeObserver);

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return {
    ...actual,
    useRoute: () => routeMocks,
  };
});

vi.mock("@antv/g6", () => ({
  default: {
    registerNode: g6Mocks.registerNode,
    TreeGraph: g6Mocks.TreeGraphCtor,
    Marker: g6Mocks.Marker,
  },
}));

vi.mock("@antv/g6-core", () => ({}));

vi.mock("@/api/chain", () => chainApiMocks);
vi.mock("@/api/analysis", () => analysisApiMocks);
vi.mock("@/stores/chain", () => ({
  useChainStore: () => ({
    selectedChain: null,
    selectedNode: null,
    nodeTree: [],
    setSelectedChain: vi.fn(),
    setSelectedNode: vi.fn(),
    setNodeTree: vi.fn(),
  }),
}));
vi.mock("element-plus", () => ({
  ElMessage: {
    info: elementPlusMocks.info,
    warning: elementPlusMocks.warning,
    success: elementPlusMocks.success,
  },
  ElMessageBox: {
    confirm: elementPlusMocks.confirm,
  },
}));

const PassthroughStub = defineComponent({
  inheritAttrs: false,
  template: '<div v-bind="$attrs"><slot /></div>',
});

const EmptyStub = defineComponent({
  inheritAttrs: false,
  template: '<div v-bind="$attrs" />',
});

const ElButtonStub = defineComponent({
  inheritAttrs: false,
  emits: ["click"],
  template:
    '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>',
});

const ElInputStub = defineComponent({
  inheritAttrs: false,
  props: { modelValue: String, placeholder: String },
  template: '<div class="el-input-stub" />',
});

function mountChainDiagnosis(): VueWrapper {
  return shallowMount(ChainDiagnosis, {
    global: {
      directives: {
        loading: { beforeMount() {}, updated() {} },
      },
      stubs: {
        ElButton: ElButtonStub,
        ElInput: ElInputStub,
        ElIcon: PassthroughStub,
        ElTag: PassthroughStub,
        RouterLink: EmptyStub,
      },
    },
  });
}

function findButtonByText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll("button").find((item) => item.text() === text);
  if (!button) throw new Error(`Button not found: ${text}`);
  return button;
}

function buildDiagnosisTree(): DiagnosisTreeNode {
  return {
    nodeId: 1,
    nodeName: "新能源汽车产业链",
    nodeLevel: 0,
    directEnterpriseCount: 0,
    subtreeEnterpriseCount: 20,
    healthScore: 82,
    healthLevel: "HEALTHY",
    healthLabel: "健康",
    children: [
      {
        nodeId: 10,
        nodeName: "动力电池",
        nodeLevel: 1,
        directEnterpriseCount: 3,
        subtreeEnterpriseCount: 8,
        healthScore: 70,
        healthLevel: "NORMAL",
        healthLabel: "一般",
        weakReasons: ["产能不足"],
        children: [],
      },
      {
        nodeId: 20,
        nodeName: "电驱动",
        nodeLevel: 1,
        directEnterpriseCount: 0,
        subtreeEnterpriseCount: 0,
        healthLevel: "EMPTY",
        healthLabel: "空白",
        children: [],
      },
    ],
  };
}

describe("ChainDiagnosis", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resizeObserverMocks.observe.mockClear();
    resizeObserverMocks.unobserve.mockClear();
    resizeObserverMocks.disconnect.mockClear();
    routeMocks.query = reactive({});
    Object.keys(g6Mocks.handlers).forEach(
      (key) => delete g6Mocks.handlers[key],
    );
    chainApiMocks.fetchChains.mockResolvedValue([
      {
        id: 1,
        name: "新能源汽车产业链",
        nodeCount: 5,
        enterpriseCount: 20,
      } as IndustryChain,
      {
        id: 2,
        name: "集成电路产业链",
        nodeCount: 3,
        enterpriseCount: 8,
      } as IndustryChain,
    ]);
    analysisApiMocks.fetchDiagnosisTree.mockResolvedValue(buildDiagnosisTree());
    analysisApiMocks.createAnalysisReport.mockResolvedValue({
      taskId: "task-1",
      chainId: 1,
      scopeType: "SELECTED_NODES",
      nodeIds: [10],
      status: "PENDING",
      createdAt: "2026-07-03T00:00:00Z",
    });
  });

  it("loads chains and the diagnosis tree on mount", async () => {
    mountChainDiagnosis();
    await flushPromises();

    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(1);
    expect(analysisApiMocks.fetchDiagnosisTree).toHaveBeenCalledWith(1);
  });

  it("selects the chain from the route query on mount", async () => {
    routeMocks.query = reactive({ chainId: "2" });
    mountChainDiagnosis();
    await flushPromises();

    expect(analysisApiMocks.fetchDiagnosisTree).toHaveBeenLastCalledWith(2);
  });

  it("switches chains when a chain item is clicked", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    const chainButton = wrapper
      .findAll("button")
      .find((button) => button.text().includes("集成电路产业链"));
    if (!chainButton) throw new Error("Chain button not found");
    await chainButton.trigger("click");
    await flushPromises();

    expect(analysisApiMocks.fetchDiagnosisTree).toHaveBeenLastCalledWith(2);
  });

  it("enters report select mode when clicking create report button", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    await findButtonByText(wrapper, "创建报告").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("报告选择模式");
    expect(wrapper.text()).toContain("已选 0 个节点");
  });

  it("toggles node selection via the G6 node:click handler in report mode", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    await findButtonByText(wrapper, "创建报告").trigger("click");
    await flushPromises();

    const handlers = g6Mocks.handlers["node:click"] ?? [];
    expect(handlers.length).toBeGreaterThan(0);
    const model = {
      nodeId: 10,
      nodeName: "动力电池",
      id: "node-10",
      label: "动力电池",
      subLabel: "一般 · 8 家",
      healthLevel: "NORMAL",
      healthLabel: "一般",
      healthScore: 70,
      nodeLevel: 1,
      nodePath: "/10/",
      keywords: null,
      description: null,
      directEnterpriseCount: 3,
      subtreeEnterpriseCount: 8,
      weakReasons: ["产能不足"],
      size: 59,
      fill: "#d9a90f",
      stroke: "#a16207",
      collapsed: false,
      children: [],
    };
    handlers[0]({ item: { getModel: () => model }, target: undefined });

    await flushPromises();
    expect(wrapper.text()).toContain("已选 1 个节点");
  });

  it("shows node detail when clicking a node in browse mode", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    const handlers = g6Mocks.handlers["node:click"] ?? [];
    expect(handlers.length).toBeGreaterThan(0);
    const model = {
      nodeId: 10,
      nodeName: "动力电池",
      id: "node-10",
      label: "动力电池",
      subLabel: "一般 · 8 家",
      healthLevel: "NORMAL",
      healthLabel: "一般",
      healthScore: 70,
      nodeLevel: 1,
      nodePath: "/10/",
      keywords: "battery",
      description: "电池制造",
      directEnterpriseCount: 3,
      subtreeEnterpriseCount: 8,
      weakReasons: ["产能不足"],
      size: 59,
      fill: "#d9a90f",
      stroke: "#a16207",
      collapsed: false,
      children: [],
    };
    handlers[0]({ item: { getModel: () => model }, target: undefined });

    await flushPromises();
    expect(wrapper.text()).toContain("动力电池");
    expect(wrapper.text()).toContain("产能不足");
    expect(wrapper.text()).toContain("70");
  });

  it("calls createAnalysisReport with correct params when generating a report with selection", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    await findButtonByText(wrapper, "创建报告").trigger("click");
    await flushPromises();

    const handlers = g6Mocks.handlers["node:click"] ?? [];
    const model = {
      nodeId: 10,
      nodeName: "动力电池",
      id: "node-10",
      label: "动力电池",
      subLabel: "一般 · 8 家",
      healthLevel: "NORMAL",
      healthLabel: "一般",
      healthScore: 70,
      nodeLevel: 1,
      nodePath: "/10/",
      keywords: null,
      description: null,
      directEnterpriseCount: 3,
      subtreeEnterpriseCount: 8,
      weakReasons: ["产能不足"],
      size: 59,
      fill: "#d9a90f",
      stroke: "#a16207",
      collapsed: false,
      children: [],
    };
    handlers[0]({ item: { getModel: () => model }, target: undefined });
    await flushPromises();

    await findButtonByText(wrapper, "生成报告").trigger("click");
    await flushPromises();

    expect(analysisApiMocks.createAnalysisReport).toHaveBeenCalledTimes(1);
    expect(analysisApiMocks.createAnalysisReport).toHaveBeenCalledWith({
      chainId: 1,
      reportType: "HEALTH_ANALYSIS",
      scopeType: "SELECTED_NODES",
      nodeIds: [10],
    });
    expect(elementPlusMocks.success).toHaveBeenCalled();
  });

  it("does not call createAnalysisReport when generating a report without selection", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    await findButtonByText(wrapper, "创建报告").trigger("click");
    await flushPromises();

    await findButtonByText(wrapper, "生成报告").trigger("click");
    await flushPromises();

    expect(elementPlusMocks.warning).toHaveBeenCalledWith(
      "请先选择需要分析的节点",
    );
    expect(analysisApiMocks.createAnalysisReport).not.toHaveBeenCalled();
  });

  it("passes scopeType=ALL_CHAIN and empty nodeIds when all first-level nodes selected", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    await findButtonByText(wrapper, "创建报告").trigger("click");
    await flushPromises();

    await findButtonByText(wrapper, "全选一级").trigger("click");
    await flushPromises();

    await findButtonByText(wrapper, "生成报告").trigger("click");
    await flushPromises();

    expect(analysisApiMocks.createAnalysisReport).toHaveBeenCalledTimes(1);
    expect(analysisApiMocks.createAnalysisReport).toHaveBeenCalledWith({
      chainId: 1,
      reportType: "HEALTH_ANALYSIS",
      scopeType: "ALL_CHAIN",
      nodeIds: [],
    });
  });

  it("shows an info message when createAnalysisReport rejects (backend not ready)", async () => {
    analysisApiMocks.createAnalysisReport.mockRejectedValueOnce(
      new Error("mock not implemented"),
    );
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    await findButtonByText(wrapper, "创建报告").trigger("click");
    await flushPromises();

    const handlers = g6Mocks.handlers["node:click"] ?? [];
    const model = {
      nodeId: 10,
      nodeName: "动力电池",
      id: "node-10",
      label: "动力电池",
      subLabel: "一般 · 8 家",
      healthLevel: "NORMAL",
      healthLabel: "一般",
      healthScore: 70,
      nodeLevel: 1,
      nodePath: "/10/",
      keywords: null,
      description: null,
      directEnterpriseCount: 3,
      subtreeEnterpriseCount: 8,
      weakReasons: ["产能不足"],
      size: 59,
      fill: "#d9a90f",
      stroke: "#a16207",
      collapsed: false,
      children: [],
    };
    handlers[0]({ item: { getModel: () => model }, target: undefined });
    await flushPromises();

    await findButtonByText(wrapper, "生成报告").trigger("click");
    await flushPromises();

    expect(analysisApiMocks.createAnalysisReport).toHaveBeenCalled();
    expect(elementPlusMocks.info).toHaveBeenCalledWith(
      "报告生成功能将在后续启用",
    );
  });

  it("selects all first-level nodes via the 全选一级 button", async () => {
    const wrapper = mountChainDiagnosis();
    await flushPromises();

    await findButtonByText(wrapper, "创建报告").trigger("click");
    await flushPromises();

    await findButtonByText(wrapper, "全选一级").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("已选 2 个节点");
    expect(wrapper.text()).toContain("整条产业链");
  });
});
