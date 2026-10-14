package com.petmatch.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Account", description = "Information about the logged-in user")
public class TestController {

    @GetMapping("/api/me")
    @Operation(summary = "Who am I", description = "Echoes the identity found in the JWT. Handy for checking that a token works.")
    public String whoAmI(Authentication authentication) {
        return "Logged in as: " + authentication.getName();
    }
}
