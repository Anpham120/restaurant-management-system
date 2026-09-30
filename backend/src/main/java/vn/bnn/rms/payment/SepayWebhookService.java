package vn.bnn.rms.payment;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.common.RealtimeEvent;
import vn.bnn.rms.common.RealtimeEvents;
import vn.bnn.rms.order.Order;
import vn.bnn.rms.order.OrderRepository;
import vn.bnn.rms.payment.PaymentDtos.SepayWebhookRequest;

/**
 * BR-15: confirm a transfer only when money came in, the content holds a pending code and the amount is exact.
 * BR-16: each SePay transaction is stored once; anything that does not fit is kept as UNMATCHED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SepayWebhookService {

    private final BankTransactionRepository bankTransactions;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final PaymentService paymentService;
    private final RealtimeEvents realtime;

    @Transactional
    public void handle(SepayWebhookRequest request) {
        if (bankTransactions.existsByProviderTxnId(String.valueOf(request.id()))) {
            log.info("SePay transaction {} already processed", request.id());
            return;
        }
        BankTransaction tx = BankTransaction.from(request);
        if (!"in".equals(tx.getTransferType())) {
            tx.resolve(MatchStatus.IGNORED, null, "Tiền ra");
            bankTransactions.save(tx);
            return;
        }
        tx.resolve(MatchStatus.UNMATCHED, null, "Không tìm thấy mã thanh toán trong nội dung");
        for (String reference : PaymentReference.candidates(request.code(), request.content())) {
            Optional<Long> orderId = payments.findOrderIdByReference(reference);
            if (orderId.isEmpty()) {
                continue;
            }
            // Lock the order first, as every other payment path does, then read the payment fresh.
            Order order = orders.findByIdForUpdate(orderId.get()).orElseThrow();
            Payment payment = payments.findByReference(reference).orElseThrow();
            if (payment.getStatus() != PaymentStatus.PENDING) {
                tx.resolve(MatchStatus.UNMATCHED, payment, "Mã " + reference + " không còn chờ thanh toán");
            } else if (!order.isOpen()) {
                tx.resolve(MatchStatus.UNMATCHED, payment, "Đơn đã đóng");
            } else if (payment.getAmount() != tx.getAmount()) {
                tx.resolve(MatchStatus.UNMATCHED, payment,
                        "Sai số tiền: cần " + payment.getAmount() + ", nhận " + tx.getAmount());
            } else {
                paymentService.completeTransfer(payment, order);
                tx.resolve(MatchStatus.MATCHED, payment, null);
            }
            break;
        }
        bankTransactions.save(tx);
        if (tx.getMatchStatus() == MatchStatus.UNMATCHED) {
            realtime.staffNotice(RealtimeEvent.BANK_TRANSACTION);
        }
    }
}
