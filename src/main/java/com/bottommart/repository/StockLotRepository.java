package com.bottommart.repository;

import com.bottommart.entity.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {
    // FIFO candidate order for a given item
    List<StockLot> findByItem_ItemIdOrderByInboundDateAsc(Long itemId);

    // FEFO candidate order for a given item
    List<StockLot> findByItem_ItemIdOrderByExpiryDateAsc(Long itemId);

    List<StockLot> findByZone_ZoneId(Long zoneId);
}
