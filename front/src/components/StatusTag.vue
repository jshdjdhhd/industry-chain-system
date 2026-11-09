<script setup lang="ts">
import { computed } from "vue";
import { getStatusMeta } from "@/constants/status";

const props = defineProps<{
  status: string;
}>();

const meta = computed(() => getStatusMeta(props.status));
</script>

<template>
  <span class="status-tag" :class="`status-tag--${meta.tone}`">
    <component :is="meta.icon" v-if="meta.icon" class="status-tag__icon" />
    {{ meta.label }}
  </span>
</template>

<style scoped>
.status-tag {
  align-items: center;
  border: 1px solid rgb(255 255 255 / 0%);
  border-radius: 999px;
  display: inline-flex;
  font-size: 12px;
  font-weight: 700;
  gap: 4px;
  line-height: 1;
  padding: 6px 9px;
  box-shadow: inset 0 0 0 1px currentcolor;
  white-space: nowrap;
}

.status-tag__icon {
  height: 12px;
  width: 12px;
}

.status-tag--blue {
  background: #eff4ff;
  color: #002fa7;
}

.status-tag--green {
  background: #ecfdf3;
  color: #099250;
}

.status-tag--amber {
  background: #fffaeb;
  color: #b54708;
}

.status-tag--red {
  background: #fef3f2;
  color: #d92d20;
}

.status-tag--gray {
  background: #f7f7f8;
  color: #667085;
}

.status-tag--purple {
  background: #f4f3ff;
  color: #6f47dc;
}
</style>
