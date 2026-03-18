# 皮带运输智能监测系统

基于 Vue 3 + Spring Boot 的煤矿皮带运输实时监测平台，接入 HGL-150 皮带测速仪（Modbus RTU）和 SICK LMS 激光雷达，实现皮带速度、截面积、瞬时载量的实时采集与展示。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端框架 | Vue 3 (Composition API) + Vite 5 |
| 状态管理 | Pinia |
| 三维渲染 | Three.js |
| 图表 | ECharts |
| 后端框架 | Spring Boot 3.5 + WebSocket + Spring Data JPA |
| 数据库 | H2（文件模式，`belt-monitor/data/`） |
| 传感器服务 | Python 3（SICK LMS 雷达采集） |
| 硬件接入 | HGL-150 测速仪（Modbus RTU）、SICK LMS 雷达（TCP） |

---

## 目录结构

```
belt-monitor/
├── belt-monitor/                        # Spring Boot 后端
│   ├── src/main/java/com/wjiamao/belt_monitor/
│   │   ├── simulator/
│   │   │   ├── DataSimulator.java       # 核心调度器，每秒推 WebSocket
│   │   │   ├── ModbusSpeedService.java  # HGL-150 速度读取（Modbus RTU）
│   │   │   └── LidarState.java          # 雷达数据共享容器
│   │   ├── controller/
│   │   │   ├── LidarController.java     # POST /api/lidar/frame
│   │   │   ├── SpeedController.java     # GET  /api/speed/records
│   │   │   └── ReportController.java    # GET  /api/report/records
│   │   ├── websocket/
│   │   │   ├── MonitorWebSocketHandler.java
│   │   │   └── WebSocketConfig.java
│   │   ├── model/
│   │   │   ├── LoadRecord.java          # 载量记录（每 10 分钟）
│   │   │   └── SpeedRecord.java         # 速度记录（每秒）
│   │   └── repository/
│   │       ├── LoadRecordRepository.java
│   │       └── SpeedRecordRepository.java
│   └── src/main/resources/
│       └── application.properties       # 端口、数据库、Modbus 参数
│
├── src/                                 # Vue 前端
│   ├── stores/monitor.js                # Pinia 全局状态（含停机迟滞逻辑）
│   ├── composables/useWebSocket.js      # WebSocket 连接管理
│   ├── views/
│   │   ├── DashboardView.vue            # 主监控页
│   │   └── ReportView.vue               # 历史报表页
│   └── components/
│       ├── TitleBar.vue                 # 顶栏（速度、运量）
│       ├── PointCloudPanel.vue          # 3D 点云（模拟展示，速度响应式）
│       ├── CameraPanel.vue              # 相机画面 + 撕裂检测框
│       ├── DetectSidebar.vue            # AI 检测结果 + 运行参数
│       └── StatusBar.vue                # 底部传感器状态
│
└── lidar_service.py                     # Python 雷达采集服务
```

---

## 快速启动

### 1. 后端

```bash
cd belt-monitor
mvn spring-boot:run
# 服务启动在 http://localhost:8080
```

### 2. 前端

```bash
npm install
npm run dev
# 浏览器打开 http://localhost:5173
```

### 3. 雷达采集服务（可选）

```bash
pip install requests
python lidar_service.py
```

---

## 传感器配置

### HGL-150 皮带测速仪（`application.properties`）

```properties
modbus.enabled=true
modbus.port=COM3          # 实际串口号
modbus.baudrate=19200
modbus.slave-id=1
modbus.register=1
```

> 将 `modbus.enabled=false` 可关闭真实读取，降级为模拟数据。

### SICK LMS 雷达（`lidar_service.py`）

```python
LIDAR_IP       = "169.254.175.235"
LIDAR_PORT     = 2111
INSTALL_HEIGHT = 1500.0   # mm，雷达到空皮带面的垂直距离，现场确认后修改
BELT_WIDTH     = 1250.0   # mm
```

> 雷达服务未运行时，后端自动降级为模拟截面积（不影响速度真实性）。

---

## 数据流

```
HGL-150 ──Modbus/COM3──▶ ModbusSpeedService
                               │
SICK LMS ──TCP──▶ lidar_service.py
                  (截面积计算)
                  POST /api/lidar/frame
                               │
                         DataSimulator（每秒）
                         ├─ load = area × speed × 0.92 × 3600
                         ├─ speed < 0.1 m/s → load = 0，停止累计吨数
                         ├─ SpeedRecord → H2（每秒）
                         ├─ LoadRecord  → H2（每 10 分钟）
                         └─ WebSocket 广播 → 前端
```

---

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/speed/status` | 测速仪连接状态与当前速度 |
| GET | `/api/speed/records?date=YYYY-MM-DD` | 速度历史记录（每秒一条） |
| GET | `/api/lidar/status` | 雷达数据连接状态 |
| POST | `/api/lidar/frame` | 雷达截面数据推送（Python 调用） |
| GET | `/api/report/records?date=YYYY-MM-DD` | 载量历史报表（每 10 分钟一条） |

---

## WebSocket 消息格式

WebSocket 地址：`ws://localhost:8080/ws/monitor`

### 实时指标（每秒）

```json
{
  "type":       "metrics",
  "load":       1842,
  "speed":      3.84,
  "crossArea":  0.0842,
  "heapHeight": 0.187,
  "shiftTon":   1250,
  "dayTon":     8640,
  "avgLoad":    1780,
  "runtimeSec": 3600,
  "pcFps":      24,
  "pcPoints":   46800,
  "inferMs":    17
}
```

### 检测结果（每秒）

```json
{
  "type":   "detection",
  "status": "normal",
  "time":   "14:23:05",
  "results": [
    { "id": "normal", "label": "皮带正常", "conf": 98.7, "color": "var(--accent2)" },
    { "id": "tear",   "label": "纵向撕裂", "conf": 0.3,  "color": "var(--danger)"  }
  ]
}
```

### 报警（事件触发）

```json
{
  "type":  "alarm",
  "level": "danger",
  "label": "紧急",
  "msg":   "底面检测到纵向撕裂，置信度 91.8%，请立即处置",
  "src":   "CAM-01",
  "time":  "14:23:05"
}
```

---

## 已知问题 / 待完成

- [ ] **空载标定**：雷达基准线当前使用理论抛物线，需现场空载运行后生成 `calibration.json`
- [ ] **启动时恢复今日吨数**：重启后 shiftTon / dayTon 从 0 开始，未从数据库恢复
- [ ] **班次清零**：shiftTon 未按 8 小时班次自动重置
- [ ] **煤炭密度校准**：当前 0.92 t/m³ 为通用值，建议用地磅比对后调整
- [ ] **相机接入**：撕裂检测当前为模拟，待 YOLOv8 部署后替换
