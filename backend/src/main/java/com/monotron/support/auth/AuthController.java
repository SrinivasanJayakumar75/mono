package com.monotron.support.auth;

import com.monotron.support.auth.dto.AuthResponse;
import com.monotron.support.auth.dto.GoogleAuthConfig;
import com.monotron.support.auth.dto.GoogleAuthRequest;
import com.monotron.support.auth.dto.LoginRequest;
import com.monotron.support.auth.dto.RefreshRequest;
import com.monotron.support.auth.dto.RegisterRequest;
import com.monotron.support.auth.dto.SwitchWorkspaceRequest;
import com.monotron.support.auth.dto.TokenPair;
import com.monotron.support.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/google/config")
    public GoogleAuthConfig googleConfig() {
        return authService.googleConfig();
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return authService.googleAuth(request);
    }

    @PostMapping("/refresh")
    public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.currentSession(principal);
    }

    @PostMapping("/switch-workspace")
    public AuthResponse switchWorkspace(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody SwitchWorkspaceRequest request) {
        return authService.switchWorkspace(principal, request.workspaceId());
    }
}
