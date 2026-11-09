import {
  computed,
  defineComponent,
  h,
  inject,
  nextTick,
  reactive,
  provide,
  type InjectionKey,
  type PropType,
  type Ref,
} from "vue";
import { flushPromises, shallowMount, type VueWrapper } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ChainManagement from "./ChainManagement.vue";
import type { ChainNode } from "@/types/domain";

const chainApiMocks = vi.hoisted(() => ({
  createChain: vi.fn(),
  createNode: vi.fn(),
  deleteChain: vi.fn(),
  deleteNode: vi.fn(),
  fetchChains: vi.fn(),
  fetchNodeTree: vi.fn(),
  updateChain: vi.fn(),
  updateNode: vi.fn(),
}));

const chainStoreMocks = vi.hoisted(() => ({
  setSelectedChain: vi.fn(),
  setSelectedNode: vi.fn(),
  setNodeTree: vi.fn(),
}));

const elementPlusMocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}));
const routeMocks = vi.hoisted(() => ({
  query: {} as { chainId?: string },
}));

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return {
    ...actual,
    useRoute: () => routeMocks,
  };
});
vi.mock("@/api/chain", () => chainApiMocks);
vi.mock("@/stores/chain", () => ({
  useChainStore: () => chainStoreMocks,
}));
vi.mock("element-plus", () => ({
  ElMessage: {
    success: elementPlusMocks.success,
    warning: elementPlusMocks.warning,
  },
  ElMessageBox: {
    confirm: elementPlusMocks.confirm,
  },
}));

const NodeEnterpriseDialogStub = defineComponent({
  name: "NodeEnterpriseDialog",
  emits: ["changed"],
  template:
    '<button data-test="emit-changed" @click="$emit(\'changed\')">changed</button>',
});

const ElButtonStub = defineComponent({
  inheritAttrs: false,
  template: '<button v-bind="$attrs"><slot /></button>',
});

const ElDialogStub = defineComponent({
  inheritAttrs: false,
  props: {
    modelValue: Boolean,
    title: { type: String, default: "" },
  },
  template:
    '<div v-if="modelValue"><span>{{ title }}</span><slot /><slot name="footer" /></div>',
});

const ElFormStub = defineComponent({
  setup(_, { expose, slots }) {
    expose({
      validate: () => Promise.resolve(),
    });
    return () => h("form", null, slots.default?.());
  },
});

const PassthroughElementPlusStub = defineComponent({
  template: "<div><slot /></div>",
});

const EmptyElementPlusStub = defineComponent({
  template: "<div />",
});

const ElInputNumberStub = defineComponent({
  inheritAttrs: false,
  props: {
    modelValue: {
      type: Number,
      default: null,
    },
    disabled: Boolean,
  },
  emits: ["update:modelValue"],
  template: `
    <input
      v-bind="$attrs"
      type="number"
      :value="modelValue ?? ''"
      :disabled="disabled"
      @input="$emit(
        'update:modelValue',
        $event.target.value === ''
          ? null
          : Number($event.target.value)
      )"
    />
  `,
});

const tableRowKey: InjectionKey<Readonly<Ref<ChainNode | undefined>>> =
  Symbol("tableRow");

const ElTableStub = defineComponent({
  props: {
    data: {
      type: Array as PropType<ChainNode[]>,
      default: () => [],
    },
  },
  setup(props, { slots }) {
    provide(
      tableRowKey,
      computed(() => props.data[0]),
    );
    return () => h("div", null, slots.default?.());
  },
});

const ElTableColumnStub = defineComponent({
  setup(_, { slots }) {
    const row = inject(tableRowKey);
    return () =>
      h(
        "div",
        null,
        row?.value ? slots.default?.({ row: row.value }) : undefined,
      );
  },
});

function mountChainManagement(): VueWrapper {
  return shallowMount(ChainManagement, {
    global: {
      directives: {
        loading: {
          beforeMount() {},
          updated() {},
        },
      },
      stubs: {
        ElButton: ElButtonStub,
        ElDialog: ElDialogStub,
        ElForm: ElFormStub,
        ElFormItem: PassthroughElementPlusStub,
        ElInput: EmptyElementPlusStub,
        ElInputNumber: ElInputNumberStub,
        ElTable: ElTableStub,
        ElTableColumn: ElTableColumnStub,
        ElTag: PassthroughElementPlusStub,
        ElTooltip: PassthroughElementPlusStub,
        NodeEnterpriseDialog: NodeEnterpriseDialogStub,
      },
    },
  });
}

function getButtonByText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll("button").find((item) => item.text() === text);
  if (!button) {
    throw new Error(`Button not found: ${text}`);
  }
  return button;
}

describe("ChainManagement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    routeMocks.query = reactive({});
    chainApiMocks.fetchChains.mockResolvedValue([
      {
        id: 1,
        name: "新能源汽车产业链",
        nodeCount: 3,
        enterpriseCount: 8,
      },
    ]);
    chainApiMocks.fetchNodeTree.mockResolvedValue([]);
    chainApiMocks.createNode.mockResolvedValue({
      id: 2,
      chainId: 1,
      name: "新增节点",
    });
    chainApiMocks.deleteNode.mockResolvedValue(undefined);
    elementPlusMocks.confirm.mockResolvedValue(undefined);
  });

  it("should reload chain summary when node enterprise relations change", async () => {
    const wrapper = mountChainManagement();

    await flushPromises();
    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(1);

    await wrapper.get('[data-test="emit-changed"]').trigger("click");
    await flushPromises();

    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(2);
  });

  it("should reload chain summary after creating a node", async () => {
    const wrapper = mountChainManagement();
    await flushPromises();
    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(1);

    await getButtonByText(wrapper, "新增节点").trigger("click");
    await getButtonByText(wrapper, "保存").trigger("click");
    await flushPromises();

    expect(chainApiMocks.createNode).toHaveBeenCalledTimes(1);
    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(2);
  });

  it("should reload chain summary after deleting a node", async () => {
    const node: ChainNode = {
      id: 12,
      chainId: 1,
      name: "动力电池",
      children: [],
    };
    chainApiMocks.fetchNodeTree.mockResolvedValue([node]);

    const wrapper = mountChainManagement();
    await flushPromises();
    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(1);

    await wrapper.get('button[aria-label="删除"]').trigger("click");
    await flushPromises();

    expect(chainApiMocks.deleteNode).toHaveBeenCalledWith(node.id);
    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(2);
  });

  it("should label stage nodes and disable stage deletion", async () => {
    const stage: ChainNode = {
      id: 1,
      chainId: 1,
      name: "原材料端",
      stageType: "UPSTREAM",
      children: [],
    };
    chainApiMocks.fetchNodeTree.mockResolvedValue([stage]);

    const wrapper = mountChainManagement();
    await flushPromises();

    expect(wrapper.text()).toContain("上游阶段");
    expect(
      wrapper.get('[data-test="delete-node-1"]').attributes("disabled"),
    ).toBeDefined();
  });
  it("should select the chain from the route query on mount", async () => {
    routeMocks.query = reactive({ chainId: "2" });
    chainApiMocks.fetchChains.mockResolvedValue([
      { id: 1, name: "新能源汽车产业链" },
      { id: 2, name: "集成电路产业链" },
    ]);

    mountChainManagement();
    await flushPromises();

    expect(chainApiMocks.fetchNodeTree).toHaveBeenLastCalledWith(2);
  });

  it("should switch chains when the route query changes", async () => {
    routeMocks.query = reactive({ chainId: "1" });
    chainApiMocks.fetchChains.mockResolvedValue([
      { id: 1, name: "新能源汽车产业链" },
      { id: 2, name: "集成电路产业链" },
    ]);

    mountChainManagement();
    await flushPromises();
    routeMocks.query.chainId = "2";
    await nextTick();
    await flushPromises();

    expect(chainApiMocks.fetchNodeTree).toHaveBeenLastCalledWith(2);
  });

  it("should not reapply the initial route query after a manual chain selection", async () => {
    routeMocks.query = reactive({ chainId: "2" });
    chainApiMocks.fetchChains.mockResolvedValue([
      { id: 1, name: "新能源汽车产业链" },
      { id: 2, name: "集成电路产业链" },
    ]);
    const wrapper = mountChainManagement();
    await flushPromises();

    const firstChainButton = wrapper
      .findAll("button")
      .find((button) => button.text().includes("新能源汽车产业链"));
    if (!firstChainButton) throw new Error("First chain button not found");
    await firstChainButton.trigger("click");
    await flushPromises();
    await wrapper.get('[data-test="emit-changed"]').trigger("click");
    await flushPromises();

    expect(chainApiMocks.fetchNodeTree).toHaveBeenLastCalledWith(1);
  });

  it("should prevent stage parent edits with the expected message", async () => {
    chainApiMocks.fetchNodeTree.mockResolvedValue([{
      id: 10,
      chainId: 1,
      parentId: null,
      name: "上游",
      stageType: "UPSTREAM",
      sortNo: 1,
      children: [],
    }]);

    const wrapper = mountChainManagement();
    await flushPromises();
    await wrapper.get('button[aria-label="编辑"]').trigger("click");

    expect(
      wrapper.get('[data-test="node-parent-id"]')
        .attributes("disabled"),
    ).toBeDefined();
    expect(wrapper.text()).toContain("阶段节点无法修改父节点");
  });

  it("should request an atomic parent change for ordinary nodes", async () => {
    const node: ChainNode = {
      id: 12,
      chainId: 1,
      parentId: 10,
      name: "动力电池",
      sortNo: 2,
      children: [],
    };
    chainApiMocks.fetchNodeTree.mockResolvedValue([node]);
    chainApiMocks.updateNode.mockResolvedValue({
      ...node,
      parentId: 20,
    });

    const wrapper = mountChainManagement();
    await flushPromises();
    await wrapper.get('button[aria-label="编辑"]').trigger("click");
    await wrapper.get('[data-test="node-parent-id"]').setValue("20");
    await getButtonByText(wrapper, "保存").trigger("click");
    await flushPromises();

    expect(chainApiMocks.updateNode).toHaveBeenCalledWith(
      12,
      expect.objectContaining({
        parentId: 20,
        parentChangeRequested: true,
        sortNo: 2,
      }),
    );
  });

  it("should not request a move when the parent is unchanged", async () => {
    const node: ChainNode = {
      id: 12,
      chainId: 1,
      parentId: 10,
      name: "动力电池",
      sortNo: 2,
      children: [],
    };
    chainApiMocks.fetchNodeTree.mockResolvedValue([node]);
    chainApiMocks.updateNode.mockResolvedValue(node);

    const wrapper = mountChainManagement();
    await flushPromises();
    await wrapper.get('button[aria-label="编辑"]').trigger("click");
    await getButtonByText(wrapper, "保存").trigger("click");
    await flushPromises();

    expect(chainApiMocks.updateNode).toHaveBeenCalledWith(
      12,
      expect.objectContaining({
        parentId: 10,
        parentChangeRequested: false,
        sortNo: 2,
      }),
    );
  });

  it("should not show success when the atomic update fails", async () => {
    const node: ChainNode = {
      id: 12,
      chainId: 1,
      parentId: 10,
      name: "动力电池",
      sortNo: 2,
      children: [],
    };
    chainApiMocks.fetchNodeTree.mockResolvedValue([node]);
    chainApiMocks.updateNode.mockRejectedValue(
      new Error("父节点不存在"),
    );

    const wrapper = mountChainManagement();
    await flushPromises();
    await wrapper.get('button[aria-label="编辑"]').trigger("click");
    await wrapper.get('[data-test="node-parent-id"]').setValue("99");
    await getButtonByText(wrapper, "保存").trigger("click");
    await flushPromises();

    expect(elementPlusMocks.success).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("编辑节点");
  });
});
