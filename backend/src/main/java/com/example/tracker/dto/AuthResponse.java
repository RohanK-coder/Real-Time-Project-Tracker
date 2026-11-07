package com.example.tracker.dto;

import com.example.tracker.model.Role;

public record AuthResponse(String token, String username, Role role) {
}
