<template>
  <header class="titlebar">
    <div class="tb-brand">
      <div class="logo-icon">AI</div>
      <div>
        <div class="logo-title">BELT MONITOR</div>
        <div class="logo-sub">北方魏家峁煤电有限责任公司</div>
      </div>
    </div>

    <div class="tb-divider"></div>

    <nav class="tb-nav">
      <span
        v-for="tab in tabs"
        :key="tab.label"
        class="tb-tab"
        :class="{ active: activeTab === tab.label, disabled: !tab.route }"
        @click="onTabClick(tab)"
      >{{ tab.label }}</span>
    </nav>

    <div class="tb-metrics">
      <div class="tb-metric">
        <span class="metric-label">皮带速度</span>
        <span class="metric-value">{{ store.metrics.speed.toFixed(2) }}<em> m/s</em></span>
      </div>
      <div class="tb-metric">
        <span class="metric-label">截面积</span>
        <span class="metric-value">{{ store.metrics.crossArea.toFixed(2) }}<em> m²</em></span>
      </div>
      <div class="tb-metric">
        <span class="metric-label">本班运量</span>
        <span class="metric-value">{{ fmtTon(store.metrics.shiftTon) }}<em> t</em></span>
      </div>
      <div class="tb-metric">
        <span class="metric-label">今日累计</span>
        <span class="metric-value">{{ fmtTon(store.metrics.dayTon) }}<em> t</em></span>
      </div>
    </div>

    <div class="tb-right">
      <div class="belt-status" :class="store.beltRunning ? 'running' : 'stopped'">
        <span class="status-dot"></span>
        {{ store.beltRunning ? '皮带运行中' : '皮带停机' }}
      </div>

      <div class="sensor-leds">
        <span class="led-item" :class="store.sensors.lidar ? 'on' : 'off'" title="三维雷达">LiDAR</span>
        <span class="led-item" :class="store.sensors.camera ? 'on' : 'off'" title="底部相机">CAM</span>
        <span class="led-item" :class="store.sensors.plc ? 'on' : 'off'" title="PLC">PLC</span>
      </div>

      <div class="tb-clock">
        <span>{{ timeStr }}</span>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMonitorStore } from '@/stores/monitor'

const store  = useMonitorStore()
const router = useRouter()
const route  = useRoute()

defineEmits(['openAlarms'])

// tab 定义：有 route 的才能点击跳转
const tabs = [
  { label: '实时监控', route: '/dashboard' },
  { label: '载量报表', route: '/report'    },
  { label: '报警管理', route: null          },
  { label: '历史回放', route: null          },
  { label: '设备状态', route: null          },
]

// 根据当前路由自动高亮对应 tab
const activeTab = computed(() => {
  const matched = tabs.find(t => t.route && route.path.startsWith(t.route))
  return matched ? matched.label : '实时监控'
})

function onTabClick(tab) {
  if (tab.route) router.push(tab.route)
}

const timeStr = ref('--:--:--')
let timer = null
onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})
onUnmounted(() => clearInterval(timer))

function updateTime() {
  const n = new Date()
  timeStr.value = [n.getHours(), n.getMinutes(), n.getSeconds()]
    .map((v) => String(v).padStart(2, '0'))
    .join(':')
}

function fmtTon(v) {
  return Math.round(v).toLocaleString()
}
</script>

<style scoped>
.titlebar {
  height: 68px;
  background: var(--bg-panel);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 2px 24px rgba(0, 180, 255, 0.07);
  display: flex;
  align-items: center;
  padding: 0 22px;
  gap: 16px;
  flex-shrink: 0;
}

.tb-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.logo-icon {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, #1a4a7a, #0090cc);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.logo-title {
  font-family: var(--font-head);
  font-size: 20px;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: 1px;
  line-height: 1.2;
}
.logo-sub {
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.5px;
  margin-top: 2px;
}

.tb-divider {
  width: 1px;
  height: 32px;
  background: var(--border);
  flex-shrink: 0;
}

.tb-nav {
  display: flex;
  gap: 3px;
  flex-shrink: 0;
}
.tb-tab {
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-sec);
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid transparent;
  transition: all 0.15s;
  white-space: nowrap;
  user-select: none;
}
.tb-tab:hover:not(.disabled) {
  color: var(--text-pri);
  background: var(--bg-hover);
}
.tb-tab.active {
  color: var(--accent);
  border-color: var(--border-glow);
  background: rgba(0, 212, 255, 0.07);
}
.tb-tab.disabled {
  cursor: default;
  opacity: 0.4;
}

.tb-metrics {
  display: flex;
  gap: 0;
  margin-left: 10px;
}
.tb-metric {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 18px;
  border-left: 1px solid var(--sep, #0d2035);
  min-width: 108px;
}
.metric-label {
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 1px;
  margin-bottom: 2px;
}
.metric-value {
  font-family: var(--font-mono);
  font-size: 19px;
  font-weight: 600;
  color: var(--text-pri);
  line-height: 1;
}
.metric-value em {
  font-style: normal;
  font-size: 12px;
  color: var(--text-sec);
  margin-left: 2px;
}

.tb-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.belt-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  border: 1px solid;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.belt-status.running {
  color: var(--accent2);
  border-color: rgba(0, 255, 157, 0.35);
  background: rgba(0, 255, 157, 0.06);
}
.belt-status.stopped {
  color: var(--warn);
  border-color: rgba(255, 170, 0, 0.35);
  background: rgba(255, 170, 0, 0.06);
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.6s ease-in-out infinite;
}

.sensor-leds {
  display: flex;
  gap: 7px;
}
.led-item {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  padding: 4px 9px;
  border: 1px solid var(--border);
  border-radius: 3px;
  color: var(--text-muted);
}
.led-item.on {
  color: var(--accent2);
  border-color: rgba(0, 255, 157, 0.3);
  background: rgba(0, 255, 157, 0.05);
}
.led-item.off {
  color: var(--danger);
  border-color: rgba(255, 59, 92, 0.3);
}

.tb-clock {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--accent);
  letter-spacing: 2px;
  padding: 0 8px;
}
</style>
