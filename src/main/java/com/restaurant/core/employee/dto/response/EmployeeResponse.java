package com.restaurant.core.employee.dto.response;

import java.time.LocalDate;
import java.util.List;

/** Hồ sơ nhân viên kèm tài khoản (null nếu chưa có) và vai trò xếp theo mã. Không bao giờ chứa mật khẩu. */
public record EmployeeResponse(long id, String employeeCode, String fullName, String gender, LocalDate dateOfBirth, String phone,
                               String email, String address, LocalDate hireDate, LocalDate endDate, String workStatus,
                               long departmentId, String departmentName, Long accountId, String username, String accountStatus,
                               Boolean mustChangePassword, List<String> roles) {
}
