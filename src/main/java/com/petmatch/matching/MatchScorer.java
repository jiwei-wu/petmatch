package com.petmatch.matching;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Pure calculation: scores one (lost post, sighting post) pair.
 * No database access; every parameter comes from ScoringProperties.
 */
@Component
public class MatchScorer {

    private static final List<String> SIZE_ORDER = List.of("small", "medium", "large");

    /** The only fields scoring needs. Decoupled from the JPA entity so tests can simply create one. */
    public record Side(String color, String size, Boolean hasCollar, OffsetDateTime eventTime) {}

    /** total is out of 100; parts holds each weighted component so we can explain why a pair matched. */
    public record Result(double total, Map<String, Double> parts) {}

    private final ScoringProperties p;

    public MatchScorer(ScoringProperties properties) {
        this.p = properties;
    }

    public Result score(Side lost, Side sighting, double distanceMeters) {
        double days = daysBetween(lost.eventTime(), sighting.eventTime());

        double d = distanceScore(distanceMeters, days) * p.getDistanceWeight();
        double t = timeScore(days) * p.getTimeWeight();
        double c = colorScore(lost.color(), sighting.color()) * p.getColorWeight();
        double s = sizeScore(lost.size(), sighting.size()) * p.getSizeWeight();
        double k = collarScore(lost.hasCollar(), sighting.hasCollar()) * p.getCollarWeight();

        double weightSum = p.getDistanceWeight() + p.getTimeWeight() + p.getColorWeight()
                + p.getSizeWeight() + p.getCollarWeight();
        double total = (d + t + c + s + k) / weightSum * 100;

        Map<String, Double> parts = new LinkedHashMap<>();
        parts.put("distance", round(d));
        parts.put("time", round(t));
        parts.put("color", round(c));
        parts.put("size", round(s));
        parts.put("collar", round(k));
        return new Result(round(total), parts);
    }

    // ---------- Individual components (each returns 0..1; package-private for unit tests) ----------

    double distanceScore(double meters, double daysElapsed) {
        double tolerance = Math.min(
                p.getMaxToleranceMeters(),
                p.getDistanceToleranceMeters() + p.getToleranceGrowthMetersPer7Days() * daysElapsed / 7.0);
        if (meters <= tolerance) return 1.0;
        return halfLife(meters - tolerance, p.getDistanceHalfLifeMeters());
    }

    double timeScore(double daysElapsed) {
        return halfLife(daysElapsed, p.getTimeHalfLifeDays());
    }

    double colorScore(String a, String b) {
        String x = normalizeColor(a);
        String y = normalizeColor(b);
        if (x == null || y == null) return p.getColorUnknownScore();
        if (x.equals(y)) return 1.0;
        Double v = p.getColorSimilarity().get(x + "|" + y);
        if (v == null) v = p.getColorSimilarity().get(y + "|" + x);
        return v != null ? v : p.getColorUnrelatedScore();
    }

    double sizeScore(String a, String b) {
        String x = norm(a);
        String y = norm(b);
        // List.of(...).indexOf(null) throws NullPointerException, so handle unknown sizes first
        if (x == null || y == null) return p.getSizeUnknownScore();
        int i = SIZE_ORDER.indexOf(x);
        int j = SIZE_ORDER.indexOf(y);
        if (i < 0 || j < 0) return p.getSizeUnknownScore();
        int diff = Math.abs(i - j);
        if (diff == 0) return 1.0;
        if (diff == 1) return p.getSizeAdjacentScore();
        return 0.0;
    }

    /** lost = owner says the pet wore a collar; found = finder saw a collar; null = unknown. */
    double collarScore(Boolean lost, Boolean found) {
        if (lost == null || found == null) return p.getCollarUnknown();
        if (lost && found) return p.getCollarBothHave();
        if (lost) return p.getCollarLostHasFoundNone();
        if (found) return p.getCollarLostNoneFoundHas();
        return p.getCollarBothNone();
    }

    // ---------- Small helpers ----------

    private String normalizeColor(String raw) {
        String c = norm(raw);
        if (c == null) return null;
        return p.getColorSynonyms().getOrDefault(c, c);
    }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase();
        return t.isEmpty() ? null : t;
    }

    private static double halfLife(double x, double halfLife) {
        return Math.pow(0.5, Math.max(0, x) / halfLife);
    }

    private static double daysBetween(OffsetDateTime a, OffsetDateTime b) {
        return Math.abs(Duration.between(a, b).toMinutes()) / 1440.0;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

        /**
     * Score one row from the candidate query. The anchor post may be either the lost side
     * or the sighting side, so this decides which is which before scoring.
     */
    public Result score(CandidateRow row) {
        Side anchor = new Side(row.getAnchorColor(), row.getAnchorSize(),
                row.getAnchorHasCollar(), toTime(row.getAnchorEpoch()));
        Side candidate = new Side(row.getColor(), row.getSize(),
                row.getHasCollar(), toTime(row.getEventEpoch()));
        boolean anchorIsLost = "LOST".equals(row.getAnchorType());
        return anchorIsLost
                ? score(anchor, candidate, row.getDistanceMeters())
                : score(candidate, anchor, row.getDistanceMeters());
    }

    public static OffsetDateTime toTime(Double epochSeconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(Math.round(epochSeconds * 1000)), ZoneOffset.UTC);
    }
}