<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Link, Plus, Search } from "@element-plus/icons-vue";
import { fetchEnterprises } from "@/api/enterprise";
import {
  batchLinkEnterprisesToNode,
  fetchNodeEnterprises,
  linkEnterpriseToNode,
  unlinkEnterpriseFromNode,
} from "@/api/chain";
import StatusTag from "@/components/StatusTag.vue";
import type {
  BatchLinkEnterpriseResult,
  BatchLinkEnterpriseStatus,
  ChainNode,
  EnterpriseListItem,
  NodeEnterprise,
} from "@/types/domain";

const props = defineProps<{
  modelValue: boolean;
  node: ChainNode | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [visible: boolean];
  changed: [];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value),
});

const loading = ref(false);
const searchLoading = ref(false);
const linkedEnterprises = ref<NodeEnterprise[]>([]);
const candidateEnterprises = ref<EnterpriseListItem[]>([]);
const selectedEnterpriseId = ref<number>();
const selectedEnterpriseIds = ref<number[]>([]);
const batchLinking = ref(false);
const batchResult = ref<BatchLinkEnterpriseResult | null>(null);
const query = reactive({
  keyword: "",
  pageNum: 1,
  pageSize: 10,
});

async function loadLinkedEnterprises() {
  if (!props.node) return;
  loading.value = true;
  try {
    const result = await fetchNodeEnterprises(props.node.id);
    linkedEnterprises.value = result.records;
  } finally {
    loading.value = false;
  }
}

async function searchEnterprises() {
  searchLoading.value = true;
  try {
    const result = await fetchEnterprises({
      ...query,
      status: "ACTIVE",
    });
    candidateEnterprises.value = result.records;
  } finally {
    searchLoading.value = false;
  }
}

async function linkEnterprise() {
  if (!props.node || !selectedEnterpriseId.value) {
    ElMessage.warning("请选择企业");
    return;
  }

  await linkEnterpriseToNode(props.node.id, selectedEnterpriseId.value);
  ElMessage.success("企业已挂接到节点");
  selectedEnterpriseId.value = undefined;
  await loadLinkedEnterprises();
  emit("changed");
}

function batchStatusText(status: BatchLinkEnterpriseStatus) {
  const statusMap: Record<string, string> = {
    LINKED: "成功",
    ALREADY_LINKED: "已挂接",
    DUPLICATE_IN_REQUEST: "请求内重复",
    FAILED: "失败",
  };
  return statusMap[status] ?? status;
}

function batchStatusType(status: BatchLinkEnterpriseStatus) {
  if (status === "LINKED") return "success";
  if (status === "FAILED") return "danger";
  if (status === "ALREADY_LINKED" || status === "DUPLICATE_IN_REQUEST")
    return "warning";
  return "info";
}

async function batchLinkEnterprises() {
  if (!props.node || selectedEnterpriseIds.value.length === 0) {
    ElMessage.warning("请选择企业");
    return;
  }

  const enterpriseIds = selectedEnterpriseIds.value.filter(
    (enterpriseId): enterpriseId is number => typeof enterpriseId === "number",
  );
  if (enterpriseIds.length === 0) {
    ElMessage.warning("请选择企业");
    return;
  }

  batchLinking.value = true;
  try {
    const result = await batchLinkEnterprisesToNode(props.node.id, enterpriseIds);
    batchResult.value = result;
    ElMessage.success(
      `批量挂接完成：成功 ${result.linkedCount}，跳过 ${result.skippedCount}，失败 ${result.failedCount}`,
    );
    selectedEnterpriseIds.value = [];
    await loadLinkedEnterprises();
    emit("changed");
  } finally {
    batchLinking.value = false;
  }
}

async function unlinkEnterprise(row: NodeEnterprise) {
  if (!props.node) return;

  await ElMessageBox.confirm(
    `确认从当前节点解绑“${row.enterpriseName}”？`,
    "解绑企业",
    {
      confirmButtonText: "解绑",
      cancelButtonText: "取消",
      type: "warning",
    },
  );
  await unlinkEnterpriseFromNode(props.node.id, row.enterpriseId);
  ElMessage.success("企业已解绑");
  await loadLinkedEnterprises();
  emit("changed");
}

watch(
  () => [props.modelValue, props.node?.id],
  () => {
    if (props.modelValue && props.node) {
      selectedEnterpriseId.value = undefined;
      selectedEnterpriseIds.value = [];
      batchResult.value = null;
      loadLinkedEnterprises();
      searchEnterprises();
    }
  },
);
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="node ? `节点企业：${node.name}` : '节点企业'"
    width="860px"
  >
    <div class="node-enterprise-dialog">
      <section class="node-enterprise-dialog__linker">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="搜索存续企业"
          @keyup.enter="searchEnterprises"
        >
          <template #append>
            <el-button
              :icon="Search"
              :loading="searchLoading"
              @click="searchEnterprises"
            />
          </template>
        </el-input>
        <el-select
          v-model="selectedEnterpriseId"
          filterable
          placeholder="选择企业"
        >
          <el-option
            v-for="enterprise in candidateEnterprises"
            :key="enterprise.id"
            :label="enterprise.name"
            :value="enterprise.id"
          />
        </el-select>
        <el-button :icon="Link" type="primary" @click="linkEnterprise"
          >关联企业</el-button
        >
      </section>

      <section class="node-enterprise-dialog__batch-linker">
        <el-select
          v-model="selectedEnterpriseIds"
          collapse-tags
          collapse-tags-tooltip
          filterable
          multiple
          placeholder="选择多个企业"
        >
          <el-option
            v-for="enterprise in candidateEnterprises"
            :key="enterprise.id"
            :label="enterprise.name"
            :value="enterprise.id"
          />
        </el-select>
        <el-button
          :disabled="!node || selectedEnterpriseIds.length === 0"
          :icon="Plus"
          :loading="batchLinking"
          plain
          type="primary"
          @click="batchLinkEnterprises"
          >批量关联</el-button
        >
      </section>

      <section v-if="batchResult" class="node-enterprise-dialog__batch-result">
        <div class="node-enterprise-dialog__batch-summary">
          批量挂接完成：成功 {{ batchResult.linkedCount }}，跳过
          {{ batchResult.skippedCount }}，失败 {{ batchResult.failedCount }}
        </div>
        <el-table :data="batchResult.items" border size="small">
          <el-table-column label="企业 ID" min-width="120" prop="enterpriseId" />
          <el-table-column label="处理状态" min-width="140">
            <template #default="{ row }">
              <el-tag :type="batchStatusType(row.status)">
                {{ batchStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220" prop="message" />
        </el-table>
      </section>

      <el-table v-loading="loading" :data="linkedEnterprises" border>
        <el-table-column
          label="企业名称"
          min-width="220"
          prop="enterpriseName"
        />
        <el-table-column label="挂接来源" min-width="120">
          <template #default="{ row }">
            <StatusTag :status="row.source" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180" prop="createdAt" />
        <el-table-column fixed="right" label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="unlinkEnterprise(row)"
              >解绑</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>
</template>

<style scoped>
.node-enterprise-dialog {
  display: grid;
  gap: 14px;
}

.node-enterprise-dialog__linker {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: 1fr 1fr auto;
}

.node-enterprise-dialog__batch-linker {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: 1fr auto;
}

.node-enterprise-dialog__batch-result {
  display: grid;
  gap: 10px;
}

.node-enterprise-dialog__batch-summary {
  background: #f0f7ff;
  border: 1px solid #c6e2ff;
  border-radius: 6px;
  color: #1d4ed8;
  font-size: 13px;
  padding: 10px 12px;
}

@media (max-width: 720px) {
  .node-enterprise-dialog__linker,
  .node-enterprise-dialog__batch-linker {
    grid-template-columns: 1fr;
  }
}
</style>
