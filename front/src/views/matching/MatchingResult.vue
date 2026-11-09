<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { Check, Close, EditPen } from "@element-plus/icons-vue";
import {
  acceptMatchingResult,
  manualConfirmMatching,
  rejectMatchingResult,
} from "@/api/matching";
import StatusTag from "@/components/StatusTag.vue";
import type { ChainNode, MatchingResult, MatchingTask } from "@/types/domain";

const props = defineProps<{
  task: MatchingTask | null;
  nodes: ChainNode[];
}>();

const emit = defineEmits<{
  changed: [];
}>();

const rejecting = ref(false);
const manualDialogVisible = ref(false);
const rejectDialogVisible = ref(false);
const activeResult = ref<MatchingResult | null>(null);
const formRef = ref<FormInstance>();

const rejectForm = reactive({
  rejectReason: "",
});

const manualForm = reactive({
  nodeId: undefined as number | undefined,
  reason: "",
});

const rejectRules: FormRules<typeof rejectForm> = {
  rejectReason: [
    { required: true, message: "拒绝原因不能为空", trigger: "blur" },
  ],
};

const manualRules: FormRules<typeof manualForm> = {
  nodeId: [{ required: true, message: "请选择节点", trigger: "change" }],
  reason: [
    { required: true, message: "人工确认理由不能为空", trigger: "blur" },
  ],
};

const flattenedNodes = computed(() => flattenNodes(props.nodes));

function flattenNodes(
  nodes: ChainNode[],
  prefix = "",
): Array<{ id: number; label: string }> {
  return nodes.flatMap((node) => {
    const label = prefix ? `${prefix} / ${node.name}` : node.name;
    return [
      { id: node.id, label },
      ...flattenNodes(node.children ?? [], label),
    ];
  });
}

async function accept(result: MatchingResult) {
  await acceptMatchingResult(result.resultId);
  ElMessage.success("推荐已采纳，正式挂链关系已创建");
  emit("changed");
}

function openReject(result: MatchingResult) {
  activeResult.value = result;
  rejectForm.rejectReason = "";
  rejectDialogVisible.value = true;
}

async function submitReject() {
  if (!activeResult.value) return;
  await formRef.value?.validate();
  rejecting.value = true;
  try {
    await rejectMatchingResult(
      activeResult.value.resultId,
      rejectForm.rejectReason,
    );
    ElMessage.success("推荐已拒绝");
    rejectDialogVisible.value = false;
    emit("changed");
  } finally {
    rejecting.value = false;
  }
}

function openManual(result?: MatchingResult) {
  activeResult.value = result ?? null;
  manualForm.nodeId = result?.nodeId;
  manualForm.reason = "";
  manualDialogVisible.value = true;
}

async function submitManual() {
  if (!props.task || !manualForm.nodeId) return;
  await formRef.value?.validate();
  await manualConfirmMatching({
    enterpriseId: props.task.enterpriseId,
    nodeId: manualForm.nodeId,
    reason: manualForm.reason,
  });
  ElMessage.success("人工改选已确认，正式挂链关系已创建");
  manualDialogVisible.value = false;
  emit("changed");
}
</script>

<template>
  <section class="surface matching-result">
    <div class="matching-result__head">
      <div>
        <h3>推荐结果</h3>
        <p>采纳后创建正式挂链关系；拒绝会保存拒绝原因；可人工改选节点。</p>
      </div>
      <el-button :icon="EditPen" plain :disabled="!task" @click="openManual()"
        >人工改选</el-button
      >
    </div>

    <div v-if="!task" class="empty-note">选择企业和产业链后生成推荐。</div>

    <div v-else class="matching-result__list">
      <article
        v-for="result in task.results"
        :key="result.resultId"
        class="matching-result__item"
      >
        <div class="matching-result__path">
          <strong>{{ result.nodePath }}</strong>
          <StatusTag :status="result.decision || 'PENDING'" />
        </div>
        <div class="matching-result__confidence">
          <span>置信度 {{ result.confidence.toFixed(2) }}</span>
          <el-progress
            :percentage="Math.round(result.confidence * 100)"
            :show-text="false"
          />
        </div>
        <p>{{ result.reason }}</p>
        <p class="matching-result__evidence">
          {{ result.evidence || "无证据字段" }}
        </p>
        <el-alert
          v-if="result.confidence < 0.5"
          :closable="false"
          title="置信度低于 0.5，建议人工核验"
          type="warning"
        />
        <div class="toolbar">
          <el-button :icon="Check" type="primary" @click="accept(result)"
            >采纳</el-button
          >
          <el-button
            :icon="Close"
            plain
            type="danger"
            @click="openReject(result)"
            >拒绝</el-button
          >
          <el-button :icon="EditPen" plain @click="openManual(result)"
            >人工改选</el-button
          >
        </div>
      </article>
    </div>

    <el-dialog v-model="rejectDialogVisible" title="拒绝推荐" width="480px">
      <el-form
        ref="formRef"
        :model="rejectForm"
        :rules="rejectRules"
        label-position="top"
      >
        <el-form-item label="拒绝原因" prop="rejectReason">
          <el-input
            v-model="rejectForm.rejectReason"
            :rows="4"
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button :loading="rejecting" type="danger" @click="submitReject"
          >确认拒绝</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="manualDialogVisible" title="人工改选节点" width="560px">
      <el-form
        ref="formRef"
        :model="manualForm"
        :rules="manualRules"
        label-position="top"
      >
        <el-form-item label="目标节点" prop="nodeId">
          <el-select
            v-model="manualForm.nodeId"
            filterable
            placeholder="选择产业链节点"
          >
            <el-option
              v-for="node in flattenedNodes"
              :key="node.id"
              :label="node.label"
              :value="node.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="人工确认理由" prop="reason">
          <el-input v-model="manualForm.reason" :rows="4" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitManual">确认挂链</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.matching-result {
  display: grid;
  gap: 14px;
}

.matching-result__head {
  align-items: start;
  display: flex;
  justify-content: space-between;
}

.matching-result__head h3 {
  font-size: 18px;
  margin: 0;
}

.matching-result__head p {
  color: #667085;
  margin: 4px 0 0;
}

.matching-result__list {
  display: grid;
  gap: 12px;
}

.matching-result__item {
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  padding: 14px;
}

.matching-result__path,
.matching-result__confidence {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.matching-result__confidence .el-progress {
  flex: 1;
  max-width: 240px;
}

.matching-result__item p {
  margin: 0;
}

.matching-result__evidence {
  color: #667085;
  font-size: 13px;
}
</style>
