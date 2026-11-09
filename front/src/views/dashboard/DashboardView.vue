<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  Calendar,
  Coin,
  DataLine,
  Link,
  OfficeBuilding,
  PieChart,
  Refresh,
} from "@element-plus/icons-vue";
import { fetchDashboardOverview } from "@/api/dashboard";
import type { DashboardMetric, DashboardOverview } from "@/types/domain";

const overview = ref<DashboardOverview>();
const loading = ref(false);

const metricIcons = [OfficeBuilding, Coin, Link, PieChart];
const chartColors = [
  "#1f6fff",
  "#18a66a",
  "#ff9810",
  "#7b61ff",
  "#ef5b5b",
  "#06b6d4",
  "#f472b6",
  "#84cc16",
];

const totalEnterprises = computed(() =>
  overview.value?.chainDistribution.reduce((sum, item) => sum + item.value, 0),
);

const donutStyle = computed(() => {
  const records = overview.value?.chainDistribution ?? [];
  if (records.length === 0) return { background: "#edf3fb" };

  const total = records.reduce((sum, item) => sum + item.value, 0) || 1;
  let cursor = 0;
  const segments = records.map((item, index) => {
    const start = cursor;
    cursor += (item.value / total) * 360;
    return `${chartColors[index % chartColors.length]} ${start}deg ${cursor}deg`;
  });
  return { background: `conic-gradient(${segments.join(", ")})` };
});

const maxNodeValue = computed(() => {
  const values = overview.value?.nodeStatistics.map((item) => item.value) ?? [];
  return Math.max(...values, 1);
});

const progressPoints = computed(() => {
  const records = overview.value?.weeklyProgress ?? [];
  const max = Math.max(...records.map((item) => item.recommendations), 1);
  const step = records.length > 1 ? 520 / (records.length - 1) : 0;
  return records.map((item, index) => ({
    x: 36 + index * step,
    y: 176 - (item.recommendations / max) * 132,
    item,
  }));
});

const progressPolyline = computed(() =>
  progressPoints.value.map((point) => `${point.x},${point.y}`).join(" "),
);

const dateRangeText = computed(() => {
  const records = overview.value?.weeklyProgress ?? [];
  if (records.length === 0) return "当前数据";
  return `${records[0].date} ~ ${records[records.length - 1].date}`;
});

const progressTotals = computed(() => {
  const records = overview.value?.weeklyProgress ?? [];
  return records.reduce(
    (totals, item) => ({
      inbound: totals.inbound + item.inbound,
      nodes: totals.nodes + item.nodes,
      recommendations: totals.recommendations + item.recommendations,
      accepted: totals.accepted + item.accepted,
    }),
    { inbound: 0, nodes: 0, recommendations: 0, accepted: 0 },
  );
});

// 待挂链企业数（从指标卡片取，与后端计算一致：总企业数 - 已挂链企业数）
const pendingEnterprises = computed(() => {
  const metrics = overview.value?.metrics;
  if (!metrics || metrics.length < 3) return 0;
  return Number(metrics[2].value) || 0;
});

const matchingRingStyle = computed(() => {
  const summary = overview.value?.matchingSummary;
  if (!summary) return {};
  const values = [
    { color: "#1f6fff", value: summary.accepted },
    { color: "#ef5b5b", value: summary.rejected },
    { color: "#ff9810", value: summary.manual },
    { color: "#dbe5f2", value: pendingEnterprises.value },
  ];
  const total = values.reduce((sum, item) => sum + item.value, 0);
  if (total === 0) return { background: "#edf3fb" };

  let cursor = 0;
  const segments = values.map((item) => {
    const start = cursor;
    cursor += (item.value / total) * 360;
    return `${item.color} ${start}deg ${cursor}deg`;
  });
  return { background: `conic-gradient(${segments.join(", ")})` };
});

async function loadDashboard() {
  loading.value = true;
  try {
    overview.value = await fetchDashboardOverview();
  } finally {
    loading.value = false;
  }
}

function metricClass(metric: DashboardMetric) {
  return `dashboard-metric--${metric.tone ?? "blue"}`;
}

function formatMetricValue(metric: DashboardMetric) {
  return `${metric.value}${metric.unit ?? ""}`;
}

function nodeBarStyle(value: number, color: string) {
  const height = Math.max(16, Math.round((value / maxNodeValue.value) * 166));
  return { height: `${height}px`, backgroundColor: color };
}

function progressBarStyle(value: number) {
  const max =
    Math.max(
      ...(overview.value?.weeklyProgress.map((item) => item.recommendations) ??
        []),
      1,
    ) || 1;
  return { height: `${Math.max(10, Math.round((value / max) * 94))}px` };
}

onMounted(loadDashboard);
</script>

<template>
  <div v-loading="loading" class="dashboard page">
    <div class="page__header dashboard__header">
      <div>
        <h2 class="page__title">数据看板</h2>
        <p class="page__description">
          汇总企业入库、产业链挂接、智能推荐与近期操作情况。
        </p>
      </div>
      <div class="dashboard__filters">
        <el-button :icon="Calendar" plain>{{ dateRangeText }}</el-button>
        <el-button :icon="Refresh" plain @click="loadDashboard">刷新</el-button>
      </div>
    </div>

    <template v-if="overview">
      <section class="dashboard__metrics">
        <article
          v-for="(metric, index) in overview.metrics"
          :key="metric.label"
          class="dashboard-metric"
          :class="metricClass(metric)"
        >
          <div class="dashboard-metric__icon">
            <el-icon><component :is="metricIcons[index]" /></el-icon>
          </div>
          <div class="dashboard-metric__body">
            <span class="dashboard-metric__label">{{ metric.label }}</span>
            <strong>{{ formatMetricValue(metric) }}</strong>
            <small v-if="metric.trend">{{ metric.trend }}</small>
          </div>
        </article>
      </section>

      <section class="dashboard__grid dashboard__grid--top">
        <article class="dashboard-card dashboard-card--distribution">
          <div class="dashboard-card__title">
            <h3>产业链分布</h3>
          </div>
          <div class="distribution">
            <div class="distribution__donut" :style="donutStyle">
              <div class="distribution__center">
                <strong>{{ totalEnterprises?.toLocaleString() }}</strong>
                <span>总数</span>
              </div>
            </div>
            <ul class="distribution__legend">
              <li
                v-for="(item, index) in overview.chainDistribution"
                :key="item.name"
              >
                <span
                  class="distribution__dot"
                  :style="{
                    backgroundColor: chartColors[index % chartColors.length],
                  }"
                />
                <span>{{ item.name }}</span>
                <strong>{{ item.value }}</strong>
                <em>{{ item.percent }}</em>
              </li>
            </ul>
          </div>
        </article>

        <article class="dashboard-card dashboard-card--matching">
          <div class="dashboard-card__title">
            <h3>AI 智能挂链采纳情况</h3>
          </div>
          <div class="matching-summary">
            <div class="matching-summary__ring" :style="matchingRingStyle">
              <div>
                <strong>{{ pendingEnterprises }}</strong>
                <span>待挂链</span>
              </div>
            </div>
            <div class="matching-summary__legend">
              <p>
                <i class="is-blue" />已采纳
                <strong>{{ overview.matchingSummary.accepted }}</strong>
              </p>
              <p>
                <i class="is-red" />已拒绝
                <strong>{{ overview.matchingSummary.rejected }}</strong>
              </p>
              <p>
                <i class="is-amber" />人工挂链
                <strong>{{ overview.matchingSummary.manual }}</strong>
              </p>
            </div>
          </div>
          <div class="matching-summary__stats">
            <div>
              <span>平均置信度</span>
              <strong>{{ overview.matchingSummary.averageConfidence }}</strong>
            </div>
            <div>
              <span>本周推荐次数</span>
              <strong class="is-green">{{
                overview.matchingSummary.weeklyRecommendations
              }}</strong>
            </div>
          </div>
        </article>

        <article class="dashboard-card dashboard-card--nodes">
          <div class="dashboard-card__title">
            <h3>节点企业统计</h3>
          </div>
          <div class="node-chart">
            <div
              v-for="(item, index) in overview.nodeStatistics"
              :key="item.name"
              class="node-chart__item"
            >
              <strong>{{ item.value }}</strong>
              <span
                class="node-chart__bar"
                :style="{
                  ...nodeBarStyle(item.value, item.tone),
                  animationDelay: `${index * 0.05}s`,
                }"
              />
              <small>{{ item.name }}</small>
            </div>
          </div>
        </article>
      </section>

      <section class="dashboard__grid dashboard__grid--bottom">
        <article class="dashboard-card dashboard-card--progress">
          <div class="dashboard-card__title">
            <h3>本周进度</h3>
          </div>
          <div class="progress-kpis">
            <div>
              <span>入库企业</span>
              <strong>{{ progressTotals.inbound }}<small>家</small></strong>
            </div>
            <div>
              <span>新增节点</span>
              <strong>{{ progressTotals.nodes }}<small>个</small></strong>
            </div>
            <div>
              <span>智能推荐</span>
              <strong
                >{{ progressTotals.recommendations }}<small>次</small></strong
              >
            </div>
            <div>
              <span>采纳次数</span>
              <strong>{{ progressTotals.accepted }}<small>次</small></strong>
            </div>
          </div>
          <div class="progress-chart">
            <svg
              viewBox="0 0 592 206"
              preserveAspectRatio="none"
              aria-hidden="true"
            >
              <line x1="28" y1="44" x2="574" y2="44" />
              <line x1="28" y1="88" x2="574" y2="88" />
              <line x1="28" y1="132" x2="574" y2="132" />
              <line x1="28" y1="176" x2="574" y2="176" />
              <polyline :points="progressPolyline" />
              <circle
                v-for="point in progressPoints"
                :key="point.item.date"
                :cx="point.x"
                :cy="point.y"
                r="4"
              />
            </svg>
            <div class="progress-bars">
              <div
                v-for="item in overview.weeklyProgress"
                :key="item.date"
                class="progress-bars__item"
              >
                <span :style="progressBarStyle(item.accepted)" />
                <small>{{ item.date }}</small>
              </div>
            </div>
          </div>
        </article>

        <article class="dashboard-card dashboard-card--operations">
          <div class="dashboard-card__title">
            <h3>最近操作</h3>
          </div>
          <el-table :data="overview.recentOperations" border>
            <el-table-column label="时间" min-width="110" prop="createdAt" />
            <el-table-column label="操作类型" min-width="150" prop="action" />
            <el-table-column label="对象" min-width="210" prop="targetName" />
            <el-table-column label="操作人" min-width="90" prop="operator" />
            <el-table-column label="结果" min-width="90">
              <template #default="{ row }">
                <span
                  class="operation-result"
                  :class="{
                    'is-danger': ['失败', '已拒绝'].includes(row.result),
                  }"
                >
                  {{ row.result }}
                </span>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            class="dashboard-card__link"
            :icon="DataLine"
            link
            type="primary"
            @click="$router.push('/operation-logs')"
          >
            查看全部操作
          </el-button>
        </article>
      </section>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  gap: 18px;
}

.dashboard__header {
  align-items: center;
}

.dashboard__filters {
  display: flex;
  gap: 10px;
}

.dashboard__metrics {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-metric {
  align-items: center;
  animation: fadeInUp 0.5s ease-out both;
  backdrop-filter: blur(18px);
  background:
    radial-gradient(circle at 100% 0, rgb(8 191 211 / 13%), transparent 34%),
    linear-gradient(135deg, rgb(255 255 255 / 94%), rgb(246 251 255 / 82%));
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 18px 48px rgb(16 64 120 / 9%);
  display: flex;
  gap: 20px;
  min-height: 138px;
  padding: 22px 28px;
  position: relative;
  overflow: hidden;
}

.dashboard-metric::before {
  background: linear-gradient(180deg, #1f6fff, #08bfd3);
  content: "";
  inset: 18px auto 18px 0;
  opacity: 0.85;
  position: absolute;
  width: 3px;
}

.dashboard-metric:nth-child(1) {
  animation-delay: 0s;
}
.dashboard-metric:nth-child(2) {
  animation-delay: 0.1s;
}
.dashboard-metric:nth-child(3) {
  animation-delay: 0.2s;
}
.dashboard-metric:nth-child(4) {
  animation-delay: 0.3s;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dashboard-metric__icon {
  align-items: center;
  background:
    linear-gradient(135deg, rgb(31 111 255 / 16%), rgb(8 191 211 / 13%)),
    #eaf2ff;
  border-radius: 50%;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 60%);
  color: var(--app-accent);
  display: flex;
  flex: 0 0 auto;
  font-size: 34px;
  height: 72px;
  justify-content: center;
  width: 72px;
}

.dashboard-metric__body {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.dashboard-metric__label {
  color: #405674;
  font-weight: 700;
}

.dashboard-metric strong {
  color: var(--app-accent);
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
}

.dashboard-metric small {
  color: #18a66a;
  font-size: 14px;
}

.dashboard-metric--green .dashboard-metric__icon {
  background: #e8f7f0;
  color: #18a66a;
}

.dashboard-metric--green strong {
  color: #18a66a;
}

.dashboard-metric--amber .dashboard-metric__icon {
  background: #fff3df;
  color: #ff9810;
}

.dashboard-metric--amber strong {
  color: #ff9810;
}

.dashboard-metric--amber small {
  color: #ef4444;
}

.dashboard-metric--purple .dashboard-metric__icon {
  background: #f0ebff;
  color: #7b61ff;
}

.dashboard-metric--purple strong {
  color: #7b61ff;
}

.dashboard__grid {
  display: grid;
  gap: 18px;
}

.dashboard__grid--top {
  grid-template-columns: 1fr 1fr;
}

.dashboard-card--nodes {
  grid-column: 1 / -1;
}

.dashboard__grid--bottom {
  grid-template-columns: 1.08fr 1fr;
}

.dashboard-card {
  animation: fadeInUp 0.5s ease-out both;
  backdrop-filter: blur(18px);
  background:
    radial-gradient(circle at 100% 0, rgb(31 111 255 / 8%), transparent 30%),
    linear-gradient(180deg, rgb(255 255 255 / 92%), rgb(248 251 255 / 84%));
  border: 1px solid rgb(216 229 245 / 92%);
  border-radius: 8px;
  box-shadow: 0 18px 48px rgb(16 64 120 / 9%);
  min-width: 0;
  overflow: hidden;
  padding: 18px 20px;
  position: relative;
}

.dashboard-card::before {
  background:
    radial-gradient(circle at 10% 0, rgb(8 191 211 / 10%), transparent 30%),
    linear-gradient(90deg, rgb(31 111 255 / 7%), transparent 42%);
  content: "";
  inset: 0;
  opacity: 0.72;
  pointer-events: none;
  position: absolute;
}

.dashboard-card > * {
  position: relative;
  z-index: 1;
}

.dashboard-card--distribution {
  animation-delay: 0.1s;
}
.dashboard-card--nodes {
  animation-delay: 0.2s;
}
.dashboard-card--matching {
  animation-delay: 0.3s;
}
.dashboard-card--progress {
  animation-delay: 0.4s;
}
.dashboard-card--operations {
  animation-delay: 0.5s;
}

.dashboard-card__title {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.dashboard-card__title h3 {
  color: var(--app-ink);
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0;
  margin: 0;
}

.distribution {
  align-items: center;
  display: grid;
  gap: 14px;
  grid-template-columns: 188px minmax(0, 1fr);
  min-height: 230px;
}

.distribution__donut {
  animation: donutGrow 0.8s ease-out both;
  border-radius: 50%;
  box-shadow:
    0 0 0 10px rgb(31 111 255 / 5%),
    0 18px 42px rgb(31 111 255 / 14%);
  display: grid;
  height: 178px;
  place-items: center;
  width: 178px;
}

@keyframes donutGrow {
  from {
    opacity: 0;
    transform: scale(0.5) rotate(-90deg);
  }
  to {
    opacity: 1;
    transform: scale(1) rotate(0deg);
  }
}

.distribution__center {
  align-items: center;
  background: rgb(255 255 255 / 92%);
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgb(216 229 245 / 88%);
  display: flex;
  flex-direction: column;
  height: 92px;
  justify-content: center;
  width: 92px;
}

.distribution__center strong {
  color: #14284a;
  font-size: 24px;
  line-height: 1;
}

.distribution__center span {
  color: #6d7d99;
  margin-top: 6px;
}

.distribution__legend {
  display: grid;
  gap: 16px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.distribution__legend li {
  align-items: center;
  color: #273957;
  display: grid;
  gap: 8px;
  grid-template-columns: 10px 1fr auto auto;
}

.distribution__dot {
  border-radius: 50%;
  height: 10px;
  width: 10px;
}

.distribution__legend strong {
  font-weight: 700;
}

.distribution__legend em {
  color: #8090aa;
  font-style: normal;
}

.node-chart {
  align-items: end;
  background:
    radial-gradient(circle at 50% 0, rgb(31 111 255 / 8%), transparent 34%),
    linear-gradient(180deg, rgb(247 251 255 / 82%), rgb(238 246 255 / 42%));
  border: 1px solid #e1eaf7;
  border-radius: 8px;
  display: grid;
  gap: 9px;
  grid-template-columns: repeat(auto-fill, minmax(56px, 1fr));
  min-height: 238px;
  padding: 18px 12px 0;
}

.node-chart__item {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.node-chart__item strong {
  color: #14284a;
  font-size: 16px;
}

.node-chart__bar {
  animation: barGrow 0.8s ease-out both;
  border-radius: 4px 4px 0 0;
  box-shadow:
    0 12px 28px rgb(31 111 255 / 14%),
    inset 0 -10px 20px rgb(0 0 0 / 6%);
  transform-origin: bottom;
  width: 32px;
}

@keyframes barGrow {
  from {
    transform: scaleY(0);
  }
  to {
    transform: scaleY(1);
  }
}

.node-chart__item small {
  color: #273957;
  line-height: 1.25;
  min-height: 34px;
  text-align: center;
}

.matching-summary {
  align-items: center;
  display: grid;
  gap: 18px;
  grid-template-columns: 160px minmax(0, 1fr);
  min-height: 164px;
}

.matching-summary__ring {
  animation: ringGrow 0.8s ease-out both;
  background: conic-gradient(
    #1f6fff 0deg 309deg,
    #ef5b5b 309deg 346deg,
    #ff9810 346deg 360deg
  );
  border-radius: 50%;
  box-shadow:
    0 0 0 10px rgb(31 111 255 / 5%),
    0 18px 42px rgb(31 111 255 / 14%);
  display: grid;
  height: 142px;
  place-items: center;
  width: 142px;
}

@keyframes ringGrow {
  from {
    opacity: 0;
    transform: scale(0.5) rotate(-180deg);
  }
  to {
    opacity: 1;
    transform: scale(1) rotate(0deg);
  }
}

.matching-summary__ring > div {
  align-items: center;
  background: rgb(255 255 255 / 92%);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  height: 78px;
  justify-content: center;
  width: 78px;
}

.matching-summary__ring strong {
  color: #14284a;
  font-size: 24px;
  line-height: 1;
}

.matching-summary__ring span,
.matching-summary__stats span {
  color: #6d7d99;
}

.matching-summary__legend {
  display: grid;
  gap: 13px;
}

.matching-summary__legend p {
  align-items: center;
  color: #273957;
  display: grid;
  gap: 10px;
  grid-template-columns: 10px 64px 1fr;
  margin: 0;
}

.matching-summary__legend i {
  border-radius: 50%;
  display: inline-block;
  height: 10px;
  width: 10px;
}

.matching-summary__legend strong {
  color: #14284a;
  text-align: right;
}

.is-blue {
  background: #1f6fff;
}

.is-red {
  background: #ef5b5b;
}

.is-amber {
  background: #ff9810;
}

.matching-summary__stats {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.matching-summary__stats div {
  background: rgb(247 251 255 / 78%);
  border: 1px solid #e1eaf7;
  border-radius: 6px;
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 12px;
}

.matching-summary__stats strong {
  color: #1f6fff;
  font-size: 24px;
}

.matching-summary__stats .is-green {
  color: #18a66a;
}

.progress-kpis {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
}

.progress-kpis div {
  background: rgb(247 251 255 / 78%);
  border: 1px solid #e7eef8;
  border-radius: 6px;
  display: grid;
  gap: 6px;
  padding: 12px 14px;
}

.progress-kpis span {
  color: #273957;
  font-weight: 700;
}

.progress-kpis strong {
  color: #1f6fff;
  font-size: 24px;
  line-height: 1;
}

.progress-kpis small {
  color: #273957;
  font-size: 13px;
  margin-left: 3px;
}

.progress-chart {
  background:
    radial-gradient(circle at 18% 0, rgb(255 152 16 / 9%), transparent 28%),
    linear-gradient(180deg, rgb(248 251 255 / 78%), rgb(255 255 255 / 40%));
  border: 1px solid #e6eef8;
  border-radius: 8px;
  min-height: 226px;
  overflow: hidden;
  padding: 10px 10px 2px;
  position: relative;
}

.progress-chart svg {
  height: 206px;
  overflow: visible;
  width: 100%;
}

.progress-chart line {
  display: none;
}

.progress-chart polyline {
  fill: none;
  filter: drop-shadow(0 5px 9px rgb(255 152 16 / 20%));
  stroke: #ff9810;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 3;
}

.progress-chart circle {
  fill: #ffffff;
  stroke: #ff9810;
  stroke-width: 3;
}

.progress-bars {
  bottom: 2px;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  left: 36px;
  position: absolute;
  right: 22px;
  top: 54px;
}

.progress-bars__item {
  align-items: center;
  display: flex;
  flex-direction: column;
  justify-content: end;
}

.progress-bars__item span {
  background: rgb(31 111 255 / 16%);
  border-radius: 3px 3px 0 0;
  width: 18px;
}

.progress-bars__item small {
  color: #536783;
  margin-top: 8px;
}

.dashboard-card--operations :deep(.el-table th.el-table__cell) {
  background: #f3f8fe;
  color: #1b2b4b;
}

.operation-result {
  color: #18a66a;
  font-weight: 700;
}

.operation-result.is-danger {
  color: #ef4444;
}

.dashboard-card__link {
  margin-top: 12px;
}

@media (max-width: 1280px) {
  .dashboard__metrics,
  .dashboard__grid--top,
  .dashboard__grid--bottom {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-card--matching {
    grid-column: 1 / -1;
  }

  .dashboard-card--nodes {
    grid-column: 1 / -1;
  }
}

@media (max-width: 860px) {
  .dashboard__metrics,
  .dashboard__grid--top,
  .dashboard__grid--bottom,
  .distribution,
  .matching-summary,
  .progress-kpis {
    grid-template-columns: 1fr;
  }

  .dashboard__filters {
    flex-wrap: wrap;
  }
}
</style>
