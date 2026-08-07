package com.bottommart.repository;

import com.bottommart.entity.StockChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockChangeLogRepository extends JpaRepository<StockChangeLog, Long> {
    List<StockChangeLog> findByLot_LotIdOrderByChangedAtDesc(Long lotId);
}
