package com.restaurant.core.finance.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.restaurant.core.finance.dto.CashReconciliationResponse;
import com.restaurant.core.finance.dto.ReconcileRequest;
import com.restaurant.core.finance.repository.CashReconciliationRepository;

/** Đối soát tiền mặt cuối ngày; mỗi lần lập lại là một phiên bản mới (bản cũ SUPERSEDED). Owner: Bùi Đào Đức Anh. */
@Service
public class CashReconciliationService {

    private final CashReconciliationRepository repo;

    public CashReconciliationService(CashReconciliationRepository repo) {
        this.repo = repo;
    }

    public CashReconciliationResponse reconcile(long actorId, ReconcileRequest r) {
        repo.reconcile(r.businessDate(), r.openingCash(), r.actualCash(), actorId, blank(r.reason()), blank(r.revisionReason()));
        return get(r.businessDate());
    }

    public CashReconciliationResponse get(LocalDate date) {
        CashReconciliationResponse.Summary s = repo.summary(date);
        return new CashReconciliationResponse(s.businessDate(), s.openingCash(), s.cashSales(), s.cashExpenses(), s.expectedCash(),
                s.actualCash(), s.difference(), s.reason(), s.reconVersion(), s.reconciledById(), s.stale(), repo.versions(date));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
