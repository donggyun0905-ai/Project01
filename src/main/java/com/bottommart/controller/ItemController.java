package com.bottommart.controller;

import com.bottommart.entity.Item;
import com.bottommart.repository.ItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemRepository itemRepository;

    public record ItemRequest(
            @NotBlank String itemName,
            String category,
            @NotBlank String unit,
            Integer thresholdMin,
            Integer capacityMax
    ) {}

    @GetMapping
    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    @GetMapping("/{id}")
    public Item findById(@PathVariable Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Item create(@Valid @RequestBody ItemRequest request) {
        Item item = Item.builder()
                .itemName(request.itemName())
                .category(request.category())
                .unit(request.unit())
                .thresholdMin(request.thresholdMin())
                .capacityMax(request.capacityMax())
                .build();
        return itemRepository.save(item);
    }

    @PutMapping("/{id}")
    public Item update(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
        item.setItemName(request.itemName());
        item.setCategory(request.category());
        item.setUnit(request.unit());
        item.setThresholdMin(request.thresholdMin());
        item.setCapacityMax(request.capacityMax());
        return itemRepository.save(item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!itemRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id);
        }
        itemRepository.deleteById(id);
    }
}
