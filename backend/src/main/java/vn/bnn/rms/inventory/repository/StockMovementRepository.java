package vn.bnn.rms.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.bnn.rms.inventory.entity.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByItemIdOrderByCreatedAtDescIdDesc(Long itemId);
}
