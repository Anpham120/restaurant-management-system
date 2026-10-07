package vn.khoibep.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.khoibep.rms.entity.BankTransaction;
import vn.khoibep.rms.enums.MatchStatus;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByProviderTxnId(String providerTxnId);

    List<BankTransaction> findByMatchStatusOrderByReceivedAtDesc(MatchStatus matchStatus);
}
