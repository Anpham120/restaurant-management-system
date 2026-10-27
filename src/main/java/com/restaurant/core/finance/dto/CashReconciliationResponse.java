package com.restaurant.core.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đối soát tiền mặt một ngày: số thu/chi tính lại tại thời điểm xem (rpt_cash_reconciliation). stale = có thu/chi
 * phát sinh sau lần đối soát hiện hành → cần đối soát lại. Chưa đối soát thì các trường của bản đối soát là null.
 */
public record CashReconciliationResponse(LocalDate businessDate, BigDecimal openingCash, BigDecimal cashSales, BigDecimal cashExpenses,
                                         BigDecimal expectedCash, BigDecimal actualCash, BigDecimal difference, String reason,
                                         Integer reconVersion, Long reconciledById, boolean stale, List<Version> versions) {

    public record Summary(LocalDate businessDate, BigDecimal openingCash, BigDecimal cashSales, BigDecimal cashExpenses,
                          BigDecimal expectedCash, BigDecimal actualCash, BigDecimal difference, String reason, Integer reconVersion,
                          Long reconciledById, boolean stale) {
    }

    /** Lịch sử phiên bản (số chụp lúc lập). */
    public record Version(long id, int version, String status, BigDecimal openingCash, BigDecimal cashSales, BigDecimal cashExpenses,
                          BigDecimal expectedCash, BigDecimal actualCash, BigDecimal difference, String reason, String revisionReason,
                          long reconciledById, Instant reconciledAt) {
    }
}
