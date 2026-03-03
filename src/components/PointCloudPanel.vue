<template>
  <div class="panel">
    <!-- 头部 -->
    <div class="panel-head">
      <div class="ph-left">
        <span class="ph-id">CH-01</span>
        <span class="ph-sep">|</span>
        <span class="ph-name">三维点云 · 皮带截面</span>
      </div>
      <div class="ph-right">
        <span class="ph-chip">{{ store.metrics.pcFps }} FPS</span>
        <span class="ph-chip">{{ fmtPts(store.metrics.pcPoints) }} pts</span>
        <span class="ph-chip ok">在线</span>
      </div>
    </div>

    <!-- 点云画布 -->
    <div class="pc-wrap" ref="wrapRef">
      <canvas ref="canvasRef"></canvas>

      <!-- 视图切换按钮 -->
      <div class="view-ctrl">
        <button
          v-for="v in views" :key="v.key"
          class="vbtn"
          :class="{ active: currentView === v.key }"
          @click="setView(v.key)"
        >{{ v.label }}</button>
      </div>

      <!-- 高度图例 -->
      <div class="pc-legend">
        <span class="leg-label">高</span>
        <div class="leg-bar"></div>
        <span class="leg-label">低</span>
      </div>

      <!-- 叠加信息 -->
      <div class="pc-stats">
        <div class="pcs-item">
          <span class="pcs-k">堆料高度</span>
          <span class="pcs-v">{{ store.metrics.heapHeight.toFixed(2) }}</span>
          <span class="pcs-u">m</span>
        </div>
        <div class="pcs-item">
          <span class="pcs-k">截面积</span>
          <span class="pcs-v">{{ store.metrics.crossArea.toFixed(2) }}</span>
          <span class="pcs-u">m²</span>
        </div>
        <div class="pcs-item">
          <span class="pcs-k">采样时刻</span>
          <span class="pcs-v">{{ timeStr }}</span>
        </div>
      </div>

      <!-- 标尺 -->
      <div class="pc-ruler">
        <div class="ruler-inner">
          <span>−1.4m</span><span>−0.7</span><span>0</span><span>+0.7</span><span>+1.4m</span>
        </div>
      </div>

      <!-- 扫描线 -->
      <div class="scan-line"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'
import { useMonitorStore } from '@/stores/monitor'

const store    = useMonitorStore()
const wrapRef  = ref(null)
const canvasRef = ref(null)
const currentView = ref('3d')
const timeStr  = ref('--:--:--')

const views = [
  { key: '3d',  label: '3D'  },
  { key: 'top', label: '俯视' },
  { key: 'sec', label: '截面' },
]

let renderer, scene, camera, pcMesh, animId, resizeObserver
let drag = false, mx = 0, my = 0, ry = 0.25, rx = 0.2
let frame = 0

function fmtPts(n) {
  return Math.round(n).toLocaleString()
}

// ── Three.js 初始化 ──
function initThree() {
  const canvas = canvasRef.value
  const wrap   = wrapRef.value

  renderer = new THREE.WebGLRenderer({ canvas, antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setClearColor(0x060c14)

  scene  = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0x060c14, 0.035)

  camera = new THREE.PerspectiveCamera(50, 1, 0.1, 300)
  camera.position.set(0, 9, 18)
  camera.lookAt(0, 0, 0)

  // 网格
  const grid = new THREE.GridHelper(30, 30, 0x1a3a5c, 0x0d2035)
  grid.position.y = -2
  scene.add(grid)

  // 皮带平面
  const beltMesh = new THREE.Mesh(
    new THREE.PlaneGeometry(28, 2.8),
    new THREE.MeshBasicMaterial({ color: 0x0a1628, side: THREE.DoubleSide })
  )
  beltMesh.rotation.x = -Math.PI / 2
  beltMesh.position.y = -1.995
  scene.add(beltMesh)

  // 皮带边缘线
  const edgeMat = new THREE.LineBasicMaterial({ color: 0x1a3a5c })
  for (const z of [-1.4, 1.4]) {
    const geo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-14, -2, z),
      new THREE.Vector3(14, -2, z)
    ])
    scene.add(new THREE.Line(geo, edgeMat))
  }

  // 截面轮廓线
  buildSectionLine()
  scene.add(new THREE.AmbientLight(0xffffff, 0.5))

  buildPointCloud()
  resize()

  // ResizeObserver 自适应容器尺寸
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(wrap)

  // 鼠标交互
  canvas.addEventListener('mousedown', onMouseDown)
  window.addEventListener('mouseup',   onMouseUp)
  window.addEventListener('mousemove', onMouseMove)

  animate()
}

function buildSectionLine() {
  const pts = []
  for (let i = 0; i <= 50; i++) {
    const x  = (i / 50 - 0.5) * 2.8
    const nx = x / 1.4
    const h  = Math.max(-2, Math.pow(1 - nx * nx, 1.1) * 1.05 - 2 + Math.pow(Math.max(0, 1 - nx * nx), 1) * 0.9)
    pts.push(new THREE.Vector3(x, h, 0))
  }
  scene.add(new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(pts),
    new THREE.LineBasicMaterial({ color: 0x00d4ff, transparent: true, opacity: 0.4 })
  ))
}

function buildPointCloud() {
  if (pcMesh) scene.remove(pcMesh)
  const pos = [], col = [], N = 6500
  const c = new THREE.Color()

  for (let i = 0; i < N; i++) {
    const x  = (Math.random() - 0.5) * 2.8
    const z  = (Math.random() - 0.5) * 26
    const nx = x / 1.4
    const bh = Math.max(0, 1 - nx * nx * 1.12)
    const ny = (Math.random() - 0.5) * 0.1
    const y  = -2 + bh * (0.82 + Math.sin(z * 0.22) * 0.13) + ny + (bh > 0 ? bh * 0.85 : 0)
    if (y > -2) {
      pos.push(x, y, z)
      const t = Math.min(1, Math.max(0, (y + 1.6) / 1.9))
      if (t < 0.33)      c.setRGB(0, t * 0.8 + 0.1, 1)
      else if (t < 0.66) c.setRGB(0, 0.85, 1 - (t - 0.33) * 2)
      else               c.setRGB((t - 0.66) * 3, 1 - (t - 0.66) * 2, 0)
      col.push(c.r, c.g, c.b)
    }
  }
  // 皮带底面散点
  for (let i = 0; i < 600; i++) {
    pos.push((Math.random() - 0.5) * 2.8, -2 + Math.random() * 0.01, (Math.random() - 0.5) * 26)
    col.push(0.07, 0.18, 0.3)
  }

  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3))
  geo.setAttribute('color',    new THREE.Float32BufferAttribute(col, 3))
  pcMesh = new THREE.Points(geo, new THREE.PointsMaterial({ size: 0.055, vertexColors: true }))
  scene.add(pcMesh)
}

function animate() {
  animId = requestAnimationFrame(animate)
  frame++

  if (currentView.value === '3d') {
    if (!drag) ry += 0.002
    const R = 18
    camera.position.x = Math.sin(ry) * R * Math.cos(rx * 0.5)
    camera.position.z = Math.cos(ry) * R * Math.cos(rx * 0.5)
    camera.position.y = 6 + rx * 4.5
    camera.lookAt(0, 0, 0)
  }

  // 每 90 帧刷新点云（模拟实时扫描）
  if (frame % 90 === 0) {
    buildPointCloud()
    const n = new Date()
    timeStr.value = [n.getHours(), n.getMinutes(), n.getSeconds()]
      .map(v => String(v).padStart(2, '0')).join(':')
  }

  renderer.render(scene, camera)
}

function resize() {
  const wrap = wrapRef.value
  if (!wrap) return
  const w = wrap.clientWidth, h = wrap.clientHeight
  renderer.setSize(w, h)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
}

function setView(v) {
  currentView.value = v
  if (v === 'top') { camera.position.set(0, 22, 0.01); camera.lookAt(0, 0, 0) }
  else if (v === 'sec') { camera.position.set(10, 0, 0); camera.lookAt(0, 0, 0) }
  else { camera.position.set(0, 9, 18); camera.lookAt(0, 0, 0) }
}

function onMouseDown(e) { drag = true; mx = e.clientX; my = e.clientY }
function onMouseUp()    { drag = false }
function onMouseMove(e) {
  if (!drag) return
  ry += (e.clientX - mx) * 0.008; mx = e.clientX
  rx += (e.clientY - my) * 0.004; my = e.clientY
  rx = Math.max(-0.5, Math.min(1.0, rx))
}

onMounted(initThree)
onUnmounted(() => {
  cancelAnimationFrame(animId)
  resizeObserver?.disconnect()
  canvasRef.value?.removeEventListener('mousedown', onMouseDown)
  window.removeEventListener('mouseup',   onMouseUp)
  window.removeEventListener('mousemove', onMouseMove)
  renderer?.dispose()
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
.ph-chip  {
  font-family: var(--font-mono); font-size: 10px; color: var(--text-muted);
  background: var(--bg-deep, #060c14); border: 1px solid var(--border);
  padding: 2px 7px; border-radius: 2px;
}
.ph-chip.ok { color: var(--accent2); border-color: rgba(0,255,157,.25); background: rgba(0,255,157,.05); }

.pc-wrap {
  flex: 1; position: relative; overflow: hidden;
  background: #070d16;
}
canvas { display: block; width: 100% !important; height: 100% !important; }

/* View buttons */
.view-ctrl { position: absolute; top: 10px; right: 12px; display: flex; gap: 2px; }
.vbtn {
  font-family: var(--font-mono); font-size: 10px; font-weight: 600;
  letter-spacing: .08em; text-transform: uppercase;
  padding: 4px 10px;
  background: rgba(6,12,20,.85); border: 1px solid var(--border);
  color: var(--text-sec); cursor: pointer; border-radius: 2px;
  transition: all .15s;
}
.vbtn:hover { color: var(--text-pri); border-color: var(--border-glow); }
.vbtn.active {
  background: rgba(0,212,255,.1); border-color: var(--accent); color: var(--accent);
}

/* Legend */
.pc-legend {
  position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  pointer-events: none;
}
.leg-label { font-family: var(--font-mono); font-size: 9px; color: var(--text-muted); }
.leg-bar {
  width: 8px; height: 80px;
  background: linear-gradient(to bottom, #ff3b5c, #ffaa00, #00ff9d, #00d4ff);
  border: 1px solid var(--border);
}

/* Stats overlay */
.pc-stats {
  position: absolute; left: 12px; bottom: 28px;
  display: flex; flex-direction: column; gap: 4px;
  pointer-events: none;
}
.pcs-item {
  display: flex; align-items: baseline; gap: 6px;
  background: rgba(6,12,20,.82); border: 1px solid var(--border);
  padding: 3px 8px; border-radius: 2px;
}
.pcs-k { font-family: var(--font-mono); font-size: 9px; color: var(--text-muted); letter-spacing: .06em; min-width: 48px; }
.pcs-v { font-family: var(--font-mono); font-size: 13px; font-weight: 600; color: var(--text-pri); }
.pcs-u { font-family: var(--font-mono); font-size: 9px; color: var(--text-sec); }

/* Ruler */
.pc-ruler {
  position: absolute; bottom: 0; left: 0; right: 0; height: 22px;
  pointer-events: none;
}
.ruler-inner {
  position: absolute; top: 4px; left: 12px; right: 12px;
  border-top: 1px solid rgba(0,212,255,.12);
  display: flex; justify-content: space-between; padding-top: 3px;
}
.ruler-inner span { font-family: var(--font-mono); font-size: 9px; color: rgba(0,212,255,.3); }

/* Scan line */
.scan-line {
  position: absolute; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  opacity: 0.4;
  animation: scanAnim 3s linear infinite;
  pointer-events: none;
}
@keyframes scanAnim {
  0%   { top: 0%;   opacity: 0; }
  5%   { opacity: 0.4; }
  95%  { opacity: 0.4; }
  100% { top: 100%; opacity: 0; }
}
</style>
