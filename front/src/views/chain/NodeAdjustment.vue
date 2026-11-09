<template>
  <div class="page node-adjustment-page">
    <div class="page__header">
      <div>
        <h2 class="page__title">节点调整</h2>
        <p class="page__description">
          在当前产业链内移动节点子树，并批量保存同级节点顺序。
        </p>
      </div>
    </div>

    <section class="surface node-adjustment-page__toolbar">
      <label class="toolbar-field">
        <span>产业链</span>
        <el-select
          :model-value="selectedChainId"
          data-test="chain-select"
          :disabled="chainsLoading || moving || sorting"
          placeholder="请选择产业链"
          @change="handleChainChange"
        >
          <el-option
            v-for="chain in chains"
            :key="chain.id"
            :label="chain.name"
            :value="chain.id"
          />
        </el-select>
      </label>
      <el-button
        data-test="refresh-tree"
        :disabled="selectedChainId === null || moving || sorting"
        :loading="treeLoading"
        @click="handleRefresh"
      >
        刷新
      </el-button>
    </section>

    <el-empty
      v-if="!chainsLoading && chains.length === 0"
      description="暂无可调整的产业链"
    />

    <div v-else class="node-adjustment-page__workspace">
      <section class="surface node-adjustment-page__tree-panel">
        <div class="panel-heading">
          <h3>节点树</h3>
          <span>选择需要移动或排序的节点</span>
        </div>
        <div v-loading="treeLoading" class="node-tree">
          <el-empty
            v-if="!treeLoading && nodeTree.length === 0"
            description="当前产业链暂无节点"
          />
          <el-tree
            v-else
            :current-node-key="selectedNodeId"
            :data="nodeTree"
            :props="{ children: 'children', label: 'name' }"
            default-expand-all
            highlight-current
            node-key="id"
            @node-click="handleNodeClick"
          />
        </div>
      </section>

      <section class="node-adjustment-page__operations">
        <div class="surface operation-card">
          <div class="panel-heading">
            <h3>节点移动</h3>
            <span>节点及其整棵子树会一起移动</span>
          </div>

          <div v-if="selectedEntry" class="operation-content">
            <el-alert
              v-if="selectedIsStage"
              :closable="false"
              show-icon
              title="阶段节点可以改名和排序，但不能移动或删除"
              type="info"
            />            <dl class="node-context">
              <div>
                <dt>当前节点</dt>
                <dd>{{ selectedEntry.pathName }}</dd>
              </div>
              <div>
                <dt>原父节点</dt>
                <dd>{{ selectedParentName }}</dd>
              </div>
              <div>
                <dt>原位置</dt>
                <dd>第 {{ selectedEntry.position + 1 }} 位</dd>
              </div>
              <div>
                <dt>影响范围</dt>
                <dd>{{ selectedSubtreeCount }} 个节点</dd>
              </div>
            </dl>

            <label class="operation-field">
              <span>目标父节点</span>
              <el-select
                v-model="targetParentSelection"
                data-test="target-parent-select"
                :disabled="moving || sorting || selectedIsStage"
                @change="resetTargetPosition"
              >
                <el-option label="设为一级节点" :value="ROOT_PARENT_ID" />
                <el-option
                  v-for="entry in validParentOptions"
                  :key="entry.id"
                  :label="entry.pathName"
                  :value="entry.id"
                />
              </el-select>
            </label>

            <label class="operation-field">
              <span>目标位置</span>
              <el-input-number
                v-model="targetPosition"
                data-test="target-position"
                :disabled="moving || sorting || selectedIsStage"
                :max="maxTargetPosition"
                :min="1"
              />
              <small>目标同级组共可放置 {{ maxTargetPosition }} 个位置</small>
            </label>
          </div>
          <div v-else class="operation-placeholder">请从左侧选择节点</div>

          <div class="operation-actions">
            <el-button
              data-test="preview-move"
              :disabled="!selectedEntry || moving || sorting || selectedIsStage"
              :loading="moving"
              type="primary"
              @click="handleMove"
            >
              预览并移动
            </el-button>
          </div>
        </div>

        <div class="surface operation-card">
          <div class="panel-heading">
            <h3>同级排序</h3>
            <span>连续调整后一次性保存完整顺序</span>
          </div>

          <div v-if="selectedEntry" class="sibling-list">
            <div
              v-for="(node, index) in siblingDraft"
              :key="node.id"
              class="sibling-row"
              data-test="sibling-row"
              :class="{ 'is-current': node.id === selectedNodeId }"
            >
              <span class="sibling-row__position">{{ index + 1 }}</span>
              <span class="sibling-row__name">{{ node.name }}</span>
              <div class="sibling-row__actions">
                <el-button
                  :data-test="`move-up-${node.id}`"
                  :disabled="index === 0 || moving || sorting"
                  text
                  @click="reorderSibling(node.id, 'up')"
                >
                  上移
                </el-button>
                <el-button
                  :data-test="`move-down-${node.id}`"
                  :disabled="
                    index === siblingDraft.length - 1 || moving || sorting
                  "
                  text
                  @click="reorderSibling(node.id, 'down')"
                >
                  下移
                </el-button>
              </div>
            </div>
          </div>
          <div v-else class="operation-placeholder">请从左侧选择节点</div>

          <div class="operation-actions">
            <el-button
              :disabled="!sortDirty || moving || sorting"
              @click="discardSortDraft"
            >
              放弃修改
            </el-button>
            <el-button
              data-test="save-sort"
              :disabled="!selectedEntry || !sortDirty || moving || sorting"
              :loading="sorting"
              type="primary"
              @click="saveSort"
            >
              保存排序
            </el-button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { fetchChains, fetchNodeTree, moveNode, sortNodes } from "@/api/chain";
import type { ChainNode, IndustryChain } from "@/types/domain";
import {
  buildSortRequest,
  countSubtreeNodes,
  flattenNodeTree,
  getSiblingNodes,
  getValidParentOptions,
  moveSibling,
} from "./nodeAdjustmentViewModel";

const ROOT_PARENT_ID = 0;

const chains = ref<IndustryChain[]>([]);
const selectedChainId = ref<number | null>(null);
const nodeTree = ref<ChainNode[]>([]);
const selectedNodeId = ref<number | null>(null);
const targetParentSelection = ref(ROOT_PARENT_ID);
const targetPosition = ref(1);
const siblingDraft = ref<ChainNode[]>([]);
const chainsLoading = ref(false);
const treeLoading = ref(false);
const moving = ref(false);
const sorting = ref(false);

const flattenedNodes = computed(() => flattenNodeTree(nodeTree.value));
const selectedEntry = computed(
  () =>
    flattenedNodes.value.find((entry) => entry.id === selectedNodeId.value) ??
    null,
);
const selectedIsStage = computed(
  () => Boolean(selectedEntry.value?.node.stageType),
);
const selectedParentName = computed(() => {
  if (!selectedEntry.value?.parentId) return "一级节点";
  return (
    flattenedNodes.value.find(
      (entry) => entry.id === selectedEntry.value?.parentId,
    )?.name ?? "一级节点"
  );
});
const selectedSubtreeCount = computed(() =>
  selectedEntry.value ? countSubtreeNodes(selectedEntry.value.node) : 0,
);
const targetParentId = computed(() =>
  targetParentSelection.value === ROOT_PARENT_ID
    ? null
    : targetParentSelection.value,
);
const validParentOptions = computed(() =>
  selectedEntry.value
    ? getValidParentOptions(nodeTree.value, selectedEntry.value.id)
    : [],
);
const targetSiblings = computed(() =>
  getSiblingNodes(nodeTree.value, targetParentId.value).filter(
    (node) => node.id !== selectedNodeId.value,
  ),
);
const maxTargetPosition = computed(() => targetSiblings.value.length + 1);
const originalSiblingIds = computed(() => {
  if (!selectedEntry.value) return [];
  return getSiblingNodes(nodeTree.value, selectedEntry.value.parentId).map(
    (node) => node.id,
  );
});
const sortDirty = computed(
  () =>
    siblingDraft.value.map((node) => node.id).join(",") !==
    originalSiblingIds.value.join(","),
);

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "操作失败";
}

function resetSortDraft() {
  siblingDraft.value = selectedEntry.value
    ? getSiblingNodes(nodeTree.value, selectedEntry.value.parentId)
    : [];
}

function resetTargetPosition() {
  targetPosition.value = Math.min(
    Math.max(targetPosition.value, 1),
    maxTargetPosition.value,
  );
}

function selectNode(nodeId: number | null) {
  selectedNodeId.value = nodeId;
  if (!selectedEntry.value) {
    targetParentSelection.value = ROOT_PARENT_ID;
    targetPosition.value = 1;
    siblingDraft.value = [];
    return;
  }
  targetParentSelection.value = selectedEntry.value.parentId ?? ROOT_PARENT_ID;
  targetPosition.value = selectedEntry.value.position + 1;
  resetSortDraft();
}

async function confirmDiscardDraft(): Promise<boolean> {
  if (!sortDirty.value) return true;
  try {
    await ElMessageBox.confirm(
      "当前同级排序尚未保存，是否放弃修改？",
      "放弃未保存排序",
      {
        confirmButtonText: "放弃修改",
        cancelButtonText: "继续编辑",
        type: "warning",
      },
    );
    return true;
  } catch {
    return false;
  }
}

async function loadTree(preserveNodeId: number | null = null) {
  if (selectedChainId.value === null) return;
  treeLoading.value = true;
  try {
    const nextTree = await fetchNodeTree(selectedChainId.value);
    nodeTree.value = nextTree;
    const preserved = flattenNodeTree(nextTree).some(
      (entry) => entry.id === preserveNodeId,
    );
    selectNode(preserved ? preserveNodeId : null);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    treeLoading.value = false;
  }
}

async function loadChains() {
  chainsLoading.value = true;
  try {
    chains.value = await fetchChains();
    selectedChainId.value = chains.value[0]?.id ?? null;
    if (selectedChainId.value !== null) await loadTree();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    chainsLoading.value = false;
  }
}

async function handleChainChange(chainId: number) {
  if (chainId === selectedChainId.value) return;
  if (!(await confirmDiscardDraft())) return;
  selectedChainId.value = chainId;
  selectNode(null);
  await loadTree();
}

async function handleRefresh() {
  if (!(await confirmDiscardDraft())) return;
  await loadTree(selectedNodeId.value);
}

async function handleNodeClick(node: ChainNode) {
  if (
    sortDirty.value &&
    selectedEntry.value?.parentId !==
      flattenedNodes.value.find((entry) => entry.id === node.id)?.parentId &&
    !(await confirmDiscardDraft())
  ) {
    return;
  }
  selectNode(node.id);
}

function reorderSibling(nodeId: number, direction: "up" | "down") {
  siblingDraft.value = moveSibling(siblingDraft.value, nodeId, direction);
}

function discardSortDraft() {
  resetSortDraft();
}

async function saveSort() {
  if (!selectedEntry.value || !sortDirty.value) return;
  sorting.value = true;
  try {
    await sortNodes(
      buildSortRequest(selectedEntry.value.parentId, siblingDraft.value),
    );
    ElMessage.success("同级排序已保存");
    await loadTree(selectedNodeId.value);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    sorting.value = false;
  }
}

function buildTargetOrder(selectedNode: ChainNode): ChainNode[] {
  const targetOrder = [...targetSiblings.value];
  const insertionIndex = Math.min(
    Math.max(targetPosition.value - 1, 0),
    targetOrder.length,
  );
  targetOrder.splice(insertionIndex, 0, selectedNode);
  return targetOrder;
}

async function handleMove() {
  if (selectedIsStage.value) {
    ElMessage.warning("上下游阶段节点不允许移动");
    return;
  }
  const entry = selectedEntry.value;
  if (!entry) return;
  const targetParentName =
    targetParentId.value === null
      ? "一级节点"
      : (flattenedNodes.value.find(
          (candidate) => candidate.id === targetParentId.value,
        )?.name ?? "未知节点");
  const summary = [
    `待移动节点：${entry.name}`,
    `原父节点：${selectedParentName.value}`,
    `原位置：第 ${entry.position + 1} 位`,
    `目标父节点：${targetParentName}`,
    `目标位置：第 ${targetPosition.value} 位`,
    `受影响子树：${selectedSubtreeCount.value} 个节点`,
  ].join("\n");

  try {
    await ElMessageBox.confirm(summary, "确认移动节点", {
      confirmButtonText: "确认移动",
      cancelButtonText: "取消",
      type: "warning",
    });
  } catch {
    return;
  }

  moving.value = true;
  try {
    await moveNode(entry.id, {
      newParentId: targetParentId.value,
      sortOrder: targetPosition.value - 1,
    });
    try {
      await sortNodes(
        buildSortRequest(targetParentId.value, buildTargetOrder(entry.node)),
      );
    } catch {
      ElMessage.warning("节点已移动，但目标位置保存失败");
      await loadTree(entry.id);
      return;
    }
    ElMessage.success("节点移动成功");
    await loadTree(entry.id);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    moving.value = false;
  }
}

onMounted(loadChains);
</script>

<style scoped>
.node-adjustment-page {
  display: grid;
  gap: 16px;
}

.node-adjustment-page__toolbar {
  align-items: end;
  display: flex;
  gap: 12px;
  padding: 16px;
}

.toolbar-field,
.operation-field {
  display: grid;
  gap: 7px;
}

.toolbar-field {
  min-width: 280px;
}

.toolbar-field > span,
.operation-field > span {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

.node-adjustment-page__workspace {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(300px, 0.9fr) minmax(460px, 1.4fr);
}

.node-adjustment-page__tree-panel,
.operation-card {
  min-width: 0;
  padding: 18px;
}

.panel-heading {
  align-items: baseline;
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-heading h3 {
  color: #172033;
  font-size: 17px;
  margin: 0;
}

.panel-heading span {
  color: #718096;
  font-size: 12px;
}

.node-tree {
  min-height: 460px;
}

.node-adjustment-page__operations {
  display: grid;
  gap: 16px;
}

.operation-card {
  display: grid;
  gap: 16px;
}

.operation-content {
  display: grid;
  gap: 14px;
}

.node-context {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.node-context div {
  background: #f7f9fc;
  border: 1px solid #e4eaf2;
  border-radius: 6px;
  padding: 10px 12px;
}

.node-context dt {
  color: #718096;
  font-size: 12px;
}

.node-context dd {
  color: #22324f;
  font-weight: 700;
  margin: 5px 0 0;
}

.operation-field small {
  color: #8492a6;
}

.operation-placeholder {
  color: #8492a6;
  padding: 24px 0;
  text-align: center;
}

.operation-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.sibling-list {
  border: 1px solid #e4eaf2;
  border-radius: 6px;
  overflow: hidden;
}

.sibling-row {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  min-height: 48px;
  padding: 0 12px;
}

.sibling-row + .sibling-row {
  border-top: 1px solid #edf1f6;
}

.sibling-row.is-current {
  background: #eef5ff;
}

.sibling-row__position {
  color: #8492a6;
  text-align: center;
}

.sibling-row__name {
  color: #22324f;
  font-weight: 600;
}

.sibling-row__actions {
  display: flex;
}

@media (max-width: 980px) {
  .node-adjustment-page__workspace {
    grid-template-columns: 1fr;
  }

  .node-tree {
    min-height: 280px;
  }
}

@media (max-width: 720px) {
  .node-adjustment-page__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-field {
    min-width: 0;
  }

  .node-context {
    grid-template-columns: 1fr;
  }
}
</style>
