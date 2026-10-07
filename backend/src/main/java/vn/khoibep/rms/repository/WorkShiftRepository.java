package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.WorkShift;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    List<WorkShift> findAllByOrderByStartTimeAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
