package com.petmatch.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * A match as seen by one of its two owners. The other side appears only through their
 * public post fields: never their user id, email or coordinates.
 */
public record MatchResponse(
        Long matchId,
        String status,
        double score,
        Map<String, Double> breakdown,
        OffsetDateTime createdAt,
        Long myPostId,
        String myPostType,
        Long otherPostId,
        String otherPublicArea,
        String otherColor,
        String otherSize,
        Boolean otherHasCollar,
        OffsetDateTime otherEventTime) {
}