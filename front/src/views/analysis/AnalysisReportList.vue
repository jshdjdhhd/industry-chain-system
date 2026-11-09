<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Refresh, Search } from "@element-plus/icons-vue";
import AppTable from "@/components/AppTable.vue";
import SearchBar from "@/components/SearchBar.vue";
import {
  fetchAnalysisReports,
  type AnalysisReportQuery,
} from "@/api/analysis";
import { fetchChains } from "@/api/chain";
import type {
  AnalysisReportStatus,
  AnalysisReportTask,
  IndustryChain,
} from "@/types/domain";
import { statusText, statusType } from "./reportStatus";

interface ReportQuery {
  pageNum: number;
  pageSize: number;
  chainId?: number | "";
  status?: AnalysisReportStatus | "";
}

const router = useRouter();

const loading = ref(false);
const errorMessage = ref<string | null>(null);
const records = ref<AnalysisReportTask[]>([]);
const total = ref(0);
const chains = ref<IndustryChain[]>([]);

const query = reactive<ReportQuery>({
  pageNum: 1,
  pageSize: 10,
  chainId: "",
  status: "",
});

const statusOptions: { label: string; value: AnalysisReportStatus }[] = [
  { label: "已创建", value: "CREATED" },
  { label: "处理中", value: "PROCESSING" },
  { label: "已完成", value: "COMPLETED" },
  { label: "失败", value: "FAILED" },
  { label: "已取消", value: "CANCELLED" },
];

function buildParams(): AnalysisReportQuery {
  return {
    pageNum: query.pageNum,
    pageSize: query.pageSize,
    chainId: query.chainId === "" ? undefined : Number(query.chainId),
    status: query.status === "" ? undefined : (query.status as string),
  };
}

async function loadReports() {
  loading.value = true;
  errorMessage.value = null;
  try {
    const result = await fetchAnalysisReports(buildParams());
    records.value = result.records;
    total.value = result.total;
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "加载报告列表失败";
  } finally {
    loading.value = false;
  }
}

async function loadChains() {
  try {
    chains.value = await fetchChains();
  } catch {
    chains.value = [];
  }
}

function resetQuery() {
  query.chainId = "";
  query.status = "";
  query.pageNum = 1;
  loadReports();
}

function search() {
  query.pageNum = 1;
  loadReports();
}

function openDetail(row: AnalysisReportTask) {
  router.push(`/analysis/reports/${row.id}`);
}

onMounted(() => {
  loadChains();
  loadReports();
});
</script>

<template>
  <div class="analysis-report-list" v-loading="loading">
    <div class="analysis-report-list__header">
      <div>
        <h2 class="analysis-report-list__title">报告历史</h2>
        <p class="analysis-report-list__description">
          查看健康分析报告任务的执行进度、状态与结果，点击行可进入详情预览或下载。
        </p>
      </div>
      <el-button :icon="Refresh" plain @click="loadReports">刷新</el-button>
    </div>

    <SearchBar :model-value="query">
      <el-form-item label="产业链">
        <el-select
          v-model="query.chainId"
          clearable
          placeholder="全部产业链"
          @change="search"
        >
          <el-option
            v-for="chain in chains"
            :key="chain.id"
            :label="chain.name"
            :value="chain.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="query.status"
          clearable
          placeholder="全部状态"
          @change="search"
        >
          <el-option
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <template #actions>
        <el-button :icon="Search" type="primary" @click="search"
          >查询</el-button
        >
        <el-button plain @click="resetQuery">重置</el-button>
      </template>
    </SearchBar>

    <div v-if="errorMessage" class="analysis-report-list__error">
      <el-alert type="error" :title="errorMessage" :closable="false" />
    </div>

    <AppTable
      :data="records"
      :loading="loading"
      :page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      @update:page="
        query.pageNum = $event;
        loadReports();
      "
      @update:page-size="
        query.pageSize = $event;
        query.pageNum = 1;
        loadReports();
      "
    >
      <el-table-column label="报告ID" min-width="100" prop="id" />
      <el-table-column
        label="产业链"
        min-width="160"
        prop="chainName"
      />
      <el-table-column label="范围" min-width="120">
        <template #default="{ row }">
          {{ row.scopeType === "ALL_CHAIN" ? "整链" : "选定节点" }}
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="失败原因"
        min-width="220"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span v-if="row.errorMessage" class="analysis-report-list__error-text">
            {{ row.errorMessage }}
          </span>
          <span v-else class="analysis-report-list__muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170" prop="createdAt" />
      <el-table-column label="完成时间" min-width="170">
        <template #default="{ row }">
          {{ row.completedAt ?? "—" }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)"
            >查看详情</el-button
          >
        </template>
      </el-table-column>
    </AppTable>
  </div>
</template>

<style scoped>
.analysis-report-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.analysis-report-list__header {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.analysis-report-list__title {
  color: #0b56d8;
  font-size: 18px;
  font-weight: 800;
  margin: 0;
}

.analysis-report-list__description {
  color: #536275;
  font-size: 13px;
  margin: 4px 0 0;
}

.analysis-report-list__error {
  margin: 0;
}

.analysis-report-list__error-text {
  color: #d23f3f;
}

.analysis-report-list__muted {
  color: #9aa6b8;
}
</style>
