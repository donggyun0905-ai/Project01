package com.bottommart.controller;

import com.bottommart.entity.Alert;
import com.bottommart.entity.Item;
import com.bottommart.repository.AlertRepository;
import com.bottommart.repository.ItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;
    private final ItemRepository itemRepository;

    public record AlertRequest(
            @NotNull Long itemId,
            @NotBlank String alertType,
            @NotBlank String message,
            Boolean isResolved
    ) {}

    @GetMapping
    public List<Alert> findAll() {
        return alertRepository.findAll();
    }

    @GetMapping("/unresolved")
    public List<Alert> findUnresolved() {
        return alertRepository.findByIsResolvedFalse();
    }

    @GetMapping("/{id}")
    public Alert findById(@PathVariable Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Alert create(@Valid @RequestBody AlertRequest request) {
        Alert alert = Alert.builder()
                .item(getItem(request.itemId()))
                .alertType(request.alertType())
                .message(request.message())
                .isResolved(request.isResolved() != null ? request.isResolved() : false)
                .build();
        return alertRepository.save(alert);
    }

    @PutMapping("/{id}")
    public Alert update(@PathVariable Long id, @Valid @RequestBody AlertRequest request) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + id));
        alert.setItem(getItem(request.itemId()));
        alert.setAlertType(request.alertType());
        alert.setMessage(request.message());
        if (request.isResolved() != null) {
            alert.setIsResolved(request.isResolved());
        }
        return alertRepository.save(alert);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!alertRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + id);
        }
        alertRepository.deleteById(id);
    }

    private Item getItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
    }
}
