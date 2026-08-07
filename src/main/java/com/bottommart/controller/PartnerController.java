package com.bottommart.controller;

import com.bottommart.entity.Partner;
import com.bottommart.repository.PartnerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerRepository partnerRepository;

    public record PartnerRequest(
            @NotBlank String name,
            @NotBlank String type,
            String contact
    ) {}

    @GetMapping
    public List<Partner> findAll() {
        return partnerRepository.findAll();
    }

    @GetMapping("/{id}")
    public Partner findById(@PathVariable Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Partner create(@Valid @RequestBody PartnerRequest request) {
        Partner partner = Partner.builder()
                .name(request.name())
                .type(request.type())
                .contact(request.contact())
                .build();
        return partnerRepository.save(partner);
    }

    @PutMapping("/{id}")
    public Partner update(@PathVariable Long id, @Valid @RequestBody PartnerRequest request) {
        Partner partner = partnerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found: " + id));
        partner.setName(request.name());
        partner.setType(request.type());
        partner.setContact(request.contact());
        return partnerRepository.save(partner);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!partnerRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Partner not found: " + id);
        }
        partnerRepository.deleteById(id);
    }
}
