package com.restaurant.core.finance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Giao dịch chuyển khoản không ghi nhận được (MISMATCH: lệch tiền, mã lạ, sai tài khoản nhận; AFTER_CANCEL: QR đã hủy
 * hoặc hóa đơn đã kết thúc). invoiceId/amountDue null khi không tìm được yêu cầu thanh toán theo mã tham chiếu.
 * rawPayload chỉ có ở API chi tiết.
 */
public record PaymentIncidentResponse(long id, String transactionCode, String paymentReference, BigDecimal amount,
                                      String processingResult, Instant receivedAt, Long paymentRequestId, Long invoiceId,
                                      String paymentRequestStatus, BigDecimal amountDue, String resolutionStatus,
                                      Long resolvedById, String resolvedByName, Instant resolvedAt, String resolutionNote,
                                      String rawPayload) {
}
