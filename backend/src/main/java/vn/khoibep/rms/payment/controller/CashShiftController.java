package vn.khoibep.rms.payment.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.payment.dto.CashShiftDtos.CashShiftDto;
import vn.khoibep.rms.payment.dto.CashShiftDtos.CloseShiftRequest;
import vn.khoibep.rms.payment.dto.CashShiftDtos.ExpenseRequest;
import vn.khoibep.rms.payment.dto.CashShiftDtos.OpenShiftRequest;
import vn.khoibep.rms.payment.service.CashShiftService;

/** FR-17. There is no API to change or delete a shift or a cash expense (BR-39). */
@RestController
@RequestMapping("/api/cash-shifts")
@PreAuthorize("hasRole('CASHIER')")
@RequiredArgsConstructor
public class CashShiftController {

    private final CashShiftService cashShiftService;
    private final CurrentUser currentUser;

    /** 204 when no shift is open. */
    @GetMapping("/current")
    public ResponseEntity<CashShiftDto> current() {
        return cashShiftService.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CashShiftDto open(@Valid @RequestBody OpenShiftRequest request) {
        return cashShiftService.open(request.openingFloat(), currentUser.id());
    }

    @PostMapping("/current/expenses")
    public CashShiftDto addExpense(@Valid @RequestBody ExpenseRequest request) {
        return cashShiftService.addExpense(request.amount(), request.reason(), currentUser.id());
    }

    @PostMapping("/current/close")
    public CashShiftDto close(@Valid @RequestBody CloseShiftRequest request) {
        return cashShiftService.close(request.countedCash(), request.note(), currentUser.id());
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public List<CashShiftDto> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return cashShiftService.list(from, to);
    }
}
