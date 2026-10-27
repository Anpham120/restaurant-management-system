package com.restaurant.core.employee;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.restaurant.core.TestData;
import com.restaurant.core.order.OrderApiTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhân viên, tài khoản, vai trò, đổi mật khẩu; ca, phân ca, chấm công và điều chỉnh. */
class EmployeeIT extends OrderApiTestBase {

    private static int seq = 0;

    private static String uniq(String p) {
        return p + (System.nanoTime() % 1_000_000_000L) + (seq++);
    }

    private ResultActions putJson(String token, String url, String body, Object... vars) throws Exception {
        return mvc.perform(put(url, vars).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions loginAs(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private long department() throws Exception {
        return readLong(postJson(manager, "/api/departments", "{\"name\":\"" + uniq("Phòng ") + "\"}").andExpect(status().isCreated()), "$.data.id");
    }

    private ResultActions createEmployee(long dept, String code, String username, String roles) throws Exception {
        return postJson(manager, "/api/employees", "{\"employeeCode\":\"" + code + "\",\"fullName\":\"Nguyễn Văn Test\",\"departmentId\":" + dept
                + ",\"hireDate\":\"2026-01-01\",\"username\":\"" + username + "\",\"initialPassword\":\"Tam-thoi-123\",\"roles\":" + roles + "}");
    }

    @Test
    void employeeAccountLifecycle() throws Exception {
        long dept = department();
        String code = uniq("E");
        String user = uniq("nv");
        ResultActions created = createEmployee(dept, code, user, "[\"waiter\"]").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.workStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles", contains("WAITER")))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        long emp = readLong(created, "$.data.id");
        long acc = readLong(created, "$.data.accountId");

        createEmployee(dept, uniq("E"), user, "[\"WAITER\"]").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE"));
        createEmployee(dept, uniq("E"), uniq("nv"), "[\"CHEF\"]").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_ROLE"));
        assertThat(data.count("SELECT count(*) FROM employees WHERE full_name = 'Nguyễn Văn Test' AND department_id = ?", dept)).isEqualTo(1);
        createEmployee(999_999_999L, uniq("E"), uniq("nv"), "[\"WAITER\"]").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("REFERENCE_NOT_FOUND"));
        getAs(waiter, "/api/employees").andExpect(status().isForbidden());
        getAs(manager, "/api/roles").andExpect(jsonPath("$.data.length()").value(4));
        getAs(manager, "/api/employees?status=ACTIVE&size=2").andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalItems", greaterThan(2))).andExpect(jsonPath("$.data.items[*].workStatus", everyItem(is("ACTIVE"))));

        // đăng nhập lần đầu → đổi mật khẩu
        String token = "Bearer " + read(loginAs(user, "Tam-thoi-123").andExpect(jsonPath("$.data.mustChangePassword").value(true)), "$.data.accessToken");
        // chưa đổi mật khẩu: chỉ /me và đổi mật khẩu
        getAs(token, "/api/tables").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        getAs(token, "/api/auth/me").andExpect(status().isOk());
        postJson(token, "/api/auth/change-password", "{\"currentPassword\":\"sai\",\"newPassword\":\"Moi-mat-khau-1\"}")
                .andExpect(status().isUnauthorized());
        postJson(token, "/api/auth/change-password", "{\"currentPassword\":\"Tam-thoi-123\",\"newPassword\":\"ngan\"}")
                .andExpect(status().isBadRequest());
        postJson(token, "/api/auth/change-password", "{\"currentPassword\":\"Tam-thoi-123\",\"newPassword\":\"Moi-mat-khau-1\"}")
                .andExpect(status().isOk());
        loginAs(user, "Tam-thoi-123").andExpect(status().isUnauthorized());
        loginAs(user, "Moi-mat-khau-1").andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(false));
        getAs(token, "/api/tables").andExpect(status().isOk());                    // cùng token, hết bị chặn ngay

        // vai trò, khóa/mở, đặt lại mật khẩu
        putJson(manager, "/api/accounts/{id}/roles", "{\"roles\":[\"KITCHEN\",\"CASHIER\"]}", acc).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", contains("CASHIER", "KITCHEN")));
        // vai trò đọc từ DB: token phát lúc còn WAITER có hiệu lực theo vai trò mới ngay
        getAs(token, "/api/kitchen/queue").andExpect(status().isOk());
        postEmpty(token, "/api/tables/{id}/sessions", data.table()).andExpect(status().isForbidden());
        getAs(token, "/api/auth/me").andExpect(jsonPath("$.data.roles", contains("CASHIER", "KITCHEN")));
        postEmpty(manager, "/api/accounts/{id}/lock", acc).andExpect(jsonPath("$.data.accountStatus").value("LOCKED"));
        postEmpty(manager, "/api/accounts/{id}/lock", acc).andExpect(status().isConflict());
        loginAs(user, "Moi-mat-khau-1").andExpect(status().isUnauthorized());
        getAs(token, "/api/auth/me").andExpect(status().isUnauthorized());
        postEmpty(manager, "/api/accounts/{id}/unlock", acc).andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
        postJson(manager, "/api/accounts/{id}/reset-password", "{\"newPassword\":\"Reset-12345\"}", acc)
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        token = "Bearer " + read(loginAs(user, "Reset-12345").andExpect(status().isOk()), "$.data.accessToken");

        // sửa hồ sơ; nghỉ việc với ngày trước ngày vào làm → CHECK
        putJson(manager, "/api/employees/{id}", "{\"fullName\":\"Nguyễn Văn Sửa\",\"departmentId\":" + dept
                + ",\"hireDate\":\"2026-01-01\",\"phone\":\"0900000000\",\"workStatus\":\"ACTIVE\"}", emp)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn Sửa"));
        postJson(manager, "/api/employees/{id}/leave", "{\"endDate\":\"2025-12-31\"}", emp)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("INVALID_DATA"));
        postJson(manager, "/api/employees/{id}/leave", "{\"endDate\":\"" + LocalDate.now() + "\"}", emp)
                .andExpect(jsonPath("$.data.workStatus").value("LEFT"));
        loginAs(user, "Reset-12345").andExpect(status().isUnauthorized());
        // token còn hạn nhưng nhân viên đã nghỉ → 401 ở mọi endpoint (không chỉ hàm rc_*)
        getAs(token, "/api/kitchen/queue").andExpect(status().isUnauthorized());
        putJson(manager, "/api/employees/{id}", "{\"fullName\":\"x\",\"departmentId\":" + dept + ",\"workStatus\":\"ACTIVE\"}", emp)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMPLOYEE_LEFT"));
    }

    @Test
    void managerCannotLockThemself() throws Exception {
        TestData.Staff boss = data.staff("MANAGER");
        String bossToken = bearer(boss);
        ResultActions me = getAs(bossToken, "/api/employees/{id}", boss.employeeId());
        long acc = readLong(me, "$.data.accountId");
        postEmpty(bossToken, "/api/accounts/{id}/lock", acc).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SELF_LOCKOUT"));
        putJson(bossToken, "/api/accounts/{id}/roles", "{\"roles\":[\"WAITER\"]}", acc).andExpect(jsonPath("$.error.code").value("SELF_LOCKOUT"));
        postJson(bossToken, "/api/employees/{id}/leave", "{\"endDate\":\"" + LocalDate.now() + "\"}", boss.employeeId())
                .andExpect(jsonPath("$.error.code").value("SELF_LOCKOUT"));
        postEmpty(bossToken, "/api/accounts/{id}/lock", 999_999_999L).andExpect(status().isNotFound());
    }

    @Test
    void shiftsAssignmentsAndAttendance() throws Exception {
        long morning = readLong(postJson(manager, "/api/shifts", "{\"name\":\"" + uniq("Sáng ") + "\",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"breakMinutes\":30}")
                .andExpect(status().isCreated()), "$.data.id");
        long night = readLong(postJson(manager, "/api/shifts", "{\"name\":\"" + uniq("Đêm ") + "\",\"startTime\":\"22:00\",\"endTime\":\"02:00\"}"), "$.data.id");
        postJson(waiter, "/api/shifts", "{\"name\":\"x\",\"startTime\":\"08:00\",\"endTime\":\"09:00\"}").andExpect(status().isForbidden());
        getAs(waiter, "/api/shifts").andExpect(status().isOk());

        long emp = waiterStaff.employeeId();
        LocalDate day = LocalDate.of(2027, 3, 1).plusDays(seq++ % 300);
        long a1 = readLong(postJson(manager, "/api/shift-assignments", "{\"employeeId\":" + emp + ",\"shiftId\":" + morning + ",\"workDate\":\"" + day + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scheduledStartAt").value(day + "T01:00:00Z"))
                .andExpect(jsonPath("$.data.scheduledEndAt").value(day + "T05:00:00Z"))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED")), "$.data.id");
        postJson(manager, "/api/shift-assignments", "{\"employeeId\":" + emp + ",\"shiftId\":" + morning + ",\"workDate\":\"" + day + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("OVERLAP"));
        long a2 = readLong(postJson(manager, "/api/shift-assignments", "{\"employeeId\":" + emp + ",\"shiftId\":" + night + ",\"workDate\":\"" + day + "\"}")
                .andExpect(jsonPath("$.data.scheduledEndAt").value(day + "T19:00:00Z")), "$.data.id");

        // Waiter chỉ thấy ca của mình dù truyền employeeId khác
        List<Number> seen = read(getAs(waiter, "/api/shift-assignments?from=" + day + "&to=" + day + "&employeeId=1"), "$.data[*].employeeId");
        assertThat(seen).hasSize(2).allMatch(n -> n.longValue() == emp);

        // chấm công: thiếu giờ → 422; đủ → công = 235 − 30 phút nghỉ
        String base = day + "T";
        postJson(manager, "/api/attendances", "{\"shiftAssignmentId\":" + a1 + ",\"status\":\"PRESENT\"}")
                .andExpect(status().isUnprocessableContent());
        long att = readLong(postJson(manager, "/api/attendances", "{\"shiftAssignmentId\":" + a1 + ",\"status\":\"PRESENT\",\"actualStartAt\":\""
                + base + "01:05:00Z\",\"actualEndAt\":\"" + base + "05:00:00Z\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.workedMinutes").value(205)), "$.data.id");
        getAs(manager, "/api/shift-assignments?from=" + day + "&to=" + day + "&employeeId=" + emp)
                .andExpect(jsonPath("$.data[?(@.id == " + a1 + ")].status", contains("COMPLETED")));
        postJson(manager, "/api/attendances", "{\"shiftAssignmentId\":" + a1 + ",\"status\":\"ABSENT\"}")
                .andExpect(status().isConflict());
        postJson(manager, "/api/shift-assignments/{id}/cancel", reason("đổi ca"), a1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NOT_CANCELLABLE"));

        // điều chỉnh: phút công vượt khoảng có mặt → CHECK; hợp lệ → lưu cũ/mới
        postJson(manager, "/api/attendances/{id}/adjustments", "{\"status\":\"PRESENT\",\"actualStartAt\":\"" + base + "01:00:00Z\",\"actualEndAt\":\""
                + base + "02:00:00Z\",\"workedMinutes\":500,\"reason\":\"sai\"}", att)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.error.code").value("INVALID_DATA"));
        postJson(manager, "/api/attendances/{id}/adjustments", "{\"status\":\"PRESENT\",\"actualStartAt\":\"" + base + "01:00:00Z\",\"actualEndAt\":\""
                + base + "05:00:00Z\",\"reason\":\"quên quẹt thẻ\"}", att)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.workedMinutes").value(210));
        getAs(manager, "/api/attendances/{id}/adjustments", att).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].oldWorkedMinutes").value(205)).andExpect(jsonPath("$.data[0].newWorkedMinutes").value(210));
        getAs(waiter, "/api/attendances?from=" + day + "&to=" + day).andExpect(jsonPath("$.data[0].workedMinutes").value(210));
        postJson(waiter, "/api/attendances/{id}/adjustments", "{\"status\":\"ABSENT\",\"reason\":\"x\"}", att).andExpect(status().isForbidden());

        // hủy ca chưa chấm → được phân lại đúng khung giờ đó
        postJson(manager, "/api/shift-assignments/{id}/cancel", reason("nghỉ phép"), a2).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        postJson(manager, "/api/attendances", "{\"shiftAssignmentId\":" + a2 + ",\"status\":\"ABSENT\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ASSIGNMENT_CANCELLED"));
        postJson(manager, "/api/shift-assignments", "{\"employeeId\":" + emp + ",\"shiftId\":" + night + ",\"workDate\":\"" + day + "\"}")
                .andExpect(status().isCreated());
        getAs(manager, "/api/shift-assignments?from=" + day + "&to=" + day.plusDays(100)).andExpect(status().isBadRequest());
    }
}
