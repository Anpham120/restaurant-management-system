package com.restaurant.core.finance.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.restaurant.core.finance.dto.InvoiceResponse;
import com.restaurant.core.finance.dto.PaymentRequest;
import com.restaurant.core.finance.dto.PaymentResponse;
import com.restaurant.core.finance.repository.PaymentRepository;
import com.restaurant.core.finance.strategy.PaymentMethod;
import com.restaurant.core.finance.strategy.PaymentStrategy;

/** Chọn strategy theo phương thức thanh toán. Không chứa logic tiền — xem PaymentStrategy. Owner: Phạm Duy An. */
@Service
public class PaymentService {

    private final Map<PaymentMethod, PaymentStrategy> strategies = new EnumMap<>(PaymentMethod.class);
    private final PaymentRepository payments;
    private final InvoiceService invoices;

    public PaymentService(List<PaymentStrategy> all, PaymentRepository payments, InvoiceService invoices) {
        this.payments = payments;
        this.invoices = invoices;
        for (PaymentStrategy s : all) {
            if (strategies.put(s.method(), s) != null) {
                throw new IllegalStateException("Trùng PaymentStrategy cho " + s.method());
            }
        }
    }

    public PaymentResponse start(long invoiceId, long actorId, PaymentRequest request) {
        PaymentStrategy strategy = strategies.get(request.method());
        if (strategy == null) {
            throw new IllegalStateException("Chưa có PaymentStrategy cho " + request.method());
        }
        return strategy.start(invoiceId, actorId, request);
    }

    public InvoiceResponse cancelRequest(long requestId, long actorId, String reason) {
        payments.cancelRequest(requestId, actorId, reason);
        return invoices.get(payments.findInvoiceOfRequest(requestId));
    }
}
