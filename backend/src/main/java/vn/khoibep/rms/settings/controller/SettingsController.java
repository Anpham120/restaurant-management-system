package vn.khoibep.rms.settings.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.settings.dto.SettingsDtos.SettingsDto;
import vn.khoibep.rms.settings.dto.SettingsDtos.SettingsRequest;
import vn.khoibep.rms.settings.service.SettingsService;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public SettingsDto get() {
        return settingsService.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto update(@Valid @RequestBody SettingsRequest request) {
        return settingsService.update(request);
    }
}
