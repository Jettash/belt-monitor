package com.wjiamao.belt_monitor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 皮带速度记录
 * 每秒写入一条，来自 HGL-150 测速仪（Modbus RTU）
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "speed_records")
public class SpeedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 采集时间 */
    private LocalDateTime ts;

    /** 皮带速度 (m/s) */
    private double speed;

    public SpeedRecord(LocalDateTime ts, double speed) {
        this.ts    = ts;
        this.speed = speed;
    }
}
