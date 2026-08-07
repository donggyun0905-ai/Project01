package com.bottommart.controller;

import com.bottommart.entity.*;
import com.bottommart.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-lots")
@RequiredArgsConstructor
public class StockLotController {

    private final StockLotRepository stockLotRepository;
    private final ItemRepository itemRepository;
    private final ZoneRepository zoneRepository;
    private final PartnerRepository partnerRepository;
    private final AppUserRepository appUserRepository;

    public record StockLotRequest(
            @NotNull Long itemId,
            @NotNull Long zoneId,
            @NotNull Long partnerId,
            @NotNull Integer quantity,
            @NotNull LocalDate inboundDate,
            LocalDate expiryDate,
            String status,
            @NotNull Long createdByUserId,
            Long parentLotId
    ) {}

    @GetMapping
    public List<StockLot> findAll() {
        return stockLotRepository.findAll();
    }

    @GetMapping("/{id}")
    public StockLot findById(@PathVariable Long id) {
        return stockLotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockLot not found: " + id));
    }

    // FIFO order for a given item
    @GetMapping("/by-item/{itemId}/fifo")
    public List<StockLot> findFifoByItem(@PathVariable Long itemId) {
        return stockLotRepository.findByItem_ItemIdOrderByInboundDateAsc(itemId);
    }

    // FEFO order for a given item
    @GetMapping("/by-item/{itemId}/fefo")
    public List<StockLot> findFefoByItem(@PathVariable Long itemId) {
        return stockLotRepository.findByItem_ItemIdOrderByExpiryDateAsc(itemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockLot create(@Valid @RequestBody StockLotRequest request) {
        StockLot lot = StockLot.builder()
                .item(getItem(request.itemId()))
                .zone(getZone(request.zoneId()))
                .partner(getPartner(request.partnerId()))
                .quantity(request.quantity())
                .inboundDate(request.inboundDate())
                .expiryDate(request.expiryDate())
                .status(request.status() != null ? request.status() : "NORMAL")
                .createdBy(getUser(request.createdByUserId()))
                .parentLot(request.parentLotId() != null ? getLot(request.parentLotId()) : null)
                .build();
        return stockLotRepository.save(lot);
    }

    @PutMapping("/{id}")
    public StockLot update(@PathVariable Long id, @Valid @RequestBody StockLotRequest request) {
        StockLot lot = getLot(id);
        lot.setItem(getItem(request.itemId()));
        lot.setZone(getZone(request.zoneId()));
        lot.setPartner(getPartner(request.partnerId()));
        lot.setQuantity(request.quantity());
        lot.setInboundDate(request.inboundDate());
        lot.setExpiryDate(request.expiryDate());
        lot.setStatus(request.status() != null ? request.status() : lot.getStatus());
        lot.setCreatedBy(getUser(request.createdByUserId()));
        lot.setParentLot(request.parentLotId() != null ? getLot(request.parentLotId()) : null);
        return stockLotRepository.save(lot);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!stockLotRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StockLot not found: " + id);
        }
        stockLotRepository.deleteById(id);
    }

    private StockLot getLot(Long id) {
        return stockLotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockLot not found: " + id));
    }

    private Item getItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
    }

    private Zone getZone(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Zone not found: " + id));
    }

    private Partner getPartner(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found: " + id));
    }

    private AppUser getUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
