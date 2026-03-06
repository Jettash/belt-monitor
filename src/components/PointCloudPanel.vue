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
        <span class="ph-chip">{{ fps }} FPS</span>
        <span class="ph-chip">{{ fmtPts(pointCount) }} pts</span>
        <span class="ph-chip ok">在线</span>
      </div>
    </div>

    <!-- 三视图主体 -->
    <div class="views-layout">

      <!-- 左：主3D视图（占主体） -->
      <div class="view-cell view-main">
        <div class="view-label">
          <span class="vl-dot"></span>3D 透视视图
        </div>
        <div class="pc-wrap" ref="wrap3d">
          <canvas ref="canvas3d"></canvas>
          <div v-if="webglFailed" class="webgl-fallback">
            <p>WebGL 不可用</p>
          </div>
          <!-- 视角控制 -->
          <div class="view-ctrl">
            <button v-for="v in views" :key="v.key"
              class="vbtn" :class="{ active: currentView === v.key }"
              @click="setView3d(v.key)">{{ v.label }}</button>
          </div>
          <!-- 高度图例 -->
          <div class="pc-legend">
            <span class="leg-label">满</span>
            <div class="leg-bar"></div>
            <span class="leg-label">空</span>
          </div>
          <!-- 扫描线 -->
          <div class="scan-line"></div>
        </div>
      </div>

      <!-- 右侧：侧视图 + 截面视图纵向排列 -->
      <div class="view-side-col">

        <!-- 侧视图（右侧上） -->
        <div class="view-cell view-sub">
          <div class="view-label">
            <span class="vl-dot vl-cyan"></span>侧视图 · XZ平面
          </div>
          <div class="pc-wrap" ref="wrapSide">
            <canvas ref="canvasSide"></canvas>
            <div class="scan-line scan-line--slow"></div>
          </div>
        </div>

        <!-- 截面视图（右侧下） -->
        <div class="view-cell view-sub">
          <div class="view-label">
            <span class="vl-dot vl-green"></span>横截面 · YZ平面
          </div>
          <div class="pc-wrap" ref="wrapSec">
            <canvas ref="canvasSec"></canvas>
            <!-- 截面叠加指标 -->
            <div class="sec-overlay">
              <div class="so-row">
                <span class="so-k">截面积</span>
                <span class="so-v">{{ crossArea.toFixed(4) }}</span>
                <span class="so-u">m²</span>
              </div>
              <div class="so-row">
                <span class="so-k">煤面高</span>
                <span class="so-v">{{ coalMaxH.toFixed(3) }}</span>
                <span class="so-u">m</span>
              </div>
            </div>
            <!-- 截面标尺 -->
            <div class="pc-ruler">
              <div class="ruler-inner">
                <span>−0.625</span><span>−0.3</span><span>0</span><span>+0.3</span><span>+0.625</span>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>

    <!-- 底部状态栏 -->
    <div class="status-bar">
      <div class="sb-item">
        <span class="sb-k">瞬时载量</span>
        <span class="sb-v accent-yellow">{{ load.toFixed(0) }}</span>
        <span class="sb-u">t/h</span>
      </div>
      <div class="sb-sep"></div>
      <div class="sb-item">
        <span class="sb-k">煤面最高</span>
        <span class="sb-v">{{ coalMaxH.toFixed(3) }}</span>
        <span class="sb-u">m</span>
      </div>
      <div class="sb-sep"></div>
      <div class="sb-item">
        <span class="sb-k">截面积</span>
        <span class="sb-v">{{ crossArea.toFixed(4) }}</span>
        <span class="sb-u">m²</span>
      </div>
      <div class="sb-sep"></div>
      <div class="sb-item">
        <span class="sb-k">皮带速度</span>
        <span class="sb-v">{{ BELT_SPEED.toFixed(1) }}</span>
        <span class="sb-u">m/s</span>
      </div>
      <div class="sb-sep"></div>
      <div class="sb-item">
        <span class="sb-k">采样时刻</span>
        <span class="sb-v accent-blue">{{ timeStr }}</span>
      </div>
      <div class="sb-spacer"></div>
      <div class="sb-item">
        <span class="sb-k">点数</span>
        <span class="sb-v">{{ fmtPts(pointCount) }}</span>
      </div>
      <div class="sb-sep"></div>
      <div class="sb-item">
        <span class="sb-k">帧率</span>
        <span class="sb-v accent-green">{{ fps }}</span>
        <span class="sb-u">fps</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as THREE from 'three'

// ── refs ──────────────────────────────────────────────────
const wrap3d     = ref(null)
const canvas3d   = ref(null)
const wrapSide   = ref(null)
const canvasSide = ref(null)
const wrapSec    = ref(null)
const canvasSec  = ref(null)

const currentView = ref('3d')
const webglFailed = ref(false)
const timeStr     = ref('--:--:--')
const fps         = ref(60)
const pointCount  = ref(0)
const coalMaxH    = ref(0)
const crossArea   = ref(0)
const load        = ref(0)

const views = [
  { key: '3d',   label: '3D'   },
  { key: 'top',  label: '俯视'  },
  { key: 'iso',  label: '等距'  },
]

// ── 物理参数 ──────────────────────────────────────────────
const BELT_W     = 1.25
const BELT_HALF  = BELT_W / 2
const BELT_DEPTH = 0.25
const BELT_LEN   = 6.0
const BELT_SPEED = 4.0
const COAL_DEN   = 0.92
const MAX_COAL_H = BELT_DEPTH

function beltY(x) {
  const nx = x / BELT_HALF
  return -BELT_DEPTH * (1 - nx * nx)
}

function maxCoalH(x, loadFactor, zPhase) {
  const nx = x / BELT_HALF
  const profile = Math.max(0, 1 - nx * nx * 1.15)
  const wave = 0.75 + 0.25 * Math.sin(zPhase * 1.8) * Math.cos(zPhase * 0.7)
  return MAX_COAL_H * loadFactor * profile * wave
}

// ── Three.js 主3D场景 ─────────────────────────────────────
let renderer3d, scene3d, camera3d
let coalMesh3d, beltMesh3d, sectionLine3d
let animId3d, resizeObs3d
let drag = false, mx = 0, my = 0
let ry = -0.4, rx = 0.85, radius = 5.5
let camCX = 0, camCY = -0.05, camCZ = 0

// ── Three.js 侧视图场景 ───────────────────────────────────
let rendererSide, sceneSide, cameraSide
let coalMeshSide, beltMeshSide
let animIdSide, resizeObsSide

// ── Three.js 截面场景 ─────────────────────────────────────
let rendererSec, sceneSec, cameraSec
let sectionMeshSec, beltLineSec
let animIdSec, resizeObsSec

// FPS
let fpsFrames = 0, fpsLast = performance.now()

// 流动偏移（全局，所有视图共享）
let flowOffset = 0
let loadPhase  = 0
let currentLoadFactor = 0.65

// ── 颜色工具 ─────────────────────────────────────────────
function coalColor(t, c) {
  const stops = [
    [0.00, 0.20, 0.85],
    [0.00, 0.70, 0.90],
    [0.00, 0.90, 0.30],
    [0.85, 0.88, 0.00],
    [1.00, 0.42, 0.00],
    [1.00, 0.05, 0.10],
  ]
  const s = (stops.length - 1) * t
  const i = Math.min(Math.floor(s), stops.length - 2)
  const f = s - i
  const a = stops[i], b = stops[i + 1]
  c.setRGB(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f)
}

// ── 点云数据生成（带流动偏移）─────────────────────────────
// 返回 { positions, colors, area, maxH, count }
function generateCoalData(loadFactor, offset, N_COAL = 9000, N_BELT = 1200, N_NOISE = 300) {
  const positions = []
  const colors    = []
  const c = new THREE.Color()

  // 皮带底面点
  for (let i = 0; i < N_BELT; i++) {
    const x = (Math.random() - 0.5) * BELT_W
    const z = (Math.random() - 0.5) * BELT_LEN
    const y = beltY(x) + Math.random() * 0.008
    positions.push(x, y, z)
    const lum = 0.06 + Math.random() * 0.04
    c.setRGB(lum * 0.6, lum * 0.8, lum * 1.2)
    colors.push(c.r, c.g, c.b)
  }

  // 煤料点（流动：z坐标加上偏移后取模）
  for (let i = 0; i < N_COAL; i++) {
    let x
    let accepted = false
    for (let attempt = 0; attempt < 8; attempt++) {
      const attemptX = (Math.random() - 0.5) * BELT_W
      const nx = attemptX / BELT_HALF
      if (Math.random() < Math.max(0, 1 - nx * nx * 1.2)) {
        x = attemptX; accepted = true; break
      }
    }
    if (!accepted) x = (Math.random() - 0.5) * BELT_W * 0.7

    // 原始z坐标（均匀分布）
    const zRaw = (Math.random() - 0.5) * BELT_LEN
    // 流动偏移：沿-Z方向流动（模拟皮带向某方向运输）
    // 取模使点云循环
    let z = ((zRaw + offset % BELT_LEN + BELT_LEN * 1.5) % BELT_LEN) - BELT_LEN / 2

    const by = beltY(x)
    const zPhase = z * 1.5 + 0.3
    const maxH   = maxCoalH(x, loadFactor, zPhase)
    if (maxH < 0.005) continue

    const t    = Math.random()
    const coalH = maxH * (1 - (1 - t) * (1 - t))
    const scatter = (Math.random() - 0.5) * 0.018
    const y = by + coalH + scatter
    if (y > 0.005 || y < by - 0.005) continue

    positions.push(x, y, z)
    const colorT = Math.min(1, coalH / MAX_COAL_H)
    coalColor(colorT, c)
    const bright = 0.8 + Math.random() * 0.2
    colors.push(c.r * bright, c.g * bright, c.b * bright)
  }

  // 飞散噪点
  for (let i = 0; i < N_NOISE; i++) {
    const x = (Math.random() - 0.5) * BELT_W * 0.9
    const z = (Math.random() - 0.5) * BELT_LEN
    const y = beltY(x) + Math.random() * 0.04
    positions.push(x, y, z)
    const lum = 0.1 + Math.random() * 0.1
    colors.push(lum * 0.5, lum * 0.7, lum)
  }

  // 截面积计算
  const N_SEC = 60
  const secPts = []
  for (let i = 0; i < N_SEC; i++) {
    const x = -BELT_HALF + i * BELT_W / (N_SEC - 1)
    secPts.push(maxCoalH(x, loadFactor, 0.3))
  }
  const dx = BELT_W / (N_SEC - 1)
  let area = 0
  for (let i = 0; i < N_SEC - 1; i++) {
    area += (secPts[i] + secPts[i + 1]) / 2 * dx
  }

  return {
    positions,
    colors,
    area,
    maxH: Math.max(...secPts),
    count: positions.length / 3,
    secPts,
  }
}

// ── 皮带曲面构建工具 ─────────────────────────────────────
function buildBeltGeo() {
  const SEG_X = 40, SEG_Z = 80
  const geo = new THREE.PlaneGeometry(BELT_W, BELT_LEN, SEG_X, SEG_Z)
  const pos = geo.attributes.position
  for (let i = 0; i < pos.count; i++) {
    pos.setY(i, beltY(pos.getX(i)))
  }
  pos.needsUpdate = true
  geo.computeVertexNormals()
  return geo
}

// ════════════════════════════════════════════════════════════
//  主 3D 场景
// ════════════════════════════════════════════════════════════
function init3D() {
  const canvas = canvas3d.value
  const wrap   = wrap3d.value
  try {
    renderer3d = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' })
    renderer3d.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer3d.setClearColor(0x060c14, 1)
  } catch (e) {
    webglFailed.value = true; return
  }

  scene3d = new THREE.Scene()
  scene3d.fog = new THREE.FogExp2(0x060c14, 0.05)

  camera3d = new THREE.PerspectiveCamera(46, 1, 0.05, 100)
  applyCamera3d()

  scene3d.add(new THREE.AmbientLight(0x334466, 2.2))
  const dl = new THREE.DirectionalLight(0x88aaff, 1.4)
  dl.position.set(1, 3, 2)
  scene3d.add(dl)

  const grid = new THREE.GridHelper(10, 40, 0x0d2035, 0x0a1828)
  grid.position.set(0, -0.32, 0)
  scene3d.add(grid)

  // 皮带
  const beltGeo = buildBeltGeo()
  const beltMat = new THREE.MeshPhongMaterial({
    color: 0x1a2e42, side: THREE.DoubleSide,
    transparent: true, opacity: 0.55, shininess: 8,
  })
  beltMesh3d = new THREE.Mesh(beltGeo, beltMat)
  beltMesh3d.rotation.x = -Math.PI / 2
  scene3d.add(beltMesh3d)

  // 边缘线
  const edgeMat = new THREE.LineBasicMaterial({ color: 0x1e4060 })
  for (const side of [-1, 1]) {
    const x = side * BELT_HALF
    const pts = Array.from({ length: 21 }, (_, i) =>
      new THREE.Vector3(x, beltY(x), -BELT_LEN / 2 + i * BELT_LEN / 20))
    scene3d.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), edgeMat))
  }

  // 横向参考线
  const refMat = new THREE.LineBasicMaterial({ color: 0x0d2a40, transparent: true, opacity: 0.5 })
  for (let z = -2.5; z <= 2.5; z += 1.0) {
    const pts = []
    for (let i = 0; i <= 40; i++) {
      const x = -BELT_HALF + i * BELT_W / 40
      pts.push(new THREE.Vector3(x, beltY(x) + 0.001, z))
    }
    scene3d.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), refMat))
  }

  // 截面高亮线
  updateSectionLine3d(0.65, 0)

  // 初始点云
  update3DCloud(0.65, 0)

  // 鼠标控制
  canvas.addEventListener('mousedown', onDown)
  window.addEventListener('mouseup', onUp)
  window.addEventListener('mousemove', onMove)
  canvas.addEventListener('wheel', onWheel, { passive: true })
  canvas.addEventListener('contextmenu', e => e.preventDefault())

  resizeObs3d = new ResizeObserver(onResize3d)
  resizeObs3d.observe(wrap)
  onResize3d()
}

function update3DCloud(loadFactor, offset) {
  if (coalMesh3d) { scene3d.remove(coalMesh3d); coalMesh3d.geometry.dispose() }
  const data = generateCoalData(loadFactor, offset)
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(data.positions, 3))
  geo.setAttribute('color',    new THREE.Float32BufferAttribute(data.colors, 3))
  coalMesh3d = new THREE.Points(geo, new THREE.PointsMaterial({
    size: 0.022, vertexColors: true, sizeAttenuation: true,
    transparent: true, opacity: 0.92,
  }))
  scene3d.add(coalMesh3d)

  // 更新指标
  crossArea.value  = data.area
  coalMaxH.value   = data.maxH
  load.value       = data.area * BELT_SPEED * COAL_DEN * 3600
  pointCount.value = data.count

  return data
}

function updateSectionLine3d(loadFactor, zPhase) {
  if (sectionLine3d) scene3d.remove(sectionLine3d)
  const pts = []
  for (let i = 0; i <= 60; i++) {
    const x = -BELT_HALF + i * BELT_W / 60
    pts.push(new THREE.Vector3(x, beltY(x) + maxCoalH(x, loadFactor, zPhase), 0))
  }
  sectionLine3d = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(pts),
    new THREE.LineBasicMaterial({ color: 0x00ffee, transparent: true, opacity: 0.85 })
  )
  scene3d.add(sectionLine3d)
}

// ════════════════════════════════════════════════════════════
//  侧视图场景（XZ平面，正交，俯视角度看侧面流动）
// ════════════════════════════════════════════════════════════
function initSide() {
  const canvas = canvasSide.value
  const wrap   = wrapSide.value
  try {
    rendererSide = new THREE.WebGLRenderer({ canvas, antialias: true })
    rendererSide.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    rendererSide.setClearColor(0x060c14, 1)
  } catch (e) { return }

  sceneSide = new THREE.Scene()

  // 正交相机，侧视（从X轴正方向看YZ平面）
  // 显示 z: [-3, 3], y: [-0.3, 0.15]
  cameraSide = new THREE.OrthographicCamera(-BELT_LEN / 2, BELT_LEN / 2, 0.25, -0.35, 0.01, 50)
  cameraSide.position.set(5, 0, 0)
  cameraSide.lookAt(0, -0.05, 0)

  sceneSide.add(new THREE.AmbientLight(0x334466, 3))

  // 皮带侧面轮廓（最低点轨迹线）
  const beltLinePts = []
  for (let i = 0; i <= 80; i++) {
    const z = -BELT_LEN / 2 + i * BELT_LEN / 80
    beltLinePts.push(new THREE.Vector3(0, -BELT_DEPTH, z))
  }
  sceneSide.add(new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(beltLinePts),
    new THREE.LineBasicMaterial({ color: 0x1e4060, linewidth: 1 })
  ))

  // 皮带边线（Y=0线）
  const topLinePts = [
    new THREE.Vector3(0, 0, -BELT_LEN / 2),
    new THREE.Vector3(0, 0,  BELT_LEN / 2),
  ]
  sceneSide.add(new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(topLinePts),
    new THREE.LineBasicMaterial({ color: 0x1e4060 })
  ))

  // 网格辅助线
  const gridMat = new THREE.LineBasicMaterial({ color: 0x0d2035, transparent: true, opacity: 0.4 })
  for (let z = -3; z <= 3; z += 1) {
    sceneSide.add(new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(0, -0.35, z),
        new THREE.Vector3(0,  0.25, z),
      ]), gridMat
    ))
  }

  updateSideCloud(0.65, 0)

  resizeObsSide = new ResizeObserver(() => onResizeSide())
  resizeObsSide.observe(wrap)
  onResizeSide()
}

function updateSideCloud(loadFactor, offset) {
  if (coalMeshSide) { sceneSide.remove(coalMeshSide); coalMeshSide.geometry.dispose() }

  // 侧视图：取x=0截面（中心线）沿z方向分布的点
  // 用一个垂直于X轴的投影：每个点的 z 坐标和 y 坐标（煤高）
  const positions = []
  const colors    = []
  const c = new THREE.Color()

  const N = 2500
  for (let i = 0; i < N; i++) {
    const zRaw = (Math.random() - 0.5) * BELT_LEN
    let z = ((zRaw + offset % BELT_LEN + BELT_LEN * 1.5) % BELT_LEN) - BELT_LEN / 2

    // 随机x（但侧视图投影到x=0附近，加轻微散射显示层次）
    const xScatter = (Math.random() - 0.5) * 0.03
    const x = (Math.random() - 0.5) * BELT_W * 0.8

    const zPhase = z * 1.5 + 0.3
    const maxH   = maxCoalH(x, loadFactor, zPhase)
    if (maxH < 0.003) continue

    const by = beltY(x)
    const t  = Math.random()
    const coalH = maxH * (1 - (1 - t) * (1 - t))
    const y = by + coalH + (Math.random() - 0.5) * 0.012

    if (y > 0.01 || y < by - 0.008) continue

    // 侧视：x 轴对应 z，y 轴对应 y（高度）
    positions.push(xScatter, y, z)

    const colorT = Math.min(1, coalH / MAX_COAL_H)
    coalColor(colorT, c)
    const bright = 0.75 + Math.random() * 0.25
    colors.push(c.r * bright, c.g * bright, c.b * bright)
  }

  // 皮带底面（侧视轮廓感）
  for (let i = 0; i < 300; i++) {
    const z = (Math.random() - 0.5) * BELT_LEN
    const y = -BELT_DEPTH + Math.random() * 0.01
    positions.push((Math.random() - 0.5) * 0.02, y, z)
    const lum = 0.05 + Math.random() * 0.03
    colors.push(lum * 0.6, lum * 0.8, lum * 1.2)
  }

  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geo.setAttribute('color',    new THREE.Float32BufferAttribute(colors, 3))
  coalMeshSide = new THREE.Points(geo, new THREE.PointsMaterial({
    size: 0.025, vertexColors: true, sizeAttenuation: true,
    transparent: true, opacity: 0.85,
  }))
  sceneSide.add(coalMeshSide)
}

function onResizeSide() {
  const wrap = wrapSide.value
  if (!wrap || !rendererSide || !cameraSide) return
  const w = wrap.clientWidth, h = wrap.clientHeight
  rendererSide.setSize(w, h)
  // 保持比例
  const asp = w / h
  const halfZ = BELT_LEN / 2
  const halfY = halfZ / asp
  cameraSide.left   = -halfZ
  cameraSide.right  =  halfZ
  cameraSide.top    =  halfY * 0.5
  cameraSide.bottom = -halfY * 0.8
  cameraSide.updateProjectionMatrix()
}

// ════════════════════════════════════════════════════════════
//  截面视图（YX平面，Z=0处的横截面）
// ════════════════════════════════════════════════════════════
function initSec() {
  const canvas = canvasSec.value
  const wrap   = wrapSec.value
  try {
    rendererSec = new THREE.WebGLRenderer({ canvas, antialias: true })
    rendererSec.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    rendererSec.setClearColor(0x060c14, 1)
  } catch (e) { return }

  sceneSec = new THREE.Scene()

  // 正交相机，正面看截面（从Z轴正方向看XY平面）
  cameraSec = new THREE.OrthographicCamera(-BELT_HALF - 0.1, BELT_HALF + 0.1, 0.15, -0.32, 0.01, 50)
  cameraSec.position.set(0, 0, 5)
  cameraSec.lookAt(0, -0.05, 0)

  sceneSec.add(new THREE.AmbientLight(0x334466, 3))

  // 皮带截面轮廓线
  const beltPts = []
  for (let i = 0; i <= 60; i++) {
    const x = -BELT_HALF + i * BELT_W / 60
    beltPts.push(new THREE.Vector3(x, beltY(x), 0))
  }
  beltLineSec = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(beltPts),
    new THREE.LineBasicMaterial({ color: 0x1e4060 })
  )
  sceneSec.add(beltLineSec)

  // 网格
  const gridMat = new THREE.LineBasicMaterial({ color: 0x0d2035, transparent: true, opacity: 0.35 })
  for (const x of [-0.625, -0.3, 0, 0.3, 0.625]) {
    sceneSec.add(new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(x, -0.32, 0),
        new THREE.Vector3(x,  0.15, 0),
      ]), gridMat
    ))
  }

  updateSecCloud(0.65)

  resizeObsSec = new ResizeObserver(() => onResizeSec())
  resizeObsSec.observe(wrap)
  onResizeSec()
}

function updateSecCloud(loadFactor) {
  if (sectionMeshSec) { sceneSec.remove(sectionMeshSec); sectionMeshSec.geometry.dispose() }

  const positions = []
  const colors    = []
  const c = new THREE.Color()

  // 截面点（从多个z附近采样然后投影）
  const N = 2000
  for (let i = 0; i < N; i++) {
    let x
    let accepted = false
    for (let attempt = 0; attempt < 8; attempt++) {
      const ax = (Math.random() - 0.5) * BELT_W
      const nx = ax / BELT_HALF
      if (Math.random() < Math.max(0, 1 - nx * nx * 1.2)) {
        x = ax; accepted = true; break
      }
    }
    if (!accepted) x = (Math.random() - 0.5) * BELT_W * 0.7

    // 从Z=0附近小范围采样（截面感）
    const zSample = (Math.random() - 0.5) * 0.3
    const zPhase = zSample * 1.5 + 0.3
    const maxH   = maxCoalH(x, loadFactor, zPhase)
    if (maxH < 0.005) continue

    const by = beltY(x)
    const t  = Math.random()
    const coalH = maxH * (1 - (1 - t) * (1 - t))
    const y = by + coalH + (Math.random() - 0.5) * 0.01
    if (y > 0.005 || y < by - 0.005) continue

    // 截面视图：x→x, y→y, z散射（视觉层次）
    const zScatter = (Math.random() - 0.5) * 0.05
    positions.push(x, y, zScatter)

    const colorT = Math.min(1, coalH / MAX_COAL_H)
    coalColor(colorT, c)
    const bright = 0.75 + Math.random() * 0.25
    colors.push(c.r * bright, c.g * bright, c.b * bright)
  }

  // 皮带底面点
  for (let i = 0; i < 300; i++) {
    const x = (Math.random() - 0.5) * BELT_W
    const y = beltY(x) + Math.random() * 0.006
    positions.push(x, y, (Math.random() - 0.5) * 0.02)
    const lum = 0.06 + Math.random() * 0.03
    colors.push(lum * 0.6, lum * 0.8, lum * 1.2)
  }

  // 截面轮廓填充感（煤面曲线上方加密点）
  for (let i = 0; i < 400; i++) {
    const x = -BELT_HALF + Math.random() * BELT_W
    const nx = x / BELT_HALF
    const profile = Math.max(0, 1 - nx * nx * 1.15)
    const maxH = MAX_COAL_H * loadFactor * profile * 0.9

    const by = beltY(x)
    const y = by + maxH * (0.96 + Math.random() * 0.06)
    if (y > 0.005) continue

    positions.push(x, y, (Math.random() - 0.5) * 0.02)
    coalColor(0.9 + Math.random() * 0.1, c)
    colors.push(c.r * 0.9, c.g * 0.9, c.b * 0.9)
  }

  // 煤面包络线
  const envPts = []
  for (let i = 0; i <= 80; i++) {
    const x = -BELT_HALF + i * BELT_W / 80
    const nx = x / BELT_HALF
    const profile = Math.max(0, 1 - nx * nx * 1.15)
    const maxH = MAX_COAL_H * loadFactor * profile * 0.9
    envPts.push(new THREE.Vector3(x, beltY(x) + maxH, 0.05))
  }
  // 用额外的线对象
  if (sceneSec._envLine) sceneSec.remove(sceneSec._envLine)
  const envLine = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(envPts),
    new THREE.LineBasicMaterial({ color: 0x00ffee, transparent: true, opacity: 0.75 })
  )
  sceneSec._envLine = envLine
  sceneSec.add(envLine)

  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geo.setAttribute('color',    new THREE.Float32BufferAttribute(colors, 3))
  sectionMeshSec = new THREE.Points(geo, new THREE.PointsMaterial({
    size: 0.018, vertexColors: true, sizeAttenuation: true,
    transparent: true, opacity: 0.9,
  }))
  sceneSec.add(sectionMeshSec)
}

function onResizeSec() {
  const wrap = wrapSec.value
  if (!wrap || !rendererSec || !cameraSec) return
  const w = wrap.clientWidth, h = wrap.clientHeight
  rendererSec.setSize(w, h)
  const asp = w / h
  const halfX = BELT_HALF + 0.12
  const halfY = halfX / asp
  cameraSec.left   = -halfX
  cameraSec.right  =  halfX
  cameraSec.top    =  halfY * 0.55
  cameraSec.bottom = -halfY * 1.1
  cameraSec.updateProjectionMatrix()
}

// ── 统一动画循环 ─────────────────────────────────────────
let lastRebuild = 0
let frame = 0

function animate() {
  animId3d = requestAnimationFrame(animate)
  frame++

  const now = performance.now()
  const dt  = 0.016  // 约60fps

  // 流动偏移（煤随皮带运动）
  flowOffset += BELT_SPEED * dt * 0.1  // 缩放因子调整视觉速度

  // 自转（3D视图）
  if (currentView.value === '3d' && !drag) {
    ry += 0.0012
    applyCamera3d()
  }

  // 每 2s 重建点云（更新载量波动）
  if (now - lastRebuild > 900) {
    loadPhase += 0.10
    const lf = 0.62 + Math.sin(loadPhase) * 0.25 + (Math.random() - 0.5) * 0.06
    currentLoadFactor = Math.max(0.35, Math.min(0.92, lf))

    update3DCloud(currentLoadFactor, flowOffset)
    updateSectionLine3d(currentLoadFactor, loadPhase)
    updateSideCloud(currentLoadFactor, flowOffset)
    updateSecCloud(currentLoadFactor)

    lastRebuild = now
    const n = new Date()
    timeStr.value = [n.getHours(), n.getMinutes(), n.getSeconds()]
      .map(v => String(v).padStart(2, '0')).join(':')
  }

  // FPS
  fpsFrames++
  if (now - fpsLast >= 1000) {
    fps.value = fpsFrames
    fpsFrames = 0
    fpsLast   = now
  }

  renderer3d?.render(scene3d, camera3d)
  rendererSide?.render(sceneSide, cameraSide)
  rendererSec?.render(sceneSec, cameraSec)
}

// ── 相机工具 ─────────────────────────────────────────────
function applyCamera3d() {
  if (!camera3d) return
  camera3d.position.set(
    camCX + radius * Math.sin(rx) * Math.sin(ry),
    camCY + radius * Math.cos(rx),
    camCZ + radius * Math.sin(rx) * Math.cos(ry),
  )
  camera3d.lookAt(camCX, camCY, camCZ)
}

function setView3d(v) {
  currentView.value = v
  if (!camera3d) return
  if (v === '3d')  { ry = -0.4; rx = 0.85; radius = 5.5; camCX = 0; camCY = -0.05; camCZ = 0 }
  if (v === 'top') { rx = 0.05; ry = 0;    radius = 4.0; camCX = 0; camCY = 0;      camCZ = 0 }
  if (v === 'iso') { ry = -0.8; rx = 0.6;  radius = 4.5; camCX = 0; camCY = -0.05; camCZ = 0 }
  applyCamera3d()
}

function onDown(e)  { drag = true; mx = e.clientX; my = e.clientY }
function onUp()     { drag = false }
function onMove(e)  {
  if (!drag) return
  const dx = e.clientX - mx, dy = e.clientY - my
  ry += dx * 0.007
  rx  = Math.max(0.05, Math.min(Math.PI * 0.48, rx - dy * 0.005))
  mx = e.clientX; my = e.clientY
  applyCamera3d()
}
function onWheel(e) {
  radius = Math.max(0.8, Math.min(20, radius + e.deltaY * 0.01))
  applyCamera3d()
}

function onResize3d() {
  const wrap = wrap3d.value
  if (!wrap || !renderer3d || !camera3d) return
  const w = wrap.clientWidth, h = wrap.clientHeight
  renderer3d.setSize(w, h)
  camera3d.aspect = w / h
  camera3d.updateProjectionMatrix()
}

function fmtPts(n) { return Math.round(n).toLocaleString() }

onMounted(() => {
  init3D()
  initSide()
  initSec()
  animate()
})

onUnmounted(() => {
  cancelAnimationFrame(animId3d)
  resizeObs3d?.disconnect()
  resizeObsSide?.disconnect()
  resizeObsSec?.disconnect()
  canvas3d.value?.removeEventListener('mousedown', onDown)
  window.removeEventListener('mouseup', onUp)
  window.removeEventListener('mousemove', onMove)
  renderer3d?.dispose()
  rendererSide?.dispose()
  rendererSec?.dispose()
})
</script>

<style scoped>
/* ═══════════════════════════════════════════════════
   全局面板
═══════════════════════════════════════════════════ */
.panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
  background: #060c14;
  font-family: var(--font-mono, 'JetBrains Mono', 'Fira Mono', monospace);
}

/* ═══════════════════════════════════════════════════
   头部
═══════════════════════════════════════════════════ */
.panel-head {
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  background: #0b1220;
  border-bottom: 1px solid rgba(0, 180, 255, 0.12);
}
.ph-left  { display: flex; align-items: center; gap: 8px; }
.ph-id    { font-size: 10px; color: #3a5a78; letter-spacing: .12em; }
.ph-sep   { color: rgba(0,180,255,0.18); font-size: 12px; }
.ph-name  { font-size: 12px; font-weight: 600; color: #c8dff2; letter-spacing: .04em; }
.ph-right { display: flex; gap: 6px; }
.ph-chip  {
  font-size: 10px;
  color: #4a6a8a;
  background: #060c14;
  border: 1px solid rgba(0,180,255,0.12);
  padding: 2px 8px;
  border-radius: 2px;
  letter-spacing: .05em;
}
.ph-chip.ok { color: #00e676; border-color: rgba(0,230,118,.25); background: rgba(0,230,118,.05); }

/* ═══════════════════════════════════════════════════
   三视图布局
═══════════════════════════════════════════════════ */
.views-layout {
  flex: 1;
  display: grid;
  /* 主视图占 62%，右侧两个子视图各占 38% */
  grid-template-columns: 62fr 38fr;
  gap: 2px;
  overflow: hidden;
  background: rgba(0,180,255,0.05);
  min-height: 0;
}

.view-side-col {
  display: grid;
  grid-template-rows: 1fr 1fr;
  gap: 2px;
  min-height: 0;
}

/* ═══════════════════════════════════════════════════
   视图单元
═══════════════════════════════════════════════════ */
.view-cell {
  display: flex;
  flex-direction: column;
  background: #060c14;
  overflow: hidden;
  min-height: 0;
}

.view-label {
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  background: rgba(11, 18, 32, 0.95);
  border-bottom: 1px solid rgba(0,180,255,0.08);
  font-size: 9px;
  color: #4a6a8a;
  letter-spacing: .12em;
  text-transform: uppercase;
}

.vl-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #0096ff;
  box-shadow: 0 0 5px #0096ff88;
  flex-shrink: 0;
}
.vl-dot.vl-cyan  { background: #00e5ff; box-shadow: 0 0 5px #00e5ff88; }
.vl-dot.vl-green { background: #00e676; box-shadow: 0 0 5px #00e67688; }

.pc-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
  min-height: 0;
}
canvas { display: block; width: 100% !important; height: 100% !important; }

/* ═══════════════════════════════════════════════════
   视角控制按钮（主视图）
═══════════════════════════════════════════════════ */
.view-ctrl {
  position: absolute;
  top: 8px;
  right: 10px;
  display: flex;
  gap: 2px;
}
.vbtn {
  font-family: inherit;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: .1em;
  text-transform: uppercase;
  padding: 3px 9px;
  background: rgba(6,12,20,.9);
  border: 1px solid rgba(0,180,255,0.14);
  color: #4a6a8a;
  cursor: pointer;
  border-radius: 2px;
  transition: all .15s;
}
.vbtn:hover  { color: #c8dff2; border-color: rgba(0,180,255,.3); }
.vbtn.active { background: rgba(0,180,255,.12); border-color: #0096ff; color: #00c8ff; }

/* ═══════════════════════════════════════════════════
   高度图例（主视图右侧）
═══════════════════════════════════════════════════ */
.pc-legend {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  pointer-events: none;
}
.leg-label { font-size: 8px; color: #3a5a78; }
.leg-bar {
  width: 7px;
  height: 80px;
  background: linear-gradient(to bottom, #ff1a1a, #ff8800, #ffee00, #00e676, #00aaff, #0033dd);
  border: 1px solid rgba(0,180,255,.18);
  border-radius: 1px;
}

/* ═══════════════════════════════════════════════════
   截面叠加指标
═══════════════════════════════════════════════════ */
.sec-overlay {
  position: absolute;
  top: 8px;
  left: 8px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  pointer-events: none;
}
.so-row {
  display: flex;
  align-items: baseline;
  gap: 5px;
  background: rgba(6,12,20,.85);
  border: 1px solid rgba(0,180,255,0.1);
  padding: 2px 7px;
  border-radius: 2px;
}
.so-k { font-size: 8px; color: #3a5a78; min-width: 38px; }
.so-v { font-size: 11px; font-weight: 600; color: #c8dff2; }
.so-u { font-size: 8px; color: #5a7a98; }

/* ═══════════════════════════════════════════════════
   标尺（截面视图底部）
═══════════════════════════════════════════════════ */
.pc-ruler {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 20px;
  pointer-events: none;
}
.ruler-inner {
  position: absolute;
  top: 3px;
  left: 8px;
  right: 8px;
  border-top: 1px solid rgba(0,180,255,.1);
  display: flex;
  justify-content: space-between;
  padding-top: 3px;
}
.ruler-inner span { font-size: 8px; color: rgba(0,180,255,.25); }

/* ═══════════════════════════════════════════════════
   扫描线
═══════════════════════════════════════════════════ */
.scan-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0,200,255,.45), transparent);
  animation: scanAnim 3.5s linear infinite;
  pointer-events: none;
}
.scan-line--slow { animation-duration: 5.5s; }

@keyframes scanAnim {
  0%   { top: 0%;   opacity: 0; }
  5%   { opacity: .4; }
  95%  { opacity: .4; }
  100% { top: 100%; opacity: 0; }
}

/* ═══════════════════════════════════════════════════
   WebGL 降级提示
═══════════════════════════════════════════════════ */
.webgl-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3a5a78;
  font-size: 11px;
  letter-spacing: .1em;
}

/* ═══════════════════════════════════════════════════
   底部状态栏
═══════════════════════════════════════════════════ */
.status-bar {
  height: 32px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 14px;
  background: #080f1c;
  border-top: 1px solid rgba(0,180,255,0.1);
  overflow: hidden;
}
.sb-item {
  display: flex;
  align-items: baseline;
  gap: 5px;
  padding: 0 10px;
  flex-shrink: 0;
}
.sb-k { font-size: 8px; color: #3a5a78; letter-spacing: .08em; }
.sb-v { font-size: 12px; font-weight: 700; color: #c8dff2; letter-spacing: .04em; }
.sb-u { font-size: 8px; color: #5a7a98; }
.sb-sep {
  width: 1px;
  height: 16px;
  background: rgba(0,180,255,0.1);
  flex-shrink: 0;
}
.sb-spacer { flex: 1; }
.accent-yellow { color: #ffd040 !important; text-shadow: 0 0 8px rgba(255,200,0,.3); }
.accent-blue   { color: #40c0ff !important; }
.accent-green  { color: #00e676 !important; }
</style>