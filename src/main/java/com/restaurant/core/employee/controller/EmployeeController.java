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
import com.restaurant.core.employee.dto.request.CreateEmployeeRequest;
import com.restaurant.core.employee.dto.request.DepartmentRequest;
import com.restaurant.core.employee.dto.request.LeaveRequest;
import com.restaurant.core.employee.dto.request.ResetPasswordRequest;
import com.restaurant.core.employee.dto.request.RolesRequest;
import com.restaurant.core.employee.dto.request.UpdateEmployeeRequest;
import com.restaurant.core.employee.dto.response.DepartmentResponse;
import com.restaurant.core.employee.dto.response.EmployeeResponse;
import com.restaurant.core.employee.dto.response.RoleResponse;
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
    public ApiResponse<List<DepartmentResponse>> getAllDepartments() {
        return ApiResponse.ok(service.getAllDepartments());
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
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return ApiResponse.ok(service.getAllRoles());
    }

    @GetMapping("/employees")
    public ApiResponse<PageResponse<EmployeeResponse>> getEmployees(
            @RequestParam(required = false) @Pattern(regexp = "ACTIVE|INACTIVE|LEFT") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.getEmployees(status, page, size));
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> getEmployeeById(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.getEmployeeById(id));
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return ApiResponse.ok(service.createEmployee(request));
    }

    @PutMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> updateEmployee(@PathVariable @Positive long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return ApiResponse.ok(service.updateEmployee(id, currentUser.employeeId(), request));
    }

    @PostMapping("/employees/{id}/leave")
    public ApiResponse<EmployeeResponse> markEmployeeAsLeft(@PathVariable @Positive long id, @Valid @RequestBody LeaveRequest request) {
        return ApiResponse.ok(service.markEmployeeAsLeft(id, currentUser.employeeId(), request.endDate()));
    }

    @PutMapping("/accounts/{id}/roles")
    public ApiResponse<EmployeeResponse> updateAccountRoles(@PathVariable @Positive long id, @Valid @RequestBody RolesRequest request) {
        return ApiResponse.ok(service.updateAccountRoles(id, currentUser.employeeId(), request.roles()));
    }

    @PostMapping("/accounts/{id}/lock")
    public ApiResponse<EmployeeResponse> lockAccount(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.lockAccount(id, currentUser.employeeId()));
    }

    @PostMapping("/accounts/{id}/unlock")
    public ApiResponse<EmployeeResponse> unlockAccount(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.unlockAccount(id));
    }

    @PostMapping("/accounts/{id}/reset-password")
    public ApiResponse<EmployeeResponse> resetAccountPassword(@PathVariable @Positive long id, @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(service.resetAccountPassword(id, request.newPassword()));
    }
}
