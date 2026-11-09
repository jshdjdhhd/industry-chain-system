<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { fetchOperationLogs } from "@/api/operationLog";
import type { OperationLog } from "@/types/domain";

const logs = ref<OperationLog[]>([]);
const total = ref(0);
const loading = ref(false);

const query = reactive({
  pageNum: 1,
  pageSize: 20,
});

async function loadLogs() {
  loading.value = true;
  try {
    const result = await fetchOperationLogs(query);
    logs.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  query.pageNum = page;
  loadLogs();
}

function handleSizeChange(size: number) {
  query.pageSize = size;
  query.pageNum = 1;
  loadLogs();
}

onMounted(loadLogs);
</script>

<template>
  <div v-loading="loading" class="operation-log page">
    <div class="page__header">
      <h2 class="page__title">全部操作</h2>
    </div>
    <el-table :data="logs" border stripe>
      <el-table-column label="时间" min-width="160" prop="createdAt" />
      <el-table-column label="操作类型" min-width="150" prop="action" />
      <el-table-column label="对象" min-width="200" prop="targetType" />
      <el-table-column label="操作人" min-width="100" prop="operator" />
    </el-table>
    <el-pagination
      v-if="total > 0"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
