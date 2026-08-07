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
@RequestMapping("/api/outbounds")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundRepository outboundRepository;
    private final StockLotRepository stockLotRepository;
    private final PartnerRepository partnerRepository;
    private final AppUserRepository appUserRepository;

    public record OutboundRequest(
            @NotNull Long lotId,
            @NotNull Long partnerId,
            @NotNull Integer quantity,
            @NotNull LocalDate outboundDate,
            @NotNull Long createdByUserId
    ) {}

    @GetMapping
    public List<Outbound> findAll() {
        return outboundRepository.findAll();
    }

    @GetMapping("/{id}")
    public Outbound findById(@PathVariable Long id) {
        return outboundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbound not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Outbound create(@Valid @RequestBody OutboundRequest request) {
        Outbound outbound = Outbound.builder()
                .lot(getLot(request.lotId()))
                .partner(getPartner(request.partnerId()))
                .quantity(request.quantity())
                .outboundDate(request.outboundDate())
                .createdBy(getUser(request.createdByUserId()))
                .build();
        return outboundRepository.save(outbound);
    }

    @PutMapping("/{id}")
    public Outbound update(@PathVariable Long id, @Valid @RequestBody OutboundRequest request) {
        Outbound outbound = outboundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbound not found: " + id));
        outbound.setLot(getLot(request.lotId()));
        outbound.setPartner(getPartner(request.partnerId()));
        outbound.setQuantity(request.quantity());
        outbound.setOutboundDate(request.outboundDate());
        outbound.setCreatedBy(getUser(request.createdByUserId()));
        return outboundRepository.save(outbound);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!outboundRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbound not found: " + id);
        }
        outboundRepository.deleteById(id);
    }

    private StockLot getLot(Long id) {
        return stockLotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StockLot not found: " + id));
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
