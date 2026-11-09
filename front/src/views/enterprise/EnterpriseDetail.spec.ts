import { flushPromises, mount } from "@vue/test-utils";
import { reactive, nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EnterpriseDetail from "./EnterpriseDetail.vue";
import { fetchEnterprise } from "@/api/enterprise";
import { fetchTags } from "@/api/tag";

const routeState = reactive({ params: { id: "1" } });
const push = vi.fn();

vi.mock("vue-router", () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push }),
}));

vi.mock("element-plus", () => ({
  ElMessage: { success: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}));

vi.mock("@element-plus/icons-vue", () => ({
  ArrowLeft: {},
  CircleCheck: {},
  CircleClose: {},
  Clock: {},
  Connection: {},
  DataLine: {},
  Edit: {},
  FolderChecked: {},
  Plus: {},
  Refresh: {},
  Warning: {},
}));

vi.mock("@/api/enterprise", () => ({
  fetchEnterprise: vi.fn(),
  bindEnterpriseTag: vi.fn(),
  unbindEnterpriseTag: vi.fn(),
}));

vi.mock("@/api/tag", () => ({ fetchTags: vi.fn() }));

const enterprise = {
  id: 1,
  name: "宁德时代",
  creditCode: "91350900MA3359X93F",
  legalPerson: "张三",
  industry: "动力电池",
  businessScope: "动力电池研发",
  mainProducts: "动力电池",
  status: "ACTIVE",
  tags: [],
  linkedNodes: [
    {
      nodeId: 102,
      nodeName: "电池制造",
      nodePath: "新能源汽车/中游/电池制造",
      source: "AI",
    },
  ],
  operationLogs: [],
};

function mountDetail() {
  return mount(EnterpriseDetail, {
    global: {
      directives: { loading: {} },
      stubs: {
        StatusTag: { props: ["status"], template: `<span>{{ status }}</span>` },
        EnterpriseProfilePanel: {
          props: ["enterpriseId"],
          template: `<section data-test="profile-panel">profile:{{ enterpriseId }}</section>`,
        },
        "el-button": { template: `<button @click="$emit('click')"><slot /></button>` },
        "el-select": { template: `<div><slot /></div>` },
        "el-option": true,
        "el-tag": { template: `<span><slot /></span>` },
        "el-descriptions": { template: `<dl><slot /></dl>` },
        "el-descriptions-item": { template: `<div><slot /></div>` },
        "el-table": { props: ["data"], template: `<div class="el-table"><slot /></div>` },
        "el-table-column": true,
      },
    },
  });
}

describe("EnterpriseDetail", () => {
  beforeEach(() => {
    routeState.params.id = "1";
    vi.mocked(fetchEnterprise).mockReset();
    vi.mocked(fetchTags).mockReset();
    vi.mocked(fetchEnterprise).mockImplementation((id: number) =>
      Promise.resolve({ ...enterprise, id, name: id === 2 ? "比亚迪" : "宁德时代" }),
    );
    vi.mocked(fetchTags).mockResolvedValue([]);
    push.mockReset();
  });

  it("mounts profile panel between linked nodes and operation logs", async () => {
    const wrapper = mountDetail();
    await flushPromises();

    const text = wrapper.text();
    expect(text.indexOf("已挂接节点")).toBeLessThan(text.indexOf("profile:1"));
    expect(text.indexOf("profile:1")).toBeLessThan(text.indexOf("操作日志"));
  });

  it("reloads detail and passes new enterpriseId when route param changes", async () => {
    const wrapper = mountDetail();
    await flushPromises();

    routeState.params.id = "2";
    await nextTick();
    await flushPromises();

    expect(fetchEnterprise).toHaveBeenLastCalledWith(2);
    expect(wrapper.find('[data-test="profile-panel"]').text()).toBe("profile:2");
  });
});
