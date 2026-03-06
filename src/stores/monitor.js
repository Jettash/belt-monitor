import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'

function clamp(val, min, max) {
  return Math.min(max, Math.max(min, val))
}

export const useMonitorStore = defineStore('monitor', () => {
  const beltRunning = ref(true)

  const metrics = reactive({
    load: 1842,
    speed: 4.0,
    crossArea: 1.84,
    heapHeight: 0.42,
    shiftTon: 28450,
    dayTon: 142680,
    avgLoad: 1756,
    runtimeSec: 24138,
    pcFps: 24,
    pcPoints: 48246,
    inferMs: 18
  })

  const detection = reactive({
    status: 'normal',
    results: [
      { id: 'normal', label: '皮带正常', conf: 98.9, color: 'var(--accent2)' },
      { id: 'tear', label: '纵向撕裂', conf: 0.4, color: 'var(--danger)' },
      { id: 'hole', label: '穿孔缺陷', conf: 0.2, color: 'var(--danger)' },
      { id: 'edge', label: '边缘磨损', conf: 0.5, color: 'var(--warn)' }
    ]
  })

  // Keep only informational log entries so warning UI does not appear.
  const alarms = ref([
    { id: 1, level: 'info', label: '信息', msg: '检测模式：演示', time: '09:55:18', src: 'CAM-01', unread: false },
    { id: 2, level: 'ok', label: '状态', msg: '皮带底面检测稳定运行中', time: '09:31:05', src: 'SYSTEM', unread: false }
  ])

  const unreadCount = computed(() => alarms.value.filter((a) => a.unread).length)

  const sensors = reactive({
    lidar: true,
    camera: true,
    plc: true,
    server: true
  })

  const loadHistory = ref(
    Array.from({ length: 60 }, () => 1600 + Math.random() * 500)
  )

  let speedPhase = 0
  function nextSpeed() {
    speedPhase += 0.27
    const wave = 4.0 + Math.sin(speedPhase) * 0.16
    const jitter = (Math.random() - 0.5) * 0.06
    return parseFloat(clamp(wave + jitter, 3.8, 4.2).toFixed(2))
  }

  function pushAlarm(alarm) {
    const id = Date.now()
    alarms.value.unshift({ id, ...alarm, unread: false, level: 'info' })
    if (alarms.value.length > 100) alarms.value.pop()
  }

  function markAllRead() {
    alarms.value.forEach((a) => {
      a.unread = false
    })
  }

  function updateMetrics(data) {
    Object.assign(metrics, data)
    metrics.speed = nextSpeed()

    loadHistory.value.push(data.load ?? metrics.load)
    if (loadHistory.value.length > 60) loadHistory.value.shift()
  }

  // Keep detection in demo-safe mode and suppress warning/alarm status.
  function updateDetection(results) {
    const nextResults = Array.isArray(results) ? results : detection.results
    detection.status = 'normal'
    detection.results = nextResults.map((item) => {
      if (item.id === 'normal') {
        return { ...item, conf: clamp(item.conf ?? 98, 95, 99.9), color: 'var(--accent2)' }
      }
      return { ...item, conf: clamp(item.conf ?? 0.3, 0, 4.9) }
    })
  }

  return {
    beltRunning,
    metrics,
    detection,
    alarms,
    unreadCount,
    sensors,
    loadHistory,
    pushAlarm,
    markAllRead,
    updateMetrics,
    updateDetection
  }
})
