package vn.khoibep.rms.payment.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.payment.dto.PaymentDtos.BankTransactionDto;
import vn.khoibep.rms.payment.dto.PaymentDtos.CashRequest;
import vn.khoibep.rms.payment.dto.PaymentDtos.PaymentDto;
import vn.khoibep.rms.payment.dto.PaymentDtos.PaymentInstruction;
import vn.khoibep.rms.payment.dto.PaymentDtos.WebhookStatus;
import vn.khoibep.rms.payment.enums.MatchStatus;
import vn.khoibep.rms.payment.service.PaymentService;
import vn.khoibep.rms.payment.service.SepayWebhookMonitor;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CASHIER')")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final SepayWebhookMonitor webhookMonitor;
    private final CurrentUser currentUser;

    @PostMapping("/orders/{id}/payments/cash")
    public PaymentDto payCash(@PathVariable Long id, @Valid @RequestBody CashRequest request) {
        return paymentService.payCash(id, request.receivedAmount(), currentUser.id());
    }

    @PostMapping("/orders/{id}/payments/transfer")
    public PaymentInstruction requestTransfer(@PathVariable Long id) {
        return paymentService.requestTransfer(id);
    }

    @PostMapping("/payments/{id}/confirm")
    public PaymentDto confirmManually(@PathVariable Long id) {
        return paymentService.confirmManually(id, currentUser.id());
    }

    /** FR-08.9: for the receipt; 404 until the order is paid. */
    @GetMapping("/orders/{id}/payment")
    public PaymentDto paidPayment(@PathVariable Long id) {
        return paymentService.paidPayment(id);
    }

    @GetMapping("/bank-transactions")
    public List<BankTransactionDto> bankTransactions(
            @RequestParam(defaultValue = "UNMATCHED") MatchStatus status) {
        return paymentService.bankTransactions(status);
    }

    /** BR-32: whether SePay deliveries keep failing, for the warning on the cashier screen. */
    @GetMapping("/bank-transactions/webhook-status")
    public WebhookStatus webhookStatus() {
        return webhookMonitor.status();
    }
}
