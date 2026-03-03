/**
 * useWebSocket.js
 *
 * 统一管理 WebSocket 连接。
 * - 当后端（Java Spring Boot）就绪后，把 MOCK_MODE 改为 false 即可切换到真实数据。
 * - 真实数据协议：后端推送 JSON，格式见下方 parseMessage()。
 */

import { ref, onUnmounted } from 'vue'
import { useMonitorStore } from '@/stores/monitor'

// ─── 切换这里来启用/禁用 Mock ───
const MOCK_MODE = false
// WebSocket 地址（对应 vite.config.js 里的代理）
const WS_URL = 'ws://localhost:8080/ws/monitor'

export function useWebSocket() {
  const store  = useMonitorStore()
  const connected = ref(false)
  let   ws        = null
  let   mockTimer = null
  let   tearCycle = 0

  // ── 启动 ──
  function connect() {
    if (MOCK_MODE) {
      startMock()
    } else {
      openWS()
    }
  }

  // ── 关闭 ──
  function disconnect() {
    if (ws)        { ws.close(); ws = null }
    if (mockTimer) { clearInterval(mockTimer); mockTimer = null }
    connected.value = false
  }

  // ════════════════════════════
  //  真实 WebSocket
  // ════════════════════════════
  function openWS() {
    ws = new WebSocket(WS_URL)

    ws.onopen = () => {
      connected.value = true
      console.log('[WS] connected')
    }

    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data)
        parseMessage(msg)
      } catch (err) {
        console.warn('[WS] parse error', err)
      }
    }

    ws.onclose = () => {
      connected.value = false
      console.log('[WS] closed, retry in 3s')
      setTimeout(openWS, 3000)   // 断线自动重连
    }

    ws.onerror = (e) => console.error('[WS] error', e)
  }

  /**
   * 解析后端推送的消息。
   * 后端需要推送以下两种类型：
   *
   * 类型 1 — 实时指标
   * {
   *   "type": "metrics",
   *   "load": 1842,
   *   "speed": 2.48,
   *   "crossArea": 1.84,
   *   "heapHeight": 0.42,
   *   "shiftTon": 28450,
   *   "dayTon": 142680,
   *   "pcFps": 24,
   *   "pcPoints": 48246
   * }
   *
   * 类型 2 — 检测结果
   * {
   *   "type": "detection",
   *   "status": "normal",   // "normal" | "warning" | "alarm"
   *   "results": [
   *     { "id": "normal", "label": "皮带正常", "conf": 98.7 },
   *     { "id": "tear",   "label": "纵向撕裂", "conf": 0.3  },
   *     ...
   *   ]
   * }
   *
   * 类型 3 — 报警推送
   * {
   *   "type": "alarm",
   *   "level": "danger",
   *   "label": "紧急",
   *   "msg":   "检测到皮带纵向撕裂",
   *   "src":   "CAM-01"
   * }
   */
  function parseMessage(msg) {
    switch (msg.type) {
      case 'metrics':
        store.updateMetrics(msg)
        break
      case 'detection':
        store.updateDetection(msg.results, msg.status)
        if (msg.status === 'alarm') {
          store.pushAlarm({
            level: 'danger',
            label: '紧急',
            msg: msg.results.find(r => r.conf > 80)?.label + ' 检测告警',
            time: new Date().toTimeString().slice(0, 8),
            src: 'CAM-01'
          })
        }
        break
      case 'alarm':
        store.pushAlarm({
          ...msg,
          time: new Date().toTimeString().slice(0, 8)
        })
        break
    }
  }

  // ════════════════════════════
  //  Mock 数据（对接后端前使用）
  // ════════════════════════════
  function startMock() {
    connected.value = true
    let loadBase = 1842
    let rts      = store.metrics.runtimeSec

    mockTimer = setInterval(() => {
      tearCycle++

      // 随机波动载量
      loadBase += (Math.random() - 0.5) * 70
      loadBase  = Math.max(1300, Math.min(2200, loadBase))
      const load = Math.round(loadBase)

      rts++
      store.updateMetrics({
        load,
        speed:      parseFloat((2.28 + Math.random() * 0.35).toFixed(2)),
        crossArea:  parseFloat((1.52 + Math.random() * 0.52).toFixed(2)),
        heapHeight: parseFloat((0.30 + Math.random() * 0.18).toFixed(2)),
        shiftTon:   store.metrics.shiftTon + load / 3600,
        dayTon:     store.metrics.dayTon   + load / 3600,
        avgLoad:    Math.round(1660 + Math.random() * 130),
        runtimeSec: rts,
        pcFps:      20 + Math.floor(Math.random() * 6),
        pcPoints:   43000 + Math.floor(Math.random() * 7000),
        inferMs:    13 + Math.floor(Math.random() * 8),
      })

      // 每 55 秒模拟一次撕裂事件，持续 20 秒
      if (tearCycle === 55) _triggerTear()
      if (tearCycle === 75) _clearTear()
      if (tearCycle > 75)   tearCycle = 0

    }, 1000)
  }

  function _triggerTear() {
    store.updateDetection([
      { id: 'normal', label: '皮带正常', conf: 4.2,  color: 'var(--text-muted)' },
      { id: 'tear',   label: '纵向撕裂', conf: 91.8, color: 'var(--danger)'     },
      { id: 'hole',   label: '穿孔缺陷', conf: 2.1,  color: 'var(--danger)'     },
      { id: 'edge',   label: '边缘破损', conf: 1.9,  color: 'var(--warn)'       },
    ], 'alarm')

    store.pushAlarm({
      level: 'danger',
      label: '紧急',
      msg:   '底面检测到纵向撕裂，置信度 91.8%，请立即处置',
      time:  new Date().toTimeString().slice(0, 8),
      src:   'CAM-01',
    })
  }

  function _clearTear() {
    store.updateDetection([
      { id: 'normal', label: '皮带正常', conf: 98.7, color: 'var(--accent2)' },
      { id: 'tear',   label: '纵向撕裂', conf: 0.3,  color: 'var(--danger)' },
      { id: 'hole',   label: '穿孔缺陷', conf: 0.1,  color: 'var(--danger)' },
      { id: 'edge',   label: '边缘破损', conf: 0.9,  color: 'var(--warn)'   },
    ], 'normal')
  }

  onUnmounted(disconnect)

  return { connected, connect, disconnect }
}
