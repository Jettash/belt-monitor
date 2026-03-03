<template>
  <div class="panel">
    <div class="panel-head">
      <div class="ph-left">
        <span class="ph-id">CAM-01</span>
        <span class="ph-sep">|</span>
        <span class="ph-name">皮带底面图像 · 撕裂检测</span>
      </div>
      <div class="ph-right">
        <span class="ph-chip">回程段 K0+080</span>
        <span class="ph-chip" :class="statusChipClass">{{ statusLabel }}</span>
      </div>
    </div>

    <div class="cam-wrap" ref="wrapRef">

      <!-- 真实相机流：后端返回 MJPEG，img 标签直接播放 -->
      <!-- 相机到货后只需要换后端代码，这里完全不动 -->
      <img
        class="cam-stream"
        :src="streamUrl"
        @error="onStreamError"
        @load="onStreamLoad"
        v-show="streamOk"
      />

      <!-- 相机离线时显示的模拟画面 -->
      <canvas
        ref="canvasRef"
        class="cam-fallback"
        v-show="!streamOk"
      ></canvas>

      <!-- 检测框叠加层（始终在最上面，后续接算法结果在这里画框） -->
      <canvas ref="overlayRef" class="cam-overlay"></canvas>

      <!-- 状态指示灯 -->
      <div class="cam-indicator">
        <div class="ind-led" :class="isAlarm ? 'alarm' : 'normal'"></div>
        <span class="ind-text">皮带状态：{{ statusLabel }}</span>
      </div>

      <!-- 角落标线 -->
      <div class="bracket tl"></div>
      <div class="bracket tr"></div>
      <div class="bracket bl"></div>
      <div class="bracket br"></div>

      <!-- 底部信息 -->
      <div class="cam-footer">● REC &nbsp; CAM-01 &nbsp; {{ timeStr }}</div>

      <!-- 推理时间 -->
      <div class="cam-infer">YOLOv8 · {{ store.metrics.inferMs }}ms</div>

      <!-- 相机未连接提示 -->
      <div class="cam-offline-tip" v-if="!streamOk">
        <span class="offline-dot"></span>
        相机未连接 · 模拟画面
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useMonitorStore } from '@/stores/monitor'

const store      = useMonitorStore()
const wrapRef    = ref(null)
const canvasRef  = ref(null)   // 离线模拟画面
const overlayRef = ref(null)   // 检测框叠加层
const timeStr    = ref('--:--:--')
const streamOk   = ref(false)  // 相机流是否正常

// ── 后端 MJPEG 流地址 ──
// 相机到货后，只需修改后端 CameraStreamController.java 里的 generateFrame() 方法
// 前端这里完全不需要改

const streamUrl = 'http://localhost:8080/api/camera/stream'


let fallbackCtx, overlayCtx
let animId, cf = 0
let tearActive = false, tearProgress = 0
let resizeObserver

// ── 计算属性 ──
const isAlarm = computed(() => store.detection.status === 'alarm')

const statusLabel = computed(() => {
  if (store.detection.status === 'alarm')   return '撕裂告警'
  if (store.detection.status === 'warning') return '检测警告'
  return '正常'
})

const statusChipClass = computed(() => {
  if (store.detection.status === 'alarm')   return 'danger'
  if (store.detection.status === 'warning') return 'warn'
  return 'ok'
})

// ── 相机流事件 ──
function onStreamLoad()  { streamOk.value = true  }
function onStreamError() { streamOk.value = false }

// ── 监听检测状态，控制检测框叠加层 ──
watch(() => store.detection.status, (val) => {
  if (val === 'alarm') {
    tearActive   = true
    tearProgress = 0
  } else {
    tearActive   = false
    tearProgress = 0
    if (overlayCtx) {
      const cv = overlayRef.value
      overlayCtx.clearRect(0, 0, cv.width, cv.height)
    }
  }
})

// ── Canvas 尺寸同步 ──
function resizeCanvas() {
  const wrap = wrapRef.value
  if (!wrap) return
  const w = wrap.clientWidth, h = wrap.clientHeight
  if (canvasRef.value)  { canvasRef.value.width  = w; canvasRef.value.height  = h }
  if (overlayRef.value) { overlayRef.value.width = w; overlayRef.value.height = h }
}

// ════════════════════════════════════════
// 离线模拟画面（相机未连接时显示）
// ════════════════════════════════════════
function drawFallback() {
  animId = requestAnimationFrame(drawFallback)
  cf++

  // 更新时间戳
  const n = new Date()
  timeStr.value = [n.getHours(), n.getMinutes(), n.getSeconds()]
    .map(v => String(v).padStart(2, '0')).join(':')

  // 相机流正常时只画检测框，不画模拟背景
  if (streamOk.value) {
    drawOverlay()
    return
  }

  const cv = canvasRef.value
  if (!cv || !fallbackCtx) return
  const W = cv.width, H = cv.height
  fallbackCtx.clearRect(0, 0, W, H)

  // 皮带背景
  const bg = fallbackCtx.createLinearGradient(0, 0, 0, H)
  bg.addColorStop(0,   '#0e1009')
  bg.addColorStop(0.5, '#161c0b')
  bg.addColorStop(1,   '#0a0c05')
  fallbackCtx.fillStyle = bg
  fallbackCtx.fillRect(0, 0, W, H)

  // 运动条纹（模拟皮带移动）
  const SPD = 2.6, SH = 42, off = (cf * SPD) % SH
  fallbackCtx.strokeStyle = 'rgba(55,50,25,.16)'
  fallbackCtx.lineWidth   = 1
  for (let y = -SH + off; y < H + SH; y += SH) {
    fallbackCtx.beginPath()
    fallbackCtx.moveTo(0, y)
    fallbackCtx.lineTo(W, y + 5)
    fallbackCtx.stroke()
  }

  // 晕影
  const vig = fallbackCtx.createRadialGradient(W/2, H/2, H*0.15, W/2, H/2, H*0.75)
  vig.addColorStop(0, 'transparent')
  vig.addColorStop(1, 'rgba(0,0,0,.55)')
  fallbackCtx.fillStyle = vig
  fallbackCtx.fillRect(0, 0, W, H)

  // 煤屑颗粒
  for (let i = 0; i < 20; i++) {
    const px = Math.sin(i * 93 + cf * 0.007) * W * 0.38 + W / 2
    const py = ((i * 69 + cf * SPD * 0.78) % (H + 10)) - 5
    const r  = 0.6 + Math.abs(Math.sin(i * 31)) * 1.6
    fallbackCtx.beginPath()
    fallbackCtx.arc(px, py, r, 0, Math.PI * 2)
    fallbackCtx.fillStyle = `rgba(8,7,2,${0.2 + Math.abs(Math.sin(i * 0.6)) * 0.28})`
    fallbackCtx.fill()
  }

  // 中心虚线
  fallbackCtx.strokeStyle = 'rgba(0,212,255,.08)'
  fallbackCtx.lineWidth   = 1
  fallbackCtx.setLineDash([5, 4])
  fallbackCtx.beginPath()
  fallbackCtx.moveTo(W / 2, 0)
  fallbackCtx.lineTo(W / 2, H)
  fallbackCtx.stroke()
  fallbackCtx.setLineDash([])

  // 扫描线
  const sy = (cf * 1.1) % (H + 6)
  const sg = fallbackCtx.createLinearGradient(0, sy - 4, 0, sy + 4)
  sg.addColorStop(0,   'transparent')
  sg.addColorStop(0.5, 'rgba(0,212,255,.05)')
  sg.addColorStop(1,   'transparent')
  fallbackCtx.fillStyle = sg
  fallbackCtx.fillRect(0, sy - 4, W, 8)

  drawOverlay()
}

// ════════════════════════════════════════
// 检测框叠加层
// 后续接入 YOLOv8 真实结果后：
// 把 tearActive/tearProgress 替换成后端推过来的 BBox 坐标即可
// ════════════════════════════════════════
function drawOverlay() {
  const cv = overlayRef.value
  if (!cv || !overlayCtx) return
  const W = cv.width, H = cv.height
  overlayCtx.clearRect(0, 0, W, H)

  if (!tearActive) return

  tearProgress = Math.min(1, tearProgress + 0.01)
  const tx   = W * 0.47
  const tLen = tearProgress * (H - 20)

  // 撕裂裂缝线
  overlayCtx.strokeStyle = `rgba(255,59,92,${0.7 + Math.sin(cf * 0.3) * 0.12})`
  overlayCtx.lineWidth   = 1.5
  overlayCtx.shadowColor = 'rgba(255,59,92,.3)'
  overlayCtx.shadowBlur  = 8
  overlayCtx.beginPath()
  overlayCtx.moveTo(tx, 12)
  for (let y = 12; y < 12 + tLen; y += 2) {
    overlayCtx.lineTo(tx + Math.sin(y * 0.5) * 2.2, y)
  }
  overlayCtx.stroke()
  overlayCtx.shadowBlur = 0

  if (tearProgress > 0.2) {
    const bx = tx - 26, by = 8, bw = 56, bh = Math.min(tLen + 14, H - 18)

    // 检测框
    overlayCtx.strokeStyle = `rgba(255,59,92,${0.5 + Math.sin(cf * 0.2) * 0.15})`
    overlayCtx.lineWidth   = 1
    overlayCtx.strokeRect(bx, by, bw, bh)

    // 四角标记
    overlayCtx.strokeStyle = 'rgba(255,59,92,.85)'
    overlayCtx.lineWidth   = 1.2
    for (const [ex, ey] of [[bx, by], [bx+bw, by], [bx, by+bh], [bx+bw, by+bh]]) {
      const sx = ex === bx ? 1 : -1
      const sy = ey === by ? 1 : -1
      overlayCtx.beginPath()
      overlayCtx.moveTo(ex, ey + sy * 7)
      overlayCtx.lineTo(ex, ey)
      overlayCtx.lineTo(ex + sx * 7, ey)
      overlayCtx.stroke()
    }

    // 置信度标签
    const conf = Math.round(88 + tearProgress * 9)
    overlayCtx.fillStyle = 'rgba(255,59,92,.8)'
    overlayCtx.fillRect(bx, by - 14, 62, 13)
    overlayCtx.fillStyle = '#fff'
    overlayCtx.font = `500 9px 'Share Tech Mono', monospace`
    overlayCtx.fillText(`TEAR  ${conf}%`, bx + 4, by - 4)
  }
}

// ── 生命周期 ──
onMounted(async () => {
  await nextTick()
  await new Promise(r => setTimeout(r, 100))

  fallbackCtx = canvasRef.value.getContext('2d')
  overlayCtx  = overlayRef.value.getContext('2d')

  resizeCanvas()
  resizeObserver = new ResizeObserver(resizeCanvas)
  resizeObserver.observe(wrapRef.value)

  drawFallback()
})

onUnmounted(() => {
  cancelAnimationFrame(animId)
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.panel { display: flex; flex-direction: column; overflow: hidden; }

.panel-head {
  height: 36px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 12px;
  background: var(--bg-panel); border-bottom: 1px solid var(--border);
}
.ph-left  { display: flex; align-items: center; gap: 8px; }
.ph-id    { font-family: var(--font-mono); font-size: 10px; color: var(--text-muted); letter-spacing: .1em; }
.ph-sep   { color: var(--border); }
.ph-name  { font-size: 12px; font-weight: 600; color: var(--text-pri); }
.ph-right { display: flex; gap: 6px; }
.ph-chip {
  font-family: var(--font-mono); font-size: 10px; color: var(--text-muted);
  background: var(--bg-deep, #060c14); border: 1px solid var(--border);
  padding: 2px 7px; border-radius: 2px;
}
.ph-chip.ok     { color: var(--accent2); border-color: rgba(0,255,157,.25); background: rgba(0,255,157,.05); }
.ph-chip.warn   { color: var(--warn);    border-color: rgba(255,170,0,.3);  background: rgba(255,170,0,.05); }
.ph-chip.danger { color: var(--danger);  border-color: rgba(255,59,92,.4);  background: rgba(255,59,92,.06);
  animation: blink 0.6s step-end infinite; }

/* 画面容器 */
.cam-wrap { flex: 1; position: relative; overflow: hidden; background: #0a0c05; }

/* 真实相机流 */
.cam-stream {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  object-fit: cover; display: block;
  z-index: 1;
}

/* 离线模拟画面 */
.cam-fallback {
  position: absolute; inset: 0;
  width: 100% !important; height: 100% !important;
  display: block; z-index: 1;
}
.cam-fallback.hidden { display: none; }

/* 检测框叠加层（始终最上层） */
.cam-overlay {
  position: absolute; inset: 0;
  width: 100% !important; height: 100% !important;
  pointer-events: none; z-index: 3;
}

/* 状态指示灯 */
.cam-indicator {
  position: absolute; top: 10px; left: 10px;
  display: flex; align-items: center; gap: 7px;
  background: rgba(6,12,20,.85); border: 1px solid var(--border);
  padding: 5px 10px; z-index: 4;
}
.ind-led { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.ind-led.normal { background: var(--accent2); border: 1px solid rgba(0,255,157,.4); }
.ind-led.alarm  { background: var(--danger);  border: 1px solid rgba(255,59,92,.5);
  animation: pulse 0.4s ease-in-out infinite; }
.ind-text { font-family: var(--font-mono); font-size: 9px; color: var(--text-sec); letter-spacing: .06em; }

/* 角落标线 */
.bracket {
  position: absolute; width: 18px; height: 18px;
  border-color: rgba(0,212,255,.25); border-style: solid;
  pointer-events: none; z-index: 4;
}
.bracket.tl { top: 8px;    left: 8px;   border-width: 1px 0 0 1px; }
.bracket.tr { top: 8px;    right: 8px;  border-width: 1px 1px 0 0; }
.bracket.bl { bottom: 8px; left: 8px;   border-width: 0 0 1px 1px; }
.bracket.br { bottom: 8px; right: 8px;  border-width: 0 1px 1px 0; }

.cam-footer {
  position: absolute; bottom: 8px; left: 10px;
  font-family: var(--font-mono); font-size: 9px; color: rgba(200,210,220,.28);
  letter-spacing: .06em; pointer-events: none; z-index: 4;
}
.cam-infer {
  position: absolute; bottom: 8px; right: 10px;
  font-family: var(--font-mono); font-size: 9px; color: rgba(0,212,255,.3);
  pointer-events: none; z-index: 4;
}

/* 离线提示 */
.cam-offline-tip {
  position: absolute; top: 10px; right: 10px;
  display: flex; align-items: center; gap: 6px;
  font-family: var(--font-mono); font-size: 9px; color: var(--warn);
  background: rgba(6,12,20,.85); border: 1px solid rgba(255,170,0,.3);
  padding: 4px 10px; z-index: 4;
}
.offline-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--warn);
  animation: pulse 1.2s ease-in-out infinite;
}
</style>