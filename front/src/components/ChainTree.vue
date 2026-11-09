<template>
  <el-tree
    class="chain-tree"
    :current-node-key="selectedNodeId"
    :data="nodes"
    default-expand-all
    highlight-current
    node-key="id"
    :props="{ label: 'name', children: 'children' }"
    @node-click="handleNodeClick"
  >
    <template #default="{ data }">
      <div class="chain-tree__node">
        <span>{{ data.name }}</span>
        <span class="chain-tree__counts">
          {{ data.directEnterpriseCount ?? 0 }} /
          {{ data.subtreeEnterpriseCount ?? 0 }}
        </span>
      </div>
    </template>
  </el-tree>
</template>

<script setup lang="ts">
import type { ChainNode } from "@/types/domain";

defineProps<{
  nodes: ChainNode[];
  selectedNodeId?: number;
}>();

const emit = defineEmits<{
  "update:selectedNodeId": [nodeId: number];
  select: [node: ChainNode];
}>();

function handleNodeClick(node: ChainNode) {
  emit("update:selectedNodeId", node.id);
  emit("select", node);
}
</script>

<style scoped>
.chain-tree {
  --el-tree-node-hover-bg-color: #f3f6fb;
}

.chain-tree__node {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  width: 100%;
}

.chain-tree__counts {
  color: #667085;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
</style>
