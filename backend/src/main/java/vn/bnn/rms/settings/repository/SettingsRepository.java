package vn.bnn.rms.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.bnn.rms.settings.entity.RestaurantSettings;

public interface SettingsRepository extends JpaRepository<RestaurantSettings, Integer> {
}
