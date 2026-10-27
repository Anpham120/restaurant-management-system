package com.restaurant.core.employee.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.restaurant.core.employee.dto.ShiftDtos.AttendanceAdjustmentView;
import com.restaurant.core.employee.dto.ShiftDtos.AttendanceResponse;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftAssignmentResponse;
import com.restaurant.core.employee.dto.ShiftDtos.ShiftResponse;

/**
 * Ca, phân ca, chấm công. Chồng ca bị EXCLUDE ex_shift_assignment_no_overlap chặn (23P01 → 409 OVERLAP).
 * attendance_adjustments chỉ-thêm (trigger chặn UPDATE/DELETE). Owner: Nguyễn Quang Hiếu.
 */
@Repository
public class ShiftRepository {

    /** Giờ ca là giờ địa phương của nhà hàng; ca kết thúc ≤ giờ bắt đầu thì sang ngày hôm sau. */
    private static final String ZONE = "Asia/Ho_Chi_Minh";

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

    /** Trạng thái phân ca + phút nghỉ, khóa dòng để chấm công/hủy không đua nhau. */
    public record AssignmentLock(String status, int scheduledBreakMinutes) {
    }

    /** Giá trị hiện tại của bản chấm công (khóa dòng) để ghi lịch sử điều chỉnh. */
    public record AttendanceLock(long id, String status, Instant actualStartAt, Instant actualEndAt, Integer workedMinutes,
                                 int scheduledBreakMinutes) {
    }

    private final JdbcClient jdbc;

    public ShiftRepository(JdbcClient jdbc) {
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

    public long insertShift(String name, LocalTime start, LocalTime end, int breakMinutes, boolean active) {
        return jdbc.sql("INSERT INTO shifts (shift_name, start_time, end_time, break_minutes, is_active) VALUES (:n, :s, :e, :b, :a) RETURNING id")
                .param("n", name).param("s", start).param("e", end).param("b", breakMinutes).param("a", active).query(Long.class).single();
    }

    /** Ca đã phân giữ giờ đã chụp (scheduled_*), nên sửa ca chỉ ảnh hưởng lần phân ca sau. */
    public int updateShift(long id, String name, LocalTime start, LocalTime end, int breakMinutes, boolean active) {
        return jdbc.sql("UPDATE shifts SET shift_name = :n, start_time = :s, end_time = :e, break_minutes = :b, is_active = :a WHERE id = :id")
                .param("id", id).param("n", name).param("s", start).param("e", end).param("b", breakMinutes).param("a", active).update();
    }

    // ---------- phân ca
    /** Chụp giờ ca vào bản phân ca. Rỗng nếu ca không hoạt động hoặc nhân viên không ACTIVE. */
    public Optional<Long> insertAssignment(long employeeId, long shiftId, LocalDate workDate, String note) {
        return jdbc.sql("""
                        INSERT INTO shift_assignments (employee_id, shift_id, work_date, scheduled_start_at, scheduled_end_at,
                                                       scheduled_break_minutes, note)
                        SELECT e.id, s.id, CAST(:d AS date),
                               (CAST(:d AS date) + s.start_time) AT TIME ZONE :z,
                               (CAST(:d AS date) + s.end_time + CASE WHEN s.end_time <= s.start_time THEN interval '1 day'
                                                                     ELSE interval '0' END) AT TIME ZONE :z,
                               s.break_minutes, :n
                          FROM shifts s, employees e
                         WHERE s.id = :s AND s.is_active AND e.id = :e AND e.work_status = 'ACTIVE'
                        RETURNING id""")
                .param("e", employeeId).param("s", shiftId).param("d", workDate).param("z", ZONE).param("n", note)
                .query(Long.class).optional();
    }

    public Optional<ShiftAssignmentResponse> findAssignment(long id) {
        return jdbc.sql(ASSIGNMENT_SELECT + " WHERE sa.id = :id").param("id", id).query(ShiftAssignmentResponse.class).optional();
    }

    public List<ShiftAssignmentResponse> findAssignments(LocalDate from, LocalDate to, Long employeeId) {
        return jdbc.sql(ASSIGNMENT_SELECT + """
                         WHERE sa.work_date BETWEEN :f AND :t AND (CAST(:e AS bigint) IS NULL OR sa.employee_id = :e)
                         ORDER BY sa.scheduled_start_at, sa.id""")
                .param("f", from).param("t", to).param("e", employeeId).query(ShiftAssignmentResponse.class).list();
    }

    public Optional<AssignmentLock> lockAssignment(long id) {
        return jdbc.sql("SELECT status, scheduled_break_minutes FROM shift_assignments WHERE id = :id FOR UPDATE")
                .param("id", id).query(AssignmentLock.class).optional();
    }

    public int cancelAssignment(long id, long actorId, String reason) {
        return jdbc.sql("""
                        UPDATE shift_assignments SET status = 'CANCELLED', cancel_reason = :r, cancelled_by_id = :a, cancelled_at = now()
                         WHERE id = :id AND status = 'SCHEDULED'
                           AND NOT EXISTS (SELECT 1 FROM attendances WHERE shift_assignment_id = :id)""")
                .param("id", id).param("a", actorId).param("r", reason).update();
    }

    public void completeAssignment(long id) {
        jdbc.sql("UPDATE shift_assignments SET status = 'COMPLETED' WHERE id = :id AND status = 'SCHEDULED'").param("id", id).update();
    }

    // ---------- chấm công
    public long insertAttendance(long assignmentId, String status, Instant start, Instant end, Integer worked, String note, long actorId) {
        return jdbc.sql("""
                        INSERT INTO attendances (shift_assignment_id, attendance_status, actual_start_at, actual_end_at, worked_minutes,
                                                 note, confirmed_by_id, confirmed_at)
                        VALUES (:sa, :st, :s, :e, :w, :n, :a, now()) RETURNING id""")
                .param("sa", assignmentId).param("st", status).param("s", ts(start)).param("e", ts(end)).param("w", worked)
                .param("n", note).param("a", actorId).query(Long.class).single();
    }

    public Optional<AttendanceLock> lockAttendance(long id) {
        return jdbc.sql("""
                        SELECT at.id, at.attendance_status AS status, at.actual_start_at, at.actual_end_at, at.worked_minutes,
                               sa.scheduled_break_minutes
                          FROM attendances at JOIN shift_assignments sa ON sa.id = at.shift_assignment_id
                         WHERE at.id = :id FOR UPDATE OF at""")
                .param("id", id).query(AttendanceLock.class).optional();
    }

    public void insertAdjustment(AttendanceLock old, String status, Instant start, Instant end, Integer worked, String reason, long actorId) {
        jdbc.sql("""
                        INSERT INTO attendance_adjustments (attendance_id, old_status, new_status, old_start_at, new_start_at, old_end_at,
                                                            new_end_at, old_worked_minutes, new_worked_minutes, reason, adjusted_by_id)
                        VALUES (:id, :os, :ns, :o1, :n1, :o2, :n2, :ow, :nw, :r, :a)""")
                .param("id", old.id()).param("os", old.status()).param("ns", status).param("o1", ts(old.actualStartAt()))
                .param("n1", ts(start)).param("o2", ts(old.actualEndAt())).param("n2", ts(end)).param("ow", old.workedMinutes())
                .param("nw", worked).param("r", reason).param("a", actorId).update();
    }

    public void updateAttendance(long id, String status, Instant start, Instant end, Integer worked, long actorId) {
        jdbc.sql("""
                        UPDATE attendances SET attendance_status = :st, actual_start_at = :s, actual_end_at = :e, worked_minutes = :w,
                               confirmed_by_id = :a, confirmed_at = now()
                         WHERE id = :id""")
                .param("id", id).param("st", status).param("s", ts(start)).param("e", ts(end)).param("w", worked).param("a", actorId)
                .update();
    }

    public Optional<AttendanceResponse> findAttendance(long id) {
        return jdbc.sql(ATTENDANCE_SELECT + " WHERE at.id = :id").param("id", id).query(AttendanceResponse.class).optional();
    }

    public List<AttendanceResponse> findAttendances(LocalDate from, LocalDate to, Long employeeId) {
        return jdbc.sql(ATTENDANCE_SELECT + """
                         WHERE sa.work_date BETWEEN :f AND :t AND (CAST(:e AS bigint) IS NULL OR sa.employee_id = :e)
                         ORDER BY sa.work_date, sa.scheduled_start_at, at.id""")
                .param("f", from).param("t", to).param("e", employeeId).query(AttendanceResponse.class).list();
    }

    public List<AttendanceAdjustmentView> findAdjustments(long attendanceId) {
        return jdbc.sql("""
                        SELECT id, old_status, new_status, old_start_at, new_start_at, old_end_at, new_end_at, old_worked_minutes,
                               new_worked_minutes, reason, adjusted_by_id, adjusted_at
                          FROM attendance_adjustments WHERE attendance_id = :id ORDER BY id""")
                .param("id", attendanceId).query(AttendanceAdjustmentView.class).list();
    }

    private static java.sql.Timestamp ts(Instant i) {
        return i == null ? null : java.sql.Timestamp.from(i);
    }
}
