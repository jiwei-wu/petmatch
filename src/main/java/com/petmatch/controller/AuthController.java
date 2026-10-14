package com.petmatch.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.petmatch.dto.AuthResponse;
import com.petmatch.dto.LoginRequest;
import com.petmatch.dto.RegisterRequest;
import com.petmatch.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Register and log in; both return a JWT")
@RestController
@RequestMapping("/api/auth")

public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new account", description = "Creates the account and returns a JWT. Fails if the email is already registered.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Log in", description = "Returns a JWT. Paste it into the Authorize button to call protected endpoints.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}