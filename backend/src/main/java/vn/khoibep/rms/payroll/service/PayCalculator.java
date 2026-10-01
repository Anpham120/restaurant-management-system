package vn.khoibep.rms.payroll.service;

import vn.khoibep.rms.employee.enums.PayType;

/** BR-26 pay formulas, kept apart so the worked examples of US-25 can be unit tested. */
public final class PayCalculator {

    private PayCalculator() {
    }

    /**
     * Pay for the month before bonuses and deductions, rounded down to the dong. Hourly: hours worked × rate.
     * Monthly: rate ÷ standard days × (days worked + days of paid leave).
     */
    public static long base(PayType type, long rate, long workedMinutes, int workDays, int paidLeaveDays,
                            int standardDays) {
        return switch (type) {
            case HOURLY -> workedMinutes * rate / 60;
            case MONTHLY -> rate * (workDays + paidLeaveDays) / standardDays;
        };
    }
}
