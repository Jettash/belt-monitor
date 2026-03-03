# 皮带运输智能监测系统 — 前端工程

## 技术栈

| 层级 | 技术 |
|------|------|
| 框架 | Vue 3 (Composition API) |
| 构建 | Vite 5 |
| 状态管理 | Pinia |
| 三维渲染 | Three.js |
| 图表 | ECharts（后续载量曲线使用）|
| 样式 | 原生 CSS + Scoped |

## 目录结构

```
src/
├── assets/
│   └── global.css          # CSS 变量 + 全局样式
├── components/
│   ├── TitleBar.vue        # 顶部导航 + 实时指标条
│   ├── PointCloudPanel.vue # Three.js 三维点云
│   ├── CameraPanel.vue     # 皮带底面相机 + 撕裂检测框
│   ├── DetectSidebar.vue   # AI结果 / 运行参数 / 事件日志
│   └── StatusBar.vue       # 底部传感器状态条
├── stores/
│   └── monitor.js          # Pinia 全局状态
├── composables/
│   └── useWebSocket.js     # WebSocket / Mock 数据管理
├── App.vue                 # 根组件布局
└── main.js
```

## 启动

```bash
npm install
npm run dev
# 浏览器打开 http://localhost:5173
```

## 对接 Java 后端

当后端（Spring Boot）开发完成后：

**第一步**：打开 `src/composables/useWebSocket.js`，将顶部改为：
```js
const MOCK_MODE = false
const WS_URL = 'ws://你的服务器IP:8080/ws/monitor'
```

**第二步**：后端 WebSocket 需要推送以下三种 JSON 格式：

### 1. 实时指标（每秒推送）
```json
{
  "type": "metrics",
  "load": 1842,
  "speed": 2.48,
  "crossArea": 1.84,
  "heapHeight": 0.42,
  "shiftTon": 28450,
  "dayTon": 142680,
  "pcFps": 24,
  "pcPoints": 48246
}
```

### 2. 检测结果（每帧推送）
```json
{
  "type": "detection",
  "status": "normal",
  "results": [
    { "id": "normal", "label": "皮带正常", "conf": 98.7, "color": "var(--accent2)" },
    { "id": "tear",   "label": "纵向撕裂", "conf": 0.3,  "color": "var(--danger)"  }
  ]
}
```

### 3. 报警推送（事件触发时推送）
```json
{
  "type": "alarm",
  "level": "danger",
  "label": "紧急",
  "msg": "底面检测到纵向撕裂，置信度 91.8%",
  "src": "CAM-01"
}
```

## 后续开发计划

- [ ] 载量统计页（ECharts 折线图 + 班次报表）
- [ ] 报警管理页（筛选、确认、导出）
- [ ] 历史回放页（时间轴 + 点云/视频同步）
- [ ] 真实相机流接入（WebRTC / flv.js）
- [ ] 真实点云数据接入（WebSocket 传输 PCD 数据）
