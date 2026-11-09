<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { ArrowLeft, Check } from "@element-plus/icons-vue";
import {
  createEnterprise,
  fetchEnterprise,
  updateEnterprise,
} from "@/api/enterprise";
import type { EnterprisePayload } from "@/types/domain";

const route = useRoute();
const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const submitting = ref(false);
const enterpriseId = computed(() =>
  route.params.id ? Number(route.params.id) : undefined,
);
const isEdit = computed(() => Number.isFinite(enterpriseId.value));

const form = reactive<EnterprisePayload>({
  name: "",
  creditCode: "",
  legalPerson: "",
  businessScope: "",
  mainProducts: "",
  address: "",
  outputValue: null,
  taxAmount: null,
});

const rules: FormRules<EnterprisePayload> = {
  name: [{ required: true, message: "企业名称不能为空", trigger: "blur" }],
};

async function loadEnterprise() {
  if (!enterpriseId.value) return;
  loading.value = true;
  try {
    const detail = await fetchEnterprise(enterpriseId.value);
    Object.assign(form, {
      name: detail.name,
      creditCode: detail.creditCode || "",
      legalPerson: detail.legalPerson || "",
      businessScope: detail.businessScope || "",
      mainProducts: detail.mainProducts || "",
      address: detail.address || "",
      outputValue: detail.outputValue ?? null,
      taxAmount: detail.taxAmount ?? null,
    });
  } finally {
    loading.value = false;
  }
}

async function submit() {
  await formRef.value?.validate();
  submitting.value = true;
  try {
    if (enterpriseId.value) {
      await updateEnterprise(enterpriseId.value, form);
      ElMessage.success("企业已更新");
      router.push(`/enterprises/${enterpriseId.value}`);
    } else {
      const created = await createEnterprise(form);
      ElMessage.success("企业已新增");
      router.push(`/enterprises/${created.id}`);
    }
  } finally {
    submitting.value = false;
  }
}

onMounted(loadEnterprise);
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page__header">
      <div>
        <h2 class="page__title">{{ isEdit ? "编辑企业" : "新增企业" }}</h2>
        <p class="page__description">
          企业导入后默认未入库，正式状态流转由后端业务动作控制。
        </p>
      </div>
      <el-button :icon="ArrowLeft" plain @click="router.back()">返回</el-button>
    </div>

    <section class="surface">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="企业名称" prop="name">
            <el-input
              v-model="form.name"
              maxlength="128"
              placeholder="请输入企业名称"
            />
          </el-form-item>
          <el-form-item label="统一社会信用代码" prop="creditCode">
            <el-input
              v-model="form.creditCode"
              maxlength="32"
              placeholder="请输入统一社会信用代码"
            />
          </el-form-item>
          <el-form-item label="法定代表人" prop="legalPerson">
            <el-input
              v-model="form.legalPerson"
              maxlength="64"
              placeholder="请输入法定代表人"
            />
          </el-form-item>
          <el-form-item label="经营地址" prop="address">
            <el-input
              v-model="form.address"
              maxlength="255"
              placeholder="请输入经营地址"
            />
          </el-form-item>
          <el-form-item label="产值（万元）" prop="outputValue">
            <el-input-number
              v-model="form.outputValue"
              :min="0"
              :precision="2"
            />
          </el-form-item>
          <el-form-item label="税收（万元）" prop="taxAmount">
            <el-input-number v-model="form.taxAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item class="full-row" label="经营范围" prop="businessScope">
            <el-input v-model="form.businessScope" :rows="4" type="textarea" />
          </el-form-item>
          <el-form-item class="full-row" label="主营产品" prop="mainProducts">
            <el-input v-model="form.mainProducts" :rows="3" type="textarea" />
          </el-form-item>
        </div>
        <div class="form-actions">
          <el-button plain @click="router.back()">取消</el-button>
          <el-button
            :icon="Check"
            :loading="submitting"
            type="primary"
            @click="submit"
            >保存</el-button
          >
        </div>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.form-actions {
  border-top: 1px solid #e6edf7;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 10px;
  padding-top: 16px;
}
</style>
