package com.wjiamao.belt_monitor.repository;

import com.wjiamao.belt_monitor.model.LoadRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoadRecordRepository extends JpaRepository<LoadRecord, Long> {

    /** 按时间范围查询，升序排列 */
    List<LoadRecord> findByTsBetweenOrderByTsAsc(LocalDateTime start, LocalDateTime end);
}
