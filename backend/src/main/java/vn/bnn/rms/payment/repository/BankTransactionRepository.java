package vn.bnn.rms.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.bnn.rms.payment.entity.BankTransaction;
import vn.bnn.rms.payment.enums.MatchStatus;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByProviderTxnId(String providerTxnId);

    List<BankTransaction> findByMatchStatusOrderByReceivedAtDesc(MatchStatus matchStatus);
}
