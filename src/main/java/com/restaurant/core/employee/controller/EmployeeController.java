package com.restaurant.core.employee.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.common.response.PageResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.restaurant.core.employee.dto.EmployeeDtos.DepartmentRequest;
import com.restaurant.core.employee.dto.EmployeeDtos.DepartmentResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.EmployeeResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.LeaveRequest;
import com.restaurant.core.employee.dto.EmployeeDtos.ResetPasswordRequest;
import com.restaurant.core.employee.dto.EmployeeDtos.RoleResponse;
import com.restaurant.core.employee.dto.EmployeeDtos.RolesRequest;
import com.restaurant.core.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.restaurant.core.employee.service.EmployeeService;
import com.restaurant.core.security.CurrentUser;

/** Nhân viên, tài khoản, vai trò, phòng ban — chỉ MANAGER. Owner: Nguyễn Quang Hiếu. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MANAGER')")
public class EmployeeController {

    private final EmployeeService service;
    private final CurrentUser currentUser;

    public EmployeeController(EmployeeService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/departments")
    public ApiResponse<List<DepartmentResponse>> departments() {
        return ApiResponse.ok(service.departments());
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.ok(service.createDepartment(request));
    }

    @PutMapping("/departments/{id}")
    public ApiResponse<DepartmentResponse> updateDepartment(@PathVariable @Positive long id, @Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.ok(service.updateDepartment(id, request));
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.ok(service.roles());
    }

    @GetMapping("/employees")
    public ApiResponse<PageResponse<EmployeeResponse>> employees(
            @RequestParam(required = false) @Pattern(regexp = "ACTIVE|INACTIVE|LEFT") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.employees(status, page, size));
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> employee(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.employee(id));
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> update(@PathVariable @Positive long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return ApiResponse.ok(service.update(id, currentUser.employeeId(), request));
    }

    @PostMapping("/employees/{id}/leave")
    public ApiResponse<EmployeeResponse> leave(@PathVariable @Positive long id, @Valid @RequestBody LeaveRequest request) {
        return ApiResponse.ok(service.leave(id, currentUser.employeeId(), request.endDate()));
    }

    @PutMapping("/accounts/{id}/roles")
    public ApiResponse<EmployeeResponse> roles(@PathVariable @Positive long id, @Valid @RequestBody RolesRequest request) {
        return ApiResponse.ok(service.replaceRoles(id, currentUser.employeeId(), request.roles()));
    }

    @PostMapping("/accounts/{id}/lock")
    public ApiResponse<EmployeeResponse> lock(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.lock(id, currentUser.employeeId()));
    }

    @PostMapping("/accounts/{id}/unlock")
    public ApiResponse<EmployeeResponse> unlock(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.unlock(id));
    }

    @PostMapping("/accounts/{id}/reset-password")
    public ApiResponse<EmployeeResponse> resetPassword(@PathVariable @Positive long id, @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(service.resetPassword(id, request.newPassword()));
    }
}
