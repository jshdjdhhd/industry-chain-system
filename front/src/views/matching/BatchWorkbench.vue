<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Check,
  Close,
  DataAnalysis,
  Refresh,
  VideoPlay,
  VideoPause,
} from "@element-plus/icons-vue";
import {
  acceptMatchingResult,
  createBatchMatchingTasks,
  fetchBatchTasks,
  rejectMatchingResult,
  revokeMatchingTask,
  type BatchQueryVO,
} from "@/api/matching";
import StatusTag from "@/components/StatusTag.vue";
import { useOptionsStore } from "@/stores/options";
import {
  buildBatchMatchingRequest,
  buildBatchOperationLogs,
  summarizeBatchTasks,
  isTaskRunningStatus,
  canDecideResult,
  getBatchChainName,
  getBatchEnterpriseName,
  type BatchMatchingScope,
} from "./matchingViewModel";
import type { EnterpriseOption, MatchingTask } from "@/types/domain";

const BATCH_MAX_TASKS = 500;
const POLL_INTERVAL_MS = 3_000;

const emit = defineEmits<{
  "view-single": [enterpriseId: number, chainId: number, taskId?: number];
}>();

const optionsStore = useOptionsStore();
const enterprises = computed(() => optionsStore.enterpriseOptions);
const chains = computed(() => optionsStore.chainOptions);
const enterpriseLoading = ref(false);
const chainLoading = ref(false);
const enterpriseSearchKeyword = ref("");
const enterpriseSearchResults = ref<EnterpriseOption[]>([]);

const batchForm = reactive({
  enterpriseScope: "ALL" as BatchMatchingScope,
  chainScope: "ALL" as BatchMatchingScope,
  enterpriseIds: [] as number[],
  chainIds: [] as number[],
});

const batchTasks = ref<MatchingTask[]>([]);
const batchSubmitting = ref(false);
const batchPolling = ref(false);
const pollTimer = ref<ReturnType<typeof setTimeout> | null>(null);
const resultFilter = ref<
  "ALL" | "COMPLETED" | "PROCESSING" | "FAILED" | "ACCEPTED" | "PENDING"
>("ALL");
const selectedTaskIds = ref<number[]>([]);
const showOperationLogs = ref(false);
const batchOperating = ref(false);

const enterpriseMap = computed(() => {
  const map = new Map<number, string>();
  for (const e of enterprises.value) map.set(e.id, e.name);
  return map;
});

const chainMap = computed(() => {
  const map = new Map<number, string>();
  for (const c of chains.value) map.set(c.id, c.name);
  return map;
});

const displayedEnterpriseOptions = computed(() =>
  enterpriseSearchKeyword.value.trim()
    ? enterpriseSearchResults.value
    : enterprises.value,
);

const batchPairCount = computed(() => {
  if (batchForm.enterpriseScope === "ALL" || batchForm.chainScope === "ALL") {
    return null;
  }
  return batchForm.enterpriseIds.length * batchForm.chainIds.length;
});

const batchEnterpriseSummary = computed(() =>
  batchForm.enterpriseScope === "ALL"
    ? "全部"
    : String(batchForm.enterpriseIds.length),
);

const batchChainSummary = computed(() =>
  batchForm.chainScope === "ALL" ? "全部" : String(batchForm.chainIds.length),
);

const batchPairSummary = computed(() =>
  batchPairCount.value === null ? "由后端计算" : String(batchPairCount.value),
);

const batchOverLimit = computed(
  () => batchPairCount.value !== null && batchPairCount.value > BATCH_MAX_TASKS,
);

const batchCanSubmit = computed(
  () =>
    (batchForm.enterpriseScope === "ALL" ||
      batchForm.enterpriseIds.length > 0) &&
    (batchForm.chainScope === "ALL" || batchForm.chainIds.length > 0) &&
    !batchOverLimit.value &&
    !batchSubmitting.value,
);

const statusSummary = computed(() => summarizeBatchTasks(batchTasks.value));

const batchOperationLogs = computed(() => {
  if (!showOperationLogs.value) return [];
  return buildBatchOperationLogs(
    batchTasks.value,
    enterpriseMap.value,
    chainMap.value,
  );
});

const selectedTaskIdSet = computed(() => new Set(selectedTaskIds.value));

const filteredTasks = computed(() => {
  if (resultFilter.value === "ALL") return batchTasks.value;
  if (resultFilter.value === "ACCEPTED") {
    return batchTasks.value.filter((t) =>
      t.results.some((r) => r.decision === "ACCEPTED"),
    );
  }
  if (resultFilter.value === "PENDING") {
    return batchTasks.value.filter((t) =>
      t.results.some((r) => !r.decision || r.decision === "PENDING"),
    );
  }
  return batchTasks.value.filter((t) => t.status === resultFilter.value);
});

const allSelected = computed(
  () =>
    filteredTasks.value.length > 0 &&
    filteredTasks.value.every((t) => selectedTaskIdSet.value.has(t.taskId)),
);

function toggleSelectAll() {
  if (allSelected.value) {
    selectedTaskIds.value = [];
  } else {
    selectedTaskIds.value = filteredTasks.value.map((t) => t.taskId);
  }
}

function toggleSelectTask(taskId: number) {
  const idx = selectedTaskIds.value.indexOf(taskId);
  if (idx >= 0) {
    selectedTaskIds.value.splice(idx, 1);
  } else {
    selectedTaskIds.value.push(taskId);
  }
}

async function loadEnterpriseOptions() {
  enterpriseLoading.value = true;
  try {
    await optionsStore.loadEnterpriseOptions();
  } catch {
    // HTTP 拦截器已处理错误提示
  } finally {
    enterpriseLoading.value = false;
  }
}

async function loadChainOptions() {
  chainLoading.value = true;
  try {
    await optionsStore.loadChainOptions();
  } catch {
    // HTTP 拦截器已处理错误提示
  } finally {
    chainLoading.value = false;
  }
}

let enterpriseSearchTimer: number | null = null;

function searchEnterprises(keyword: string) {
  enterpriseSearchKeyword.value = keyword;
  if (enterpriseSearchTimer !== null) {
    window.clearTimeout(enterpriseSearchTimer);
    enterpriseSearchTimer = null;
  }
  const trimmed = keyword.trim();
  if (!trimmed || trimmed.length < 2) {
    enterpriseSearchResults.value = [];
    enterpriseLoading.value = false;
    return;
  }
  enterpriseSearchTimer = window.setTimeout(async () => {
    enterpriseLoading.value = true;
    try {
      enterpriseSearchResults.value =
        await optionsStore.searchEnterpriseOptions(trimmed);
    } catch {
      enterpriseSearchResults.value = [];
    } finally {
      enterpriseLoading.value = false;
      enterpriseSearchTimer = null;
    }
  }, 300);
}

async function submitBatchMatching() {
  if (!batchCanSubmit.value) {
    if (batchOverLimit.value) {
      ElMessage.warning(`批量任务最多支持 ${BATCH_MAX_TASKS} 个组合`);
      return;
    }
    ElMessage.warning("请选择要批量挂链的企业和产业链范围");
    return;
  }

  batchSubmitting.value = true;
  try {
    const result = await createBatchMatchingTasks(
      buildBatchMatchingRequest({
        enterpriseScope: batchForm.enterpriseScope,
        chainScope: batchForm.chainScope,
        selectedEnterpriseIds: batchForm.enterpriseIds,
        selectedChainIds: batchForm.chainIds,
      }),
    );
    batchTasks.value = result.tasks;
    selectedTaskIds.value = [];
    ElMessage.success(`已提交 ${result.submittedCount} 个智能挂链任务`);
    startPolling();
  } catch (error) {
    if (error instanceof Error) {
      ElMessage.error(error.message);
    }
  } finally {
    batchSubmitting.value = false;
  }
}

function startPolling() {
  stopPolling();
  batchPolling.value = true;
  scheduleNextPoll();
}

function scheduleNextPoll() {
  if (!batchPolling.value) return;
  pollTimer.value = setTimeout(async () => {
    if (!batchPolling.value || !batchTasks.value.length) {
      stopPolling();
      return;
    }
    await pollBatchTasks();
    if (batchPolling.value) {
      scheduleNextPoll();
    }
  }, POLL_INTERVAL_MS);
}

function stopPolling() {
  batchPolling.value = false;
  if (pollTimer.value) {
    clearTimeout(pollTimer.value);
    pollTimer.value = null;
  }
}

async function pollBatchTasks() {
  const taskIds = batchTasks.value.map((t) => t.taskId);
  if (taskIds.length === 0) {
    stopPolling();
    return;
  }

  try {
    // 使用批量查询接口（单次 HTTP 请求），而非逐个查询
    const result: BatchQueryVO = await fetchBatchTasks(taskIds);
    batchTasks.value = result.tasks;

    const stillRunning = result.tasks.some((t) =>
      isTaskRunningStatus(t.status),
    );
    if (!stillRunning) {
      stopPolling();
      ElMessage.success("所有批量任务已完成");
    }
  } catch {
    // Silently continue polling on error
  }
}

async function refreshAllTasks() {
  if (!batchTasks.value.length) return;
  const taskIds = batchTasks.value.map((t) => t.taskId);
  try {
    const result: BatchQueryVO = await fetchBatchTasks(taskIds);
    batchTasks.value = result.tasks;
    ElMessage.success("任务状态已刷新");
  } catch {
    ElMessage.error("刷新失败，请稍后重试");
  }
}

async function refreshBatchTasksSilently() {
  const taskIds = batchTasks.value.map((t) => t.taskId);
  if (!taskIds.length) return;
  try {
    const result: BatchQueryVO = await fetchBatchTasks(taskIds);
    batchTasks.value = result.tasks;
  } catch {
    // 刷新失败不遮挡本次批量操作结果提示。
  }
}

function showBatchDecisionResult(action: "采纳" | "拒绝", successCount: number, failedCount: number) {
  if (failedCount > 0) {
    ElMessage.warning(
      `批量${action}完成：成功 ${successCount} 个，失败 ${failedCount} 个，请查看最新任务状态。`,
    );
    return;
  }
  if (successCount > 0) {
    ElMessage.success(`已${action} ${successCount} 个候选结果`);
    return;
  }
  ElMessage.warning(`未能${action}任何候选结果，请刷新后重试`);
}

function togglePolling() {
  if (batchPolling.value) {
    stopPolling();
    ElMessage.info("已暂停自动刷新");
  } else {
    startPolling();
    ElMessage.info("已开启自动刷新");
  }
}

async function batchAcceptSelected() {
  if (batchOperating.value) return;
  batchOperating.value = true;
  try {
    const pendingTasks = batchTasks.value.filter(
      (t) =>
        selectedTaskIds.value.includes(t.taskId) &&
        t.results.some((r) => canDecideResult(r)),
    );
    if (pendingTasks.length === 0) {
      ElMessage.warning("选中任务中没有可采纳的候选结果");
      return;
    }

    try {
      await ElMessageBox.confirm(
        `将采纳选中 ${pendingTasks.length} 个任务的所有待处理候选结果，确认继续？`,
        "批量采纳",
        {
          confirmButtonText: "确认采纳",
          cancelButtonText: "取消",
          type: "info",
        },
      );
    } catch {
      return;
    }

    let acceptCount = 0;
    let failedCount = 0;
    for (const task of pendingTasks) {
      for (const result of task.results) {
        if (canDecideResult(result)) {
          try {
            await acceptMatchingResult(result.resultId);
            result.decision = "ACCEPTED";
            acceptCount++;
          } catch {
            failedCount++;
          }
        }
      }
    }

    await refreshBatchTasksSilently();
    showBatchDecisionResult("采纳", acceptCount, failedCount);
    batchTasks.value = [...batchTasks.value];
  } finally {
    batchOperating.value = false;
  }
}

async function batchRejectSelected() {
  if (batchOperating.value) return;
  batchOperating.value = true;
  try {
    const pendingTasks = batchTasks.value.filter(
      (t) =>
        selectedTaskIds.value.includes(t.taskId) &&
        t.results.some((r) => canDecideResult(r)),
    );
    if (pendingTasks.length === 0) {
      ElMessage.warning("选中任务中没有可拒绝的候选结果");
      return;
    }

    try {
      await ElMessageBox.confirm(
        `将拒绝选中 ${pendingTasks.length} 个任务的所有待处理候选结果，确认继续？`,
        "批量拒绝",
        {
          confirmButtonText: "确认拒绝",
          cancelButtonText: "取消",
          type: "warning",
        },
      );
    } catch {
      return;
    }

    let rejectCount = 0;
    let failedCount = 0;
    for (const task of pendingTasks) {
      for (const result of task.results) {
        if (canDecideResult(result)) {
          try {
            await rejectMatchingResult(result.resultId, "批量拒绝");
            result.decision = "REJECTED";
            rejectCount++;
          } catch {
            failedCount++;
          }
        }
      }
    }

    await refreshBatchTasksSilently();
    showBatchDecisionResult("拒绝", rejectCount, failedCount);
    batchTasks.value = [...batchTasks.value];
  } finally {
    batchOperating.value = false;
  }
}

async function revokeTask(taskId: number) {
  try {
    await ElMessageBox.confirm(
      "撤回后会删除本次推荐结果；如果已经采纳或人工确认，会同时解绑正式挂链关系。",
      "确认撤回",
      {
        confirmButtonText: "撤回",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
  } catch {
    return;
  }

  try {
    await revokeMatchingTask(taskId);
    const idx = batchTasks.value.findIndex((t) => t.taskId === taskId);
    if (idx >= 0) {
      batchTasks.value[idx] = {
        ...batchTasks.value[idx],
        status: "CANCELLED",
        results: [],
      };
    }
    ElMessage.success("任务已撤回");
  } catch {
    ElMessage.error("撤回失败");
  }
}

function viewSingleTask(task: MatchingTask) {
  emit("view-single", task.enterpriseId, task.chainId, task.taskId);
}

function hasDecidableResult(task: MatchingTask): boolean {
  return task.results.some(canDecideResult);
}

function getEnterpriseName(task: MatchingTask): string {
  return getBatchEnterpriseName(task, enterpriseMap.value);
}

function getChainName(task: MatchingTask): string {
  return getBatchChainName(task, chainMap.value);
}

function getBestResult(task: MatchingTask): string {
  if (!task.results.length) return "-";
  const best = task.results[0];
  return best.nodePath || "未知节点";
}

function getBestConfidence(task: MatchingTask): string {
  if (!task.results.length) return "-";
  return Number(task.results[0].confidence).toFixed(2);
}

function getRecommendLevel(task: MatchingTask): string {
  if (!task.results.length) return "-";
  const confidence = task.results[0].confidence;
  if (confidence >= 0.8) return "建议采纳";
  if (confidence >= 0.65) return "建议复核";
  return "不建议采纳";
}

function getBestConfidenceNum(task: MatchingTask): number {
  if (!task.results.length) return 0;
  return Number(task.results[0].confidence);
}

function getConfidenceClass(confidence: number): string {
  if (confidence >= 0.8) return "confidence-high";
  if (confidence >= 0.65) return "confidence-medium";
  return "confidence-low";
}

function getProgressPercent(): number {
  if (statusSummary.value.total === 0) return 0;
  return Math.round(
    ((statusSummary.value.completed + statusSummary.value.failed) /
      statusSummary.value.total) *
      100,
  );
}

function getProgressStatus(): "" | "success" | "exception" | "warning" {
  if (statusSummary.value.failed > 0) return "exception";
  if (statusSummary.value.processing > 0) return "";
  return "success";
}

async function quickAcceptTask(task: MatchingTask) {
  const pendingResults = task.results.filter((r) => canDecideResult(r));
  if (pendingResults.length === 0) {
    ElMessage.warning("该任务没有可采纳的候选结果");
    return;
  }

  let acceptCount = 0;
  let failedCount = 0;
  for (const result of pendingResults) {
    try {
      await acceptMatchingResult(result.resultId);
      result.decision = "ACCEPTED";
      acceptCount++;
    } catch {
      failedCount++;
    }
  }

  await refreshBatchTasksSilently();
  showBatchDecisionResult("采纳", acceptCount, failedCount);
  batchTasks.value = [...batchTasks.value];
}

async function quickRejectTask(task: MatchingTask) {
  const pendingResults = task.results.filter((r) => canDecideResult(r));
  if (pendingResults.length === 0) {
    ElMessage.warning("该任务没有可拒绝的候选结果");
    return;
  }

  let rejectCount = 0;
  let failedCount = 0;
  for (const result of pendingResults) {
    try {
      await rejectMatchingResult(result.resultId, "批量拒绝");
      result.decision = "REJECTED";
      rejectCount++;
    } catch {
      failedCount++;
    }
  }

  await refreshBatchTasksSilently();
  showBatchDecisionResult("拒绝", rejectCount, failedCount);
  batchTasks.value = [...batchTasks.value];
}

onBeforeUnmount(() => {
  stopPolling();
  if (enterpriseSearchTimer !== null) {
    window.clearTimeout(enterpriseSearchTimer);
  }
});

watch(
  () => batchForm.enterpriseScope,
  (scope) => {
    if (scope === "SELECTED") {
      void loadEnterpriseOptions();
    }
  },
);

watch(
  () => batchForm.chainScope,
  (scope) => {
    if (scope === "SELECTED") {
      void loadChainOptions();
    }
  },
);
</script>

<template>
  <div class="batch-workbench">
    <!-- Scope Selection -->
    <section class="surface batch-scope">
      <h3>批量挂链范围</h3>
      <el-form label-position="top">
        <el-form-item label="企业范围">
          <el-radio-group v-model="batchForm.enterpriseScope">
            <el-radio-button value="ALL">全部存续企业</el-radio-button>
            <el-radio-button value="SELECTED">指定企业</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="batchForm.enterpriseScope === 'SELECTED'"
          label="选择企业"
        >
          <el-select
            v-model="batchForm.enterpriseIds"
            filterable
            multiple
            remote
            reserve-keyword
            :loading="enterpriseLoading"
            placeholder="搜索并选择企业"
            :remote-method="searchEnterprises"
            style="width: 100%"
          >
            <el-option
              v-for="enterprise in displayedEnterpriseOptions"
              :key="enterprise.id"
              :label="enterprise.name"
              :value="enterprise.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="产业链范围">
          <el-radio-group v-model="batchForm.chainScope">
            <el-radio-button value="ALL">全部产业链</el-radio-button>
            <el-radio-button value="SELECTED">指定产业链</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="batchForm.chainScope === 'SELECTED'"
          label="选择产业链"
        >
          <el-select
            v-model="batchForm.chainIds"
            filterable
            multiple
            :loading="chainLoading"
            placeholder="选择一个或多个产业链"
            style="width: 100%"
          >
            <el-option
              v-for="chain in chains"
              :key="chain.id"
              :label="chain.name"
              :value="chain.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="batch-scope__summary">
        <div>
          <span>企业数</span>
          <strong>{{ batchEnterpriseSummary }}</strong>
        </div>
        <div>
          <span>产业链数</span>
          <strong>{{ batchChainSummary }}</strong>
        </div>
        <div :class="{ 'is-danger': batchOverLimit }">
          <span>任务组合</span>
          <strong>{{ batchPairSummary }}</strong>
        </div>
      </div>
      <el-alert
        v-if="batchOverLimit"
        :closable="false"
        :title="`当前组合数超过 ${BATCH_MAX_TASKS}，请缩小企业或产业链范围。`"
        type="warning"
      />

      <div class="batch-scope__actions">
        <el-button
          :icon="DataAnalysis"
          :disabled="!batchCanSubmit"
          :loading="batchSubmitting"
          type="primary"
          size="large"
          @click="submitBatchMatching"
        >
          一键提交并执行批量挂链
        </el-button>
      </div>
    </section>

    <!-- Progress & Stats -->
    <section v-if="batchTasks.length > 0" class="surface batch-progress">
      <div class="batch-progress__header">
        <h3>批量任务进度</h3>
        <div class="batch-progress__controls">
          <el-button
            :icon="batchPolling ? VideoPause : VideoPlay"
            :type="batchPolling ? 'warning' : 'primary'"
            plain
            size="small"
            @click="togglePolling"
          >
            {{ batchPolling ? "暂停刷新" : "自动刷新" }}
          </el-button>
          <el-button
            :icon="Refresh"
            plain
            size="small"
            @click="refreshAllTasks"
          >
            手动刷新
          </el-button>
        </div>
      </div>

      <div class="batch-progress__bar">
        <div class="batch-progress__bar-label">
          <span>完成进度</span>
          <span
            >{{ getProgressPercent() }}%（{{
              statusSummary.completed + statusSummary.failed
            }}
            / {{ statusSummary.total }}）</span
          >
        </div>
        <el-progress
          :percentage="getProgressPercent()"
          :status="getProgressStatus()"
          :stroke-width="12"
          :show-text="false"
        />
      </div>

      <div class="batch-stats">
        <div class="batch-stats__item">
          <span class="batch-stats__value">{{ statusSummary.total }}</span>
          <span class="batch-stats__label">总任务</span>
        </div>
        <div class="batch-stats__item batch-stats__item--processing">
          <span class="batch-stats__value">{{ statusSummary.processing }}</span>
          <span class="batch-stats__label">处理中</span>
        </div>
        <div class="batch-stats__item batch-stats__item--completed">
          <span class="batch-stats__value">{{ statusSummary.completed }}</span>
          <span class="batch-stats__label">已完成</span>
        </div>
        <div class="batch-stats__item batch-stats__item--failed">
          <span class="batch-stats__value">{{ statusSummary.failed }}</span>
          <span class="batch-stats__label">失败</span>
        </div>
        <div class="batch-stats__item batch-stats__item--accepted">
          <span class="batch-stats__value">{{ statusSummary.accepted }}</span>
          <span class="batch-stats__label">已采纳</span>
        </div>
        <div class="batch-stats__item batch-stats__item--pending">
          <span class="batch-stats__value">{{ statusSummary.pending }}</span>
          <span class="batch-stats__label">待处理</span>
        </div>
      </div>

      <!-- Filter tabs -->
      <div class="batch-filter">
        <el-radio-group v-model="resultFilter" size="small">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="COMPLETED">已完成</el-radio-button>
          <el-radio-button value="PROCESSING">处理中</el-radio-button>
          <el-radio-button value="FAILED">失败</el-radio-button>
          <el-radio-button value="ACCEPTED">已采纳</el-radio-button>
          <el-radio-button value="PENDING">待处理</el-radio-button>
        </el-radio-group>
      </div>

      <!-- Batch actions -->
      <div v-if="selectedTaskIds.length > 0" class="batch-actions">
        <span>已选 {{ selectedTaskIds.length }} 个任务</span>
        <el-button
          :icon="Check"
          type="primary"
          size="small"
          :disabled="batchOperating"
          @click="batchAcceptSelected"
        >
          批量采纳
        </el-button>
        <el-button
          :icon="Close"
          type="danger"
          plain
          size="small"
          :disabled="batchOperating"
          @click="batchRejectSelected"
        >
          批量拒绝
        </el-button>
      </div>

      <!-- Results Table -->
      <el-table
        :data="filteredTasks"
        border
        max-height="520"
        style="width: 100%"
      >
        <el-table-column width="44">
          <template #header>
            <el-checkbox :model-value="allSelected" @change="toggleSelectAll" />
          </template>
          <template #default="{ row }">
            <el-checkbox
              :model-value="selectedTaskIdSet.has(row.taskId)"
              @change="toggleSelectTask(row.taskId)"
            />
          </template>
        </el-table-column>
        <el-table-column label="企业" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getEnterpriseName(row) }}
          </template>
        </el-table-column>
        <el-table-column label="产业链" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getChainName(row) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column
          label="最佳候选节点"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="node-path">{{ getBestResult(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="90" align="center">
          <template #default="{ row }">
            <span :class="getConfidenceClass(getBestConfidenceNum(row))">
              {{ getBestConfidence(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="推荐等级" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="
                getRecommendLevel(row) === '建议采纳'
                  ? 'success'
                  : getRecommendLevel(row) === '建议复核'
                    ? 'warning'
                    : 'info'
              "
              effect="plain"
            >
              {{ getRecommendLevel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="viewSingleTask(row)"
            >
              详情
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED' && hasDecidableResult(row)"
              link
              type="success"
              size="small"
              @click="quickAcceptTask(row)"
            >
              采纳
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED' && hasDecidableResult(row)"
              link
              type="warning"
              size="small"
              @click="quickRejectTask(row)"
            >
              拒绝
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED'"
              link
              type="danger"
              size="small"
              @click="revokeTask(row.taskId)"
            >
              撤回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- Operation Logs -->
    <section v-if="batchTasks.length > 0" class="surface batch-logs">
      <div class="batch-logs__header">
        <h3>批量操作日志</h3>
        <el-button
          link
          type="primary"
          @click="showOperationLogs = !showOperationLogs"
        >
          {{ showOperationLogs ? "收起" : "展开" }}
        </el-button>
      </div>
      <template v-if="showOperationLogs">
        <el-table :data="batchOperationLogs" border row-key="id" size="small">
          <el-table-column type="expand" width="48">
            <template #default="{ row }">
              <div class="operation-log__details">
                <div
                  v-for="detail in row.details"
                  :key="detail.title"
                  class="operation-log__detail"
                >
                  <strong>{{ detail.title }}</strong>
                  <span style="white-space: pre-line">{{
                    detail.description
                  }}</span>
                  <small v-if="detail.evidence">{{ detail.evidence }}</small>
                </div>
                <span v-if="!row.details?.length" class="empty-note">
                  暂无可展开内容。
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作类型" prop="action" width="140" />
          <el-table-column label="操作内容" prop="content" min-width="260" />
          <el-table-column label="结果" prop="result" width="160" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                v-if="row.revoke"
                link
                type="danger"
                size="small"
                @click="revokeTask(row.revoke.taskId)"
              >
                撤回
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </section>

    <!-- Empty state -->
    <section v-if="batchTasks.length === 0" class="surface batch-empty">
      <div class="batch-empty__content">
        <el-icon :size="48" color="#c0c4cc"><DataAnalysis /></el-icon>
        <p>选择企业与产业链范围，点击"一键提交并执行"开始批量智能挂链。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.batch-workbench {
  display: grid;
  gap: 16px;
}

.batch-scope h3,
.batch-progress h3,
.batch-logs h3 {
  font-size: 18px;
  margin: 0 0 12px;
}

.batch-scope__summary {
  border: 1px solid #dbe5f2;
  border-radius: 6px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 16px 0;
  overflow: hidden;
}

.batch-scope__summary div {
  display: grid;
  gap: 4px;
  padding: 14px;
}

.batch-scope__summary div + div {
  border-left: 1px solid #dbe5f2;
}

.batch-scope__summary span {
  color: #667085;
}

.batch-scope__summary strong {
  color: #182b49;
  font-size: 24px;
}

.batch-scope__summary .is-danger strong {
  color: #d92d20;
}

.batch-scope__actions {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

.batch-progress__header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.batch-progress__controls {
  display: flex;
  gap: 8px;
}

.batch-progress__bar {
  margin-bottom: 16px;
}

.batch-progress__bar-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  color: #667085;
}

.batch-stats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.batch-stats__item {
  background: #f7faff;
  border: 1px solid #e1e8f2;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  padding: 12px;
  text-align: center;
}

.batch-stats__value {
  color: #182b49;
  font-size: 24px;
  font-weight: 700;
}

.batch-stats__label {
  color: #667085;
  font-size: 13px;
}

.batch-stats__item--processing .batch-stats__value {
  color: #1f6fff;
}

.batch-stats__item--completed .batch-stats__value {
  color: #12b76a;
}

.batch-stats__item--failed .batch-stats__value {
  color: #d92d20;
}

.batch-stats__item--accepted .batch-stats__value {
  color: #039855;
}

.batch-stats__item--pending .batch-stats__value {
  color: #f79009;
}

.batch-filter {
  margin-bottom: 12px;
}

.batch-actions {
  align-items: center;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 6px;
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px 14px;
}

.batch-actions span {
  color: #182b49;
  font-size: 14px;
  font-weight: 500;
}

.batch-empty__content {
  align-items: center;
  display: grid;
  gap: 12px;
  justify-items: center;
  padding: 60px 20px;
  text-align: center;
}

.batch-empty__content p {
  color: #667085;
  margin: 0;
  max-width: 400px;
}

.batch-empty__hint {
  color: #9ca3af !important;
  font-size: 13px;
}

.batch-logs__header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.operation-log__details {
  background: #f8fbff;
  border: 1px solid #dbe5f2;
  border-radius: 6px;
  display: grid;
  gap: 10px;
  margin: 4px 8px;
  padding: 12px;
}

.operation-log__detail {
  display: grid;
  gap: 4px;
}

.operation-log__detail strong {
  color: #182b49;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.operation-log__detail span {
  color: #667085;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: normal;
}

.operation-log__detail small {
  background: #ffffff;
  border: 1px solid #e1e8f2;
  border-radius: 4px;
  color: #475467;
  padding: 8px;
}

@media (max-width: 720px) {
  .batch-scope__summary {
    grid-template-columns: 1fr;
  }

  .batch-scope__summary div + div {
    border-left: 0;
    border-top: 1px solid #dbe5f2;
  }

  .batch-stats {
    grid-template-columns: repeat(3, 1fr);
  }
}

/* ========== 表格样式优化 ========== */
.batch-progress :deep(.el-table) {
  background: transparent;
}

.batch-progress :deep(.el-table__header-wrapper th) {
  background: linear-gradient(180deg, #f6faff, #edf6ff);
  font-weight: 700;
}

.batch-progress :deep(.el-table__row) {
  transition: background 180ms ease;
}

.batch-progress :deep(.el-table__row:hover) {
  background: rgb(31 111 255 / 4%);
}

.node-path {
  color: #344054;
  font-size: 13px;
}

.confidence-high {
  color: #12b76a;
  font-weight: 700;
}

.confidence-medium {
  color: #f59e0b;
  font-weight: 700;
}

.confidence-low {
  color: #667085;
}
</style>
