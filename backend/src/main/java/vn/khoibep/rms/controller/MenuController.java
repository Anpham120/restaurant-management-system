package vn.khoibep.rms.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.dto.MenuDtos.AvailabilityRequest;
import vn.khoibep.rms.dto.MenuDtos.CategoryDto;
import vn.khoibep.rms.dto.MenuDtos.CategoryRequest;
import vn.khoibep.rms.dto.MenuDtos.MenuItemDto;
import vn.khoibep.rms.dto.MenuDtos.MenuItemRequest;
import vn.khoibep.rms.service.MenuService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return menuService.categories();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return menuService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return menuService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        menuService.deleteCategory(id);
    }

    @GetMapping("/menu-items")
    public List<MenuItemDto> items() {
        return menuService.items();
    }

    @PostMapping("/menu-items")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemDto createItem(@Valid @RequestBody MenuItemRequest request) {
        return menuService.createItem(request);
    }

    @PutMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public MenuItemDto updateItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        return menuService.updateItem(id, request);
    }

    @DeleteMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        menuService.deleteItem(id);
    }

    /** FR-03.3: the kitchen can mark a dish sold out. MANAGER and ADMIN inherit CHEF. */
    @PatchMapping("/menu-items/{id}/availability")
    @PreAuthorize("hasRole('CHEF')")
    public MenuItemDto setAvailability(@PathVariable Long id, @Valid @RequestBody AvailabilityRequest request) {
        return menuService.setAvailability(id, request.available());
    }
}
