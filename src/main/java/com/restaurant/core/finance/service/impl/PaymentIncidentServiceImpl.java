package com.restaurant.core.finance.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.finance.dto.request.ManualTransferRequest;
import com.restaurant.core.finance.dto.response.ManualTransferResponse;
import com.restaurant.core.finance.dto.response.PaymentIncidentResponse;
import com.restaurant.core.finance.entity.PaymentRequest;
import com.restaurant.core.finance.entity.PaymentWebhookEvent;
import com.restaurant.core.finance.repository.PaymentIncidentQueryRepository;
import com.restaurant.core.finance.repository.PaymentRequestRepository;
import com.restaurant.core.finance.repository.PaymentWebhookEventRepository;
import com.restaurant.core.finance.service.InvoiceService;
import com.restaurant.core.finance.service.PaymentIncidentService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentIncidentServiceImpl implements PaymentIncidentService {

    private final PaymentIncidentQueryRepository incidentQueryRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final QrTransferProcessor qrTransferProcessor;
    private final InvoiceService invoiceService;
    private final JsonMapper json;
    private final BusinessDateProvider clock;

    @Override
    public PageResponse<PaymentIncidentResponse> getAllPaymentIncidents(String status, int page, int size) {
        return new PageResponse<>(incidentQueryRepository.findIncidents(status, page, size), page, size,
                incidentQueryRepository.countIncidents(status));
    }

    @Override
    public PaymentIncidentResponse getPaymentIncidentById(long incidentId) {
        return incidentQueryRepository.findIncident(incidentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sự cố thanh toán " + incidentId));
    }

    @Override
    @Transactional
    public PaymentIncidentResponse resolvePaymentIncident(long incidentId, long actorId, String note) {
        PaymentWebhookEvent event = webhookEventRepository.findByIdForUpdate(incidentId)
                .filter(e -> e.getResolutionStatus() != null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sự cố thanh toán " + incidentId));
        if (!event.isOpenIncident()) {
            throw ApiException.invalidState("Sự cố " + incidentId + " đã được xử lý");
        }
        event.resolve(actorId, note.strip(), clock.now());
        webhookEventRepository.flush();
        return getPaymentIncidentById(incidentId);
    }

    /**
     * Đi qua đúng QrTransferProcessor như webhook (khóa mã giao dịch, đối chiếu số tiền và trạng thái), chỉ khác nguồn:
     * payload ghi rõ "MANUAL" và người ghi nhận. Lệch tiền hay yêu cầu đã hủy thì mở sự cố như webhook thật.
     */
    @Override
    @Transactional
    public ManualTransferResponse recordManualTransfer(long paymentRequestId, long actorId, ManualTransferRequest request) {
        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu thanh toán " + paymentRequestId));
        if (paymentRequest.getPaymentReference() == null) {
            throw ApiException.invalidState("Yêu cầu thanh toán " + paymentRequestId + " không phải chuyển khoản QR");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "MANUAL");
        payload.put("recordedById", actorId);
        payload.put("transactionCode", request.transactionCode().strip());
        payload.put("transferAmount", request.amount());
        payload.put("note", request.note().strip());
        String result = qrTransferProcessor.recordVerifiedTransfer(request.transactionCode().strip(), paymentRequest.getPaymentReference(),
                request.amount(), true, json.writeValueAsString(payload)).name();
        return new ManualTransferResponse(result, invoiceService.getInvoiceById(paymentRequest.getInvoiceId()));
    }
}
