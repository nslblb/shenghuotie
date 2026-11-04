<script setup lang="ts">
import { ref, onMounted, onUnmounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getDashboard, getTrends, getHotTopics, getDashboardStats } from '@/api/admin'
import type { DashboardVO, TrendDataVO, HotTopicVO, DashboardStatsVO } from '@/api/admin'

const router = useRouter()

const stats = ref<DashboardVO | null>(null)
const trendData = ref<TrendDataVO | null>(null)
const hotTopics = ref<HotTopicVO[]>([])
const dashboardStats = ref<DashboardStatsVO | null>(null)
const loading = ref(true)
const trendDays = ref(7)

const trendChartRef = shallowRef<echarts.ECharts | null>(null)
const topicChartRef = shallowRef<echarts.ECharts | null>(null)
const trendChartEl = ref<HTMLDivElement | null>(null)
const topicChartEl = ref<HTMLDivElement | null>(null)

async function loadAllData() {
  loading.value = true
  try {
    const [dashboardRes, trendsRes, topicsRes, statsRes] = await Promise.all([
      getDashboard(),
      getTrends(trendDays.value),
      getHotTopics(),
      getDashboardStats(),
    ])
    stats.value = dashboardRes.data.data
    trendData.value = trendsRes.data.data
    hotTopics.value = topicsRes.data.data
    dashboardStats.value = statsRes.data.data
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderTrendChart()
  renderTopicChart()
}

function renderTrendChart() {
  if (!trendChartEl.value || !trendData.value) return

  if (!trendChartRef.value) {
    trendChartRef.value = echarts.init(trendChartEl.value)
  }

  const dates = trendData.value.userTrends.map((item) => item.date)
  const userCounts = trendData.value.userTrends.map((item) => item.count)
  const postCounts = trendData.value.postTrends.map((item) => item.count)

  trendChartRef.value.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
    },
    legend: {
      data: ['用户注册', '帖子发布'],
      bottom: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '12%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
    },
    series: [
      {
        name: '用户注册',
        type: 'line',
        smooth: true,
        lineStyle: { width: 3 },
        showSymbol: true,
        areaStyle: {
          opacity: 0.1,
        },
        emphasis: {
          focus: 'series',
        },
        data: userCounts,
      },
      {
        name: '帖子发布',
        type: 'line',
        smooth: true,
        lineStyle: { width: 3 },
        showSymbol: true,
        areaStyle: {
          opacity: 0.1,
        },
        emphasis: {
          focus: 'series',
        },
        data: postCounts,
      },
    ],
  })
}

function renderTopicChart() {
  if (!topicChartEl.value || hotTopics.value.length === 0) return

  if (!topicChartRef.value) {
    topicChartRef.value = echarts.init(topicChartEl.value)
  }

  const data = hotTopics.value.map((item) => ({
    name: item.topic,
    value: item.count,
  }))

  topicChartRef.value.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 10,
      top: 20,
      bottom: 20,
    },
    series: [
      {
        name: '话题分布',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold',
          },
        },
        labelLine: {
          show: false,
        },
        data: data,
      },
    ],
  })
}

function handleResize() {
  trendChartRef.value?.resize()
  topicChartRef.value?.resize()
}

function handleTrendDaysChange() {
  loadAllData()
}

function goToReports() {
  router.push('/admin/reports')
}

function goToAppeals() {
  router.push('/admin/appeals')
}

const statCards = [
  { key: 'totalUsers', label: '总用户数', icon: 'User', color: '#409eff' },
  { key: 'totalPosts', label: '总帖子数', icon: 'Document', color: '#67c23a' },
  { key: 'totalComments', label: '总评论数', icon: 'ChatDotRound', color: '#e6a23c' },
  { key: 'totalLikes', label: '总点赞数', icon: 'Star', color: '#f56c6c' },
]

onMounted(() => {
  loadAllData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChartRef.value?.dispose()
  topicChartRef.value?.dispose()
})
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <h2 style="margin-bottom: 24px">仪表盘</h2>

    <div class="stat-grid">
      <el-card v-for="card in statCards" :key="card.key" shadow="hover">
        <div class="stat-card">
          <div class="stat-icon" :style="{ background: card.color }">
            <el-icon :size="28" color="#fff"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats?.[card.key as keyof DashboardVO] ?? '-' }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <div class="today-grid">
      <el-card shadow="hover">
        <div class="today-item">
          <span>今日新增用户</span>
          <strong>{{ stats?.todayNewUsers ?? 0 }}</strong>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div class="today-item">
          <span>今日新增帖子</span>
          <strong>{{ stats?.todayNewPosts ?? 0 }}</strong>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div class="today-item">
          <span>今日评论</span>
          <strong>{{ dashboardStats?.todayComments ?? 0 }}</strong>
        </div>
      </el-card>
      <el-card shadow="hover">
        <div class="today-item">
          <span>今日点赞</span>
          <strong>{{ dashboardStats?.todayLikes ?? 0 }}</strong>
        </div>
      </el-card>
    </div>

    <div class="task-cards">
      <el-card shadow="hover" class="task-card" @click="goToReports">
        <div class="task-content">
          <el-icon :size="32" color="#e6a23c"><Warning /></el-icon>
          <div class="task-info">
            <div class="task-label">待处理举报</div>
            <div class="task-value">{{ dashboardStats?.pendingReports ?? 0 }}</div>
          </div>
        </div>
        <el-icon class="task-arrow"><ArrowRight /></el-icon>
      </el-card>
      <el-card shadow="hover" class="task-card" @click="goToAppeals">
        <div class="task-content">
          <el-icon :size="32" color="#409eff"><ChatDotRound /></el-icon>
          <div class="task-info">
            <div class="task-label">待处理申诉</div>
            <div class="task-value">{{ dashboardStats?.pendingAppeals ?? 0 }}</div>
          </div>
        </div>
        <el-icon class="task-arrow"><ArrowRight /></el-icon>
      </el-card>
      <el-card shadow="hover" class="task-card online-card">
        <div class="task-content">
          <el-icon :size="32" color="#67c23a"><User /></el-icon>
          <div class="task-info">
            <div class="task-label">实时在线</div>
            <div class="task-value online-value">{{ dashboardStats?.onlineUsers ?? 0 }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <div class="chart-section">
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>趋势统计</span>
            <el-radio-group v-model="trendDays" size="small" @change="handleTrendDaysChange">
              <el-radio-button :value="7">近7天</el-radio-button>
              <el-radio-button :value="14">近14天</el-radio-button>
              <el-radio-button :value="30">近30天</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <div ref="trendChartEl" class="chart-container"></div>
      </el-card>
    </div>

    <div class="chart-section">
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>热门话题 TOP10</span>
          </div>
        </template>
        <div ref="topicChartEl" class="chart-container topic-chart"></div>
        <div v-if="hotTopics.length === 0" class="empty-topics">
          <el-empty description="暂无话题数据" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.today-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.today-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
}

.today-item strong {
  font-size: 24px;
  color: #409eff;
}

.task-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.task-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.task-card:hover {
  transform: translateY(-2px);
}

.task-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.task-info {
  display: flex;
  flex-direction: column;
}

.task-label {
  font-size: 14px;
  color: #909399;
}

.task-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.online-value {
  color: #67c23a;
}

.task-arrow {
  color: #c0c4cc;
  font-size: 20px;
}

.online-card {
  cursor: default;
}

.online-card:hover {
  transform: none;
}

.chart-section {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 320px;
}

.topic-chart {
  height: 300px;
}

.empty-topics {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .today-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .task-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .today-grid {
    grid-template-columns: 1fr;
  }

  .task-cards {
    grid-template-columns: 1fr;
  }
}
</style>
