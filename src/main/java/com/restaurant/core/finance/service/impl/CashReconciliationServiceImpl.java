package com.restaurant.core.finance.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.dto.response.CashReconciliationResponse;
import com.restaurant.core.finance.dto.request.ReconcileRequest;
import com.restaurant.core.finance.entity.DailyCashReconciliation;
import com.restaurant.core.finance.repository.CashReconciliationQueryRepository;
import com.restaurant.core.finance.repository.DailyCashReconciliationRepository;
import com.restaurant.core.finance.service.CashReconciliationService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashReconciliationServiceImpl implements CashReconciliationService {

    private final DailyCashReconciliationRepository reconciliationRepository;
    private final CashReconciliationQueryRepository reconciliationQueryRepository;
    private final BusinessDateProvider clock;

    @Override
    @Transactional
    public CashReconciliationResponse reconcileCashForDay(long actorId, ReconcileRequest request) {
        LocalDate day = request.businessDate();
        if (day == null || day.isAfter(clock.today())) {
            throw ApiException.invalidRequest("Ngày kinh doanh không hợp lệ hoặc ở tương lai");
        }
        if (request.openingCash() == null || request.actualCash() == null) {
            throw ApiException.invalidRequest("Thiếu quỹ đầu ngày hoặc số tiền thực đếm");
        }
        reconciliationRepository.lockBusinessDate(day);
        int nextVersion = reconciliationRepository.findLatestVersion(day) + 1;
        String reason = blankToNull(request.reason());
        String revisionReason = blankToNull(request.revisionReason());
        if (nextVersion > 1 && revisionReason == null) {
            throw ApiException.invalidRequest("Ngày " + day + " đã đối soát: lập lại phải ghi lý do lập lại");
        }
        BigDecimal cashSales = reconciliationRepository.sumCashSales(day);
        BigDecimal cashExpenses = reconciliationRepository.sumCashDrawerExpenses(day);
        BigDecimal expected = DailyCashReconciliation.calculateExpectedCash(request.openingCash(), cashSales, cashExpenses);
        if (request.actualCash().compareTo(expected) != 0 && reason == null) {
            throw ApiException.invalidRequest("Tiền thực đếm lệch " + request.actualCash().subtract(expected).toPlainString()
                    + " so với sổ: phải ghi lý do");
        }
        reconciliationRepository.findByBusinessDateAndStatus(day, DailyCashReconciliation.CURRENT).ifPresent(current -> {
            current.supersede();
            reconciliationRepository.flush();
        });
        reconciliationRepository.saveAndFlush(new DailyCashReconciliation(day, nextVersion, request.openingCash(), cashSales, cashExpenses,
                request.actualCash(), reason, revisionReason, actorId, clock.now()));
        return getCashReconciliation(day);
    }

    @Override
    public CashReconciliationResponse getCashReconciliation(LocalDate businessDate) {
        CashReconciliationResponse.Summary s = buildSummary(businessDate);
        return new CashReconciliationResponse(s.businessDate(), s.openingCash(), s.cashSales(), s.cashExpenses(), s.expectedCash(),
                s.actualCash(), s.difference(), s.reason(), s.reconVersion(), s.reconciledById(), s.stale(),
                reconciliationQueryRepository.versions(businessDate));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /**
     * Số liệu TƯƠI của ngày (thu tiền mặt, chi từ két) đặt cạnh biên bản hiện hành. stale = biên bản đã lập lệch số
     * tươi (phát sinh thu/chi sau khi đối soát → cần lập phiên bản mới).
     */
    private CashReconciliationResponse.Summary buildSummary(LocalDate day) {
        BigDecimal liveSales = reconciliationRepository.sumCashSales(day);
        BigDecimal liveExpenses = reconciliationRepository.sumCashDrawerExpenses(day);
        DailyCashReconciliation current = reconciliationRepository.findByBusinessDateAndStatus(day, DailyCashReconciliation.CURRENT).orElse(null);
        if (current == null) {
            return new CashReconciliationResponse.Summary(day, null, liveSales, liveExpenses, null, null, null, null, null, null, false);
        }
        BigDecimal expected = DailyCashReconciliation.calculateExpectedCash(current.getOpeningCash(), liveSales, liveExpenses);
        boolean stale = current.getCashSales().compareTo(liveSales) != 0 || current.getCashExpenses().compareTo(liveExpenses) != 0;
        return new CashReconciliationResponse.Summary(day, current.getOpeningCash(), liveSales, liveExpenses, expected, current.getActualCash(),
                current.getActualCash().subtract(expected), current.getReason(), current.getVersion(), current.getReconciledById(), stale);
    }
}
