package vn.bnn.rms.attendance.controller;

import java.time.LocalDate;
import java.util.List;

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

import vn.bnn.rms.attendance.dto.AttendanceDtos.AddAttendanceRequest;
import vn.bnn.rms.attendance.dto.AttendanceDtos.AttendanceDto;
import vn.bnn.rms.attendance.dto.AttendanceDtos.ClockStatusDto;
import vn.bnn.rms.attendance.dto.AttendanceDtos.EditAttendanceRequest;
import vn.bnn.rms.attendance.service.AttendanceService;
import vn.bnn.rms.common.security.CurrentUser;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CurrentUser currentUser;

    // ---- Any signed-in employee, for themselves -------------------------------------------------------

    @GetMapping("/me/attendance/status")
    public ClockStatusDto status() {
        return attendanceService.status(currentUser.id());
    }

    @PostMapping("/me/attendance/check-in")
    public AttendanceDto checkIn() {
        return attendanceService.checkIn(currentUser.id());
    }

    @PostMapping("/me/attendance/check-out")
    public AttendanceDto checkOut() {
        return attendanceService.checkOut(currentUser.id());
    }

    @GetMapping("/me/attendance")
    public List<AttendanceDto> mine(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return attendanceService.mine(currentUser.id(), from, to);
    }

    // ---- Managers --------------------------------------------------------------------------------------

    @GetMapping("/attendance")
    @PreAuthorize("hasRole('MANAGER')")
    public List<AttendanceDto> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) Long employeeId) {
        return attendanceService.list(from, to, employeeId);
    }

    @PostMapping("/attendance")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceDto add(@Valid @RequestBody AddAttendanceRequest request) {
        return attendanceService.add(request, currentUser.id());
    }

    @PutMapping("/attendance/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public AttendanceDto edit(@PathVariable Long id, @Valid @RequestBody EditAttendanceRequest request) {
        return attendanceService.edit(id, request, currentUser.id());
    }
}
