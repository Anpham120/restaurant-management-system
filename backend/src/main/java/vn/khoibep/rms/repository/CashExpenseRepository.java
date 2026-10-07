package vn.khoibep.rms.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.CashExpense;

public interface CashExpenseRepository extends JpaRepository<CashExpense, Long> {

    /** Oldest first, with who paid each out. */
    @Query("""
            select e from CashExpense e
            join fetch e.createdBy
            where e.shift.id in :shiftIds
            order by e.createdAt, e.id""")
    List<CashExpense> findByShiftIds(@Param("shiftIds") Collection<Long> shiftIds);
}
