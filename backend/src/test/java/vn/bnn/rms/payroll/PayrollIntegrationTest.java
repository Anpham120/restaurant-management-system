package vn.bnn.rms.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.bnn.rms.IntegrationTest;

/**
 * US-25, US-26, BR-26 and BR-27: payroll from attendance and paid leave; a finalized month is locked. Each test
 * uses its own month, since a payroll covers everyone who worked in it.
 */
class PayrollIntegrationTest extends IntegrationTest {

    @Test
    void hourlyAndMonthlyPayFollowAttendanceAndPaidLeave() throws Exception {
        YearMonth month = YearMonth.of(2035, 1);
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        EmployeeRef chef = newEmployee("CHEF", "MONTHLY", 8_000_000);
        // US-25 AC1: 10 days of 10 hours.
        for (int day = 1; day <= 10; day++) {
            addAttendance(waiter.id(), month.atDay(day), 8, 18);
        }
        // US-25 AC2: 24 days of work and 1 day of paid leave, out of 26.
        for (int day = 1; day <= 24; day++) {
            addAttendance(chef.id(), month.atDay(day), 8, 16);
        }
        long leave = readLong(post("/api/me/leave-requests", as(chef.username()),
                        Map.of("fromDate", month.atDay(25).toString(), "toDate", month.atDay(25).toString(),
                                "type", "PAID", "reason", "Việc gia đình"))
                        .andExpect(status().isCreated()),
                "$.id");
        post("/api/leave-requests/" + leave + "/approve", as("quanly"), null).andExpect(status().isOk());

        clock.set(month.plusMonths(1).atDay(2).atTime(9, 0));
        ResultActions payroll = post("/api/payrolls", as("admin"), Map.of("period", month.toString(), "standardDays", 26))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        List<Integer> waiterPay = read(payroll, "$.payslips[?(@.employeeId == " + waiter.id() + ")].baseAmount");
        List<Integer> chefPay = read(payroll, "$.payslips[?(@.employeeId == " + chef.id() + ")].baseAmount");
        List<Integer> chefLeave = read(payroll, "$.payslips[?(@.employeeId == " + chef.id() + ")].paidLeaveDays");
        assertThat(waiterPay).containsExactly(2_500_000);
        assertThat(chefPay).containsExactly(7_692_307);
        assertThat(chefLeave).containsExactly(1);
    }

    @Test
    void aFinalizedPayrollLocksPayAndAttendance() throws Exception {
        YearMonth month = YearMonth.of(2036, 3);
        EmployeeRef cashier = newEmployee("CASHIER", "HOURLY", 30_000);
        long record = addAttendance(cashier.id(), month.atDay(2), 8, 12);
        clock.set(month.plusMonths(1).atDay(2).atTime(9, 0));
        ResultActions created = post("/api/payrolls", as("admin"), Map.of("period", month.toString()))
                .andExpect(status().isCreated());
        long payrollId = readLong(created, "$.id");
        List<Integer> slips = read(created, "$.payslips[?(@.employeeId == " + cashier.id() + ")].id");
        long slipId = slips.get(0);

        // 4 hours at 30.000 đ, plus a bonus.
        post("/api/payslips/" + slipId + "/adjustments", as("admin"), Map.of("amount", 50_000, "reason", "Thưởng chuyên cần"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseAmount").value(120_000))
                .andExpect(jsonPath("$.netAmount").value(170_000));
        post("/api/payslips/" + slipId + "/adjustments", as("admin"), Map.of("amount", -500_000, "reason", "Phạt"))
                .andExpect(status().isConflict());

        post("/api/payrolls/" + payrollId + "/finalize", as("admin"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalizedByName").value("Quản trị hệ thống"));
        post("/api/payrolls/" + payrollId + "/recalculate", as("admin"), null).andExpect(status().isConflict());
        post("/api/payslips/" + slipId + "/adjustments", as("admin"), Map.of("amount", 10_000, "reason", "Thêm"))
                .andExpect(status().isConflict());

        // US-24 AC3: the month's attendance is locked too.
        ResultActions locked = put("/api/attendance/" + record, as("quanly"), Map.of("checkInAt",
                        vn(month.atDay(2).atTime(8, 0)), "checkOutAt", vn(month.atDay(2).atTime(13, 0)), "reason", "Sửa"))
                .andExpect(status().isConflict());
        assertThat(body(locked)).contains("đã chốt");

        // US-26: one's own finalized payslip, nobody else's.
        get("/api/me/payslips", as(cashier.username()))
                .andExpect(jsonPath("$[0].period").value(month.toString()))
                .andExpect(jsonPath("$[0].netAmount").value(170_000));
        get("/api/me/payslips/" + slipId, as(cashier.username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustments[0].reason").value("Thưởng chuyên cần"));
        EmployeeRef other = newEmployee("WAITER", "HOURLY", 25_000);
        get("/api/me/payslips/" + slipId, as(other.username())).andExpect(status().isForbidden());
        get("/api/payrolls", as("quanly")).andExpect(status().isForbidden());
    }

    @Test
    void aMonthIsFinalizedOnlyOnceOverAndWithNobodyStillClockedIn() throws Exception {
        YearMonth month = YearMonth.of(2037, 5);
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        long shift = newWorkShift("08:00", "12:00");
        LocalDate day = month.atDay(10);
        assign(waiter.id(), shift, day);
        clock.set(day.atTime(8, 0));
        post("/api/me/attendance/check-in", as(waiter.username()), null).andExpect(status().isOk());

        // Still in May: a draft is fine, finalizing is not.
        clock.set(month.atDay(20).atTime(9, 0));
        long id = readLong(post("/api/payrolls", as("admin"), Map.of("period", month.toString()))
                .andExpect(status().isCreated()), "$.id");
        ResultActions early = post("/api/payrolls/" + id + "/finalize", as("admin"), null).andExpect(status().isConflict());
        assertThat(body(early)).contains("tháng đã kết thúc");

        clock.set(month.plusMonths(1).atDay(1).atTime(9, 0));
        ResultActions open = post("/api/payrolls/" + id + "/finalize", as("admin"), null).andExpect(status().isConflict());
        assertThat(body(open)).contains("chưa ra ca");
        post("/api/payrolls", as("admin"), Map.of("period", month.toString())).andExpect(status().isConflict());
    }

    /** Work a manager enters by hand, from one hour to another (Vietnam time). */
    private long addAttendance(long employeeId, LocalDate day, int fromHour, int toHour) throws Exception {
        return readLong(post("/api/attendance", as("quanly"), Map.of("employeeId", employeeId,
                        "checkInAt", vn(day.atTime(fromHour, 0)), "checkOutAt", vn(day.atTime(toHour, 0)),
                        "reason", "Nhập bù"))
                        .andExpect(status().isCreated()),
                "$.id");
    }
}
