package vn.khoibep.rms.audit.dto;

import java.time.Instant;

import vn.khoibep.rms.audit.entity.AuditEntry;
import vn.khoibep.rms.audit.enums.AuditAction;

public final class AuditDtos {

    private AuditDtos() {
    }

    /** @param tableName null for a takeaway order, or when the action has no order */
    public record AuditEntryDto(Long id, AuditAction action, Long employeeId, String employeeName, Long orderId,
                                String tableName, String subject, String beforeValue, String afterValue, Long amount,
                                String reason, Instant createdAt) {
        public static AuditEntryDto from(AuditEntry a) {
            Long orderId = a.getOrder() == null ? null : a.getOrder().getId();
            String tableName = a.getOrder() == null ? null : a.getOrder().tableLabel();
            return new AuditEntryDto(a.getId(), a.getAction(), a.getEmployee().getId(), a.getEmployee().getFullName(),
                    orderId, tableName, a.getSubject(), a.getBeforeValue(), a.getAfterValue(), a.getAmount(),
                    a.getReason(), a.getCreatedAt());
        }
    }
}
