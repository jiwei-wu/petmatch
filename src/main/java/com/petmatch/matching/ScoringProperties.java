package com.petmatch.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * All scoring parameters live here. To tune the engine, change the defaults below,
 * or override them in application.yaml with petmatch.scoring.xxx. MatchScorer itself
 * never needs to be edited for a parameter change.
 */
@Component
@ConfigurationProperties(prefix = "petmatch.scoring")
public class ScoringProperties {

    // ---- Weights (ideally sum to 100; initial assumptions, to be tuned later with simulated data) ----
    private double distanceWeight = 40;
    private double timeWeight = 20;
    private double colorWeight = 20;
    private double sizeWeight = 10;
    private double collarWeight = 10;
    // ---- Persisted matches: only pairs scoring at least this much are stored (out of 100) ----
    private double matchThreshold = 50;

    // ---- Distance: full score within 500 m, then the score halves every 500 m;
    //      the tolerated radius grows slowly as more days pass (heuristic) ----
    private double distanceToleranceMeters = 500;
    private double distanceHalfLifeMeters = 500;
    private double toleranceGrowthMetersPer7Days = 250;
    private double maxToleranceMeters = 2000;

    // ---- Time: score halves every 7 days ----
    private double timeHalfLifeDays = 7;

    // ---- Colour ----
    private double colorUnknownScore = 0.5;
    private double colorUnrelatedScore = 0.1;
    private Map<String, String> colorSynonyms = defaultColorSynonyms();
    /** Key is "a|b" (order does not matter), value is a similarity between 0 and 1. */
    private Map<String, Double> colorSimilarity = defaultColorSimilarity();

    // ---- Size: same bucket 1.0, adjacent bucket 0.5, otherwise 0 ----
    private double sizeAdjacentScore = 0.5;
    private double sizeUnknownScore = 0.5;

    // ---- Collar (asymmetric: owner says collar but finder saw none is more plausible than the reverse) ----
    private double collarBothHave = 1.0;
    private double collarLostHasFoundNone = 0.4;
    private double collarLostNoneFoundHas = 0.1;
    private double collarBothNone = 0.6;
    private double collarUnknown = 0.5;

    private static Map<String, String> defaultColorSynonyms() {
        Map<String, String> m = new HashMap<>();
        m.put("gray", "grey");
        m.put("ginger", "orange");
        m.put("yellow", "orange");
        m.put("tortie", "tortoiseshell");
        m.put("tabby", "tabby");
        m.put("brown tabby", "tabby");
        m.put("black and white", "tuxedo");
        return m;
    }

    private static Map<String, Double> defaultColorSimilarity() {
        Map<String, Double> m = new HashMap<>();
        m.put("orange|cream", 0.6);
        m.put("brown|tabby", 0.6);
        m.put("grey|black", 0.4);
        m.put("white|cream", 0.4);
        m.put("brown|orange", 0.4);
        m.put("brown|black", 0.4);
        m.put("tabby|grey", 0.4);
        m.put("calico|tortoiseshell", 0.5);
        m.put("tuxedo|black", 0.5);
        m.put("tuxedo|white", 0.5);
        return m;
    }

    // ---- Getters and setters (required by @ConfigurationProperties) ----
    public double getDistanceWeight() { return distanceWeight; }
    public void setDistanceWeight(double v) { this.distanceWeight = v; }
    public double getTimeWeight() { return timeWeight; }
    public void setTimeWeight(double v) { this.timeWeight = v; }
    public double getColorWeight() { return colorWeight; }
    public void setColorWeight(double v) { this.colorWeight = v; }
    public double getSizeWeight() { return sizeWeight; }
    public void setSizeWeight(double v) { this.sizeWeight = v; }
    public double getCollarWeight() { return collarWeight; }
    public void setCollarWeight(double v) { this.collarWeight = v; }
    public double getDistanceToleranceMeters() { return distanceToleranceMeters; }
    public void setDistanceToleranceMeters(double v) { this.distanceToleranceMeters = v; }
    public double getDistanceHalfLifeMeters() { return distanceHalfLifeMeters; }
    public void setDistanceHalfLifeMeters(double v) { this.distanceHalfLifeMeters = v; }
    public double getToleranceGrowthMetersPer7Days() { return toleranceGrowthMetersPer7Days; }
    public void setToleranceGrowthMetersPer7Days(double v) { this.toleranceGrowthMetersPer7Days = v; }
    public double getMaxToleranceMeters() { return maxToleranceMeters; }
    public void setMaxToleranceMeters(double v) { this.maxToleranceMeters = v; }
    public double getTimeHalfLifeDays() { return timeHalfLifeDays; }
    public void setTimeHalfLifeDays(double v) { this.timeHalfLifeDays = v; }
    public double getColorUnknownScore() { return colorUnknownScore; }
    public void setColorUnknownScore(double v) { this.colorUnknownScore = v; }
    public double getColorUnrelatedScore() { return colorUnrelatedScore; }
    public void setColorUnrelatedScore(double v) { this.colorUnrelatedScore = v; }
    public Map<String, String> getColorSynonyms() { return colorSynonyms; }
    public void setColorSynonyms(Map<String, String> v) { this.colorSynonyms = v; }
    public Map<String, Double> getColorSimilarity() { return colorSimilarity; }
    public void setColorSimilarity(Map<String, Double> v) { this.colorSimilarity = v; }
    public double getSizeAdjacentScore() { return sizeAdjacentScore; }
    public void setSizeAdjacentScore(double v) { this.sizeAdjacentScore = v; }
    public double getSizeUnknownScore() { return sizeUnknownScore; }
    public void setSizeUnknownScore(double v) { this.sizeUnknownScore = v; }
    public double getCollarBothHave() { return collarBothHave; }
    public void setCollarBothHave(double v) { this.collarBothHave = v; }
    public double getCollarLostHasFoundNone() { return collarLostHasFoundNone; }
    public void setCollarLostHasFoundNone(double v) { this.collarLostHasFoundNone = v; }
    public double getCollarLostNoneFoundHas() { return collarLostNoneFoundHas; }
    public void setCollarLostNoneFoundHas(double v) { this.collarLostNoneFoundHas = v; }
    public double getCollarBothNone() { return collarBothNone; }
    public void setCollarBothNone(double v) { this.collarBothNone = v; }
    public double getCollarUnknown() { return collarUnknown; }
    public void setCollarUnknown(double v) { this.collarUnknown = v; }
    public double getMatchThreshold() { return matchThreshold; }
    public void setMatchThreshold(double v) { this.matchThreshold = v; }
}