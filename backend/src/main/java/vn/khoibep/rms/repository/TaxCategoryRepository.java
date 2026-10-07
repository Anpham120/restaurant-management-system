package vn.khoibep.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.TaxCategory;

public interface TaxCategoryRepository extends JpaRepository<TaxCategory, Long> {

    List<TaxCategory> findAllByOrderByIdAsc();

    /** BR-45: a new dish without a tax category takes this one. */
    Optional<TaxCategory> findFirstByOrderByIdAsc();

    boolean existsByNameIgnoreCase(String name);
}
