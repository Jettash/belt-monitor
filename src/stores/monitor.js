import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'

export const useMonitorStore = defineStore('monitor', () => {

  // ── 皮带运行状态 ──
  const beltRunning = ref(true)

  // ── 实时指标 ──
  const metrics = reactive({
    load:       1842,   // 瞬时载量 t/h
    speed:      2.48,   // 皮带速度 m/s
    crossArea:  1.84,   // 截面积 m²
    heapHeight: 0.42,   // 堆料高度 m
    shiftTon:   28450,  // 本班运量 t
    dayTon:     142680, // 今日运量 t
    avgLoad:    1756,   // 均值载量 t/h
    runtimeSec: 24138,  // 运行时长 s
    pcFps:      24,
    pcPoints:   48246,
    inferMs:    18,
  })

  // ── 检测结果 ──
  const detection = reactive({
    status: 'normal',   // 'normal' | 'warning' | 'alarm'
    results: [
      { id: 'normal', label: '皮带正常', conf: 98.7, color: 'var(--accent2)' },
      { id: 'tear',   label: '纵向撕裂', conf: 0.3,  color: 'var(--danger)' },
      { id: 'hole',   label: '穿孔缺陷', conf: 0.1,  color: 'var(--danger)' },
      { id: 'edge',   label: '边缘破损', conf: 0.9,  color: 'var(--warn)'   },
    ]
  })

  // ── 报警日志 ──
  const alarms = ref([
    { id: 1, level: 'warn',   label: '警告', msg: '瞬时载量超额定上限 2200 t/h',     time: '09:55:18', src: 'LiDAR-01', unread: true  },
    { id: 2, level: 'info',   label: '信息', msg: '皮带速度波动 ±0.12 m/s，已记录',  time: '09:31:05', src: 'PLC',      unread: false },
    { id: 3, level: 'ok',     label: '恢复', msg: '载量恢复正常范围',                time: '08:47:22', src: 'LiDAR-01', unread: false },
    { id: 4, level: 'info',   label: '信息', msg: '系统初始化完成，全部传感器在线',  time: '08:00:01', src: 'SYSTEM',   unread: false },
  ])

  const unreadCount = computed(() => alarms.value.filter(a => a.unread).length)

  // ── 传感器在线状态 ──
  const sensors = reactive({
    lidar:  true,
    camera: true,
    plc:    true,
    server: true,
  })

  // ── 载量历史（用于折线图）──
  const loadHistory = ref(
    Array.from({ length: 60 }, () => 1600 + Math.random() * 500)
  )

  // ── Actions ──

  /** 追加一条报警，id 自增 */
  function pushAlarm(alarm) {
    const id = Date.now()
    alarms.value.unshift({ id, ...alarm, unread: true })
    // 最多保留 100 条
    if (alarms.value.length > 100) alarms.value.pop()
  }

  /** 标记所有报警为已读 */
  function markAllRead() {
    alarms.value.forEach(a => (a.unread = false))
  }

  /** 更新实时指标（供 WebSocket / mock 调用）*/
  function updateMetrics(data) {
    Object.assign(metrics, data)
    loadHistory.value.push(data.load ?? metrics.load)
    if (loadHistory.value.length > 60) loadHistory.value.shift()
  }

  /** 更新检测结果 */
  function updateDetection(results, status) {
    detection.results = results
    detection.status  = status
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
    updateDetection,
  }
})
