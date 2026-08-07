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
@RequestMapping("/api/stock-change-logs")
@RequiredArgsConstructor
public class StockChangeLogController {

    private final StockChangeLogRepository stockChangeLogRepository;
    private final StockLotRepository stockLotRepository;
    private final AppUserRepository appUserRepository;

    public record StockChangeLogRequest(
            @NotNull Long lotId,
            @NotNull Long changedByUserId,
            @NotNull String changeType,
            String beforeValue,
            String afterValue,
            String reason,
            Boolean isReverted
    ) {}

    @GetMapping
    public List<StockChangeLog> findAll() {
        return stockChangeLogRepository.findAll();
    }

    @GetMapping("/{id}")
    public StockChangeLog findById(@PathVariable Long id) {
        return stockChangeLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockChangeLog not found: " + id));
    }

    @GetMapping("/by-lot/{lotId}")
    public List<StockChangeLog> findByLot(@PathVariable Long lotId) {
        return stockChangeLogRepository.findByLot_LotIdOrderByChangedAtDesc(lotId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockChangeLog create(@Valid @RequestBody StockChangeLogRequest request) {
        StockChangeLog log = StockChangeLog.builder()
                .lot(getLot(request.lotId()))
                .changedBy(getUser(request.changedByUserId()))
                .changeType(request.changeType())
                .beforeValue(request.beforeValue())
                .afterValue(request.afterValue())
                .reason(request.reason())
                .isReverted(request.isReverted() != null ? request.isReverted() : false)
                .build();
        return stockChangeLogRepository.save(log);
    }

    @PutMapping("/{id}")
    public StockChangeLog update(@PathVariable Long id, @Valid @RequestBody StockChangeLogRequest request) {
        StockChangeLog log = stockChangeLogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockChangeLog not found: " + id));
        log.setLot(getLot(request.lotId()));
        log.setChangedBy(getUser(request.changedByUserId()));
        log.setChangeType(request.changeType());
        log.setBeforeValue(request.beforeValue());
        log.setAfterValue(request.afterValue());
        log.setReason(request.reason());
        if (request.isReverted() != null) {
            log.setIsReverted(request.isReverted());
        }
        return stockChangeLogRepository.save(log);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!stockChangeLogRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StockChangeLog not found: " + id);
        }
        stockChangeLogRepository.deleteById(id);
    }

    private StockLot getLot(Long id) {
        return stockLotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockLot not found: " + id));
    }

    private AppUser getUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
