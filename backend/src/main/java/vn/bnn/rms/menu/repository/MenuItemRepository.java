package vn.bnn.rms.menu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import vn.bnn.rms.menu.entity.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    @Query("select m from MenuItem m join fetch m.category c order by c.sortOrder, c.name, m.name")
    List<MenuItem> findAllWithCategory();

    boolean existsByCategoryId(Long categoryId);
}
