package vn.khoibep.rms.employee.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import vn.khoibep.rms.employee.dto.EmployeeDtos.EmployeeDetailDto;
import vn.khoibep.rms.employee.dto.EmployeeDtos.ProfileRequest;
import vn.khoibep.rms.employee.dto.EmployeeDtos.ResetPasswordRequest;
import vn.khoibep.rms.employee.dto.EmployeeDtos.ResignRequest;
import vn.khoibep.rms.employee.dto.EmployeeDtos.SetActiveRequest;
import vn.khoibep.rms.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import vn.khoibep.rms.employee.service.EmployeeService;

@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<EmployeeDetailDto> list() {
        return employeeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDetailDto create(@Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{id}")
    public EmployeeDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(id, request, currentUser.id());
    }

    @PutMapping("/{id}/profile")
    public EmployeeDetailDto updateProfile(@PathVariable Long id, @Valid @RequestBody ProfileRequest request) {
        return employeeService.updateProfile(id, request);
    }

    @PostMapping("/{id}/resign")
    public EmployeeDetailDto resign(@PathVariable Long id, @Valid @RequestBody ResignRequest request) {
        return employeeService.resign(id, request.leftOn(), currentUser.id());
    }

    @PatchMapping("/{id}/active")
    public EmployeeDetailDto setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest request) {
        return employeeService.setActive(id, request.active(), currentUser.id());
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        employeeService.resetPassword(id, request.newPassword());
    }
}
