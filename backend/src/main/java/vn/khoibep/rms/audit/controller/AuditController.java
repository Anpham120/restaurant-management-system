package vn.khoibep.rms.audit.controller;

import java.time.LocalDate;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.audit.dto.AuditDtos.AuditEntryDto;
import vn.khoibep.rms.audit.service.AuditService;

/** FR-16.2. There is deliberately no endpoint that edits or deletes the log (FR-16.3). */
@RestController
@RequestMapping("/api/audit-entries")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public List<AuditEntryDto> search(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return auditService.search(from, to);
    }
}
