package vn.khoibep.rms.payroll.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import vn.khoibep.rms.employee.enums.PayType;

/** BR-26 formulas, with the worked examples of US-25. */
class PayCalculatorTest {

    @Test
    void hourlyPayIsHoursTimesRate() {
        // US-25 AC1: 100 hours at 25.000 đ.
        assertThat(PayCalculator.base(PayType.HOURLY, 25_000, 100 * 60, 10, 0, 26)).isEqualTo(2_500_000);
    }

    @Test
    void monthlyPayIsProRataToPaidDays() {
        // US-25 AC2: 8.000.000 ÷ 26 × (24 days + 1 day of paid leave) = 7.692.307,69 đ.
        assertThat(PayCalculator.base(PayType.MONTHLY, 8_000_000, 0, 24, 1, 26)).isEqualTo(7_692_307);
    }

    @Test
    void partsOfADongAreDropped() {
        // 90 minutes at 25.001 đ an hour = 37.501,5 đ.
        assertThat(PayCalculator.base(PayType.HOURLY, 25_001, 90, 1, 0, 26)).isEqualTo(37_501);
    }
}
