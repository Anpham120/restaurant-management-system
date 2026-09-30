package vn.bnn.rms.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    public record EmployeeDto(Long id, String fullName, String username, Role role, boolean active) {
        public static EmployeeDto from(Employee e) {
            return new EmployeeDto(e.getId(), e.getFullName(), e.getUsername(), e.getRole(), e.isActive());
        }
    }

    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                    message = "chỉ gồm chữ không dấu, số, dấu chấm, gạch dưới, gạch ngang") String username,
            @NotNull Role role,
            @NotBlank @Size(min = 6, max = 100) String password) {
    }

    public record UpdateEmployeeRequest(@NotBlank @Size(max = 100) String fullName, @NotNull Role role) {
    }

    public record SetActiveRequest(@NotNull Boolean active) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 6, max = 100) String newPassword) {
    }
}
