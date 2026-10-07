package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import vn.khoibep.rms.model.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("select m from MenuItem m join fetch m.category c join fetch m.taxCategory left join fetch m.appPrices"
            + " order by c.sortOrder, c.name, m.name")
    List<MenuItem> findAllWithCategory();

    boolean existsByCategoryId(Long categoryId);
}
