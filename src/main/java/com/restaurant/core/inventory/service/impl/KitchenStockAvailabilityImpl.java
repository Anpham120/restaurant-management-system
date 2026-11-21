package com.restaurant.core.inventory.service.impl;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.inventory.KitchenStockAvailability;
import com.restaurant.core.inventory.repository.KitchenStockAvailabilityRepository;
import com.restaurant.core.inventory.repository.KitchenStockAvailabilityRepository.DishIngredient;
import com.restaurant.core.menu.StockPortionCounter;

@Service
@RequiredArgsConstructor
public class KitchenStockAvailabilityImpl implements KitchenStockAvailability, StockPortionCounter {

    private static final BigDecimal MAX_PORTIONS = BigDecimal.valueOf(Integer.MAX_VALUE);

    private final KitchenStockAvailabilityRepository repository;
    private final BusinessDateProvider clock;

    @Override
    public Map<Long, Integer> countAvailablePortions(Collection<Long> dailyMenuItemIds) {
        Map<Long, List<DishIngredient>> recipes = findRecipes(dailyMenuItemIds);
        if (recipes.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> available = repository.findAvailableIngredients(ingredientTypes(recipes), clock.now());
        return recipes.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> countPortions(e.getValue(), available)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailableForBatch(List<DishPortions> dishes) {
        Map<Long, List<DishIngredient>> recipes = findRecipes(dishes.stream().map(DishPortions::dailyMenuItemId).distinct().toList());
        if (recipes.isEmpty()) {
            return;
        }
        Set<Long> typeIds = ingredientTypes(recipes);
        repository.lockPlacement(typeIds);
        Map<Long, BigDecimal> available = repository.findAvailableIngredients(typeIds, clock.now());
        for (DishPortions dish : dishes) {
            List<DishIngredient> recipe = recipes.getOrDefault(dish.dailyMenuItemId(), List.of());
            int remaining = countPortions(recipe, available);
            if (dish.quantity() > remaining) {
                throw ApiException.insufficientStock("Món " + dish.name() + " không đủ nguyên liệu: cần " + dish.quantity()
                        + " suất, có thể làm thêm " + remaining + " suất trong lượt này");
            }
            for (DishIngredient ingredient : recipe) {
                BigDecimal needed = ingredient.quantityPerServing().multiply(BigDecimal.valueOf(dish.quantity()));
                available.merge(ingredient.ingredientTypeId(), needed.negate(), BigDecimal::add);
            }
        }
    }

    private Map<Long, List<DishIngredient>> findRecipes(Collection<Long> dishIds) {
        if (dishIds.isEmpty()) {
            return Map.of();
        }
        return repository.findRequirements(dishIds).stream().collect(Collectors.groupingBy(DishIngredient::dailyMenuItemId));
    }

    private static Set<Long> ingredientTypes(Map<Long, List<DishIngredient>> recipes) {
        return recipes.values().stream().flatMap(List::stream).map(DishIngredient::ingredientTypeId).collect(Collectors.toSet());
    }

    private static int countPortions(List<DishIngredient> recipe, Map<Long, BigDecimal> available) {
        return recipe.stream().mapToInt(ingredient -> available.getOrDefault(ingredient.ingredientTypeId(), BigDecimal.ZERO)
                        .divideToIntegralValue(ingredient.quantityPerServing()).min(MAX_PORTIONS).intValueExact())
                .min().orElse(Integer.MAX_VALUE);
    }
}
