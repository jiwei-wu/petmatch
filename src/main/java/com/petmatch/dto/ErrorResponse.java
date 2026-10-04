package com.petmatch.dto;


import java.time.OffsetDateTime;

public class ErrorResponse {

    private String message;
    private OffsetDateTime timestamp;

    public ErrorResponse(String message) {
        this.message = message;
        this.timestamp = OffsetDateTime.now();
    }

    public String getMessage() { return message; }
    public OffsetDateTime getTimestamp() { return timestamp; }
}
