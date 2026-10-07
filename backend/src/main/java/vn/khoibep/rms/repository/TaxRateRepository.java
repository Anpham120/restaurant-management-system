package vn.khoibep.rms.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.model.TaxRate;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    /** Every rate, a few per category: read whole for a bill or the tax page. */
    List<TaxRate> findAllByOrderByEffectiveFromAsc();

    Optional<TaxRate> findByCategoryIdAndEffectiveFrom(Long categoryId, LocalDate effectiveFrom);
}
