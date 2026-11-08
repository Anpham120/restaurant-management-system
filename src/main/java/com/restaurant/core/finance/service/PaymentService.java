package com.restaurant.core.finance.service;

import com.restaurant.core.finance.dto.response.InvoiceResponse;
import com.restaurant.core.finance.dto.response.PaymentResponse;
import com.restaurant.core.finance.dto.request.StartPaymentRequest;

/** Thu tiền một hóa đơn đang chờ thu. Cách thu cụ thể nằm ở các PaymentStrategy (tiền mặt, QR). Owner: Phạm Duy An. */
public interface PaymentService {

    PaymentResponse startPayment(long invoiceId, long actorId, StartPaymentRequest request);

    InvoiceResponse cancelPaymentRequest(long requestId, long actorId, String reason);
}
