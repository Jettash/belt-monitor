<template>
  <header class="titlebar">
    <div class="tb-brand">
      <div class="logo-icon">AI</div>
      <div>
        <div class="logo-title">BELT MONITOR</div>
        <div class="logo-sub">魏家峁煤矿 · 运输皮带智能监控</div>
      </div>
    </div>

    <div class="tb-divider"></div>

    <nav class="tb-nav">
      <span
        v-for="tab in tabs"
        :key="tab"
        class="tb-tab"
        :class="{ active: activeTab === tab }"
        @click="activeTab = tab"
      >{{ tab }}</span>
    </nav>

    <div class="tb-metrics">
      <div class="tb-metric">
        <span class="metric-label">瞬时载量</span>
        <span class="metric-value" :class="loadClass">
          {{ fmtLoad(store.metrics.load) }}<em> t/h</em>
        </span>
      </div>
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

      <!-- Commented out by request: hide warning/alarm detection status UI. -->
      <!--
      <div class="tb-metric">
        <span class="metric-label">检测状态</span>
        <span class="metric-value" :class="detClass">{{ detLabel }}</span>
      </div>
      -->
    </div>

    <div class="tb-right">
      <div class="belt-status" :class="store.beltRunning ? 'running' : 'stopped'">
        <span class="status-dot"></span>
        {{ store.beltRunning ? '皮带运行中' : '皮带停机' }}
      </div>

      <!-- Commented out by request: hide warning bell UI. -->
      <!--
      <div class="alarm-bell" :class="{ ringing: store.unreadCount > 0 }" @click="emit('openAlarms')">
        🔔
        <span v-if="store.unreadCount > 0" class="bell-badge">{{ store.unreadCount }}</span>
      </div>
      -->

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
import { useMonitorStore } from '@/stores/monitor'

const store = useMonitorStore()
const emit = defineEmits(['openAlarms'])

const tabs = ['实时监控', '载量报表', '报警管理', '历史回放', '设备状态']
const activeTab = ref('实时监控')
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

const loadClass = computed(() => (store.metrics.load > 2000 ? 'warn' : ''))

const detClass = computed(() => {
  if (store.detection.status === 'alarm') return 'danger'
  if (store.detection.status === 'warning') return 'warn'
  return 'ok'
})

const detLabel = computed(() => {
  if (store.detection.status === 'alarm') return '撕裂告警'
  if (store.detection.status === 'warning') return '检测警告'
  return '正常'
})

function fmtLoad(v) {
  return Math.round(v).toLocaleString()
}
function fmtTon(v) {
  return Math.round(v).toLocaleString()
}
</script>

<style scoped>
.titlebar {
  height: 56px;
  background: var(--bg-panel);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  flex-shrink: 0;
}

.tb-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.logo-icon {
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, #1a4a7a, #0090cc);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}
.logo-title {
  font-family: var(--font-head);
  font-size: 16px;
  font-weight: 700;
  color: var(--accent);
  letter-spacing: 1px;
}
.logo-sub {
  font-size: 10px;
  color: var(--text-muted);
  letter-spacing: 1.5px;
}

.tb-divider {
  width: 1px;
  height: 28px;
  background: var(--border);
  flex-shrink: 0;
}

.tb-nav {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}
.tb-tab {
  padding: 5px 13px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-sec);
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid transparent;
  transition: all 0.15s;
  white-space: nowrap;
}
.tb-tab:hover {
  color: var(--text-pri);
  background: var(--bg-hover);
}
.tb-tab.active {
  color: var(--accent);
  border-color: var(--border-glow);
  background: rgba(0, 212, 255, 0.07);
}

.tb-metrics {
  display: flex;
  gap: 0;
  margin-left: 8px;
}
.tb-metric {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 14px;
  border-left: 1px solid var(--sep, #0d2035);
  min-width: 90px;
}
.metric-label {
  font-size: 10px;
  color: var(--text-muted);
  letter-spacing: 1px;
  margin-bottom: 1px;
}
.metric-value {
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-pri);
  line-height: 1;
}
.metric-value em {
  font-style: normal;
  font-size: 10px;
  color: var(--text-sec);
  margin-left: 2px;
}
.metric-value.warn {
  color: var(--warn);
}
.metric-value.danger {
  color: var(--danger);
  animation: blink 0.6s step-end infinite;
}
.metric-value.ok {
  color: var(--accent2);
}

.tb-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.belt-status {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px 12px;
  border-radius: 20px;
  border: 1px solid;
  font-size: 12px;
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
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.6s ease-in-out infinite;
}

.alarm-bell {
  position: relative;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 15px;
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text-sec);
  transition: all 0.15s;
}
.alarm-bell:hover {
  border-color: var(--warn);
  color: var(--warn);
}
.alarm-bell.ringing {
  border-color: rgba(255, 59, 92, 0.4);
  color: var(--danger);
  animation: blink 0.8s step-end infinite;
}
.bell-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: var(--danger);
  color: #fff;
  font-size: 9px;
  font-family: var(--font-mono);
  display: flex;
  align-items: center;
  justify-content: center;
}

.sensor-leds {
  display: flex;
  gap: 6px;
}
.led-item {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.08em;
  padding: 3px 7px;
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
  font-size: 13px;
  font-weight: 600;
  color: var(--accent);
  letter-spacing: 1.5px;
  padding: 0 6px;
}
</style>
