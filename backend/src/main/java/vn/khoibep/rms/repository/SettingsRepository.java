package vn.khoibep.rms.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.RestaurantSettings;

public interface SettingsRepository extends JpaRepository<RestaurantSettings, Integer> {
}
