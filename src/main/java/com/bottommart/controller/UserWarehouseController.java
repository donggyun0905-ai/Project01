package com.bottommart.controller;

import com.bottommart.entity.AppUser;
import com.bottommart.entity.UserWarehouse;
import com.bottommart.entity.UserWarehouseId;
import com.bottommart.entity.Warehouse;
import com.bottommart.repository.AppUserRepository;
import com.bottommart.repository.UserWarehouseRepository;
import com.bottommart.repository.WarehouseRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// STAFF-to-warehouse assignment (N:M). ADMIN users need no row here.
@RestController
@RequestMapping("/api/user-warehouses")
@RequiredArgsConstructor
public class UserWarehouseController {

    private final UserWarehouseRepository userWarehouseRepository;
    private final AppUserRepository appUserRepository;
    private final WarehouseRepository warehouseRepository;

    public record UserWarehouseRequest(
            @NotNull Long userId,
            @NotNull Long warehouseId
    ) {}

    @GetMapping
    public List<UserWarehouse> findAll() {
        return userWarehouseRepository.findAll();
    }

    @GetMapping("/by-user/{userId}")
    public List<UserWarehouse> findByUser(@PathVariable Long userId) {
        return userWarehouseRepository.findByUser_UserId(userId);
    }

    @GetMapping("/{userId}/{warehouseId}")
    public UserWarehouse findById(@PathVariable Long userId, @PathVariable Long warehouseId) {
        UserWarehouseId id = new UserWarehouseId(userId, warehouseId);
        return userWarehouseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found: " + userId + "/" + warehouseId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserWarehouse create(@Valid @RequestBody UserWarehouseRequest request) {
        AppUser user = getUser(request.userId());
        Warehouse warehouse = getWarehouse(request.warehouseId());
        UserWarehouse assignment = UserWarehouse.builder()
                .id(new UserWarehouseId(user.getUserId(), warehouse.getWarehouseId()))
                .user(user)
                .warehouse(warehouse)
                .build();
        return userWarehouseRepository.save(assignment);
    }

    @DeleteMapping("/{userId}/{warehouseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long userId, @PathVariable Long warehouseId) {
        UserWarehouseId id = new UserWarehouseId(userId, warehouseId);
        if (!userWarehouseRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found: " + userId + "/" + warehouseId);
        }
        userWarehouseRepository.deleteById(id);
    }

    private AppUser getUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    private Warehouse getWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found: " + id));
    }
}
