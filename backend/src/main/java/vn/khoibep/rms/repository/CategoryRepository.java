package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
