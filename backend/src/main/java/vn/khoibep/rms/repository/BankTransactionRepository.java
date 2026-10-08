package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.enums.MatchStatus;
import vn.khoibep.rms.model.BankTransaction;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByProviderTxnId(String providerTxnId);

    /** Newest first; the id breaks ties between transactions received in the same instant. */
    List<BankTransaction> findByMatchStatusOrderByReceivedAtDescIdDesc(MatchStatus matchStatus, Pageable page);
}
