package vn.khoibep.rms.leave.controller;

import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.leave.dto.LeaveDtos.CreateLeaveRequest;
import vn.khoibep.rms.leave.dto.LeaveDtos.DecisionRequest;
import vn.khoibep.rms.leave.dto.LeaveDtos.LeaveDto;
import vn.khoibep.rms.leave.enums.LeaveStatus;
import vn.khoibep.rms.leave.service.LeaveService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final CurrentUser currentUser;

    // ---- Any signed-in employee, for themselves -------------------------------------------------------

    @GetMapping("/me/leave-requests")
    public List<LeaveDto> mine() {
        return leaveService.mine(currentUser.id());
    }

    @PostMapping("/me/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveDto create(@Valid @RequestBody CreateLeaveRequest request) {
        return leaveService.create(currentUser.id(), request);
    }

    @PostMapping("/me/leave-requests/{id}/cancel")
    public LeaveDto cancel(@PathVariable Long id) {
        return leaveService.cancel(id, currentUser.id());
    }

    // ---- Managers --------------------------------------------------------------------------------------

    @GetMapping("/leave-requests")
    @PreAuthorize("hasRole('MANAGER')")
    public List<LeaveDto> list(@RequestParam(required = false) LeaveStatus status) {
        return leaveService.list(status);
    }

    @PostMapping("/leave-requests/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public LeaveDto approve(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return leaveService.approve(id, request == null ? null : request.note(), currentUser.id());
    }

    @PostMapping("/leave-requests/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public LeaveDto reject(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return leaveService.reject(id, request.note(), currentUser.id());
    }
}
