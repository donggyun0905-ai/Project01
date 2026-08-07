package com.bottommart.controller;

import com.bottommart.entity.Warehouse;
import com.bottommart.entity.Zone;
import com.bottommart.repository.WarehouseRepository;
import com.bottommart.repository.ZoneRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRepository zoneRepository;
    private final WarehouseRepository warehouseRepository;

    public record ZoneRequest(
            @NotNull Long warehouseId,
            @NotBlank String zoneName,
            Integer capacity
    ) {}

    @GetMapping
    public List<Zone> findAll() {
        return zoneRepository.findAll();
    }

    @GetMapping("/{id}")
    public Zone findById(@PathVariable Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Zone create(@Valid @RequestBody ZoneRequest request) {
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found: " + request.warehouseId()));
        Zone zone = Zone.builder()
                .warehouse(warehouse)
                .zoneName(request.zoneName())
                .capacity(request.capacity())
                .build();
        return zoneRepository.save(zone);
    }

    @PutMapping("/{id}")
    public Zone update(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found: " + id));
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Warehouse not found: " + request.warehouseId()));
        zone.setWarehouse(warehouse);
        zone.setZoneName(request.zoneName());
        zone.setCapacity(request.capacity());
        return zoneRepository.save(zone);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!zoneRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found: " + id);
        }
        zoneRepository.deleteById(id);
    }
}
