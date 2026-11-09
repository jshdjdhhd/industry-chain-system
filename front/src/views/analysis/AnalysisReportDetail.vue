<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowLeft, Download, Refresh } from "@element-plus/icons-vue";
import {
  downloadAnalysisReport,
  fetchAnalysisReportDetail,
  retryAnalysisReport,
  type AnalysisReportDownloadFormat,
} from "@/api/analysis";
import type {
  AnalysisReportDetail,
  AnalysisReportStatus,
} from "@/types/domain";
import { statusText, statusType } from "./reportStatus";

const POLL_INTERVAL_MS = 2000;
const POLL_MAX_DURATION_MS = 5 * 60 * 1000;
const TERMINAL_STATUSES: AnalysisReportStatus[] = [
  "COMPLETED",
  "FAILED",
  "CANCELLED",
];

const route = useRoute();
const router = useRouter();

const reportId = Number(route.params.id);

const loading = ref(false);
const detail = ref<AnalysisReportDetail | null>(null);
const errorMessage = ref<string | null>(null);
const downloading = ref(false);
const retrying = ref(false);
// 轮询 in-flight 守卫，避免手动刷新与轮询并发覆盖最新响应
const pollInFlight = ref(false);

let pollTimer: ReturnType<typeof setTimeout> | null = null;
let pollStartedAt = 0;

const isProcessing = computed(
  () => detail.value?.status === "PROCESSING" || detail.value?.status === "CREATED",
);
const isCompleted = computed(() => detail.value?.status === "COMPLETED");
const isFailed = computed(() => detail.value?.status === "FAILED");
const isCancelled = computed(() => detail.value?.status === "CANCELLED");

function clearPollTimer() {
  if (pollTimer !== null) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function shouldStopPolling(): boolean {
  if (!detail.value) return true;
  if (TERMINAL_STATUSES.includes(detail.value.status)) return true;
  if (Date.now() - pollStartedAt > POLL_MAX_DURATION_MS) return true;
  return false;
}

function schedulePoll() {
  clearPollTimer();
  pollTimer = setTimeout(pollOnce, POLL_INTERVAL_MS);
}

async function pollOnce() {
  if (shouldStopPolling()) {
    clearPollTimer();
    return;
  }
  pollInFlight.value = true;
  try {
    const result = await fetchAnalysisReportDetail(reportId);
    detail.value = result;
    if (!shouldStopPolling()) {
      schedulePoll();
    } else {
      clearPollTimer();
    }
  } catch {
    clearPollTimer();
  } finally {
    pollInFlight.value = false;
  }
}

async function loadDetail() {
  // 轮询 in-flight 时不重复加载，避免旧响应覆盖新响应的重入竞态
  if (pollInFlight.value) return;
  loading.value = true;
  errorMessage.value = null;
  try {
    const result = await fetchAnalysisReportDetail(reportId);
    detail.value = result;
    if (!TERMINAL_STATUSES.includes(result.status)) {
      pollStartedAt = Date.now();
      schedulePoll();
    }
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "加载报告详情失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (Number.isNaN(reportId)) {
    errorMessage.value = "报告ID无效";
    router.push("/analysis/reports");
    return;
  }
  loadDetail();
});
onBeforeUnmount(clearPollTimer);

async function handleDownload(format: AnalysisReportDownloadFormat) {
  if (!detail.value) return;
  downloading.value = true;
  try {
    const blob = await downloadAnalysisReport(reportId, format);
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    const title = detail.value.title ?? `report-${detail.value.id}`;
    link.download = `${title}.${format}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "下载报告失败",
    );
  } finally {
    downloading.value = false;
  }
}

async function handleRetry() {
  if (!detail.value) return;
  try {
    await ElMessageBox.confirm(
      "确认重试该报告任务？将创建一个新的分析任务。",
      "重试确认",
      { type: "warning" },
    );
  } catch {
    return;
  }
  retrying.value = true;
  try {
    const task = await retryAnalysisReport(reportId);
    ElMessage.success("已提交重试，即将跳转到新任务");
    router.push(`/analysis/reports/${task.id}`);
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "重试失败",
    );
  } finally {
    retrying.value = false;
  }
}

function goBack() {
  router.push("/analysis/reports");
}
</script>

<template>
  <div class="analysis-report-detail" v-loading="loading">
    <div class="analysis-report-detail__header">
      <el-button :icon="ArrowLeft" link @click="goBack">返回列表</el-button>
      <h2 class="analysis-report-detail__title">
        {{ detail?.title ?? `报告 #${reportId}` }}
      </h2>
      <el-tag v-if="detail" :type="statusType(detail.status)">
        {{ statusText(detail.status) }}
      </el-tag>
    </div>

    <div v-if="errorMessage" class="analysis-report-detail__error">
      <el-alert type="error" :title="errorMessage" :closable="false" />
    </div>

    <template v-if="detail">
      <div class="analysis-report-detail__meta">
        <div class="analysis-report-detail__meta-item">
          <span class="analysis-report-detail__meta-label">产业链</span>
          <span>{{ detail.chainName ?? detail.chainId }}</span>
        </div>
        <div class="analysis-report-detail__meta-item">
          <span class="analysis-report-detail__meta-label">范围</span>
          <span>{{ detail.scopeType === "ALL_CHAIN" ? "整链" : "选定节点" }}</span>
        </div>
        <div v-if="detail.retryCount" class="analysis-report-detail__meta-item">
          <span class="analysis-report-detail__meta-label">重试次数</span>
          <span>{{ detail.retryCount }}</span>
        </div>
        <div class="analysis-report-detail__meta-item">
          <span class="analysis-report-detail__meta-label">创建时间</span>
          <span>{{ detail.createdAt ?? "—" }}</span>
        </div>
        <div class="analysis-report-detail__meta-item">
          <span class="analysis-report-detail__meta-label">完成时间</span>
          <span>{{ detail.completedAt ?? "—" }}</span>
        </div>
      </div>

      <div v-if="isProcessing" class="analysis-report-detail__processing">
        <el-icon class="analysis-report-detail__spinner">
          <Refresh />
        </el-icon>
        <span>报告生成中，每 2 秒自动刷新…</span>
        <el-button
          :icon="Refresh"
          plain
          :loading="pollInFlight"
          @click="loadDetail"
          >立即刷新</el-button
        >
      </div>

      <div v-if="isFailed" class="analysis-report-detail__failure">
        <el-alert
          type="error"
          :title="detail.errorMessage ?? '报告生成失败'"
          :closable="false"
        />
        <el-button
          :icon="Refresh"
          type="primary"
          :loading="retrying"
          @click="handleRetry"
        >
          重试任务
        </el-button>
      </div>

      <div v-if="isCancelled" class="analysis-report-detail__cancelled">
        <el-alert type="info" title="该任务已取消" :closable="false" />
        <el-button
          :icon="Refresh"
          type="primary"
          :loading="retrying"
          @click="handleRetry"
        >
          重试任务
        </el-button>
      </div>

      <div v-if="isCompleted" class="analysis-report-detail__completed">
        <div class="analysis-report-detail__actions">
          <el-button
            :icon="Download"
            type="primary"
            :loading="downloading"
            @click="handleDownload('pdf')"
          >
            下载 PDF
          </el-button>
          <el-button
            :icon="Download"
            plain
            :loading="downloading"
            @click="handleDownload('md')"
          >
            下载 MD
          </el-button>
        </div>
        <iframe
          v-if="detail.contentHtml"
          class="analysis-report-detail__iframe"
          sandbox=""
          :srcdoc="detail.contentHtml"
          title="健康分析报告预览"
        />
        <el-empty
          v-else
          description="报告内容暂未生成"
          :image-size="120"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.analysis-report-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analysis-report-detail__header {
  align-items: center;
  display: flex;
  gap: 12px;
}

.analysis-report-detail__title {
  color: #0b56d8;
  flex: 1;
  font-size: 18px;
  font-weight: 800;
  margin: 0;
}

.analysis-report-detail__meta {
  background: #ffffff;
  border: 1px solid rgb(216 229 245 / 82%);
  border-radius: 8px;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  padding: 14px 16px;
}

.analysis-report-detail__meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.analysis-report-detail__meta-label {
  color: #536275;
  font-size: 12px;
}

.analysis-report-detail__processing {
  align-items: center;
  background: rgb(255 251 230 / 80%);
  border: 1px solid rgb(230 162 60 / 30%);
  border-radius: 8px;
  display: flex;
  gap: 10px;
  padding: 14px 16px;
}

.analysis-report-detail__spinner {
  animation: analysis-report-spin 1s linear infinite;
  color: #e6a23c;
}

@keyframes analysis-report-spin {
  to {
    transform: rotate(360deg);
  }
}

.analysis-report-detail__failure,
.analysis-report-detail__cancelled {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.analysis-report-detail__completed {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.analysis-report-detail__actions {
  display: flex;
  gap: 10px;
}

.analysis-report-detail__iframe {
  background: #ffffff;
  border: 1px solid rgb(216 229 245 / 82%);
  border-radius: 8px;
  min-height: 720px;
  width: 100%;
}
</style>
