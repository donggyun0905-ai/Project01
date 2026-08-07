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

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalRepository approvalRepository;
    private final ItemRepository itemRepository;
    private final AlertRepository alertRepository;
    private final AppUserRepository appUserRepository;

    public record ApprovalRequest(
            @NotNull Long itemId,
            Long alertId,
            @NotBlank String requestType,
            @NotNull Integer requestedQty,
            String status,
            Long requestedByUserId,
            Long approvedByUserId,
            LocalDateTime approvedAt
    ) {}

    @GetMapping
    public List<Approval> findAll() {
        return approvalRepository.findAll();
    }

    @GetMapping("/{id}")
    public Approval findById(@PathVariable Long id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Approval create(@Valid @RequestBody ApprovalRequest request) {
        Approval approval = Approval.builder()
                .item(getItem(request.itemId()))
                .alert(request.alertId() != null ? getAlert(request.alertId()) : null)
                .requestType(request.requestType())
                .requestedQty(request.requestedQty())
                .status(request.status() != null ? request.status() : "대기")
                .requestedBy(request.requestedByUserId() != null ? getUser(request.requestedByUserId()) : null)
                .approvedBy(request.approvedByUserId() != null ? getUser(request.approvedByUserId()) : null)
                .approvedAt(request.approvedAt())
                .build();
        return approvalRepository.save(approval);
    }

    @PutMapping("/{id}")
    public Approval update(@PathVariable Long id, @Valid @RequestBody ApprovalRequest request) {
        Approval approval = approvalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found: " + id));
        approval.setItem(getItem(request.itemId()));
        approval.setAlert(request.alertId() != null ? getAlert(request.alertId()) : null);
        approval.setRequestType(request.requestType());
        approval.setRequestedQty(request.requestedQty());
        if (request.status() != null) {
            approval.setStatus(request.status());
        }
        approval.setRequestedBy(request.requestedByUserId() != null ? getUser(request.requestedByUserId()) : null);
        approval.setApprovedBy(request.approvedByUserId() != null ? getUser(request.approvedByUserId()) : null);
        approval.setApprovedAt(request.approvedAt());
        return approvalRepository.save(approval);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!approvalRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval not found: " + id);
        }
        approvalRepository.deleteById(id);
    }

    private Item getItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
    }

    private Alert getAlert(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + id));
    }

    private AppUser getUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
