<template>
  <div class="report-page">
    <TitleBar />

    <div class="rp-body">

      <!-- ── 查询栏 ── -->
      <div class="query-bar">
        <div class="qb-quick">
          <button
            v-for="q in quickOptions"
            :key="q.label"
            class="qbtn"
            :class="{ active: activeQuick === q.label }"
            @click="applyQuick(q)"
          >{{ q.label }}</button>
        </div>

        <div class="qb-range">
          <span class="qb-label">起止日期</span>
          <input class="date-input" type="date" v-model="startDate" />
          <span class="qb-sep">—</span>
          <input class="date-input" type="date" v-model="endDate" />
          <button class="qbtn-primary" @click="fetchData" :disabled="loading">
            {{ loading ? '查询中...' : '查 询' }}
          </button>
        </div>
      </div>

      <!-- ── 统计卡片 ── -->
      <div class="stat-row">
        <div class="stat-card">
          <div class="sc-label">查询时段总运量</div>
          <div class="sc-value accent-green">{{ fmt(totalTon) }}</div>
          <div class="sc-unit">吨</div>
        </div>
        <div class="stat-card">
          <div class="sc-label">峰值时段运量</div>
          <div class="sc-value accent-yellow">{{ fmt(peakTon) }}</div>
          <div class="sc-unit">吨 / 10 min</div>
        </div>
        <div class="stat-card">
          <div class="sc-label">时段平均运量</div>
          <div class="sc-value">{{ fmt(avgTon) }}</div>
          <div class="sc-unit">吨 / 10 min</div>
        </div>
        <div class="stat-card">
          <div class="sc-label">有效记录时段</div>
          <div class="sc-value accent-blue">{{ records.length }}</div>
          <div class="sc-unit">个</div>
        </div>
      </div>

      <!-- ── 图表区 ── -->
      <div class="chart-section">
        <div class="section-title">
          <span class="st-dot"></span>每时段运量趋势（10 分钟 / 段）
        </div>
        <div class="chart-wrap">
          <div v-if="records.length === 0 && !loading" class="empty-tip">
            <span>暂无数据</span>
            <span class="empty-sub">后端运行满 10 分钟后将自动记录第一条数据</span>
          </div>
          <div v-else ref="chartEl" class="chart-canvas"></div>
        </div>
      </div>

      <!-- ── 数据表格 ── -->
      <div class="table-section">
        <div class="section-title">
          <span class="st-dot st-dot--cyan"></span>明细记录
          <span class="record-count">共 {{ records.length }} 条</span>
        </div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>记录时间</th>
                <th>本时段运量 (t)</th>
                <th>本班累计 (t)</th>
                <th>今日累计 (t)</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="records.length === 0">
                <td colspan="4" class="no-data">— 暂无数据 —</td>
              </tr>
              <tr
                v-for="r in pagedRecords"
                :key="r.id"
                :class="{ peak: r.periodTon === peakTon && peakTon > 0 }"
              >
                <td class="td-time">{{ formatTs(r.ts) }}</td>
                <td class="td-num">{{ r.periodTon.toFixed(1) }}</td>
                <td class="td-num muted">{{ Math.round(r.shiftTon).toLocaleString() }}</td>
                <td class="td-num muted">{{ Math.round(r.dayTon).toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="pagination" v-if="totalPages > 1">
          <button class="page-btn" :disabled="page === 1" @click="page--">‹</button>
          <span class="page-info">{{ page }} / {{ totalPages }}</span>
          <button class="page-btn" :disabled="page === totalPages" @click="page++">›</button>
        </div>
      </div>

    </div>

    <StatusBar :connected="false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import TitleBar  from '@/components/TitleBar.vue'
import StatusBar from '@/components/StatusBar.vue'

// ── 日期状态 ─────────────────────────────────────────────
const today     = () => new Date().toISOString().slice(0, 10)
const startDate = ref(today())
const endDate   = ref(today())
const activeQuick = ref('今天')

const quickOptions = [
  {
    label: '今天',
    apply: () => {
      const t = today()
      startDate.value = t
      endDate.value   = t
    }
  },
  {
    label: '昨天',
    apply: () => {
      const d = new Date()
      d.setDate(d.getDate() - 1)
      const s = d.toISOString().slice(0, 10)
      startDate.value = s
      endDate.value   = s
    }
  },
  {
    label: '近 7 天',
    apply: () => {
      const d = new Date()
      d.setDate(d.getDate() - 6)
      startDate.value = d.toISOString().slice(0, 10)
      endDate.value   = today()
    }
  },
]

function applyQuick(q) {
  activeQuick.value = q.label
  q.apply()
  fetchData()
}

// ── 数据获取 ─────────────────────────────────────────────
const records = ref([])
const loading = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const url = `/api/report/records?start=${startDate.value}&end=${endDate.value}`
    const res = await fetch(url)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    records.value = await res.json()
    page.value = 1
  } catch (e) {
    console.error('[Report] 查询失败', e)
    records.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchData())

// ── 统计计算 ─────────────────────────────────────────────
const totalTon = computed(() =>
  records.value.reduce((s, r) => s + r.periodTon, 0)
)
const peakTon = computed(() =>
  records.value.length ? Math.max(...records.value.map(r => r.periodTon)) : 0
)
const avgTon = computed(() =>
  records.value.length ? totalTon.value / records.value.length : 0
)

function fmt(v) {
  return v > 0 ? v.toFixed(1) : '—'
}

// ── 分页 ─────────────────────────────────────────────────
const PAGE_SIZE = 20
const page = ref(1)

const totalPages = computed(() =>
  Math.max(1, Math.ceil(records.value.length / PAGE_SIZE))
)
const pagedRecords = computed(() => {
  const s = (page.value - 1) * PAGE_SIZE
  return records.value.slice(s, s + PAGE_SIZE)
})

// ── 时间格式化 ────────────────────────────────────────────
function formatTs(ts) {
  // ts 格式：2024-03-16T09:00:00
  if (!ts) return '—'
  const d = new Date(ts)
  const date = d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  const time = d.toTimeString().slice(0, 5)
  return `${date} ${time}`
}

function formatTimeOnly(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  // 多天查询时显示日期+时间，单天只显示时间
  const sameDay = startDate.value === endDate.value
  if (sameDay) return d.toTimeString().slice(0, 5)
  return `${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')} ${d.toTimeString().slice(0,5)}`
}

// ── ECharts ───────────────────────────────────────────────
const chartEl = ref(null)
let chart = null

function initChart() {
  if (!chartEl.value) return
  chart = echarts.init(chartEl.value)
  window.addEventListener('resize', handleResize)
}

function handleResize() {
  chart?.resize()
}

function renderChart() {
  if (!chart || records.value.length === 0) return

  const xLabels = records.value.map(r => formatTimeOnly(r.ts))
  const barData  = records.value.map(r => r.periodTon)
  const lineData = records.value.map(r => r.shiftTon - records.value[0].shiftTon + records.value[0].periodTon)

  chart.setOption({
    backgroundColor: '#060c14',
    grid: { top: 48, right: 60, bottom: 70, left: 70, containLabel: false },
    legend: {
      top: 10,
      right: 60,
      textStyle: { color: '#7aabcc', fontSize: 12 },
      data: ['时段运量', '累计运量'],
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0a1628',
      borderColor: '#1a3a5c',
      borderWidth: 1,
      textStyle: { color: '#e8f4ff', fontSize: 13 },
      formatter(params) {
        let s = `<div style="color:#7aabcc;margin-bottom:4px">${params[0].name}</div>`
        for (const p of params) {
          const dot = `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color};margin-right:6px"></span>`
          s += `${dot}${p.seriesName}：<b>${p.value?.toFixed(1)}</b> t<br/>`
        }
        return s
      }
    },
    xAxis: {
      type: 'category',
      data: xLabels,
      axisLabel: {
        color: '#3d6080',
        fontSize: 11,
        rotate: xLabels.length > 30 ? 45 : 0,
        interval: Math.max(0, Math.floor(xLabels.length / 24) - 1),
      },
      axisLine:  { lineStyle: { color: '#1a3a5c' } },
      axisTick:  { lineStyle: { color: '#1a3a5c' } },
    },
    yAxis: [
      {
        type: 'value',
        name: '时段运量 (t)',
        nameTextStyle: { color: '#3d6080', fontSize: 11 },
        axisLabel: { color: '#3d6080', fontSize: 11 },
        axisLine:  { show: true, lineStyle: { color: '#1a3a5c' } },
        splitLine: { lineStyle: { color: '#0d2035', type: 'dashed' } },
      },
      {
        type: 'value',
        name: '累计运量 (t)',
        nameTextStyle: { color: '#3d6080', fontSize: 11 },
        axisLabel: { color: '#3d6080', fontSize: 11 },
        axisLine:  { show: true, lineStyle: { color: '#1a3a5c' } },
        splitLine: { show: false },
      }
    ],
    series: [
      {
        name: '时段运量',
        type: 'bar',
        data: barData,
        barMaxWidth: 28,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00d4ff' },
            { offset: 1, color: '#004488' },
          ]),
          borderRadius: [2, 2, 0, 0],
        },
        emphasis: {
          itemStyle: { color: '#00ff9d' }
        },
      },
      {
        name: '累计运量',
        type: 'line',
        yAxisIndex: 1,
        data: lineData,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#ffaa00', width: 1.5, type: 'dashed' },
        itemStyle: { color: '#ffaa00' },
      }
    ],
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      {
        type: 'slider',
        height: 18,
        bottom: 8,
        fillerColor: 'rgba(0,180,255,0.1)',
        borderColor: '#1a3a5c',
        handleStyle: { color: '#1a3a5c' },
        textStyle: { color: '#3d6080', fontSize: 10 },
      }
    ],
  }, true)
}

// 数据变化时重新渲染图表
watch(records, async () => {
  await nextTick()
  if (!chart && chartEl.value) initChart()
  renderChart()
})

// 图表容器出现时初始化（首次有数据时 chartEl 才渲染）
watch(chartEl, (el) => {
  if (el && !chart) {
    initChart()
    renderChart()
  }
})

onUnmounted(() => {
  chart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-deep);
}

/* ── 主体滚动区 ── */
.rp-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── 查询栏 ── */
.query-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 20px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 4px;
  flex-wrap: wrap;
}
.qb-quick {
  display: flex;
  gap: 6px;
}
.qbtn {
  padding: 6px 16px;
  font-size: 13px;
  font-family: var(--font-ui);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 3px;
  color: var(--text-sec);
  cursor: pointer;
  transition: all 0.15s;
}
.qbtn:hover  { color: var(--text-pri); border-color: var(--border-glow); }
.qbtn.active { color: var(--accent); border-color: var(--border-glow); background: rgba(0,212,255,0.07); }

.qb-range {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}
.qb-label {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
}
.date-input {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 3px;
  padding: 6px 10px;
  font-size: 13px;
  font-family: var(--font-mono);
  color: var(--text-pri);
  outline: none;
  color-scheme: dark;
  cursor: pointer;
  transition: border-color 0.15s;
}
.date-input:focus { border-color: var(--border-glow); }
.qb-sep { color: var(--text-muted); }

.qbtn-primary {
  padding: 7px 24px;
  font-size: 13px;
  font-family: var(--font-ui);
  font-weight: 600;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #0055aa, #00b4ff);
  border: none;
  border-radius: 3px;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0,180,255,0.25);
  transition: opacity 0.15s;
}
.qbtn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

/* ── 统计卡片 ── */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.stat-card {
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 18px 22px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
  overflow: hidden;
}
.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--accent), transparent);
}
.sc-label {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.5px;
}
.sc-value {
  font-family: var(--font-mono);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-pri);
  line-height: 1.1;
  letter-spacing: 1px;
}
.sc-unit {
  font-size: 12px;
  color: var(--text-muted);
}
.accent-green  { color: var(--accent2) !important; }
.accent-yellow { color: var(--warn)    !important; }
.accent-blue   { color: var(--accent)  !important; }

/* ── 图表区 ── */
.chart-section {
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 4px;
  overflow: hidden;
}
.chart-wrap {
  height: 280px;
  position: relative;
}
.chart-canvas {
  width: 100%;
  height: 100%;
}
.empty-tip {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 14px;
}
.empty-sub {
  font-size: 12px;
  color: var(--text-muted);
  opacity: 0.6;
}

/* ── 表格区 ── */
.table-section {
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 4px;
  overflow: hidden;
  padding-bottom: 4px;
}
.table-wrap {
  overflow-x: auto;
}

/* ── section 标题 ── */
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border);
}
.st-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 5px rgba(0,212,255,0.6);
  flex-shrink: 0;
}
.st-dot--cyan { background: #00e5ff; box-shadow: 0 0 5px rgba(0,229,255,0.5); }
.record-count {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.7;
}

/* ── 表格 ── */
.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table th {
  padding: 10px 20px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
  text-align: left;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.data-table td {
  padding: 9px 20px;
  font-size: 13px;
  color: var(--text-sec);
  border-bottom: 1px solid rgba(26, 58, 92, 0.5);
  white-space: nowrap;
}
.data-table tr:last-child td { border-bottom: none; }
.data-table tr:hover td { background: var(--bg-hover); }
.data-table tr.peak td { background: rgba(255,170,0,0.05); }
.data-table tr.peak td.td-num:first-of-type { color: var(--warn); font-weight: 600; }

.td-time { font-family: var(--font-mono); font-size: 13px; color: var(--text-pri); }
.td-num  { font-family: var(--font-mono); font-size: 14px; color: var(--text-pri); text-align: right; }
.td-num.muted { color: var(--text-sec); font-size: 13px; }
.no-data { text-align: center; color: var(--text-muted); padding: 40px; font-size: 13px; }

/* ── 分页 ── */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 12px;
  border-top: 1px solid var(--border);
}
.page-btn {
  width: 32px;
  height: 32px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 3px;
  color: var(--text-sec);
  font-size: 16px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.page-btn:hover:not(:disabled) { border-color: var(--border-glow); color: var(--accent); }
.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.page-info {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-sec);
  min-width: 60px;
  text-align: center;
}
</style>
