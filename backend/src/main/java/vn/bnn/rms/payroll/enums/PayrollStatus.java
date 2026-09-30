package vn.bnn.rms.payroll.enums;

/** A DRAFT can be recalculated and adjusted; FINALIZED locks the payroll and the month's attendance (BR-27). */
public enum PayrollStatus {
    DRAFT,
    FINALIZED
}
