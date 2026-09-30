package vn.bnn.rms.payment;

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

import vn.bnn.rms.common.CurrentUser;
import vn.bnn.rms.payment.PaymentDtos.BankTransactionDto;
import vn.bnn.rms.payment.PaymentDtos.CashRequest;
import vn.bnn.rms.payment.PaymentDtos.PaymentDto;
import vn.bnn.rms.payment.PaymentDtos.PaymentInstruction;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CASHIER')")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
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

    @GetMapping("/bank-transactions")
    public List<BankTransactionDto> bankTransactions(
            @RequestParam(defaultValue = "UNMATCHED") MatchStatus status) {
        return paymentService.bankTransactions(status);
    }
}
