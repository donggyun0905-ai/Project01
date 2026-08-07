package com.bottommart.controller;

import com.bottommart.entity.AppUser;
import com.bottommart.repository.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserRepository appUserRepository;

    public record UserRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank String name,
            @NotBlank String role
    ) {}

    @GetMapping
    public List<AppUser> findAll() {
        return appUserRepository.findAll();
    }

    @GetMapping("/{id}")
    public AppUser findById(@PathVariable Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppUser create(@Valid @RequestBody UserRequest request) {
        AppUser user = AppUser.builder()
                .username(request.username())
                .password(request.password())
                .name(request.name())
                .role(request.role())
                .build();
        return appUserRepository.save(user);
    }

    @PutMapping("/{id}")
    public AppUser update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
        user.setUsername(request.username());
        user.setPassword(request.password());
        user.setName(request.name());
        user.setRole(request.role());
        return appUserRepository.save(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!appUserRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id);
        }
        appUserRepository.deleteById(id);
    }
}
