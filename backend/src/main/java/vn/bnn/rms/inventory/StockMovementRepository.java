package vn.bnn.rms.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByItemIdOrderByCreatedAtDescIdDesc(Long itemId);
}
