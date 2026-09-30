package vn.bnn.rms.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.bnn.rms.auth.dto.AuthDtos.ChangePasswordRequest;
import vn.bnn.rms.auth.dto.AuthDtos.LoginRequest;
import vn.bnn.rms.auth.dto.AuthDtos.LoginResponse;
import vn.bnn.rms.auth.service.AuthService;
import vn.bnn.rms.common.security.CurrentUser;
import vn.bnn.rms.employee.dto.EmployeeDtos.EmployeeDto;

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

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.id(), request);
    }
}
