package vn.bnn.rms.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsDto(String name, String address, String phone, String bankCode, String bankAccountNo,
                              String bankAccountName) {
        public static SettingsDto from(RestaurantSettings s) {
            return new SettingsDto(s.getName(), s.getAddress(), s.getPhone(), s.getBankCode(), s.getBankAccountNo(),
                    s.getBankAccountName());
        }
    }

    public record SettingsRequest(@NotBlank @Size(max = 150) String name,
                                  @Size(max = 300) String address,
                                  @Size(max = 20) String phone,
                                  @Size(max = 20) String bankCode,
                                  @Size(max = 30) String bankAccountNo,
                                  @Size(max = 100) String bankAccountName) {
    }
}
