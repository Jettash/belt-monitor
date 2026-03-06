import { ref, onUnmounted } from 'vue'
import { useMonitorStore } from '@/stores/monitor'

const MOCK_MODE = false
const WS_URL = 'ws://localhost:8080/ws/monitor'

export function useWebSocket() {
  const store = useMonitorStore()
  const connected = ref(false)

  let ws = null
  let mockTimer = null
  let tearCycle = 0

  function connect() {
    if (MOCK_MODE) startMock()
    else openWS()
  }

  function disconnect() {
    if (ws) {
      ws.close()
      ws = null
    }
    if (mockTimer) {
      clearInterval(mockTimer)
      mockTimer = null
    }
    connected.value = false
  }

  function openWS() {
    ws = new WebSocket(WS_URL)

    ws.onopen = () => {
      connected.value = true
      console.log('[WS] connected')
    }

    ws.onmessage = (e) => {
      try {
        parseMessage(JSON.parse(e.data))
      } catch (err) {
        console.warn('[WS] parse error', err)
      }
    }

    ws.onclose = () => {
      connected.value = false
      console.log('[WS] closed, retry in 3s')
      setTimeout(openWS, 3000)
    }

    ws.onerror = (e) => console.error('[WS] error', e)
  }

  function parseMessage(msg) {
    switch (msg.type) {
      case 'metrics':
        store.updateMetrics(msg)
        break
      case 'detection':
        store.updateDetection(msg.results, 'normal')
        // Commented out by request: keep warning/alarm UI hidden.
        // if (msg.status === 'alarm') {
        //   store.pushAlarm({
        //     level: 'danger',
        //     label: '紧急',
        //     msg: msg.results.find((r) => r.conf > 80)?.label + ' 检测告警',
        //     time: new Date().toTimeString().slice(0, 8),
        //     src: 'CAM-01'
        //   })
        // }
        break
      case 'alarm':
        // Commented out by request: keep warning/alarm UI hidden.
        // store.pushAlarm({
        //   ...msg,
        //   time: new Date().toTimeString().slice(0, 8)
        // })
        break
      default:
        break
    }
  }

  function startMock() {
    connected.value = true
    let loadBase = 1842
    let runtimeSec = store.metrics.runtimeSec

    mockTimer = setInterval(() => {
      tearCycle++

      loadBase += (Math.random() - 0.5) * 70
      loadBase = Math.max(1300, Math.min(2200, loadBase))
      const load = Math.round(loadBase)

      runtimeSec++
      store.updateMetrics({
        load,
        speed: parseFloat((3.8 + Math.random() * 0.4).toFixed(2)),
        crossArea: parseFloat((1.52 + Math.random() * 0.52).toFixed(2)),
        heapHeight: parseFloat((0.30 + Math.random() * 0.18).toFixed(2)),
        shiftTon: store.metrics.shiftTon + load / 3600,
        dayTon: store.metrics.dayTon + load / 3600,
        avgLoad: Math.round(1660 + Math.random() * 130),
        runtimeSec,
        pcFps: 20 + Math.floor(Math.random() * 6),
        pcPoints: 43000 + Math.floor(Math.random() * 7000),
        inferMs: 13 + Math.floor(Math.random() * 8)
      })

      // Commented out by request: disable mock alarm/warning cycle.
      // if (tearCycle === 55) triggerTear()
      // if (tearCycle === 75) clearTear()
      // if (tearCycle > 75) tearCycle = 0
      if (tearCycle > 75) tearCycle = 0
    }, 1000)
  }

  function triggerTear() {
    store.updateDetection(
      [
        { id: 'normal', label: '皮带正常', conf: 4.2, color: 'var(--text-muted)' },
        { id: 'tear', label: '纵向撕裂', conf: 91.8, color: 'var(--danger)' },
        { id: 'hole', label: '穿孔缺陷', conf: 2.1, color: 'var(--danger)' },
        { id: 'edge', label: '边缘破损', conf: 1.9, color: 'var(--warn)' }
      ],
      'alarm'
    )
  }

  function clearTear() {
    store.updateDetection(
      [
        { id: 'normal', label: '皮带正常', conf: 98.7, color: 'var(--accent2)' },
        { id: 'tear', label: '纵向撕裂', conf: 0.3, color: 'var(--danger)' },
        { id: 'hole', label: '穿孔缺陷', conf: 0.1, color: 'var(--danger)' },
        { id: 'edge', label: '边缘破损', conf: 0.9, color: 'var(--warn)' }
      ],
      'normal'
    )
  }

  onUnmounted(disconnect)

  return { connected, connect, disconnect }
}
