<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import * as echarts from "echarts/core";
import { RadarChart } from "echarts/charts";
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import {
  fetchEnterpriseProfileAnalysis,
  fetchEnterpriseRadar,
} from "@/api/enterprise";
import type { EnterpriseRadarMetric, SimilarEnterprise } from "@/types/domain";

echarts.use([
  RadarChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

const props = defineProps<{ enterpriseId: number }>();
const router = useRouter();
const radarLoading = ref(false);
const similarLoading = ref(false);
const refreshing = ref(false);
const error = ref("");
const similarLimit = ref<5 | 10 | 20>(5);
const metrics = ref<EnterpriseRadarMetric[]>([]);
const similarEnterprises = ref<SimilarEnterprise[]>([]);
const chartEl = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let radarRequestId = 0;
let similarRequestId = 0;

async function loadRadar(keepOld = false) {
  if (!props.enterpriseId || radarLoading.value) return;
  const current = ++radarRequestId;
  if (!keepOld) {
    radarLoading.value = true;
    error.value = "";
  }
  try {
    const result = await fetchEnterpriseRadar(props.enterpriseId);
    if (current !== radarRequestId) return;
    metrics.value = result.metrics;
    await nextTick();
    renderChart();
  } catch (err) {
    if (current !== radarRequestId) return;
    const message = err instanceof Error ? err.message : "企业雷达加载失败";
    if (keepOld && metrics.value.length) {
      ElMessage.error(message);
    } else {
      error.value = message;
    }
  } finally {
    if (current === radarRequestId) radarLoading.value = false;
  }
}

async function loadSimilar(keepOld = false) {
  if (!props.enterpriseId || similarLoading.value) return;
  const current = ++similarRequestId;
  if (!keepOld) similarLoading.value = true;
  try {
    const result = await fetchEnterpriseProfileAnalysis(
      props.enterpriseId,
      similarLimit.value,
    );
    if (current !== similarRequestId) return;
    similarEnterprises.value = result.similarEnterprises;
  } catch (err) {
    if (current !== similarRequestId) return;
    const message = err instanceof Error ? err.message : "相似企业加载失败";
    if (keepOld && similarEnterprises.value.length) {
      ElMessage.error(message);
    }
  } finally {
    if (current === similarRequestId) similarLoading.value = false;
  }
}

async function refresh() {
  if (radarLoading.value || similarLoading.value || refreshing.value) return;
  refreshing.value = true;
  try {
    await Promise.all([loadRadar(true), loadSimilar(true)]);
  } finally {
    refreshing.value = false;
  }
}

function renderChart() {
  if (!chartEl.value || metrics.value.length === 0) return;
  if (!chart) chart = echarts.init(chartEl.value);
  chart.setOption({
    tooltip: {},
    radar: {
      indicator: metrics.value.map((item) => ({ name: item.name, max: 100 })),
    },
    series: [
      {
        type: "radar",
        data: [
          {
            value: metrics.value.map((item) => item.value),
            name: "企业画像",
          },
        ],
      },
    ],
  });
}

function goEnterprise(row: SimilarEnterprise) {
  router.push(`/enterprises/${row.enterpriseId}`);
}

function handleResize() {
  chart?.resize();
}

watch(
  () => props.enterpriseId,
  () => {
    loadRadar(false);
    loadSimilar(false);
  },
);
watch(similarLimit, () => loadSimilar(true));

onMounted(() => {
  window.addEventListener("resize", handleResize);
  loadRadar(false);
  loadSimilar(false);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <section class="surface enterprise-profile" v-loading="refreshing">
    <div class="section-head">
      <div>
        <h3>企业多维画像</h3>
        <p class="section-head__desc">基于企业信息生成画像，并推荐相似企业。</p>
      </div>
      <div class="section-head__actions">
        <el-select v-model="similarLimit" class="limit-select" size="small">
          <el-option :value="5" label="Top 5" />
          <el-option :value="10" label="Top 10" />
          <el-option :value="20" label="Top 20" />
        </el-select>
        <el-button
          :icon="Refresh"
          :loading="refreshing"
          plain
          size="small"
          @click="refresh"
        >
          刷新
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="error && !metrics.length"
      :closable="false"
      :title="error"
      type="error"
    />

    <div class="profile-grid">
      <div class="radar-card">
        <div v-if="metrics.length" ref="chartEl" class="radar-chart" />
        <div v-else-if="radarLoading" class="empty-note">雷达加载中...</div>
        <div v-else class="empty-note">暂无画像数据</div>
        <ul v-if="metrics.length" class="metric-list">
          <li v-for="metric in metrics" :key="metric.key">
            <span>{{ metric.name }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.rawValue }}</small>
          </li>
        </ul>
      </div>

      <div class="similar-card">
        <h4>相似企业推荐</h4>
        <div v-if="similarLoading" class="empty-note">相似企业加载中...</div>
        <div v-else-if="similarEnterprises.length" class="similar-list">
          <button
            v-for="item in similarEnterprises"
            :key="item.enterpriseId"
            class="similar-item"
            type="button"
            @click="goEnterprise(item)"
          >
            <span class="similar-item__main">
              <strong>{{ item.enterpriseName }}</strong>
              <em>{{ item.similarityPercent }}%</em>
            </span>
            <span class="similar-item__meta">
              {{ item.industry || "未填写行业" }} ·
              {{ item.mainProducts || "未填写主营产品" }}
            </span>
            <span class="similar-item__reason">
              {{ item.reasons.join("；") || "暂无相似原因" }}
            </span>
          </button>
        </div>
        <div v-else class="empty-note">
          暂无相似企业，可尝试增加数量或刷新画像
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.enterprise-profile {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-head {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 0;
}

.section-head h3 {
  font-size: 18px;
  margin: 0;
}

.section-head__desc {
  color: #667085;
  margin: 6px 0 0;
}

.section-head__actions {
  display: flex;
  gap: 8px;
}

.limit-select {
  width: 100px;
}

.profile-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 420px);
}

.radar-card,
.similar-card {
  background: #f8fafc;
  border: 1px solid #e4e7ec;
  border-radius: 14px;
  padding: 16px;
}

.radar-chart {
  height: 320px;
  width: 100%;
}

.metric-list {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.metric-list li {
  align-items: center;
  background: #fff;
  border-radius: 10px;
  display: grid;
  gap: 4px;
  grid-template-columns: 1fr auto;
  padding: 10px;
}

.metric-list small {
  color: #667085;
  grid-column: 1 / -1;
}

.similar-card h4 {
  font-size: 16px;
  margin: 0 0 12px;
}

.similar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.similar-item {
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 12px;
  cursor: pointer;
  padding: 12px;
  text-align: left;
}

.similar-item:hover {
  border-color: #2f6bff;
}

.similar-item__main {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.similar-item__main em {
  color: #2f6bff;
  font-style: normal;
  font-weight: 700;
}

.similar-item__meta,
.similar-item__reason {
  color: #667085;
  display: block;
  font-size: 13px;
  margin-top: 6px;
}

.empty-note {
  color: #667085;
  padding: 48px 0;
  text-align: center;
}

@media (max-width: 960px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
