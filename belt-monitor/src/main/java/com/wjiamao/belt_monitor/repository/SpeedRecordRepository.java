package com.wjiamao.belt_monitor.repository;

import com.wjiamao.belt_monitor.model.SpeedRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SpeedRecordRepository extends JpaRepository<SpeedRecord, Long> {

    List<SpeedRecord> findByTsBetweenOrderByTsAsc(LocalDateTime start, LocalDateTime end);
}
