package vn.khoibep.rms.payroll.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.payroll.dto.PayrollDtos.AdjustmentRequest;
import vn.khoibep.rms.payroll.dto.PayrollDtos.CreatePayrollRequest;
import vn.khoibep.rms.payroll.dto.PayrollDtos.MyPayslipDto;
import vn.khoibep.rms.payroll.dto.PayrollDtos.PayrollDetailDto;
import vn.khoibep.rms.payroll.dto.PayrollDtos.PayrollSummaryDto;
import vn.khoibep.rms.payroll.dto.PayrollDtos.PayslipDto;
import vn.khoibep.rms.payroll.dto.PayrollDtos.RecalculateRequest;
import vn.khoibep.rms.payroll.service.PayrollService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final CurrentUser currentUser;

    // ---- ADMIN (BR-22: pay is for ADMIN only) ----------------------------------------------------------

    @GetMapping("/payrolls")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PayrollSummaryDto> list() {
        return payrollService.list();
    }

    @PostMapping("/payrolls")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollDetailDto create(@Valid @RequestBody CreatePayrollRequest request) {
        return payrollService.create(request, currentUser.id());
    }

    @GetMapping("/payrolls/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PayrollDetailDto get(@PathVariable Long id) {
        return payrollService.get(id);
    }

    @PostMapping("/payrolls/{id}/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    public PayrollDetailDto recalculate(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) RecalculateRequest request) {
        return payrollService.recalculate(id, request == null ? null : request.standardDays());
    }

    @PostMapping("/payrolls/{id}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public PayrollDetailDto finalizePayroll(@PathVariable Long id) {
        return payrollService.finalizePayroll(id, currentUser.id());
    }

    @PostMapping("/payslips/{id}/adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PayslipDto addAdjustment(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest request) {
        return payrollService.addAdjustment(id, request, currentUser.id());
    }

    @DeleteMapping("/pay-adjustments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PayslipDto removeAdjustment(@PathVariable Long id) {
        return payrollService.removeAdjustment(id);
    }

    // ---- Any signed-in employee, own payslips only -----------------------------------------------------

    @GetMapping("/me/payslips")
    public List<MyPayslipDto> mine() {
        return payrollService.mine(currentUser.id());
    }

    @GetMapping("/me/payslips/{id}")
    public PayslipDto myPayslip(@PathVariable Long id) {
        return payrollService.myPayslip(id, currentUser.id());
    }
}
