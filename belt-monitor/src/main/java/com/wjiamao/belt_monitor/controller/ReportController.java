package com.wjiamao.belt_monitor.controller;

import com.wjiamao.belt_monitor.model.LoadRecord;
import com.wjiamao.belt_monitor.repository.LoadRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 载量报表 REST 接口
 * GET /api/report/records?date=YYYY-MM-DD
 * GET /api/report/records?start=YYYY-MM-DD&end=YYYY-MM-DD
 * 不传参数则默认返回今天的数据
 */
@RestController
@RequestMapping("/api/report")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private LoadRecordRepository repository;

    @GetMapping("/records")
    public List<LoadRecord> getRecords(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        LocalDateTime from, to;

        if (start != null && end != null) {
            // 自定义范围：包含 end 当天全天
            from = LocalDate.parse(start).atStartOfDay();
            to   = LocalDate.parse(end).plusDays(1).atStartOfDay();
        } else {
            // 按单日查询，默认今天
            LocalDate d = (date != null) ? LocalDate.parse(date) : LocalDate.now();
            from = d.atStartOfDay();
            to   = d.plusDays(1).atStartOfDay();
        }

        return repository.findByTsBetweenOrderByTsAsc(from, to);
    }
}
