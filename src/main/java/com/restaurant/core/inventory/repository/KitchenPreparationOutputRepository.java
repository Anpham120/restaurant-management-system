package com.restaurant.core.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.restaurant.core.inventory.entity.KitchenPreparationOutput;

public interface KitchenPreparationOutputRepository extends JpaRepository<KitchenPreparationOutput, Long> {

    List<KitchenPreparationOutput> findByKitchenPreparation_IdOrderById(Long kitchenPreparationId);
}
