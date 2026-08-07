package com.bottommart.repository;

import com.bottommart.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByWarehouse_WarehouseId(Long warehouseId);
}
