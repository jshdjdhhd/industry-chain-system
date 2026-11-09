<template>
  <div class="page node-search-page">
    <div class="page__header">
      <div>
        <h2 class="page__title">节点搜索</h2>
        <p class="page__description">
          按节点知识库 keywords 跨产业链检索节点。
        </p>
      </div>
    </div>

    <section class="surface node-search-page__filters">
      <el-form :model="query" class="node-search-form" label-position="top">
        <el-form-item label="关键词" :error="keywordError">
          <el-input
            v-model="query.keyword"
            data-test="keyword-input"
            placeholder="请输入节点关键词"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="产业链">
          <el-select
            v-model="query.chainId"
            data-test="chain-select"
            placeholder="全部"
          >
            <el-option label="全部" :value="ALL_CHAINS" />
            <el-option
              v-for="chain in chains"
              :key="chain.id"
              :label="chain.name"
              :value="chain.id"
            />
          </el-select>
        </el-form-item>
        <div class="node-search-form__actions">
          <el-button
            data-test="search-button"
            :loading="loading"
            type="primary"
            @click="handleSearch"
          >
            搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </div>
      </el-form>
    </section>

    <section class="surface surface--flush node-search-page__results">
      <div v-if="!hasSearched" class="empty-note node-search-page__initial">
        请输入关键词搜索
      </div>
      <template v-else>
        <el-table
          v-loading="loading"
          :data="records"
          border
          empty-text="未找到匹配节点"
        >
          <el-table-column label="节点名称" min-width="180" prop="name" />
          <el-table-column
            label="关键词"
            min-width="200"
            prop="keywords"
            show-overflow-tooltip
          />
          <el-table-column label="节点定义" min-width="220">
            <template #default="{ row }">
              {{ row.definition || "—" }}
            </template>
          </el-table-column>
          <el-table-column label="所属产业链" min-width="180">
            <template #default="{ row }">
              {{ getChainName(row.chainId) }}
            </template>
          </el-table-column>
          <el-table-column label="层级" min-width="72" prop="levelNo" />
          <el-table-column fixed="right" label="操作" min-width="180">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button link type="primary" @click="viewEnterprises(row)">
                  查看企业
                </el-button>
                <el-button
                  :data-test="`go-tree-${row.id}`"
                  :disabled="!row.chainId"
                  link
                  type="primary"
                  @click="goToTree(row)"
                >
                  跳转到树
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="node-search-page__pagination">
          <el-pagination
            :current-page="pagination.pageNum"
            :page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, prev, pager, next, sizes"
            @current-change="changePage"
            @size-change="changePageSize"
          />
        </div>
      </template>
    </section>

    <NodeEnterpriseDialog
      v-model="enterpriseDialogVisible"
      :node="selectedNode"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { fetchChains, searchNodesByKeyword } from "@/api/chain";
import type { ChainNode, IndustryChain } from "@/types/domain";
import NodeEnterpriseDialog from "./NodeEnterpriseDialog.vue";

const ALL_CHAINS = 0;

const router = useRouter();
const chains = ref<IndustryChain[]>([]);
const records = ref<ChainNode[]>([]);
const loading = ref(false);
const hasSearched = ref(false);
const keywordError = ref("");
const selectedNode = ref<ChainNode | null>(null);
const enterpriseDialogVisible = ref(false);

const query = reactive({
  keyword: "",
  chainId: undefined as number | undefined,
});

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
});

const chainNames = computed(
  () => new Map(chains.value.map((chain) => [chain.id, chain.name])),
);

async function loadChains() {
  try {
    chains.value = await fetchChains();
  } catch {
    chains.value = [];
  }
}

async function executeSearch() {
  const keyword = query.keyword.trim();
  if (!keyword) {
    keywordError.value = "关键词不能为空";
    return;
  }

  keywordError.value = "";
  hasSearched.value = true;
  loading.value = true;
  try {
    const result = await searchNodesByKeyword({
      keyword,
      chainId: query.chainId || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    });
    records.value = result.records;
    pagination.total = result.total;
    pagination.pageNum = result.pageNum;
    pagination.pageSize = result.pageSize;
  } finally {
    loading.value = false;
  }
}

async function handleSearch() {
  pagination.pageNum = 1;
  await executeSearch();
}

function resetSearch() {
  query.keyword = "";
  query.chainId = undefined;
  keywordError.value = "";
  records.value = [];
  pagination.pageNum = 1;
  pagination.pageSize = 20;
  pagination.total = 0;
  hasSearched.value = false;
}

async function changePage(pageNum: number) {
  pagination.pageNum = pageNum;
  await executeSearch();
}

async function changePageSize(pageSize: number) {
  pagination.pageSize = pageSize;
  pagination.pageNum = 1;
  await executeSearch();
}

function getChainName(chainId?: number): string {
  if (!chainId) return "—";
  return chainNames.value.get(chainId) ?? String(chainId);
}

function viewEnterprises(node: ChainNode) {
  selectedNode.value = node;
  enterpriseDialogVisible.value = true;
}

function goToTree(node: ChainNode) {
  if (!node.chainId) return;
  router.push({
    path: "/chains",
    query: { chainId: String(node.chainId) },
  });
}

onMounted(loadChains);
</script>

<style scoped>
.node-search-page {
  display: grid;
  gap: 16px;
}

.node-search-page__filters {
  padding: 16px;
}

.node-search-form {
  align-items: end;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(260px, 2fr) minmax(220px, 1fr) auto;
}

.node-search-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.node-search-form__actions {
  display: flex;
  gap: 8px;
  padding-bottom: 1px;
}

.node-search-page__initial {
  padding: 52px 20px;
  text-align: center;
}

.node-search-page__pagination {
  display: flex;
  justify-content: flex-end;
  padding: 16px;
}

.row-actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 860px) {
  .node-search-form {
    grid-template-columns: 1fr;
  }

  .node-search-form__actions {
    padding-bottom: 0;
  }

  .node-search-page__pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
