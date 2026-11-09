<template>
  <section class="app-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      row-key="id"
      @selection-change="$emit('selection-change', $event)"
    >
      <slot />
    </el-table>

    <div class="app-table__footer">
      <span class="app-table__total">共 {{ total }} 条</span>
      <el-pagination
        background
        :current-page="page"
        layout="prev, pager, next, sizes, jumper"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        @current-change="$emit('update:page', $event)"
        @size-change="$emit('update:pageSize', $event)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  data: unknown[];
  loading?: boolean;
  total: number;
  page: number;
  pageSize: number;
}>();

defineEmits<{
  "update:page": [page: number];
  "update:pageSize": [pageSize: number];
  "selection-change": [rows: unknown[]];
}>();
</script>

<style scoped>
.app-table {
  backdrop-filter: blur(18px);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 90%), rgb(247 251 255 / 82%)),
    rgb(255 255 255 / 86%);
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 18px 50px rgb(16 64 120 / 9%);
  overflow-x: auto;
  overflow-y: hidden;
}

.app-table :deep(.el-table) {
  background: transparent;
  min-width: 920px;
}

.app-table :deep(.el-table__inner-wrapper::before) {
  background: #dbe9f8;
}

.app-table :deep(.el-table__header-wrapper th) {
  background: linear-gradient(180deg, #f6faff, #edf6ff);
}

.app-table :deep(.el-table__row) {
  transition:
    background 180ms ease,
    transform 180ms ease;
}

.app-table :deep(.el-table__row:hover) {
  transform: translateX(2px);
}

.app-table__footer {
  align-items: center;
  background: rgb(248 251 255 / 72%);
  border-top: 1px solid #e6edf7;
  display: flex;
  justify-content: space-between;
  padding: 14px 16px;
}

.app-table__total {
  color: #536275;
  font-size: 14px;
}
</style>
