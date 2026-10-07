package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /** Suppliers still supplying first. */
    List<Supplier> findAllByOrderByActiveDescNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
