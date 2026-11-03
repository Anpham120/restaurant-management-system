package com.restaurant.core.employee.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Tạo nhân viên + tài khoản + vai trò trong một transaction; tài khoản mới phải đổi mật khẩu khi đăng nhập lần đầu. */
public record CreateEmployeeRequest(@NotBlank @Size(max = 20) String employeeCode, @NotBlank @Size(max = 100) String fullName,
                                    @NotNull @Positive Long departmentId, @Size(max = 10) String gender, LocalDate dateOfBirth,
                                    @Size(max = 15) String phone, @Email @Size(max = 100) String email,
                                    @Size(max = 255) String address, LocalDate hireDate,
                                    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
                                    @NotBlank @Size(min = 8, max = 72) String initialPassword,
                                    @NotEmpty @Size(max = 4) List<@NotBlank String> roles) {
}
