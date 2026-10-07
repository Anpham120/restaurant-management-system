package vn.khoibep.rms.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import vn.khoibep.rms.enums.Confirmation;
import vn.khoibep.rms.enums.MatchStatus;
import vn.khoibep.rms.enums.PaymentMethod;
import vn.khoibep.rms.enums.PaymentStatus;
import vn.khoibep.rms.model.BankTransaction;
import vn.khoibep.rms.model.Payment;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** @param amount the part of the bill taken this time (BR-43); the whole rest when null */
    public record CashRequest(@NotNull @Min(0) @Max(1_000_000_000) Long receivedAmount,
                              @Min(0) @Max(1_000_000_000) Long amount) {
    }

    /** @param amount the part of the bill asked for (BR-43); the whole rest when null */
    public record TransferRequest(@Min(1) @Max(1_000_000_000) Long amount) {
    }

    /** @param change cash to hand back; null for transfers */
    public record PaymentDto(Long id, Long orderId, PaymentMethod method, PaymentStatus status, long amount,
                             String reference, Long receivedAmount, Long change, Confirmation confirmation,
                             Instant paidAt) {
        public static PaymentDto from(Payment p) {
            Long change = p.getReceivedAmount() == null ? null : p.getReceivedAmount() - p.getAmount();
            return new PaymentDto(p.getId(), p.getOrder().getId(), p.getMethod(), p.getStatus(), p.getAmount(),
                    p.getReference(), p.getReceivedAmount(), change, p.getConfirmation(), p.getPaidAt());
        }
    }

    /** What the cashier screen or the guest phone needs to show a VietQR code. */
    public record PaymentInstruction(Long paymentId, Long orderId, long amount, String reference,
                                     String qrImageUrl, String bankCode, String bankAccountNo,
                                     String bankAccountName) {
    }

    public record BankTransactionDto(Long id, String providerTxnId, String gateway, long amount, String content,
                                     String code, MatchStatus matchStatus, String note, Long paymentId,
                                     Instant receivedAt) {
        public static BankTransactionDto from(BankTransaction t) {
            return new BankTransactionDto(t.getId(), t.getProviderTxnId(), t.getGateway(), t.getAmount(),
                    t.getContent(), t.getCode(), t.getMatchStatus(), t.getNote(),
                    t.getPayment() == null ? null : t.getPayment().getId(), t.getReceivedAt());
        }
    }

    /**
     * BR-32: failing once SePay deliveries failed 3 times in a row.
     *
     * @param since when the current run of failures began; null while the webhook works
     */
    public record WebhookStatus(boolean failing, int failures, Instant since, String lastError) {
    }

    /** Body SePay posts to the webhook (docs.sepay.vn/tich-hop-webhooks.html). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SepayWebhookRequest(Long id, String gateway, String transactionDate, String accountNumber,
                                      String subAccount, String code, String content, String transferType,
                                      String description, Long transferAmount, Long accumulated,
                                      String referenceCode) {
    }
}
