package vn.khoibep.rms.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.payment.entity.BankTransaction;
import vn.khoibep.rms.payment.enums.MatchStatus;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByProviderTxnId(String providerTxnId);

    List<BankTransaction> findByMatchStatusOrderByReceivedAtDesc(MatchStatus matchStatus);
}
