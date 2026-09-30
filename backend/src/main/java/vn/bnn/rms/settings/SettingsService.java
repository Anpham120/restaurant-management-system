package vn.bnn.rms.settings;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.bnn.rms.settings.SettingsDtos.SettingsDto;
import vn.bnn.rms.settings.SettingsDtos.SettingsRequest;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settings;

    @Transactional(readOnly = true)
    public RestaurantSettings current() {
        return settings.findById(RestaurantSettings.ID)
                .orElseThrow(() -> new IllegalStateException("restaurant_settings row is missing"));
    }

    @Transactional(readOnly = true)
    public SettingsDto get() {
        return SettingsDto.from(current());
    }

    @Transactional
    public SettingsDto update(SettingsRequest request) {
        RestaurantSettings s = current();
        s.setName(request.name().trim());
        s.setAddress(request.address());
        s.setPhone(request.phone());
        s.setBankCode(request.bankCode());
        s.setBankAccountNo(request.bankAccountNo());
        s.setBankAccountName(request.bankAccountName());
        s.setWaitAlertMinutes(request.waitAlertMinutes());
        s.setUpdatedAt(Instant.now());
        return SettingsDto.from(s);
    }
}
