<script setup lang="ts">
import { computed, ref } from "vue";
import { Close, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { revokeMatchingTask } from "@/api/matching";
import { unlinkEnterpriseFromNode } from "@/api/chain";
import { useMatchingStore } from "@/stores/matching";
import { buildMatchingOperationLogs } from "./matchingViewModel";
import type { MatchingOperationLog } from "@/types/domain";

const matchingStore = useMatchingStore();
const searchKeyword = ref("");
const filterType = ref<string>("all");
const currentPage = ref(1);
const pageSize = ref(20);
const operationRevoking = ref<string | null>(null);

const operationLogs = computed<MatchingOperationLog[]>(() => {
  const currentTask = matchingStore.currentTask;
  return buildMatchingOperationLogs({
    enterprise: null,
    task: currentTask,
    statusLabel: currentTask?.status ?? "",
  });
});

const ACTION_FILTER_MAP: Record<string, string[]> = {
  批量挂链: ["批量智能推荐"],
  单企业挂链: ["智能挂链", "人工挂链"],
  人工智能分析: ["人工智能分析"],
  取消挂链: ["取消挂链"],
  撤销操作: ["撤销操作"],
};

const filteredLogs = computed(() => {
  let logs = operationLogs.value;
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase();
    logs = logs.filter(
      (l) =>
        l.content.toLowerCase().includes(kw) ||
        l.action.toLowerCase().includes(kw) ||
        String(l.id).includes(kw),
    );
  }
  if (filterType.value !== "all") {
    const matchActions = ACTION_FILTER_MAP[filterType.value] ?? [filterType.value];
    logs = logs.filter((l) => matchActions.some((a) => l.action.includes(a)));
  }
  return logs;
});

const paginatedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredLogs.value.slice(start, start + pageSize.value);
});

async function revokeOperation(log: MatchingOperationLog) {
  if (!log.revoke) return;
  try {
    await ElMessageBox.confirm(
      "撤回后会删除本次推荐结果；如果已经采纳或人工确认，会同时解绑正式挂链关系。",
      "确认撤回操作",
      { confirmButtonText: "撤回", cancelButtonText: "取消", type: "warning" },
    );
  } catch {
    return;
  }
  operationRevoking.value = log.id;
  try {
    if (log.revoke.type === "TASK") {
      await revokeMatchingTask(log.revoke.taskId);
    } else {
      await unlinkEnterpriseFromNode(log.revoke.nodeId, log.revoke.enterpriseId);
    }
    ElMessage.success("操作已撤回");
  } finally {
    operationRevoking.value = null;
  }
}
</script>

<template>
  <div class="page">
    <div class="page__header">
      <div>
        <h2 class="page__title">操作日志</h2>
        <p class="page__description">
          查看智能挂链系统的所有操作记录，支持筛选和撤回。
        </p>
      </div>
    </div>

    <div class="logs-toolbar">
      <el-input
        v-model="searchKeyword"
        :prefix-icon="Search"
        placeholder="搜索操作内容..."
        clearable
        style="width: 260px"
      />
      <el-select v-model="filterType" placeholder="操作类型" style="width: 160px">
        <el-option label="全部" value="all" />
        <el-option label="批量挂链" value="批量挂链" />
        <el-option label="单企业挂链" value="单企业挂链" />
        <el-option label="人工智能分析" value="人工智能分析" />
        <el-option label="取消挂链" value="取消挂链" />
        <el-option label="撤销操作" value="撤销操作" />
      </el-select>
      <el-button :icon="Search" type="primary" @click="currentPage = 1">查询</el-button>
    </div>

    <section class="surface logs-table">
      <el-table :data="paginatedLogs" border row-key="id">
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="logs-table__details">
              <div
                v-for="detail in row.details"
                :key="detail.title"
                class="logs-table__detail"
              >
                <strong>{{ detail.title }}</strong>
                <span style="white-space: pre-line">{{ detail.description }}</span>
                <small v-if="detail.evidence">{{ detail.evidence }}</small>
              </div>
              <span v-if="!row.details?.length" class="empty-note">
                暂无可展开内容。
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" min-width="170" prop="createdAt" />
        <el-table-column label="操作人" min-width="100" prop="operator" />
        <el-table-column label="操作类型" min-width="120" prop="action" />
        <el-table-column label="操作内容" min-width="240" prop="content" />
        <el-table-column label="结果" min-width="90" prop="result" />
        <el-table-column label="操作" min-width="110">
          <template #default="{ row }">
            <el-button
              v-if="row.revoke"
              :icon="Close"
              :loading="operationRevoking === row.id"
              link
              type="danger"
              @click="revokeOperation(row)"
            >
              撤回
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="logs-table__pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredLogs.length"
          layout="total, prev, pager, next"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.logs-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.logs-table {
  display: grid;
  gap: 16px;
}

.logs-table__details {
  background: #f8fbff;
  border: 1px solid #dbe5f2;
  border-radius: 6px;
  display: grid;
  gap: 10px;
  margin: 4px 8px;
  padding: 12px;
}

.logs-table__detail {
  display: grid;
  gap: 4px;
}

.logs-table__detail strong {
  color: #182b49;
  font-size: 14px;
}

.logs-table__detail span,
.logs-table__detail small {
  color: #667085;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.logs-table__detail small {
  background: #ffffff;
  border: 1px solid #e1e8f2;
  border-radius: 4px;
  color: #475467;
  padding: 8px;
}

.logs-table__pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}
</style>
