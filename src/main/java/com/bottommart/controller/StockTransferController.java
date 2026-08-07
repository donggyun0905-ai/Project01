package com.bottommart.controller;

import com.bottommart.entity.*;
import com.bottommart.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferRepository stockTransferRepository;
    private final StockLotRepository stockLotRepository;
    private final ZoneRepository zoneRepository;
    private final AppUserRepository appUserRepository;

    public record StockTransferRequest(
            @NotNull Long lotId,
            @NotNull Long fromZoneId,
            @NotNull Long toZoneId,
            @NotNull Integer quantity,
            @NotNull Long handlerId
    ) {}

    @GetMapping
    public List<StockTransfer> findAll() {
        return stockTransferRepository.findAll();
    }

    @GetMapping("/{id}")
    public StockTransfer findById(@PathVariable Long id) {
        return stockTransferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockTransfer not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockTransfer create(@Valid @RequestBody StockTransferRequest request) {
        validateZones(request.fromZoneId(), request.toZoneId());
        StockTransfer transfer = StockTransfer.builder()
                .lot(getLot(request.lotId()))
                .fromZone(getZone(request.fromZoneId()))
                .toZone(getZone(request.toZoneId()))
                .quantity(request.quantity())
                .handler(getUser(request.handlerId()))
                .build();
        return stockTransferRepository.save(transfer);
    }

    @PutMapping("/{id}")
    public StockTransfer update(@PathVariable Long id, @Valid @RequestBody StockTransferRequest request) {
        validateZones(request.fromZoneId(), request.toZoneId());
        StockTransfer transfer = stockTransferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockTransfer not found: " + id));
        transfer.setLot(getLot(request.lotId()));
        transfer.setFromZone(getZone(request.fromZoneId()));
        transfer.setToZone(getZone(request.toZoneId()));
        transfer.setQuantity(request.quantity());
        transfer.setHandler(getUser(request.handlerId()));
        return stockTransferRepository.save(transfer);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!stockTransferRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StockTransfer not found: " + id);
        }
        stockTransferRepository.deleteById(id);
    }

    // from_zone must differ from to_zone (schema design decision, see 확정안 스키마 설계.pdf)
    private void validateZones(Long fromZoneId, Long toZoneId) {
        if (fromZoneId.equals(toZoneId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromZoneId and toZoneId must differ");
        }
    }

    private StockLot getLot(Long id) {
        return stockLotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockLot not found: " + id));
    }

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found: " + id));
    }

    private AppUser getUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
