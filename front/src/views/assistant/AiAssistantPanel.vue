<template>
  <button
    v-if="!store.panelOpen"
    class="ai-fab"
    type="button"
    aria-label="打开 AI 助手"
    @click="store.openPanel()"
  >
    <el-icon :size="24"><ChatDotRound /></el-icon>
  </button>

  <Transition name="ai-panel-slide">
    <section v-if="store.panelOpen" class="ai-panel" aria-label="AI 智能助手">
      <header class="ai-panel__header">
        <div>
          <span class="ai-panel__eyebrow">产业链智能助手</span>
          <h2 class="ai-panel__title">
            {{ store.currentSession?.title || "新对话" }}
          </h2>
        </div>
        <el-button
          text
          size="small"
          :icon="Setting"
          aria-label="助手权限管理"
          @click="openSettings()"
        >
          助手权限管理
        </el-button>
        <el-button
          text
          :icon="Minus"
          aria-label="收起 AI 助手"
          @click="store.closePanel()"
        />
      </header>

      <div v-if="viewMode === 'chat'" class="ai-panel__toolbar">
        <el-select
          data-testid="session-selector"
          class="ai-panel__session-selector"
          size="small"
          :model-value="store.currentSessionId ?? undefined"
          :disabled="store.streaming"
          aria-label="切换聊天会话"
          @change="onSessionChange"
        >
          <el-option
            v-for="session in store.sessions"
            :key="session.id"
            :label="session.title"
            :value="session.id"
          />
        </el-select>
        <el-button
          data-testid="new-session-button"
          text
          size="small"
          :icon="Plus"
          :disabled="store.streaming"
          @click="store.createSession()"
        >
          新建
        </el-button>
        <el-button
          text
          size="small"
          :icon="Delete"
          :disabled="store.streaming"
          @click="confirmDeleteCurrent"
        >
          删除
        </el-button>
        <el-button
          text
          size="small"
          :icon="DeleteFilled"
          :disabled="store.streaming"
          @click="confirmClearAll"
        >
          清空
        </el-button>
        <el-switch
          v-model="store.agentMode"
          size="small"
          active-text="Agent"
          inactive-text="问答"
          :disabled="store.streaming"
        />
      </div>

      <div v-if="viewMode === 'chat'" class="ai-panel__context">
        <el-select
          class="ai-panel__selector"
          size="small"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          filterable
          :model-value="store.currentSession?.enterpriseIds ?? []"
          :disabled="store.streaming"
          placeholder="选择企业（可多选）"
          @change="onEnterpriseChange"
        >
          <el-option
            v-for="enterprise in enterprises"
            :key="enterprise.id"
            :label="enterprise.name"
            :value="enterprise.id"
          />
        </el-select>
        <el-select
          class="ai-panel__selector"
          size="small"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          filterable
          :model-value="store.currentSession?.chainIds ?? []"
          :disabled="store.streaming"
          placeholder="选择产业链（可多选）"
          @change="onChainChange"
        >
          <el-option
            v-for="chain in chains"
            :key="chain.id"
            :label="chain.name"
            :value="chain.id"
          />
        </el-select>
      </div>

      <div v-if="viewMode === 'chat' && store.storageError" class="ai-panel__error" role="alert">
        <span>{{ store.storageError }}</span>
        <el-button text size="small" @click="store.dismissStorageError()">
          关闭
        </el-button>
      </div>

      <!-- 权限管理视图 -->
      <div v-if="viewMode === 'settings'" class="ai-panel__messages">
        <div class="ai-panel__settings">
          <div class="ai-panel__settings-header">
            <el-button text size="small" :icon="ChatDotRound" @click="closeSettings()">返回对话</el-button>
            <span class="ai-panel__settings-title">AI 工具权限管理</span>
          </div>

          <div class="ai-panel__settings-actions">
            <span>选择用户：</span>
            <el-select v-model="aiPermSelectedUserId" placeholder="选择用户" @change="loadAiPermConfig" style="width: 180px;">
              <el-option v-for="u in allUsers" :key="u.id" :label="u.username" :value="u.id" />
            </el-select>
            <el-button size="small" @click="enableAll" :disabled="!aiPermSelectedUserId">全部允许</el-button>
            <el-button size="small" type="warning" @click="disableAllWrites" :disabled="!aiPermSelectedUserId">禁用写操作</el-button>
          </div>

          <div v-if="aiPermSelectedUserId" v-loading="aiPermLoading" class="ai-panel__settings-content">
            <div v-for="group in aiPermGroups" :key="group" class="ai-panel__perm-group">
              <h4 class="ai-panel__perm-group-title">{{ group }}</h4>
              <div v-for="tool in aiPermToolsByGroup(group)" :key="tool.toolName" class="ai-panel__perm-item">
                <div>
                  <span class="ai-panel__perm-tool-name">{{ tool.toolName }}</span>
                  <span class="ai-panel__perm-tool-desc">{{ tool.description }}</span>
                  <el-tag v-if="tool.readOnly" size="small" type="info" style="margin-left: 8px;">只读</el-tag>
                  <el-tag v-else size="small" type="warning" style="margin-left: 8px;">写入</el-tag>
                </div>
                <el-switch
                  :model-value="getToolEnabled(tool.toolName)"
                  @change="(val: boolean) => toggleAiPerm(tool.toolName, val)"
                />
              </div>
            </div>
          </div>
          <el-empty v-else description="请先选择一个用户" />
        </div>
      </div>

      <!-- 对话视图 -->
      <div v-if="viewMode === 'chat'" ref="messageContainer" class="ai-panel__messages">
        <div
          v-if="!store.currentSession?.messages.length"
          class="ai-panel__empty"
        >
          <span class="ai-panel__empty-icon">
            <el-icon :size="28"><ChatDotRound /></el-icon>
          </span>
          <strong>从一个产业链问题开始</strong>
          <p>可直接通用问答，也可选择企业、产业链或批量范围获得上下文分析。</p>
          <div class="ai-panel__suggestions">
            <el-button size="small" @click="quickAsk('什么是产业链？')">
              产业链概念
            </el-button>
            <el-button
              size="small"
              @click="quickAsk('企业挂链有哪些注意事项？')"
            >
              挂链注意事项
            </el-button>
          </div>
        </div>

        <template v-for="message in store.currentSession?.messages" :key="message.id">
          <article v-if="isConfirmMessage(message)" :class="['ai-msg', 'ai-msg--assistant']">
            <div class="ai-msg__avatar" aria-hidden="true">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="ai-msg__body">
              <div class="ai-confirm-card" :class="`ai-confirm-card--${message.status}`">
                <div class="ai-confirm-card__header">
                  <el-icon><Warning /></el-icon>
                  <span>请求操作：{{ message.description }}</span>
                </div>
                <div class="ai-confirm-card__detail">
                  <p>工具：{{ message.tool }}</p>
                  <p>风险等级：
                    <el-tag :type="riskTagType(message.riskLevel)" size="small">
                      {{ riskLabel(message.riskLevel) }}
                    </el-tag>
                  </p>
                </div>
                <div v-if="message.status === 'pending'" class="ai-confirm-card__actions">
                  <el-button size="small" @click="store.confirmAction(message.confirmId, false)">取消</el-button>
                  <el-button size="small" type="primary" @click="store.confirmAction(message.confirmId, true)">确认执行</el-button>
                </div>
                <div v-else class="ai-confirm-card__status">
                  <el-tag v-if="message.status === 'confirmed'" type="success">✅ 已确认执行</el-tag>
                  <el-tag v-else-if="message.status === 'rejected'" type="info">❌ 已取消</el-tag>
                  <el-tag v-else-if="message.status === 'timeout'" type="warning">⏰ 已超时</el-tag>
                </div>
              </div>
            </div>
          </article>
          <article v-else :class="['ai-msg', `ai-msg--${message.role}`]">
            <div class="ai-msg__avatar" aria-hidden="true">
              <el-icon v-if="message.role === 'user'"><User /></el-icon>
              <el-icon v-else><Monitor /></el-icon>
            </div>
            <div class="ai-msg__body">
              <div
                v-if="message.status === 'streaming' && !message.content"
                class="ai-msg__thinking"
              >
                <span></span><span></span><span></span>
                AI 正在思考
              </div>
              <div
                v-else
                class="ai-msg__content markdown-body"
                v-html="renderMarkdown(message.content)"
              ></div>
            </div>
          </article>
        </template>
      </div>

      <footer v-if="viewMode === 'chat'" class="ai-panel__input">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          maxlength="4000"
          show-word-limit
          resize="none"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          :disabled="store.streaming || !store.canSend"
          @keydown="handleKeydown"
        />
        <div class="ai-panel__input-actions">
          <el-button
            v-if="hasFailedMessage"
            text
            size="small"
            :icon="RefreshRight"
            @click="store.retryLastMessage()"
          >
            重试
          </el-button>
          <el-button
            v-if="store.streaming"
            data-testid="stop-button"
            type="danger"
            :icon="VideoPause"
            @click="store.stopStream('stopped')"
          >
            停止
          </el-button>
          <el-button
            v-else
            data-testid="send-button"
            type="primary"
            :icon="Promotion"
            :disabled="!inputText.trim() || !store.canSend"
            @click="handleSend"
          >
            发送
          </el-button>
        </div>
      </footer>
    </section>
  </Transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import {
  ChatDotRound,
  Delete,
  DeleteFilled,
  Minus,
  Monitor,
  Plus,
  Promotion,
  RefreshRight,
  Setting,
  User,
  VideoPause,
  Warning,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import DOMPurify from "dompurify";
import { marked } from "marked";
import { useAiChatStore } from "@/stores/aiChat";
import type { ConfirmMessage } from "@/stores/aiChat";
import { useOptionsStore } from "@/stores/options";
import { fetchAgentTools, fetchAgentPermissions, updateAgentPermissions } from "@/api/agent";
import { fetchUsers } from "@/api/system";
import type { SysUserVO } from "@/types/domain";

const store = useAiChatStore();
const optionsStore = useOptionsStore();
const inputText = ref("");
const messageContainer = ref<HTMLElement | null>(null);
const enterprises = computed(() => optionsStore.enterpriseOptions);
const chains = computed(() => optionsStore.chainOptions);

// ========== viewMode ==========
const viewMode = ref<"chat" | "settings">("chat");

// ========== AI 权限管理 ==========
const aiPermLoading = ref(false);
const aiPermTools = ref<{ toolName: string; description: string; readOnly: boolean; group: string; enabled: boolean }[]>([]);
const aiPermSelectedUserId = ref<number | null>(null);
const aiPermOverrides = ref<Map<string, boolean>>(new Map());
const allUsers = ref<SysUserVO[]>([]);

const aiPermGroups = computed(() => {
  const groups = new Set(aiPermTools.value.map(t => t.group));
  return Array.from(groups);
});

function aiPermToolsByGroup(group: string) {
  return aiPermTools.value.filter(t => t.group === group);
}

function getToolEnabled(toolName: string): boolean {
  return aiPermOverrides.value.get(toolName) ?? true;
}

async function loadAllUsers() {
  const res = await fetchUsers({ pageNum: 1, pageSize: 100 });
  allUsers.value = res.records;
}

async function loadAiPermConfig(userId: number) {
  aiPermLoading.value = true;
  try {
    const tools = await fetchAgentTools();
    aiPermTools.value = tools;
    const config = await fetchAgentPermissions();
    aiPermOverrides.value = new Map(config.permissions.map(p => [p.toolName, p.enabled]));
    aiPermSelectedUserId.value = userId;
  } finally {
    aiPermLoading.value = false;
  }
}

async function toggleAiPerm(toolName: string, enabled: boolean) {
  if (!aiPermSelectedUserId.value) return;
  await updateAgentPermissions(aiPermSelectedUserId.value, [{ toolName, enabled }]);
  aiPermOverrides.value.set(toolName, enabled);
  ElMessage.success(enabled ? "已启用" : "已禁用");
}

async function disableAllWrites() {
  if (!aiPermSelectedUserId.value) return;
  const writeTools = aiPermTools.value.filter(t => !t.readOnly);
  await updateAgentPermissions(aiPermSelectedUserId.value, writeTools.map(t => ({ toolName: t.toolName, enabled: false })));
  writeTools.forEach(t => aiPermOverrides.value.set(t.toolName, false));
  ElMessage.success("已禁用所有写操作");
}

async function enableAll() {
  if (!aiPermSelectedUserId.value) return;
  await updateAgentPermissions(aiPermSelectedUserId.value, aiPermTools.value.map(t => ({ toolName: t.toolName, enabled: true })));
  aiPermTools.value.forEach(t => aiPermOverrides.value.set(t.toolName, true));
  ElMessage.success("已全部启用");
}

function openSettings() {
  viewMode.value = "settings";
  loadAllUsers();
}

function closeSettings() {
  viewMode.value = "chat";
  aiPermSelectedUserId.value = null;
  aiPermTools.value = [];
  aiPermOverrides.value = new Map();
}

const hasFailedMessage = computed(() => {
  const messages = store.currentSession?.messages;
  return messages?.at(-1)?.status === "failed";
});

function isConfirmMessage(msg: any): msg is ConfirmMessage {
  return msg && msg.type === "confirm";
}

function riskTagType(level: string) {
  return level === "high" ? "danger" : level === "medium" ? "warning" : "info";
}

function riskLabel(level: string) {
  return level === "high" ? "高风险" : level === "medium" ? "中风险" : "低风险";
}

function renderMarkdown(text: string): string {
  if (!text) return "";
  const html = marked.parse(text, { async: false }) as string;
  return DOMPurify.sanitize(html);
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    handleSend();
  }
}

function handleSend() {
  const text = inputText.value.trim();
  if (!text || !store.canSend) return;
  if (store.agentMode) {
    store.sendMessageViaAgent(text);
  } else {
    store.sendMessage(text);
  }
  inputText.value = "";
}

function quickAsk(text: string) {
  if (!store.canSend) return;
  store.sendMessage(text);
}

function onSessionChange(value: string) {
  store.switchSession(value);
}

function onEnterpriseChange(value: number[]) {
  const selected = value
    .map((id) => enterprises.value.find((item) => item.id === id))
    .filter((item): item is { id: number; name: string } => Boolean(item));
  store.setEnterprises(selected);
}

function onChainChange(value: number[]) {
  const selected = value
    .map((id) => chains.value.find((item) => item.id === id))
    .filter((item): item is { id: number; name: string } => Boolean(item));
  store.setChains(selected);
}

async function confirmDeleteCurrent() {
  const id = store.currentSessionId;
  if (!id) return;
  try {
    await ElMessageBox.confirm(
      "删除当前对话后，本地聊天记录将无法恢复。",
      "删除对话",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    store.deleteSession(id);
  } catch {
    // 用户取消，无需提示。
  }
}

async function confirmClearAll() {
  try {
    await ElMessageBox.confirm(
      "确认清空当前用户的全部本地对话吗？",
      "清空对话",
      {
        confirmButtonText: "清空",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    store.clearAllSessions();
  } catch {
    // 用户取消，无需提示。
  }
}

function loadSharedOptions() {
  void Promise.allSettled([
    optionsStore.loadEnterpriseOptions(),
    optionsStore.loadChainOptions(),
  ]);
}

function scrollToBottom() {
  const container = messageContainer.value;
  if (container) {
    container.scrollTop = container.scrollHeight;
  }
}

watch(
  () => store.panelOpen,
  (open) => {
    if (open) {
      loadSharedOptions();
    }
  },
);

watch(
  () => store.currentSession?.messages,
  () => {
    void nextTick(scrollToBottom);
  },
  { deep: true },
);

onMounted(() => {
  if (store.panelOpen) {
    loadSharedOptions();
  }
});
</script>

<style scoped>
.ai-fab {
  align-items: center;
  background: linear-gradient(135deg, #155eef, #0ea5a8);
  border: 1px solid rgb(255 255 255 / 35%);
  border-radius: 18px;
  bottom: 24px;
  box-shadow: 0 16px 36px rgb(18 65 121 / 28%);
  color: #fff;
  cursor: pointer;
  display: flex;
  height: 54px;
  justify-content: center;
  position: fixed;
  right: 24px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
  width: 54px;
  z-index: 2000;
}

.ai-fab:hover {
  box-shadow: 0 18px 42px rgb(18 65 121 / 36%);
  transform: translateY(-2px);
}

.ai-fab:focus-visible {
  outline: 3px solid rgb(21 94 239 / 28%);
  outline-offset: 3px;
}

.ai-panel {
  background: #fff;
  border: 1px solid rgb(204 220 240 / 85%);
  border-radius: 18px;
  bottom: 24px;
  box-shadow: 0 24px 70px rgb(19 46 82 / 24%);
  display: flex;
  flex-direction: column;
  height: min(780px, calc(100vh - 96px));
  overflow: hidden;
  position: fixed;
  right: 24px;
  width: 520px;
  z-index: 2000;
}

.ai-panel__header {
  align-items: center;
  background:
    radial-gradient(circle at 85% 10%, rgb(8 191 211 / 18%), transparent 38%),
    linear-gradient(135deg, #0d2855, #154a85);
  color: #fff;
  display: flex;
  flex-shrink: 0;
  justify-content: space-between;
  min-height: 68px;
  padding: 10px 14px 10px 18px;
}

.ai-panel__eyebrow {
  color: rgb(223 244 255 / 78%);
  display: block;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.ai-panel__title {
  font-size: 15px;
  font-weight: 650;
  margin: 3px 0 0;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-panel__header :deep(.el-button) {
  color: #fff;
}

.ai-panel__toolbar {
  align-items: center;
  border-bottom: 1px solid #e8eef6;
  display: flex;
  flex-shrink: 0;
  gap: 2px;
  padding: 8px 12px;
}

.ai-panel__session-selector {
  flex: 1;
  min-width: 0;
}

.ai-panel__context {
  background: #f7faff;
  border-bottom: 1px solid #e8eef6;
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  padding: 9px 12px;
}

.ai-panel__selector {
  display: block;
  flex: 1;
  min-width: 0;
  width: 0;
}

.ai-panel__selector :deep(.el-select__wrapper) {
  align-items: center;
  display: flex;
  min-height: 34px;
  width: 100%;
}

.ai-panel__selector :deep(.el-select__selection) {
  min-width: 0;
  width: 100%;
}

.ai-panel__selector :deep(.el-select__placeholder) {
  max-width: calc(100% - 22px);
}

.ai-panel__selector :deep(.el-select__suffix) {
  align-items: center;
  display: flex;
}

.ai-panel__error,
.ai-panel__hint {
  align-items: center;
  display: flex;
  flex-shrink: 0;
  font-size: 12px;
  justify-content: space-between;
  padding: 7px 14px;
}

.ai-panel__error {
  background: #fff1f0;
  color: #cf1322;
}

.ai-panel__hint {
  background: #fff8e8;
  color: #9a6700;
}

.ai-panel__messages {
  background:
    linear-gradient(rgb(248 251 255 / 94%), rgb(244 249 252 / 94%)),
    radial-gradient(circle at 20% 0, rgb(21 94 239 / 8%), transparent 35%);
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px;
}

.ai-panel__empty {
  align-items: center;
  color: #62748a;
  display: flex;
  flex-direction: column;
  padding: 54px 10px 20px;
  text-align: center;
}

.ai-panel__empty-icon {
  align-items: center;
  background: linear-gradient(135deg, #e8f0ff, #e4fbfb);
  border-radius: 18px;
  color: #155eef;
  display: flex;
  height: 54px;
  justify-content: center;
  margin-bottom: 14px;
  width: 54px;
}

.ai-panel__empty strong {
  color: #1a2b42;
  font-size: 14px;
}

.ai-panel__empty p {
  font-size: 12px;
  line-height: 1.6;
  margin: 7px 0 16px;
}

.ai-panel__suggestions {
  display: flex;
  gap: 8px;
}

.ai-msg {
  align-items: flex-start;
  display: flex;
  gap: 9px;
  margin-bottom: 16px;
}

.ai-msg--user {
  flex-direction: row-reverse;
}

.ai-msg__avatar {
  align-items: center;
  background: #e8eef7;
  border-radius: 10px;
  color: #38506c;
  display: flex;
  flex-shrink: 0;
  height: 30px;
  justify-content: center;
  width: 30px;
}

.ai-msg--user .ai-msg__avatar {
  background: #155eef;
  color: #fff;
}

.ai-msg__body {
  max-width: calc(100% - 42px);
}

.ai-msg__content,
.ai-msg__thinking {
  border-radius: 4px 13px 13px;
  font-size: 13px;
  line-height: 1.65;
  padding: 9px 12px;
  word-break: break-word;
}

.ai-msg--assistant .ai-msg__content,
.ai-msg__thinking {
  background: #fff;
  border: 1px solid #e1e9f3;
  color: #24364c;
}

.ai-msg--user .ai-msg__content {
  background: linear-gradient(135deg, #155eef, #1677c8);
  border-radius: 13px 4px 13px 13px;
  color: #fff;
}

.ai-msg__thinking {
  align-items: center;
  color: #718096;
  display: flex;
  gap: 4px;
}

.ai-msg__thinking span {
  animation: ai-pulse 1.2s infinite ease-in-out;
  background: #7c8da4;
  border-radius: 50%;
  height: 4px;
  width: 4px;
}

.ai-msg__thinking span:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-msg__thinking span:nth-child(3) {
  animation-delay: 0.3s;
  margin-right: 4px;
}

.ai-confirm-card {
  background: #fff;
  border: 1px solid #e1e9f3;
  border-radius: 8px;
  padding: 12px;
  margin-top: 4px;
}

.ai-confirm-card--pending {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.ai-confirm-card__header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  margin-bottom: 8px;
}

.ai-confirm-card__detail {
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}

.ai-confirm-card__detail p {
  margin: 2px 0;
}

.ai-confirm-card__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.ai-confirm-card__status {
  text-align: right;
}

@keyframes ai-pulse {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  30% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

.ai-panel__input {
  background: #fff;
  border-top: 1px solid #e1e9f3;
  flex-shrink: 0;
  padding: 12px 14px;
}

.ai-panel__input-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}

.markdown-body :deep(p) {
  margin: 3px 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  font-size: 1em;
  margin: 7px 0 4px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.markdown-body :deep(code) {
  background: rgb(15 35 60 / 8%);
  border-radius: 4px;
  font-size: 12px;
  padding: 2px 4px;
}

.markdown-body :deep(pre) {
  background: rgb(15 35 60 / 8%);
  border-radius: 7px;
  overflow-x: auto;
  padding: 8px;
}

.ai-panel-slide-enter-active,
.ai-panel-slide-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.ai-panel-slide-enter-from,
.ai-panel-slide-leave-to {
  opacity: 0;
  transform: translateY(18px) scale(0.98);
}

@media (max-width: 720px) {
  .ai-panel {
    border-radius: 14px;
    bottom: 12px;
    height: calc(100vh - 24px);
    right: 12px;
    width: calc(100vw - 24px);
  }

  .ai-panel__toolbar {
    flex-wrap: wrap;
  }

  .ai-panel__session-selector {
    flex-basis: 100%;
  }
}

/* ========== 权限管理视图 ========== */
.ai-panel__settings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-panel__settings-header {
  align-items: center;
  display: flex;
  gap: 8px;
}

.ai-panel__settings-title {
  color: #102033;
  font-size: 15px;
  font-weight: 700;
}

.ai-panel__settings-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
}

.ai-panel__settings-content {
  display: grid;
  gap: 12px;
}

.ai-panel__perm-group {
  margin-bottom: 4px;
}

.ai-panel__perm-group-title {
  color: #102033;
  font-size: 14px;
  font-weight: 700;
  margin: 0 0 6px;
}

.ai-panel__perm-item {
  align-items: center;
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 6px;
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  padding: 8px 12px;
  transition: background 150ms ease;
}

.ai-panel__perm-item:hover {
  background: rgb(31 111 255 / 4%);
}

.ai-panel__perm-tool-name {
  font-weight: 600;
  color: #102033;
  font-size: 13px;
}

.ai-panel__perm-tool-desc {
  color: #63738b;
  font-size: 12px;
  margin-left: 8px;
}
</style>
