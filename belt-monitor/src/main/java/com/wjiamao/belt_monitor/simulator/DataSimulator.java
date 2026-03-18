package com.wjiamao.belt_monitor.simulator;

import com.wjiamao.belt_monitor.model.LoadRecord;
import com.wjiamao.belt_monitor.model.SpeedRecord;
import com.wjiamao.belt_monitor.repository.LoadRecordRepository;
import com.wjiamao.belt_monitor.repository.SpeedRecordRepository;
import com.wjiamao.belt_monitor.websocket.MonitorWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 每秒推送一次实时指标，每 10 分钟写入一条载量记录。
 *
 * 数据来源优先级：
 *   速度   → HGL-150 Modbus（不可用时降级为模拟）
 *   截面积 → SICK LMS 雷达（不可用时降级为模拟）
 *   载量   → area × speed × COAL_DENSITY × 3600；皮带停止时强制为 0
 *   点云、相机推理等 → 始终为模拟（前端展示用）
 */
@Slf4j
@Component
@EnableScheduling
public class DataSimulator {

    // ── 物理常数 ───────────────────────────────────────────
    /** 煤炭堆积密度 t/m³（与雷达服务保持一致，现场标定后调整） */
    private static final double COAL_DENSITY      = 0.92;
    /** 速度低于此值视为皮带停止，载量强制归零 */
    private static final double SPEED_STOP_THRESH = 0.1;
    /** 载量上限（过滤明显异常帧） */
    private static final double LOAD_MAX          = 2500.0;

    @Autowired private LoadRecordRepository  loadRecordRepository;
    @Autowired private SpeedRecordRepository speedRecordRepository;
    @Autowired private ModbusSpeedService    modbusSpeedService;
    @Autowired private LidarState            lidarState;

    private final Random             random        = new Random();
    private final DateTimeFormatter  timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── 运行时状态 ─────────────────────────────────────────
    private double shiftTon   = 0;
    private double dayTon     = 0;
    private double runtimeSec = 0;
    private int    tearCycle  = 0;

    private double lastSavedShiftTon = 0;

    // ── 模拟参数（点云展示用，与真实数据无关） ────────────
    private double simLoadPhase = 0;

    /**
     * 每 1000ms 执行一次，推送实时指标
     */
    @Scheduled(fixedRate = 1000)
    public void pushMetrics() {

        // ── 1. 速度（真实 / 降级模拟）─────────────────────
        double rawSpeed = modbusSpeedService.readSpeed();
        boolean speedReal = !Double.isNaN(rawSpeed) && rawSpeed >= 0;
        double speed = speedReal
                ? rawSpeed
                : 2.28 + random.nextDouble() * 0.35;

        speedRecordRepository.save(new SpeedRecord(LocalDateTime.now(), speed));

        // ── 2. 皮带运行判断 ────────────────────────────────
        boolean beltRunning = speed >= SPEED_STOP_THRESH;

        // ── 3. 截面积（真实 / 降级模拟）───────────────────
        double area, heapHeight;
        boolean lidarReal = lidarState.isAvailable();
        if (lidarReal) {
            area       = lidarState.getCrossArea();
            heapHeight = lidarState.getHeapHeight();
        } else {
            // 模拟截面积：用正弦波让波形好看一些
            simLoadPhase += 0.10;
            double lf  = 0.62 + Math.sin(simLoadPhase) * 0.25 + (random.nextDouble() - 0.5) * 0.06;
            lf         = Math.max(0.35, Math.min(0.92, lf));
            area       = 0.04 + lf * 0.10;            // 约 0.04~0.13 m²
            heapHeight = 0.05 + lf * 0.20;
        }

        // ── 4. 载量计算 ────────────────────────────────────
        double instantLoad;
        if (!beltRunning) {
            // 皮带停止：一切归零，不累计吨数
            instantLoad = 0.0;
            area        = 0.0;
            heapHeight  = 0.0;
        } else {
            instantLoad = area * speed * COAL_DENSITY * 3600.0;
            instantLoad = Math.max(0, Math.min(LOAD_MAX, instantLoad));
        }

        // ── 5. 吨数累计（仅皮带运行时） ────────────────────
        if (beltRunning) {
            shiftTon += instantLoad / 3600.0;
            dayTon   += instantLoad / 3600.0;
        }
        runtimeSec++;

        // ── 6. 模拟点云参数（前端展示，不影响载量） ────────
        int    pcFps    = 20 + random.nextInt(6);
        int    pcPoints = beltRunning ? 43000 + random.nextInt(7000) : 5000 + random.nextInt(2000);
        int    inferMs  = 13 + random.nextInt(8);
        int    avgLoad  = (int) Math.max(0, instantLoad * (0.95 + random.nextDouble() * 0.10));

        // ── 7. 推送 WebSocket ──────────────────────────────
        String json = String.format("""
                {
                  "type":       "metrics",
                  "load":       %.0f,
                  "speed":      %.2f,
                  "crossArea":  %.4f,
                  "heapHeight": %.3f,
                  "shiftTon":   %.0f,
                  "dayTon":     %.0f,
                  "avgLoad":    %d,
                  "runtimeSec": %.0f,
                  "pcFps":      %d,
                  "pcPoints":   %d,
                  "inferMs":    %d
                }""",
                instantLoad, speed, area, heapHeight,
                shiftTon, dayTon, avgLoad, runtimeSec,
                pcFps, pcPoints, inferMs
        );

        MonitorWebSocketHandler.broadcast(json);

        if (log.isDebugEnabled()) {
            log.debug("speed={:.2f} area={:.4f} load={:.0f} real=[spd={} lidar={}]",
                    speed, area, instantLoad, speedReal, lidarReal);
        }
    }

    /**
     * 每55秒模拟一次撕裂事件（相机接入前的演示）
     */
    @Scheduled(fixedRate = 1000)
    public void pushDetection() {
        tearCycle++;

        String status, results;
        if (tearCycle >= 55 && tearCycle < 75) {
            status = "alarm";
            results = """
                    [
                      {"id":"normal","label":"皮带正常","conf":4.2,  "color":"var(--text-muted)"},
                      {"id":"tear",  "label":"纵向撕裂","conf":91.8, "color":"var(--danger)"},
                      {"id":"hole",  "label":"穿孔缺陷","conf":2.1,  "color":"var(--danger)"},
                      {"id":"edge",  "label":"边缘破损","conf":1.9,  "color":"var(--warn)"}
                    ]""";
        } else {
            if (tearCycle >= 75) tearCycle = 0;
            status = "normal";
            results = """
                    [
                      {"id":"normal","label":"皮带正常","conf":98.7, "color":"var(--accent2)"},
                      {"id":"tear",  "label":"纵向撕裂","conf":0.3,  "color":"var(--danger)"},
                      {"id":"hole",  "label":"穿孔缺陷","conf":0.1,  "color":"var(--danger)"},
                      {"id":"edge",  "label":"边缘破损","conf":0.9,  "color":"var(--warn)"}
                    ]""";
        }

        String time = LocalTime.now().format(timeFormatter);
        MonitorWebSocketHandler.broadcast(String.format("""
                {
                  "type":   "detection",
                  "status": "%s",
                  "time":   "%s",
                  "results": %s
                }""", status, time, results));

        if (tearCycle == 55) {
            MonitorWebSocketHandler.broadcast(String.format("""
                    {
                      "type":  "alarm",
                      "level": "danger",
                      "label": "紧急",
                      "msg":   "底面检测到纵向撕裂，置信度 91.8%%，请立即处置",
                      "src":   "CAM-01",
                      "time":  "%s"
                    }""", time));
        }
    }

    /**
     * 每 10 分钟将载量数据写入数据库
     */
    @Scheduled(fixedRate = 600_000, initialDelay = 600_000)
    public void saveLoadRecord() {
        double periodTon = shiftTon - lastSavedShiftTon;
        lastSavedShiftTon = shiftTon;

        loadRecordRepository.save(new LoadRecord(
                LocalDateTime.now(),
                Math.max(0, periodTon),
                shiftTon,
                dayTon
        ));
        log.info("[DB] 载量写入 period={:.1f}t shift={:.0f}t day={:.0f}t",
                periodTon, shiftTon, dayTon);
    }
}
