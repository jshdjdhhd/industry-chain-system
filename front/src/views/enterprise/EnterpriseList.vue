<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ElMessage,
  ElMessageBox,
  type UploadRequestOptions,
} from "element-plus";
import {
  CircleCheck,
  Delete,
  Download,
  Files,
  Plus,
  Search,
  Upload,
} from "@element-plus/icons-vue";
import {
  batchInboundEnterprises,
  batchOutboundEnterprises,
  deleteEnterprise,
  fetchEnterprises,
  importEnterprises,
  inboundEnterprise,
  outboundEnterprise,
  type EnterpriseQuery,
  type ImportResult,
} from "@/api/enterprise";
import { fetchTags } from "@/api/tag";
import { fetchChains, fetchNodeTree } from "@/api/chain";
import AppTable from "@/components/AppTable.vue";
import SearchBar from "@/components/SearchBar.vue";
import StatusTag from "@/components/StatusTag.vue";
import { useEnterpriseStore } from "@/stores/enterprise";
import type {
  ChainNode,
  EnterpriseListItem,
  IndustryChain,
  Tag,
} from "@/types/domain";

const router = useRouter();
const route = useRoute();
const enterpriseStore = useEnterpriseStore();
const loading = ref(false);
const importLoading = ref(false);
const importResultVisible = ref(false);
const lastImportResult = ref<ImportResult | null>(null);
const enterprises = ref<EnterpriseListItem[]>([]);
const tags = ref<Tag[]>([]);
const chains = ref<IndustryChain[]>([]);
const nodeTree = ref<ChainNode[]>([]);
const selectedChainId = ref<number | undefined>();
const selectedRows = ref<EnterpriseListItem[]>([]);
const total = ref(0);

const query = reactive<EnterpriseQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: "",
  status: "",
});

const defaultStatus = computed(() =>
  typeof route.meta.defaultStatus === "string" ? route.meta.defaultStatus : "",
);
const lockedStatus = computed(() => route.meta.lockedStatus === true);
const pageTitle = computed(() =>
  typeof route.meta.listTitle === "string" ? route.meta.listTitle : "企业管理",
);
const lockedStatusLabel = computed(() => {
  if (defaultStatus.value === "UNREGISTERED") return "未入库";
  if (defaultStatus.value === "OUTBOUND") return "出库";
  return "全部企业";
});
const pageDescription = computed(() =>
  defaultStatus.value === "UNREGISTERED"
    ? "集中处理已采集或导入但尚未进入正式企业库的数据。"
    : defaultStatus.value === "OUTBOUND"
      ? "集中处理已出库企业，可查看历史信息并决定是否重新入库。"
      : "维护企业基础信息、入库状态、标签和挂链入口。",
);

async function loadEnterprises() {
  loading.value = true;
  try {
    const result = await fetchEnterprises({ ...query });
    enterprises.value = result.records;
    total.value = result.total;
    enterpriseStore.setQueryCache({ ...query });
  } finally {
    loading.value = false;
  }
}

async function loadTags() {
  tags.value = await fetchTags();
}

async function loadChains() {
  chains.value = await fetchChains();
}

async function loadNodeTree(chainId: number) {
  nodeTree.value = await fetchNodeTree(chainId);
}

function handleChainChange(chainId: number | undefined) {
  query.nodeId = undefined;
  nodeTree.value = [];
  if (chainId != null) {
    loadNodeTree(chainId);
  }
}

function resetQuery() {
  query.keyword = "";
  query.status = defaultStatus.value;
  query.tagId = undefined;
  query.nodeId = undefined;
  selectedChainId.value = undefined;
  nodeTree.value = [];
  query.pageNum = 1;
  loadEnterprises();
}

function downloadImportTemplate() {
  const headers =
    "企业名称,统一社会信用代码,法定代表人,主营产品,产值(万元),税收(万元),经营地址";
  const blob = new Blob(["﻿" + headers + "\n"], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "企业导入模板.csv";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

async function runInbound(row: EnterpriseListItem) {
  await inboundEnterprise(row.id);
  ElMessage.success(row.status === "OUTBOUND" ? "企业已重新入库" : "企业已入库");
  await loadEnterprises();
}

async function runOutbound(row: EnterpriseListItem) {
  await ElMessageBox.confirm(
    `确认将“${row.name}”出库？历史挂链关系将保留。`,
    "确认出库",
    {
      confirmButtonText: "出库",
      cancelButtonText: "取消",
      type: "warning",
    },
  );
  await outboundEnterprise(row.id);
  ElMessage.success("企业已出库");
  await loadEnterprises();
}

async function runDelete(row: EnterpriseListItem) {
  await ElMessageBox.confirm(
    `确认删除未入库企业“${row.name}”？删除后不可恢复。`,
    "删除未入库企业",
    {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning",
    },
  );
  await deleteEnterprise(row.id);
  ElMessage.success("未入库企业已删除");
  await loadEnterprises();
}

async function runBatchOutbound() {
  const ids = selectedRows.value
    .filter((row) => row.status === "ACTIVE")
    .map((row) => row.id);
  if (ids.length === 0) {
    ElMessage.warning("请先选择存续企业");
    return;
  }

  await ElMessageBox.confirm(
    `确认批量出库 ${ids.length} 家企业？`,
    "批量出库",
    {
      confirmButtonText: "批量出库",
      cancelButtonText: "取消",
      type: "warning",
    },
  );
  await batchOutboundEnterprises(ids);
  ElMessage.success("批量出库已提交");
  await loadEnterprises();
}

async function runBatchInbound() {
  const ids = selectedRows.value
    .filter((row) => row.status === "UNREGISTERED" || row.status === "OUTBOUND")
    .map((row) => row.id);
  if (ids.length === 0) {
    ElMessage.warning("请先选择未入库或已出库企业");
    return;
  }

  await ElMessageBox.confirm(
    `确认批量入库 ${ids.length} 家企业？`,
    "批量入库",
    {
      confirmButtonText: "批量入库",
      cancelButtonText: "取消",
      type: "warning",
    },
  );
  const successCount = await batchInboundEnterprises(ids);
  ElMessage.success(`批量入库已完成，成功 ${successCount} 家`);
  await loadEnterprises();
}

async function handleImport(options: UploadRequestOptions) {
  importLoading.value = true;
  try {
    const result = await importEnterprises(options.file as File);
    lastImportResult.value = result;
    importResultVisible.value = true;
    ElMessage.success(
      `导入完成：成功 ${result.successCount} 条，失败 ${result.failureCount} 条`,
    );
    await loadEnterprises();
  } finally {
    importLoading.value = false;
  }
}

function handleSelectionChange(rows: unknown[]) {
  selectedRows.value = rows as EnterpriseListItem[];
}

function openDetail(row: EnterpriseListItem) {
  enterpriseStore.setSelectedEnterprise(row);
  router.push(`/enterprises/${row.id}`);
}

onMounted(() => {
  loadTags();
  loadChains();
});

watch(
  () => route.fullPath,
  () => {
    query.status = defaultStatus.value;
    query.pageNum = 1;
    loadEnterprises();
  },
  { immediate: true },
);
</script>

<template>
  <div class="page enterprise-list">
    <div class="page__header">
      <div>
        <h2 class="page__title">{{ pageTitle }}</h2>
        <p class="page__description">{{ pageDescription }}</p>
      </div>
      <div class="toolbar">
        <el-button
          :icon="Plus"
          type="primary"
          @click="router.push('/enterprises/new')"
          >新增企业</el-button
        >
        <el-upload
          accept=".xlsx,.csv"
          :disabled="importLoading"
          :http-request="handleImport"
          :show-file-list="false"
        >
          <el-button :icon="Upload" :loading="importLoading" plain
            >导入</el-button
          >
        </el-upload>
        <el-button :icon="Download" plain @click="downloadImportTemplate"
          >下载模板</el-button
        >
        <el-button
          v-if="lockedStatus"
          :icon="CircleCheck"
          type="success"
          plain
          @click="runBatchInbound"
          >{{ defaultStatus === "OUTBOUND" ? "批量重新入库" : "批量入库" }}</el-button
        >
        <el-button
          v-else
          :icon="Download"
          type="warning"
          plain
          @click="runBatchOutbound"
          >批量出库</el-button
        >
        <el-button
          :icon="Files"
          plain
          @click="router.push('/enterprises/import-records')"
          >导入记录</el-button
        >
      </div>
    </div>

    <section class="enterprise-list__summary" aria-label="企业列表状态">
      <div>
        <span>当前结果</span>
        <strong>{{ total }}</strong>
      </div>
      <div>
        <span>已选企业</span>
        <strong>{{ selectedRows.length }}</strong>
      </div>
      <div>
        <span>当前视图</span>
        <strong>{{ lockedStatus ? lockedStatusLabel : "全部企业" }}</strong>
      </div>
    </section>

    <SearchBar :model-value="query">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="企业名称 / 统一社会信用代码 / 法人"
          @keyup.enter="loadEnterprises"
        />
      </el-form-item>
      <el-form-item label="企业状态">
        <el-select
          v-model="query.status"
          clearable
          :disabled="lockedStatus"
          placeholder="全部状态"
        >
          <el-option label="未入库" value="UNREGISTERED" />
          <el-option label="存续" value="ACTIVE" />
          <el-option label="出库" value="OUTBOUND" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-select
          v-model="query.tagId"
          clearable
          filterable
          placeholder="全部标签"
        >
          <el-option
            v-for="tag in tags"
            :key="tag.id"
            :label="tag.name"
            :value="tag.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="产业链">
        <el-select
          v-model="selectedChainId"
          clearable
          filterable
          placeholder="全部产业链"
          @change="handleChainChange"
        >
          <el-option
            v-for="chain in chains"
            :key="chain.id"
            :label="chain.name"
            :value="chain.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="产业链节点">
        <el-tree-select
          v-model="query.nodeId"
          :data="nodeTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          clearable
          filterable
          check-strictly
          :disabled="!selectedChainId"
          placeholder="全部节点"
          style="width: 220px"
        />
      </el-form-item>
      <template #actions>
        <el-button :icon="Search" type="primary" @click="loadEnterprises"
          >查询</el-button
        >
        <el-button plain @click="resetQuery">重置</el-button>
      </template>
    </SearchBar>

    <AppTable
      :data="enterprises"
      :loading="loading"
      :page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      @selection-change="handleSelectionChange"
      @update:page="
        query.pageNum = $event;
        loadEnterprises();
      "
      @update:page-size="
        query.pageSize = $event;
        query.pageNum = 1;
        loadEnterprises();
      "
    >
      <el-table-column type="selection" width="44" />
      <el-table-column label="企业名称" min-width="220" prop="name" />
      <el-table-column label="法定代表人" min-width="100" prop="legalPerson" />
      <el-table-column
        label="主营产品"
        min-width="180"
        prop="mainProducts"
        show-overflow-tooltip
      />
      <el-table-column label="标签" min-width="180">
        <template #default="{ row }">
          <div class="tag-cell">
            <el-tag
              v-for="tag in row.tags || []"
              :key="tag.id"
              size="small"
              effect="plain"
              :style="tag.color ? { borderColor: tag.color, color: tag.color } : {}"
            >
              {{ tag.name }}
            </el-tag>
            <span v-if="!row.tags?.length" class="empty-inline">-</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column
        label="产值（万元）"
        min-width="120"
        prop="outputValue"
      />
      <el-table-column label="税收（万元）" min-width="120" prop="taxAmount" />
      <el-table-column
        label="经营地址"
        min-width="220"
        prop="address"
        show-overflow-tooltip
      />
      <el-table-column fixed="right" label="操作" min-width="230">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)"
            >详情</el-button
          >
          <el-button
            link
            type="primary"
            @click="router.push(`/enterprises/${row.id}/edit`)"
            >编辑</el-button
          >
          <el-button
            v-if="row.status === 'UNREGISTERED' || row.status === 'OUTBOUND'"
            link
            type="success"
            @click="runInbound(row)"
          >
            {{ row.status === "OUTBOUND" ? "重新入库" : "入库" }}
          </el-button>
          <el-button
            v-if="row.status === 'ACTIVE'"
            link
            type="warning"
            @click="runOutbound(row)"
          >
            出库
          </el-button>
          <el-button
            v-if="row.status === 'UNREGISTERED'"
            :icon="Delete"
            link
            type="danger"
            @click="runDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </AppTable>

    <el-dialog v-model="importResultVisible" title="导入结果" width="680px">
      <div v-if="lastImportResult" class="import-result">
        <div class="import-result__metrics">
          <div>
            <span>成功</span>
            <strong>{{ lastImportResult.successCount }}</strong>
          </div>
          <div>
            <span>失败</span>
            <strong class="is-danger">{{
              lastImportResult.failureCount
            }}</strong>
          </div>
        </div>
        <el-table
          v-if="lastImportResult.failures.length"
          :data="lastImportResult.failures"
          border
        >
          <el-table-column label="行号" width="90" prop="rowNumber" />
          <el-table-column
            label="企业名称"
            min-width="180"
            prop="enterpriseName"
          />
          <el-table-column label="失败原因" min-width="240" prop="reason" />
        </el-table>
        <div v-else class="empty-note">本次导入没有失败记录。</div>
      </div>
      <template #footer>
        <el-button @click="importResultVisible = false">关闭</el-button>
        <el-button
          type="primary"
          @click="router.push('/enterprises/import-records')"
        >
          查看导入记录
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.enterprise-list {
  position: relative;
}

.enterprise-list__summary {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.enterprise-list__summary div {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(135deg, rgb(255 255 255 / 90%), rgb(246 251 255 / 74%)),
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 12%), transparent 34%);
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 16px 42px rgb(16 64 120 / 8%);
  display: grid;
  gap: 6px;
  min-height: 86px;
  overflow: hidden;
  padding: 16px 18px;
  position: relative;
}

.enterprise-list__summary div::before {
  background: linear-gradient(180deg, #1f6fff, #08bfd3);
  content: "";
  inset: 16px auto 16px 0;
  position: absolute;
  width: 3px;
}

.enterprise-list__summary span {
  color: #63738b;
  font-size: 13px;
  font-weight: 700;
}

.enterprise-list__summary strong {
  color: #102033;
  font-size: 28px;
  font-weight: 850;
  line-height: 1;
}

.import-result {
  display: grid;
  gap: 14px;
}

.import-result__metrics {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.import-result__metrics div {
  background: #f7faff;
  border: 1px solid #dbe5f2;
  border-radius: 6px;
  display: grid;
  gap: 4px;
  padding: 12px;
}

.import-result__metrics span {
  color: #667085;
}

.import-result__metrics strong {
  color: #099250;
  font-size: 24px;
}

.import-result__metrics .is-danger {
  color: #d92d20;
}

.tag-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.empty-inline {
  color: #667085;
  font-size: 13px;
}

@media (max-width: 860px) {
  .enterprise-list__summary {
    grid-template-columns: 1fr;
  }
}
</style>
