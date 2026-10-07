package vn.khoibep.rms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.entity.RestaurantSettings;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsDto(String name, String address, String phone, String bankCode, String bankAccountNo,
                              String bankAccountName, int waitAlertMinutes) {
        public static SettingsDto from(RestaurantSettings s) {
            return new SettingsDto(s.getName(), s.getAddress(), s.getPhone(), s.getBankCode(), s.getBankAccountNo(),
                    s.getBankAccountName(), s.getWaitAlertMinutes());
        }
    }

    /** BR-28: waitAlertMinutes from 1 to 120. */
    public record SettingsRequest(@NotBlank @Size(max = 150) String name,
                                  @Size(max = 300) String address,
                                  @Size(max = 20) String phone,
                                  @Size(max = 20) String bankCode,
                                  @Size(max = 30) String bankAccountNo,
                                  @Size(max = 100) String bankAccountName,
                                  @NotNull @Min(1) @Max(120) Integer waitAlertMinutes) {
    }
}
