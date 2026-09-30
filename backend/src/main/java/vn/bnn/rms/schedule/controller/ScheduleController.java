package vn.bnn.rms.schedule.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.bnn.rms.common.security.CurrentUser;
import vn.bnn.rms.schedule.dto.ScheduleDtos.AssignRequest;
import vn.bnn.rms.schedule.dto.ScheduleDtos.AssignmentDto;
import vn.bnn.rms.schedule.dto.ScheduleDtos.CopyWeekRequest;
import vn.bnn.rms.schedule.dto.ScheduleDtos.CopyWeekResult;
import vn.bnn.rms.schedule.dto.ScheduleDtos.StaffDto;
import vn.bnn.rms.schedule.dto.ScheduleDtos.WorkShiftDto;
import vn.bnn.rms.schedule.dto.ScheduleDtos.WorkShiftRequest;
import vn.bnn.rms.schedule.service.ScheduleService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentUser currentUser;

    @GetMapping("/work-shifts")
    @PreAuthorize("hasRole('MANAGER')")
    public List<WorkShiftDto> shifts() {
        return scheduleService.shifts();
    }

    @PostMapping("/work-shifts")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkShiftDto createShift(@Valid @RequestBody WorkShiftRequest request) {
        return scheduleService.createShift(request);
    }

    @PutMapping("/work-shifts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public WorkShiftDto updateShift(@PathVariable Long id, @Valid @RequestBody WorkShiftRequest request) {
        return scheduleService.updateShift(id, request);
    }

    @GetMapping("/schedule/staff")
    @PreAuthorize("hasRole('MANAGER')")
    public List<StaffDto> staff() {
        return scheduleService.staff();
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasRole('MANAGER')")
    public List<AssignmentDto> week(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return scheduleService.week(from);
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentDto assign(@Valid @RequestBody AssignRequest request) {
        return scheduleService.assign(request, currentUser.id());
    }

    @DeleteMapping("/schedule/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable Long id) {
        scheduleService.unassign(id);
    }

    @PostMapping("/schedule/copy-week")
    @PreAuthorize("hasRole('MANAGER')")
    public CopyWeekResult copyWeek(@Valid @RequestBody CopyWeekRequest request) {
        return scheduleService.copyWeek(request.fromWeek(), request.toWeek(), currentUser.id());
    }

    /** Any signed-in employee, own schedule only. */
    @GetMapping("/me/schedule")
    public List<AssignmentDto> mine(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleService.mine(currentUser.id(), from, to);
    }
}
