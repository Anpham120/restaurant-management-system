package com.restaurant.core.employee.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.employee.dto.response.ShiftResponses.AttendanceAdjustmentView;
import com.restaurant.core.employee.dto.response.ShiftResponses.AttendanceResponse;
import com.restaurant.core.employee.dto.response.ShiftResponses.ShiftAssignmentResponse;
import com.restaurant.core.employee.dto.response.ShiftResponses.ShiftResponse;

/** Truy vấn CHỈ ĐỌC ca, phân ca, chấm công (ghép nhân viên, trả thẳng DTO). Ghi: các JpaRepository. Owner: Nguyễn Quang Hiếu. */
@Repository
public class ShiftQueryRepository {

    private static final String ASSIGNMENT_SELECT = """
            SELECT sa.id, sa.employee_id, e.full_name AS employee_name, sa.shift_id, s.shift_name, sa.work_date,
                   sa.scheduled_start_at, sa.scheduled_end_at, sa.scheduled_break_minutes, sa.status, sa.note, sa.cancel_reason,
                   sa.cancelled_at, at.id AS attendance_id
              FROM shift_assignments sa
              JOIN employees e ON e.id = sa.employee_id
              JOIN shifts s ON s.id = sa.shift_id
              LEFT JOIN attendances at ON at.shift_assignment_id = sa.id
            """;

    private static final String ATTENDANCE_SELECT = """
            SELECT at.id, at.shift_assignment_id, sa.employee_id, e.full_name AS employee_name, sa.work_date,
                   at.attendance_status AS status, at.actual_start_at, at.actual_end_at, at.worked_minutes, at.note,
                   at.confirmed_by_id, at.confirmed_at
              FROM attendances at
              JOIN shift_assignments sa ON sa.id = at.shift_assignment_id
              JOIN employees e ON e.id = sa.employee_id
            """;

    private final JdbcClient jdbc;

    public ShiftQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- ca
    public List<ShiftResponse> findShifts(boolean activeOnly) {
        return jdbc.sql("SELECT id, shift_name AS name, start_time, end_time, break_minutes, is_active AS active FROM shifts"
                        + " WHERE is_active OR NOT :a ORDER BY start_time, id")
                .param("a", activeOnly).query(ShiftResponse.class).list();
    }

    public Optional<ShiftResponse> findShift(long id) {
        return jdbc.sql("SELECT id, shift_name AS name, start_time, end_time, break_minutes, is_active AS active FROM shifts WHERE id = :id")
                .param("id", id).query(ShiftResponse.class).optional();
    }

    // ---------- phân ca

    public Optional<ShiftAssignmentResponse> findShiftAssignment(long id) {
        return jdbc.sql(ASSIGNMENT_SELECT + " WHERE sa.id = :id").param("id", id).query(ShiftAssignmentResponse.class).optional();
    }

    public List<ShiftAssignmentResponse> findShiftAssignments(LocalDate from, LocalDate to, Long employeeId) {
        return jdbc.sql(ASSIGNMENT_SELECT + """
                         WHERE sa.work_date BETWEEN :f AND :t AND (CAST(:e AS bigint) IS NULL OR sa.employee_id = :e)
                         ORDER BY sa.scheduled_start_at, sa.id""")
                .param("f", from).param("t", to).param("e", employeeId).query(ShiftAssignmentResponse.class).list();
    }

    // ---------- chấm công

    public Optional<AttendanceResponse> findAttendance(long id) {
        return jdbc.sql(ATTENDANCE_SELECT + " WHERE at.id = :id").param("id", id).query(AttendanceResponse.class).optional();
    }

    public List<AttendanceResponse> findAttendances(LocalDate from, LocalDate to, Long employeeId) {
        return jdbc.sql(ATTENDANCE_SELECT + """
                         WHERE sa.work_date BETWEEN :f AND :t AND (CAST(:e AS bigint) IS NULL OR sa.employee_id = :e)
                         ORDER BY sa.work_date, sa.scheduled_start_at, at.id""")
                .param("f", from).param("t", to).param("e", employeeId).query(AttendanceResponse.class).list();
    }

    public List<AttendanceAdjustmentView> findAttendanceAdjustments(long attendanceId) {
        return jdbc.sql("""
                        SELECT id, old_status, new_status, old_start_at, new_start_at, old_end_at, new_end_at, old_worked_minutes,
                               new_worked_minutes, reason, adjusted_by_id, adjusted_at
                          FROM attendance_adjustments WHERE attendance_id = :id ORDER BY id""")
                .param("id", attendanceId).query(AttendanceAdjustmentView.class).list();
    }

}
