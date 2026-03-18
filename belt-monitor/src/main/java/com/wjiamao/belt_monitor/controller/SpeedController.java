package com.wjiamao.belt_monitor.controller;

import com.wjiamao.belt_monitor.model.SpeedRecord;
import com.wjiamao.belt_monitor.repository.SpeedRecordRepository;
import com.wjiamao.belt_monitor.simulator.ModbusSpeedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 皮带速度记录查询接口
 *
 * GET /api/speed/records?date=YYYY-MM-DD
 * GET /api/speed/records?start=YYYY-MM-DD&end=YYYY-MM-DD
 * GET /api/speed/status   — 当前 Modbus 连接状态
 */
@RestController
@RequestMapping("/api/speed")
@CrossOrigin
public class SpeedController {

    @Autowired
    private SpeedRecordRepository speedRecordRepository;

    @Autowired
    private ModbusSpeedService modbusSpeedService;

    @GetMapping("/records")
    public List<SpeedRecord> getRecords(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        LocalDateTime from, to;

        if (date != null) {
            LocalDate d = LocalDate.parse(date);
            from = d.atStartOfDay();
            to   = d.plusDays(1).atStartOfDay();
        } else {
            LocalDate s = start != null ? LocalDate.parse(start) : LocalDate.now();
            LocalDate e = end   != null ? LocalDate.parse(end)   : LocalDate.now();
            from = s.atStartOfDay();
            to   = e.plusDays(1).atStartOfDay();
        }

        return speedRecordRepository.findByTsBetweenOrderByTsAsc(from, to);
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "connected", modbusSpeedService.isConnected(),
                "lastSpeed",  modbusSpeedService.readSpeed()
        );
    }
}
