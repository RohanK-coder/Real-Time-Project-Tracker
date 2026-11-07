package com.example.tracker.service;

import com.example.tracker.dto.AuthRequest;
import com.example.tracker.dto.AuthResponse;
import com.example.tracker.dto.RegisterRequest;
import com.example.tracker.model.AppUser;
import com.example.tracker.model.Role;
import com.example.tracker.repository.UserRepository;
import com.example.tracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        AppUser user = userRepository.save(AppUser.builder()
                .username(username).email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_MEMBER) // never taken from the request
                .build());
        return new AuthResponse(jwtService.generateToken(user.getUsername(), user.getRole()), user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request, String clientIp) {
        rateLimiter.check(request.username(), clientIp);
        // Throws BadCredentialsException (-> 401) for unknown user or wrong password alike.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser user = userRepository.findByUsername(request.username()).orElseThrow();
        return new AuthResponse(jwtService.generateToken(user.getUsername(), user.getRole()), user.getUsername(), user.getRole());
    }

    @Transactional(readOnly = true)
    public AuthResponse me(String username) {
        AppUser user = userRepository.findByUsername(username).orElseThrow();
        return new AuthResponse(null, user.getUsername(), user.getRole());
    }
}
