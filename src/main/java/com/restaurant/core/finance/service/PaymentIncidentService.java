package com.restaurant.core.finance.service;

import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.finance.dto.request.ManualTransferRequest;
import com.restaurant.core.finance.dto.response.ManualTransferResponse;
import com.restaurant.core.finance.dto.response.PaymentIncidentResponse;

/**
 * Đối soát sự cố thanh toán QR: khách chuyển thiếu/thừa, chuyển sau khi hủy QR, chuyển nhầm tài khoản (MISMATCH,
 * AFTER_CANCEL), và ghi nhận tay khi webhook SePay bị mất. Owner: Phạm Duy An.
 */
public interface PaymentIncidentService {

    /** status: OPEN (mặc định ở controller), RESOLVED, hoặc null = tất cả. Mới nhất trước. */
    PageResponse<PaymentIncidentResponse> getAllPaymentIncidents(String status, int page, int size);

    PaymentIncidentResponse getPaymentIncidentById(long incidentId);

    /** OPEN → RESOLVED kèm người xử lý, thời điểm, ghi chú. Đã RESOLVED → 409. */
    PaymentIncidentResponse resolvePaymentIncident(long incidentId, long actorId, String note);

    /** Webhook bị mất: Quản lý đối chiếu sao kê rồi ghi nhận tay giao dịch cho yêu cầu QR đang chờ. */
    ManualTransferResponse recordManualTransfer(long paymentRequestId, long actorId, ManualTransferRequest request);
}
