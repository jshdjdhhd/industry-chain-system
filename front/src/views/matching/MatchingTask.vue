<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { ElMessage } from "element-plus";
import { marked } from "marked";
import {
  Check,
  Close,
  DataAnalysis,
  EditPen,
  Refresh,
  Search,
} from "@element-plus/icons-vue";
import { fetchEnterprise } from "@/api/enterprise";
import {
  acceptMatchingResult,
  cancelMatchingTask,
  createMatchingTask,
  fetchInitialMatchingTask,
  fetchMatchingTask,
  manualConfirmMatching,
  rejectMatchingResult,
  retryMatchingTask,
} from "@/api/matching";
import SearchBar from "@/components/SearchBar.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useMatchingStore } from "@/stores/matching";
import { useOptionsStore } from "@/stores/options";
import {
  canCancelTask,
  canDecideResult,
  canRetryTask,
  candidateLinkTip,
  candidateRiskTip,
  flattenChainNodes,
  isLowConfidenceCandidate,
  isTaskRunningStatus,
  matchingDecisionTip,
  matchingTaskStatusTip,
  pickActiveResult,
} from "./matchingViewModel";
import BatchWorkbench from "./BatchWorkbench.vue";
import type {
  ChainNode,
  EnterpriseListItem,
  EnterpriseOption,
  MatchingTask,
} from "@/types/domain";

type TaskOperation = "refresh" | "cancel" | "retry" | null;

interface TaskWaitResult {
  task: MatchingTask;
  timedOut: boolean;
}

const matchingStore = useMatchingStore();
const optionsStore = useOptionsStore();
const activeTab = ref<"batch" | "single">("batch");

// Markdown 渲染配置
marked.setOptions({
  breaks: true, // 支持换行
  gfm: true, // 支持 GitHub 风格 Markdown
});

function renderMarkdown(text: string): string {
  if (!text) return "";
  try {
    // 预处理：确保 Markdown 符号前有换行（LLM 有时返回的文本缺少换行）
    let processed = text
      // 标题符号前加双换行（### xxx → \n\n### xxx）
      .replace(/(#{1,4}\s)/g, "\n\n$1")
      // 无序列表项前加换行（句号/冒号后紧跟 * 列表项）
      .replace(/([。：])\*\s/g, "$1\n* ")
      // 有序列表项前加换行
      .replace(/([。：])(\d+\.\s)/g, "$1\n$2")
      // 清理开头的换行
      .replace(/^\n+/, "");
    return marked.parse(processed) as string;
  } catch {
    return text;
  }
}

const enterprises = computed(() => optionsStore.enterpriseOptions);
const selectedEnterpriseDetail = ref<EnterpriseListItem | null>(null);
const chains = computed(() => optionsStore.chainOptions);
const nodes = ref<ChainNode[]>([]);
const task = ref<MatchingTask | null>(null);
const activeResultId = ref<number>();
const loading = ref(false);
const optionsLoading = ref(false);
const enterpriseSearching = ref(false);
const enterpriseSearchKeyword = ref("");
const enterpriseSearchResults = ref<EnterpriseOption[]>([]);
const manualDialogVisible = ref(false);
const manualSubmitting = ref(false);
const taskOperation = ref<TaskOperation>(null);
const frontendTimeoutTaskId = ref<number | null>(null);

const TASK_POLL_INTERVAL_MS = 2_000;
const TASK_POLL_TIMEOUT_MS = 90_000;
const FRONTEND_POLL_TIMEOUT_MESSAGE =
  "前端等待超时，可刷新状态；如果任务仍在处理中，可取消任务。";

const form = reactive({
  enterpriseId: undefined as number | undefined,
  chainId: undefined as number | undefined,
});

const manualForm = reactive({
  nodeId: undefined as number | undefined,
  reason: "",
});

const selectedEnterprise = computed(() => {
  if (selectedEnterpriseDetail.value?.id === form.enterpriseId) {
    return selectedEnterpriseDetail.value;
  }
  return null;
});

const displayedEnterpriseOptions = computed(() => {
  const source = enterpriseSearchKeyword.value.trim()
    ? enterpriseSearchResults.value
    : enterprises.value;
  const detail = selectedEnterpriseDetail.value;
  if (!detail || source.some((item) => item.id === detail.id)) {
    return source;
  }
  return [{ id: detail.id, name: detail.name }, ...source];
});

const activeResult = computed(() =>
  pickActiveResult(task.value, activeResultId.value, manualForm.nodeId),
);

const flattenedNodes = computed(() => flattenChainNodes(nodes.value));
const nodeCount = computed(() => flattenedNodes.value.length);
const isTaskProcessing = computed(() =>
  isTaskRunningStatus(task.value?.status),
);
const canShowCancelAction = computed(() => canCancelTask(task.value?.status));
const canShowRetryAction = computed(() => canRetryTask(task.value?.status));
const activeResultAlreadyLinked = computed(() => {
  if (!activeResult.value) return false;
  return (
    activeResult.value.alreadyLinked === true ||
    task.value?.existingLinkedNodeIds?.includes(activeResult.value.nodeId) ===
      true
  );
});
const activeResultCanDecide = computed(
  () => canDecideResult(activeResult.value) && !activeResultAlreadyLinked.value,
);
const activeResultDecisionTip = computed(() =>
  matchingDecisionTip(activeResult.value),
);
const activeResultRiskTip = computed(() =>
  candidateRiskTip(activeResult.value),
);
const activeResultLinkTip = computed(() => {
  if (activeResultAlreadyLinked.value) {
    return (
      activeResult.value?.linkWarning ??
      "该企业已挂接到此候选节点，请勿重复采纳。"
    );
  }
  return candidateLinkTip(activeResult.value, task.value);
});
const taskStatusTip = computed(() => matchingTaskStatusTip(task.value?.status));
const hasOnlyLowConfidenceCandidates = computed(
  () =>
    !!task.value?.results.length &&
    task.value.results.every((result) => isLowConfidenceCandidate(result)),
);
const hasFrontendPollTimeout = computed(
  () =>
    frontendTimeoutTaskId.value === task.value?.taskId &&
    isTaskRunningStatus(task.value?.status),
);
const selectedManualNodeLabel = computed(
  () =>
    displayNodes.value.find((node) => node.id === manualForm.nodeId)?.label ??
    "未知节点",
);

const displayNodes = computed(() => {
  if (!task.value || !task.value.results.length) {
    return flattenedNodes.value.map((node) => ({
      id: node.id,
      label: node.label,
      isRecommended: false,
      confidence: 0,
      reason: "",
      resultId: null as number | null,
      isLowConfidence: false,
      isAlreadyLinked: false,
      linkTip: "",
    }));
  }

  const linkedNodeIds = new Set(task.value.existingLinkedNodeIds ?? []);
  const recommendedNodeIds = new Set(
    task.value.results.map((result) => result.nodeId),
  );
  const recommendedNodes = task.value.results.map((result) => {
    const isAlreadyLinked =
      result.alreadyLinked === true || linkedNodeIds.has(result.nodeId);
    return {
      id: result.nodeId,
      label: result.nodePath,
      isRecommended: true,
      confidence: result.confidence,
      reason: result.reason,
      resultId: result.resultId,
      isLowConfidence: isLowConfidenceCandidate(result),
      isAlreadyLinked,
      linkTip: isAlreadyLinked
        ? result.linkWarning ?? "该企业已挂接到此候选节点，请勿重复挂接。"
        : "",
    };
  });

  const unrecommendedNodes = flattenedNodes.value
    .filter((node) => !recommendedNodeIds.has(node.id))
    .map((node) => ({
      id: node.id,
      label: node.label,
      isRecommended: false,
      confidence: 0,
      reason: "",
      resultId: null as number | null,
      isLowConfidence: false,
      isAlreadyLinked: linkedNodeIds.has(node.id),
      linkTip: linkedNodeIds.has(node.id)
        ? "该企业已挂接到此节点，请勿重复挂接。"
        : "",
    }));

  return [...recommendedNodes, ...unrecommendedNodes];
});

async function loadNodes(silent = false) {
  if (!form.chainId) {
    nodes.value = [];
    return;
  }
  nodes.value = (await optionsStore.loadNodeCandidates(form.chainId)).map(
    (candidate) => ({
      id: candidate.id,
      chainId: candidate.chainId,
      parentId: null,
      name: candidate.name,
      stageType: null,
      keywords: candidate.keywords,
      definition: candidate.description,
      nodePath: candidate.nodePath,
      children: [],
    }),
  );
  if (!silent) {
    ElMessage.success(`已读取 ${nodeCount.value} 个产业链节点`);
  }
}

async function loadSingleTabOptions() {
  optionsLoading.value = true;
  try {
    const [enterpriseResult, chainResult] = await Promise.allSettled([
      optionsStore.loadEnterpriseOptions(),
      optionsStore.loadChainOptions(),
    ]);
    if (enterpriseResult.status === "rejected") {
      ElMessage.warning("企业选项加载失败，请刷新重试");
    } else if (!form.enterpriseId && enterpriseResult.value.length > 0) {
      form.enterpriseId = enterpriseResult.value[0].id;
    }
    if (chainResult.status === "rejected") {
      ElMessage.warning("产业链选项加载失败，请刷新重试");
    } else if (!form.chainId && chainResult.value.length > 0) {
      form.chainId = chainResult.value[0].id;
    }
    if (form.chainId) {
      await loadNodes(true).catch(() => {
        ElMessage.warning("节点列表加载失败，请刷新重试");
      });
    }
  } finally {
    optionsLoading.value = false;
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
  if (!trimmed) {
    enterpriseSearchResults.value = [];
    enterpriseSearching.value = false;
    return;
  }
  if (trimmed.length < 2) {
    enterpriseSearchResults.value = [];
    enterpriseSearching.value = false;
    return;
  }
  enterpriseSearchTimer = window.setTimeout(async () => {
    enterpriseSearching.value = true;
    try {
      const result = await optionsStore.searchEnterpriseOptions(trimmed);
      if (enterpriseSearchKeyword.value.trim() === trimmed) {
        enterpriseSearchResults.value = result;
      }
    } catch {
      if (enterpriseSearchKeyword.value.trim() === trimmed) {
        enterpriseSearchResults.value = [];
      }
    } finally {
      enterpriseSearching.value = false;
      enterpriseSearchTimer = null;
    }
  }, 300);
}

async function generateRecommendation() {
  if (!form.enterpriseId || !form.chainId) {
    ElMessage.warning("请选择企业和产业链");
    return;
  }

  loading.value = true;
  matchingStore.setRecommending(true);
  clearFrontendPollTimeout();
  try {
    const submittedTask = await createMatchingTask({
      enterpriseId: form.enterpriseId,
      chainId: form.chainId,
    });
    applyTaskState(submittedTask);
    await resolveTaskAfterSubmission(
      submittedTask,
      "分析任务已提交，正在调用大模型生成结果",
    );
  } catch (error) {
    if (!isHttpError(error) && error instanceof Error) {
      ElMessage.error(error.message);
    }
  } finally {
    loading.value = false;
    matchingStore.setRecommending(false);
  }
}

async function loadSelectedEnterpriseDetail() {
  if (!form.enterpriseId) {
    selectedEnterpriseDetail.value = null;
    return;
  }
  try {
    selectedEnterpriseDetail.value = await fetchEnterprise(form.enterpriseId);
  } catch {
    selectedEnterpriseDetail.value = null;
  }
}

async function refreshEnterpriseContext() {
  await loadSelectedEnterpriseDetail();
}

async function refreshTask(silent = false) {
  if (!task.value) return;
  taskOperation.value = "refresh";
  try {
    applyTaskState(await fetchMatchingTask(task.value.taskId));
    if (!silent) {
      ElMessage.success("任务状态已刷新");
    }
  } finally {
    taskOperation.value = null;
  }
}

async function cancelTaskAction() {
  if (!task.value || !canShowCancelAction.value) return;
  taskOperation.value = "cancel";
  try {
    const cancelledTask = await cancelMatchingTask(task.value.taskId);
    applyTaskState(cancelledTask);
    ElMessage.success("任务已取消");
  } finally {
    taskOperation.value = null;
    matchingStore.setRecommending(false);
  }
}

async function retryTaskAction() {
  if (!task.value || !canShowRetryAction.value) return;
  taskOperation.value = "retry";
  matchingStore.setRecommending(true);
  clearFrontendPollTimeout();
  try {
    const retriedTask = await retryMatchingTask(task.value.taskId);
    applyTaskState(retriedTask);
    await resolveTaskAfterSubmission(
      retriedTask,
      "已重新发起分析任务，正在调用大模型生成结果",
    );
  } finally {
    taskOperation.value = null;
    matchingStore.setRecommending(false);
  }
}

function selectResultById(resultId: number) {
  activeResultId.value = resultId;
}

function selectManualNode(nodeId: number) {
  manualForm.nodeId = nodeId;
  manualForm.reason = "手动选择节点";
  activeResultId.value = undefined;
}

async function quickAccept() {
  if (!activeResult.value) return;
  if (!canDecideResult(activeResult.value)) {
    ElMessage.warning(
      activeResultLinkTip.value || matchingDecisionTip(activeResult.value),
    );
    return;
  }
  await acceptMatchingResult(activeResult.value.resultId);
  ElMessage.success("推荐已采纳");
  await refreshTask(true);
}

async function quickReject() {
  if (!activeResult.value) return;
  if (!canDecideResult(activeResult.value)) {
    ElMessage.warning(
      activeResultLinkTip.value || matchingDecisionTip(activeResult.value),
    );
    return;
  }
  await rejectMatchingResult(activeResult.value.resultId, "人工判定不采纳");
  ElMessage.success("推荐已拒绝");
  await refreshTask(true);
}

function openManualDialog() {
  if (!form.enterpriseId || (!manualForm.nodeId && !activeResult.value)) {
    ElMessage.warning("请选择企业和要人工确认的节点");
    return;
  }
  manualForm.nodeId = activeResult.value?.nodeId ?? manualForm.nodeId;
  manualForm.reason = activeResult.value
    ? `人工复核后确认更适合：${activeResult.value.nodePath}`
    : manualForm.reason || `人工确认挂链至：${selectedManualNodeLabel.value}`;
  manualDialogVisible.value = true;
}

async function submitManualConfirm() {
  const enterpriseId = task.value?.enterpriseId ?? form.enterpriseId;
  if (!enterpriseId || !manualForm.nodeId) {
    ElMessage.warning("请选择企业和要人工确认的节点");
    return;
  }
  if (!manualForm.reason.trim()) {
    ElMessage.warning("请填写人工确认理由");
    return;
  }

  manualSubmitting.value = true;
  try {
    await manualConfirmMatching({
      enterpriseId,
      nodeId: manualForm.nodeId,
      ...(task.value ? { taskId: task.value.taskId } : {}),
      ...(activeResult.value
        ? { sourceResultId: activeResult.value.resultId }
        : {}),
      reason: manualForm.reason.trim(),
    });
    ElMessage.success("人工改选已确认，正式挂链关系已创建");
    manualDialogVisible.value = false;
    if (task.value) {
      await refreshTask(true);
    }
    await refreshEnterpriseContext();
  } finally {
    manualSubmitting.value = false;
  }
}

function quickManualConfirm() {
  openManualDialog();
}

function applyTaskState(nextTask: MatchingTask) {
  const previousActiveResultId = activeResultId.value;
  if (
    frontendTimeoutTaskId.value !== null &&
    (frontendTimeoutTaskId.value !== nextTask.taskId ||
      !isTaskRunningStatus(nextTask.status))
  ) {
    clearFrontendPollTimeout();
  }

  task.value = nextTask;
  activeResultId.value =
    nextTask.results.find(
      (result) => result.resultId === previousActiveResultId,
    )?.resultId ?? nextTask.results[0]?.resultId;
  matchingStore.setCurrentTask(nextTask);
}

async function resolveTaskAfterSubmission(
  submittedTask: MatchingTask,
  processingMessage: string,
) {
  if (isTaskRunningStatus(submittedTask.status)) {
    ElMessage.info(processingMessage);
    const waitResult = await waitForTaskCompletion(submittedTask.taskId);
    applyTaskState(waitResult.task);
    if (waitResult.timedOut) {
      frontendTimeoutTaskId.value = waitResult.task.taskId;
      ElMessage.warning(FRONTEND_POLL_TIMEOUT_MESSAGE);
      return;
    }
  }

  reportTaskOutcome(task.value ?? submittedTask);
}

function reportTaskOutcome(currentTask: MatchingTask) {
  if (currentTask.status === "COMPLETED") {
    if (hasOnlyLowConfidenceCandidates.value) {
      ElMessage.warning(
        "已列出低置信候选，当前不建议自动挂链，请人工复核后再处理。",
      );
      return;
    }
    if (currentTask.results.length > 0) {
      ElMessage.success("人工智能分析已生成");
      return;
    }
    ElMessage.warning("未找到可用候选节点，建议读取节点后人工确认挂链。");
    return;
  }

  if (currentTask.status === "FAILED") {
    ElMessage.error("人工智能分析失败，请检查 LLM 配置或稍后重试。");
    return;
  }

  if (currentTask.status === "TIMEOUT") {
    ElMessage.warning("后端任务执行超时，可刷新状态或重试任务。");
    return;
  }

  if (currentTask.status === "CANCELLED") {
    ElMessage.info("任务已取消，可按需重新发起重试任务。");
  }
}

async function waitForTaskCompletion(taskId: number): Promise<TaskWaitResult> {
  const deadline = Date.now() + TASK_POLL_TIMEOUT_MS;

  while (Date.now() < deadline) {
    await sleep(TASK_POLL_INTERVAL_MS);
    const latestTask = await fetchMatchingTask(taskId);
    applyTaskState(latestTask);
    if (!isTaskRunningStatus(latestTask.status)) {
      return { task: latestTask, timedOut: false };
    }
  }

  const latestTask = await fetchMatchingTask(taskId);
  applyTaskState(latestTask);
  return {
    task: latestTask,
    timedOut: isTaskRunningStatus(latestTask.status),
  };
}

function clearFrontendPollTimeout() {
  frontendTimeoutTaskId.value = null;
}

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function isHttpError(error: unknown) {
  return typeof error === "object" && error !== null && "isAxiosError" in error;
}

let skipWatchers = false;

async function handleViewSingle(
  enterpriseId: number,
  chainId: number,
  taskId?: number,
) {
  // Reset state to avoid cross-enterprise data leakage
  task.value = null;
  activeResultId.value = undefined;
  manualForm.nodeId = undefined;
  manualForm.reason = "";
  matchingStore.setCurrentTask(null);

  // Set form values with watchers suppressed to avoid duplicate API calls
  skipWatchers = true;
  form.enterpriseId = enterpriseId;
  form.chainId = chainId;
  activeTab.value = "single";

  try {
    // Load enterprise detail, nodes, and optionally the task
    await Promise.all([loadSelectedEnterpriseDetail(), loadNodes(true)]);

    if (taskId) {
      try {
        const taskData = await fetchMatchingTask(taskId);
        applyTaskState(taskData);
      } catch {
        // Task may have been revoked or deleted
      }
    }
  } finally {
    skipWatchers = false;
  }
}

watch(
  () => form.chainId,
  () => {
    if (!skipWatchers) loadNodes(true);
  },
);

watch(
  () => form.enterpriseId,
  () => {
    if (!skipWatchers) loadSelectedEnterpriseDetail();
  },
);

watch(
  () => activeTab.value,
  async (tab) => {
    if (tab === "single") {
      await loadSingleTabOptions();
    }
  },
);

onMounted(async () => {
  if (activeTab.value === "single") {
    await loadSingleTabOptions();
  }

  try {
    const initialTask = await fetchInitialMatchingTask();
    if (initialTask) {
      applyTaskState(initialTask);
    }
  } catch {
    // 示例任务加载失败不影响页面
  }
});

onBeforeUnmount(() => {
  if (enterpriseSearchTimer !== null) {
    window.clearTimeout(enterpriseSearchTimer);
  }
});
</script>

<template>
  <div class="page">
    <div class="page__header">
      <div>
        <h2 class="page__title">智能挂链</h2>
        <p class="page__description">
          支持批量挂链工作台，并为单企业提供大模型人工智能分析。
        </p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="matching-tabs">
      <!-- Batch Tab - Primary -->
      <el-tab-pane label="批量挂链工作台" name="batch">
        <BatchWorkbench @view-single="handleViewSingle" />
      </el-tab-pane>

      <!-- Single Enterprise Tab - AI Analysis -->
      <el-tab-pane label="人工智能分析" name="single">
        <div class="single-enterprise">
          <SearchBar class="matching-search" :model-value="form">
            <el-form-item label="企业">
              <el-select
                v-model="form.enterpriseId"
                filterable
                remote
                reserve-keyword
                :loading="enterpriseSearching || optionsLoading"
                placeholder="搜索存续企业"
                :remote-method="searchEnterprises"
              >
                <el-option
                  v-for="enterprise in displayedEnterpriseOptions"
                  :key="enterprise.id"
                  :label="enterprise.name"
                  :value="enterprise.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="产业链">
              <el-select
                v-model="form.chainId"
                filterable
                :loading="optionsLoading"
                placeholder="选择产业链"
              >
                <el-option
                  v-for="chain in chains"
                  :key="chain.id"
                  :label="chain.name"
                  :value="chain.id"
                />
              </el-select>
            </el-form-item>
            <template #actions>
              <el-button :icon="Search" plain @click="() => loadNodes()"
                >读取节点</el-button
              >
              <el-button
                :icon="DataAnalysis"
                :loading="loading"
                :disabled="taskOperation !== null || manualSubmitting"
                type="primary"
                @click="generateRecommendation"
              >
                {{ loading ? "分析中" : "开始分析" }}
              </el-button>
            </template>
          </SearchBar>

          <div class="single-enterprise__toolbar">
            <el-button
              :icon="Refresh"
              :disabled="
                !task || loading || taskOperation !== null || manualSubmitting
              "
              :loading="taskOperation === 'refresh'"
              plain
              @click="refreshTask"
            >
              刷新状态
            </el-button>
            <el-button
              v-if="canShowCancelAction"
              :disabled="taskOperation !== null || manualSubmitting"
              :loading="taskOperation === 'cancel'"
              plain
              type="danger"
              @click="cancelTaskAction"
            >
              取消任务
            </el-button>
            <el-button
              v-if="canShowRetryAction"
              :disabled="taskOperation !== null || manualSubmitting"
              :loading="taskOperation === 'retry'"
              plain
              type="primary"
              @click="retryTaskAction"
            >
              重试任务
            </el-button>
          </div>

          <div class="matching-workbench">
            <section class="matching-panel matching-panel--enterprise">
              <h3>企业信息</h3>
              <div v-if="selectedEnterprise" class="enterprise-summary">
                <div class="enterprise-summary__head">
                  <strong>{{ selectedEnterprise.name }}</strong>
                  <StatusTag :status="selectedEnterprise.status" />
                </div>
                <dl>
                  <dt>统一社会信用代码</dt>
                  <dd>{{ selectedEnterprise.creditCode || "-" }}</dd>
                  <dt>主营产品</dt>
                  <dd>{{ selectedEnterprise.mainProducts || "-" }}</dd>
                  <dt>注册地址</dt>
                  <dd>{{ selectedEnterprise.address || "-" }}</dd>
                  <dt>经营范围</dt>
                  <dd>{{ selectedEnterprise.businessScope || "-" }}</dd>
                </dl>
                <div class="enterprise-summary__tags">
                  <el-tag
                    v-for="tag in selectedEnterprise.tags"
                    :key="tag.id"
                    effect="plain"
                    type="primary"
                  >
                    {{ tag.name }}
                  </el-tag>
                </div>
              </div>
              <div v-else class="empty-note">请选择一家存续企业。</div>
            </section>

            <section class="matching-panel matching-panel--candidates">
              <h3>候选节点</h3>
              <p class="matching-panel__hint">
                候选范围包含 {{ nodeCount }} 个产业链节点。
                <template v-if="task && !isTaskProcessing">
                  AI 分析 {{ task.results.length }} 个节点，共
                  {{ displayNodes.length }} 个节点可选。
                </template>
                <template v-else-if="!task"
                  >显示全部节点，可手动选择挂链。</template
                >
              </p>

              <div v-if="task" class="matching-panel__status">
                <StatusTag :status="task.status" />
                <span class="matching-panel__status-text">{{
                  taskStatusTip
                }}</span>
              </div>

              <div v-if="hasFrontendPollTimeout" class="matching-panel__alerts">
                <el-alert
                  :closable="false"
                  :title="FRONTEND_POLL_TIMEOUT_MESSAGE"
                  type="warning"
                />
              </div>

              <div v-if="isTaskProcessing" class="ai-skeleton">
                <div class="ai-skeleton__card" />
                <div class="ai-skeleton__card" />
                <div class="ai-skeleton__card" />
                <div class="ai-loading-hint">
                  大模型正在深度分析，请耐心等待...
                </div>
              </div>
              <div v-else class="candidate-list">
                <button
                  v-for="node in displayNodes"
                  :key="node.id"
                  class="candidate-list__item"
                  :class="{
                    'is-active': node.isRecommended
                      ? node.resultId === activeResultId
                      : manualForm.nodeId === node.id && !activeResultId,
                    'is-recommended': node.isRecommended,
                    'is-unrecommended': !node.isRecommended && task,
                    'is-low-confidence':
                      node.isRecommended && node.isLowConfidence,
                    'is-linked': node.isAlreadyLinked,
                  }"
                  type="button"
                  @click="
                    node.isRecommended && node.resultId
                      ? selectResultById(node.resultId)
                      : selectManualNode(node.id)
                  "
                >
                  <span class="candidate-list__radio" />
                  <span class="candidate-list__body">
                    <strong>{{ node.label }}</strong>
                    <small v-if="node.isRecommended">{{ node.reason }}</small>
                    <small
                      v-else-if="task"
                      class="candidate-list__unrecommended"
                    >
                      未推荐节点
                    </small>
                    <small v-else class="candidate-list__manual"
                      >可手动选择</small
                    >
                    <small
                      v-if="node.linkTip"
                      class="candidate-list__link-warning"
                    >
                      {{ node.linkTip }}
                    </small>
                  </span>
                  <span v-if="node.isRecommended" class="candidate-list__score">
                    <svg class="confidence-ring" viewBox="0 0 36 36">
                      <circle
                        class="confidence-ring__track"
                        cx="18"
                        cy="18"
                        r="15.9"
                      />
                      <circle
                        class="confidence-ring__fill"
                        cx="18"
                        cy="18"
                        r="15.9"
                        :stroke="
                          node.confidence >= 0.8
                            ? '#22c55e'
                            : node.confidence >= 0.65
                              ? '#eab308'
                              : '#ef4444'
                        "
                        :stroke-dasharray="`${node.confidence * 100} 100`"
                        transform="rotate(-90 18 18)"
                      />
                      <text
                        class="confidence-ring__text"
                        x="18"
                        y="20"
                        text-anchor="middle"
                      >
                        {{ node.confidence.toFixed(2) }}
                      </text>
                    </svg>
                  </span>
                  <span
                    v-else-if="task"
                    class="candidate-list__score candidate-list__score--unrecommended"
                  >
                    -
                  </span>
                </button>
              </div>
            </section>

            <section class="matching-panel matching-panel--reason">
              <h3>分析与建议</h3>
              <div v-if="activeResult" class="reason-panel">
                <div class="ai-suggestion-card">
                  <div class="ai-suggestion-card__header">
                    <span class="ai-suggestion-card__icon"
                      ><el-icon><DataAnalysis /></el-icon
                    ></span>
                    <span class="ai-suggestion-card__title">AI 建议</span>
                  </div>
                  <div
                    class="ai-suggestion-card__content markdown-body"
                    v-html="renderMarkdown(activeResult.reason)"
                  ></div>
                  <div class="ai-suggestion-card__summary">
                    综合分析：该企业可重点评估 {{ activeResult.nodePath }} 节点
                  </div>
                </div>
                <div class="evidence-table">
                  <div>
                    <span>分析证据</span>
                    <strong>{{
                      activeResult.evidence || "大模型分析结果"
                    }}</strong>
                  </div>
                  <div>
                    <span>置信度</span>
                    <strong>{{ activeResult.confidence.toFixed(2) }}</strong>
                  </div>
                  <div>
                    <span>当前状态</span>
                    <strong>
                      <StatusTag :status="activeResult.decision || 'PENDING'" />
                    </strong>
                  </div>
                </div>
                <div v-if="activeResultDecisionTip" class="decision-callout">
                  {{ activeResultDecisionTip }}
                </div>
                <div v-if="activeResultLinkTip" class="link-callout">
                  {{ activeResultLinkTip }}
                </div>
                <div v-if="activeResultRiskTip" class="risk-callout">
                  {{ activeResultRiskTip }}
                </div>
                <div class="reason-panel__actions">
                  <el-button
                    :icon="Check"
                    :disabled="!activeResultCanDecide"
                    type="primary"
                    @click="quickAccept"
                  >
                    采纳
                  </el-button>
                  <el-button
                    :icon="Close"
                    :disabled="!activeResultCanDecide"
                    plain
                    type="danger"
                    @click="quickReject"
                  >
                    拒绝
                  </el-button>
                  <el-button :icon="EditPen" plain @click="quickManualConfirm">
                    人工改选
                  </el-button>
                </div>
              </div>
              <div
                v-else-if="manualForm.nodeId && !activeResultId"
                class="reason-panel"
              >
                <el-alert
                  :closable="false"
                  title="手动选择节点"
                  :description="`已选择节点：${selectedManualNodeLabel}`"
                  type="info"
                />
                <el-alert
                  :closable="false"
                  title="操作提示"
                  description="您正在手动选择节点，点击下方按钮确认人工挂链。"
                  type="info"
                />
                <div class="reason-panel__actions">
                  <el-button
                    :icon="EditPen"
                    type="primary"
                    @click="quickManualConfirm"
                  >
                    确认人工挂链
                  </el-button>
                </div>
              </div>
              <div v-else class="ai-empty-state">
                <div class="ai-empty-state__icon">
                  <el-icon :size="48"><Search /></el-icon>
                </div>
                <div class="ai-empty-state__text">
                  选择候选节点查看 AI 分析依据
                </div>
              </div>
            </section>
          </div>

          <!-- Operation Logs Link -->
          <div class="logs-link">
            <el-button
              text
              type="primary"
              @click="$router.push('/matching/logs')"
            >
              查看操作日志 →
            </el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Manual Confirm Dialog -->
    <el-dialog v-model="manualDialogVisible" title="人工改选节点" width="560px">
      <el-form label-position="top">
        <el-form-item label="目标节点">
          <el-select
            v-model="manualForm.nodeId"
            filterable
            placeholder="选择产业链节点"
            style="width: 100%"
          >
            <el-option
              v-for="node in flattenedNodes"
              :key="node.id"
              :label="node.label"
              :value="node.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="人工确认理由">
          <el-input
            v-model="manualForm.reason"
            :rows="4"
            maxlength="300"
            show-word-limit
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button
          :loading="manualSubmitting"
          type="primary"
          @click="submitManualConfirm"
        >
          确认人工挂链
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ===== Shared ===== */
.matching-tabs {
  margin-top: 4px;
}
.matching-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.matching-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
}
.single-enterprise {
  display: grid;
  gap: 16px;
}
.single-enterprise__toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.logs-link {
  text-align: center;
  padding: 12px 0;
}

/* ===== Matching Panels (light theme, consistent with project) ===== */
.matching-panel {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(135deg, rgb(255 255 255 / 90%), rgb(246 251 255 / 74%)),
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 12%), transparent 34%);
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 16px 42px rgb(16 64 120 / 8%);
  padding: 18px;
  position: relative;
}

.matching-panel h3 {
  color: #102033;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
}

.matching-panel--enterprise::before {
  background: linear-gradient(180deg, #1f6fff, #08bfd3);
  content: "";
  inset: 18px auto 18px 0;
  position: absolute;
  width: 3px;
  border-radius: 2px;
}

.matching-panel--enterprise {
  padding-left: 24px;
}

/* Enterprise Summary */
.enterprise-summary__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.enterprise-summary__head strong {
  color: #102033;
  font-size: 18px;
  font-weight: 700;
}

.enterprise-summary dl {
  display: grid;
  gap: 6px 12px;
  grid-template-columns: auto 1fr;
  margin: 0;
}

.enterprise-summary dt {
  color: #63738b;
  font-size: 13px;
}

.enterprise-summary dd {
  color: #102033;
  margin: 0;
  font-size: 13px;
}

.enterprise-summary__tags {
  border-top: 1px solid #e8ecf1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 12px;
  margin-top: 12px;
}

.matching-panel__hint {
  color: #63738b;
  margin: 0 0 14px;
  font-size: 13px;
}

.matching-panel__status {
  align-items: center;
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.matching-panel__status-text {
  color: #63738b;
  font-size: 13px;
}

.matching-panel__alerts {
  margin-bottom: 14px;
}

/* ===== Candidate List ===== */
.candidate-list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
  padding-right: 4px;
}

.candidate-list__item {
  align-items: flex-start;
  background: #f8fafc;
  border: 1px solid #e8ecf1;
  border-radius: 8px;
  cursor: pointer;
  display: grid;
  gap: 10px;
  grid-template-columns: auto 1fr auto;
  padding: 12px 14px;
  text-align: left;
  transition: all 0.2s ease;
}

.candidate-list__item:hover {
  background: #f0f5ff;
  border-color: #c8d6f0;
}

.candidate-list__item.is-active {
  background: #e8f0ff;
  border-color: #1f6fff;
  box-shadow: 0 0 0 2px rgb(31 111 255 / 12%);
}

.candidate-list__item.is-recommended {
  border-left: 3px solid #1f6fff;
}

.candidate-list__item.is-low-confidence {
  border-left: 3px dashed #d4a017;
}

.candidate-list__item.is-linked {
  border-left: 3px solid #d97706;
}

.candidate-list__item.is-unrecommended {
  opacity: 0.55;
}

.candidate-list__radio {
  align-items: center;
  border: 2px solid #c8d6f0;
  border-radius: 50%;
  display: flex;
  height: 18px;
  justify-content: center;
  margin-top: 2px;
  width: 18px;
}

.candidate-list__item.is-active .candidate-list__radio {
  border-color: #1f6fff;
}

.candidate-list__item.is-active .candidate-list__radio::after {
  background: #1f6fff;
  border-radius: 50%;
  content: "";
  height: 8px;
  width: 8px;
}

.candidate-list__body {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.candidate-list__body strong {
  color: #102033;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-list__body small {
  color: #63738b;
  font-size: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.candidate-list__unrecommended {
  color: #9ba3af;
  font-style: italic;
}

.candidate-list__manual {
  color: #9ba3af;
  font-size: 12px;
}

.candidate-list__link-warning {
  color: #a16207;
  font-weight: 600;
}

/* Ring Confidence Indicator */
.candidate-list__score {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: center;
}

.candidate-list__score .confidence-ring {
  width: 44px;
  height: 44px;
}

.candidate-list__score .confidence-ring__text {
  fill: #102033;
  font-size: 10px;
  font-weight: 700;
}

.candidate-list__score .confidence-ring__track {
  fill: none;
  stroke: #e8ecf1;
  stroke-width: 4;
}

.candidate-list__score .confidence-ring__fill {
  fill: none;
  stroke-width: 4;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.6s ease;
}

.candidate-list__score--unrecommended {
  color: #9ba3af;
  font-size: 14px;
}

/* ===== Reason / Analysis Panel ===== */
.reason-panel {
  display: grid;
  gap: 12px;
}

/* AI Suggestion Card - subtle accent, light theme */
.ai-suggestion-card {
  background:
    linear-gradient(135deg, rgb(248 245 255 / 90%), rgb(240 248 255 / 80%)),
    radial-gradient(circle at 0 0, rgb(139 92 246 / 6%), transparent 50%);
  border: 1px solid rgb(196 181 253 / 40%);
  border-left: 3px solid #8b5cf6;
  border-radius: 0 8px 8px 0;
  padding: 16px;
}

.ai-suggestion-card__header {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-suggestion-card__icon {
  align-items: center;
  color: #7c3aed;
  display: inline-flex;
  font-size: 18px;
}

.ai-suggestion-card__title {
  color: #7c3aed;
  font-size: 14px;
  font-weight: 600;
}

.ai-suggestion-card__content {
  color: #102033;
  font-size: 14px;
  line-height: 1.6;
}

.ai-suggestion-card__summary {
  color: #63738b;
  font-size: 13px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e8ecf1;
}

/* Risk Warning */
.risk-callout {
  background: #fffbeb;
  border-left: 3px solid #d4a017;
  border-radius: 0 8px 8px 0;
  color: #92600a;
  font-size: 13px;
  padding: 10px 14px;
}

/* Existing Link Warning */
.link-callout {
  background: #fff7ed;
  border-left: 3px solid #d97706;
  border-radius: 0 8px 8px 0;
  color: #9a3412;
  font-size: 13px;
  padding: 10px 14px;
}

/* Decision Tip */
.decision-callout {
  background: #f0f5ff;
  border-left: 3px solid #1f6fff;
  border-radius: 0 8px 8px 0;
  color: #102033;
  font-size: 13px;
  padding: 10px 14px;
}

/* Action Buttons */
.reason-panel__actions {
  display: flex;
  gap: 10px;
  padding-top: 8px;
}

/* Empty / Initial State */
.empty-note {
  color: #9ba3af;
  font-size: 14px;
  text-align: center;
  padding: 40px 20px;
}

.ai-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 60px 20px;
  color: #9ba3af;
}

.ai-empty-state__icon {
  opacity: 0.4;
}

.ai-empty-state__text {
  color: #63738b;
  font-size: 14px;
}

/* Loading skeleton */
.ai-skeleton {
  display: grid;
  gap: 12px;
}

.ai-skeleton__card {
  background: #f0f2f5;
  border: 1px solid #e8ecf1;
  border-radius: 8px;
  height: 80px;
  position: relative;
  overflow: hidden;
}

.ai-skeleton__card::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.6),
    transparent
  );
  animation: ai-shimmer 1.5s infinite;
}

@keyframes ai-shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

.ai-loading-hint {
  color: #1f6fff;
  font-size: 14px;
  text-align: center;
  animation: ai-pulse 2s ease-in-out infinite;
}

@keyframes ai-pulse {
  0%,
  100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}

/* Evidence table */
.evidence-table {
  background: #f8fafc;
  border: 1px solid #e8ecf1;
  border-radius: 8px;
  overflow: hidden;
}

.evidence-table div {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
}

.evidence-table div > span,
.evidence-table div > strong {
  border-bottom: 1px solid #e8ecf1;
  padding: 10px 12px;
}

.evidence-table div:last-child > span,
.evidence-table div:last-child > strong {
  border-bottom: 0;
}

.evidence-table div > span {
  background: #f0f2f5;
  color: #63738b;
  font-size: 13px;
}

.evidence-table div > strong {
  color: #102033;
  font-size: 13px;
  font-weight: 500;
}

/* ===== Workbench Layout ===== */
.matching-workbench {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(260px, 320px) minmax(320px, 1fr) minmax(
      300px,
      1fr
    );
}

.matching-workbench > section {
  min-width: 0;
}

@media (max-width: 1200px) {
  .matching-workbench {
    grid-template-columns: 1fr 1fr;
  }
  .matching-panel--candidates {
    grid-column: 1 / -1;
  }
}

@media (max-width: 860px) {
  .matching-workbench {
    grid-template-columns: 1fr;
  }
  .matching-panel--candidates {
    grid-column: auto;
  }
  .reason-panel__actions {
    flex-direction: column;
  }
}

@media (max-width: 720px) {
  .reason-panel__actions {
    flex-direction: column;
  }
}

/* ===== Markdown Body Styles ===== */
.markdown-body {
  font-size: 13px;
  line-height: 1.6;
  color: #102033;
  word-break: break-word;
}

.markdown-body :first-child {
  margin-top: 0;
}
.markdown-body :last-child {
  margin-bottom: 0;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 10px 0 4px;
  font-weight: 600;
  color: #102033;
}

.markdown-body h1 {
  font-size: 15px;
}
.markdown-body h2 {
  font-size: 14px;
}
.markdown-body h3 {
  font-size: 13px;
}

.markdown-body p {
  margin: 5px 0;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 20px;
  margin: 5px 0;
}

.markdown-body li {
  margin: 3px 0;
}

.markdown-body li > p {
  margin: 3px 0;
}

.markdown-body strong {
  font-weight: 600;
  color: #102033;
}

.markdown-body code {
  background: #f0f2f5;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}

.markdown-body pre {
  background: #f8fafc;
  border: 1px solid #e8ecf1;
  border-radius: 6px;
  padding: 10px;
  margin: 6px 0;
  overflow-x: auto;
}

.markdown-body pre code {
  background: none;
  padding: 0;
}

.markdown-body blockquote {
  border-left: 2px solid #1f6fff;
  padding-left: 10px;
  margin: 6px 0;
  color: #63738b;
}

.markdown-body hr {
  border: none;
  border-top: 1px solid #e8ecf1;
  margin: 8px 0;
}

.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 6px 0;
  font-size: 12px;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #e8ecf1;
  padding: 4px 8px;
  text-align: left;
}

.markdown-body th {
  background: #f0f2f5;
  font-weight: 600;
}
</style>
