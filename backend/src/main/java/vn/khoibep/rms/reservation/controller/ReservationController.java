package vn.khoibep.rms.reservation.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.order.dto.OrderDtos.OrderDto;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ConfirmationDto;
import vn.khoibep.rms.reservation.dto.ReservationDtos.DepositInstruction;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ReservationDayDto;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ReservationDto;
import vn.khoibep.rms.reservation.dto.ReservationDtos.ReservationRequest;
import vn.khoibep.rms.reservation.dto.ReservationDtos.SeatRequest;
import vn.khoibep.rms.reservation.enums.ReservationStatus;
import vn.khoibep.rms.reservation.service.ReservationService;

/** FR-18. Waiters and managers take bookings; only a manager confirms a deposit by hand (BR-42). */
@RestController
@RequestMapping("/api/reservations")
@PreAuthorize("hasRole('WAITER')")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final CurrentUser currentUser;

    @GetMapping
    public ReservationDayDto day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reservationService.day(date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDto create(@Valid @RequestBody ReservationRequest request) {
        return reservationService.create(request, currentUser.id());
    }

    @PutMapping("/{id}")
    public ReservationDto update(@PathVariable Long id, @Valid @RequestBody ReservationRequest request) {
        return reservationService.update(id, request);
    }

    @GetMapping("/{id}/confirmation")
    public ConfirmationDto confirmation(@PathVariable Long id) {
        return reservationService.confirmation(id);
    }

    @PostMapping("/{id}/confirmation")
    public ConfirmationDto confirmationSent(@PathVariable Long id) {
        return reservationService.confirmationSent(id);
    }

    @PostMapping("/{id}/deposit")
    public DepositInstruction deposit(@PathVariable Long id) {
        return reservationService.deposit(id);
    }

    @PostMapping("/{id}/deposit/confirm")
    @PreAuthorize("hasRole('MANAGER')")
    public ReservationDto confirmDeposit(@PathVariable Long id) {
        return reservationService.confirmDeposit(id, currentUser.id());
    }

    @PostMapping("/{id}/seat")
    public OrderDto seat(@PathVariable Long id, @RequestBody(required = false) SeatRequest request) {
        return reservationService.seat(id, request == null ? null : request.tableId(), currentUser.id());
    }

    @PostMapping("/{id}/cancel")
    public ReservationDto cancel(@PathVariable Long id) {
        return reservationService.end(id, ReservationStatus.CANCELLED);
    }

    @PostMapping("/{id}/no-show")
    public ReservationDto noShow(@PathVariable Long id) {
        return reservationService.end(id, ReservationStatus.NO_SHOW);
    }
}
