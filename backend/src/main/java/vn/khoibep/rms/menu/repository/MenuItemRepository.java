package vn.khoibep.rms.menu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import vn.khoibep.rms.menu.entity.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("select m from MenuItem m join fetch m.category c join fetch m.taxCategory order by c.sortOrder, c.name, m.name")
    List<MenuItem> findAllWithCategory();

    boolean existsByCategoryId(Long categoryId);
}
