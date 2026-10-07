package vn.khoibep.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.khoibep.rms.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** The phone must be normalised already (BR-44). */
    Optional<Customer> findByPhone(String phone);

    List<Customer> findByOrderByCreatedAtDesc(Pageable page);

    /** Any part of the phone number, or of the name in lower case; the newest guests first. */
    @Query("""
            select c from Customer c
            where (:digits <> '' and c.phone like concat('%', :digits, '%'))
               or lower(c.name) like concat('%', :text, '%')
            order by c.createdAt desc""")
    List<Customer> search(@Param("digits") String digits, @Param("text") String text, Pageable page);
}
