package com.example.tracker.controller;

import com.example.tracker.dto.AuthRequest;
import com.example.tracker.dto.AuthResponse;
import com.example.tracker.dto.RegisterRequest;
import com.example.tracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request, HttpServletRequest http) {
        // getRemoteAddr honours X-Forwarded-For only when server.forward-headers-strategy is enabled
        // (set in prod, where the app sits behind a trusted reverse proxy).
        return authService.login(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication auth) {
        return authService.me(auth.getName());
    }
}
