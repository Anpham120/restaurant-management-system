package vn.bnn.rms.employee;

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

import vn.bnn.rms.common.CurrentUser;
import vn.bnn.rms.employee.EmployeeDtos.CreateEmployeeRequest;
import vn.bnn.rms.employee.EmployeeDtos.EmployeeDto;
import vn.bnn.rms.employee.EmployeeDtos.ResetPasswordRequest;
import vn.bnn.rms.employee.EmployeeDtos.SetActiveRequest;
import vn.bnn.rms.employee.EmployeeDtos.UpdateEmployeeRequest;

@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<EmployeeDto> list() {
        return employeeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeDto create(@Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{id}")
    public EmployeeDto update(@PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(id, request, currentUser.id());
    }

    @PatchMapping("/{id}/active")
    public EmployeeDto setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest request) {
        return employeeService.setActive(id, request.active(), currentUser.id());
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        employeeService.resetPassword(id, request.newPassword());
    }
}
