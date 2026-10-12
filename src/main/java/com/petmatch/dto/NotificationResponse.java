package com.petmatch.dto;

import java.time.OffsetDateTime;

/** What a user sees for one notification. Only their own post and the match id: no other user's data. */
public record NotificationResponse(
        Long id,
        String type,
        Long matchId,
        Long myPostId,
        double score,
        OffsetDateTime createdAt,
        boolean read) {
}