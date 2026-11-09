<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowLeft, Edit, Plus } from "@element-plus/icons-vue";
import {
  bindEnterpriseTag,
  fetchEnterprise,
  unbindEnterpriseTag,
} from "@/api/enterprise";
import { fetchTags } from "@/api/tag";
import StatusTag from "@/components/StatusTag.vue";
import EnterpriseProfilePanel from "./EnterpriseProfilePanel.vue";
import type { EnterpriseDetail, Tag } from "@/types/domain";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const enterprise = ref<EnterpriseDetail | null>(null);
const tags = ref<Tag[]>([]);
const selectedTagId = ref<number>();

const enterpriseId = computed(() => Number(route.params.id));

async function loadDetail() {
  loading.value = true;
  try {
    enterprise.value = await fetchEnterprise(enterpriseId.value);
  } finally {
    loading.value = false;
  }
}

async function loadTags() {
  tags.value = await fetchTags();
}

async function bindTag() {
  if (!selectedTagId.value) return;
  await bindEnterpriseTag(enterpriseId.value, selectedTagId.value);
  ElMessage.success("标签已绑定");
  selectedTagId.value = undefined;
  await loadDetail();
}

async function unbindTag(tag: Tag) {
  await ElMessageBox.confirm(`确认移除“${tag.name}”标签？`, "移除标签", {
    confirmButtonText: "移除",
    cancelButtonText: "取消",
    type: "warning",
  });
  await unbindEnterpriseTag(enterpriseId.value, tag.id);
  ElMessage.success("标签已移除");
  await loadDetail();
}

onMounted(() => {
  loadDetail();
  loadTags();
});

watch(enterpriseId, async (id, oldId) => {
  if (!Number.isFinite(id) || id === oldId) return;
  await loadDetail();
});
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page__header">
      <div>
        <h2 class="page__title">{{ enterprise?.name || "企业详情" }}</h2>
        <p class="page__description">
          查看企业基础信息、标签和已挂接产业链节点。
        </p>
      </div>
      <div class="toolbar">
        <el-button :icon="ArrowLeft" plain @click="router.push('/enterprises')"
          >返回列表</el-button
        >
        <el-button
          :icon="Edit"
          type="primary"
          @click="router.push(`/enterprises/${enterpriseId}/edit`)"
        >
          编辑企业
        </el-button>
      </div>
    </div>

    <section class="surface">
      <el-descriptions v-if="enterprise" :column="2" border>
        <el-descriptions-item label="企业状态">
          <StatusTag :status="enterprise.status" />
        </el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码">
          {{ enterprise.creditCode || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="法定代表人">{{
          enterprise.legalPerson || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="所属行业">{{
          enterprise.industry || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="经营地址">{{
          enterprise.address || "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="产值（万元）">{{
          enterprise.outputValue ?? "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="税收（万元）">{{
          enterprise.taxAmount ?? "-"
        }}</el-descriptions-item>
        <el-descriptions-item label="经营范围" :span="2">
          {{ enterprise.businessScope || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="主营产品" :span="2">
          {{ enterprise.mainProducts || "-" }}
        </el-descriptions-item>
      </el-descriptions>
      <div v-else class="empty-note">暂无企业信息</div>
    </section>

    <section class="surface">
      <div class="section-head">
        <h3>企业标签</h3>
        <div class="section-head__actions">
          <el-select
            v-model="selectedTagId"
            clearable
            filterable
            placeholder="选择标签"
          >
            <el-option
              v-for="tag in tags"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            />
          </el-select>
          <el-button :icon="Plus" type="primary" @click="bindTag"
            >绑定标签</el-button
          >
        </div>
      </div>
      <div class="tag-list">
        <el-tag
          v-for="tag in enterprise?.tags || []"
          :key="tag.id"
          closable
          effect="plain"
          :style="tag.color ? { borderColor: tag.color, color: tag.color } : {}"
          @close="unbindTag(tag)"
        >
          {{ tag.name }}
        </el-tag>
        <span v-if="!enterprise?.tags?.length" class="empty-inline"
          >暂无标签</span
        >
      </div>
    </section>

    <section class="surface">
      <div class="section-head">
        <h3>已挂接节点</h3>
      </div>
      <el-table :data="enterprise?.linkedNodes || []" border>
        <el-table-column label="节点路径" min-width="260" prop="nodePath" />
        <el-table-column label="节点名称" min-width="140" prop="nodeName" />
        <el-table-column label="来源" min-width="120">
          <template #default="{ row }">
            <StatusTag :status="row.source" />
          </template>
        </el-table-column>
        <el-table-column label="置信度" min-width="120" prop="confidence" />
        <el-table-column label="挂接时间" min-width="180" prop="createdAt" />
      </el-table>
    </section>

    <EnterpriseProfilePanel v-if="enterprise" :enterprise-id="enterpriseId" />

    <section class="surface">
      <div class="section-head">
        <h3>操作日志</h3>
      </div>
      <el-table :data="enterprise?.operationLogs || []" border>
        <el-table-column label="操作时间" min-width="180" prop="createdAt" />
        <el-table-column label="操作人" min-width="120">
          <template #default="{ row }">
            {{ row.operator || "-" }}
          </template>
        </el-table-column>
        <el-table-column label="操作类型" min-width="120" prop="action" />
        <el-table-column label="操作内容" min-width="200">
          <template #default="{ row }">
            {{ row.targetName || row.targetType }}
          </template>
        </el-table-column>
        <el-table-column label="结果" min-width="120">
          <template #default="{ row }">
            {{ row.result || "-" }}
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.section-head {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-head h3 {
  font-size: 18px;
  margin: 0;
}

.section-head__actions {
  display: flex;
  gap: 8px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.empty-inline {
  color: #667085;
}
</style>
