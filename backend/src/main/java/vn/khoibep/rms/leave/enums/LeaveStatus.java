package vn.khoibep.rms.leave.enums;

/** Only a PENDING request can be approved, rejected or cancelled (BR-24). */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
