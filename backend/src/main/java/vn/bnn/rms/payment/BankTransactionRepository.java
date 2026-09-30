package vn.bnn.rms.payment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByProviderTxnId(String providerTxnId);

    List<BankTransaction> findByMatchStatusOrderByReceivedAtDesc(MatchStatus matchStatus);
}
