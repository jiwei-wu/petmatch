package com.petmatch.dto;

import java.time.OffsetDateTime;

/**
 * A contact request as seen by one of its two parties. contactEmail is the OTHER person's
 * email and is null unless the request has been ACCEPTED.
 */
public record ContactRequestResponse(
        Long id,
        Long matchId,
        String direction,
        String status,
        String message,
        OffsetDateTime createdAt,
        String contactEmail) {
}