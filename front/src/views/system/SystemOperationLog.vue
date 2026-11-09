<script setup lang="ts">
import { ref, onMounted } from "vue";
import { fetchOperationLogs } from "@/api/operationLog";
import type { OperationLog } from "@/types/domain";

const loading = ref(false);
const tableData = ref<OperationLog[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(20);

const filterOperator = ref("");
const filterAction = ref("");
const filterTargetType = ref("");

async function loadData() {
  loading.value = true;
  try {
    const res = await fetchOperationLogs({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      operator: filterOperator.value || undefined,
      action: filterAction.value || undefined,
      targetType: filterTargetType.value || undefined,
    });
    tableData.value = res.records;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pageNum.value = 1;
  loadData();
}

onMounted(loadData);
</script>

<template>
  <div class="page">
    <h2 class="page__title">操作日志</h2>

    <div class="search-bar">
      <el-input v-model="filterOperator" placeholder="操作人" clearable style="width: 150px" @keyup.enter="handleSearch" />
      <el-input v-model="filterAction" placeholder="操作类型" clearable style="width: 150px" @keyup.enter="handleSearch" />
      <el-select v-model="filterTargetType" placeholder="目标类型" clearable style="width: 150px">
        <el-option label="企业" value="enterprise" />
        <el-option label="标签" value="tag" />
        <el-option label="产业链" value="chain" />
        <el-option label="节点" value="chain_node" />
        <el-option label="匹配" value="matching_result" />
        <el-option label="用户" value="user" />
      </el-select>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="action" label="操作类型" width="150" />
      <el-table-column prop="targetType" label="目标类型" width="120" />
      <el-table-column prop="targetId" label="目标ID" width="100" />
      <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
    </el-table>

    <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="loadData" />
  </div>
</template>

<style scoped>
.page { padding: 20px; }
.page__title { margin-bottom: 20px; font-size: 20px; font-weight: 600; }
.search-bar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
</style>
