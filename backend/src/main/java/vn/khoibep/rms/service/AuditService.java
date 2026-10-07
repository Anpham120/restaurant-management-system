package vn.khoibep.rms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.common.util.DateRange;
import vn.khoibep.rms.dto.AuditDtos.AuditEntryDto;
import vn.khoibep.rms.entity.AuditEntry;
import vn.khoibep.rms.entity.Order;
import vn.khoibep.rms.enums.AuditAction;
import vn.khoibep.rms.repository.AuditEntryRepository;
import vn.khoibep.rms.repository.EmployeeRepository;

/** FR-16: writes and reads the audit log. Nothing here, or anywhere else, edits or deletes it (BR-34). */
@Service
@RequiredArgsConstructor
public class AuditService {

    /** FR-16.2: one look covers at most about a quarter. */
    static final int MAX_DAYS = 92;

    private final AuditEntryRepository entries;
    private final EmployeeRepository employees;
    private final CurrentUser currentUser;
    private final Clock clock;

    /**
     * BR-34: written in the transaction of the action itself, so the line exists exactly when the action happened.
     * The employee is the one signed in, never a value sent by the client.
     *
     * @param order null for actions on the menu
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, Order order, String subject, String beforeValue, String afterValue,
                       Long amount, String reason) {
        entries.save(new AuditEntry(action, employees.getReferenceById(currentUser.id()), order, subject, beforeValue,
                afterValue, amount, reason, clock.instant()));
    }

    /** FR-16.2: newest first; the days are dates in Vietnam. */
    @Transactional(readOnly = true)
    public List<AuditEntryDto> search(LocalDate from, LocalDate to) {
        DateRange.check(from, to, MAX_DAYS);
        ZoneId zone = clock.getZone();
        return entries.findBetween(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().map(AuditEntryDto::from).toList();
    }
}
