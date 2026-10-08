package vn.khoibep.rms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import vn.khoibep.rms.IntegrationTest;

/** US-21 and BR-23: weekly schedule, no overlapping shifts, copying a week. Each test uses its own dates. */
class ScheduleIntegrationTest extends IntegrationTest {

    @Test
    void overlappingShiftsOnOneDayAreRejected() throws Exception {
        EmployeeRef waiter = newEmployee("WAITER", "HOURLY", 25_000);
        long morning = newWorkShift("07:00", "14:00");
        long lunch = newWorkShift("11:00", "15:00");
        long evening = newWorkShift("14:00", "22:00");
        LocalDate day = LocalDate.of(2030, 1, 7);

        assign(waiter.id(), morning, day);
        ResultActions clash = post("/api/schedule", as("quanly"),
                Map.of("employeeId", waiter.id(), "workShiftId", lunch, "workDate", day.toString()))
                .andExpect(status().isConflict());
        assertThat(body(clash)).contains("Trùng giờ");

        // Back-to-back shifts do not overlap.
        assign(waiter.id(), evening, day);
        post("/api/schedule", as("quanly"),
                Map.of("employeeId", waiter.id(), "workShiftId", morning, "workDate", day.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void shiftsCannotCrossMidnight() throws Exception {
        post("/api/work-shifts", as("quanly"), Map.of("name", unique("Đêm"), "startTime", "22:00", "endTime", "06:00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void copyingAWeekSkipsPeopleWhoLeft() throws Exception {
        EmployeeRef stays = newEmployee("CHEF", "MONTHLY", 9_000_000);
        EmployeeRef leaves = newEmployee("WAITER", "HOURLY", 25_000);
        long shift = newWorkShift("08:00", "16:00");
        LocalDate monday = LocalDate.of(2031, 3, 3);
        assign(stays.id(), shift, monday);
        assign(stays.id(), shift, monday.plusDays(2));
        assign(leaves.id(), shift, monday.plusDays(1));
        post("/api/employees/" + leaves.id() + "/resign", as("admin"), Map.of("leftOn", monday.plusDays(4).toString()))
                .andExpect(status().isOk());

        post("/api/schedule/copy-week", as("quanly"),
                Map.of("fromWeek", monday.toString(), "toWeek", monday.plusWeeks(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copied").value(2))
                .andExpect(jsonPath("$.skipped").value(1));

        List<Integer> people = read(get("/api/schedule?from=" + monday.plusWeeks(1), as("quanly")), "$[*].employeeId");
        assertThat(people).containsOnly((int) stays.id());
    }

    @Test
    void staffSeeOnlyTheirOwnSchedule() throws Exception {
        EmployeeRef first = newEmployee("WAITER", "HOURLY", 25_000);
        EmployeeRef second = newEmployee("WAITER", "HOURLY", 25_000);
        long shift = newWorkShift("09:00", "13:00");
        LocalDate day = LocalDate.of(2032, 5, 5);
        assign(first.id(), shift, day);
        assign(second.id(), shift, day);

        get("/api/me/schedule?from=" + day + "&to=" + day, as(first.username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(first.id()));
        get("/api/schedule?from=" + day, as(first.username())).andExpect(status().isForbidden());
    }
}
