package com.petmatch.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * What the client sees for each candidate. Privacy: no coordinates, no user id,
 * and distance is only a coarse bucket, never an exact number.
 */
public record CandidateResponse(
        Long postId,
        String type,
        String publicArea,
        String color,
        String size,
        Boolean hasCollar,
        OffsetDateTime eventTime,
        String distanceBucket,
        double score,
        Map<String, Double> breakdown) {
}