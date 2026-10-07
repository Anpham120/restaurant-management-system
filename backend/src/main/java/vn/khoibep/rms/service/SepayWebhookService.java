package vn.khoibep.rms.service;

import java.time.Instant;
import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.realtime.RealtimeEvent;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.dto.PaymentDtos.SepayWebhookRequest;
import vn.khoibep.rms.entity.BankTransaction;
import vn.khoibep.rms.entity.Order;
import vn.khoibep.rms.entity.Payment;
import vn.khoibep.rms.entity.Reservation;
import vn.khoibep.rms.enums.Confirmation;
import vn.khoibep.rms.enums.MatchStatus;
import vn.khoibep.rms.enums.PaymentStatus;
import vn.khoibep.rms.enums.ReservationStatus;
import vn.khoibep.rms.repository.BankTransactionRepository;
import vn.khoibep.rms.repository.OrderRepository;
import vn.khoibep.rms.repository.PaymentRepository;
import vn.khoibep.rms.repository.ReservationRepository;

/**
 * BR-15: confirm a transfer only when money came in, the content holds a pending code and the amount is exact.
 * BR-16: each SePay transaction is stored once; anything that does not fit is kept as UNMATCHED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SepayWebhookService {

    /** NFR-12: transfers kept as UNMATCHED, for the monitoring server. */
    static final String UNMATCHED_METRIC = "rms.bank.transactions.unmatched";

    private final BankTransactionRepository bankTransactions;
    private final PaymentRepository payments;
    private final ReservationRepository reservations;
    private final OrderRepository orders;
    private final PaymentService paymentService;
    private final RealtimeEvents realtime;
    private final MeterRegistry meters;

    /** Registered at 0 on start, so the first unmatched transfer after a restart still counts as an increase. */
    @PostConstruct
    void registerMetrics() {
        meters.counter(UNMATCHED_METRIC);
    }

    @Transactional
    public void handle(SepayWebhookRequest request) {
        if (bankTransactions.existsByProviderTxnId(String.valueOf(request.id()))) {
            log.info("SePay transaction {} already processed", request.id());
            return;
        }
        BankTransaction tx = BankTransaction.received(String.valueOf(request.id()), request.gateway(),
                request.accountNumber(), request.transferAmount() == null ? 0 : request.transferAmount(),
                request.content(), request.code(), request.transferType());
        if (!"in".equals(tx.getTransferType())) {
            tx.resolve(MatchStatus.IGNORED, null, "Tiền ra");
            bankTransactions.save(tx);
            return;
        }
        tx.resolve(MatchStatus.UNMATCHED, null, "Không tìm thấy mã thanh toán trong nội dung");
        for (String reference : PaymentReference.candidates(request.code(), request.content())) {
            Optional<Long> orderId = payments.findOrderIdByReference(reference);
            if (orderId.isEmpty()) {
                // BR-42: a booking code is the transfer content of its deposit.
                Optional<Reservation> booking = reservations.findByCodeForUpdate(reference);
                if (booking.isEmpty()) {
                    continue;
                }
                matchDeposit(tx, booking.get());
                break;
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
            meters.counter(UNMATCHED_METRIC).increment();
        }
    }

    /** FR-18.3, BR-42: the deposit counts only when the booking still waits for it and the amount is the one asked. */
    private void matchDeposit(BankTransaction tx, Reservation booking) {
        String code = booking.getCode();
        if (booking.isDepositPaid()) {
            tx.resolveDeposit(MatchStatus.UNMATCHED, booking, "Đã nhận cọc của mã " + code + " trước đó");
        } else if (booking.getStatus() != ReservationStatus.BOOKED || booking.getDepositAmount() == 0) {
            tx.resolveDeposit(MatchStatus.UNMATCHED, booking, "Booking " + code + " không còn chờ cọc");
        } else if (booking.getDepositAmount() != tx.getAmount()) {
            tx.resolveDeposit(MatchStatus.UNMATCHED, booking,
                    "Sai số tiền cọc: cần " + booking.getDepositAmount() + ", nhận " + tx.getAmount());
        } else {
            booking.depositPaid(Confirmation.AUTO, null, Instant.now());
            tx.resolveDeposit(MatchStatus.MATCHED, booking, null);
            realtime.staffNotice(RealtimeEvent.RESERVATIONS_CHANGED);
        }
    }
}
