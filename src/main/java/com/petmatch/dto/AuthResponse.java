package com.petmatch.dto;


public class AuthResponse {

    private String token;
    private Long userId;
    private String email;
    private String displayName;

    public AuthResponse(String token, Long userId, String email, String displayName) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
}