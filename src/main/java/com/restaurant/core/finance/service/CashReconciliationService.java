package com.restaurant.core.finance.service;

import java.time.LocalDate;

import com.restaurant.core.finance.dto.CashReconciliationResponse;
import com.restaurant.core.finance.dto.ReconcileRequest;

/** Đối soát tiền mặt cuối ngày (Quản lý). Mỗi lần lập lại là một phiên bản mới. Owner: Bùi Đào Đức Anh. */
public interface CashReconciliationService {

    /** Lập biên bản: tự tính thu tiền mặt và chi từ két của ngày; lệch tiền phải có lý do, lập lại phải có lý do lập lại. */
    CashReconciliationResponse reconcileCashForDay(long actorId, ReconcileRequest request);

    CashReconciliationResponse getCashReconciliation(LocalDate businessDate);
}
