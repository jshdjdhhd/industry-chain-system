<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import { Delete, Edit, Plus, Search } from "@element-plus/icons-vue";
import {
  createTag,
  deleteTag,
  fetchTags,
  updateTag,
  type TagPayload,
} from "@/api/tag";
import SearchBar from "@/components/SearchBar.vue";
import type { Tag } from "@/types/domain";

const loading = ref(false);
const dialogVisible = ref(false);
const saving = ref(false);
const editingId = ref<number>();
const formRef = ref<FormInstance>();
const tags = ref<Tag[]>([]);
const query = reactive({
  keyword: "",
  type: "",
});

const form = reactive<TagPayload>({
  name: "",
  type: "CUSTOM",
  color: "#002FA7",
  description: "",
});

const rules: FormRules<TagPayload> = {
  name: [{ required: true, message: "标签名称不能为空", trigger: "blur" }],
  type: [{ required: true, message: "标签类型不能为空", trigger: "change" }],
};

async function loadTags() {
  loading.value = true;
  try {
    tags.value = await fetchTags(query);
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = undefined;
  Object.assign(form, {
    name: "",
    type: "CUSTOM",
    color: "#002FA7",
    description: "",
  });
  dialogVisible.value = true;
}

function openEdit(tag: Tag) {
  editingId.value = tag.id;
  Object.assign(form, {
    name: tag.name,
    type: tag.type,
    color: tag.color || "#002FA7",
    description: tag.description || "",
  });
  dialogVisible.value = true;
}

async function saveTag() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    if (editingId.value) {
      await updateTag(editingId.value, form);
      ElMessage.success("标签已更新");
    } else {
      await createTag(form);
      ElMessage.success("标签已新增");
    }
    dialogVisible.value = false;
    await loadTags();
  } finally {
    saving.value = false;
  }
}

async function removeTag(tag: Tag) {
  await ElMessageBox.confirm(
    `确认删除“${tag.name}”？已使用标签会由后端阻止删除。`,
    "删除标签",
    {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning",
    },
  );
  await deleteTag(tag.id);
  ElMessage.success("标签已删除");
  await loadTags();
}

onMounted(loadTags);
</script>

<template>
  <div class="page">
    <div class="page__header">
      <div>
        <h2 class="page__title">标签管理</h2>
        <p class="page__description">
          维护产业、规模、风险和自定义标签，供企业筛选和智能挂链使用。
        </p>
      </div>
      <el-button :icon="Plus" type="primary" @click="openCreate"
        >新增标签</el-button
      >
    </div>

    <SearchBar :model-value="query">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="标签名称"
          @keyup.enter="loadTags"
        />
      </el-form-item>
      <el-form-item label="标签类型">
        <el-select v-model="query.type" clearable placeholder="全部类型">
          <el-option label="产业标签" value="INDUSTRY" />
          <el-option label="规模标签" value="SCALE" />
          <el-option label="风险标签" value="RISK" />
          <el-option label="自定义标签" value="CUSTOM" />
        </el-select>
      </el-form-item>
      <template #actions>
        <el-button :icon="Search" type="primary" @click="loadTags"
          >查询</el-button
        >
      </template>
    </SearchBar>

    <section class="surface surface--flush">
      <el-table v-loading="loading" :data="tags" border>
        <el-table-column label="标签名称" min-width="160" prop="name" />
        <el-table-column label="类型" min-width="140" prop="type" />
        <el-table-column label="颜色" min-width="110">
          <template #default="{ row }">
            <span
              class="color-swatch"
              :style="{ backgroundColor: row.color || '#002FA7' }"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="描述"
          min-width="260"
          prop="description"
          show-overflow-tooltip
        />
        <el-table-column label="使用企业数" min-width="120" prop="usedCount" />
        <el-table-column fixed="right" label="操作" min-width="150">
          <template #default="{ row }">
            <el-button :icon="Edit" link type="primary" @click="openEdit(row)"
              >编辑</el-button
            >
            <el-tooltip
              :disabled="!(row.usedCount > 0)"
              content="该标签已被企业使用，需先解绑后删除"
              placement="top"
            >
              <span>
                <el-button
                  :disabled="row.usedCount > 0"
                  :icon="Delete"
                  link
                  type="danger"
                  @click="removeTag(row)"
                  >删除</el-button
                >
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑标签' : '新增标签'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="标签类型" prop="type">
          <el-select v-model="form.type">
            <el-option label="产业标签" value="INDUSTRY" />
            <el-option label="规模标签" value="SCALE" />
            <el-option label="风险标签" value="RISK" />
            <el-option label="自定义标签" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <el-color-picker v-model="form.color" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" :rows="3" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="saving" type="primary" @click="saveTag"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.color-swatch {
  border: 1px solid #d9e2ef;
  border-radius: 4px;
  display: inline-block;
  height: 20px;
  width: 36px;
}
</style>
