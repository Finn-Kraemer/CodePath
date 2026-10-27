package de.codepath.backend.identity;

public record AuthResponse(String username, String role, String displayName, String courseName) {
}
