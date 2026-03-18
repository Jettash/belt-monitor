package com.wjiamao.belt_monitor.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 载量记录实体
 * 每 10 分钟写入一次，记录该时段累计运量
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "load_records")
public class LoadRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 记录时间（精确到分钟） */
    private LocalDateTime ts;

    /** 本时段运量 (t) — 过去 10 分钟累计 */
    private double periodTon;

    /** 本班累计运量 (t) */
    private double shiftTon;

    /** 今日累计运量 (t) */
    private double dayTon;

    public LoadRecord(LocalDateTime ts, double periodTon, double shiftTon, double dayTon) {
        this.ts        = ts;
        this.periodTon = periodTon;
        this.shiftTon  = shiftTon;
        this.dayTon    = dayTon;
    }
}
