import {
  computed,
  defineComponent,
  h,
  inject,
  provide,
  type InjectionKey,
  type PropType,
  type Ref,
} from "vue";
import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import NodeKeywordSearch from "./NodeKeywordSearch.vue";
import type { ChainNode } from "@/types/domain";

const chainApiMocks = vi.hoisted(() => ({
  fetchChains: vi.fn(),
  searchNodesByKeyword: vi.fn(),
}));

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}));

vi.mock("@/api/chain", () => chainApiMocks);
vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>();
  return {
    ...actual,
    useRouter: () => routerMocks,
  };
});
vi.mock("./NodeEnterpriseDialog.vue", () => ({
  default: { name: "NodeEnterpriseDialog", template: "<div />" },
}));

const ElInputStub = defineComponent({
  name: "ElInput",
  inheritAttrs: false,
  props: { modelValue: String },
  emits: ["update:modelValue", "keyup"],
  setup(props, { attrs, emit }) {
    return () =>
      h("input", {
        ...attrs,
        value: props.modelValue,
        onInput: (event: Event) =>
          emit("update:modelValue", (event.target as HTMLInputElement).value),
        onKeyup: (event: KeyboardEvent) => emit("keyup", event),
      });
  },
});

const ElSelectStub = defineComponent({
  name: "ElSelect",
  inheritAttrs: false,
  props: { modelValue: Number },
  emits: ["update:modelValue"],
  setup(props, { attrs, emit, slots }) {
    return () =>
      h(
        "select",
        {
          ...attrs,
          value: props.modelValue ?? "",
          onChange: (event: Event) => {
            const value = (event.target as HTMLSelectElement).value;
            emit("update:modelValue", value ? Number(value) : undefined);
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
    value: [String, Number] as PropType<string | number>,
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

const ElFormItemStub = defineComponent({
  name: "ElFormItem",
  props: { error: String },
  setup(props, { slots }) {
    return () =>
      h("div", [
        slots.default?.(),
        props.error ? h("span", props.error) : null,
      ]);
  },
});

const tableRowKey: InjectionKey<Readonly<Ref<ChainNode | undefined>>> =
  Symbol("searchTableRow");

const ElTableStub = defineComponent({
  name: "ElTable",
  props: {
    data: {
      type: Array as PropType<ChainNode[]>,
      default: () => [],
    },
    emptyText: String,
  },
  setup(props, { slots }) {
    provide(
      tableRowKey,
      computed(() => props.data[0]),
    );
    return () => h("div", slots.default?.());
  },
});

const ElTableColumnStub = defineComponent({
  name: "ElTableColumn",
  setup(_, { slots }) {
    const row = inject(tableRowKey);
    return () =>
      h("div", row?.value ? slots.default?.({ row: row.value }) : undefined);
  },
});

const ElPaginationStub = defineComponent({
  name: "ElPagination",
  emits: ["current-change", "size-change"],
  template: "<div />",
});

function mountPage() {
  return shallowMount(NodeKeywordSearch, {
    global: {
      directives: {
        loading: {
          beforeMount() {},
          updated() {},
        },
      },
      stubs: {
        ElButton: ElButtonStub,
        ElForm: defineComponent({ template: "<form><slot /></form>" }),
        ElFormItem: ElFormItemStub,
        ElInput: ElInputStub,
        ElOption: ElOptionStub,
        ElPagination: ElPaginationStub,
        ElSelect: ElSelectStub,
        ElTable: ElTableStub,
        ElTableColumn: ElTableColumnStub,
        NodeEnterpriseDialog: true,
      },
    },
  });
}

async function search(wrapper: ReturnType<typeof mountPage>, keyword = "电芯") {
  await wrapper.get('[data-test="keyword-input"]').setValue(keyword);
  await wrapper.get('[data-test="search-button"]').trigger("click");
  await flushPromises();
}

describe("NodeKeywordSearch", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    chainApiMocks.fetchChains.mockResolvedValue([
      { id: 1, name: "新能源汽车产业链" },
      { id: 2, name: "集成电路产业链" },
    ]);
    chainApiMocks.searchNodesByKeyword.mockResolvedValue({
      records: [],
      total: 0,
      pageNum: 1,
      pageSize: 20,
    });
  });

  it("should search with the selected chain and pagination defaults", async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-test="keyword-input"]').setValue(" 电芯 ");
    await wrapper.get('[data-test="chain-select"]').setValue("2");
    await wrapper.get('[data-test="search-button"]').trigger("click");
    await flushPromises();

    expect(chainApiMocks.searchNodesByKeyword).toHaveBeenCalledWith({
      keyword: "电芯",
      chainId: 2,
      pageNum: 1,
      pageSize: 20,
    });
  });

  it("should reject a blank keyword without requesting the API", async () => {
    const wrapper = mountPage();
    await flushPromises();

    await search(wrapper, "   ");

    expect(chainApiMocks.searchNodesByKeyword).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("关键词不能为空");
  });

  it("should omit chainId when searching all chains", async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('[data-test="chain-select"]').setValue("2");
    await wrapper.get('[data-test="chain-select"]').setValue("0");
    await search(wrapper);

    expect(chainApiMocks.searchNodesByKeyword).toHaveBeenCalledWith(
      expect.objectContaining({ chainId: undefined }),
    );
  });

  it("should render results with chain names and an empty definition placeholder", async () => {
    chainApiMocks.searchNodesByKeyword.mockResolvedValue({
      records: [
        {
          id: 12,
          chainId: 2,
          name: "晶圆制造",
          keywords: "晶圆,芯片",
          definition: null,
          levelNo: 2,
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 20,
    });
    const wrapper = mountPage();
    await flushPromises();

    await search(wrapper, "晶圆");

    expect(wrapper.findComponent(ElTableStub).props("data")).toHaveLength(1);
    expect(wrapper.text()).toContain("集成电路产业链");
    expect(wrapper.text()).toContain("—");
  });

  it("should request the same filters when the page changes", async () => {
    const wrapper = mountPage();
    await flushPromises();
    await search(wrapper);

    wrapper.findComponent(ElPaginationStub).vm.$emit("current-change", 2);
    await flushPromises();

    expect(chainApiMocks.searchNodesByKeyword).toHaveBeenLastCalledWith({
      keyword: "电芯",
      chainId: undefined,
      pageNum: 2,
      pageSize: 20,
    });
  });

  it("should expose the backend-empty result text after a search", async () => {
    const wrapper = mountPage();
    await flushPromises();
    await search(wrapper);

    expect(wrapper.findComponent(ElTableStub).props("emptyText")).toBe(
      "未找到匹配节点",
    );
  });

  it("should navigate to the matching chain tree", async () => {
    chainApiMocks.searchNodesByKeyword.mockResolvedValue({
      records: [{ id: 12, chainId: 2, name: "晶圆制造" }],
      total: 1,
      pageNum: 1,
      pageSize: 20,
    });
    const wrapper = mountPage();
    await flushPromises();
    await search(wrapper, "晶圆");

    await wrapper.get('[data-test="go-tree-12"]').trigger("click");

    expect(routerMocks.push).toHaveBeenCalledWith({
      path: "/chains",
      query: { chainId: "2" },
    });
  });
});
