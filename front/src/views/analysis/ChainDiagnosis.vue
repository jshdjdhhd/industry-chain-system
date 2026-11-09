<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
} from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Expand, Refresh, Search } from "@element-plus/icons-vue";
import G6, {
  type IGroup,
  type IShape,
  type Item,
  type TreeGraph,
} from "@antv/g6";
import type { TreeGraphData } from "@antv/g6-core";
import { fetchChains } from "@/api/chain";
import {
  createAnalysisReport,
  fetchDiagnosisTree,
} from "@/api/analysis";
import { useChainStore } from "@/stores/chain";
import type {
  ChainHealthLevel,
  DiagnosisTreeNode,
  IndustryChain,
} from "@/types/domain";
import {
  collectSelectedNodeIds,
  isAllFirstLevelSelected,
  toG6TreeData,
  toggleSelectedNodeId,
  type G6DiagnosisNodeModel,
} from "./chainDiagnosisViewModel";

type DiagnosisMode = "BROWSE" | "REPORT_SELECT";

const route = useRoute();
const chainStore = useChainStore();

const chainLoading = ref(false);
const treeLoading = ref(false);
const chains = ref<IndustryChain[]>([]);
const selectedChainId = ref<number>();
const diagnosisRoot = ref<DiagnosisTreeNode | null>(null);

const mode = ref<DiagnosisMode>("BROWSE");
const selectedNodeIds = ref<Set<number>>(new Set());
const selectedDetail = ref<G6DiagnosisNodeModel | null>(null);
const reportCreating = ref(false);
const initialRouteApplied = ref(false);

const containerRef = ref<HTMLDivElement>();
const graphHolder = shallowRef<TreeGraph | null>(null);
let resizeObserver: ResizeObserver | null = null;

const selectedChain = computed(() =>
  chains.value.find((chain) => chain.id === selectedChainId.value),
);

const selectedCount = computed(() => selectedNodeIds.value.size);

const allFirstLevelSelected = computed(() => {
  if (!diagnosisRoot.value) return false;
  return isAllFirstLevelSelected(diagnosisRoot.value, selectedNodeIds.value);
});

const scopeType = computed<"ALL_CHAIN" | "SELECTED_NODES">(() => {
  if (diagnosisRoot.value && allFirstLevelSelected.value) {
    return "ALL_CHAIN";
  }
  return "SELECTED_NODES";
});

function parseRouteChainId(value: unknown): number | undefined {
  const rawValue = Array.isArray(value) ? value[0] : value;
  const chainId = Number(rawValue);
  return Number.isInteger(chainId) && chainId > 0 ? chainId : undefined;
}

async function loadChains() {
  chainLoading.value = true;
  try {
    chains.value = await fetchChains();
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
      await loadDiagnosisTree();
    }
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    chainLoading.value = false;
  }
}

async function loadDiagnosisTree() {
  if (!selectedChainId.value) return;
  treeLoading.value = true;
  try {
    const root = await fetchDiagnosisTree(selectedChainId.value);
    diagnosisRoot.value = root;
    selectedDetail.value = null;
    selectedNodeIds.value = new Set();
    await nextTick();
    renderGraph();
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    treeLoading.value = false;
  }
}

function selectChain(chain: IndustryChain) {
  if (selectedChainId.value === chain.id) return;
  selectedChainId.value = chain.id;
  chainStore.setSelectedChain(chain);
  exitReportSelectMode();
  loadDiagnosisTree();
}

function ensureGraph() {
  if (graphHolder.value && !graphHolder.value.get("destroyed")) {
    return graphHolder.value;
  }
  if (!containerRef.value) return null;

  G6.registerNode(
    "diagnosis-node",
    {
      draw(cfg, group: IGroup) {
        const model = cfg as unknown as G6DiagnosisNodeModel;
        const size = model.size ?? 32;
        const fill = model.fill ?? "#94a3b8";
        const stroke = model.stroke ?? "#475467";
        const keyShape = group.addShape("circle", {
          attrs: {
            x: 0,
            y: 0,
            r: size / 2,
            fill,
            stroke,
            lineWidth: 2,
            shadowColor: "rgba(15, 23, 42, 0.18)",
            shadowBlur: 10,
            shadowOffsetY: 4,
          },
          name: "circle-shape",
          draggable: true,
        });
        group.addShape("text", {
          attrs: {
            x: size / 2 + 10,
            y: -7,
            text: model.label ?? "",
            fill: "#172033",
            fontSize: 13,
            fontWeight: 600,
            textBaseline: "middle",
          },
          name: "label-shape",
          draggable: true,
        });
        group.addShape("text", {
          attrs: {
            x: size / 2 + 10,
            y: 12,
            text: model.subLabel ?? "",
            fill: "#667085",
            fontSize: 12,
            textBaseline: "middle",
          },
          name: "sublabel-shape",
          draggable: true,
        });
        if (model.children && model.children.length) {
          group.addShape("marker", {
            attrs: {
              x: -size / 2 - 14,
              y: 0,
              r: 9,
              symbol: model.collapsed ? G6.Marker.expand : G6.Marker.collapse,
              stroke: "#475467",
              fill: "#f8fafc",
              lineWidth: 2,
              cursor: "pointer",
            },
            name: "collapse-marker",
            draggable: true,
          });
        }
        return keyShape as unknown as IShape;
      },
      setState(name, value, item?: Item) {
        if (name !== "selected" || !item) return;
        const container = item.getContainer();
        const shape = container.find(
          (element: IShape) => element.get("name") === "circle-shape",
        ) as IShape | undefined;
        if (!shape) return;
        const model = item.getModel() as unknown as G6DiagnosisNodeModel;
        if (value) {
          shape.attr({
            lineWidth: 4,
            stroke: "#1d4ed8",
          });
        } else {
          shape.attr({
            lineWidth: 2,
            stroke: model.stroke ?? "#475467",
          });
        }
      },
    },
    "single-node",
  );

  const graph = new G6.TreeGraph({
    container: containerRef.value,
    width: containerRef.value.clientWidth || 800,
    height: containerRef.value.clientHeight || 600,
    modes: {
      default: [
        "drag-canvas",
        "zoom-canvas",
        {
          type: "collapse-expand",
          trigger: "click",
          shouldBegin: (event: { target?: IShape }) =>
            event.target?.get?.("name") === "collapse-marker",
        },
      ],
    },
    defaultNode: {
      type: "diagnosis-node",
      anchorPoints: [
        [0, 0.5],
        [1, 0.5],
      ],
    },
    defaultEdge: {
      type: "cubic-horizontal",
      style: {
        stroke: "#cbd7e4",
        lineWidth: 1.5,
      },
    },
    layout: {
      type: "compactBox",
      direction: "LR",
      getId: (d: { id: string }) => d.id,
      getHeight: (d: G6DiagnosisNodeModel) => (d.size ?? 32) + 14,
      getWidth: (d: G6DiagnosisNodeModel) =>
        Math.max(180, (d.size ?? 32) + (d.label?.length ?? 0) * 14),
      getVGap: () => 26,
      getHGap: () => 120,
    },
    fitView: false,
    fitViewPadding: [34, 120, 34, 34],
  });

  graph.on("node:click", (event: { item?: Item; target?: IShape }) => {
    const item = event.item;
    if (!item) return;
    const model = item.getModel() as unknown as G6DiagnosisNodeModel;
    // 折叠/展开由 marker 触发，click 事件仍会冒泡，需忽略 marker 命中
    if (event.target?.get?.("name") === "collapse-marker") return;
    handleNodeClick(model);
  });

  graphHolder.value = graph;
  return graph;
}

function renderGraph() {
  const graph = ensureGraph();
  if (!graph || !diagnosisRoot.value) return;
  const data = toG6TreeData(diagnosisRoot.value);
  graph.data(data as unknown as TreeGraphData);
  graph.render();
  graph.fitView(32);
}

function handleNodeClick(model: G6DiagnosisNodeModel) {
  if (mode.value === "BROWSE") {
    setSelectedDetail(model);
  } else {
    // 报告选择模式：切换选中状态
    selectedNodeIds.value = toggleSelectedNodeId(
      selectedNodeIds.value,
      model.nodeId,
    );
    updateNodeSelectedState();
  }
}

function setSelectedDetail(model: G6DiagnosisNodeModel) {
  selectedDetail.value = model;
  const graph = graphHolder.value;
  if (!graph || graph.get("destroyed")) return;
  graph.getNodes().forEach((node: Item) => graph.clearItemStates(node));
  const item = graph.findById(model.id);
  if (item) graph.setItemState(item, "selected", true);
}

function updateNodeSelectedState() {
  const graph = graphHolder.value;
  if (!graph || graph.get("destroyed") || !diagnosisRoot.value) return;
  graph.getNodes().forEach((node: Item) => {
    const model = node.getModel() as unknown as G6DiagnosisNodeModel;
    graph.setItemState(
      node,
      "selected",
      selectedNodeIds.value.has(model.nodeId),
    );
  });
}

function fitView() {
  graphHolder.value?.fitView(28);
}

function resetView() {
  if (!diagnosisRoot.value || !graphHolder.value) return;
  const data = toG6TreeData(diagnosisRoot.value, { collapsedDepth: 999 });
  graphHolder.value.changeData(data as unknown as TreeGraphData);
  graphHolder.value.fitView(32);
}

function enterReportSelectMode() {
  mode.value = "REPORT_SELECT";
  selectedDetail.value = null;
  selectedNodeIds.value = new Set();
  const graph = graphHolder.value;
  if (graph && !graph.get("destroyed")) {
    graph.getNodes().forEach((node: Item) => graph.clearItemStates(node));
  }
}

function exitReportSelectMode() {
  if (selectedNodeIds.value.size > 0) {
    ElMessageBox.confirm("退出选择模式将清空已选节点，是否继续？", "退出选择", {
      confirmButtonText: "退出",
      cancelButtonText: "取消",
      type: "warning",
    })
      .then(() => {
        mode.value = "BROWSE";
        selectedNodeIds.value = new Set();
        updateNodeSelectedState();
      })
      .catch(() => {
        // 用户取消，保持选择模式
      });
  } else {
    mode.value = "BROWSE";
    updateNodeSelectedState();
  }
}

function selectAllFirstLevel() {
  if (!diagnosisRoot.value) return;
  const firstLevel = diagnosisRoot.value.children ?? [];
  selectedNodeIds.value = new Set(firstLevel.map((node) => node.nodeId));
  updateNodeSelectedState();
}

function clearSelection() {
  selectedNodeIds.value = new Set();
  updateNodeSelectedState();
}

async function createReport() {
  if (!selectedChainId.value) {
    ElMessage.warning("请先选择产业链");
    return;
  }
  if (selectedNodeIds.value.size === 0) {
    ElMessage.warning("请先选择需要分析的节点");
    return;
  }
  reportCreating.value = true;
  const nodeIds =
    scopeType.value === "ALL_CHAIN"
      ? []
      : collectSelectedNodeIds(selectedNodeIds.value);
  try {
    await createAnalysisReport({
      chainId: selectedChainId.value,
      reportType: "HEALTH_ANALYSIS",
      scopeType: scopeType.value,
      nodeIds,
    });
    ElMessage.success("报告任务已提交，可在报告历史中查看进度");
  } catch {
    // 7.3 后端接口未就绪、mock 分支 reject，统一提示功能待启用。
    ElMessage.info("报告生成功能将在后续启用");
  } finally {
    reportCreating.value = false;
  }
}

function handleResize() {
  const graph = graphHolder.value;
  if (!graph || graph.get("destroyed")) return;
  if (!containerRef.value) return;
  graph.changeSize(
    containerRef.value.clientWidth,
    containerRef.value.clientHeight,
  );
}

onMounted(async () => {
  await loadChains();
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(() => handleResize());
    resizeObserver.observe(containerRef.value);
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  if (graphHolder.value && !graphHolder.value.get("destroyed")) {
    graphHolder.value.destroy();
  }
  graphHolder.value = null;
});

const healthLevelTone: Record<string, string> = {
  HEALTHY: "success",
  NORMAL: "warning",
  WEAK: "warning",
  RISK: "danger",
  EMPTY: "danger",
};

function healthLevelTagType(level: ChainHealthLevel): string {
  return healthLevelTone[level ?? ""] ?? "info";
}
</script>

<template>
  <div class="chain-diagnosis">
    <div class="chain-diagnosis__sidebar">
      <div class="chain-diagnosis__sidebar-head">
        <el-input
          :model-value="selectedChain?.name"
          readonly
          placeholder="选择产业链"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
      <div v-loading="chainLoading" class="chain-diagnosis__chain-list">
        <div
          v-if="!chains.length && !chainLoading"
          class="chain-diagnosis__empty"
        >
          暂无产业链数据
        </div>
        <button
          v-for="chain in chains"
          :key="chain.id"
          type="button"
          class="chain-diagnosis__chain-item"
          :class="{ 'is-active': chain.id === selectedChainId }"
          @click="selectChain(chain)"
        >
          <span class="chain-diagnosis__chain-name">{{ chain.name }}</span>
          <span class="chain-diagnosis__chain-meta">
            {{ chain.nodeCount ?? 0 }} 节点 /
            {{ chain.enterpriseCount ?? 0 }} 企业
          </span>
        </button>
      </div>
    </div>

    <div class="chain-diagnosis__main">
      <div class="chain-diagnosis__toolbar">
        <div class="chain-diagnosis__toolbar-left">
          <span class="chain-diagnosis__title">
            {{ selectedChain?.name ?? "产业链健康分析" }}
          </span>
          <el-tag v-if="mode === 'REPORT_SELECT'" type="primary" size="small">
            报告选择模式
          </el-tag>
          <el-tag v-else type="info" size="small">浏览模式</el-tag>
        </div>
        <div class="chain-diagnosis__toolbar-right">
          <el-button :icon="Expand" size="small" @click="fitView">
            适应画布
          </el-button>
          <el-button :icon="Refresh" size="small" @click="resetView">
            重置展开
          </el-button>
          <el-button
            v-if="mode === 'BROWSE'"
            type="primary"
            size="small"
            @click="enterReportSelectMode"
          >
            创建报告
          </el-button>
          <el-button v-else size="small" @click="exitReportSelectMode">
            退出选择
          </el-button>
        </div>
      </div>

      <div
        v-loading="treeLoading"
        class="chain-diagnosis__canvas-wrap"
        :class="{ 'is-report-mode': mode === 'REPORT_SELECT' }"
      >
        <div
          v-if="!diagnosisRoot && !treeLoading"
          class="chain-diagnosis__empty"
        >
          暂无诊断数据，请选择产业链
        </div>
        <div ref="containerRef" class="chain-diagnosis__canvas"></div>
      </div>

      <div v-if="mode === 'REPORT_SELECT'" class="chain-diagnosis__report-bar">
        <div class="chain-diagnosis__report-info">
          已选 <strong>{{ selectedCount }}</strong> 个节点
          <span class="chain-diagnosis__report-scope">
            （范围：{{ scopeType === "ALL_CHAIN" ? "整条产业链" : "子树" }}）
          </span>
          <span v-if="selectedCount > 0" class="chain-diagnosis__report-hint">
            选中父节点时将分析其整棵子树
          </span>
        </div>
        <div class="chain-diagnosis__report-actions">
          <el-button
            size="small"
            :disabled="!diagnosisRoot"
            @click="selectAllFirstLevel"
          >
            全选一级
          </el-button>
          <el-button
            size="small"
            :disabled="selectedCount === 0"
            @click="clearSelection"
          >
            清空
          </el-button>
          <el-button
            type="primary"
            size="small"
            :loading="reportCreating"
            @click="createReport"
          >
            生成报告
          </el-button>
        </div>
      </div>
    </div>

    <div class="chain-diagnosis__detail">
      <template v-if="selectedDetail">
        <div class="chain-diagnosis__detail-head">
          <span class="chain-diagnosis__detail-title">
            {{ selectedDetail.nodeName }}
          </span>
          <el-tag
            :type="healthLevelTagType(selectedDetail.healthLevel)"
            size="small"
          >
            {{ selectedDetail.healthLabel }}
          </el-tag>
        </div>
        <div class="chain-diagnosis__detail-score">
          <span class="chain-diagnosis__score-value">
            {{ selectedDetail.healthScore }}
          </span>
          <span class="chain-diagnosis__score-unit">健康分</span>
        </div>
        <dl class="chain-diagnosis__detail-list">
          <div class="chain-diagnosis__detail-row">
            <dt>节点路径</dt>
            <dd>{{ selectedDetail.nodePath || "—" }}</dd>
          </div>
          <div class="chain-diagnosis__detail-row">
            <dt>节点层级</dt>
            <dd>{{ selectedDetail.nodeLevel }} 级</dd>
          </div>
          <div class="chain-diagnosis__detail-row">
            <dt>健康等级</dt>
            <dd>{{ selectedDetail.healthLevel }}</dd>
          </div>
          <div class="chain-diagnosis__detail-row">
            <dt>直接企业数</dt>
            <dd>{{ selectedDetail.directEnterpriseCount }} 家</dd>
          </div>
          <div class="chain-diagnosis__detail-row">
            <dt>子树企业数</dt>
            <dd>{{ selectedDetail.subtreeEnterpriseCount }} 家</dd>
          </div>
          <div
            v-if="selectedDetail.weakReasons.length"
            class="chain-diagnosis__detail-row"
          >
            <dt>弱项原因</dt>
            <dd>
              <el-tag
                v-for="reason in selectedDetail.weakReasons"
                :key="reason"
                type="danger"
                size="small"
                class="chain-diagnosis__weak-tag"
              >
                {{ reason }}
              </el-tag>
            </dd>
          </div>
          <div
            v-if="selectedDetail.keywords"
            class="chain-diagnosis__detail-row"
          >
            <dt>关键词</dt>
            <dd>{{ selectedDetail.keywords }}</dd>
          </div>
          <div
            v-if="selectedDetail.description"
            class="chain-diagnosis__detail-row"
          >
            <dt>节点定义</dt>
            <dd>{{ selectedDetail.description }}</dd>
          </div>
        </dl>
      </template>
      <div v-else class="chain-diagnosis__detail-empty">
        <template v-if="mode === 'BROWSE'"> 点击节点查看详情 </template>
        <template v-else> 点击节点选择分析范围 </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chain-diagnosis {
  display: flex;
  gap: 12px;
  height: calc(100vh - 60px - 44px - 48px);
  min-height: 520px;
}

.chain-diagnosis__sidebar {
  background: #ffffff;
  border: 1px solid rgb(216 229 245 / 82%);
  border-radius: 10px;
  box-shadow: 0 6px 22px rgb(20 68 130 / 6%);
  display: flex;
  flex: 0 0 220px;
  flex-direction: column;
  overflow: hidden;
}

.chain-diagnosis__sidebar-head {
  border-bottom: 1px solid rgb(226 236 248 / 80%);
  padding: 12px;
}

.chain-diagnosis__chain-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  padding: 8px;
}

.chain-diagnosis__empty {
  align-items: center;
  color: #94a3b8;
  display: flex;
  flex: 1;
  justify-content: center;
  padding: 24px;
}

.chain-diagnosis__chain-item {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  color: #243a58;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  text-align: left;
  transition: background 0.15s ease;
}

.chain-diagnosis__chain-item:hover {
  background: rgb(31 111 255 / 6%);
}

.chain-diagnosis__chain-item.is-active {
  background: linear-gradient(
    90deg,
    rgb(31 111 255 / 13%),
    rgb(8 191 211 / 10%)
  );
  border-color: rgb(31 111 255 / 22%);
  box-shadow: inset 3px 0 0 #1f6fff;
  color: #0b56d8;
}

.chain-diagnosis__chain-name {
  font-size: 14px;
  font-weight: 700;
}

.chain-diagnosis__chain-meta {
  color: #667085;
  font-size: 12px;
}

.chain-diagnosis__main {
  background: #ffffff;
  border: 1px solid rgb(216 229 245 / 82%);
  border-radius: 10px;
  box-shadow: 0 6px 22px rgb(20 68 130 / 6%);
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.chain-diagnosis__toolbar {
  align-items: center;
  border-bottom: 1px solid rgb(226 236 248 / 80%);
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 10px 14px;
}

.chain-diagnosis__toolbar-left {
  align-items: center;
  display: flex;
  gap: 10px;
}

.chain-diagnosis__title {
  color: #0b56d8;
  font-size: 16px;
  font-weight: 800;
}

.chain-diagnosis__toolbar-right {
  display: flex;
  gap: 8px;
}

.chain-diagnosis__canvas-wrap {
  flex: 1;
  min-height: 320px;
  position: relative;
}

.chain-diagnosis__canvas-wrap.is-report-mode {
  background: linear-gradient(
    135deg,
    rgb(31 111 255 / 4%),
    rgb(8 191 211 / 3%)
  );
}

.chain-diagnosis__canvas {
  height: 100%;
  width: 100%;
}

.chain-diagnosis__report-bar {
  align-items: center;
  border-top: 1px solid rgb(226 236 248 / 80%);
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 10px 14px;
}

.chain-diagnosis__report-info {
  color: #243a58;
  font-size: 13px;
}

.chain-diagnosis__report-scope {
  color: #667085;
}

.chain-diagnosis__report-hint {
  color: #0b56d8;
  font-size: 12px;
  margin-left: 8px;
}

.chain-diagnosis__report-actions {
  display: flex;
  gap: 8px;
}

.chain-diagnosis__detail {
  background: #ffffff;
  border: 1px solid rgb(216 229 245 / 82%);
  border-radius: 10px;
  box-shadow: 0 6px 22px rgb(20 68 130 / 6%);
  display: flex;
  flex: 0 0 300px;
  flex-direction: column;
  overflow-y: auto;
  padding: 16px;
}

.chain-diagnosis__detail-head {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.chain-diagnosis__detail-title {
  color: #172033;
  font-size: 16px;
  font-weight: 800;
}

.chain-diagnosis__detail-score {
  align-items: baseline;
  background: linear-gradient(135deg, #f0f7ff, #e6f6fb);
  border: 1px solid rgb(31 111 255 / 16%);
  border-radius: 10px;
  display: flex;
  gap: 6px;
  margin-bottom: 14px;
  padding: 12px 14px;
}

.chain-diagnosis__score-value {
  color: #0b56d8;
  font-size: 26px;
  font-weight: 800;
}

.chain-diagnosis__score-unit {
  color: #667085;
  font-size: 12px;
}

.chain-diagnosis__detail-list {
  display: grid;
  gap: 10px;
  margin: 0;
}

.chain-diagnosis__detail-row {
  display: grid;
  gap: 4px;
}

.chain-diagnosis__detail-row dt {
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}

.chain-diagnosis__detail-row dd {
  color: #172033;
  font-size: 13px;
  margin: 0;
  word-break: break-all;
}

.chain-diagnosis__weak-tag {
  margin: 2px 4px 2px 0;
}

.chain-diagnosis__detail-empty {
  align-items: center;
  color: #94a3b8;
  display: flex;
  flex: 1;
  justify-content: center;
  text-align: center;
}

@media (max-width: 1100px) {
  .chain-diagnosis {
    flex-wrap: wrap;
  }

  .chain-diagnosis__sidebar {
    flex-basis: 100%;
  }

  .chain-diagnosis__detail {
    flex-basis: 100%;
  }
}
</style>
