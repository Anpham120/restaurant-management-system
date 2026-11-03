package com.restaurant.core.employee.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** workStatus chỉ ACTIVE/INACTIVE; nghỉ việc (LEFT) đi qua POST /api/employees/{id}/leave. */
public record UpdateEmployeeRequest(@NotBlank @Size(max = 100) String fullName, @NotNull @Positive Long departmentId,
                                    @Size(max = 10) String gender, LocalDate dateOfBirth, @Size(max = 15) String phone,
                                    @Email @Size(max = 100) String email, @Size(max = 255) String address, LocalDate hireDate,
                                    @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String workStatus) {
}
