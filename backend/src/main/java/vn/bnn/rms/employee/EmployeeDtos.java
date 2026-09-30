package vn.bnn.rms.employee;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class EmployeeDtos {

    private static final String PHONE = "^[0-9+ .()-]*$";
    private static final String PHONE_MESSAGE = "chỉ gồm số và các dấu + . ( ) -";

    private EmployeeDtos() {
    }

    /** Sign-in and "me" responses: no profile or pay. */
    public record EmployeeDto(Long id, String fullName, String username, Role role, boolean active) {
        public static EmployeeDto from(Employee e) {
            return new EmployeeDto(e.getId(), e.getFullName(), e.getUsername(), e.getRole(), e.isActive());
        }
    }

    /** For ADMIN screens only, since it carries pay (BR-22). */
    public record EmployeeDetailDto(Long id, String fullName, String username, Role role, boolean active,
                                    String phone, LocalDate hiredOn, LocalDate leftOn, PayType payType,
                                    long payRate) {
        public static EmployeeDetailDto from(Employee e) {
            return new EmployeeDetailDto(e.getId(), e.getFullName(), e.getUsername(), e.getRole(), e.isActive(),
                    e.getPhone(), e.getHiredOn(), e.getLeftOn(), e.getPayType(), e.getPayRate());
        }
    }

    /** Profile fields are optional here; pay starts as hourly at 0 until set (FR-12). */
    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                    message = "chỉ gồm chữ không dấu, số, dấu chấm, gạch dưới, gạch ngang") String username,
            @NotNull Role role,
            @NotBlank @Size(min = 6, max = 100) String password,
            @Size(max = 20) @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            LocalDate hiredOn,
            PayType payType,
            @PositiveOrZero(message = "không được âm") Long payRate) {
    }

    public record UpdateEmployeeRequest(@NotBlank @Size(max = 100) String fullName, @NotNull Role role) {
    }

    /** FR-12.1, FR-12.2. */
    public record ProfileRequest(
            @Size(max = 20) @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            LocalDate hiredOn,
            @NotNull PayType payType,
            @NotNull @PositiveOrZero(message = "không được âm") Long payRate) {
    }

    /** FR-12.3. */
    public record ResignRequest(@NotNull LocalDate leftOn) {
    }

    public record SetActiveRequest(@NotNull Boolean active) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 6, max = 100) String newPassword) {
    }
}
