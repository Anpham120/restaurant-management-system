package com.restaurant.core.menu.service.impl;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.menu.DailyMenuLookup;
import com.restaurant.core.menu.entity.DailyMenuItem;
import com.restaurant.core.menu.repository.DailyMenuItemRepository;
import com.restaurant.core.menu.repository.RecipeIngredientRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyMenuLookupImpl implements DailyMenuLookup {

    private final DailyMenuItemRepository dailyMenuItemRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final BusinessDateProvider businessDate;

    @Override
    public Optional<OrderableDish> findDish(long dailyMenuItemId) {
        return dailyMenuItemRepository.findWithMenuById(dailyMenuItemId).map(this::toOrderableDish);
    }

    @Override
    @Transactional
    public Optional<OrderableDish> findDishForUpdate(long dailyMenuItemId) {
        return dailyMenuItemRepository.findByIdForUpdate(dailyMenuItemId).map(this::toOrderableDish);
    }

    @Override
    public List<RecipeLine> getRecipeLines(long dailyMenuItemId) {
        long recipeId = dailyMenuItemRepository.findById(dailyMenuItemId).map(DailyMenuItem::getRecipeId).orElseThrow();
        return recipeIngredientRepository.findByRecipe_IdOrderByKitchenIngredientTypeId(recipeId).stream()
                .map(r -> new RecipeLine(r.getKitchenIngredientTypeId(), r.getQuantityPerServing()))
                .toList();
    }

    private OrderableDish toOrderableDish(DailyMenuItem d) {
        return new OrderableDish(d.getId(), d.getMenuItem().getName(), d.getMenuItem().getPrice(), d.getMenuItem().isActive(),
                d.getDailyMenu().isOnSaleOn(businessDate.today()), d.isPaused(), d.getPauseReason(), d.getQuotaTotal(),
                d.getRecipeId());
    }
}
