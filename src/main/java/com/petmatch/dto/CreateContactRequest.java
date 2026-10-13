package com.petmatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateContactRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 500, message = "message must be at most 500 characters")
        String message) {
}