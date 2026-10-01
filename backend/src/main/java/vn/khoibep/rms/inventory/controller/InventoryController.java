package vn.khoibep.rms.inventory.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.inventory.dto.InventoryDtos.InventoryItemDto;
import vn.khoibep.rms.inventory.dto.InventoryDtos.InventoryItemRequest;
import vn.khoibep.rms.inventory.dto.InventoryDtos.MovementDto;
import vn.khoibep.rms.inventory.dto.InventoryDtos.MovementRequest;
import vn.khoibep.rms.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/inventory-items")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<InventoryItemDto> list() {
        return inventoryService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemDto create(@Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.create(request);
    }

    @PutMapping("/{id}")
    public InventoryItemDto update(@PathVariable Long id, @Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.update(id, request);
    }

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemDto addMovement(@PathVariable Long id, @Valid @RequestBody MovementRequest request) {
        return inventoryService.addMovement(id, request, currentUser.id());
    }

    @GetMapping("/{id}/movements")
    public List<MovementDto> movements(@PathVariable Long id) {
        return inventoryService.movements(id);
    }
}
