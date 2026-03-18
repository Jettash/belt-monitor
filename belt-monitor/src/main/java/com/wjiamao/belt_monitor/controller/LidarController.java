package com.wjiamao.belt_monitor.controller;

import com.wjiamao.belt_monitor.simulator.LidarState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 接收 Python lidar_service 推送的截面数据
 *
 * POST /api/lidar/frame
 * Body: { "crossArea": 0.0842, "heapHeight": 0.187 }
 *
 * Python 每秒推一次（1 秒内多帧的均值），Java 用真实速度算载量后广播。
 */
@RestController
@RequestMapping("/api/lidar")
@CrossOrigin
public class LidarController {

    @Autowired
    private LidarState lidarState;

    @PostMapping("/frame")
    public ResponseEntity<Void> receiveFrame(@RequestBody Map<String, Object> body) {
        double area   = toDouble(body.get("crossArea"),  0.0);
        double height = toDouble(body.get("heapHeight"), 0.0);
        lidarState.update(area, height);
        return ResponseEntity.ok().build();
    }

    /** 查看雷达当前状态（调试用） */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
            "available",   lidarState.isAvailable(),
            "crossArea",   lidarState.getCrossArea(),
            "heapHeight",  lidarState.getHeapHeight()
        );
    }

    private static double toDouble(Object v, double fallback) {
        if (v instanceof Number n) return n.doubleValue();
        return fallback;
    }
}
