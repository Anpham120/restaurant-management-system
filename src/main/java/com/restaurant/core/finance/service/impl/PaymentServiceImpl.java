package com.restaurant.core.finance.service.impl;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.dto.InvoiceResponse;
import com.restaurant.core.finance.dto.PaymentResponse;
import com.restaurant.core.finance.dto.StartPaymentRequest;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.repository.PaymentRequestRepository;
import com.restaurant.core.finance.service.InvoiceService;
import com.restaurant.core.finance.service.PaymentService;
import com.restaurant.core.finance.strategy.PaymentMethod;
import com.restaurant.core.finance.strategy.PaymentStrategy;

/** Chọn PaymentStrategy theo phương thức thanh toán; hủy yêu cầu thanh toán đang mở. */
@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private final Map<PaymentMethod, PaymentStrategy> strategies = new EnumMap<>(PaymentMethod.class);
    private final PaymentRequestRepository paymentRequestRepository;
    private final InvoiceLocking invoiceLocking;
    private final InvoiceService invoiceService;
    private final BusinessDateProvider clock;

    public PaymentServiceImpl(List<PaymentStrategy> all, PaymentRequestRepository paymentRequestRepository, InvoiceLocking invoiceLocking,
                              InvoiceService invoiceService, BusinessDateProvider clock) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.invoiceLocking = invoiceLocking;
        this.invoiceService = invoiceService;
        this.clock = clock;
        for (PaymentStrategy s : all) {
            if (strategies.put(s.method(), s) != null) {
                throw new IllegalStateException("Trùng PaymentStrategy cho " + s.method());
            }
        }
    }

    @Override
    @Transactional
    public PaymentResponse startPayment(long invoiceId, long actorId, StartPaymentRequest request) {
        PaymentStrategy strategy = strategies.get(request.method());
        if (strategy == null) {
            throw new IllegalStateException("Chưa có PaymentStrategy cho " + request.method());
        }
        return strategy.startPayment(invoiceId, actorId, request);
    }

    @Override
    @Transactional
    public InvoiceResponse cancelPaymentRequest(long requestId, long actorId, String reason) {
        String cleanReason = InvoiceServiceImpl.requireReason(reason, "Hủy yêu cầu thanh toán bắt buộc có lý do");
        long invoiceId = paymentRequestRepository.findInvoiceIdOfRequest(requestId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu thanh toán " + requestId));
        invoiceLocking.lockInvoiceById(invoiceId);
        PaymentRequest paymentRequest = paymentRequestRepository.findByIdForUpdate(requestId).orElseThrow();
        if (!paymentRequest.isOpen()) {
            throw ApiException.invalidState("Yêu cầu thanh toán " + requestId + " đang " + paymentRequest.getStatus() + ", không còn mở");
        }
        paymentRequest.cancel(cleanReason, actorId, clock.now());
        paymentRequestRepository.flush();
        return invoiceService.getInvoiceById(invoiceId);
    }
}
