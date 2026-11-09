<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { Refresh, Search } from "@element-plus/icons-vue";
import {
  fetchEnterpriseImportRecords,
  type ImportRecordQuery,
} from "@/api/enterprise";
import AppTable from "@/components/AppTable.vue";
import SearchBar from "@/components/SearchBar.vue";
import StatusTag from "@/components/StatusTag.vue";
import type { EnterpriseImportRecord } from "@/types/domain";

const loading = ref(false);
const records = ref<EnterpriseImportRecord[]>([]);
const total = ref(0);

const query = reactive<ImportRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: "",
  status: "",
});

async function loadRecords() {
  loading.value = true;
  try {
    const result = await fetchEnterpriseImportRecords({ ...query });
    records.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function resetQuery() {
  query.keyword = "";
  query.status = "";
  query.pageNum = 1;
  loadRecords();
}

onMounted(loadRecords);
</script>

<template>
  <div class="page">
    <div class="page__header">
      <div>
        <h2 class="page__title">导入记录</h2>
        <p class="page__description">
          追踪企业批量导入批次、成功数量、失败数量和行级错误原因。
        </p>
      </div>
      <el-button :icon="Refresh" plain @click="loadRecords">刷新</el-button>
    </div>

    <SearchBar :model-value="query">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="文件名 / 操作人"
          @keyup.enter="loadRecords"
        />
      </el-form-item>
      <el-form-item label="导入状态">
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="部分失败" value="PARTIAL_FAILED" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <template #actions>
        <el-button :icon="Search" type="primary" @click="loadRecords"
          >查询</el-button
        >
        <el-button plain @click="resetQuery">重置</el-button>
      </template>
    </SearchBar>

    <AppTable
      :data="records"
      :loading="loading"
      :page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      @update:page="
        query.pageNum = $event;
        loadRecords();
      "
      @update:page-size="
        query.pageSize = $event;
        query.pageNum = 1;
        loadRecords();
      "
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="record-failures">
            <h3>失败明细</h3>
            <el-table v-if="row.failures.length" :data="row.failures" border>
              <el-table-column label="行号" width="90" prop="rowNumber" />
              <el-table-column
                label="企业名称"
                min-width="180"
                prop="enterpriseName"
              />
              <el-table-column label="失败原因" min-width="260" prop="reason" />
            </el-table>
            <div v-else class="empty-note">该批次无失败明细。</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="文件名称" min-width="260" prop="fileName" />
      <el-table-column label="导入时间" min-width="170" prop="importedAt" />
      <el-table-column label="操作人" min-width="100" prop="operator" />
      <el-table-column label="总行数" min-width="90" prop="totalCount" />
      <el-table-column label="成功" min-width="90" prop="successCount" />
      <el-table-column label="失败" min-width="90" prop="failureCount" />
      <el-table-column label="状态" min-width="120">
        <template #default="{ row }">
          <StatusTag :status="row.status" />
        </template>
      </el-table-column>
    </AppTable>
  </div>
</template>

<style scoped>
.record-failures {
  background: #f7faff;
  display: grid;
  gap: 10px;
  padding: 14px 18px;
}

.record-failures h3 {
  color: #0f1f3d;
  font-size: 16px;
  margin: 0;
}
</style>
