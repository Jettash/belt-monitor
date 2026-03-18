package com.wjiamao.belt_monitor.simulator;

import org.springframework.stereotype.Component;

/**
 * 雷达截面数据共享容器
 *
 * Python lidar_service 每秒通过 POST /api/lidar/frame 更新此状态，
 * DataSimulator 每秒读取此状态计算真实载量。
 *
 * 超过 3 秒未更新视为"数据超时"，DataSimulator 自动降级为模拟数据。
 */
@Component
public class LidarState {

    private static final long STALE_MS = 3_000;

    private volatile double crossArea   = 0.0;   // 截面积 m²
    private volatile double heapHeight  = 0.0;   // 最大堆高 m
    private volatile long   lastUpdateMs = 0;

    public void update(double crossArea, double heapHeight) {
        this.crossArea    = Math.max(0, crossArea);
        this.heapHeight   = Math.max(0, heapHeight);
        this.lastUpdateMs = System.currentTimeMillis();
    }

    public double getCrossArea()  { return crossArea;  }
    public double getHeapHeight() { return heapHeight; }

    /** 雷达数据是否在线（3 秒内有过更新） */
    public boolean isAvailable() {
        return lastUpdateMs > 0
            && System.currentTimeMillis() - lastUpdateMs <= STALE_MS;
    }
}
