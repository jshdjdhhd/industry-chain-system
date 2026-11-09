import { defineComponent, h, type PropType } from "vue";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import NodeAdjustment from "./NodeAdjustment.vue";
import type { ChainNode } from "@/types/domain";

const chainApiMocks = vi.hoisted(() => ({
  fetchChains: vi.fn(),
  fetchNodeTree: vi.fn(),
  moveNode: vi.fn(),
  sortNodes: vi.fn(),
}));

const elementMocks = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  confirm: vi.fn(),
}));

vi.mock("@/api/chain", () => chainApiMocks);
vi.mock("element-plus", () => ({
  ElMessage: {
    success: elementMocks.success,
    warning: elementMocks.warning,
    error: elementMocks.error,
  },
  ElMessageBox: {
    confirm: elementMocks.confirm,
  },
}));

const tree: ChainNode[] = [
  {
    id: 1,
    name: "根节点 A",
    stageType: "UPSTREAM",
    children: [
      {
        id: 2,
        name: "子节点 A1",
        children: [{ id: 3, name: "孙节点 A11", children: [] }],
      },
      { id: 4, name: "子节点 A2", children: [] },
    ],
  },
  { id: 5, name: "根节点 B", children: [] },
];

const ElSelectStub = defineComponent({
  name: "ElSelect",
  inheritAttrs: false,
  props: {
    modelValue: [Number, String] as PropType<number | string | null>,
  },
  emits: ["update:modelValue", "change"],
  setup(props, { attrs, emit, slots }) {
    return () =>
      h(
        "select",
        {
          ...attrs,
          value: props.modelValue ?? "",
          onChange: (event: Event) => {
            const rawValue = (event.target as HTMLSelectElement).value;
            const value = rawValue === "" ? null : Number(rawValue);
            emit("update:modelValue", value);
            emit("change", value);
          },
        },
        slots.default?.(),
      );
  },
});

const ElOptionStub = defineComponent({
  name: "ElOption",
  props: {
    label: String,
    value: [Number, String] as PropType<number | string>,
  },
  setup(props) {
    return () => h("option", { value: props.value }, props.label);
  },
});

const ElButtonStub = defineComponent({
  name: "ElButton",
  inheritAttrs: false,
  setup(_, { attrs, slots }) {
    return () => h("button", attrs, slots.default?.());
  },
});

const ElInputNumberStub = defineComponent({
  name: "ElInputNumber",
  inheritAttrs: false,
  props: { modelValue: Number },
  emits: ["update:modelValue", "change"],
  setup(props, { attrs, emit }) {
    return () =>
      h("input", {
        ...attrs,
        type: "number",
        value: props.modelValue,
        onInput: (event: Event) => {
          const value = Number((event.target as HTMLInputElement).value);
          emit("update:modelValue", value);
          emit("change", value);
        },
      });
  },
});

const ElTreeStub = defineComponent({
  name: "ElTree",
  props: {
    data: {
      type: Array as PropType<ChainNode[]>,
      default: () => [],
    },
  },
  emits: ["node-click"],
  template: "<div />",
});

function mountPage() {
  return shallowMount(NodeAdjustment, {
    global: {
      directives: {
        loading: {
          beforeMount() {},
          updated() {},
        },
      },
      stubs: {
        ElAlert: defineComponent({
          props: { title: String },
          template: "<div>{{ title }}</div>",
        }),
        ElButton: ElButtonStub,
        ElEmpty: defineComponent({
          props: { description: String },
          template: "<div>{{ description }}</div>",
        }),
        ElInputNumber: ElInputNumberStub,
        ElOption: ElOptionStub,
        ElSelect: ElSelectStub,
        ElTree: ElTreeStub,
      },
    },
  });
}

async function selectNode(
  wrapper: ReturnType<typeof mountPage>,
  node: ChainNode,
) {
  wrapper.findComponent(ElTreeStub).vm.$emit("node-click", node);
  await wrapper.vm.$nextTick();
}

describe("NodeAdjustment", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    chainApiMocks.fetchChains.mockResolvedValue([
      { id: 1, name: "产业链 A" },
      { id: 2, name: "产业链 B" },
    ]);
    chainApiMocks.fetchNodeTree.mockResolvedValue(tree);
    chainApiMocks.moveNode.mockResolvedValue({ id: 2, name: "子节点 A1" });
    chainApiMocks.sortNodes.mockResolvedValue(undefined);
    elementMocks.confirm.mockResolvedValue("confirm");
  });

  it("loads the first chain and derives the selected node context", async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(chainApiMocks.fetchChains).toHaveBeenCalledTimes(1);
    expect(chainApiMocks.fetchNodeTree).toHaveBeenCalledWith(1);
    expect(wrapper.findComponent(ElTreeStub).props("data")).toEqual(tree);

    await selectNode(wrapper, tree[0].children![0]);

    expect(wrapper.text()).toContain("子节点 A1");
    expect(wrapper.text()).toContain("根节点 A");
    expect(wrapper.text()).toContain("第 1 位");
    expect(wrapper.text()).toContain("2 个节点");
  });

  it("disables stage movement but keeps sorting available", async () => {
    const wrapper = mountPage();
    await flushPromises();
    await selectNode(wrapper, tree[0]);

    expect(
      wrapper.get('[data-test="target-parent-select"]').attributes("disabled"),
    ).toBeDefined();
    expect(
      wrapper.get('[data-test="preview-move"]').attributes("disabled"),
    ).toBeDefined();
    expect(wrapper.text()).toContain(
      "阶段节点可以改名和排序，但不能移动或删除",
    );
    expect(wrapper.get('[data-test="save-sort"]')).toBeDefined();
  });
  it("uses a valid select sentinel while submitting root moves as null", async () => {
    const wrapper = mountPage();
    await flushPromises();
    await selectNode(wrapper, tree[0].children![0]);

    expect(
      wrapper
        .findAllComponents(ElOptionStub)
        .some((option) => option.props("value") === 0),
    ).toBe(true);

    await wrapper.get('[data-test="target-parent-select"]').setValue("0");
    await wrapper.get('[data-test="preview-move"]').trigger("click");
    await flushPromises();

    expect(chainApiMocks.moveNode).toHaveBeenCalledWith(2, {
      newParentId: null,
      sortOrder: 0,
    });
    expect(chainApiMocks.sortNodes).toHaveBeenCalledWith(
      expect.objectContaining({ parentId: null }),
    );
  });

  it("confirms a move, saves the target sibling order and refreshes the tree", async () => {
    const wrapper = mountPage();
    await flushPromises();
    await selectNode(wrapper, tree[0].children![0]);

    await wrapper.get('[data-test="target-parent-select"]').setValue("5");
    await wrapper.get('[data-test="preview-move"]').trigger("click");
    await flushPromises();

    expect(elementMocks.confirm).toHaveBeenCalledWith(
      expect.stringContaining("子节点 A1"),
      "确认移动节点",
      expect.any(Object),
    );
    expect(elementMocks.confirm.mock.calls[0][0]).toContain("根节点 A");
    expect(elementMocks.confirm.mock.calls[0][0]).toContain("根节点 B");
    expect(elementMocks.confirm.mock.calls[0][0]).toContain("2 个节点");
    expect(chainApiMocks.moveNode).toHaveBeenCalledWith(2, {
      newParentId: 5,
      sortOrder: 0,
    });
    expect(chainApiMocks.sortNodes).toHaveBeenCalledWith({
      parentId: 5,
      nodeOrders: [{ nodeId: 2, sortOrder: 0 }],
    });
    expect(chainApiMocks.fetchNodeTree).toHaveBeenCalledTimes(2);
  });

  it("reports partial success and refreshes when target sorting fails", async () => {
    chainApiMocks.sortNodes.mockRejectedValueOnce(new Error("排序失败"));
    const wrapper = mountPage();
    await flushPromises();
    await selectNode(wrapper, tree[0].children![0]);

    await wrapper.get('[data-test="target-parent-select"]').setValue("5");
    await wrapper.get('[data-test="preview-move"]').trigger("click");
    await flushPromises();

    expect(elementMocks.warning).toHaveBeenCalledWith(
      "节点已移动，但目标位置保存失败",
    );
    expect(chainApiMocks.fetchNodeTree).toHaveBeenCalledTimes(2);
  });

  it("keeps sibling changes local until saving the complete order", async () => {
    const wrapper = mountPage();
    await flushPromises();
    await selectNode(wrapper, tree[0].children![0]);

    await wrapper.get('[data-test="move-down-2"]').trigger("click");

    expect(chainApiMocks.sortNodes).not.toHaveBeenCalled();
    expect(
      wrapper
        .findAll('[data-test="sibling-row"]')
        .map((row) => row.get(".sibling-row__name").text()),
    ).toEqual(["子节点 A2", "子节点 A1"]);

    await wrapper.get('[data-test="save-sort"]').trigger("click");
    await flushPromises();

    expect(chainApiMocks.sortNodes).toHaveBeenCalledWith({
      parentId: 1,
      nodeOrders: [
        { nodeId: 4, sortOrder: 0 },
        { nodeId: 2, sortOrder: 1 },
      ],
    });
  });

  it("requires confirmation before switching chains with an unsaved draft", async () => {
    const wrapper = mountPage();
    await flushPromises();
    await selectNode(wrapper, tree[0].children![0]);
    await wrapper.get('[data-test="move-down-2"]').trigger("click");

    elementMocks.confirm.mockRejectedValueOnce(new Error("cancel"));
    await wrapper.get('[data-test="chain-select"]').setValue("2");
    await flushPromises();
    expect(chainApiMocks.fetchNodeTree).not.toHaveBeenCalledWith(2);

    elementMocks.confirm.mockResolvedValueOnce("confirm");
    await wrapper.get('[data-test="chain-select"]').setValue("2");
    await flushPromises();
    expect(chainApiMocks.fetchNodeTree).toHaveBeenCalledWith(2);
  });

  it("renders the chain empty state", async () => {
    chainApiMocks.fetchChains.mockResolvedValueOnce([]);
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("暂无可调整的产业链");
  });

  it("keeps actions disabled until a node is selected", async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(
      wrapper.get('[data-test="preview-move"]').attributes(),
    ).toHaveProperty("disabled");
    expect(wrapper.get('[data-test="save-sort"]').attributes()).toHaveProperty(
      "disabled",
    );
  });
});
