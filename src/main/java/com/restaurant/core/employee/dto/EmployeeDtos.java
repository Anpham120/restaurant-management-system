package com.restaurant.core.employee.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Hồ sơ nhân viên, tài khoản, vai trò, phòng ban (1.1–1.2). Vai trò là role_code: MANAGER, WAITER, KITCHEN, CASHIER. */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record DepartmentRequest(@NotBlank @Size(max = 50) String name) {
    }

    public record DepartmentResponse(long id, String name) {
    }

    public record RoleResponse(long id, String roleCode, String roleName) {
    }

    /** Tạo nhân viên + tài khoản + vai trò trong một transaction; tài khoản mới phải đổi mật khẩu khi đăng nhập lần đầu. */
    public record CreateEmployeeRequest(@NotBlank @Size(max = 20) String employeeCode, @NotBlank @Size(max = 100) String fullName,
                                        @NotNull @Positive Long departmentId, @Size(max = 10) String gender, LocalDate dateOfBirth,
                                        @Size(max = 15) String phone, @Email @Size(max = 100) String email,
                                        @Size(max = 255) String address, LocalDate hireDate,
                                        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
                                        @NotBlank @Size(min = 8, max = 72) String initialPassword,
                                        @NotEmpty @Size(max = 4) List<@NotBlank String> roles) {
    }

    /** workStatus chỉ ACTIVE/INACTIVE; nghỉ việc (LEFT) đi qua POST /api/employees/{id}/leave. */
    public record UpdateEmployeeRequest(@NotBlank @Size(max = 100) String fullName, @NotNull @Positive Long departmentId,
                                        @Size(max = 10) String gender, LocalDate dateOfBirth, @Size(max = 15) String phone,
                                        @Email @Size(max = 100) String email, @Size(max = 255) String address, LocalDate hireDate,
                                        @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String workStatus) {
    }

    public record LeaveRequest(@NotNull LocalDate endDate) {
    }

    public record RolesRequest(@NotEmpty @Size(max = 4) List<@NotBlank String> roles) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record EmployeeResponse(long id, String employeeCode, String fullName, String gender, LocalDate dateOfBirth, String phone,
                                   String email, String address, LocalDate hireDate, LocalDate endDate, String workStatus,
                                   long departmentId, String departmentName, Long accountId, String username, String accountStatus,
                                   Boolean mustChangePassword, List<String> roles) {
    }
}
