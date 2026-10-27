package com.restaurant.core.employee.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.employee.dto.ShiftDtos.AdjustAttendanceRequest;
import com.restaurant.core.employee.dto.ShiftDtos.AssignShiftRequest;
import com.restaurant.core.employee.dto.ShiftDtos.AttendanceAdjustmentView;
import com.restaurant.core.employee.dto.ShiftDtos.AttendanceRequest;
import com.restaurant.core.employee.dto.ShiftDtos.AttendanceResponse;
import com.restaurant.core.employee.dto.ShiftDtos.ReasonRequest;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftAssignmentResponse;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftRequest;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftResponse;
import com.restaurant.core.employee.service.ShiftService;
import com.restaurant.core.security.CurrentUser;

/** Ca làm, phân ca, chấm công. Nhân viên không phải Manager chỉ xem ca/công của chính mình. Owner: Nguyễn Quang Hiếu. */
@RestController
@RequestMapping("/api")
public class ShiftController {

    private final ShiftService service;
    private final CurrentUser currentUser;

    public ShiftController(ShiftService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/shifts")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ShiftResponse>> shifts(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.shifts(activeOnly));
    }

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ShiftResponse> createShift(@Valid @RequestBody ShiftRequest request) {
        return ApiResponse.ok(service.createShift(request));
    }

    @PutMapping("/shifts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ShiftResponse> updateShift(@PathVariable @Positive long id, @Valid @RequestBody ShiftRequest request) {
        return ApiResponse.ok(service.updateShift(id, request));
    }

    @PostMapping("/shift-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ShiftAssignmentResponse> assign(@Valid @RequestBody AssignShiftRequest request) {
        return ApiResponse.ok(service.assign(request));
    }

    /** Manager xem mọi người (lọc employeeId tùy chọn); vai trò khác luôn chỉ thấy ca của mình. */
    @GetMapping("/shift-assignments")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<ShiftAssignmentResponse>> assignments(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                  @RequestParam(required = false) @Positive Long employeeId) {
        return ApiResponse.ok(service.assignments(from, to, scope(employeeId)));
    }

    @PostMapping("/shift-assignments/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<ShiftAssignmentResponse> cancel(@PathVariable @Positive long id, @Valid @RequestBody ReasonRequest request) {
        return ApiResponse.ok(service.cancel(id, currentUser.employeeId(), request.reason()));
    }

    @PostMapping("/attendances")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<AttendanceResponse> record(@Valid @RequestBody AttendanceRequest request) {
        return ApiResponse.ok(service.record(currentUser.employeeId(), request));
    }

    @GetMapping("/attendances")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<AttendanceResponse>> attendances(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                             @RequestParam(required = false) @Positive Long employeeId) {
        return ApiResponse.ok(service.attendances(from, to, scope(employeeId)));
    }

    @PostMapping("/attendances/{id}/adjustments")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<AttendanceResponse> adjust(@PathVariable @Positive long id, @Valid @RequestBody AdjustAttendanceRequest request) {
        return ApiResponse.ok(service.adjust(id, currentUser.employeeId(), request));
    }

    @GetMapping("/attendances/{id}/adjustments")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<AttendanceAdjustmentView>> adjustments(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.adjustments(id));
    }

    private Long scope(Long requested) {
        return currentUser.roles().contains("MANAGER") ? requested : Long.valueOf(currentUser.employeeId());
    }
}
