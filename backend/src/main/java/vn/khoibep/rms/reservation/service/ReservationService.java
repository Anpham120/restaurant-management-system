package vn.khoibep.rms.reservation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.audit.enums.AuditAction;
import vn.khoibep.rms.audit.service.AuditService;
import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.common.realtime.RealtimeEvent;
import vn.khoibep.rms.common.realtime.RealtimeEvents;
import vn.khoibep.rms.common.util.Money;
import vn.khoibep.rms.customer.service.CustomerService;
import vn.khoibep.rms.order.dto.OrderDtos.OrderDto;
import vn.khoibep.rms.order.repository.OrderRepository;
import vn.khoibep.rms.order.repository.OrderRepository.BookingOrder;
import vn.khoibep.rms.order.service.OrderService;
import vn.khoibep.rms.payment.enums.Confirmation;
import vn.khoibep.rms.payment.repository.PaymentRepository;
import vn.khoibep.rms.payment.service.PaymentReference;
import vn.khoibep.rms.payment.service.VietQr;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ConfirmationDto;
import vn.khoibep.rms.reservation.dto.ReservationDtos.DepositInstruction;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ReservationDayDto;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ReservationDto;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ReservationRequest;
import vn.khoibep.rms.reservation.entity.Reservation;
import vn.khoibep.rms.reservation.enums.ReservationStatus;
import vn.khoibep.rms.reservation.repository.ReservationRepository;
import vn.khoibep.rms.settings.entity.RestaurantSettings;
import vn.khoibep.rms.settings.service.SettingsService;
import vn.khoibep.rms.table.entity.DiningTable;
import vn.khoibep.rms.table.repository.DiningTableRepository;

/** FR-18, BR-42: bookings, their deposits by bank transfer, and the order they open when the guests arrive. */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final ReservationRepository reservations;
    private final DiningTableRepository tables;
    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final CustomerService customerService;
    private final SettingsService settings;
    private final AuditService audit;
    private final RealtimeEvents realtime;
    private final Clock clock;

    /** Bookings of a Vietnam date by time, with every deposit still held. */
    @Transactional(readOnly = true)
    public ReservationDayDto day(LocalDate date) {
        ZoneId zone = clock.getZone();
        List<Reservation> found = reservations.findBetween(date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
        Map<Long, Long> orderOf = found.isEmpty() ? Map.of()
                : orders.findByReservationIds(found.stream().map(Reservation::getId).toList()).stream()
                        .collect(Collectors.toMap(BookingOrder::getReservationId, BookingOrder::getOrderId));
        return new ReservationDayDto(date, reservations.depositsHeld(),
                found.stream().map(r -> ReservationDto.from(r, orderOf.get(r.getId()))).toList());
    }

    @Transactional
    public ReservationDto create(ReservationRequest request, Long employeeId) {
        Reservation reservation = new Reservation(newCode(), employeeId, clock.instant());
        describe(reservation, request);
        reservations.save(reservation);
        return changed(reservation);
    }

    @Transactional
    public ReservationDto update(Long id, ReservationRequest request) {
        Reservation reservation = lock(id);
        describe(reservation, request);
        return changed(reservation);
    }

    /** FR-18.2: the confirmation as it would be sent now, and when one was last sent. */
    @Transactional(readOnly = true)
    public ConfirmationDto confirmation(Long id) {
        Reservation reservation = get(id);
        return new ConfirmationDto(confirmationText(reservation), reservation.getConfirmationSentAt());
    }

    /** FR-18.2: the text sent and when, kept as evidence of what the guest was told. */
    @Transactional
    public ConfirmationDto confirmationSent(Long id) {
        Reservation reservation = lock(id);
        reservation.confirmationSent(confirmationText(reservation), clock.instant());
        return new ConfirmationDto(reservation.getConfirmationText(), reservation.getConfirmationSentAt());
    }

    /** FR-18.3: VietQR for the deposit; the booking code is the transfer content, for the webhook to find. */
    @Transactional(readOnly = true)
    public DepositInstruction deposit(Long id) {
        Reservation reservation = get(id);
        reservation.requireDepositDue();
        RestaurantSettings bank = settings.current();
        if (!bank.hasBankAccount()) {
            throw ApiException.conflict("Nhà hàng chưa cấu hình tài khoản nhận chuyển khoản");
        }
        return new DepositInstruction(reservation.getId(), reservation.getDepositAmount(), reservation.getCode(),
                VietQr.imageUrl(bank.getBankCode(), bank.getBankAccountNo(), bank.getBankAccountName(),
                        reservation.getDepositAmount(), reservation.getCode()),
                bank.getBankCode(), bank.getBankAccountNo(), bank.getBankAccountName());
    }

    /** FR-18.3, BR-34: a manager saw the deposit in the bank app; the log keeps who said so. */
    @Transactional
    public ReservationDto confirmDeposit(Long id, Long employeeId) {
        Reservation reservation = lock(id);
        reservation.depositPaid(Confirmation.MANUAL, employeeId, clock.instant());
        audit.record(AuditAction.MANUAL_CONFIRMATION, null, "Cọc " + reservation.getCode(), null, null,
                reservation.getDepositAmount(), null);
        return changed(reservation);
    }

    /** FR-18.4: the guests arrive and sit at the planned table, or at the one given. */
    @Transactional
    public OrderDto seat(Long id, Long tableId, Long employeeId) {
        Reservation reservation = lock(id);
        Long where = tableId != null ? tableId : reservation.getTable() == null ? null : reservation.getTable().getId();
        if (where == null) {
            throw ApiException.badRequest("Chọn bàn cho khách");
        }
        reservation.seat();
        OrderDto order = orderService.openForReservation(reservation, where, employeeId);
        realtime.staffNotice(RealtimeEvent.RESERVATIONS_CHANGED);
        return order;
    }

    /** FR-18.5: cancelled or no-show; a deposit received stays held. */
    @Transactional
    public ReservationDto end(Long id, ReservationStatus outcome) {
        Reservation reservation = lock(id);
        reservation.end(outcome);
        return changed(reservation);
    }

    private void describe(Reservation reservation, ReservationRequest request) {
        DiningTable table = request.tableId() == null ? null : tables.findById(request.tableId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bàn"));
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        reservation.describe(request.guestName().trim(), request.phone().trim(), request.reservedAt(),
                request.guestCount(), table, note, request.depositAmount());
        reservation.linkCustomer(customerService.findOrCreate(request.phone(), request.guestName()));
    }

    private String confirmationText(Reservation r) {
        RestaurantSettings shop = settings.current();
        List<String> lines = new ArrayList<>();
        lines.add(shop.getName() + " xác nhận đặt bàn");
        lines.add("Mã đặt bàn: " + r.getCode());
        lines.add("Khách: " + r.getGuestName() + " · " + r.getPhone());
        lines.add("Thời gian: " + WHEN.format(r.getReservedAt().atZone(clock.getZone())) + ", " + r.getGuestCount()
                + " khách" + (r.getTable() == null ? "" : ", bàn " + r.getTable().getName()));
        if (r.getDepositAmount() > 0) {
            if (r.isDepositPaid()) {
                lines.add("Đã nhận cọc " + Money.vnd(r.getDepositAmount()) + ".");
            } else if (shop.hasBankAccount()) {
                lines.add("Tiền cọc " + Money.vnd(r.getDepositAmount()) + ": chuyển khoản tới "
                        + shop.getBankAccountName() + ", số tài khoản " + shop.getBankAccountNo() + " ("
                        + shop.getBankCode() + "), nội dung " + r.getCode() + ".");
            } else {
                lines.add("Tiền cọc " + Money.vnd(r.getDepositAmount()) + ", nội dung chuyển khoản " + r.getCode()
                        + ".");
            }
            lines.add("Tiền cọc được trừ vào hoá đơn khi quý khách tới.");
        }
        lines.add("Huỷ hoặc đổi giờ xin báo trước 24 giờ.");
        String contact = Stream.of(shop.getAddress(), shop.getPhone())
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining(" · "));
        if (!contact.isEmpty()) {
            lines.add(contact);
        }
        return String.join("\n", lines);
    }

    /** BR-42: a booking code never equals a payment code, so the webhook always knows which one it got. */
    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = PaymentReference.generate();
            if (!payments.existsByReference(code) && !reservations.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking code");
    }

    private ReservationDto changed(Reservation reservation) {
        realtime.staffNotice(RealtimeEvent.RESERVATIONS_CHANGED);
        return ReservationDto.from(reservation, null);
    }

    private Reservation get(Long id) {
        return reservations.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking"));
    }

    private Reservation lock(Long id) {
        return reservations.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking"));
    }
}
