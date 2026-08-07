package com.bottommart.repository;

import com.bottommart.entity.UserWarehouse;
import com.bottommart.entity.UserWarehouseId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserWarehouseRepository extends JpaRepository<UserWarehouse, UserWarehouseId> {
    List<UserWarehouse> findByUser_UserId(Long userId);
}
