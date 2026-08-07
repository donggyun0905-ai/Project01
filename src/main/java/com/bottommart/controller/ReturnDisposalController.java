package com.bottommart.controller;

import com.bottommart.entity.*;
import com.bottommart.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/return-disposals")
@RequiredArgsConstructor
public class ReturnDisposalController {

    private final ReturnDisposalRepository returnDisposalRepository;
    private final StockLotRepository stockLotRepository;
    private final AppUserRepository appUserRepository;

    public record ReturnDisposalRequest(
            @NotNull Long lotId,
            @NotBlank String type,
            @NotBlank String reason,
            @NotNull Integer quantity,
            @NotNull Long processedByUserId,
            @NotNull LocalDate processedDate
    ) {}

    @GetMapping
    public List<ReturnDisposal> findAll() {
        return returnDisposalRepository.findAll();
    }

    @GetMapping("/{id}")
    public ReturnDisposal findById(@PathVariable Long id) {
        return returnDisposalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ReturnDisposal not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnDisposal create(@Valid @RequestBody ReturnDisposalRequest request) {
        ReturnDisposal record = ReturnDisposal.builder()
                .lot(getLot(request.lotId()))
                .type(request.type())
                .reason(request.reason())
                .quantity(request.quantity())
                .processedBy(getUser(request.processedByUserId()))
                .processedDate(request.processedDate())
                .build();
        return returnDisposalRepository.save(record);
    }

    @PutMapping("/{id}")
    public ReturnDisposal update(@PathVariable Long id, @Valid @RequestBody ReturnDisposalRequest request) {
        ReturnDisposal record = returnDisposalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ReturnDisposal not found: " + id));
        record.setLot(getLot(request.lotId()));
        record.setType(request.type());
        record.setReason(request.reason());
        record.setQuantity(request.quantity());
        record.setProcessedBy(getUser(request.processedByUserId()));
        record.setProcessedDate(request.processedDate());
        return returnDisposalRepository.save(record);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!returnDisposalRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ReturnDisposal not found: " + id);
        }
        returnDisposalRepository.deleteById(id);
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
