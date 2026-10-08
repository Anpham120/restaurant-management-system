package vn.khoibep.rms.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.khoibep.rms.common.security.CurrentUser;
import vn.khoibep.rms.dto.AuthDtos.ChangePasswordRequest;
import vn.khoibep.rms.dto.AuthDtos.LoginRequest;
import vn.khoibep.rms.dto.AuthDtos.LoginResponse;
import vn.khoibep.rms.dto.EmployeeDtos.EmployeeDto;
import vn.khoibep.rms.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public EmployeeDto me() {
        return authService.me(currentUser.id());
    }

    /** The other sessions end; the new token keeps this one going (BR-41). */
    @PostMapping("/change-password")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(currentUser.id(), request);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll() {
        authService.logoutEverywhere(currentUser.id());
    }
}
