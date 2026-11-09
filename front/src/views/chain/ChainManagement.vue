<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import {
  Delete,
  Edit,
  Link,
  Plus,
  Refresh,
  Search,
} from "@element-plus/icons-vue";
import {
  createChain,
  createNode,
  deleteChain,
  deleteNode,
  fetchChains,
  fetchNodeTree,
  updateChain,
  updateNode,
} from "@/api/chain";
import SearchBar from "@/components/SearchBar.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useChainStore } from "@/stores/chain";
import type {
  ChainNode,
  ChainNodePayload,
  ChainPayload,
  ChainStageType,
  IndustryChain,
} from "@/types/domain";
import NodeEnterpriseDialog from "./NodeEnterpriseDialog.vue";

const route = useRoute();
const chainStore = useChainStore();
const chainLoading = ref(false);
const nodeLoading = ref(false);
const chains = ref<IndustryChain[]>([]);
const nodeTree = ref<ChainNode[]>([]);
const selectedChainId = ref<number>();
const initialRouteApplied = ref(false);
const selectedNode = ref<ChainNode | null>(null);
const chainDialogVisible = ref(false);
const nodeDialogVisible = ref(false);
const nodeEnterpriseVisible = ref(false);
const editingChainId = ref<number>();
const editingNodeId = ref<number>();
const saving = ref(false);
const originalParentId = ref<number | null>(null);
const editingNodeStageType = ref<ChainStageType | null>(null);
const editingNodeIsStage = computed(
  () => editingNodeStageType.value !== null,
);
const chainFormRef = ref<FormInstance>();
const nodeFormRef = ref<FormInstance>();

const chainQuery = reactive({
  keyword: "",
});

const chainForm = reactive<ChainPayload>({
  name: "",
  description: "",
  sortNo: 0,
});

const nodeForm = reactive<ChainNodePayload>({
  parentId: null,
  name: "",
  keywords: "",
  definition: "",
  sortNo: 0,
});

const chainRules: FormRules<ChainPayload> = {
  name: [{ required: true, message: "产业链名称不能为空", trigger: "blur" }],
};

const nodeRules: FormRules<ChainNodePayload> = {
  name: [{ required: true, message: "节点名称不能为空", trigger: "blur" }],
};

const STAGE_LABELS: Record<ChainStageType, string> = {
  UPSTREAM: "上游阶段",
  MIDSTREAM: "中游阶段",
  DOWNSTREAM: "下游阶段",
};

function getStageLabel(node: ChainNode): string {
  return node.stageType ? STAGE_LABELS[node.stageType] : "普通节点";
}
const selectedChain = computed(() =>
  chains.value.find((chain) => chain.id === selectedChainId.value),
);

function parseRouteChainId(value: unknown): number | undefined {
  const rawValue = Array.isArray(value) ? value[0] : value;
  const chainId = Number(rawValue);
  return Number.isInteger(chainId) && chainId > 0 ? chainId : undefined;
}
async function loadChains() {
  chainLoading.value = true;
  try {
    chains.value = await fetchChains(chainQuery);
    const routeChainId = initialRouteApplied.value
      ? undefined
      : parseRouteChainId(route.query.chainId);
    if (
      routeChainId &&
      chains.value.some((chain) => chain.id === routeChainId)
    ) {
      selectedChainId.value = routeChainId;
    } else if (
      !selectedChainId.value ||
      !chains.value.some((chain) => chain.id === selectedChainId.value)
    ) {
      selectedChainId.value = chains.value[0]?.id;
    }
    initialRouteApplied.value = true;
    if (selectedChainId.value) {
      chainStore.setSelectedChain(
        chains.value.find((chain) => chain.id === selectedChainId.value) ??
          null,
      );
      await loadNodeTree();
    }
  } finally {
    chainLoading.value = false;
  }
}

async function loadNodeTree() {
  if (!selectedChainId.value) return;
  nodeLoading.value = true;
  try {
    nodeTree.value = await fetchNodeTree(selectedChainId.value);
    chainStore.setNodeTree(nodeTree.value);
  } finally {
    nodeLoading.value = false;
  }
}

function selectChain(chain: IndustryChain) {
  selectedChainId.value = chain.id;
  selectedNode.value = null;
  chainStore.setSelectedChain(chain);
  chainStore.setSelectedNode(null);
  loadNodeTree();
}

function selectNode(node: ChainNode) {
  selectedNode.value = node;
  chainStore.setSelectedNode(node);
}

function openCreateChain() {
  editingChainId.value = undefined;
  Object.assign(chainForm, { name: "", description: "", sortNo: 0 });
  chainDialogVisible.value = true;
}

function openEditChain(chain: IndustryChain) {
  editingChainId.value = chain.id;
  Object.assign(chainForm, {
    name: chain.name,
    description: chain.description || "",
    sortNo: chain.sortNo ?? 0,
  });
  chainDialogVisible.value = true;
}

async function saveChain() {
  await chainFormRef.value?.validate();
  saving.value = true;
  try {
    if (editingChainId.value) {
      await updateChain(editingChainId.value, chainForm);
      ElMessage.success("产业链已更新");
    } else {
      const created = await createChain(chainForm);
      selectedChainId.value = created.id;
      ElMessage.success("产业链已新增");
    }
    chainDialogVisible.value = false;
    await loadChains();
  } finally {
    saving.value = false;
  }
}

async function removeChain(chain: IndustryChain) {
  await ElMessageBox.confirm(`确认删除产业链“${chain.name}”？`, "删除产业链", {
    confirmButtonText: "删除",
    cancelButtonText: "取消",
    type: "warning",
  });
  await deleteChain(chain.id);
  ElMessage.success("产业链已删除");
  selectedChainId.value = undefined;
  await loadChains();
}

function openCreateNode(parent?: ChainNode) {
  if (!selectedChainId.value) {
    ElMessage.warning("请先选择产业链");
    return;
  }
  editingNodeId.value = undefined;
  originalParentId.value = null;
  editingNodeStageType.value = null;
  Object.assign(nodeForm, {
    parentId: parent?.id ?? selectedNode.value?.id ?? null,
    name: "",
    keywords: "",
    definition: "",
    sortNo: 0,
  });
  nodeDialogVisible.value = true;
}

function openEditNode(node: ChainNode) {
  editingNodeId.value = node.id;
  originalParentId.value = node.parentId ?? null;
  editingNodeStageType.value = node.stageType ?? null;
  Object.assign(nodeForm, {
    parentId: node.parentId ?? null,
    name: node.name,
    keywords: node.keywords || "",
    definition: node.definition || "",
    sortNo: node.sortNo ?? 0,
  });
  nodeDialogVisible.value = true;
}

async function saveNode() {
  if (!selectedChainId.value) return;
  await nodeFormRef.value?.validate();
  saving.value = true;
  try {
    if (editingNodeId.value) {
      const parentId = nodeForm.parentId ?? null;
      await updateNode(editingNodeId.value, {
        ...nodeForm,
        parentId,
        parentChangeRequested:
          parentId !== originalParentId.value,
      });
      ElMessage.success("节点已更新");
    } else {
      await createNode(selectedChainId.value, nodeForm);
      ElMessage.success("节点已新增");
    }
    nodeDialogVisible.value = false;
    await loadChains();
  } catch {
    // Keep dialog open on error
  } finally {
    saving.value = false;
  }
}

async function removeNode(node: ChainNode) {
  if (node.stageType) {
    ElMessage.warning("上下游阶段节点不允许删除");
    return;
  }
  await ElMessageBox.confirm(
    `确认删除节点“${node.name}”？有子节点或已关联企业时后端会阻止。`,
    "删除节点",
    {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning",
    },
  );
  await deleteNode(node.id);
  ElMessage.success("节点已删除");
  selectedNode.value = null;
  await loadChains();
}

function openNodeEnterprises(node: ChainNode) {
  selectedNode.value = node;
  nodeEnterpriseVisible.value = true;
}

watch(
  () => route.query.chainId,
  async (value) => {
    const chainId = parseRouteChainId(value);
    if (!chainId || chainId === selectedChainId.value) return;
    const chain = chains.value.find((item) => item.id === chainId);
    if (!chain) return;

    selectedChainId.value = chainId;
    selectedNode.value = null;
    chainStore.setSelectedChain(chain);
    chainStore.setSelectedNode(null);
    await loadNodeTree();
  },
);

onMounted(loadChains);
</script>

<template>
  <div class="page">
    <div class="page__header">
      <div>
        <h2 class="page__title">产业链管理</h2>
        <p class="page__description">
          维护产业链目录、树形节点和节点企业统计。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="Plus" type="primary" @click="openCreateChain"
          >新增产业链</el-button
        >
        <el-button :icon="Refresh" plain @click="loadChains">刷新</el-button>
      </div>
    </div>

    <div class="chain-page">
      <aside class="surface chain-page__side">
        <SearchBar :model-value="chainQuery">
          <el-form-item label="产业链名称">
            <el-input
              v-model="chainQuery.keyword"
              clearable
              placeholder="搜索产业链"
              @keyup.enter="loadChains"
            />
          </el-form-item>
          <template #actions>
            <el-button :icon="Search" type="primary" @click="loadChains"
              >查询</el-button
            >
          </template>
        </SearchBar>

        <div v-loading="chainLoading" class="chain-list">
          <button
            v-for="chain in chains"
            :key="chain.id"
            class="chain-list__item"
            :class="{ 'is-active': chain.id === selectedChainId }"
            type="button"
            @click="selectChain(chain)"
          >
            <span>
              <strong>{{ chain.name }}</strong>
              <small
                >节点 {{ chain.nodeCount ?? 0 }} · 企业
                {{ chain.enterpriseCount ?? 0 }}</small
              >
            </span>
            <StatusTag :status="chain.status || 'ENABLED'" />
          </button>
          <div v-if="!chains.length" class="empty-note">暂无产业链</div>
        </div>
      </aside>

      <section class="surface surface--flush chain-page__main">
        <div class="chain-page__table-head">
          <div>
            <h3>{{ selectedChain?.name || "节点树" }}</h3>
            <p>
              {{ selectedChain?.description || "选择产业链后维护节点树。" }}
            </p>
          </div>
          <div class="toolbar">
            <el-button
              v-if="selectedChain"
              :icon="Edit"
              plain
              @click="openEditChain(selectedChain)"
            >
              编辑产业链
            </el-button>
            <el-button
              v-if="selectedChain"
              :icon="Delete"
              plain
              type="danger"
              @click="removeChain(selectedChain)"
            >
              删除产业链
            </el-button>
            <el-button :icon="Plus" type="primary" @click="openCreateNode()"
              >新增节点</el-button
            >
          </div>
        </div>

        <el-table
          v-loading="nodeLoading"
          :data="nodeTree"
          border
          default-expand-all
          row-key="id"
          :tree-props="{ children: 'children' }"
          @row-click="selectNode"
        >
          <el-table-column label="节点名称" min-width="210" prop="name" />
          <el-table-column label="节点类型" min-width="108">
            <template #default="{ row }">
              <el-tag v-if="row.stageType" effect="plain">
                {{ getStageLabel(row) }}
              </el-tag>
              <span v-else>普通节点</span>
            </template>
          </el-table-column>          <el-table-column
            label="关键词"
            min-width="160"
            prop="keywords"
            show-overflow-tooltip
          />
          <el-table-column
            label="节点定义"
            min-width="200"
            prop="definition"
            show-overflow-tooltip
          />
          <el-table-column label="层级" min-width="56" prop="levelNo" />
          <el-table-column
            label="直接企业数"
            min-width="88"
            prop="directEnterpriseCount"
          />
          <el-table-column
            label="子树企业数"
            min-width="92"
            prop="subtreeEnterpriseCount"
          />
          <el-table-column label="排序" min-width="56" prop="sortNo" />
          <el-table-column label="状态" min-width="96">
            <template #default="{ row }">
              <StatusTag :status="row.status || 'ENABLED'" />
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="152">
            <template #default="{ row }">
              <div class="row-actions">
                <el-tooltip content="编辑" placement="top">
                  <el-button
                    :icon="Edit"
                    aria-label="编辑"
                    link
                    type="primary"
                    @click.stop="openEditNode(row)"
                  />
                </el-tooltip>
                <el-tooltip content="新增子节点" placement="top">
                  <el-button
                    :icon="Plus"
                    aria-label="新增子节点"
                    link
                    type="primary"
                    @click.stop="openCreateNode(row)"
                  />
                </el-tooltip>
                <el-tooltip content="关联企业" placement="top">
                  <el-button
                    :icon="Link"
                    aria-label="关联企业"
                    link
                    type="success"
                    @click.stop="openNodeEnterprises(row)"
                  />
                </el-tooltip>
                <el-tooltip
                  :content="
                    row.stageType
                      ? '上下游阶段节点不允许删除'
                      : '删除'
                  "
                  placement="top"
                >                  <el-button
                    :icon="Delete"
                    aria-label="删除"
                    :data-test="`delete-node-${row.id}`"
                    :disabled="Boolean(row.stageType)"
                    link
                    type="danger"
                    @click.stop="removeNode(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-dialog
      v-model="chainDialogVisible"
      :title="editingChainId ? '编辑产业链' : '新增产业链'"
      width="560px"
    >
      <el-form
        ref="chainFormRef"
        :model="chainForm"
        :rules="chainRules"
        label-position="top"
      >
        <el-form-item label="产业链名称" prop="name">
          <el-input v-model="chainForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="chainForm.description" :rows="4" type="textarea" />
        </el-form-item>
        <el-form-item label="排序" prop="sortNo">
          <el-input-number v-model="chainForm.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chainDialogVisible = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="saveChain"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      v-model="nodeDialogVisible"
      :title="editingNodeId ? '编辑节点' : '新增节点'"
      width="640px"
    >
      <el-form
        ref="nodeFormRef"
        :model="nodeForm"
        :rules="nodeRules"
        label-position="top"
      >
        <el-form-item label="父节点 ID" prop="parentId">
          <el-input-number
            v-model="nodeForm.parentId"
            data-test="node-parent-id"
            :disabled="Boolean(editingNodeId) && editingNodeIsStage"
            :min="1"
            clearable
          />
          <p
            v-if="editingNodeId && editingNodeIsStage"
            class="form-help"
            data-test="stage-parent-help"
          >
            阶段节点无法修改父节点
          </p>
        </el-form-item>
        <el-form-item label="节点名称" prop="name">
          <el-input v-model="nodeForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input
            v-model="nodeForm.keywords"
            placeholder="电芯,动力电池,锂电池"
          />
        </el-form-item>
        <el-form-item label="节点定义" prop="definition">
          <el-input v-model="nodeForm.definition" :rows="4" type="textarea" />
        </el-form-item>
        <el-form-item label="排序" prop="sortNo">
          <el-input-number v-model="nodeForm.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="saveNode"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <NodeEnterpriseDialog
      v-model="nodeEnterpriseVisible"
      :node="selectedNode"
      @changed="loadChains"
    />
  </div>
</template>

<style scoped>
.chain-page {
  display: grid;
  gap: 16px;
  grid-template-columns: 280px minmax(0, 1fr);
}

.chain-page__side {
  display: grid;
  gap: 14px;
  align-content: start;
}

.chain-list {
  display: grid;
  gap: 8px;
}

.chain-list__item {
  align-items: center;
  background: #ffffff;
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  color: #152033;
  cursor: pointer;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 12px;
  text-align: left;
}

.chain-list__item.is-active {
  background: #eff4ff;
  border-color: #002fa7;
}

.chain-list__item strong,
.chain-list__item small {
  display: block;
}

.chain-list__item small {
  color: #667085;
  margin-top: 4px;
}

.chain-page__table-head {
  align-items: start;
  border-bottom: 1px solid #d9e2ef;
  display: flex;
  justify-content: space-between;
  padding: 16px;
}

.chain-page__table-head h3 {
  font-size: 18px;
  margin: 0;
}

.chain-page__table-head p {
  color: #667085;
  margin: 4px 0 0;
}

.row-actions {
  align-items: center;
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  white-space: nowrap;
}

.row-actions :deep(.el-button) {
  font-size: 13px;
  padding: 0 2px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.form-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
  margin: 6px 0 0;
}

@media (max-width: 980px) {
  .chain-page {
    grid-template-columns: 1fr;
  }
}
</style>
