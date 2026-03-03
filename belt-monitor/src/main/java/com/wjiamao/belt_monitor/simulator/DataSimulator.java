package com.wjiamao.belt_monitor.simulator;

import com.wjiamao.belt_monitor.websocket.MonitorWebSocketHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 数据模拟器
 * 每秒生成一次模拟数据并通过 WebSocket 推送给前端
 * 等真实硬件到位后，把这里替换成读取传感器数据的代码即可
 */
@Component
@EnableScheduling
public class DataSimulator {

    private final Random random = new Random();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 状态变量
    private double load       = 1842;   // 瞬时载量 t/h
    private double shiftTon   = 28450;  // 本班运量 t
    private double dayTon     = 142680; // 今日运量 t
    private int    runtimeSec = 24138;  // 运行时长 s
    private int    tearCycle  = 0;      // 撕裂模拟周期计数

    /**
     * 每1000ms执行一次，推送实时指标
     */
    @Scheduled(fixedRate = 1000)
    public void pushMetrics() {
        // 载量随机波动
        load += (random.nextDouble() - 0.5) * 70;
        load  = Math.max(1300, Math.min(2200, load));

        double speed    = 2.28 + random.nextDouble() * 0.35;
        double area     = 1.52 + random.nextDouble() * 0.52;
        double height   = 0.30 + random.nextDouble() * 0.18;
        int    pcFps    = 20 + random.nextInt(6);
        int    pcPoints = 43000 + random.nextInt(7000);
        int    inferMs  = 13 + random.nextInt(8);
        int    avgLoad  = 1660 + random.nextInt(130);

        shiftTon   += load / 3600;
        dayTon     += load / 3600;
        runtimeSec++;

        // 拼装 JSON（不引入额外依赖，手动拼接）
        String json = String.format("""
                {
                  "type": "metrics",
                  "load": %.0f,
                  "speed": %.2f,
                  "crossArea": %.2f,
                  "heapHeight": %.2f,
                  "shiftTon": %.0f,
                  "dayTon": %.0f,
                  "avgLoad": %d,
                  "runtimeSec": %d,
                  "pcFps": %d,
                  "pcPoints": %d,
                  "inferMs": %d
                }""",
                load, speed, area, height,
                shiftTon, dayTon, avgLoad, runtimeSec,
                pcFps, pcPoints, inferMs
        );

        MonitorWebSocketHandler.broadcast(json);
    }

    /**
     * 每55秒模拟一次撕裂事件，持续20秒
     * 等相机接入后，这里替换成调用 YOLOv8 检测结果
     */
    @Scheduled(fixedRate = 1000)
    public void pushDetection() {
        tearCycle++;

        String status;
        String results;

        if (tearCycle >= 55 && tearCycle < 75) {
            // 撕裂告警状态
            status = "alarm";
            results = """
                    [
                      {"id":"normal","label":"皮带正常","conf":4.2,  "color":"var(--text-muted)"},
                      {"id":"tear",  "label":"纵向撕裂","conf":91.8, "color":"var(--danger)"},
                      {"id":"hole",  "label":"穿孔缺陷","conf":2.1,  "color":"var(--danger)"},
                      {"id":"edge",  "label":"边缘破损","conf":1.9,  "color":"var(--warn)"}
                    ]""";
        } else {
            // 正常状态
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
        String json = String.format("""
                {
                  "type": "detection",
                  "status": "%s",
                  "time": "%s",
                  "results": %s
                }""", status, time, results);

        MonitorWebSocketHandler.broadcast(json);

        // 撕裂开始时额外推一条报警消息
        if (tearCycle == 55) {
            String alarmJson = String.format("""
                    {
                      "type": "alarm",
                      "level": "danger",
                      "label": "紧急",
                      "msg": "底面检测到纵向撕裂，置信度 91.8%%，请立即处置",
                      "src": "CAM-01",
                      "time": "%s"
                    }""", time);
            MonitorWebSocketHandler.broadcast(alarmJson);
        }
    }
}