package vn.khoibep.rms.inventory.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.inventory.dto.RecipeDtos.RecipeDto;
import vn.khoibep.rms.inventory.dto.RecipeDtos.RecipeRequest;
import vn.khoibep.rms.inventory.dto.RecipeDtos.UsageDto;
import vn.khoibep.rms.inventory.service.RecipeService;
import vn.khoibep.rms.inventory.service.StockUsageService;

/** FR-09.8 → FR-09.10. Recipes are the kitchen's know-how, so only managers see them (BR-38). */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final StockUsageService usageService;

    @GetMapping("/recipes")
    public List<RecipeDto> recipes() {
        return recipeService.list();
    }

    @PutMapping("/menu-items/{id}/recipe")
    public RecipeDto replaceRecipe(@PathVariable Long id, @Valid @RequestBody RecipeRequest request) {
        return recipeService.replace(id, request);
    }

    @GetMapping("/inventory-usage")
    public List<UsageDto> usage(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return usageService.usage(from, to);
    }
}
